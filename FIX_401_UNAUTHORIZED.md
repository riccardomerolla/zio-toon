# 🔒 401 Unauthorized - Sonatype Credentials Issue

## Error Details

```
2025-11-12 12:09:01.160Z error [Sonatype]  wvlet.airframe.http.HttpClientException: [401: Unauthorized]
```

**What This Means:** The `SONATYPE_USERNAME` and/or `SONATYPE_PASSWORD` GitHub secrets are incorrect or the credentials don't have permission to access the staging repository.

---

## 🎯 Quick Fix

### Step 1: Determine Your Sonatype System

You need to figure out which Sonatype system you're using:

**Option A: Sonatype Central Portal** (New system, recommended)
- Website: https://central.sonatype.com/
- For accounts created in 2024 or later
- Uses email/username and API tokens

**Option B: Sonatype OSS (Legacy)**
- Website: https://s01.oss.sonatype.org/
- For older accounts
- Uses JIRA username and password

### Step 2: Get Your Credentials

#### For Sonatype Central (Option A - Recommended):

1. Go to: https://central.sonatype.com/
2. Log in with your account
3. Click on your profile → **View Account**
4. Scroll to **User Token** section
5. Click **Generate User Token**
6. Copy both the **Username** and **Password/Token**

**Important:** The generated token is like:
```
Username: AbCdEfGh (random string)
Password: IjKlMnOpQrStUvWxYz0123456789 (long token)
```

#### For Sonatype OSS Legacy (Option B):

1. Go to: https://s01.oss.sonatype.org/
2. Log in with your JIRA credentials
3. Click your username → **Profile**
4. Click **User Token** → **Access User Token**
5. Copy the username and password shown

Or use your JIRA credentials directly:
- Username: Your JIRA username
- Password: Your JIRA password

### Step 3: Update GitHub Secrets

1. Go to: https://github.com/riccardomerolla/zio-toon/settings/secrets/actions

2. Update `SONATYPE_USERNAME`:
   - Click on **SONATYPE_USERNAME**
   - Click **Update secret**
   - Paste your username (from Step 2)
   - **Important:** No spaces before or after!
   - Click **Update secret**

3. Update `SONATYPE_PASSWORD`:
   - Click on **SONATYPE_PASSWORD**
   - Click **Update secret**
   - Paste your password/token (from Step 2)
   - **Important:** Copy the ENTIRE token, no spaces!
   - Click **Update secret**

### Step 4: Verify Locally (Optional but Recommended)

Test your credentials before pushing a new tag:

```bash
export SONATYPE_USERNAME="your-username-here"
export SONATYPE_PASSWORD="your-password-or-token-here"

cd /Users/riccardo/git/github/riccardomerolla/zio-toon
sbt sonatypeList
```

If this works, you'll see something like:
```
[info] Profiles:
[info]  * io.github.riccardomerolla (id: your-profile-id)
```

If it fails with 401, your credentials are still wrong.

### Step 5: Re-run the Release

```bash
# Delete the failed tag
git tag -d v0.1.4
git push origin :refs/tags/v0.1.4

# Create a new tag
git tag -a v0.1.5 -m "Release version 0.1.5"
git push origin v0.1.5
```

---

## 🔍 Troubleshooting

### Still Getting 401?

1. **Check for spaces:**
   ```bash
   # Make sure there are no spaces
   # BAD:  " mytoken123 "
   # GOOD: "mytoken123"
   ```

2. **Try regenerating credentials:**
   - Go back to Sonatype Central
   - Generate a NEW user token
   - Use the new credentials

3. **Verify account is active:**
   - Can you log in to https://central.sonatype.com/ ?
   - Did you verify your email?
   - Is your namespace (io.github.riccardomerolla) approved?

4. **Check namespace verification:**
   - Go to: https://central.sonatype.com/publishing
   - Look for `io.github.riccardomerolla`
   - Status should be "Verified" or "Active"
   - If not verified, you need to verify via GitHub repository

### Namespace Verification (If Not Done)

To publish under `io.github.riccardomerolla`, you need to verify GitHub ownership:

1. Go to: https://central.sonatype.com/publishing
2. Add namespace: `io.github.riccardomerolla`
3. Follow verification steps:
   - Add a specific repository or
   - Add a GitHub verification file

This is a one-time setup.

### Alternative: Use Token Authentication

If using Sonatype Central, always use API tokens instead of your main password:
- More secure
- Can be revoked without changing main password
- Recommended by Sonatype

---

## 📋 Checklist

Before retrying:

- [ ] Determined which Sonatype system I'm using (Central vs OSS)
- [ ] Generated fresh credentials (token for Central, or JIRA creds for OSS)
- [ ] Updated `SONATYPE_USERNAME` in GitHub Secrets (no spaces!)
- [ ] Updated `SONATYPE_PASSWORD` in GitHub Secrets (no spaces!)
- [ ] Tested locally with `sbt sonatypeList` (optional)
- [ ] Verified namespace `io.github.riccardomerolla` is approved
- [ ] Deleted failed tag: `git push origin :refs/tags/v0.1.4`
- [ ] Ready to create new tag: `v0.1.5`

---

## 🎓 Common Mistakes

1. **Using wrong system credentials:**
   - Central credentials don't work with OSS
   - OSS credentials don't work with Central
   - Our build.sbt is configured for s01 (which works with both if using proper creds)

2. **Spaces in secrets:**
   - Copy-paste can add leading/trailing spaces
   - GitHub doesn't show them, but they break authentication

3. **Using account password instead of token:**
   - For Sonatype Central, you MUST use a User Token
   - Account password won't work for API access

4. **Namespace not verified:**
   - You can't publish until `io.github.riccardomerolla` is verified
   - Check: https://central.sonatype.com/publishing

---

## 🚀 Expected Success

Once credentials are correct, you'll see:

```
[info] Preparing a new staging repository for [sbt-sonatype] zio-toon 0.1.5
[info] Reading staging repository profiles...
[info] Creating staging repository...
[info] Created staging repository: iogithubriccardomerolla-1001
[info] Uploading artifacts...
[success] All published artifacts have been successfully uploaded
```

---

## 📞 Need More Help?

1. **Check your Sonatype account:**
   - https://central.sonatype.com/account (for new accounts)
   - https://s01.oss.sonatype.org/ (for legacy accounts)

2. **Sonatype Documentation:**
   - https://central.sonatype.org/publish/publish-guide/

3. **Verify namespace registration:**
   - https://central.sonatype.com/publishing

---

**Most Common Solution:** Regenerate a User Token in Sonatype Central and update both GitHub secrets with NO spaces.

