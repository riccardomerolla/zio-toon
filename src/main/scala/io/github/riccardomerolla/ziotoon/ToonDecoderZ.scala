package io.github.riccardomerolla.ziotoon

import zio._

/**
 * Legacy compatibility object for ToonDecoder ZIO operations.
 * 
 * @deprecated Use ToonDecoderService instead for proper dependency injection.
 * 
 * This object is kept for backward compatibility but the recommended approach
 * is to use ToonDecoderService with ZLayer for composable, testable effects.
 */
object ToonDecoderZ {
  
  /**
   * Decode a TOON format string into a ToonValue.
   * 
   * @deprecated Use ToonDecoderService.decode with proper ZLayer
   */
  def decode(input: String, config: DecoderConfig = DecoderConfig.default): IO[ToonError, ToonValue] =
    ZIO.fromEither(new ToonDecoder(config).decode(input))
  
  /**
   * Create a decoder with the given configuration.
   * 
   * @deprecated Use ToonDecoderService.Live directly
   */
  def make(config: DecoderConfig = DecoderConfig.default): UIO[ToonDecoder] =
    ZIO.succeed(ToonDecoder(config))
  
  /**
   * Layer that provides a ToonDecoder with default configuration.
   * 
   * @deprecated Use ToonDecoderService.live instead
   */
  val live: ULayer[ToonDecoder] =
    ZLayer.succeed(ToonDecoder())
  
  /**
   * Layer that provides a ToonDecoder with custom configuration.
   * 
   * @deprecated Use ToonDecoderService.configured instead
   */
  def configured(config: DecoderConfig): ULayer[ToonDecoder] =
    ZLayer.succeed(ToonDecoder(config))
}
