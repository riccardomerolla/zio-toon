package io.github.riccardomerolla.ziotoon

import zio._

/** Main TOON API object with convenience methods.
  *
  * This object provides both pure (Either-based) and effect-based (ZIO) methods following ZIO best practices for
  * effect-oriented programming.
  *
  * ==Overview==
  *
  * The TOON library provides two styles of API:
  *
  *   1. **Pure API**: Direct methods that return Either or plain values
  *   2. **Effect API**: ZIO-based methods that compose through dependency injection
  *
  * ==Pure API Usage==
  *
  * {{{
  * import io.github.riccardomerolla.ziotoon._
  * import ToonValue._
  *
  * // Encode a value
  * val value = obj("name" -> str("Alice"), "age" -> num(30))
  * val toonString = Toon.encode(value)
  *
  * // Decode a string
  * val result: Either[ToonError, ToonValue] = Toon.decode(toonString)
  * }}}
  *
  * ==Effect API Usage==
  *
  * The effect API follows ZIO best practices:
  *   - Effects are immutable blueprints describing operations
  *   - Errors are typed in the error channel
  *   - Dependencies are injected via ZLayer
  *   - Effects compose through for-comprehension
  *
  * {{{
  * import zio._
  *
  * val program = for {
  *   encoded <- Toon.encode(value)
  *   decoded <- Toon.decode(encoded)
  * } yield decoded
  *
  * // Run with dependencies
  * program.provide(Toon.live)
  * }}}
  *
  * ==Custom Configuration==
  *
  * {{{
  * val customLayer = Toon.configured(
  *   encoderConfig = EncoderConfig(indentSize = 4, delimiter = Delimiter.Tab),
  *   decoderConfig = DecoderConfig(strictMode = false)
  * )
  *
  * program.provide(customLayer)
  * }}}
  *
  * ==Error Handling==
  *
  * All errors are typed and handled explicitly in the error channel:
  *
  * {{{
  * val safeProgram = Toon.decode(input).catchAll {
  *   case ToonError.ParseError(msg) =>
  *     ZIO.logError(s"Parse error: $msg") *> ZIO.succeed(ToonValue.Null)
  *   case ToonError.IndentationError(msg, line) =>
  *     ZIO.logError(s"Indentation error at line $line") *> ZIO.fail(error)
  * }
  * }}}
  */
object Toon {

  /** Encode a ToonValue to TOON format string (pure method).
    *
    * This is a pure method that directly executes encoding without side effects. Use this when you don't need
    * dependency injection or effect composition.
    *
    * @param value
    *   The ToonValue to encode
    * @param config
    *   The encoder configuration (default: EncoderConfig.default)
    * @return
    *   The TOON format string
    *
    * @example
    *   {{{
    * val value = ToonValue.obj("name" -> ToonValue.str("Alice"))
    * val toonString = Toon.encode(value)
    * // Result: "name: Alice"
    *   }}}
    */
  def encode(value: ToonValue, config: EncoderConfig = EncoderConfig.default): String =
    new ToonEncoder(config).encode(value)

  /** Decode a TOON format string into a ToonValue (pure method).
    *
    * This is a pure method that directly executes decoding. Returns Either with typed errors in the Left channel.
    *
    * @param input
    *   The TOON format string to decode
    * @param config
    *   The decoder configuration (default: DecoderConfig.default)
    * @return
    *   Either ToonError or ToonValue
    *
    * @example
    *   {{{
    * val result = Toon.decode("name: Alice")
    * result match {
    *   case Right(value) => println(s"Decoded: $value")
    *   case Left(error) => println(s"Error: ${error.message}")
    * }
    *   }}}
    */
  def decode(input: String, config: DecoderConfig = DecoderConfig.default): Either[ToonError, ToonValue] =
    new ToonDecoder(config).decode(input)

  /** Encode a ToonValue to TOON format string using the encoder service (effect method).
    *
    * This method requires ToonEncoderService in the environment. Use this for dependency injection and composable
    * effects.
    *
    * The effect is a pure blueprint - actual encoding is deferred until execution.
    *
    * @param value
    *   The ToonValue to encode
    * @return
    *   A ZIO effect that produces the TOON string
    *
    * @example
    *   {{{
    * val program = for {
    *   encoded <- Toon.encode(value)
    *   _ <- Console.printLine(encoded)
    * } yield ()
    *
    * program.provide(ToonEncoderService.live, Console.live)
    *   }}}
    */
  def encode(value: ToonValue): ZIO[ToonEncoderService, Nothing, String] =
    ToonEncoderService.encode(value)

  /** Decode a TOON format string using the decoder service (effect method).
    *
    * This method requires ToonDecoderService in the environment. Use this for dependency injection and composable
    * effects.
    *
    * Errors are typed in the error channel - no exceptions are thrown.
    *
    * @param input
    *   The TOON format string to decode
    * @return
    *   A ZIO effect that either fails with ToonError or succeeds with ToonValue
    *
    * @example
    *   {{{
    * val program = Toon.decode(input).catchAll {
    *   case ToonError.ParseError(msg) =>
    *     ZIO.logWarning(s"Parse failed: $msg") *> ZIO.succeed(ToonValue.Null)
    *   case error =>
    *     ZIO.fail(error)
    * }
    *
    * program.provide(ToonDecoderService.live)
    *   }}}
    */
  def decode(input: String): ZIO[ToonDecoderService, ToonError, ToonValue] =
    ToonDecoderService.decode(input)

  /** Round-trip encode and decode using services from environment.
    *
    * This demonstrates ZIO best practices for effect composition:
    *   - Effects are composed through for-comprehension
    *   - Each effect is a pure blueprint
    *   - Dependencies are provided via environment
    *   - Errors propagate through the error channel
    *
    * @param value
    *   The ToonValue to round-trip
    * @return
    *   A ZIO effect that either fails with ToonError or succeeds with decoded ToonValue
    *
    * @example
    *   {{{
    * val original = ToonValue.obj(
    *   "name" -> ToonValue.str("Alice"),
    *   "age" -> ToonValue.num(30)
    * )
    *
    * val program = for {
    *   roundTripped <- Toon.roundTrip(original)
    *   _ <- ZIO.logInfo(s"Original == RoundTripped: ${original == roundTripped}")
    * } yield roundTripped
    *
    * program.provide(Toon.live)
    *   }}}
    */
  def roundTrip(value: ToonValue): ZIO[ToonEncoderService & ToonDecoderService, ToonError, ToonValue] =
    for {
      encoded <- ToonEncoderService.encode(value)
      decoded <- ToonDecoderService.decode(encoded)
    } yield decoded

  /** Default application layer that provides both encoder and decoder services.
    *
    * This follows ZIO best practices for service composition:
    *   - Services are provided via ZLayer
    *   - Layers are composed with `++`
    *   - Resources are automatically managed
    *
    * @example
    *   {{{
    * val program: ZIO[ToonEncoderService & ToonDecoderService, ToonError, String] =
    *   for {
    *     encoded <- Toon.encode(value)
    *     decoded <- Toon.decode(encoded)
    *   } yield encoded
    *
    * program.provide(Toon.live)
    *   }}}
    */
  val live: ULayer[ToonEncoderService & ToonDecoderService] =
    ToonEncoderService.live ++ ToonDecoderService.live

  /** Application layer with custom configuration.
    *
    * Use this when you need non-default encoding/decoding behavior. Configuration is provided at layer construction
    * time.
    *
    * @param encoderConfig
    *   Custom encoder configuration
    * @param decoderConfig
    *   Custom decoder configuration
    * @return
    *   A layer providing configured encoder and decoder services
    *
    * @example
    *   {{{
    * val customLayer = Toon.configured(
    *   encoderConfig = EncoderConfig(
    *     indentSize = 4,
    *     delimiter = Delimiter.Tab
    *   ),
    *   decoderConfig = DecoderConfig(
    *     strictMode = false
    *   )
    * )
    *
    * program.provide(customLayer)
    *   }}}
    */
  def configured(
      encoderConfig: EncoderConfig = EncoderConfig.default,
      decoderConfig: DecoderConfig = DecoderConfig.default,
    ): ULayer[ToonEncoderService & ToonDecoderService] =
    ToonEncoderService.configured(encoderConfig) ++ ToonDecoderService.configured(decoderConfig)
}
