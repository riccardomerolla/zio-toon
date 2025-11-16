package io.github.riccardomerolla.ziotoon

import zio.Chunk

import ToonError._
import ToonValue._
import scala.collection.immutable.VectorMap

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

  // Regex pattern for array headers - defined once for reuse
  private val ArrayHeaderPattern = """^(.+?)\[(\d+|\?)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r

  /** Line with metadata for parsing.
    */
  private case class Line(content: String, depth: Int, lineNumber: Int, indent: Int)

  /** Unquote a key if it's quoted, handling escape sequences. Pure helper function to reduce duplication.
    */
  private def unquoteKey(key: String, lineNumber: Int): Either[ToonError, String] =
    if (key.startsWith("\"") && key.endsWith("\""))
      unescape(key.substring(1, key.length - 1))
        .left
        .map(msg => InvalidEscape(msg, lineNumber))
        .flatMap(value => ensureStringWithinLimit(value, lineNumber))
    else ensureStringWithinLimit(key, lineNumber)

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

  /** Decode the root value based on the first line structure. Pure function using pattern matching with guards.
    */
  private def decodeRoot(lines: Chunk[Line]): Either[ToonError, ToonValue] = {
    val firstLine = lines.head
    val content   = firstLine.content

    content match {
      // Object with array field at root: "key[N]: ..."
      case c if c.matches("^.+\\[(?:\\d+|\\?).*\\]:.*") && c.contains("[") =>
        parseObject(lines, 0)._1

      // Array at root without key: "[N]: ..."
      case c if c.matches("^\\[(?:\\d+|\\?).*\\]:.*") =>
        parseArray(lines, 0, None)._1

      // Object at root: "key: ..."
      case c if c.contains(":") =>
        parseObject(lines, 0)._1

      // Single primitive value
      case _ =>
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
          ensureDepthWithinLimit(depth, idx + 1).map(_ => Line(lineText.trim, depth, idx + 1, indent)).map(Some(_))
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

  /** Parse a primitive value from a string. Pure function using pattern matching and Either composition.
    */
  private def parsePrimitive(s: String, lineNumber: Int): Either[ToonError, Primitive] = {
    val trimmed = s.trim

    trimmed match {
      case quoted if quoted.startsWith("\"") =>
        if (!quoted.endsWith("\"") || quoted.length < 2) Left(UnterminatedString(lineNumber))
        else
          unescape(quoted.substring(1, quoted.length - 1))
            .left
            .map(msg => InvalidEscape(msg, lineNumber))
            .flatMap(value => ensureStringWithinLimit(value, lineNumber))
            .map(Str.apply)

      case "null"  => Right(Null)
      case "true"  => Right(Bool(true))
      case "false" => Right(Bool(false))

      case numeric if numeric.matches("^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$") =>
        scala
          .util
          .Try(BigDecimal(numeric))
          .toEither
          .left
          .map(_ => InvalidNumber(numeric, lineNumber))
          .flatMap { big =>
            val asDouble = scala.util.Try(numeric.toDouble).toOption
            asDouble match {
              case Some(value) if value.isInfinity || value.isNaN =>
                Left(InvalidNumber(numeric, lineNumber))
              case None                                           =>
                Left(InvalidNumber(numeric, lineNumber))
              case _                                              =>
                Right(Num(big))
            }
          }

      case other =>
        ensureStringWithinLimit(other, lineNumber).map(Str.apply)
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
        (Right(Obj(VectorMap.from(fields))), idx - startIdx)
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

  /** Process a single object line and return new fields and lines consumed. Extracted method with clear responsibility:
    * validate and route to field parser.
    */
  private def processObjectLine(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      currentFields: List[(String, ToonValue)],
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val line = lines(idx)

    for {
      colonIdx <- validateAndFindColon(line)
      result   <- parseObjectField(lines, idx, depth, line.content, colonIdx)
    } yield result
  }

  /** Validate line has a colon and find its position. Extracted validation logic with clear responsibility.
    */
  private def validateAndFindColon(line: Line): Either[ToonError, Int] = {
    val colonIdx = findUnquotedColon(line.content)

    if (colonIdx < 0) {
      if (config.strictMode) Left(MissingColon(line.lineNumber))
      else Right(-1) // Signal to skip in non-strict mode
    }
    else {
      Right(colonIdx)
    }
  }

  /** Parse an object field (key-value pair). Pure function that routes to array or simple field parser.
    */
  private def parseObjectField(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      content: String,
      colonIdx: Int,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    // Skip line in non-strict mode when no colon found
    if (colonIdx < 0) {
      return Right((List.empty, 1))
    }

    // Route to appropriate parser based on content type
    if (isArrayFieldHeader(content)) {
      parseArrayField(lines, idx, depth, content)
    }
    else {
      parseSimpleField(lines, idx, depth, content, colonIdx)
    }
  }

  /** Check if content is an array field header. Extracted predicate for clarity.
    */
  private def isArrayFieldHeader(content: String): Boolean =
    content.matches("^.+?\\[(?:\\d+|\\?)[|\\t]?\\](?:\\{.+?\\})?:.*")

  /** Parse a simple key-value field. Pure function with single responsibility: parse key and route to value parser.
    */
  private def parseSimpleField(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      content: String,
      colonIdx: Int,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val line     = lines(idx)
    val key      = content.substring(0, colonIdx).trim
    val valueStr = content.substring(colonIdx + 1).trim

    for {
      unquotedKey <- unquoteKey(key, line.lineNumber)
      result      <- parseFieldValue(lines, idx, depth, unquotedKey, valueStr, line.lineNumber)
    } yield result
  }

  /** Parse the value part of a field (inline primitive or nested structure). Extracted method with clear
    * responsibility: route to appropriate value parser.
    */
  private def parseFieldValue(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      key: String,
      valueStr: String,
      lineNumber: Int,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] =
    if (valueStr.nonEmpty)
      parseInlineFieldValue(key, valueStr, lineNumber)
    else
      parseNestedOrEmptyValue(lines, idx, depth, key)

  /** Parse an inline primitive value. Single responsibility: handle inline values.
    */
  private def parseInlineFieldValue(
      key: String,
      valueStr: String,
      lineNumber: Int,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] =
    parsePrimitive(valueStr, lineNumber).map(prim => (List((key, prim)), 1))

  /** Parse nested structure or empty value. Improved name and reduced nesting with early return for empty case.
    */
  private def parseNestedOrEmptyValue(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      key: String,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    // No nested content - empty object
    if (idx + 1 >= lines.length || lines(idx + 1).depth != depth + 1) {
      return Right((List((key, Obj.empty)), 1))
    }

    // Has nested content - determine type and parse
    parseNestedValue(lines, idx, depth, key)
  }

  /** Parse nested value (array or object). Extracted method with single responsibility: identify and parse nested
    * structures.
    */
  private def parseNestedValue(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      key: String,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val nextLine = lines(idx + 1)

    if (isArrayContent(nextLine)) {
      parseNestedArray(lines, idx, key)
    }
    else {
      parseNestedObject(lines, idx, key)
    }
  }

  /** Check if line content indicates an array. Extracted predicate for clarity.
    */
  private def isArrayContent(line: Line): Boolean =
    line.content.startsWith("-") || line.content.matches("^\\[(?:\\d+|\\?).*\\]:?.*")

  /** Parse nested array. Single responsibility: handle nested array case.
    */
  private def parseNestedArray(
      lines: Chunk[Line],
      idx: Int,
      key: String,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val (result, consumed) = parseArray(lines, idx + 1, Some(key))
    result.map(arr => (List((key, arr)), consumed + 1))
  }

  /** Parse nested object. Single responsibility: handle nested object case.
    */
  private def parseNestedObject(
      lines: Chunk[Line],
      idx: Int,
      key: String,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val (result, consumed) = parseObject(lines, idx + 1)
    result.map(obj => (List((key, obj)), consumed + 1))
  }

  /** Parse an array field from object. Pure function using pattern matching and for-comprehension.
    */
  private def parseArrayField(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      content: String,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] = {
    val line = lines(idx)

    content match {
      case ArrayHeaderPattern(keyPart, lengthStr, delimSymbol, fieldList, values) =>
        for {
          unquotedKey <- unquoteKey(keyPart.trim, line.lineNumber)
          delimiter    = parseDelimiter(delimSymbol)
          lengthOpt   <- parseDeclaredLength(lengthStr, line.lineNumber)
          result      <-
            parseArrayContent(
              lines,
              idx,
              depth,
              unquotedKey,
              lengthOpt,
              delimiter,
              fieldList,
              values,
              line.lineNumber,
            )
        } yield result

      case _ => Left(ParseError(s"Invalid array header at line ${line.lineNumber}"))
    }
  }

  /** Parse delimiter symbol. Pure helper function.
    */
  private def parseDelimiter(delimSymbol: String): Delimiter =
    if (delimSymbol == "\t") Delimiter.Tab
    else if (delimSymbol == "|") Delimiter.Pipe
    else Delimiter.Comma

  private def parseDeclaredLength(lengthStr: String, lineNumber: Int): Either[ToonError, Option[Int]] =
    if (lengthStr == "?") Right(None)
    else
      scala
        .util
        .Try(lengthStr.toInt)
        .toEither
        .left
        .map(_ => ParseError(s"Invalid array length '$lengthStr' at line $lineNumber"))
        .flatMap { length =>
          ensureArrayLimit(length, "array declaration", lineNumber).map(_ => Some(length))
        }

  /** Parse array content based on format. Pure function routing to specific array parsers.
    */
  private def parseArrayContent(
      lines: Chunk[Line],
      idx: Int,
      depth: Int,
      key: String,
      lengthOpt: Option[Int],
      delimiter: Delimiter,
      fieldList: String,
      values: String,
      lineNumber: Int,
    ): Either[ToonError, (List[(String, ToonValue)], Int)] =
    if (values.nonEmpty) {
      // Inline array
      parseInlineArrayValues(values, delimiter, lengthOpt, lineNumber)
        .map(arr => (List((key, arr)), 1))
    }
    else if (fieldList != null && fieldList.nonEmpty) {
      // Tabular array
      val (result, consumed) = parseTabularArray(lines, idx + 1, lengthOpt, fieldList, delimiter, depth, lineNumber)
      result.map(arr => (List((key, arr)), consumed + 1))
    }
    else if (lengthOpt.contains(0)) {
      // Empty array
      validateArrayConstraints(lengthOpt, 0, "array", lineNumber).map(_ => (List((key, Arr.empty)), 1))
    }
    else {
      // List array
      val (result, consumed) = parseListArray(lines, idx + 1, lengthOpt, depth, lineNumber)
      result.map(arr => (List((key, arr)), consumed + 1))
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
    val headerMatch = """^(.+?)\[(\d+|\?)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r
    content match {
      case headerMatch(keyPart, lengthStr, delimSymbol, _, values) =>
        for {
          unquotedKey <- unquoteKey(keyPart.trim, lineNumber)
          lengthOpt   <- parseDeclaredLength(lengthStr, lineNumber)
          delimiter    = parseDelimiter(delimSymbol)
          result      <-
            if (values.nonEmpty)
              parseInlineArrayValues(values, delimiter, lengthOpt, lineNumber).map(arr => (unquotedKey, arr))
            else
              validateArrayConstraints(lengthOpt, 0, "array", lineNumber).map(_ => (unquotedKey, Arr.empty))
        } yield result

      case _ =>
        Left(ParseError(s"Invalid array format at line $lineNumber"))
    }
  }

  /** Parse an array from root or nested context. Pure function using pattern matching.
    */
  private def parseArray(
      lines: Chunk[Line],
      startIdx: Int,
      keyOpt: Option[String],
    ): (Either[ToonError, Arr], Int) = {
    val line    = lines(startIdx)
    val content = line.content

    // Pattern for arrays with optional key
    val ArrayPattern = """^(?:(.+?))?\[(\d+|\?)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r

    content match {
      case ArrayPattern(key, lengthStr, delimSymbol, fieldList, values) =>
        parseDeclaredLength(lengthStr, line.lineNumber) match {
          case Left(error)      => (Left(error), 1)
          case Right(lengthOpt) =>
            val delimiter = parseDelimiter(delimSymbol)
            parseArrayByFormat(lines, startIdx, lengthOpt, delimiter, fieldList, values, line)
        }

      case _ =>
        (Left(ParseError(s"Invalid array header at line ${line.lineNumber}")), 1)
    }
  }

  /** Route to the appropriate array parser based on format. Pure helper function.
    */
  private def parseArrayByFormat(
      lines: Chunk[Line],
      startIdx: Int,
      lengthOpt: Option[Int],
      delimiter: Delimiter,
      fieldList: String,
      values: String,
      line: Line,
    ): (Either[ToonError, Arr], Int) =
    if (values.nonEmpty) {
      // Inline array
      val result = parseInlineArrayValues(values, delimiter, lengthOpt, line.lineNumber)
      (result, 1)
    }
    else if (fieldList != null && fieldList.nonEmpty) {
      // Tabular array
      parseTabularArray(lines, startIdx + 1, lengthOpt, fieldList, delimiter, line.depth, line.lineNumber)
    }
    else {
      // List array
      parseListArray(lines, startIdx + 1, lengthOpt, line.depth, line.lineNumber)
    }

  /** Parse array content (used when nested). Pure tail-recursive function.
    */
  private def parseArrayContent(
      lines: Chunk[Line],
      startIdx: Int,
      expectedDepth: Int,
    ): (Either[ToonError, Arr], Int) =
    if (startIdx >= lines.length) {
      (Right(Arr.empty), 0)
    }
    else {
      val firstLine = lines(startIdx)
      if (firstLine.content.startsWith("-")) {
        // List array - parse items recursively
        @scala.annotation.tailrec
        def loop(idx: Int, items: List[ToonValue]): (Either[ToonError, List[ToonValue]], Int) =
          if (idx >= lines.length || lines(idx).depth < expectedDepth) {
            (Right(items), idx - startIdx)
          }
          else if (lines(idx).depth == expectedDepth && lines(idx).content.startsWith("-")) {
            val content = lines(idx).content.substring(1).trim
            parsePrimitive(content, lines(idx).lineNumber) match {
              case Left(err)   => (Left(err), idx - startIdx)
              case Right(prim) => loop(idx + 1, items :+ prim)
            }
          }
          else {
            loop(idx + 1, items)
          }

        val (result, consumed) = loop(startIdx, List.empty)
        (result.map(items => Arr(Chunk.fromIterable(items))), consumed)
      }
      else {
        (Left(ParseError(s"Unsupported array format at line ${firstLine.lineNumber}")), 0)
      }
    }

  /** Parse inline array. Simplified with extracted header parsing and helper reuse.
    */
  private def parseInlineArray(content: String, lineNumber: Int): Either[ToonError, Arr] =
    parseInlineArrayHeader(content, lineNumber).flatMap {
      case (length, delimiter, values) =>
        parseInlineArrayValues(values, delimiter, length, lineNumber)
    }

  /** Parse inline array header. Extracted method for header parsing logic.
    */
  private def parseInlineArrayHeader(
      content: String,
      lineNumber: Int,
    ): Either[ToonError, (Option[Int], Delimiter, String)] = {
    val headerPattern = """^\[(\d+|\?)([|\t]?)\]:\s*(.*)$""".r

    content match {
      case headerPattern(lengthStr, delimSymbol, values) =>
        parseDeclaredLength(lengthStr, lineNumber).map { length =>
          val delimiter = parseDelimiter(delimSymbol)
          (length, delimiter, values)
        }

      case _ =>
        Left(ParseError(s"Invalid inline array format at line $lineNumber"))
    }
  }

  /** Parse inline array values. Pure function with extracted validation.
    */
  private def parseInlineArrayValues(
      values: String,
      delimiter: Delimiter,
      expectedLength: Option[Int],
      lineNumber: Int,
    ): Either[ToonError, Arr] = {
    // Handle empty array case early
    if (values.isEmpty && expectedLength.contains(0)) {
      return validateArrayConstraints(expectedLength, 0, "inline array", lineNumber).map(_ => Arr.empty)
    }

    val split = splitByDelimiter(values, delimiter.char)

    for {
      _          <- validateArrayConstraints(expectedLength, split.length, "inline array", lineNumber)
      primitives <- parseArrayPrimitives(split, lineNumber)
    } yield Arr(Chunk.fromIterable(primitives))
  }

  private def validateArrayConstraints(
      expectedLength: Option[Int],
      actualLength: Int,
      arrayType: String,
      lineNumber: Int,
    ): Either[ToonError, Unit] =
    for {
      _ <- ensureArrayLimit(actualLength, arrayType, lineNumber)
      _ <- expectedLength match {
             case Some(expected) => validateArrayLength(actualLength, expected, arrayType, lineNumber)
             case None           => Right(())
           }
    } yield ()

  /** Validate array length matches expected length in strict mode. Extracted validation logic.
    */
  private def validateArrayLength(
      actualLength: Int,
      expectedLength: Int,
      arrayType: String,
      lineNumber: Int,
    ): Either[ToonError, Unit] =
    if (config.strictMode && actualLength != expectedLength) {
      Left(CountMismatch(expectedLength, actualLength, arrayType, lineNumber))
    }
    else {
      Right(())
    }

  private def ensureArrayLimit(length: Int, context: String, lineNumber: Int): Either[ToonError, Unit] =
    config.maxArrayLength match {
      case Some(limit) if length > limit =>
        Left(ArrayLengthLimitExceeded(limit, length, context, lineNumber))
      case _                             => Right(())
    }

  private def ensureDepthWithinLimit(depth: Int, lineNumber: Int): Either[ToonError, Unit] =
    config.maxDepth match {
      case Some(limit) if depth > limit => Left(DepthLimitExceeded(limit, lineNumber))
      case _                            => Right(())
    }

  private def ensureStringWithinLimit(value: String, lineNumber: Int): Either[ToonError, String] =
    config.maxStringLength match {
      case Some(limit) if value.length > limit => Left(StringTooLong(limit, value.length, lineNumber))
      case _                                   => Right(value)
    }

  /** Parse array of primitive values. Extracted parsing logic using foldLeft.
    */
  private def parseArrayPrimitives(
      values: Array[String],
      lineNumber: Int,
    ): Either[ToonError, List[Primitive]] =
    values.foldLeft[Either[ToonError, List[Primitive]]](Right(List.empty)) {
      case (Left(error), _)    => Left(error)
      case (Right(acc), value) =>
        parsePrimitive(value.trim, lineNumber).map(prim => acc :+ prim)
    }

  /** Parse tabular array. Pure functional implementation using foldLeft and recursion.
    */
  private def parseTabularArray(
      lines: Chunk[Line],
      startIdx: Int,
      expectedLength: Option[Int],
      fieldList: String,
      delimiter: Delimiter,
      headerDepth: Int,
      headerLineNumber: Int,
    ): (Either[ToonError, Arr], Int) = {
    // Parse field names using foldLeft instead of mutable buffer
    val fieldParts   = splitByDelimiter(fieldList, delimiter.char)
    val fieldsResult = fieldParts.foldLeft[Either[ToonError, List[String]]](Right(List.empty)) {
      case (Left(error), _)        => Left(error)
      case (Right(acc), fieldPart) =>
        val trimmed = fieldPart.trim
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
          unescape(trimmed.substring(1, trimmed.length - 1)) match {
            case Left(msg) =>
              val lineNumber = if (startIdx < lines.length) lines(startIdx).lineNumber else headerLineNumber
              Left(InvalidEscape(msg, lineNumber))
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
                  val row = Obj(VectorMap.from(fieldValues))
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
            val lineNumber = if (startIdx < lines.length) lines(startIdx).lineNumber else headerLineNumber
            validateArrayConstraints(expectedLength, rows.length, "tabular array", lineNumber) match {
              case Left(err) => (Left(err), consumed)
              case Right(_)  => (Right(Arr(Chunk.fromIterable(rows))), consumed)
            }
        }
    }
  }

  /** Parse list array. Pure functional implementation using tail recursion.
    */
  private def parseListArray(
      lines: Chunk[Line],
      startIdx: Int,
      expectedLength: Option[Int],
      headerDepth: Int,
      headerLineNumber: Int,
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
        val lineNumber = if (startIdx < lines.length) lines(startIdx).lineNumber else headerLineNumber
        validateArrayConstraints(expectedLength, items.length, "list array", lineNumber) match {
          case Left(err) => (Left(err), consumed)
          case Right(_)  => (Right(Arr(Chunk.fromIterable(items))), consumed)
        }
    }
  }

  /** Parse a single list item. Pure function using for-comprehension.
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
        parsePrimitive(content, line.lineNumber).map(prim => (prim, 1))
      }
      else {
        parseListItemWithField(lines, idx, content, colonIdx, line.lineNumber)
      }
    }
  }

  /** Parse a list item with a field (key: value). Helper to reduce complexity and use unquoteKey helper.
    */
  private def parseListItemWithField(
      lines: Chunk[Line],
      idx: Int,
      content: String,
      colonIdx: Int,
      lineNumber: Int,
    ): Either[ToonError, (ToonValue, Int)] = {
    val key      = content.substring(0, colonIdx).trim
    val valueStr = content.substring(colonIdx + 1).trim

    for {
      unquotedKey <- unquoteKey(key, lineNumber)
      result      <- if (valueStr.isEmpty) {
                       val (objResult, consumed) = parseObject(lines, idx + 1)
                       objResult.map(obj => (obj, consumed + 1))
                     }
                     else {
                       parsePrimitive(valueStr, lineNumber).map(prim => (Obj((unquotedKey, prim)), 1))
                     }
    } yield result
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
