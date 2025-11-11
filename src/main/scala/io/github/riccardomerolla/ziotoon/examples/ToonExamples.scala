package io.github.riccardomerolla.ziotoon.examples

import io.github.riccardomerolla.ziotoon._
import ToonValue._
import zio._

/**
 * Examples demonstrating the usage of zio-toon with ZIO best practices.
 * 
 * This demonstrates effect-oriented programming:
 * - Effects are immutable blueprints
 * - Services are accessed via ZIO environment
 * - Errors are handled in the error channel
 * - Resources are managed properly
 */
object ToonExamples extends ZIOAppDefault {
  
  /**
   * Main application logic composed as a ZIO effect.
   * Effects are pure descriptions - execution is deferred until the end.
   */
  def run = {
    val examples = for {
      _ <- printExample("Basic Primitives", basicPrimitives)
      _ <- printExample("Simple Object", simpleObject)
      _ <- printExample("Nested Objects", nestedObjects)
      _ <- printExample("Inline Arrays", inlineArrays)
      _ <- printExample("Tabular Arrays", tabularArrays)
      _ <- printExample("Tab Delimiter", tabDelimiter)
      _ <- printExampleWithError("Round-trip with Services", roundTripWithServices)
      _ <- printExample("Error Handling", errorHandling)
    } yield ExitCode.success
    
    // Provide the required services at the application entry point
    examples.provide(Toon.live)
  }
  
  /**
   * Helper to print an example with proper error handling.
   * Uses ZIO's catchAll for exhaustive error matching.
   */
  def printExample[R](title: String, example: ZIO[R, Nothing, String]): ZIO[R, Nothing, Unit] =
    for {
      _ <- Console.printLine(s"\n=== $title ===").orDie
      result <- example
      _ <- Console.printLine(result).orDie
    } yield ()
  
  /**
   * Helper to print an example that can error, catching all errors.
   */
  def printExampleWithError[R](title: String, example: ZIO[R, ToonError, String]): ZIO[R, Nothing, Unit] =
    for {
      _ <- Console.printLine(s"\n=== $title ===").orDie
      result <- example.catchAll { error =>
        ZIO.succeed(s"Error: ${error.message}")
      }
      _ <- Console.printLine(result).orDie
    } yield ()
  
  /**
   * Basic primitives example.
   * Pure computation wrapped in ZIO.succeed.
   */
  def basicPrimitives: UIO[String] = ZIO.succeed {
    val examples = List(
      ("String", str("hello")),
      ("String with spaces", str("hello world")),
      ("Number", num(42)),
      ("Decimal", num(3.14)),
      ("Boolean true", bool(true)),
      ("Boolean false", bool(false)),
      ("Null", Null)
    )
    
    examples.map { case (desc, value) =>
      // Use the pure encode method with explicit config parameter
      s"$desc: ${Toon.encode(value, EncoderConfig.default)}"
    }.mkString("\n")
  }
  
  def simpleObject: UIO[String] = ZIO.succeed {
    val user = obj(
      "name" -> str("Alice"),
      "age" -> num(30),
      "active" -> bool(true)
    )
    
    Toon.encode(user, EncoderConfig.default)
  }
  
  def nestedObjects: UIO[String] = ZIO.succeed {
    val data = obj(
      "user" -> obj(
        "name" -> str("Bob"),
        "contact" -> obj(
          "email" -> str("bob@example.com"),
          "phone" -> str("+1-555-1234")
        )
      )
    )
    
    Toon.encode(data, EncoderConfig.default)
  }
  
  def inlineArrays: UIO[String] = ZIO.succeed {
    val data = obj(
      "tags" -> arr(str("admin"), str("ops"), str("dev")),
      "numbers" -> arr(num(1), num(2), num(3), num(4), num(5)),
      "flags" -> arr(bool(true), bool(false), bool(true))
    )
    
    Toon.encode(data, EncoderConfig.default)
  }
  
  def tabularArrays: UIO[String] = ZIO.succeed {
    val data = obj(
      "users" -> arr(
        obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin"), "active" -> bool(true)),
        obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("developer"), "active" -> bool(true)),
        obj("id" -> num(3), "name" -> str("Charlie"), "role" -> str("designer"), "active" -> bool(false))
      )
    )
    
    val toon = Toon.encode(data, EncoderConfig.default)
    
    // Compare with JSON size
    val jsonEquivalent = """{
  "users": [
    {"id": 1, "name": "Alice", "role": "admin", "active": true},
    {"id": 2, "name": "Bob", "role": "developer", "active": true},
    {"id": 3, "name": "Charlie", "role": "designer", "active": false}
  ]
}"""
    
    s"TOON format (${toon.length} chars):\n$toon\n\nJSON equivalent (${jsonEquivalent.length} chars):\n$jsonEquivalent\n\nSize reduction: ${100 - (toon.length * 100 / jsonEquivalent.length)}%"
  }
  
  def tabDelimiter: UIO[String] = ZIO.succeed {
    val data = obj(
      "data" -> arr(
        obj("a" -> num(1), "b" -> num(2), "c" -> num(3)),
        obj("a" -> num(4), "b" -> num(5), "c" -> num(6))
      )
    )
    
    val config = EncoderConfig(delimiter = Delimiter.Tab)
    val toon = Toon.encode(data, config)
    
    s"Using tab delimiter:\n$toon"
  }
  
  /**
   * Round-trip example using service-based approach.
   * 
   * This demonstrates:
   * - Using services from the environment (ToonEncoderService & ToonDecoderService)
   * - Effect composition with for-comprehension
   * - Type-safe error handling in the error channel
   */
  def roundTripWithServices: ZIO[ToonEncoderService & ToonDecoderService, ToonError, String] = {
    val original = obj(
      "user" -> obj(
        "name" -> str("Alice"),
        "age" -> num(30),
        "tags" -> arr(str("admin"), str("ops"))
      ),
      "metadata" -> obj(
        "version" -> num(1),
        "active" -> bool(true)
      )
    )
    
    // Effect composition: encode then decode
    // Effects are blueprints - nothing executes until the end
    for {
      encoded <- Toon.encode(original)
      decoded <- Toon.decode(encoded)
      matches = decoded == original
    } yield {
      "Original value encoded to TOON:\n" + encoded + "\n\nDecoded back matches original: " + matches
    }
  }
  
  /**
   * Error handling example.
   * 
   * Demonstrates:
   * - Typed errors in the error channel
   * - Proper error handling with catchAll
   * - Never swallowing errors
   */
  def errorHandling: ZIO[ToonDecoderService, Nothing, String] = {
    val invalidInput = "key1: value1\nkey2"  // Missing colon in strict mode
    
    // Attempt to decode invalid input - handle all error cases
    Toon.decode(invalidInput)
      .map(value => s"Successfully decoded: $value")
      .catchAll {
        case ToonError.MissingColon(line) =>
          ZIO.succeed(s"Caught expected error: Missing colon at line $line")
        case ToonError.ParseError(msg) =>
          ZIO.succeed(s"Caught parse error: $msg")
        case other =>
          // Handle all other ToonError cases
          ZIO.succeed(s"Caught error: ${other.message}")
      }
  }
}
