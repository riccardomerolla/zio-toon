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

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.riccardomerolla" %% "toon4s" % "0.1.0-SNAPSHOT"
```

## Quick Start

### Using Services (Recommended)

```scala
import io.github.riccardomerolla.toon4s._
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
program.provide(Toon.live)
```

### Pure Methods (No ZIO)

For simple use cases without ZIO effects:

```scala
import io.github.riccardomerolla.toon4s._
import ToonValue._

// Pure encoding
val data = obj("name" -> str("Alice"), "age" -> num(30))
val toon = Toon.encode(data, EncoderConfig.default)

// Pure decoding
val result = Toon.decode(toon, DecoderConfig.default)
// Returns: Either[ToonError, ToonValue]
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

## Specification

This implementation follows the official [TOON Format Specification v2.0](https://github.com/toon-format/spec).

## License

MIT License - see LICENSE file for details.
