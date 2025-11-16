package io.github.riccardomerolla.ziotoon

/** Configuration options for TOON decoding.
  *
  * This is a pure data type (case class) following functional programming principles. Immutable configuration is
  * provided at construction time.
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
  *   maxDepth = Some(200),
  *   maxArrayLength = Some(10000),
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
  *   - Array lengths match declared counts
  *   - Indentation is consistent
  *   - Required syntax elements are present
  *   - Depth and length limits guard against malicious payloads
  *
  * Disable for more lenient parsing.
  *
  * @param strictMode
  *   Whether to enable strict mode validation (default: true)
  * @param indentSize
  *   Expected number of spaces per indentation level (default: 2)
  * @param maxDepth
  *   Maximum indentation depth allowed (default: Some(1000)). None disables the guard.
  * @param maxArrayLength
  *   Maximum number of elements allowed per array (default: Some(100000)). None disables.
  * @param maxStringLength
  *   Maximum length for decoded string values and keys (default: Some(1000000)). None disables.
  */
final case class DecoderConfig(
    strictMode: Boolean = true,
    indentSize: Int = 2,
    maxDepth: Option[Int] = Some(1000),
    maxArrayLength: Option[Int] = Some(100000),
    maxStringLength: Option[Int] = Some(1000000),
  ) {
  require(indentSize > 0, s"indentSize must be positive, found $indentSize")
  maxDepth.foreach(limit => require(limit > 0, s"maxDepth must be positive, found $limit"))
  maxArrayLength.foreach(limit => require(limit > 0, s"maxArrayLength must be positive, found $limit"))
  maxStringLength.foreach(limit => require(limit > 0, s"maxStringLength must be positive, found $limit"))
}

object DecoderConfig {

  /** Default decoder configuration.
    *
    *   - Strict mode enabled
    *   - 2-space indentation
    *   - Depth limit: 1000
    *   - Array length limit: 100000
    *   - String length limit: 1000000
    */
  val default: DecoderConfig = DecoderConfig()
}

/** Errors that can occur during TOON decoding.
  *
  * Following ZIO best practices, these are pure ADT types that don't extend Throwable. They represent typed errors in
  * the error channel of ZIO effects.
  */
sealed trait ToonError {
  def message: String
}

object ToonError {

  /** Generic parsing error.
    */
  final case class ParseError(message: String) extends ToonError

  /** Syntax error at a specific line.
    */
  final case class SyntaxError(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }

  /** Invalid escape sequence in a string.
    */
  final case class InvalidEscape(sequence: String, line: Int) extends ToonError {
    val message: String = s"Invalid escape sequence: $sequence at line $line"
  }

  /** Invalid numeric literal.
    */
  final case class InvalidNumber(value: String, line: Int) extends ToonError {
    val message: String = s"Invalid number literal '$value' at line $line"
  }

  /** String literal not properly terminated.
    */
  final case class UnterminatedString(line: Int) extends ToonError {
    val message: String = s"Unterminated string at line $line"
  }

  /** Missing colon after object key.
    */
  final case class MissingColon(line: Int) extends ToonError {
    val message: String = s"Missing colon after key at line $line"
  }

  /** Indentation error (e.g., not a multiple of indent size).
    */
  final case class IndentationError(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }

  /** Array length doesn't match declared count.
    */
  final case class CountMismatch(expected: Int, actual: Int, context: String, line: Int) extends ToonError {
    val message: String = s"Expected $expected items in $context, but got $actual at line $line"
  }

  /** Tabular row width doesn't match header width.
    */
  final case class WidthMismatch(expected: Int, actual: Int, line: Int) extends ToonError {
    val message: String = s"Expected $expected values in row, but got $actual at line $line"
  }

  /** Unexpected blank line in array context.
    */
  final case class BlankLineInArray(line: Int) extends ToonError {
    val message: String = s"Blank line in array at line $line"
  }

  /** Delimiter mismatch in array context.
    */
  final case class DelimiterMismatch(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }

  /** Document exceeded configured depth limit.
    */
  final case class DepthLimitExceeded(limit: Int, line: Int) extends ToonError {
    val message: String = s"Exceeded maximum depth $limit at line $line"
  }

  /** Array exceeded configured length limit.
    */
  final case class ArrayLengthLimitExceeded(limit: Int, actual: Int, context: String, line: Int) extends ToonError {
    val message: String = s"$context exceeds maximum length $limit (was $actual) at line $line"
  }

  /** String exceeded configured length limit.
    */
  final case class StringTooLong(limit: Int, actual: Int, line: Int) extends ToonError {
    val message: String = s"String length $actual exceeds maximum $limit at line $line"
  }
}
