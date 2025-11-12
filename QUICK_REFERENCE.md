# 🚀 Quick Reference: Publishing to Maven Central

## One-Command Verification
```bash
./verify-setup.sh
```

## Release a New Version

```bash
# 1. Tag the version
git tag -a v0.1.0 -m "Release version 0.1.0"

# 2. Push the tag (triggers automatic publishing)
git push origin v0.1.0
```

That's it! GitHub Actions will automatically:
- ✅ Run tests
- ✅ Sign artifacts with PGP
- ✅ Publish to Maven Central
- ✅ Create GitHub release

## Monitor Release Progress

```bash
# Open GitHub Actions in browser
open https://github.com/riccardomerolla/zio-toon/actions
```

## Verify Publication

```bash
# Check Maven Central (wait 10-30 minutes)
open https://search.maven.org/search?q=g:io.github.riccardomerolla
```

## Users Will Add Your Library

```scala
libraryDependencies += "io.github.riccardomerolla" %% "zio-toon" % "0.1.0"
```

## Required GitHub Secrets

Already configured (if you followed the issue):
- `PGP_SECRET` - Your PGP private key (base64 encoded)
- `PGP_PASSPHRASE` - Your PGP key passphrase  
- `SONATYPE_USERNAME` - Your Sonatype username
- `SONATYPE_PASSWORD` - Your Sonatype password/token

## Version Numbering

- `v0.1.0` → `v0.1.1` - Patch (bug fixes)
- `v0.1.0` → `v0.2.0` - Minor (new features)
- `v0.1.0` → `v1.0.0` - Major (breaking changes)

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `sbt: command not found` | Workflow updated with `sbt/setup-sbt@v1` action |
| Publishing fails | Check GitHub Secrets are correct |
| Signing fails | Verify PGP_SECRET is base64 encoded |
| Tests fail | Run `sbt test` locally first |
| Format issues | Run `sbt scalafmtAll` |

## Full Documentation

- 📖 **[PUBLISHING.md](PUBLISHING.md)** - Complete guide
- ✅ **[RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)** - Step-by-step checklist
- 📋 **[SETUP_SUMMARY.md](SETUP_SUMMARY.md)** - What was configured
- 📝 **[CHANGELOG.md](CHANGELOG.md)** - Track versions

## Emergency: Undo a Tag

```bash
# Delete local tag
git tag -d v0.1.0

# Delete remote tag
git push origin :refs/tags/v0.1.0

# Create corrected version
git tag -a v0.1.1 -m "Fixed version"
git push origin v0.1.1
```

---

**Need help?** Check the full documentation files listed above.

