# zio-toon

A Scala 3 / ZIO 2.1 implementation of TOON (Token-Oriented Object Notation), a compact serialization format optimized to reduce token usage when interacting with Large Language Models (LLMs).

**Built with ZIO Best Practices**: Effect-oriented programming, service pattern, type-safe error handling, and composable dependency injection.

## What is TOON?

TOON is a line-oriented, indentation-based text format that encodes the JSON data model with explicit structure and minimal quoting. It typically reduces token usage by 30-60% compared to JSON, making it ideal for LLM interactions where context window and token costs matter.

### Key Features

- **Token-efficient**: 30-60% fewer tokens than JSON
- **Explicit schema**: Array lengths and field names declared once
- **Minimal syntax**: Eliminates redundant punctuation (braces, brackets, quotes)
- **Tabular arrays**: CSV-like format for uniform data sets
- **Human-readable**: Indentation-based structure similar to YAML
- **Type-safe**: Full Scala 3 implementation with ADTs
- **ZIO-first**: Service pattern, ZLayer DI, typed errors, effect composition
- **JSON Integration**: Bidirectional conversion with zio-json, token savings calculation
- **Schema Support**: Automatic encoding/decoding with zio-schema for case classes

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.riccardomerolla" %% "zio-toon" % "0.1.0-SNAPSHOT"
```

## Quick Start

### Using Services (Recommended)

```scala
import io.github.riccardomerolla.ziotoon._
import ToonValue._
import zio._

// Define your application logic using services
val program = for {
  // Encode using the service from environment
  encoded <- Toon.encode(obj("name" -> str("Alice"), "age" -> num(30)))
  _       <- Console.printLine(encoded)
  
  // Decode using the service from environment
  decoded <- Toon.decode(encoded)
  _       <- Console.printLine(s"Decoded: $decoded")
} yield ()

// Provide services at application entry point
program.provideLayer(Toon.live)
```

### Pure Methods (No ZIO)

For simple use cases without ZIO effects:

```scala
import io.github.riccardomerolla.ziotoon._
import ToonValue._

// Pure encoding
val data = obj("name" -> str("Alice"), "age" -> num(30))
val toon = Toon.encode(data, EncoderConfig.default)

// Pure decoding
val result = Toon.decode(toon, DecoderConfig.default)
// Returns: Either[ToonError, ToonValue]
```

### JSON Integration

Convert between JSON and TOON, and measure token savings:

```scala
import io.github.riccardomerolla.ziotoon._
import ToonValue._
import zio._

val program = for {
  // Convert JSON to TOON
  jsonString <- ToonJsonService.fromJson("""{"name":"Alice","age":30}""")
  toonString <- ToonEncoderService.encode(jsonString)
  
  // Calculate token savings
  value = obj("name" -> str("Alice"), "age" -> num(30))
  savings <- ToonJsonService.calculateSavings(value)
  _ <- Console.printLine(s"Savings: ${savings.savingsPercent}%")
  
  // Convert TOON to JSON
  jsonOut <- ToonJsonService.toJson(value)
  _ <- Console.printLine(jsonOut)
} yield ()

// Provide layers
program.provideLayer(ToonJsonService.live ++ ToonEncoderService.live)
```

### Schema-Based Type-Safe Encoding

Automatically encode/decode case classes using zio-schema:

```scala
import io.github.riccardomerolla.ziotoon._
import zio._
import zio.schema._

case class Person(name: String, age: Int)
object Person {
  given schema: Schema[Person] = DeriveSchema.gen[Person]
}

val program = for {
  // Encode case class to TOON
  person = Person("Alice", 30)
  toonString <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(person))
  _ <- Console.printLine(toonString)
  
  // Decode TOON to case class
  decoded <- ZIO.serviceWithZIO[ToonSchemaService](_.decode[Person](toonString))
  _ <- Console.printLine(s"Decoded: $decoded")
} yield ()

// Provide layers
val layer = ToonJsonService.live >>> ToonSchemaService.live
program.provideLayer(layer)
```

## Tabular Arrays - The Key Feature

TOON excels at encoding arrays of uniform objects:

```scala
val users = obj(
  "users" -> arr(
    obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
    obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("user")),
    obj("id" -> num(3), "name" -> str("Charlie"), "role" -> str("developer"))
  )
)

val toon = Toon.encode(users, EncoderConfig.default)
println(toon)
// Output:
// users[3]{id,name,role}:
//   1,Alice,admin
//   2,Bob,user
//   3,Charlie,developer
```

## Comparison with JSON

### JSON (222 characters)
```json
{
  "users": [
    {"id": 1, "name": "Alice", "role": "admin", "active": true},
    {"id": 2, "name": "Bob", "role": "developer", "active": true},
    {"id": 3, "name": "Charlie", "role": "designer", "active": false}
  ]
}
```

### TOON (101 characters - 55% reduction!)
```
users[3]{id,name,role,active}:
  1,Alice,admin,true
  2,Bob,developer,true
  3,Charlie,designer,false
```

## ZIO Best Practices Applied

✅ **Effects as Blueprints** - All ZIO effects are pure descriptions  
✅ **Service Pattern** - Traits with Live implementations  
✅ **ZLayer DI** - Services provided at application boundaries  
✅ **Type-Safe Errors** - All errors in error channel, never thrown  
✅ **Exhaustive Error Handling** - Pattern matching covers all cases  
✅ **No Side Effects** - All I/O wrapped in ZIO effects  

## API Reference

### Core Services

#### ToonEncoderService
Encodes `ToonValue` to TOON format strings.

```scala
trait ToonEncoderService {
  def encode(value: ToonValue): UIO[String]
}
```

#### ToonDecoderService
Decodes TOON format strings to `ToonValue`.

```scala
trait ToonDecoderService {
  def decode(input: String): IO[ToonError, ToonValue]
}
```

#### ToonJsonService
Bidirectional conversion between JSON and TOON, with token savings calculation.

```scala
trait ToonJsonService {
  def toJson(value: ToonValue): UIO[String]
  def fromJson(json: String): IO[String, ToonValue]
  def calculateSavings(value: ToonValue): URIO[ToonEncoderService, TokenSavings]
  def toPrettyJson(value: ToonValue, indent: Int = 2): UIO[String]
}
```

**TokenSavings** provides:
- `jsonSize: Int` - JSON character count
- `toonSize: Int` - TOON character count
- `savings: Int` - Absolute character difference
- `savingsPercent: Double` - Percentage saved (0-100)

#### ToonSchemaService
Type-safe encoding/decoding using zio-schema.

```scala
trait ToonSchemaService {
  def encode[A](value: A)(using schema: Schema[A]): IO[String, String]
  def decode[A](toonString: String)(using schema: Schema[A]): IO[String, A]
  def encodeWithConfig[A](value: A, config: EncoderConfig)(using schema: Schema[A]): IO[String, String]
  def decodeWithConfig[A](toonString: String, config: DecoderConfig)(using schema: Schema[A]): IO[String, A]
}
```

### Configuration

#### EncoderConfig
```scala
case class EncoderConfig(
  indentSize: Int = 2,
  delimiter: Delimiter = Delimiter.Comma
)
```

#### DecoderConfig
```scala
case class DecoderConfig(
  strictMode: Boolean = true,
  indentSize: Int = 2,
  expandPaths: PathExpansion = PathExpansion.Off
)
```

### Layers

Provide services at your application entry point:

```scala
// Basic TOON encoding/decoding
val toonLayer = Toon.live

// With JSON integration
val jsonLayer = ToonJsonService.live ++ ToonEncoderService.live

// With schema support
val schemaLayer = ToonJsonService.live >>> ToonSchemaService.live

// Complete integration
val fullLayer = ToonEncoderService.live ++ ToonDecoderService.live ++ ToonJsonService.live
```

## Specification

This implementation follows the official [TOON Format Specification v2.0](https://github.com/toon-format/spec).

## License

MIT License - see LICENSE file for details.
