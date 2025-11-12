package io.github.riccardomerolla.ziotoon

import zio.Chunk

import ToonError._
import ToonValue._

/** Decoder for parsing TOON format strings into ToonValue.
  *
  * Pure functional implementation following FP principles:
  *   - No mutable state
  *   - Pure functions with Either for error handling
  *   - No early returns (boundary/break removed)
  *   - Recursive over imperative loops
  *   - Immutable data structures
  */
class ToonDecoder(config: DecoderConfig = DecoderConfig.default) {

  import StringUtils._

  /** Line with metadata for parsing.
    */
  private case class Line(content: String, depth: Int, lineNumber: Int, indent: Int)

  /** Decode a TOON format string into a ToonValue.
    *
    * Pure function - all errors in Either, no exceptions thrown, no early returns.
    */
  def decode(input: String): Either[ToonError, ToonValue] =
    if (input.trim.isEmpty) Right(Null)
    else
      for {
        lines  <- parseLines(input)
        result <- if (lines.isEmpty) Right(Null)
                  else decodeRoot(lines)
      } yield result

  /** Decode the root value based on the first line structure. Pure function using pattern matching.
    */
  private def decodeRoot(lines: Chunk[Line]): Either[ToonError, ToonValue] = {
    val firstLine = lines.head
    val content   = firstLine.content

    if (content.matches("^.+\\[\\d+.*\\]:.*") && content.contains("[")) {
      // Object with array field at root
      val (result, _) = parseObject(lines, 0)
      result
    }
    else if (content.matches("^\\[\\d+.*\\]:.*")) {
      // Array at root without key
      val (result, _) = parseArray(lines, 0, None)
      result
    }
    else if (content.contains(":")) {
      // Object at root
      val (result, _) = parseObject(lines, 0)
      result
    }
    else {
      // Single primitive value
      parsePrimitive(firstLine.content, firstLine.lineNumber)
    }
  }

  /** Parse lines with indentation information. Pure function using recursion instead of mutable state.
    */
  private def parseLines(input: String): Either[ToonError, Chunk[Line]] = {
    val rawLines = input.split("\n")

    def processLine(idx: Int, lineText: String): Either[ToonError, Option[Line]] =
      if (lineText.trim.isEmpty) Right(None)
      else {
        val indent = lineText.takeWhile(_ == ' ').length
        val depth  = indent / config.indentSize

        if (config.strictMode && indent % config.indentSize != 0) {
          Left(
            IndentationError(
              s"Indentation must be a multiple of ${config.indentSize} spaces",
              idx + 1,
            )
          )
        }
        else {
          Right(Some(Line(lineText.trim, depth, idx + 1, indent)))
        }
      }

    // Process all lines, collecting only the successful non-empty ones
    rawLines
      .zipWithIndex
      .foldLeft[Either[ToonError, List[Line]]](Right(List.empty)) {
        case (Left(error), _)              => Left(error)
        case (Right(acc), (lineText, idx)) =>
          processLine(idx, lineText).map {
            case Some(line) => acc :+ line
            case None       => acc
          }
      }
      .map(Chunk.fromIterable)
  }

  /** Parse a primitive value from a string.
    */
  private def parsePrimitive(s: String, lineNumber: Int): Either[ToonError, Primitive] = {
    val trimmed = s.trim

    // Check for quoted string
    if (trimmed.startsWith("\"")) {
      if (!trimmed.endsWith("\"") || trimmed.length < 2) {
        return Left(UnterminatedString(lineNumber))
      }
      val content = trimmed.substring(1, trimmed.length - 1)
      unescape(content) match {
        case Left(msg)        => Left(InvalidEscape(msg, lineNumber))
        case Right(unescaped) => Right(Str(unescaped))
      }
    }
    // Check for null
    else if (trimmed == "null") {
      Right(Null)
    }
    // Check for boolean
    else if (trimmed == "true") {
      Right(Bool(true))
    }
    else if (trimmed == "false") {
      Right(Bool(false))
    }
    // Check for number
    else if (trimmed.matches("^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$")) {
      try {
        val num = trimmed.toDouble
        Right(Num(num))
      }
      catch {
        case _: NumberFormatException => Right(Str(trimmed))
      }
    }
    // Otherwise it's an unquoted string
    else {
      Right(Str(trimmed))
    }
  }

  /** Parse an object from lines starting at given index. Returns (result, number of lines consumed). Pure
    * tail-recursive function without mutable state.
    */
  private def parseObject(lines: Chunk[Line], startIdx: Int): (Either[ToonError, Obj], Int) = {
    val startDepth = if (startIdx < lines.length) lines(startIdx).depth else 0

    @scala.annotation.tailrec
    def loop(idx: Int, fields: List[(String, ToonValue)]): (Either[ToonError, Obj], Int) =
      if (idx >= lines.length || lines(idx).depth < startDepth) {
        // Done - return accumulated fields
        (Right(Obj(Chunk.fromIterable(fields))), idx - startIdx)
      }
      else if (lines(idx).depth > startDepth) {
        // Skip deeper nested content (should be handled by recursive calls)
        loop(idx + 1, fields)
      }
      else {
        // Process line at current depth
        val line    = lines(idx)
        val content = line.content

        if (content.startsWith("-")) {
          // Unexpected list marker in object
          (Left(ParseError(s"Unexpected list item marker in object at line ${line.lineNumber}")), idx - startIdx)
        }
        else {
          processObjectLine(lines, idx, startDepth, fields) match {
            case Left(error)                  => (Left(error), idx - startIdx)
            case Right((newFields, consumed)) => loop(idx + consumed, fields ++ newFields)
          }
        }
      }

    loop(startIdx, List.empty)
  }

  /** Process a single object line and return new fields and lines consumed. Pure helper function.
    */
  private def processObjectLine(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      currentFields: List[(String, ToonValue)],
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val line     = lines(idx)
    val content  = line.content
    val colonIdx = findUnquotedColon(content)

    if (colonIdx < 0) {
      if (config.strictMode) {
        Left(MissingColon(line.lineNumber))
      }
      else {
        // Skip line in non-strict mode
        Right((List.empty, 1))
      }
    }
    else {
      parseObjectField(lines, idx, depth, content, colonIdx)
    }
  }

  /** Parse an object field (key-value pair). Pure function that handles all field types.
    */
  private def parseObjectField(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      content: String,
      colonIdx: Int,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val line = lines(idx)

    // Check if this is an array header
    if (content.matches("^.+?\\[\\d+[|\\t]?\\](?:\\{.+?\\})?:.*")) {
      parseArrayField(lines, idx, depth, content)
    }
    else {
      parseSimpleField(lines, idx, depth, content, colonIdx)
    }
  }

  /** Parse a simple key-value field. Pure function.
    */
  private def parseSimpleField(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      content: String,
      colonIdx: Int,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val line = lines(idx)
    val key  = content.substring(0, colonIdx).trim

    val unquotedKey = if (key.startsWith("\"") && key.endsWith("\"")) {
      unescape(key.substring(1, key.length - 1)) match {
        case Left(msg) => return Left(InvalidEscape(msg, line.lineNumber))
        case Right(k)  => k
      }
    }
    else key

    val valueStr = content.substring(colonIdx + 1).trim

    if (valueStr.isEmpty) {
      // Check for nested content
      if (idx + 1 < lines.length && lines(idx + 1).depth == depth + 1) {
        val nextLine = lines(idx + 1)
        if (nextLine.content.startsWith("-") || nextLine.content.matches("^\\[\\d+.*\\]:?.*")) {
          // Nested array
          val (result, consumed) = parseArray(lines, idx + 1, Some(unquotedKey))
          result.map(arr => (List((unquotedKey, arr)), consumed + 1))
        }
        else {
          // Nested object
          val (result, consumed) = parseObject(lines, idx + 1)
          result.map(obj => (List((unquotedKey, obj)), consumed + 1))
        }
      }
      else {
        // Empty object
        Right((List((unquotedKey, Obj.empty)), 1))
      }
    }
    else {
      // Inline primitive value
      parsePrimitive(valueStr, line.lineNumber).map(prim => (List((unquotedKey, prim)), 1))
    }
  }

  /** Parse an array field from object. Pure function extracting array parsing logic.
    */
  private def parseArrayField(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      content: String,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val line        = lines(idx)
    val headerMatch = """^(.+?)\[(\d+)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r

    content match {
      case headerMatch(keyPart, lengthStr, delimSymbol, fieldList, values) =>
        val key         = keyPart.trim
        val unquotedKey = if (key.startsWith("\"") && key.endsWith("\"")) {
          unescape(key.substring(1, key.length - 1)) match {
            case Left(msg) => return Left(InvalidEscape(msg, line.lineNumber))
            case Right(k)  => k
          }
        }
        else key

        val length    = lengthStr.toInt
        val delimiter =
          if (delimSymbol == "\t") Delimiter.Tab
          else if (delimSymbol == "|") Delimiter.Pipe
          else Delimiter.Comma

        if (values.nonEmpty) {
          // Inline array
          parseInlineArrayValues(values, delimiter, length, line.lineNumber)
            .map(arr => (List((unquotedKey, arr)), 1))
        }
        else if (fieldList != null && fieldList.nonEmpty) {
          // Tabular array
          val (result, consumed) = parseTabularArray(lines, idx + 1, length, fieldList, delimiter, depth)
          result.map(arr => (List((unquotedKey, arr)), consumed + 1))
        }
        else if (length == 0) {
          // Empty array
          Right((List((unquotedKey, Arr.empty)), 1))
        }
        else {
          // List array
          val (result, consumed) = parseListArray(lines, idx + 1, length, depth)
          result.map(arr => (List((unquotedKey, arr)), consumed + 1))
        }

      case _ =>
        Left(ParseError(s"Invalid array header at line ${line.lineNumber}"))
    }
  }

  /** Find the index of an unquoted colon in a string. Pure tail-recursive function.
    */
  private def findUnquotedColon(s: String): Int = {
    @scala.annotation.tailrec
    def loop(idx: Int, inQuotes: Boolean): Int =
      if (idx >= s.length) -1
      else
        s.charAt(idx) match {
          case '"'              => loop(idx + 1, !inQuotes)
          case ':' if !inQuotes => idx
          case _                => loop(idx + 1, inQuotes)
        }

    loop(0, false)
  }

  /** Parse an array line (e.g., "key[3]: v1,v2,v3") and return (key, array).
    */
  private def parseArrayLine(content: String, lineNumber: Int): Either[ToonError, (String, Arr)] = {
    // Extract key and array part
    val headerMatch = """^(.+?)\[(\d+)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r
    content match {
      case headerMatch(keyPart, lengthStr, delimSymbol, fieldList, values) =>
        val key         = keyPart.trim
        val unquotedKey = if (key.startsWith("\"") && key.endsWith("\"")) {
          unescape(key.substring(1, key.length - 1)) match {
            case Left(msg) => return Left(InvalidEscape(msg, lineNumber))
            case Right(k)  => k
          }
        }
        else {
          key
        }

        val length    = lengthStr.toInt
        val delimiter =
          if (delimSymbol == "\t") Delimiter.Tab
          else if (delimSymbol == "|") Delimiter.Pipe
          else Delimiter.Comma

        if (values.nonEmpty) {
          // Inline array
          parseInlineArrayValues(values, delimiter, length, lineNumber).map { arr =>
            (unquotedKey, arr)
          }
        }
        else {
          // Empty array or needs child lines (not supported in this method)
          Right((unquotedKey, Arr.empty))
        }

      case _ =>
        Left(ParseError(s"Invalid array format at line $lineNumber"))
    }
  }

  /** Parse an array from lines.
    */
  private def parseArray(lines: Chunk[Line], startIdx: Int, keyOpt: Option[String]): (Either[ToonError, Arr], Int) = {
    val line    = lines(startIdx)
    val content = line.content

    // Extract array header
    val headerMatch = """^(?:(.+?))?\[(\d+)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r
    content match {
      case headerMatch(key, lengthStr, delimSymbol, fieldList, values) =>
        val length    = lengthStr.toInt
        val delimiter =
          if (delimSymbol == "\t") Delimiter.Tab
          else if (delimSymbol == "|") Delimiter.Pipe
          else Delimiter.Comma

        if (values.nonEmpty) {
          // Inline array
          parseInlineArrayValues(values, delimiter, length, line.lineNumber)
            .map { arr =>
              (Right(arr), 1)
            }
            .getOrElse((Left(ParseError(s"Failed to parse inline array at line ${line.lineNumber}")), 1))
        }
        else if (fieldList != null && fieldList.nonEmpty) {
          // Tabular array
          parseTabularArray(lines, startIdx + 1, length, fieldList, delimiter, line.depth)
        }
        else {
          // List array
          parseListArray(lines, startIdx + 1, length, line.depth)
        }

      case _ =>
        (Left(ParseError(s"Invalid array header at line ${line.lineNumber}")), 1)
    }
  }

  /** Parse array content (used when nested).
    */
  private def parseArrayContent(lines: Chunk[Line], startIdx: Int, expectedDepth: Int)
      : (Either[ToonError, Arr], Int) = {
    // This is a simplified version - would need more logic for full support
    val firstLine = lines(startIdx)
    if (firstLine.content.startsWith("-")) {
      // List array - count items
      var idx   = startIdx
      val items = scala.collection.mutable.ArrayBuffer[ToonValue]()

      while (idx < lines.length && lines(idx).depth >= expectedDepth) {
        val line = lines(idx)
        if (line.depth == expectedDepth && line.content.startsWith("-")) {
          val content = line.content.substring(1).trim
          parsePrimitive(content, line.lineNumber) match {
            case Left(err)   => return (Left(err), idx - startIdx)
            case Right(prim) => items += prim
          }
        }
        idx += 1
      }

      (Right(Arr(Chunk.fromIterable(items))), idx - startIdx)
    }
    else {
      (Left(ParseError(s"Unsupported array format at line ${firstLine.lineNumber}")), 0)
    }
  }

  /** Parse inline array values.
    */
  private def parseInlineArray(content: String, lineNumber: Int): Either[ToonError, Arr] = {
    val headerMatch = """^\[(\d+)([|\t]?)\]:\s*(.*)$""".r
    content match {
      case headerMatch(lengthStr, delimSymbol, values) =>
        val length    = lengthStr.toInt
        val delimiter =
          if (delimSymbol == "\t") Delimiter.Tab
          else if (delimSymbol == "|") Delimiter.Pipe
          else Delimiter.Comma
        parseInlineArrayValues(values, delimiter, length, lineNumber)

      case _ =>
        Left(ParseError(s"Invalid inline array format at line $lineNumber"))
    }
  }

  /** Parse inline array values. Pure function using foldLeft instead of mutable state.
    */
  private def parseInlineArrayValues(
      values: String,
      delimiter: Delimiter,
      expectedLength: Int,
      lineNumber: Int,
    ): Either[ToonError, Arr] =
    if (values.isEmpty && expectedLength == 0) {
      Right(Arr.empty)
    }
    else {
      val split = splitByDelimiter(values, delimiter.char)

      if (config.strictMode && split.length != expectedLength) {
        Left(CountMismatch(expectedLength, split.length, "inline array", lineNumber))
      }
      else {
        // Use foldLeft to accumulate Either values
        split
          .foldLeft[Either[ToonError, List[Primitive]]](Right(List.empty)) {
            case (Left(error), _)    => Left(error)
            case (Right(acc), value) =>
              parsePrimitive(value.trim, lineNumber).map(prim => acc :+ prim)
          }
          .map(prims => Arr(Chunk.fromIterable(prims)))
      }
    }

  /** Parse tabular array. Pure functional implementation using foldLeft and recursion.
    */
  private def parseTabularArray(
      lines: Chunk[Line],
      startIdx: Int,
      expectedLength: Int,
      fieldList: String,
      delimiter: Delimiter,
      headerDepth: Int,
    ): (Either[ToonError, Arr], Int) = {
    // Parse field names using foldLeft instead of mutable buffer
    val fieldParts   = splitByDelimiter(fieldList, delimiter.char)
    val fieldsResult = fieldParts.foldLeft[Either[ToonError, List[String]]](Right(List.empty)) {
      case (Left(error), _)        => Left(error)
      case (Right(acc), fieldPart) =>
        val trimmed = fieldPart.trim
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
          unescape(trimmed.substring(1, trimmed.length - 1)) match {
            case Left(msg) => Left(InvalidEscape(msg, lines(startIdx).lineNumber))
            case Right(k)  => Right(acc :+ k)
          }
        }
        else {
          Right(acc :+ trimmed)
        }
    }

    fieldsResult match {
      case Left(error)      => (Left(error), 0)
      case Right(fieldList) =>
        val fields   = fieldList.toArray
        val rowDepth = headerDepth + 1

        // Parse rows using tail recursion
        @scala.annotation.tailrec
        def parseRows(idx: Int, rows: List[Obj]): (Either[ToonError, List[Obj]], Int) =
          if (idx >= lines.length || lines(idx).depth < rowDepth) {
            (Right(rows), idx - startIdx)
          }
          else if (lines(idx).depth == rowDepth) {
            val line   = lines(idx)
            val values = splitByDelimiter(line.content, delimiter.char)

            if (config.strictMode && values.length != fields.length) {
              (Left(WidthMismatch(fields.length, values.length, line.lineNumber)), idx - startIdx)
            }
            else {
              // Parse field values using foldLeft
              val fieldValuesResult = fields
                .zip(values)
                .foldLeft[Either[ToonError, List[(String, Primitive)]]](Right(List.empty)) {
                  case (Left(error), _)                 => Left(error)
                  case (Right(acc), (fieldName, value)) =>
                    parsePrimitive(value.trim, line.lineNumber).map(prim => acc :+ (fieldName, prim))
                }

              fieldValuesResult match {
                case Left(error)        => (Left(error), idx - startIdx)
                case Right(fieldValues) =>
                  val row = Obj(Chunk.fromIterable(fieldValues))
                  parseRows(idx + 1, rows :+ row)
              }
            }
          }
          else {
            parseRows(idx + 1, rows)
          }

        val (rowsResult, consumed) = parseRows(startIdx, List.empty)
        rowsResult match {
          case Left(error) => (Left(error), consumed)
          case Right(rows) =>
            if (config.strictMode && rows.length != expectedLength) {
              (Left(CountMismatch(expectedLength, rows.length, "tabular array", lines(startIdx).lineNumber)), consumed)
            }
            else {
              (Right(Arr(Chunk.fromIterable(rows))), consumed)
            }
        }
    }
  }

  /** Parse list array. Pure functional implementation using tail recursion.
    */
  private def parseListArray(
      lines: Chunk[Line],
      startIdx: Int,
      expectedLength: Int,
      headerDepth: Int,
    ): (Either[ToonError, Arr], Int) = {
    val itemDepth = headerDepth + 1

    @scala.annotation.tailrec
    def parseItems(idx: Int, items: List[ToonValue]): (Either[ToonError, List[ToonValue]], Int) =
      if (idx >= lines.length || lines(idx).depth < itemDepth) {
        (Right(items), idx - startIdx)
      }
      else if (lines(idx).depth == itemDepth && lines(idx).content.startsWith("-")) {
        parseListItem(lines, idx, startIdx, itemDepth) match {
          case Left(error)             => (Left(error), idx - startIdx)
          case Right((item, consumed)) => parseItems(idx + consumed, items :+ item)
        }
      }
      else {
        parseItems(idx + 1, items)
      }

    val (itemsResult, consumed) = parseItems(startIdx, List.empty)
    itemsResult match {
      case Left(error)  => (Left(error), consumed)
      case Right(items) =>
        if (config.strictMode && items.length != expectedLength) {
          (Left(CountMismatch(expectedLength, items.length, "list array", lines(startIdx).lineNumber)), consumed)
        }
        else {
          (Right(Arr(Chunk.fromIterable(items))), consumed)
        }
    }
  }

  /** Parse a single list item. Pure helper function.
    */
  private def parseListItem(
      lines: Chunk[Line],
      idx: Int,
      startIdx: Int,
      itemDepth: Int,
    ): Either[ToonError, (ToonValue, Int)] = {
    val line    = lines(idx)
    val content = line.content.substring(1).trim

    if (content.isEmpty) {
      Right((Obj.empty, 1))
    }
    else {
      val colonIdx = findUnquotedColon(content)
      if (colonIdx < 0) {
        // Simple primitive
        parsePrimitive(content, line.lineNumber).map(prim => (prim, 1))
      }
      else {
        val key      = content.substring(0, colonIdx).trim
        val valueStr = content.substring(colonIdx + 1).trim

        if (valueStr.isEmpty) {
          // Nested structure
          val (result, consumed) = parseObject(lines, idx + 1)
          result.map(obj => (obj, consumed + 1))
        }
        else {
          // Simple field
          for {
            prim        <- parsePrimitive(valueStr, line.lineNumber)
            unquotedKey <- if (key.startsWith("\"") && key.endsWith("\"")) {
                             unescape(key.substring(1, key.length - 1))
                               .left
                               .map(msg => InvalidEscape(msg, line.lineNumber))
                           }
                           else {
                             Right(key)
                           }
          } yield (Obj((unquotedKey, prim)), 1)
        }
      }
    }
  }

  /** Split a string by delimiter, respecting quoted strings. Pure functional implementation using tail recursion.
    */
  private def splitByDelimiter(s: String, delimiter: Char): Array[String] = {
    @scala.annotation.tailrec
    def loop(idx: Int, inQuotes: Boolean, current: StringBuilder, result: List[String]): List[String] =
      if (idx >= s.length) {
        result :+ current.toString
      }
      else {
        val c = s.charAt(idx)
        if (c == '"') {
          loop(idx + 1, !inQuotes, current.append(c), result)
        }
        else if (c == delimiter && !inQuotes) {
          loop(idx + 1, inQuotes, new StringBuilder, result :+ current.toString)
        }
        else {
          loop(idx + 1, inQuotes, current.append(c), result)
        }
      }

    loop(0, false, new StringBuilder, List.empty).toArray
  }
}

object ToonDecoder {
  def apply(config: DecoderConfig = DecoderConfig.default): ToonDecoder =
    new ToonDecoder(config)

  /** Convenience method to decode a TOON string.
    */
  def decode(input: String, config: DecoderConfig = DecoderConfig.default): Either[ToonError, ToonValue] =
    ToonDecoder(config).decode(input)
}
