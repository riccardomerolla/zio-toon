package io.github.riccardomerolla.ziotoon

import zio._

import ToonPromptService.PromptError

/** Service that scans a natural language prompt, detects embedded JSON structures, and rewrites them to TOON format so
  * downstream AI/agent systems can consume token-efficient structures.
  */
trait ToonPromptService {

  /** Rewrites the provided prompt, converting any detected JSON segments to TOON format.
    *
    *   - JSON code fences marked as ```json ... ``` are always converted (failing if invalid JSON)
    *   - Inline JSON objects/arrays outside fences are converted when valid; invalid inline snippets are ignored
    */
  def rewritePrompt(prompt: String): IO[PromptError, String]
}

object ToonPromptService {

  /** Errors emitted while rewriting prompts.
    */
  sealed trait PromptError {
    def message: String
  }

  object PromptError {

    /** Raised when a fenced JSON block cannot be parsed or converted.
      *
      * @param snippet
      *   Preview of the offending snippet
      * @param cause
      *   Parser error message
      */
    final case class JsonConversionFailed(snippet: String, cause: String) extends PromptError {
      val message: String = s"Failed to convert JSON snippet '${snippet.take(80)}': $cause"
    }
  }

  final private case class Replacement(start: Int, end: Int, text: String)

  final private case class InlineSegment(start: Int, end: Int, content: String)

  private val JsonFencePattern =
    """(?s)```(?i:json)\s*\n?(.*?)```""".r

  final case class Live(toonJson: ToonJsonService, encoder: ToonEncoderService) extends ToonPromptService {

    def rewritePrompt(prompt: String): IO[PromptError, String] =
      for {
        fenced <- replaceCodeFences(prompt)
        inline <- replaceInlineJson(fenced)
      } yield inline

    private def replaceCodeFences(prompt: String): IO[PromptError, String] = {
      val matches = JsonFencePattern.findAllMatchIn(prompt).toList
      ZIO
        .foreach(matches) { m =>
          val snippet = m.group(1).trim
          convertJsonSnippet(snippet).map { toon =>
            Replacement(m.start, m.end, s"```toon\n$toon\n```")
          }
        }
        .map(repls => applyReplacements(prompt, repls))
    }

    private def replaceInlineJson(prompt: String): IO[PromptError, String] = {
      val segments = findInlineJsonSegments(prompt)
      ZIO
        .foreach(segments) { seg =>
          convertJsonSnippet(seg.content).either.map {
            case Right(toon) => Some(Replacement(seg.start, seg.end, toon))
            case Left(_)     => None // Ignore invalid inline JSON segments
          }
        }
        .map(repls => applyReplacements(prompt, repls.flatten))
    }

    private def convertJsonSnippet(snippet: String): IO[PromptError, String] =
      for {
        value   <- toonJson
                     .fromJson(snippet)
                     .mapError(PromptError.JsonConversionFailed(snippet.take(120), _))
        encoded <- encoder.encode(value)
      } yield encoded

    private def applyReplacements(input: String, replacements: List[Replacement]): String = {
      val builder = new StringBuilder(input)
      replacements.sortBy(-_.start).foreach { r =>
        builder.replace(r.start, r.end, r.text)
      }
      builder.toString
    }

    private def findInlineJsonSegments(input: String): List[InlineSegment] = {
      @scala.annotation.tailrec
      def loop(idx: Int, insideFence: Boolean, acc: List[InlineSegment]): List[InlineSegment] =
        if (idx >= input.length) acc.reverse
        else if (input.startsWith("```", idx)) loop(idx + 3, !insideFence, acc)
        else if (!insideFence && (input.charAt(idx) == '{' || input.charAt(idx) == '[')) {
          matchJson(input, idx) match {
            case Some(endIdx) =>
              val segment = InlineSegment(idx, endIdx + 1, input.substring(idx, endIdx + 1).trim)
              loop(endIdx + 1, insideFence, segment :: acc)
            case None         =>
              loop(idx + 1, insideFence, acc)
          }
        }
        else loop(idx + 1, insideFence, acc)

      loop(0, insideFence = false, Nil)
    }

    private def matchJson(input: String, startIdx: Int): Option[Int] = {
      val opening = input.charAt(startIdx)
      val closing =
        if (opening == '{') '}'
        else if (opening == '[') ']'
        else return None

      @scala.annotation.tailrec
      def loop(idx: Int, stack: List[Char], inQuote: Boolean, escaping: Boolean): Option[Int] =
        if (idx >= input.length) None
        else {
          val c = input.charAt(idx)
          if (inQuote) {
            if (escaping) loop(idx + 1, stack, inQuote = true, escaping = false)
            else if (c == '\\') loop(idx + 1, stack, inQuote = true, escaping = true)
            else if (c == '"') loop(idx + 1, stack, inQuote = false, escaping = false)
            else loop(idx + 1, stack, inQuote = true, escaping = false)
          }
          else {
            c match {
              case '"'       => loop(idx + 1, stack, inQuote = true, escaping = false)
              case '{'       => loop(idx + 1, '}' :: stack, inQuote = false, escaping = false)
              case '['       => loop(idx + 1, ']' :: stack, inQuote = false, escaping = false)
              case '}' | ']' =>
                stack match {
                  case expected :: tail if expected == c =>
                    if (tail.isEmpty) Some(idx)
                    else loop(idx + 1, tail, inQuote = false, escaping = false)
                  case _                                 => None
                }
              case _         => loop(idx + 1, stack, inQuote = false, escaping = false)
            }
          }
        }

      loop(startIdx + 1, List(closing), inQuote = false, escaping = false)
    }
  }

  val live: ZLayer[ToonJsonService & ToonEncoderService, Nothing, ToonPromptService] =
    ZLayer.fromFunction(Live.apply)

  def rewritePrompt(prompt: String): ZIO[ToonPromptService, PromptError, String] =
    ZIO.serviceWithZIO[ToonPromptService](_.rewritePrompt(prompt))
}
