package io.github.riccardomerolla.ziotoon

import ToonValue._
import ToonError._
import zio.Chunk
import scala.util.boundary
import scala.util.boundary.break

/**
 * Decoder for parsing TOON format strings into ToonValue.
 *
 * Uses Scala 3's boundary/break for early returns following modern Scala 3 practices.
 */
class ToonDecoder(config: DecoderConfig = DecoderConfig.default) {
  
  import StringUtils._
  
  /**
   * Line with metadata for parsing.
   */
  private case class Line(content: String, depth: Int, lineNumber: Int, indent: Int)
  
  /**
   * Decode a TOON format string into a ToonValue.
   *
   * Following ZIO best practices: all errors are in the error channel,
   * no exceptions are thrown.
   *
   * Uses Scala 3's boundary/break for clean early exits.
   */
  def decode(input: String): Either[ToonError, ToonValue] = boundary {
    if (input.trim.isEmpty) {
      // Empty input returns null per spec
      break(Right(Null))
    }
    
    // Parse lines with indentation - handle errors in Either
    parseLines(input) match {
      case Left(error) => break(Left(error))
      case Right(lines) =>
        if (lines.isEmpty) {
          break(Right(Null))
        }

        // Determine root form
        val firstLine = lines.head
        // Check if it's a keyed array (e.g., "users[2]{...}:" or "tags[3]:")
        if (firstLine.content.matches("^.+\\[\\d+.*\\]:.*") && firstLine.content.contains("[")) {
          // This is an object with array field at root
          parseObject(lines, 0)._1
        } else if (firstLine.content.matches("^\\[\\d+.*\\]:.*")) {
          // Array at root without key
          parseArray(lines, 0, None)._1
        } else if (firstLine.content.contains(":")) {
          // Object at root
          parseObject(lines, 0)._1
        } else {
          // Single primitive value
          parsePrimitive(firstLine.content, firstLine.lineNumber)
        }
    }
  }
  
  /**
   * Parse lines with indentation information.
   * Returns Either to handle indentation errors without throwing.
   * Uses Scala 3's boundary/break for clean error handling.
   */
  private def parseLines(input: String): Either[ToonError, Chunk[Line]] = boundary {
    val rawLines = input.split("\n")
    val lines = scala.collection.mutable.ArrayBuffer[Line]()

    var idx = 0
    while (idx < rawLines.length) {
      val line = rawLines(idx)
      if (line.trim.nonEmpty) {
        val indent = line.takeWhile(_ == ' ').length
        val depth = indent / config.indentSize
        
        // Strict mode: check indentation
        if (config.strictMode && indent % config.indentSize != 0) {
          break(Left(IndentationError(
            s"Indentation must be a multiple of ${config.indentSize} spaces",
            idx + 1
          )))
        }
        
        lines += Line(line.trim, depth, idx + 1, indent)
      }
      idx += 1
    }

    Right(Chunk.fromIterable(lines))
  }
  
  /**
   * Parse a primitive value from a string.
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
        case Left(msg) => Left(InvalidEscape(msg, lineNumber))
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
      } catch {
        case _: NumberFormatException => Right(Str(trimmed))
      }
    }
    // Otherwise it's an unquoted string
    else {
      Right(Str(trimmed))
    }
  }
  
  /**
   * Parse an object from lines starting at given index.
   * Returns (result, number of lines consumed).
   */
  private def parseObject(lines: Chunk[Line], startIdx: Int): (Either[ToonError, Obj], Int) = {
    val startDepth = if (startIdx < lines.length) lines(startIdx).depth else 0
    var idx = startIdx
    val fields = scala.collection.mutable.ArrayBuffer[(String, ToonValue)]()
    
    while (idx < lines.length && lines(idx).depth >= startDepth) {
      val line = lines(idx)
      
      // Stop if we've gone back to a shallower depth
      if (line.depth < startDepth) {
        return (Right(Obj(Chunk.fromIterable(fields))), idx - startIdx)
      }
      
      // Only process lines at exactly this depth
      if (line.depth == startDepth) {
        val content = line.content
        
        // Check for list item marker
        if (content.startsWith("-")) {
          // This shouldn't happen in an object context
          return (Left(ParseError(s"Unexpected list item marker in object at line ${line.lineNumber}")), idx - startIdx)
        }
        
        // Parse key-value pair
        val colonIdx = findUnquotedColon(content)
        if (colonIdx < 0) {
          if (config.strictMode) {
            return (Left(MissingColon(line.lineNumber)), idx - startIdx)
          }
          // In non-strict mode, skip the line
          idx += 1
        } else {
          // Check if this is an array header (key[N]: ...)
          // Use a more specific regex that doesn't over-match
          if (content.matches("^.+?\\[\\d+[|\\t]?\\](?:\\{.+?\\})?:.*")) {
            // Extract the array header info
            val headerMatch = """^(.+?)\[(\d+)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r
            content match {
              case headerMatch(keyPart, lengthStr, delimSymbol, fieldList, values) =>
                val key = keyPart.trim
                val unquotedKey = if (key.startsWith("\"") && key.endsWith("\"")) {
                  unescape(key.substring(1, key.length - 1)) match {
                    case Left(msg) => return (Left(InvalidEscape(msg, line.lineNumber)), idx - startIdx)
                    case Right(k) => k
                  }
                } else {
                  key
                }
                
                val length = lengthStr.toInt
                val delimiter = if (delimSymbol == "\t") Delimiter.Tab
                               else if (delimSymbol == "|") Delimiter.Pipe
                               else Delimiter.Comma
                
                if (values.nonEmpty) {
                  // Inline array
                  parseInlineArrayValues(values, delimiter, length, line.lineNumber) match {
                    case Left(err) => return (Left(err), idx - startIdx)
                    case Right(arr) =>
                      fields += ((unquotedKey, arr))
                      idx += 1
                  }
                } else if (fieldList != null && fieldList.nonEmpty) {
                  // Tabular array with child rows
                  val (result, consumed) = parseTabularArray(lines, idx + 1, length, fieldList, delimiter, line.depth)
                  result match {
                    case Left(err) => return (Left(err), idx - startIdx)
                    case Right(arr) =>
                      fields += ((unquotedKey, arr))
                      idx += consumed + 1
                  }
                } else if (length == 0) {
                  // Empty array
                  fields += ((unquotedKey, Arr.empty))
                  idx += 1
                } else {
                  // List array with child items
                  val (result, consumed) = parseListArray(lines, idx + 1, length, line.depth)
                  result match {
                    case Left(err) => return (Left(err), idx - startIdx)
                    case Right(arr) =>
                      fields += ((unquotedKey, arr))
                      idx += consumed + 1
                  }
                }
                
              case _ =>
                return (Left(ParseError(s"Invalid array header at line ${line.lineNumber}")), idx - startIdx)
            }
          } else {
            val colonIdx = findUnquotedColon(content)
            if (colonIdx < 0) {
              if (config.strictMode) {
                return (Left(MissingColon(line.lineNumber)), idx - startIdx)
              }
              // In non-strict mode, skip the line
              idx += 1
            } else {
              val key = content.substring(0, colonIdx).trim
              val unquotedKey = if (key.startsWith("\"") && key.endsWith("\"")) {
                unescape(key.substring(1, key.length - 1)) match {
                  case Left(msg) => return (Left(InvalidEscape(msg, line.lineNumber)), idx - startIdx)
                  case Right(k) => k
                }
              } else {
                key
              }
              
              val valueStr = content.substring(colonIdx + 1).trim
              
              if (valueStr.isEmpty) {
                // Nested object or array
                idx += 1
                
                // Check if next line is an array
                if (idx < lines.length && lines(idx).depth == startDepth + 1) {
                  val nextLine = lines(idx)
                  if (nextLine.content.matches("^-.*") || unquotedKey.matches(".*\\[\\d+.*\\]:?$")) {
                    // Array
                    parseArrayContent(lines, idx, startDepth + 1) match {
                      case (Left(err), _) => return (Left(err), idx - startIdx)
                      case (Right(arr), consumed) =>
                        fields += ((unquotedKey, arr))
                        idx += consumed
                    }
                  } else {
                    // Nested object
                    parseObject(lines, idx) match {
                      case (Left(err), _) => return (Left(err), idx - startIdx)
                      case (Right(obj), consumed) =>
                        fields += ((unquotedKey, obj))
                        idx += consumed
                    }
                  }
                } else {
                  // Empty object
                  fields += ((unquotedKey, Obj.empty))
                }
              } else {
                // Inline value - not an array header
                parsePrimitive(valueStr, line.lineNumber) match {
                  case Left(err) => return (Left(err), idx - startIdx)
                  case Right(prim) => 
                    fields += ((unquotedKey, prim))
                    idx += 1
                }
              }
            }
          }
        }
      } else {
        idx += 1
      }
    }
    
    (Right(Obj(Chunk.fromIterable(fields))), idx - startIdx)
  }
  
  /**
   * Find the index of an unquoted colon in a string.
   */
  private def findUnquotedColon(s: String): Int = {
    var inQuotes = false
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"' => inQuotes = !inQuotes
        case ':' if !inQuotes => return i
        case _ =>
      }
      i += 1
    }
    -1
  }
  
  /**
   * Parse an array line (e.g., "key[3]: v1,v2,v3") and return (key, array).
   */
  private def parseArrayLine(content: String, lineNumber: Int): Either[ToonError, (String, Arr)] = {
    // Extract key and array part
    val headerMatch = """^(.+?)\[(\d+)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r
    content match {
      case headerMatch(keyPart, lengthStr, delimSymbol, fieldList, values) =>
        val key = keyPart.trim
        val unquotedKey = if (key.startsWith("\"") && key.endsWith("\"")) {
          unescape(key.substring(1, key.length - 1)) match {
            case Left(msg) => return Left(InvalidEscape(msg, lineNumber))
            case Right(k) => k
          }
        } else {
          key
        }
        
        val length = lengthStr.toInt
        val delimiter = if (delimSymbol == "\t") Delimiter.Tab
                       else if (delimSymbol == "|") Delimiter.Pipe
                       else Delimiter.Comma
        
        if (values.nonEmpty) {
          // Inline array
          parseInlineArrayValues(values, delimiter, length, lineNumber).map { arr =>
            (unquotedKey, arr)
          }
        } else {
          // Empty array or needs child lines (not supported in this method)
          Right((unquotedKey, Arr.empty))
        }
        
      case _ =>
        Left(ParseError(s"Invalid array format at line $lineNumber"))
    }
  }
  
  /**
   * Parse an array from lines.
   */
  private def parseArray(lines: Chunk[Line], startIdx: Int, keyOpt: Option[String]): (Either[ToonError, Arr], Int) = {
    val line = lines(startIdx)
    val content = line.content
    
    // Extract array header
    val headerMatch = """^(?:(.+?))?\[(\d+)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r
    content match {
      case headerMatch(key, lengthStr, delimSymbol, fieldList, values) =>
        val length = lengthStr.toInt
        val delimiter = if (delimSymbol == "\t") Delimiter.Tab
                       else if (delimSymbol == "|") Delimiter.Pipe
                       else Delimiter.Comma
        
        if (values.nonEmpty) {
          // Inline array
          parseInlineArrayValues(values, delimiter, length, line.lineNumber).map { arr =>
            (Right(arr), 1)
          }.getOrElse((Left(ParseError(s"Failed to parse inline array at line ${line.lineNumber}")), 1))
        } else if (fieldList != null && fieldList.nonEmpty) {
          // Tabular array
          parseTabularArray(lines, startIdx + 1, length, fieldList, delimiter, line.depth)
        } else {
          // List array
          parseListArray(lines, startIdx + 1, length, line.depth)
        }
        
      case _ =>
        (Left(ParseError(s"Invalid array header at line ${line.lineNumber}")), 1)
    }
  }
  
  /**
   * Parse array content (used when nested).
   */
  private def parseArrayContent(lines: Chunk[Line], startIdx: Int, expectedDepth: Int): (Either[ToonError, Arr], Int) = {
    // This is a simplified version - would need more logic for full support
    val firstLine = lines(startIdx)
    if (firstLine.content.startsWith("-")) {
      // List array - count items
      var idx = startIdx
      val items = scala.collection.mutable.ArrayBuffer[ToonValue]()
      
      while (idx < lines.length && lines(idx).depth >= expectedDepth) {
        val line = lines(idx)
        if (line.depth == expectedDepth && line.content.startsWith("-")) {
          val content = line.content.substring(1).trim
          parsePrimitive(content, line.lineNumber) match {
            case Left(err) => return (Left(err), idx - startIdx)
            case Right(prim) => items += prim
          }
        }
        idx += 1
      }
      
      (Right(Arr(Chunk.fromIterable(items))), idx - startIdx)
    } else {
      (Left(ParseError(s"Unsupported array format at line ${firstLine.lineNumber}")), 0)
    }
  }
  
  /**
   * Parse inline array values.
   */
  private def parseInlineArray(content: String, lineNumber: Int): Either[ToonError, Arr] = {
    val headerMatch = """^\[(\d+)([|\t]?)\]:\s*(.*)$""".r
    content match {
      case headerMatch(lengthStr, delimSymbol, values) =>
        val length = lengthStr.toInt
        val delimiter = if (delimSymbol == "\t") Delimiter.Tab
                       else if (delimSymbol == "|") Delimiter.Pipe
                       else Delimiter.Comma
        parseInlineArrayValues(values, delimiter, length, lineNumber)
        
      case _ =>
        Left(ParseError(s"Invalid inline array format at line $lineNumber"))
    }
  }
  
  /**
   * Parse inline array values.
   */
  private def parseInlineArrayValues(values: String, delimiter: Delimiter, expectedLength: Int, lineNumber: Int): Either[ToonError, Arr] = {
    if (values.isEmpty && expectedLength == 0) {
      return Right(Arr.empty)
    }
    
    val split = splitByDelimiter(values, delimiter.char)
    
    if (config.strictMode && split.length != expectedLength) {
      return Left(CountMismatch(expectedLength, split.length, "inline array", lineNumber))
    }
    
    // Build elements without using lambda-return to avoid non-local returns
    val elementsBuf = scala.collection.mutable.ArrayBuffer[Primitive]()
    var i = 0
    while (i < split.length) {
      parsePrimitive(split(i).trim, lineNumber) match {
        case Left(err) => return Left(err)
        case Right(prim) => elementsBuf += prim
      }
      i += 1
    }
    
    Right(Arr(Chunk.fromIterable(elementsBuf)))
  }
  
  /**
   * Parse tabular array.
   */
  private def parseTabularArray(lines: Chunk[Line], startIdx: Int, expectedLength: Int, fieldList: String, delimiter: Delimiter, headerDepth: Int): (Either[ToonError, Arr], Int) = {
    // Build fields array without using map/closure that does non-local return
    val fieldParts = splitByDelimiter(fieldList, delimiter.char)
    val fieldsBuf = scala.collection.mutable.ArrayBuffer[String]()
    var fp = 0
    while (fp < fieldParts.length) {
      val trimmed = fieldParts(fp).trim
      if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
        unescape(trimmed.substring(1, trimmed.length - 1)) match {
          case Left(msg) => return (Left(InvalidEscape(msg, lines(startIdx).lineNumber)), 0)
          case Right(k) => fieldsBuf += k
        }
      } else {
        fieldsBuf += trimmed
      }
      fp += 1
    }
    val fields = fieldsBuf.toArray

    val rowDepth = headerDepth + 1
    var idx = startIdx
    val rows = scala.collection.mutable.ArrayBuffer[Obj]()
    
    while (idx < lines.length && lines(idx).depth >= rowDepth) {
      val line = lines(idx)
      if (line.depth == rowDepth) {
        val values = splitByDelimiter(line.content, delimiter.char)
        
        if (config.strictMode && values.length != fields.length) {
          return (Left(WidthMismatch(fields.length, values.length, line.lineNumber)), idx - startIdx)
        }
        
        // Build fieldValues without map/closure
        val fieldValuesBuf = scala.collection.mutable.ArrayBuffer[(String, Primitive)]()
        var j = 0
        while (j < fields.length && j < values.length) {
          parsePrimitive(values(j).trim, line.lineNumber) match {
            case Left(err) => return (Left(err), idx - startIdx)
            case Right(prim) => fieldValuesBuf += ((fields(j), prim))
          }
          j += 1
        }
        
        rows += Obj(Chunk.fromIterable(fieldValuesBuf))
      }
      idx += 1
    }
    
    if (config.strictMode && rows.length != expectedLength) {
      return (Left(CountMismatch(expectedLength, rows.length, "tabular array", lines(startIdx).lineNumber)), idx - startIdx)
    }
    
    (Right(Arr(Chunk.fromIterable(rows))), idx - startIdx)
  }
  
  /**
   * Parse list array.
   */
  private def parseListArray(lines: Chunk[Line], startIdx: Int, expectedLength: Int, headerDepth: Int): (Either[ToonError, Arr], Int) = {
    val itemDepth = headerDepth + 1
    var idx = startIdx
    val items = scala.collection.mutable.ArrayBuffer[ToonValue]()
    
    while (idx < lines.length && lines(idx).depth >= itemDepth) {
      val line = lines(idx)
      if (line.depth == itemDepth && line.content.startsWith("-")) {
        val content = line.content.substring(1).trim
        
        if (content.isEmpty) {
          // Empty item
          items += Obj.empty
          idx += 1
        } else {
          val colonIdx = findUnquotedColon(content)
          if (colonIdx < 0) {
            // Simple primitive
            parsePrimitive(content, line.lineNumber) match {
              case Left(err) => return (Left(err), idx - startIdx)
              case Right(prim) => items += prim
            }
            idx += 1
          } else {
            // Object item
            val key = content.substring(0, colonIdx).trim
            val valueStr = content.substring(colonIdx + 1).trim
            
            if (valueStr.isEmpty) {
              // Nested structure
              idx += 1
              parseObject(lines, idx) match {
                case (Left(err), _) => return (Left(err), idx - startIdx)
                case (Right(obj), consumed) =>
                  items += obj
                  idx += consumed
              }
            } else {
              // Simple field
              parsePrimitive(valueStr, line.lineNumber) match {
                case Left(err) => return (Left(err), idx - startIdx)
                case Right(prim) =>
                  val unquotedKey = if (key.startsWith("\"") && key.endsWith("\"")) {
                    unescape(key.substring(1, key.length - 1)) match {
                      case Left(msg) => return (Left(InvalidEscape(msg, line.lineNumber)), idx - startIdx)
                      case Right(k) => k
                    }
                  } else {
                    key
                  }
                  items += Obj((unquotedKey, prim))
                  idx += 1
              }
            }
          }
        }
      } else {
        idx += 1
      }
    }
    
    if (config.strictMode && items.length != expectedLength) {
      return (Left(CountMismatch(expectedLength, items.length, "list array", lines(startIdx).lineNumber)), idx - startIdx)
    }
    
    (Right(Arr(Chunk.fromIterable(items))), idx - startIdx)
  }
  
  /**
   * Split a string by delimiter, respecting quoted strings.
   */
  private def splitByDelimiter(s: String, delimiter: Char): Array[String] = {
    val result = scala.collection.mutable.ArrayBuffer[String]()
    val current = new StringBuilder
    var inQuotes = false
    var i = 0
    
    while (i < s.length) {
      val c = s.charAt(i)
      
      if (c == '"') {
        inQuotes = !inQuotes
        current.append(c)
      } else if (c == delimiter && !inQuotes) {
        result += current.toString
        current.clear()
      } else {
        current.append(c)
      }
      
      i += 1
    }
    
    result += current.toString
    result.toArray
  }
}

object ToonDecoder {
  def apply(config: DecoderConfig = DecoderConfig.default): ToonDecoder = 
    new ToonDecoder(config)
  
  /**
   * Convenience method to decode a TOON string.
   */
  def decode(input: String, config: DecoderConfig = DecoderConfig.default): Either[ToonError, ToonValue] = 
    ToonDecoder(config).decode(input)
}
