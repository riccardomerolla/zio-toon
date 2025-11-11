package io.github.riccardomerolla.ziotoon

import zio._

/**
 * Legacy compatibility object for ToonEncoderZ.
 * 
 * @deprecated Use ToonEncoderService instead. This will be removed in a future version.
 */
@deprecated("Use ToonEncoderService instead", "0.1.0")
object ToonEncoderZ {
  
  /**
   * Encode a ToonValue to TOON format string.
   * 
   * @param value The ToonValue to encode
   * @return A ZIO effect that produces the encoded string
   */
  def encode(value: ToonValue): UIO[String] =
    ZIO.succeed(new ToonEncoder(EncoderConfig.default).encode(value))
  
  /**
   * Encode a ToonValue with custom configuration.
   */
  def encode(value: ToonValue, config: EncoderConfig): UIO[String] =
    ZIO.succeed(new ToonEncoder(config).encode(value))
}
