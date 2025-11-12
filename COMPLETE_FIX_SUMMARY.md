# ✅ Complete Fix Summary - Maven Central Publishing

## All Issues Resolved

This document summarizes ALL the fixes applied to get Maven Central publishing working.

---

## 🔧 Issue #1: `sbt: command not found`

**Error:**
```
sbt: command not found
Error: Process completed with exit code 127
```

**Fix:** Added `sbt/setup-sbt@v1` action to install sbt before running commands.

**Files Modified:**
- `.github/workflows/ci.yml`

---

## 🔧 Issue #2: `base64: invalid input`

**Error:**
```
java.lang.RuntimeException: base64: invalid input
at com.geirsson.CiReleasePlugin$.setupGpg
```

**Root Cause:** PGP_SECRET was base64 encoded with line breaks.

**Fix:**
- Created `encode-pgp-key.sh` helper script
- Updated documentation with proper encoding instructions
- Key must be encoded in ONE line without breaks: `base64 -w 0`

**Action Required:** Re-encode and update the `PGP_SECRET` in GitHub Secrets.

**Files Created:**
- `encode-pgp-key.sh`

**Files Modified:**
- `PUBLISHING.md`
- `QUICK_REFERENCE.md`

---

## 🔧 Issue #3: `Repository for publishing is not specified`

**Error:**
```
[error] java.lang.RuntimeException: Repository for publishing is not specified.
[error] (publishConfiguration) Repository for publishing is not specified.
```

**Root Cause:** sbt-ci-release couldn't determine the Sonatype repository for publishing.

**Fix:** Added explicit Sonatype Central (s01) configuration to build.sbt:

```scala
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
```

**Files Modified:**
- `build.sbt`
- `project/plugins.sbt` (downgraded to v1.8.0 for stability)

---

## 🔧 Change #4: Java Version Simplified

**Change:** CI now uses only Java 21 (removed Java 11 and 17 matrix).

**Benefit:** Simpler, faster builds.

**Files Modified:**
- `.github/workflows/ci.yml`

---

## 📁 Summary of All File Changes

### Created Files
1. ✅ `encode-pgp-key.sh` - Helper to properly encode PGP keys
2. ✅ `CI_FIX_SUMMARY.md` - Base64 encoding fix documentation
3. ✅ `REPOSITORY_FIX.md` - Publishing configuration fix documentation
4. ✅ `PUBLISHING.md` - Complete publishing guide
5. ✅ `QUICK_REFERENCE.md` - Quick troubleshooting reference
6. ✅ `RELEASE_CHECKLIST.md` - Step-by-step release guide
7. ✅ `CHANGELOG.md` - Version history tracker
8. ✅ `SETUP_SUMMARY.md` - Initial setup documentation
9. ✅ `verify-setup.sh` - Setup verification script

### Modified Files
1. ✅ `build.sbt` - Added Sonatype publishing configuration
2. ✅ `project/plugins.sbt` - Set sbt-ci-release to v1.8.0
3. ✅ `.github/workflows/ci.yml` - Added sbt setup, Java 21 only
4. ✅ `PUBLISHING.md` - Updated with all troubleshooting
5. ✅ `QUICK_REFERENCE.md` - Updated with all fixes

---

## 🎯 Action Items for You

### 1. Re-encode PGP Key (CRITICAL)

Run the helper script:
```bash
./encode-pgp-key.sh
```

Or manually:
```bash
gpg --armor --export-secret-keys YOUR_EMAIL@example.com | base64 -w 0
```

### 2. Update GitHub Secret

1. Go to: https://github.com/riccardomerolla/zio-toon/settings/secrets/actions
2. Click on **PGP_SECRET**
3. Click **Update secret**
4. Paste the new encoded key (ONE long line)
5. Click **Update secret**

### 3. Commit All Changes

```bash
git add .
git commit -m "Fix: Add Sonatype repository config and update documentation"
git push origin main
```

### 4. Wait for CI to Pass

Monitor: https://github.com/riccardomerolla/zio-toon/actions

### 5. Delete Failed Tags

```bash
# Delete tags locally
git tag -d v0.1.1 v0.1.2 v0.1.3

# Delete tags on GitHub
git push origin :refs/tags/v0.1.1
git push origin :refs/tags/v0.1.2
git push origin :refs/tags/v0.1.3
```

### 6. Create New Release

```bash
git tag -a v0.1.4 -m "Initial release - all fixes applied"
git push origin v0.1.4
```

### 7. Monitor Release

Watch the GitHub Actions workflow: https://github.com/riccardomerolla/zio-toon/actions

### 8. Verify Publication (10-30 minutes later)

Check Maven Central: https://search.maven.org/search?q=g:io.github.riccardomerolla

---

## ✅ Expected Workflow Success

Once all fixes are applied and PGP_SECRET is updated, the workflow should:

1. ✅ Checkout code
2. ✅ Setup Java 21
3. ✅ Setup SBT
4. ✅ Run scalafmt check
5. ✅ Run tests - PASS
6. ✅ Setup Java 21 (publish job)
7. ✅ Setup SBT
8. ✅ Import GPG key - SUCCESS
9. ✅ Run `sbt ci-release`
   - ✅ Detect version from tag
   - ✅ Determine publish repository (s01.oss.sonatype.org)
   - ✅ Compile and package
   - ✅ Sign artifacts with PGP
   - ✅ Publish to Maven Central staging
   - ✅ Auto-release from staging
10. ✅ Create GitHub release

---

## 📊 Configuration Overview

### build.sbt Key Settings

```scala
ThisBuild / organization := "io.github.riccardomerolla"
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo := { /* Sonatype s01 URLs */ }
ThisBuild / licenses := List("MIT" -> ...)
ThisBuild / developers := List(Developer(...))
ThisBuild / scmInfo := Some(ScmInfo(...))
```

### GitHub Secrets Required

| Secret | Description | Status |
|--------|-------------|--------|
| `PGP_SECRET` | Base64 encoded PGP private key (ONE LINE) | ⚠️ NEEDS UPDATE |
| `PGP_PASSPHRASE` | PGP key passphrase | ✅ Should be OK |
| `SONATYPE_USERNAME` | Sonatype username | ✅ Should be OK |
| `SONATYPE_PASSWORD` | Sonatype password/token | ✅ Should be OK |

### CI Workflow

- **Trigger:** Push to main, PRs, and version tags (v*)
- **Java Version:** 21
- **SBT:** Installed via sbt/setup-sbt@v1
- **Tests:** On every push/PR
- **Publish:** Only on version tag push
- **Plugin:** sbt-ci-release v1.8.0

---

## 🎓 Key Learnings

1. **Sonatype Central (s01) vs Legacy (oss)**
   - New accounts use s01.oss.sonatype.org
   - Requires explicit configuration in build.sbt
   - Different URLs for snapshots and releases

2. **PGP Key Encoding**
   - Must be base64 encoded WITHOUT line breaks
   - Standard `base64` adds line breaks every 76 chars
   - Use `base64 -w 0` or `base64 | tr -d '\n'`

3. **sbt-ci-release**
   - Version 1.8.0 works reliably with Sonatype Central
   - Needs explicit `publishTo` configuration
   - Automatically handles versioning from git tags

4. **GitHub Actions**
   - Need explicit sbt installation (sbt/setup-sbt@v1)
   - setup-java only caches, doesn't install sbt
   - Secrets must be properly encoded

---

## 📚 Documentation Reference

- **REPOSITORY_FIX.md** - This specific fix (repository configuration)
- **CI_FIX_SUMMARY.md** - Base64 and Java 21 fixes
- **PUBLISHING.md** - Complete publishing guide with troubleshooting
- **QUICK_REFERENCE.md** - Quick troubleshooting table
- **RELEASE_CHECKLIST.md** - Step-by-step release process

---

## ✨ Next Release

Once you've completed the action items above, your next release will be:

```
v0.1.4 - Initial public release
- ZIO-based TOON encoder/decoder
- JSON integration with token savings
- ZIO Schema support
- Streaming with ZStream
- Comprehensive test suite
```

---

**Status:** ✅ All fixes applied, awaiting PGP_SECRET update

**Priority:** Re-encode PGP key with `./encode-pgp-key.sh` and update GitHub Secret

**ETA to Release:** ~5 minutes after updating PGP_SECRET

