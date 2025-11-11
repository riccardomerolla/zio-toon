package io.github.riccardomerolla.toon4s

import zio._

/**
 * Legacy compatibility object for ToonEncoder ZIO operations.
 * 
 * @deprecated Use ToonEncoderService instead for proper dependency injection.
 * 
 * This object is kept for backward compatibility but the recommended approach
 * is to use ToonEncoderService with ZLayer for composable, testable effects.
 */
object ToonEncoderZ {
  
  /**
   * Encode a ToonValue to TOON format string.
   * 
   * @deprecated Use ToonEncoderService.encode with proper ZLayer
   */
  def encode(value: ToonValue, config: EncoderConfig = EncoderConfig.default): UIO[String] =
    ZIO.succeed(new ToonEncoder(config).encode(value))
  
  /**
   * Create an encoder with the given configuration.
   * 
   * @deprecated Use ToonEncoderService.Live directly
   */
  def make(config: EncoderConfig = EncoderConfig.default): UIO[ToonEncoder] =
    ZIO.succeed(ToonEncoder(config))
  
  /**
   * Layer that provides a ToonEncoder with default configuration.
   * 
   * @deprecated Use ToonEncoderService.live instead
   */
  val live: ULayer[ToonEncoder] =
    ZLayer.succeed(ToonEncoder())
  
  /**
   * Layer that provides a ToonEncoder with custom configuration.
   * 
   * @deprecated Use ToonEncoderService.configured instead
   */
  def configured(config: EncoderConfig): ULayer[ToonEncoder] =
    ZLayer.succeed(ToonEncoder(config))
}
