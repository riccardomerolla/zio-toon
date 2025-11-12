package io.github.riccardomerolla.ziotoon

import zio._

/**
 * Service for decoding TOON format strings into ToonValue.
 * 
 * This service follows ZIO best practices for effect-oriented programming:
 * - Effects are pure blueprints describing decoding operations
 * - Errors are typed in the error channel (ToonError)
 * - No exceptions are thrown
 * - Service is provided via ZLayer for dependency injection
 * 
 * ==Usage==
 * 
 * Access the service from environment and compose effects:
 * 
 * {{{
 * val program = for {
 *   decoded <- ToonDecoderService.decode(input)
 *   _ <- Console.printLine(s"Decoded: $decoded")
 * } yield decoded
 * 
 * program.provide(ToonDecoderService.live)
 * }}}
 * 
 * ==Error Handling==
 * 
 * All decoding errors are typed and can be handled explicitly:
 * 
 * {{{
 * ToonDecoderService.decode(input).catchAll {
 *   case ToonError.ParseError(msg) => 
 *     ZIO.logWarning(s"Parse failed: $msg") *> fallbackValue
 *   case ToonError.IndentationError(msg, line) =>
 *     ZIO.logError(s"Bad indentation at line $line") *> ZIO.fail(error)
 * }
 * }}}
 * 
 * ==Custom Configuration==
 * 
 * {{{
 * val customConfig = DecoderConfig(
 *   strictMode = false,
 *   indentSize = 4
 * )
 * 
 * program.provide(ToonDecoderService.configured(customConfig))
 * }}}
 */
trait ToonDecoderService {
  /**
   * Decode a TOON format string into a ToonValue.
   * 
   * This is an effect that describes the decoding operation.
   * The actual decoding is deferred until the effect is executed.
   * 
   * @param input The TOON format string to decode
   * @return A ZIO effect that either fails with ToonError or succeeds with ToonValue
   */
  def decode(input: String): IO[ToonError, ToonValue]
}

object ToonDecoderService {
  
  /**
   * Live implementation of ToonDecoderService.
   * 
   * This implementation:
   * - Uses ToonDecoder internally for parsing
   * - Converts Either results to ZIO effects
   * - Never throws exceptions - all errors are in the error channel
   * 
   * @param config The decoder configuration
   */
  final case class Live(config: DecoderConfig) extends ToonDecoderService {
    private val decoder = new ToonDecoder(config)
    
    def decode(input: String): IO[ToonError, ToonValue] =
      ZIO.fromEither(decoder.decode(input))
  }
  
  /**
   * ZLayer that provides a ToonDecoderService with default configuration.
   * 
   * This is the recommended layer for most use cases.
   * It uses default configuration (strict mode, 2-space indentation).
   * 
   * @example
   * {{{
   * val program = ToonDecoderService.decode(input)
   * program.provide(ToonDecoderService.live)
   * }}}
   */
  val live: ULayer[ToonDecoderService] =
    ZLayer.succeed(Live(DecoderConfig.default))
  
  /**
   * ZLayer that provides a ToonDecoderService with custom configuration.
   * 
   * Use this when you need non-default decoding behavior.
   * 
   * @param config Custom decoder configuration
   * @return A layer providing the configured decoder service
   * 
   * @example
   * {{{
   * val config = DecoderConfig(strictMode = false, indentSize = 4)
   * program.provide(ToonDecoderService.configured(config))
   * }}}
   */
  def configured(config: DecoderConfig): ULayer[ToonDecoderService] =
    ZLayer.succeed(Live(config))
  
  /**
   * Accessor method to decode a string using the service from the environment.
   * 
   * This is a convenience method that automatically accesses the service
   * from the ZIO environment and calls its decode method.
   * 
   * @param input The TOON format string to decode
   * @return A ZIO effect requiring ToonDecoderService that decodes the input
   * 
   * @example
   * {{{
   * // Direct use
   * ToonDecoderService.decode(input).provide(ToonDecoderService.live)
   * 
   * // In for-comprehension
   * for {
   *   value1 <- ToonDecoderService.decode(input1)
   *   value2 <- ToonDecoderService.decode(input2)
   * } yield (value1, value2)
   * }}}
   */
  def decode(input: String): ZIO[ToonDecoderService, ToonError, ToonValue] =
    ZIO.serviceWithZIO[ToonDecoderService](_.decode(input))
}
