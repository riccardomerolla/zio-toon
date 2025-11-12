package io.github.riccardomerolla.ziotoon

/**
 * Configuration options for TOON decoding.
 * 
 * This is a pure data type (case class) following functional programming principles.
 * Immutable configuration is provided at construction time.
 * 
 * ==Usage==
 * 
 * {{{
 * // Use default configuration
 * val defaultConfig = DecoderConfig.default
 * 
 * // Custom configuration
 * val customConfig = DecoderConfig(
 *   strictMode = false,
 *   indentSize = 4,
 *   expandPaths = PathExpansion.Safe
 * )
 * 
 * // Use with decoder
 * val decoder = new ToonDecoder(customConfig)
 * 
 * // Or with service
 * val layer = ToonDecoderService.configured(customConfig)
 * }}}
 * 
 * ==Strict Mode==
 * 
 * When enabled (default), validates:
 * - Array lengths match declared counts
 * - Indentation is consistent
 * - Required syntax elements are present
 * 
 * Disable for more lenient parsing.
 * 
 * @param strictMode Whether to enable strict mode validation (default: true)
 * @param indentSize Expected number of spaces per indentation level (default: 2)
 * @param expandPaths Whether to expand dotted paths in keys (default: off)
 */
final case class DecoderConfig(
  strictMode: Boolean = true,
  indentSize: Int = 2,
  expandPaths: PathExpansion = PathExpansion.Off
)

object DecoderConfig {
  /**
   * Default decoder configuration.
   * 
   * - Strict mode enabled
   * - 2-space indentation
   * - Path expansion off
   */
  val default: DecoderConfig = DecoderConfig()
}

/**
 * Path expansion options for decoder.
 * 
 * Controls whether dotted keys like "user.name" should be expanded
 * into nested objects.
 */
enum PathExpansion {
  /** No path expansion (treat dots as literal characters in keys) */
  case Off
  
  /** Safe path expansion (expand dotted paths when unambiguous) */
  case Safe
}

/**
 * Errors that can occur during TOON decoding.
 * 
 * Following ZIO best practices, these are pure ADT types that don't extend Throwable.
 * They represent typed errors in the error channel of ZIO effects.
 */
sealed trait ToonError {
  def message: String
}

object ToonError {
  /**
   * Generic parsing error.
   */
  final case class ParseError(message: String) extends ToonError
  
  /**
   * Syntax error at a specific line.
   */
  final case class SyntaxError(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }
  
  /**
   * Invalid escape sequence in a string.
   */
  final case class InvalidEscape(sequence: String, line: Int) extends ToonError {
    val message: String = s"Invalid escape sequence: $sequence at line $line"
  }
  
  /**
   * String literal not properly terminated.
   */
  final case class UnterminatedString(line: Int) extends ToonError {
    val message: String = s"Unterminated string at line $line"
  }
  
  /**
   * Missing colon after object key.
   */
  final case class MissingColon(line: Int) extends ToonError {
    val message: String = s"Missing colon after key at line $line"
  }
  
  /**
   * Indentation error (e.g., not a multiple of indent size).
   */
  final case class IndentationError(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }
  
  /**
   * Array length doesn't match declared count.
   */
  final case class CountMismatch(expected: Int, actual: Int, context: String, line: Int) extends ToonError {
    val message: String = s"Expected $expected items in $context, but got $actual at line $line"
  }
  
  /**
   * Tabular row width doesn't match header width.
   */
  final case class WidthMismatch(expected: Int, actual: Int, line: Int) extends ToonError {
    val message: String = s"Expected $expected values in row, but got $actual at line $line"
  }
  
  /**
   * Unexpected blank line in array context.
   */
  final case class BlankLineInArray(line: Int) extends ToonError {
    val message: String = s"Blank line in array at line $line"
  }
  
  /**
   * Delimiter mismatch in array context.
   */
  final case class DelimiterMismatch(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }
}
