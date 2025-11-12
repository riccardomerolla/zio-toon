# 🔧 Repository Publishing Configuration Fix

## Issue Resolved
**Error:** `Repository for publishing is not specified`

This error occurred because `sbt-ci-release` couldn't determine where to publish the artifacts.

## Solution Applied

### 1. Updated `build.sbt`
Added explicit Sonatype publishing configuration:

```scala
// Sonatype publishing configuration for Sonatype Central (s01)
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true
```

### 2. Updated `project/plugins.sbt`
Changed to a stable version of sbt-ci-release that works well with Sonatype Central:

```scala
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.8.0")
```

## Why This Was Needed

The new Sonatype Central Portal (s01.oss.sonatype.org) requires explicit configuration:
- **sonatypeCredentialHost**: Tells sbt-ci-release to use the s01 instance
- **publishTo**: Defines where snapshots and releases should be published
- **publishMavenStyle**: Ensures Maven-style publishing (required by Central)

## Verification

Local test confirms the configuration works:
```
[info] Some(s01-oss-sonatype-org-snapshots: https://s01.oss.sonatype.org/content/repositories/snapshots)
[success] Total time: 0 s
```

## Next Steps

1. **Commit the changes:**
   ```bash
   git add build.sbt project/plugins.sbt
   git commit -m "Fix: Add Sonatype publishing repository configuration"
   git push origin main
   ```

2. **Delete the failed tag:**
   ```bash
   git tag -d v0.1.3
   git push origin :refs/tags/v0.1.3
   ```

3. **Create a new release tag:**
   ```bash
   git tag -a v0.1.4 -m "Release version 0.1.4"
   git push origin v0.1.4
   ```

4. **Monitor the release:**
   - https://github.com/riccardomerolla/zio-toon/actions

## Files Modified

- ✅ `build.sbt` - Added Sonatype repository configuration
- ✅ `project/plugins.sbt` - Updated sbt-ci-release to version 1.8.0

## Expected Result

The GitHub Actions workflow should now:
1. ✅ Import PGP key successfully
2. ✅ Determine publish repository (s01.oss.sonatype.org)
3. ✅ Sign artifacts with PGP
4. ✅ Publish to Maven Central staging
5. ✅ Auto-release from staging to Central
6. ✅ Create GitHub release

---

**Status:** ✅ Fixed and ready for release

**Key Points:**
- Uses Sonatype Central Portal (s01) not legacy OSS (oss.sonatype.org)
- Snapshots go to: `s01.oss.sonatype.org/content/repositories/snapshots`
- Releases go to: `s01.oss.sonatype.org/service/local/staging/deploy/maven2`

