# toon4s

A Scala 3 / ZIO 2.1 implementation of TOON (Token-Oriented Object Notation), a compact serialization format optimized to reduce token usage when interacting with Large Language Models (LLMs).

## What is TOON?

TOON is a line-oriented, indentation-based text format that encodes the JSON data model with explicit structure and minimal quoting. It typically reduces token usage by 30-60% compared to JSON, making it ideal for LLM interactions where context window and token costs matter.

### Key Features

- **Token-efficient**: 30-60% fewer tokens than JSON
- **Explicit schema**: Array lengths and field names declared once
- **Minimal syntax**: Eliminates redundant punctuation (braces, brackets, quotes)
- **Tabular arrays**: CSV-like format for uniform data sets
- **Human-readable**: Indentation-based structure similar to YAML
- **Type-safe**: Full Scala 3 implementation with ADTs
- **ZIO integration**: Native ZIO support for functional error handling

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies += "io.github.riccardomerolla" %% "toon4s" % "0.1.0-SNAPSHOT"
```

## Quick Start

### Basic Encoding

```scala
import io.github.riccardomerolla.toon4s._
import ToonValue._

// Create a TOON value
val data = obj(
  "name" -> str("Alice"),
  "age" -> num(30),
  "active" -> bool(true)
)

// Encode to TOON format
val toon = ToonEncoder.encode(data)
println(toon)
// Output:
// name: Alice
// age: 30
// active: true
```

### Tabular Arrays

TOON excels at encoding arrays of uniform objects:

```scala
val users = obj(
  "users" -> arr(
    obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
    obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("user")),
    obj("id" -> num(3), "name" -> str("Charlie"), "role" -> str("developer"))
  )
)

val toon = ToonEncoder.encode(users)
println(toon)
// Output:
// users[3]{id,name,role}:
//   1,Alice,admin
//   2,Bob,user
//   3,Charlie,developer
```

Compare this to JSON which would require repeating the field names for each object!

### Decoding

```scala
val toonStr = """users[2]{id,name,role}:
  1,Alice,admin
  2,Bob,user"""

val result = ToonDecoder.decode(toonStr)
result match {
  case Right(value) => println(s"Decoded: $value")
  case Left(error) => println(s"Error: ${error.message}")
}
```

### Round-trip Conversion

```scala
val original = obj("name" -> str("Alice"), "age" -> num(30))
val encoded = ToonEncoder.encode(original)
val decoded = ToonDecoder.decode(encoded)
assert(decoded == Right(original))
```

## Comparison with JSON

### JSON (158 tokens)
```json
{
  "users": [
    {"id": 1, "name": "Alice", "role": "admin", "active": true},
    {"id": 2, "name": "Bob", "role": "user", "active": true},
    {"id": 3, "name": "Charlie", "role": "designer", "active": false}
  ]
}
```

### TOON (87 tokens - 45% reduction!)
```
users[3]{id,name,role,active}:
  1,Alice,admin,true
  2,Bob,user,true
  3,Charlie,designer,false
```

## Specification

This implementation follows the official [TOON Format Specification v2.0](https://github.com/toon-format/spec).

## License

MIT License - see LICENSE file for details.
