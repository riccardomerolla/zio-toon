package io.github.riccardomerolla.ziotoon

import zio._

/**
 * Service for decoding TOON format strings into ToonValue.
 * 
 * This is the service interface following ZIO best practices.
 * Effects are pure blueprints that describe decoding operations.
 */
trait ToonDecoderService {
  /**
   * Decode a TOON format string into a ToonValue.
   * 
   * @param input The TOON format string to decode
   * @return A ZIO effect that either fails with ToonError or succeeds with ToonValue
   */
  def decode(input: String): IO[ToonError, ToonValue]
}

object ToonDecoderService {
  
  /**
   * Live implementation of ToonDecoderService.
   */
  final case class Live(config: DecoderConfig) extends ToonDecoderService {
    private val decoder = new ToonDecoder(config)
    
    def decode(input: String): IO[ToonError, ToonValue] =
      ZIO.fromEither(decoder.decode(input))
  }
  
  /**
   * ZLayer that provides a ToonDecoderService with default configuration.
   */
  val live: ULayer[ToonDecoderService] =
    ZLayer.succeed(Live(DecoderConfig.default))
  
  /**
   * ZLayer that provides a ToonDecoderService with custom configuration.
   */
  def configured(config: DecoderConfig): ULayer[ToonDecoderService] =
    ZLayer.succeed(Live(config))
  
  /**
   * Accessor method to decode a string using the service from the environment.
   */
  def decode(input: String): ZIO[ToonDecoderService, ToonError, ToonValue] =
    ZIO.serviceWithZIO[ToonDecoderService](_.decode(input))
}
