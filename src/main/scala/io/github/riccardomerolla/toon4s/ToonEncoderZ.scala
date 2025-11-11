package io.github.riccardomerolla.toon4s

import zio._

/**
 * ZIO-based TOON encoder.
 */
object ToonEncoderZ {
  
  /**
   * Encode a ToonValue to TOON format string.
   */
  def encode(value: ToonValue, config: EncoderConfig = EncoderConfig.default): UIO[String] =
    ZIO.succeed(ToonEncoder(config).encode(value))
  
  /**
   * Create an encoder with the given configuration.
   */
  def make(config: EncoderConfig = EncoderConfig.default): UIO[ToonEncoder] =
    ZIO.succeed(ToonEncoder(config))
  
  /**
   * Layer that provides a ToonEncoder with default configuration.
   */
  val live: ULayer[ToonEncoder] =
    ZLayer.succeed(ToonEncoder())
  
  /**
   * Layer that provides a ToonEncoder with custom configuration.
   */
  def configured(config: EncoderConfig): ULayer[ToonEncoder] =
    ZLayer.succeed(ToonEncoder(config))
}
