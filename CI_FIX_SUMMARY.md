# 🔧 CI Fixes Applied - Base64 Encoding & Java 21

## Issues Fixed

### 1. ❌ Base64 Invalid Input Error
**Error Message:**
```
java.lang.RuntimeException: base64: invalid input
at com.geirsson.CiReleasePlugin$.setupGpg
```

**Root Cause:**
The `PGP_SECRET` GitHub Secret was base64 encoded with line breaks, which causes the decoding to fail during the signing process.

**Solution:**
- Updated documentation with proper encoding instructions
- Created `encode-pgp-key.sh` helper script
- PGP key must be base64 encoded in a **single line without line breaks**

### 2. ✅ Java Version Updated
**Change:** CI now uses only Java 21 (removed Java 11 and 17 from matrix)

## Files Modified

### 1. `.github/workflows/ci.yml`
- ✅ Removed Java version matrix (11, 17, 21)
- ✅ Now uses only Java 21 for both test and publish jobs
- ✅ Simplified workflow without matrix strategy

### 2. `PUBLISHING.md`
- ✅ Added detailed PGP key encoding instructions
- ✅ Added `base64 -w 0` flag to remove line breaks
- ✅ Added macOS alternative: `base64 | tr -d '\n'`
- ✅ Added Windows PowerShell instructions
- ✅ New troubleshooting section for "base64: invalid input"

### 3. `QUICK_REFERENCE.md`
- ✅ Added quick troubleshooting entry for base64 error
- ✅ Updated with single-line encoding command

### 4. `encode-pgp-key.sh` (NEW)
- ✅ Interactive script to properly encode PGP keys
- ✅ Validates gpg installation
- ✅ Lists available keys
- ✅ Encodes key in single line without breaks
- ✅ Saves to file and copies to clipboard
- ✅ Provides step-by-step GitHub instructions

## How to Fix Your PGP_SECRET

### Option 1: Use the Helper Script (Recommended)

```bash
./encode-pgp-key.sh
```

This will:
1. List your available PGP keys
2. Prompt for the email
3. Properly encode the key (single line, no breaks)
4. Save to `pgp-secret-encoded.txt`
5. Copy to clipboard (if available)
6. Show you exactly what to do next

### Option 2: Manual Encoding

**On macOS/Linux:**
```bash
# Export and encode in ONE LINE
gpg --armor --export-secret-keys YOUR_EMAIL@example.com | base64 -w 0

# Or if -w 0 doesn't work (macOS):
gpg --armor --export-secret-keys YOUR_EMAIL@example.com | base64 | tr -d '\n'
```

**On Windows PowerShell:**
```powershell
$key = gpg --armor --export-secret-keys YOUR_EMAIL@example.com | Out-String
[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($key))
```

### Update GitHub Secret

1. Go to: https://github.com/riccardomerolla/zio-toon/settings/secrets/actions
2. Click on `PGP_SECRET` to edit it
3. Paste the newly encoded key (should be ONE very long line)
4. Save

**Verification:** The secret should be one continuous string with no spaces or line breaks.

## Updated CI Workflow

```yaml
jobs:
  test:
    name: Test
    runs-on: ubuntu-latest
    steps:
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'  # ← Changed from matrix
          cache: 'sbt'

  publish:
    name: Publish to Maven Central
    runs-on: ubuntu-latest
    steps:
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'  # ← Changed from 11 to 21
          cache: 'sbt'
```

## Testing the Fix

### 1. Re-encode your PGP key
```bash
./encode-pgp-key.sh
```

### 2. Update the GitHub Secret
Follow the instructions from the script output.

### 3. Delete the old tags (if any)
```bash
git tag -d v0.1.1
git push origin :refs/tags/v0.1.1
```

### 4. Commit the changes
```bash
git add .
git commit -m "Fix: Update CI to Java 21 and fix PGP key encoding instructions"
git push origin main
```

### 5. Wait for CI to pass
Check: https://github.com/riccardomerolla/zio-toon/actions

### 6. Create a new release
```bash
git tag -a v0.1.1 -m "Release version 0.1.1"
git push origin v0.1.1
```

## Why This Matters

### Base64 Line Breaks Issue
- Standard `base64` command adds line breaks every 76 characters
- The `sbt-ci-release` plugin's GPG setup expects a single-line string
- When it tries to decode a multi-line base64 string, it fails with "invalid input"
- The `-w 0` flag (or `tr -d '\n'`) removes all line breaks

### Java 21
- Simplified CI workflow (no matrix complexity)
- Faster CI runs (single Java version)
- Modern Java LTS version with better performance
- Aligns with your local development environment

## Expected Result

After updating `PGP_SECRET` and pushing a new tag, you should see:

```
✅ Test job passes (Java 21)
✅ GPG key imports successfully
✅ Artifacts signed with PGP
✅ Published to Maven Central
✅ GitHub release created
```

## Troubleshooting

If it still fails:

1. **Double-check the secret:**
   ```bash
   # Re-run the encoding script
   ./encode-pgp-key.sh
   ```

2. **Verify key length:**
   - The encoded key should be 8000-15000 characters
   - All in ONE line
   - No spaces or line breaks

3. **Check passphrase:**
   - Ensure `PGP_PASSPHRASE` matches your actual passphrase
   - No extra spaces or characters

4. **Test locally:**
   ```bash
   # Verify your key works
   echo "test" | gpg --clearsign --local-user YOUR_EMAIL@example.com
   ```

---

**Status:** ✅ Ready to fix and deploy

**Next Action:** Run `./encode-pgp-key.sh` to properly encode your PGP key

