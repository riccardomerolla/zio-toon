package io.github.riccardomerolla.ziotoon

import zio.Chunk
import zio.ZIO
import zio.stream.ZStream

import ToonError._
import io.github.riccardomerolla.ziotoon.StringUtils
import scala.annotation.tailrec

/** Streaming helpers for decoding TOON tabular arrays without materialising full ASTs.
  *
  * This module exposes ZStream-based processors that emit tabular rows as soon as they are parsed, maintaining constant
  * memory usage regardless of array size.
  */
object ToonStreaming {

  /** Tabular row emitted by [[tabularRows]].
    *
    * @param key
    *   Optional field name that owns the array (None when the array is at the root)
    * @param fields
    *   Column names declared in the array header
    * @param values
    *   Raw cell values (already unescaped) for the row
    * @param lineNumber
    *   Source line number where the row was parsed (1-based)
    */
  final case class TabularRow(
      key: Option[String],
      fields: Chunk[String],
      values: Chunk[String],
      lineNumber: Int,
    )

  private val ArrayHeaderPattern = """^(.+?)\[(\d+|\?)([|\t]?)\](?:\{(.+?)\})?:\s*(.*)$""".r

  final private case class ParsedLine(content: String, depth: Int, lineNumber: Int, indent: Int)

  final private case class HeaderState(
      key: Option[String],
      fields: Chunk[String],
      delimiter: Delimiter,
      expectedLength: Option[Int],
      baseDepth: Int,
      rowsEmitted: Int,
      headerLine: Int,
    )

  final private case class StreamState(
      lineNumber: Int,
      activeHeader: Option[HeaderState],
    )

  /** Emits tabular rows as they are encountered in the incoming line stream.
    *
    * Lines should be provided without trailing newline characters. The decoder honours the supplied [[DecoderConfig]],
    * sharing strict-mode rules and guard-rail limits with the regular decoder.
    *
    * @param lines
    *   Stream of TOON lines (one per element)
    * @param config
    *   Decoder configuration controlling indentation rules and guard rails
    */
  def tabularRows(
      lines: ZStream[Any, Nothing, String],
      config: DecoderConfig = DecoderConfig.default,
    ): ZStream[Any, ToonError, TabularRow] = {
    val initialState = StreamState(lineNumber = 0, activeHeader = None)
    val withSentinel = lines.map(Some(_)) ++ ZStream.succeed(None)

    withSentinel
      .mapAccumZIO(initialState) {
        case (state, maybeLine) =>
          ZIO.fromEither(processLine(state, maybeLine, config))
      }
      .mapConcat(identity)
  }

  private def processLine(
      state: StreamState,
      maybeLine: Option[String],
      config: DecoderConfig,
    ): Either[ToonError, (StreamState, Chunk[TabularRow])] =
    maybeLine match {
      case Some(rawLine) =>
        val nextLineNumber = state.lineNumber + 1
        parseLine(rawLine, nextLineNumber, config).flatMap {
          case None       =>
            // Blank line: in strict mode this is invalid within arrays
            state.activeHeader match {
              case Some(_) if config.strictMode =>
                Left(BlankLineInArray(nextLineNumber))
              case Some(_)                      =>
                finishHeader(state.copy(lineNumber = nextLineNumber), config, nextLineNumber).map(_ -> Chunk.empty)
              case None                         =>
                Right(state.copy(lineNumber = nextLineNumber) -> Chunk.empty)
            }
          case Some(line) =>
            handleParsedLine(state.copy(lineNumber = nextLineNumber), line, config)
        }

      case None =>
        finishHeader(state, config, math.max(1, state.lineNumber)).map(_ -> Chunk.empty)
    }

  private def handleParsedLine(
      state: StreamState,
      line: ParsedLine,
      config: DecoderConfig,
    ): Either[ToonError, (StreamState, Chunk[TabularRow])] =
    state.activeHeader match {
      case Some(header) if line.depth == header.baseDepth + 1 =>
        parseRow(header, line, config).map {
          case (updatedHeader, row) =>
            (state.copy(activeHeader = Some(updatedHeader)), Chunk.single(row))
        }

      case Some(header) if line.depth <= header.baseDepth =>
        finishHeader(state, config, line.lineNumber).flatMap { cleared =>
          handleParsedLine(cleared, line, config)
        }

      case _ =>
        detectHeader(line, config).map {
          case Some(headerState) => (state.copy(activeHeader = Some(headerState)), Chunk.empty)
          case None              => (state, Chunk.empty)
        }
    }

  private def detectHeader(line: ParsedLine, config: DecoderConfig): Either[ToonError, Option[HeaderState]] =
    line.content match {
      case ArrayHeaderPattern(rawKey, lengthStr, delimSymbol, fieldList, values)
           if fieldList != null && fieldList.nonEmpty && values.isEmpty =>
        for {
          key      <- unquoteKey(rawKey.trim, line.lineNumber)
          length   <- parseDeclaredLength(lengthStr, line.lineNumber, config)
          delimiter = parseDelimiter(delimSymbol)
          fields   <- parseHeaderFields(fieldList, line.lineNumber)
        } yield Some(
          HeaderState(key, fields, delimiter, length, line.depth, rowsEmitted = 0, headerLine = line.lineNumber)
        )

      case _ =>
        Right(None)
    }

  private def parseRow(
      header: HeaderState,
      line: ParsedLine,
      config: DecoderConfig,
    ): Either[ToonError, (HeaderState, TabularRow)] = {
    val rawValues = splitByDelimiter(line.content, header.delimiter.char)

    if (config.strictMode && rawValues.length != header.fields.length) {
      Left(WidthMismatch(header.fields.length, rawValues.length, line.lineNumber))
    }
    else {
      val parsedValues = Chunk
        .fromIterable(rawValues.take(header.fields.length))
        .zipWithIndex
        .foldLeft[Either[ToonError, Chunk[String]]](Right(Chunk.empty)) {
          case (Left(error), _)         => Left(error)
          case (Right(acc), (value, _)) =>
            parseCellValue(value.trim, line.lineNumber).map(parsed => acc :+ parsed)
        }

      parsedValues.map { values =>
        val updated = header.copy(rowsEmitted = header.rowsEmitted + 1)
        val row     = TabularRow(header.key, header.fields, values, line.lineNumber)
        (updated, row)
      }
    }
  }

  private def finishHeader(
      state: StreamState,
      config: DecoderConfig,
      lineNumber: Int,
    ): Either[ToonError, StreamState] =
    state.activeHeader match {
      case Some(header) =>
        header.expectedLength match {
          case Some(expected) if config.strictMode && expected != header.rowsEmitted =>
            Left(CountMismatch(expected, header.rowsEmitted, "tabular array", lineNumber))
          case _                                                                     =>
            Right(state.copy(activeHeader = None))
        }
      case None         => Right(state)
    }

  private def parseDeclaredLength(
      lengthStr: String,
      lineNumber: Int,
      config: DecoderConfig,
    ): Either[ToonError, Option[Int]] =
    if (lengthStr == "?") Right(None)
    else
      scala
        .util
        .Try(lengthStr.toInt)
        .toEither
        .left
        .map(_ => ParseError(s"Invalid array length '$lengthStr' at line $lineNumber"))
        .flatMap { value =>
          config.maxArrayLength match {
            case Some(limit) if value > limit =>
              Left(ArrayLengthLimitExceeded(limit, value, "tabular array", lineNumber))
            case _                            =>
              Right(Some(value))
          }
        }

  private def parseDelimiter(symbol: String): Delimiter =
    if (symbol == "\t") Delimiter.Tab
    else if (symbol == "|") Delimiter.Pipe
    else Delimiter.Comma

  private def parseHeaderFields(fieldList: String, lineNumber: Int): Either[ToonError, Chunk[String]] = {
    val rawFields = fieldList.split(",").map(_.trim)

    rawFields.foldLeft[Either[ToonError, Chunk[String]]](Right(Chunk.empty)) {
      case (Left(error), _)    => Left(error)
      case (Right(acc), field) =>
        if (field.startsWith("\"") && field.endsWith("\"") && field.length >= 2) {
          StringUtils.unescape(field.substring(1, field.length - 1)) match {
            case Left(msg) => Left(InvalidEscape(msg, lineNumber))
            case Right(k)  => Right(acc :+ k)
          }
        }
        else Right(acc :+ field)
    }
  }

  private def parseCellValue(value: String, lineNumber: Int): Either[ToonError, String] =
    if (value.startsWith("\"")) {
      if (!value.endsWith("\"") || value.length < 2) Left(UnterminatedString(lineNumber))
      else StringUtils.unescape(value.substring(1, value.length - 1)).left.map(msg => InvalidEscape(msg, lineNumber))
    }
    else Right(value)

  private def parseLine(
      raw: String,
      lineNumber: Int,
      config: DecoderConfig,
    ): Either[ToonError, Option[ParsedLine]] = {
    val trimmed = raw.trim
    if (trimmed.isEmpty) Right(None)
    else {
      val indent = raw.takeWhile(_ == ' ').length
      if (config.strictMode && indent % config.indentSize != 0) {
        Left(
          IndentationError(
            s"Indentation must be a multiple of ${config.indentSize} spaces",
            lineNumber,
          )
        )
      }
      else {
        val depth = indent / config.indentSize
        config.maxDepth match {
          case Some(limit) if depth > limit => Left(DepthLimitExceeded(limit, lineNumber))
          case _                            => Right(Some(ParsedLine(trimmed, depth, lineNumber, indent)))
        }
      }
    }
  }

  private def unquoteKey(key: String, lineNumber: Int): Either[ToonError, Option[String]] =
    if (key.isEmpty) Right(None)
    else if (key.startsWith("\"") && key.endsWith("\"") && key.length >= 2)
      StringUtils
        .unescape(key.substring(1, key.length - 1))
        .left
        .map(msg => InvalidEscape(msg, lineNumber))
        .map(value => Some(value))
    else if (key == "-") Right(None)
    else Right(Some(key))

  private def splitByDelimiter(line: String, delimiter: Char): Array[String] = {
    @tailrec
    def loop(idx: Int, inQuotes: Boolean, acc: List[String], builder: StringBuilder): List[String] =
      if (idx >= line.length) acc :+ builder.toString
      else
        line.charAt(idx) match {
          case '"'                                      =>
            loop(idx + 1, !inQuotes, acc, builder.append('"'))
          case value if value == delimiter && !inQuotes =>
            loop(idx + 1, inQuotes, acc :+ builder.toString, new StringBuilder)
          case char                                     =>
            loop(idx + 1, inQuotes, acc, builder.append(char))
        }

    loop(0, inQuotes = false, Nil, new StringBuilder).toArray
  }
}
