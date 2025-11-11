package io.github.riccardomerolla.ziotoon

import zio._

/**
 * Legacy compatibility object for ToonDecoderZ.
 * 
 * @deprecated Use ToonDecoderService instead. This will be removed in a future version.
 */
@deprecated("Use ToonDecoderService instead", "0.1.0")
object ToonDecoderZ {
  
  /**
   * Decode a TOON format string into a ToonValue.
   * 
   * @param input The TOON format string to decode
   * @return A ZIO effect that either fails with ToonError or succeeds with ToonValue
   */
  def decode(input: String): IO[ToonError, ToonValue] =
    ZIO.fromEither(new ToonDecoder(DecoderConfig.default).decode(input))
  
  /**
   * Decode with custom configuration.
   */
  def decode(input: String, config: DecoderConfig): IO[ToonError, ToonValue] =
    ZIO.fromEither(new ToonDecoder(config).decode(input))
}
