# GitHub Actions Setup Summary

## ✅ Completed Setup for Maven Central Publishing

This document summarizes all the changes made to enable automated publishing to Maven Central via GitHub Actions.

## Files Created

### 1. `.github/workflows/ci.yml`
**Purpose**: Main CI/CD pipeline

**Features**:
- Runs tests on every push to `main` and on pull requests
- Tests against Java 11, 17, and 21
- Checks code formatting with scalafmt
- Uses `sbt/setup-sbt@v1` action to ensure sbt is available
- Publishes to Maven Central automatically when a version tag (e.g., `v0.1.0`) is pushed
- Uses GitHub Secrets for secure credential management

### 2. `PUBLISHING.md`
**Purpose**: Complete guide for publishing to Maven Central

**Contains**:
- Required GitHub Secrets configuration
- Step-by-step release instructions
- Troubleshooting guide
- Version naming conventions
- Verification steps

### 3. `RELEASE_CHECKLIST.md`
**Purpose**: Step-by-step checklist for releases

**Contains**:
- Pre-release checklist
- Release process steps
- Post-release verification
- Rollback procedures
- Emergency hotfix workflow

### 4. `CHANGELOG.md`
**Purpose**: Track all changes between versions

**Format**: Follows [Keep a Changelog](https://keepachangelog.com/) conventions

## Files Modified

### 1. `build.sbt`
**Changes Made**:

```scala
// Added Maven Central required metadata
ThisBuild / organizationName := "Riccardo Merolla"
ThisBuild / organizationHomepage := Some(url("https://github.com/riccardomerolla"))

ThisBuild / scmInfo := Some(ScmInfo(...))
ThisBuild / developers := List(Developer(...))
ThisBuild / licenses := List("Apache-2.0" -> ...)
ThisBuild / homepage := Some(url(...))
ThisBuild / versionScheme := Some("early-semver")

// Added description to main project
description := "A Scala 3 / ZIO 2.x implementation of TOON..."

// Marked benchmarks as non-publishable
publish / skip := true  // in benchmarks project
```

## How It Works

### Continuous Integration (CI)

1. **On every push to `main` or PR**:
   - Checks out code
   - Sets up Java (11, 17, 21)
   - Runs `sbt scalafmtCheckAll` to verify formatting
   - Runs `sbt test` to execute all tests

### Continuous Deployment (CD)

2. **On pushing a version tag** (e.g., `v0.1.0`):
   - Waits for CI tests to pass
   - Checks out code with full git history
   - Sets up Java 11
   - Imports PGP key from GitHub Secrets
   - Runs `sbt ci-release` which:
     - Determines version from git tag
     - Compiles and packages the library
     - Signs artifacts with PGP key
     - Publishes to Maven Central
     - Creates GitHub release

## GitHub Secrets Required

Ensure these secrets are set in your repository (Settings → Secrets and variables → Actions):

| Secret Name | Description |
|-------------|-------------|
| `PGP_SECRET` | Base64-encoded PGP private key |
| `PGP_PASSPHRASE` | Passphrase for PGP key |
| `SONATYPE_USERNAME` | Sonatype/Maven Central username |
| `SONATYPE_PASSWORD` | Sonatype/Maven Central password/token |

## Next Steps: First Release

To publish your first version (v0.1.0):

1. **Ensure everything is ready**:
   ```bash
   sbt clean test
   sbt scalafmtCheckAll
   ```

2. **Commit and push all changes**:
   ```bash
   git add .
   git commit -m "Setup GitHub Actions for Maven Central publishing"
   git push origin main
   ```

3. **Wait for CI to pass** on GitHub Actions

4. **Create and push the version tag**:
   ```bash
   git tag -a v0.1.0 -m "Initial release v0.1.0"
   git push origin v0.1.0
   ```

5. **Monitor the release**:
   - Go to: https://github.com/riccardomerolla/zio-toon/actions
   - Watch the "Publish to Maven Central" job

6. **Verify publication** (10-30 minutes later):
   - Check: https://search.maven.org/search?q=g:io.github.riccardomerolla%20AND%20a:zio-toon*
   - Your artifact will be at: `io.github.riccardomerolla:zio-toon_3:0.1.0`

## Testing the Setup

Before creating a real release, you can test that everything is configured correctly:

```bash
# Verify build configuration loads
sbt reload

# Check that publishing configuration is correct
sbt publishLocal

# Verify tests pass
sbt test

# Verify formatting is correct
sbt scalafmtCheckAll
```

## Artifact Coordinates

Once published, users will add your library with:

```scala
libraryDependencies += "io.github.riccardomerolla" %% "zio-toon" % "0.1.0"
```

The `%%` operator automatically appends the Scala version, so users of Scala 3.x will get `zio-toon_3`.

## Additional Features Configured

1. **Multi-version Java testing**: Ensures compatibility with Java 11, 17, and 21
2. **Automated changelog**: Template ready for tracking changes
3. **Semantic versioning**: Configured with `versionScheme`
4. **Benchmarks exclusion**: Benchmark subproject won't be published
5. **License compliance**: Apache 2.0 license properly declared
6. **Developer info**: Properly attributed for Maven Central

## Resources

- [sbt-ci-release docs](https://github.com/sbt/sbt-ci-release)
- [Maven Central Publishing Guide](https://central.sonatype.org/publish/publish-guide/)
- [Semantic Versioning](https://semver.org/)
- [Keep a Changelog](https://keepachangelog.com/)

## Support

For issues or questions about the release process:
1. Check `PUBLISHING.md` for troubleshooting
2. Review `RELEASE_CHECKLIST.md` for step-by-step guidance
3. Check GitHub Actions logs for specific error messages
4. See [Issue #4](https://github.com/riccardomerolla/zio-toon/issues/4) for context

---

**Setup completed by GitHub Copilot** on November 12, 2025

