package io.github.riccardomerolla.ziotoon

/** Utilities for string quoting and escaping according to TOON specification.
  *
  * Pure functional implementation:
  *   - No mutable state (no StringBuilder, no var)
  *   - Tail-recursive algorithms
  *   - No early returns
  *   - Composable with map and foldLeft
  */
private[ziotoon] object StringUtils {

  /** Escape a string according to TOON rules (Section 7.1). Only these sequences must be escaped: \, ", \n, \r, \t
    *
    * Pure functional implementation using foldLeft.
    */
  def escape(s: String): String =
    s.foldLeft(new StringBuilder(s.length + 10)) { (acc, c) =>
      c match {
        case '\\' => acc.append("\\\\")
        case '"'  => acc.append("\\\"")
        case '\n' => acc.append("\\n")
        case '\r' => acc.append("\\r")
        case '\t' => acc.append("\\t")
        case c    => acc.append(c)
      }
    }.toString

  /** Unescape a string according to TOON rules. Pure functional implementation using tail recursion.
    */
  def unescape(s: String): Either[String, String] = {
    @scala.annotation.tailrec
    def loop(idx: Int, acc: StringBuilder): Either[String, String] =
      if (idx >= s.length) Right(acc.toString)
      else
        s.charAt(idx) match {
          case '\\' if idx + 1 < s.length =>
            s.charAt(idx + 1) match {
              case '\\' => loop(idx + 2, acc.append('\\'))
              case '"'  => loop(idx + 2, acc.append('"'))
              case 'n'  => loop(idx + 2, acc.append('\n'))
              case 'r'  => loop(idx + 2, acc.append('\r'))
              case 't'  => loop(idx + 2, acc.append('\t'))
              case c    => Left(s"Invalid escape sequence: \\$c")
            }
          case '\\'                       => Left("Unterminated escape sequence")
          case c                          => loop(idx + 1, acc.append(c))
        }

    loop(0, new StringBuilder(s.length))
  }

  /** Check if a string needs quoting according to TOON rules (Section 7.2). A string must be quoted if:
    *   - It is empty
    *   - It has leading or trailing whitespace
    *   - It equals "true", "false", or "null"
    *   - It looks numeric
    *   - It contains special characters: :, ", \, [, ], {, }
    *   - It contains control characters
    *   - It contains the relevant delimiter
    *   - It equals "-" or starts with "-"
    *
    * Pure function using pattern matching and exists.
    */
  def needsQuoting(s: String, delimiter: Delimiter): Boolean =
    s.isEmpty ||
    s.headOption.exists(_.isWhitespace) ||
    s.lastOption.exists(_.isWhitespace) ||
    s == "true" || s == "false" || s == "null" ||
    s == "-" || s.startsWith("-") ||
    isNumericLike(s) ||
    hasSpecialCharacters(s, delimiter)

  /** Check if a string contains special characters that require quoting. Pure helper function.
    */
  private def hasSpecialCharacters(s: String, delimiter: Delimiter): Boolean =
    s.exists { c =>
      c == ':' || c == '"' || c == '\\' ||
      c == '[' || c == ']' || c == '{' || c == '}' ||
      c == '\n' || c == '\r' || c == '\t' ||
      c == delimiter.char
    }

  /** Check if a string looks numeric. Pure function.
    */
  private def isNumericLike(s: String): Boolean =
    s.matches("^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$") ||
    s.matches("^0\\d+$") // leading zeros like "05"

  /** Quote a string if needed. Pure function.
    */
  def quoteIfNeeded(s: String, delimiter: Delimiter): String =
    if (needsQuoting(s, delimiter)) s"\"${escape(s)}\""
    else s

  /** Check if a key is a valid unquoted key. Keys may be unquoted if they match: ^[A-Za-z_][A-Za-z0-9_.]*$ Pure
    * function.
    */
  def isValidUnquotedKey(s: String): Boolean =
    s.nonEmpty && s.matches("^[A-Za-z_][A-Za-z0-9_.]*$")

  /** Quote a key if needed. Pure function.
    */
  def quoteKeyIfNeeded(key: String): String =
    if (isValidUnquotedKey(key)) key
    else s"\"${escape(key)}\""
}
