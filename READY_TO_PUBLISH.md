# ✅ Credentials Verified! Ready to Publish

## Good News! 🎉

Your Sonatype credentials are **VALID** locally:
- ✅ Username: `2pjD83` (partial, shown in script output)
- ✅ Password/Token: Configured correctly
- ✅ Sonatype host: `s01.oss.sonatype.org`
- ✅ Publish configuration: Correct

---

## 🎯 Final Steps to Publish (3 Actions)

### Step 1: Update GitHub Secrets

Your local credentials work! Now update them in GitHub:

1. **Go to:** https://github.com/riccardomerolla/zio-toon/settings/secrets/actions

2. **Update SONATYPE_USERNAME:**
   - Click **SONATYPE_USERNAME** → **Update secret**
   - Value: `2pjD83` (copy from your terminal exactly)
   - Click **Update secret**

3. **Update SONATYPE_PASSWORD:**
   - Click **SONATYPE_PASSWORD** → **Update secret**
   - Paste your full token (the one starting with `RqmZc...`)
   - **IMPORTANT:** Copy the ENTIRE token with NO spaces
   - Click **Update secret**

### Step 2: Also Update PGP_SECRET (If Not Done)

```bash
# Re-encode PGP key properly
./encode-pgp-key.sh

# Then update PGP_SECRET in GitHub Secrets
# https://github.com/riccardomerolla/zio-toon/settings/secrets/actions
```

### Step 3: Create Release

```bash
# 1. Commit all the documentation and fixes
git add .
git commit -m "Add: Complete Maven Central publishing setup with documentation"
git push origin main

# 2. Wait for CI to pass (check: https://github.com/riccardomerolla/zio-toon/actions)

# 3. Delete old failed tags
git tag -d v0.1.1 v0.1.2 v0.1.3 v0.1.4
git push origin :refs/tags/v0.1.1 :refs/tags/v0.1.2 :refs/tags/v0.1.3 :refs/tags/v0.1.4

# 4. Create new release tag
git tag -a v0.1.5 -m "Initial release - Maven Central publishing configured"
git push origin v0.1.5

# 5. Monitor the release
open https://github.com/riccardomerolla/zio-toon/actions
```

---

## ⚠️ Important Note

The credentials worked **locally**, which is great! However:

- The **401 error in GitHub Actions** means the secrets in GitHub are either:
  - Wrong (different from what you tested locally)
  - Have extra spaces (invisible but breaks auth)
  - Not updated yet

Make sure to:
1. ✅ Copy credentials EXACTLY as they appear in your terminal
2. ✅ No leading or trailing spaces
3. ✅ Update BOTH username AND password/token

---

## 🔍 What the 401 Error Means

The error you saw in GitHub Actions earlier:
```
[401: Unauthorized] 
wvlet.airframe.http.HttpClientException
```

This happened because:
- GitHub Secrets had old/wrong credentials
- Your local environment has the CORRECT credentials
- Once you update GitHub Secrets → ✅ Publishing will work!

---

## 📋 Checklist Before Creating Tag

- [ ] Updated `SONATYPE_USERNAME` in GitHub Secrets
- [ ] Updated `SONATYPE_PASSWORD` in GitHub Secrets  
- [ ] Updated `PGP_SECRET` in GitHub Secrets (run `./encode-pgp-key.sh`)
- [ ] Committed all changes to main
- [ ] CI passes on main branch
- [ ] Deleted old failed tags
- [ ] Ready to create `v0.1.5` tag

---

## 🎊 Expected Timeline

Once you update the GitHub Secrets and push the tag:

- **+0 min:** Tag pushed, GitHub Actions starts
- **+2 min:** Tests pass
- **+3 min:** PGP key imported ✅
- **+5 min:** Build and package complete
- **+7 min:** Artifacts signed ✅
- **+10 min:** Published to Sonatype staging ✅
- **+12 min:** Auto-released to Maven Central ✅
- **+15 min:** GitHub release created ✅
- **+30 min:** Searchable on Maven Central 🎉

Total: **~30-40 minutes** from tag push to Maven Central!

---

## 🚀 After Publishing

Your library will be available as:

```scala
libraryDependencies += "io.github.riccardomerolla" %% "zio-toon" % "0.1.5"
```

Check at:
- Maven Central Search: https://search.maven.org/search?q=g:io.github.riccardomerolla
- Direct URL: https://repo1.maven.org/maven2/io/github/riccardomerolla/zio-toon_3/

---

## 🎯 TL;DR - Do This Now

1. **Update GitHub Secrets** (use exact values from your terminal):
   - `SONATYPE_USERNAME` = the username shown by the script
   - `SONATYPE_PASSWORD` = your full token
   - `PGP_SECRET` = run `./encode-pgp-key.sh` and use output

2. **Push everything:**
   ```bash
   git add . && git commit -m "Complete publishing setup" && git push
   ```

3. **Create release:**
   ```bash
   git tag -a v0.1.5 -m "Release" && git push origin v0.1.5
   ```

4. **Watch it work!**
   https://github.com/riccardomerolla/zio-toon/actions

---

**You're 99% there! Just update the GitHub Secrets and push the tag!** 🚀

