# Publishing to Maven Central

This project uses [sbt-ci-release](https://github.com/sbt/sbt-ci-release) to automate publishing to Maven Central.

## Prerequisites

You've already completed these steps:

1. ✅ Created a Sonatype Central Account
2. ✅ Generated a PGP Key
3. ✅ Added the sbt-ci-release plugin to `project/plugins.sbt`
4. ✅ Set up GitHub Actions secrets

## Required GitHub Secrets

Ensure the following secrets are configured in your GitHub repository settings (Settings → Secrets and variables → Actions):

- `PGP_SECRET`: Your PGP private key (base64 encoded)
- `PGP_PASSPHRASE`: The passphrase for your PGP key
- `SONATYPE_USERNAME`: Your Sonatype username
- `SONATYPE_PASSWORD`: Your Sonatype password or token

### How to encode your PGP key

```bash
# Export your private key and encode it
gpg --armor --export-secret-keys YOUR_EMAIL@example.com | base64 | pbcopy
```

Then paste the result as the `PGP_SECRET` secret in GitHub.

## How to Release

### For Regular Releases

1. Ensure all changes are merged to `main` and tests pass
2. Create and push a version tag:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
3. The GitHub Action will automatically:
   - Run tests
   - Sign artifacts with your PGP key
   - Publish to Maven Central
   - Create a GitHub release

### For Snapshots (optional)

Snapshots are automatically published on every commit to `main` (if configured). The version will be automatically suffixed with `-SNAPSHOT`.

## Version Naming Convention

Use semantic versioning with the `v` prefix:
- `v0.1.0` - Initial release
- `v0.1.1` - Patch release
- `v0.2.0` - Minor release
- `v1.0.0` - Major release

## Configuration Files

### build.sbt

The following metadata has been added for Maven Central compliance:

```scala
ThisBuild / organization := "io.github.riccardomerolla"
ThisBuild / organizationName := "Riccardo Merolla"
ThisBuild / scmInfo := Some(ScmInfo(...))
ThisBuild / developers := List(Developer(...))
ThisBuild / licenses := List("Apache-2.0" -> ...)
ThisBuild / homepage := Some(url(...))
```

### .github/workflows/ci.yml

The CI workflow:
- Runs tests on multiple Java versions (11, 17, 21)
- Checks code formatting with scalafmt
- Publishes to Maven Central when a version tag is pushed

## Troubleshooting

### Publishing fails with "unauthorized"
- Verify your `SONATYPE_USERNAME` and `SONATYPE_PASSWORD` secrets are correct
- Ensure your Sonatype account is active and verified

### Signing fails
- Check that `PGP_SECRET` is properly base64 encoded
- Verify `PGP_PASSPHRASE` matches your key's passphrase
- Ensure the PGP key hasn't expired

### "Coordinates already exist"
- Each version can only be published once
- Delete the tag locally and on GitHub, increment the version, and try again:
  ```bash
  git tag -d v0.1.0
  git push origin :refs/tags/v0.1.0
  ```

## Verifying Publication

After publishing:
1. Check [Maven Central Search](https://search.maven.org/) for your artifact (may take 10-30 minutes)
2. Your artifact will be available at:
   ```
   https://repo1.maven.org/maven2/io/github/riccardomerolla/zio-toon_3/
   ```

## Using the Published Library

Once published, users can add your library to their `build.sbt`:

```scala
libraryDependencies += "io.github.riccardomerolla" %% "zio-toon" % "0.1.0"
```

## Additional Resources

- [sbt-ci-release documentation](https://github.com/sbt/sbt-ci-release)
- [Sonatype Central documentation](https://central.sonatype.org/publish/publish-guide/)
- [GitHub Actions documentation](https://docs.github.com/en/actions)

