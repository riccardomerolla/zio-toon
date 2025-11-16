package io.github.riccardomerolla.ziotoon.cli

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

import io.github.riccardomerolla.ziotoon.*
import zio.cli.HelpDoc.Span
import zio.cli.{ Args, CliApp, Command, Exists, HelpDoc, Options }
import zio.{ Console, Scope, ZIO, ZIOAppArgs, ZIOAppDefault }
import zio.Zippable.given

object Main extends ZIOAppDefault {

  private sealed trait CliAction
  private final case class EncodeAction(request: EncodeRequest) extends CliAction
  private final case class DecodeAction(request: DecodeRequest) extends CliAction

  private final case class EncodeConfig(
      output: Option[Path],
      indent: Int,
      delimiter: String,
      optimize: Boolean,
      stats: Boolean,
      tokenizer: String,
    )

  private final case class DecodeConfig(
      output: Option[Path],
      indent: Int,
      strict: Boolean,
      stats: Boolean,
      tokenizer: String,
    )

  private final case class EncodeRequest(
      input: Path,
      config: EncodeConfig,
    )

  private final case class DecodeRequest(
      input: Path,
      config: DecodeConfig,
    )

  private val inputArg: Args[Path] = Args.file("input", Exists.Either)

  private val outputOpt: Options[Option[Path]] =
    Options
      .text("output")
      .alias("o")
      .optional
      .map(_.map(Paths.get(_)))

  private val indentOpt: Options[Int] =
    Options.integer("indent").withDefault(BigInt(2)).map(_.intValue)

  private val delimiterOpt: Options[String] = Options.text("delimiter").withDefault("comma")
  private val tokenizerOpt: Options[String] = Options.text("tokenizer").withDefault("cl100k")

  private def booleanOption(name: String, default: Boolean, label: String): Options[Boolean] = {
    Options
      .text(name)
      .collect(s"$label must be 'true' or 'false'") {
        case value if value.equalsIgnoreCase("true")  => true
        case value if value.equalsIgnoreCase("false") => false
      }
      .optional
      .map(_.getOrElse(default))
      .??(s"$label (true/false, default: $default)")
  }

  private val optimizeOpt: Options[Boolean] =
    booleanOption("optimize", default = false, label = "Enable delimiter optimizer")

  private val statsOpt: Options[Boolean] =
    booleanOption("stats", default = false, label = "Print tokenizer statistics")

  private val strictOpt: Options[Boolean] =
    booleanOption("strict", default = true, label = "Enforce strict decoder guard rails")

  private val encodeOptions: Options[EncodeConfig] =
    (outputOpt ++ indentOpt ++ delimiterOpt ++ optimizeOpt ++ statsOpt ++ tokenizerOpt).map {
      (tuple: (Option[Path], Int, String, Boolean, Boolean, String)) =>
        val (output, indent, delimiter, optimize, stats, tokenizer) = tuple
        EncodeConfig(output, indent, delimiter, optimize, stats, tokenizer)
    }

  private val decodeOptions: Options[DecodeConfig] =
    (outputOpt ++ indentOpt ++ strictOpt ++ statsOpt ++ tokenizerOpt).map {
      (tuple: (Option[Path], Int, Boolean, Boolean, String)) =>
        val (output, indent, strict, stats, tokenizer) = tuple
        DecodeConfig(output, indent, strict, stats, tokenizer)
    }

  private val encodeCommand: Command[CliAction] =
    Command("encode", encodeOptions, inputArg)
      .withHelp(HelpDoc.p(Span.text("Encode JSON input to TOON")))
      .map { case (config, input) => EncodeAction(EncodeRequest(input, config)) }

  private val decodeCommand: Command[CliAction] =
    Command("decode", decodeOptions, inputArg)
      .withHelp(HelpDoc.p(Span.text("Decode TOON input to JSON")))
      .map { case (config, input) => DecodeAction(DecodeRequest(input, config)) }

  private[cli] val cliApp: CliApp[ToonJsonService, String, Unit] =
    CliApp.make[ToonJsonService, String, CliAction, Unit](
      name = "zio-toon",
      version = "0.2.3",
      summary = Span.text("TOON CLI"),
      command = encodeCommand orElse decodeCommand,
    ) {
      case EncodeAction(request) => executeEncode(request)
      case DecodeAction(request) => executeDecode(request)
    }

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    for {
      args <- getArgs
      _    <- cliApp.run(args.toList).provide(ToonJsonService.live)
    } yield ()

  private def executeEncode(request: EncodeRequest): ZIO[ToonJsonService, String, Unit] = {
    val EncodeRequest(input, config) = request
    for {
      json      <- readUtf8(input)
      value     <- ToonJsonService.fromJson(json)
      delimiter <- ZIO.fromEither(parseDelimiter(config.delimiter)).mapError(identity)
      baseConfig = EncoderConfig(indentSize = config.indent, delimiter = delimiter)
      best <-
        if (config.optimize) selectBestEncoding(value, config.indent, config.tokenizer)
        else ZIO.succeed(baseConfig -> ToonEncoder(baseConfig).encode(value))
      (_, encoded) = best
      _ <- writeOutput(encoded, config.output)
      _ <- maybePrintStats(json, encoded, config.stats || config.optimize, config.tokenizer)
    } yield ()
  }

  private def executeDecode(request: DecodeRequest): ZIO[ToonJsonService, String, Unit] = {
    val DecodeRequest(input, config) = request
    val decoderConfig = DecoderConfig(strictMode = config.strict, indentSize = config.indent)
    for {
      toonInput <- readUtf8(input)
      value     <- ZIO.fromEither(ToonDecoder(decoderConfig).decode(toonInput).left.map(_.message))
      json      <- ToonJsonService.toPrettyJson(value, config.indent)
      _         <- writeOutput(json, config.output)
      _         <- maybePrintStats(toonInput, json, config.stats, config.tokenizer)
    } yield ()
  }

  private def readUtf8(path: Path): ZIO[Any, String, String] =
    ZIO.attempt(Files.readString(path, StandardCharsets.UTF_8)).mapError(_.getMessage)

  private def writeOutput(content: String, target: Option[Path]): ZIO[Any, String, Unit] =
    target match {
      case Some(path) =>
        ZIO
          .attempt {
            val parent = path.getParent
            if (parent != null) Files.createDirectories(parent)
            Files.write(path, content.getBytes(StandardCharsets.UTF_8))
          }
          .unit
          .mapError(_.getMessage)
      case None => Console.printLine(content).unit.mapError(_.getMessage)
    }

  private def maybePrintStats(
      input: String,
      output: String,
      enabled: Boolean,
      tokenizer: String,
    ): ZIO[Any, Nothing, Unit] =
    if (!enabled) ZIO.unit
    else {
      val inputTokens  = TokenEstimator.estimateTokens(input, tokenizer)
      val outputTokens = TokenEstimator.estimateTokens(output, tokenizer)
      val savings      = if (inputTokens > 0) Math.round((1.0 - outputTokens.toDouble / inputTokens) * 100).toInt else 0
      Console
        .printLine(
          s"[stats] tokenizer=${TokenEstimator.canonicalName(tokenizer)} input=$inputTokens output=$outputTokens delta=${outputTokens - inputTokens} savings=$savings%"
        )
        .ignore
    }

  private def selectBestEncoding(
      value: ToonValue,
      indent: Int,
      tokenizer: String,
    ): ZIO[Any, Nothing, (EncoderConfig, String)] = {
    val configs = List(Delimiter.Comma, Delimiter.Tab, Delimiter.Pipe).map(delim => EncoderConfig(indentSize = indent, delimiter = delim))
    val encoded = configs.map(cfg => cfg -> ToonEncoder(cfg).encode(value))
    val best    = encoded.minBy { case (_, text) => TokenEstimator.estimateTokens(text, tokenizer) }
    ZIO.succeed(best)
  }

  private def parseDelimiter(value: String): Either[String, Delimiter] =
    value.toLowerCase match {
      case "comma" | "," => Right(Delimiter.Comma)
      case "tab" | "\t" => Right(Delimiter.Tab)
      case "pipe" | "|"  => Right(Delimiter.Pipe)
      case other           => Left(s"Invalid delimiter: $other")
    }
}
