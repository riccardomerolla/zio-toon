package io.github.riccardomerolla.ziotoon

/**
 * Configuration options for TOON decoding.
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
  val default: DecoderConfig = DecoderConfig()
}

/**
 * Path expansion options.
 */
enum PathExpansion {
  case Off
  case Safe
}

/**
 * Errors that can occur during TOON decoding.
 */
sealed trait ToonError extends Throwable {
  def message: String
  override def getMessage: String = message
}

object ToonError {
  final case class ParseError(message: String) extends ToonError
  final case class SyntaxError(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }
  final case class InvalidEscape(sequence: String, line: Int) extends ToonError {
    val message: String = s"Invalid escape sequence: $sequence at line $line"
  }
  final case class UnterminatedString(line: Int) extends ToonError {
    val message: String = s"Unterminated string at line $line"
  }
  final case class MissingColon(line: Int) extends ToonError {
    val message: String = s"Missing colon after key at line $line"
  }
  final case class IndentationError(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }
  final case class CountMismatch(expected: Int, actual: Int, context: String, line: Int) extends ToonError {
    val message: String = s"Expected $expected items in $context, but got $actual at line $line"
  }
  final case class WidthMismatch(expected: Int, actual: Int, line: Int) extends ToonError {
    val message: String = s"Expected $expected values in row, but got $actual at line $line"
  }
  final case class BlankLineInArray(line: Int) extends ToonError {
    val message: String = s"Blank line in array at line $line"
  }
  final case class DelimiterMismatch(message: String, line: Int) extends ToonError {
    override def toString: String = s"$message at line $line"
  }
}
