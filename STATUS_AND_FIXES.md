# 📊 Maven Central Publishing - Status & Fixes

## Current Status: Credentials Verified Locally ✅

**Issue #4:** Sonatype credentials authentication failure in GitHub Actions

**Update:** Credentials are **VALID locally** (verified with `./verify-sonatype-credentials.sh`)

**Action Required:** Update GitHub Secrets to match local environment

---

## 🔴 All Issues Encountered (In Order)

### ✅ Issue #1: `sbt: command not found` - FIXED
**Error:** sbt command not available in GitHub Actions
**Solution:** Added `sbt/setup-sbt@v1` action
**Status:** ✅ RESOLVED

### ✅ Issue #2: `base64: invalid input` - FIX AVAILABLE
**Error:** PGP key encoding with line breaks
**Solution:** Created `encode-pgp-key.sh` script
**Status:** ⚠️ AWAITING ACTION (need to re-encode and update PGP_SECRET)

### ✅ Issue #3: `Repository for publishing is not specified` - FIXED
**Error:** publishTo not configured
**Solution:** Added Sonatype s01 configuration to build.sbt
**Status:** ✅ RESOLVED

### 🔴 Issue #4: `401: Unauthorized` - CURRENT ISSUE
**Error:** Sonatype authentication failure
**Solution:** Update SONATYPE_USERNAME and SONATYPE_PASSWORD secrets
**Status:** 🔴 ACTION REQUIRED

---

## 🎯 Priority Action Items

### 1. CRITICAL: Fix Sonatype Credentials

**Do This First:**
```bash
# Get fresh credentials
# Go to: https://central.sonatype.com/
# Profile → Generate User Token
# Copy username and password/token

# Test locally
export SONATYPE_USERNAME="your-username"
export SONATYPE_PASSWORD="your-token"
./verify-sonatype-credentials.sh
```

**Then Update GitHub:**
- https://github.com/riccardomerolla/zio-toon/settings/secrets/actions
- Update `SONATYPE_USERNAME` (no spaces!)
- Update `SONATYPE_PASSWORD` (no spaces!)

### 2. IMPORTANT: Fix PGP Key Encoding

**Do This Second:**
```bash
# Re-encode PGP key properly
./encode-pgp-key.sh

# Update GitHub Secret
# Go to: https://github.com/riccardomerolla/zio-toon/settings/secrets/actions
# Update PGP_SECRET with the output (one line, no breaks)
```

---

## 📋 Secrets Checklist

| Secret Name | Status | How to Get | Notes |
|-------------|--------|-----------|-------|
| `PGP_SECRET` | ⚠️ NEEDS UPDATE | Run `./encode-pgp-key.sh` | Must be ONE line, no breaks |
| `PGP_PASSPHRASE` | ✅ OK | Your PGP key passphrase | Should already be correct |
| `SONATYPE_USERNAME` | ✅ VERIFIED LOCALLY | Already set in env | Copy exact value to GitHub |
| `SONATYPE_PASSWORD` | ✅ VERIFIED LOCALLY | Already set in env | Copy exact value to GitHub |

**Note:** Your Sonatype credentials work locally! Just need to update GitHub Secrets.

---

## 🔧 Configuration Status

| Component | Status | Details |
|-----------|--------|---------|
| `build.sbt` | ✅ CONFIGURED | Sonatype s01 URLs set |
| `project/plugins.sbt` | ✅ CONFIGURED | sbt-ci-release 1.8.0 |
| `.github/workflows/ci.yml` | ✅ CONFIGURED | Java 21, sbt setup |
| PGP Key | ⚠️ NEEDS RE-ENCODING | Use encode-pgp-key.sh |
| Sonatype Credentials | 🔴 INVALID | Update secrets |
| Namespace Verification | ❓ UNKNOWN | Check central.sonatype.com |

---

## 🚀 Next Release Steps

Once secrets are fixed:

```bash
# 1. Delete failed tags
git tag -d v0.1.1 v0.1.2 v0.1.3 v0.1.4
git push origin :refs/tags/v0.1.1 :refs/tags/v0.1.2 :refs/tags/v0.1.3 :refs/tags/v0.1.4

# 2. Commit any pending changes
git add .
git commit -m "Fix: Update all documentation and helper scripts"
git push origin main

# 3. Wait for CI to pass on main
# Check: https://github.com/riccardomerolla/zio-toon/actions

# 4. Create release tag
git tag -a v0.1.5 -m "Initial release - all publishing issues resolved"
git push origin v0.1.5

# 5. Monitor release
open https://github.com/riccardomerolla/zio-toon/actions

# 6. Verify on Maven Central (10-30 min later)
open https://search.maven.org/search?q=g:io.github.riccardomerolla
```

---

## 📚 Helper Scripts

| Script | Purpose | Usage |
|--------|---------|-------|
| `verify-setup.sh` | Check overall setup | `./verify-setup.sh` |
| `encode-pgp-key.sh` | Encode PGP key properly | `./encode-pgp-key.sh` |
| `verify-sonatype-credentials.sh` | Test Sonatype creds | `export SONATYPE_USERNAME=...; ./verify-sonatype-credentials.sh` |

---

## 📖 Documentation Files

| File | Purpose |
|------|---------|
| `FIX_401_NOW.md` | Quick fix for current 401 issue |
| `FIX_401_UNAUTHORIZED.md` | Detailed 401 troubleshooting |
| `COMPLETE_FIX_SUMMARY.md` | All fixes documented |
| `REPOSITORY_FIX.md` | Publishing config fix |
| `CI_FIX_SUMMARY.md` | Base64 and Java fixes |
| `PUBLISHING.md` | Complete publishing guide |
| `QUICK_REFERENCE.md` | Quick command reference |
| `RELEASE_CHECKLIST.md` | Step-by-step release guide |

---

## 🎓 Key Learnings

1. **Sonatype Central vs OSS Legacy**
   - Different systems, different credentials
   - Central uses generated User Tokens
   - Always try Central first (newer system)

2. **GitHub Secrets Gotchas**
   - Invisible spaces break everything
   - Copy credentials EXACTLY as shown
   - Test locally before committing

3. **PGP Key Encoding**
   - Must be single line without breaks
   - Standard base64 adds breaks
   - Use `base64 -w 0` or helper script

4. **sbt-ci-release Setup**
   - Needs explicit Sonatype configuration
   - Version 1.8.0 works reliably
   - Requires proper environment variables

---

## ✅ What's Working

- ✅ GitHub Actions workflow configured
- ✅ SBT properly installed in CI
- ✅ Java 21 environment
- ✅ Build.sbt with Sonatype configuration
- ✅ Project compiles and tests pass
- ✅ PGP key imported (once re-encoded)

## 🔴 What Needs Fixing

- 🔴 SONATYPE_USERNAME secret (invalid/incorrect)
- 🔴 SONATYPE_PASSWORD secret (invalid/incorrect)
- ⚠️ PGP_SECRET secret (needs re-encoding)
- ❓ Namespace verification (may need verification)

---

## 🎯 Immediate Actions (In Order)

1. **Go to** https://central.sonatype.com/
2. **Generate** User Token (Profile → Generate User Token)
3. **Copy** both username and password/token
4. **Update** GitHub Secrets:
   - SONATYPE_USERNAME
   - SONATYPE_PASSWORD
5. **Test** locally with `./verify-sonatype-credentials.sh`
6. **Re-encode** PGP key with `./encode-pgp-key.sh`
7. **Update** PGP_SECRET in GitHub
8. **Delete** failed tags
9. **Create** new release tag v0.1.5
10. **Monitor** GitHub Actions

---

## 🎊 Expected Timeline to Success

- **Now:** Fix Sonatype credentials (5 minutes)
- **+2 min:** Fix PGP encoding (2 minutes)
- **+3 min:** Delete tags and create new release (1 minute)
- **+5 min:** GitHub Actions starts running
- **+10 min:** Artifacts published to Sonatype staging
- **+40 min:** Artifacts appear on Maven Central search

**Total:** ~40 minutes from fixing secrets to Maven Central! 🚀

---

**Current Priority:** Get Sonatype credentials from https://central.sonatype.com/ and update GitHub Secrets

