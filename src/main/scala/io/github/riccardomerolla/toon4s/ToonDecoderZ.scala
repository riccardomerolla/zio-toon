package io.github.riccardomerolla.toon4s

import zio._

/**
 * ZIO-based TOON decoder.
 */
object ToonDecoderZ {
  
  /**
   * Decode a TOON format string into a ToonValue.
   */
  def decode(input: String, config: DecoderConfig = DecoderConfig.default): IO[ToonError, ToonValue] =
    ZIO.fromEither(ToonDecoder(config).decode(input))
  
  /**
   * Create a decoder with the given configuration.
   */
  def make(config: DecoderConfig = DecoderConfig.default): UIO[ToonDecoder] =
    ZIO.succeed(ToonDecoder(config))
  
  /**
   * Layer that provides a ToonDecoder with default configuration.
   */
  val live: ULayer[ToonDecoder] =
    ZLayer.succeed(ToonDecoder())
  
  /**
   * Layer that provides a ToonDecoder with custom configuration.
   */
  def configured(config: DecoderConfig): ULayer[ToonDecoder] =
    ZLayer.succeed(ToonDecoder(config))
}
