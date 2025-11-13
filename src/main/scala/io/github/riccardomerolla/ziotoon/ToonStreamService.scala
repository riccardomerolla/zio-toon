package io.github.riccardomerolla.ziotoon

import zio._
import zio.stream._

import StringUtils._

/** Service for streaming TOON encoding/decoding of large documents.
  *
  * Following ZIO best practices:
  *   - Uses ZStream for memory-efficient processing
  *   - Backpressure handling built-in
  *   - Composable with other streams
  *   - Resource-safe with automatic cleanup
  *
  * ==Usage==
  *
  * {{{
  * val program = for {
  *   // Stream large array element by element
  *   _ <- ToonStreamService.encodeStream(largeArrayStream)
  *     .run(ZSink.fromFile(path))
  *
  *   // Decode streaming input
  *   values <- ToonStreamService.decodeStream(
  *     ZStream.fromFile(path)
  *   ).runCollect
  * } yield values
  * }}}
  */
trait ToonStreamService {

  /** Encode a stream of ToonValues into a stream of TOON format strings.
    *
    * Each value is encoded independently and emitted as a complete TOON document. Useful for processing large
    * collections without loading everything into memory.
    *
    * @param values
    *   Stream of ToonValues to encode
    * @return
    *   Stream of encoded TOON strings
    */
  def encodeStream(values: ZStream[Any, Nothing, ToonValue]): ZStream[Any, Nothing, String]

  /** Encode an array stream as a single TOON array document.
    *
    * Elements are streamed and assembled into array format incrementally. More efficient than collecting all elements
    * first. Uses `[?]` as the length marker to signal unknown size while preserving TOON compatibility.
    *
    * @param elements
    *   Stream of array elements
    * @param key
    *   Optional key for the array
    * @return
    *   Stream of TOON string chunks forming the array
    */
  def encodeArrayStream(
      elements: ZStream[Any, Nothing, ToonValue],
      key: Option[String] = None,
    ): ZStream[Any, Nothing, String]

  /** Decode a stream of TOON format strings into ToonValues.
    *
    * Each complete TOON document in the stream is decoded independently. Errors are propagated in the error channel.
    *
    * @param input
    *   Stream of TOON strings to decode
    * @return
    *   Stream of decoded ToonValues
    */
  def decodeStream(input: ZStream[Any, Nothing, String]): ZStream[Any, ToonError, ToonValue]

  /** Decode lines from a stream, parsing TOON documents incrementally.
    *
    * Useful for processing line-delimited TOON files or network streams. Handles backpressure automatically.
    *
    * @param lines
    *   Stream of TOON document lines
    * @return
    *   Stream of decoded ToonValues
    */
  def decodeLineStream(lines: ZStream[Any, Nothing, String]): ZStream[Any, ToonError, ToonValue]

  /** Transform a stream through TOON round-trip (encode then decode).
    *
    * Useful for validation or normalization of ToonValue streams.
    *
    * @param values
    *   Stream of ToonValues
    * @return
    *   Stream of round-tripped ToonValues
    */
  def roundTripStream(
      values: ZStream[Any, Nothing, ToonValue]
    ): ZStream[Any, ToonError, ToonValue]
}

object ToonStreamService {

  /** Live implementation of ToonStreamService.
    */
  final case class Live(
      encoderService: ToonEncoderService,
      decoderService: ToonDecoderService,
    ) extends ToonStreamService {

    def encodeStream(values: ZStream[Any, Nothing, ToonValue]): ZStream[Any, Nothing, String] =
      values.mapZIO(value => encoderService.encode(value))

    def encodeArrayStream(
        elements: ZStream[Any, Nothing, ToonValue],
        key: Option[String],
      ): ZStream[Any, Nothing, String] = {
      val encoder       = ToonEncoder(encoderService.config)
      val delimSymbol   = if (encoderService.config.delimiter.isComma) "" else encoderService.config.delimiter.symbol
      val lengthLabel   = "?"
      val headerContent = key match {
        case Some(k) => s"${quoteKeyIfNeeded(k)}[$lengthLabel$delimSymbol]:"
        case None    => s"[$lengthLabel$delimSymbol]:"
      }

      val headerStream = ZStream.succeed(headerContent + "\n")
      val bodyStream   =
        elements
          .flatMap(value => ZStream.fromChunk(encoder.encodeListItem(value, 1)))
          .map(line => line + "\n")

      headerStream ++ bodyStream
    }

    def decodeStream(input: ZStream[Any, Nothing, String]): ZStream[Any, ToonError, ToonValue] =
      input.mapZIO(str => decoderService.decode(str))

    def decodeLineStream(lines: ZStream[Any, Nothing, String]): ZStream[Any, ToonError, ToonValue] =
      // Accumulate lines until we have a complete document
      // For simplicity, assume each chunk of lines separated by empty line is a document
      lines
        .split(_ == "")
        .map(_.mkString("\n"))
        .mapZIO(doc => decoderService.decode(doc))

    def roundTripStream(
        values: ZStream[Any, Nothing, ToonValue]
      ): ZStream[Any, ToonError, ToonValue] =
      values
        .mapZIO(value => encoderService.encode(value))
        .mapZIO(encoded => decoderService.decode(encoded))
  }

  /** ZLayer that provides ToonStreamService.
    */
  val live: ZLayer[ToonEncoderService & ToonDecoderService, Nothing, ToonStreamService] =
    ZLayer.fromFunction((encoder: ToonEncoderService, decoder: ToonDecoderService) => Live(encoder, decoder))

  /** Accessor method for encodeStream.
    */
  def encodeStream(
      values: ZStream[Any, Nothing, ToonValue]
    ): ZStream[ToonStreamService, Nothing, String] =
    ZStream.serviceWithStream[ToonStreamService](_.encodeStream(values))

  /** Accessor method for encodeArrayStream.
    */
  def encodeArrayStream(
      elements: ZStream[Any, Nothing, ToonValue],
      key: Option[String] = None,
    ): ZStream[ToonStreamService, Nothing, String] =
    ZStream.serviceWithStream[ToonStreamService](_.encodeArrayStream(elements, key))

  /** Accessor method for decodeStream.
    */
  def decodeStream(
      input: ZStream[Any, Nothing, String]
    ): ZStream[ToonStreamService, ToonError, ToonValue] =
    ZStream.serviceWithStream[ToonStreamService](_.decodeStream(input))

  /** Accessor method for decodeLineStream.
    */
  def decodeLineStream(
      lines: ZStream[Any, Nothing, String]
    ): ZStream[ToonStreamService, ToonError, ToonValue] =
    ZStream.serviceWithStream[ToonStreamService](_.decodeLineStream(lines))

  /** Accessor method for roundTripStream.
    */
  def roundTripStream(
      values: ZStream[Any, Nothing, ToonValue]
    ): ZStream[ToonStreamService, ToonError, ToonValue] =
    ZStream.serviceWithStream[ToonStreamService](_.roundTripStream(values))
}
