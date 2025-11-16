package io.github.riccardomerolla.ziotoon.cli

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.{ Encoding, EncodingType }

object TokenEstimator {

  private val registry = Encodings.newDefaultEncodingRegistry()

  private val KnownEncodings: Map[String, (EncodingType, String)] = Map(
    "cl100k"    -> (EncodingType.CL100K_BASE, "CL100K_BASE"),
    "p50k"      -> (EncodingType.P50K_BASE, "P50K_BASE"),
    "p50k_edit" -> (EncodingType.P50K_EDIT, "P50K_EDIT"),
    "r50k"      -> (EncodingType.R50K_BASE, "R50K_BASE"),
  )

  private def encodingTypeFor(name: String): (EncodingType, String) =
    KnownEncodings.getOrElse(name.toLowerCase, KnownEncodings("cl100k"))

  def canonicalName(name: String): String = encodingTypeFor(name)._2

  private def resolveEncoding(name: String): Encoding = {
    val (encodingType, _) = encodingTypeFor(name)
    registry.getEncoding(encodingType)
  }

  def estimateTokens(text: String, tokenizer: String): Int =
    if (text.isEmpty) 0
    else resolveEncoding(tokenizer).countTokens(text)
}
