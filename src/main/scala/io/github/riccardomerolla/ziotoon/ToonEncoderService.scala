package io.github.riccardomerolla.ziotoon

import zio._

/** Service for encoding ToonValue to TOON format strings.
  *
  * This service follows ZIO best practices for effect-oriented programming:
  *   - Effects are pure blueprints describing encoding operations
  *   - No errors in error channel (UIO) - encoding never fails
  *   - Service is provided via ZLayer for dependency injection
  *   - Effects can be composed and transformed
  *
  * ==Usage==
  *
  * Access the service from environment and compose effects:
  *
  * {{{
  * val program = for {
  *   encoded <- ToonEncoderService.encode(value)
  *   _ <- Console.printLine(encoded)
  * } yield encoded
  *
  * program.provide(ToonEncoderService.live)
  * }}}
  *
  * ==Effect Composition==
  *
  * Encoding effects compose naturally with other ZIO effects:
  *
  * {{{
  * val pipeline = for {
  *   value <- loadValue
  *   encoded <- ToonEncoderService.encode(value)
  *   _ <- saveToFile(encoded)
  * } yield encoded
  * }}}
  *
  * ==Custom Configuration==
  *
  * {{{
  * val customConfig = EncoderConfig(
  *   indentSize = 4,
  *   delimiter = Delimiter.Tab
  * )
  *
  * program.provide(ToonEncoderService.configured(customConfig))
  * }}}
  */
trait ToonEncoderService {

  /** Encode a ToonValue to TOON format string.
    *
    * This is an effect that describes the encoding operation. The actual encoding is deferred until the effect is
    * executed.
    *
    * Encoding never fails - it always produces a valid TOON string.
    *
    * @param value
    *   The ToonValue to encode
    * @return
    *   A ZIO effect that produces the encoded string (infallible)
    */
  def encode(value: ToonValue): UIO[String]
}

object ToonEncoderService {

  /** Live implementation of ToonEncoderService.
    *
    * This implementation:
    *   - Uses ToonEncoder internally for formatting
    *   - Wraps pure encoding in ZIO.succeed (infallible)
    *   - Never throws exceptions
    *
    * @param config
    *   The encoder configuration
    */
  final case class Live(config: EncoderConfig) extends ToonEncoderService {
    private val encoder = new ToonEncoder(config)

    def encode(value: ToonValue): UIO[String] =
      ZIO.succeed(encoder.encode(value))
  }

  /** ZLayer that provides a ToonEncoderService with default configuration.
    *
    * This is the recommended layer for most use cases. It uses default configuration (2-space indentation, comma
    * delimiter).
    *
    * @example
    *   {{{
    * val program = ToonEncoderService.encode(value)
    * program.provide(ToonEncoderService.live)
    *   }}}
    */
  val live: ULayer[ToonEncoderService] =
    ZLayer.succeed(Live(EncoderConfig.default))

  /** ZLayer that provides a ToonEncoderService with custom configuration.
    *
    * Use this when you need non-default encoding behavior.
    *
    * @param config
    *   Custom encoder configuration
    * @return
    *   A layer providing the configured encoder service
    *
    * @example
    *   {{{
    * val config = EncoderConfig(indentSize = 4, delimiter = Delimiter.Tab)
    * program.provide(ToonEncoderService.configured(config))
    *   }}}
    */
  def configured(config: EncoderConfig): ULayer[ToonEncoderService] =
    ZLayer.succeed(Live(config))

  /** Accessor method to encode a value using the service from the environment.
    *
    * This is a convenience method that automatically accesses the service from the ZIO environment and calls its encode
    * method.
    *
    * @param value
    *   The ToonValue to encode
    * @return
    *   A ZIO effect requiring ToonEncoderService that encodes the value
    *
    * @example
    *   {{{
    * // Direct use
    * ToonEncoderService.encode(value).provide(ToonEncoderService.live)
    *
    * // In for-comprehension
    * for {
    *   encoded1 <- ToonEncoderService.encode(value1)
    *   encoded2 <- ToonEncoderService.encode(value2)
    * } yield (encoded1, encoded2)
    *   }}}
    */
  def encode(value: ToonValue): ZIO[ToonEncoderService, Nothing, String] =
    ZIO.serviceWithZIO[ToonEncoderService](_.encode(value))
}
