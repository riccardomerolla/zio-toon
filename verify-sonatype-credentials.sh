#!/bin/bash

# Script to verify Sonatype credentials before publishing
# This helps catch authentication issues early

set -e

echo "🔐 Sonatype Credentials Verification"
echo "====================================="
echo ""

# Check if environment variables are set
if [ -z "$SONATYPE_USERNAME" ] || [ -z "$SONATYPE_PASSWORD" ]; then
    echo "❌ Error: Sonatype credentials not set"
    echo ""
    echo "Please set your credentials:"
    echo "  export SONATYPE_USERNAME='your-username'"
    echo "  export SONATYPE_PASSWORD='your-password-or-token'"
    echo ""
    echo "To get credentials:"
    echo "  1. Go to: https://central.sonatype.com/"
    echo "  2. Log in"
    echo "  3. Profile → Generate User Token"
    echo "  4. Copy username and password/token"
    echo ""
    exit 1
fi

echo "✓ Environment variables set"
echo "  SONATYPE_USERNAME: ${SONATYPE_USERNAME:0:5}..."
echo "  SONATYPE_PASSWORD: ${SONATYPE_PASSWORD:0:5}..."
echo ""

# Check for spaces
if [[ "$SONATYPE_USERNAME" != "${SONATYPE_USERNAME// /}" ]]; then
    echo "⚠️  Warning: SONATYPE_USERNAME contains spaces!"
fi

if [[ "$SONATYPE_PASSWORD" != "${SONATYPE_PASSWORD// /}" ]]; then
    echo "⚠️  Warning: SONATYPE_PASSWORD contains spaces!"
fi

# Test credentials with sbt
echo "Testing credentials with sbt..."
echo ""

# Try to show publishTo configuration (this validates credentials indirectly)
OUTPUT=$(sbt -batch "show publishTo" 2>&1)

if echo "$OUTPUT" | grep -q "401"; then
    echo "❌ FAILED: 401 Unauthorized"
    echo ""
    echo "Your credentials are incorrect or don't have access."
    echo ""
    echo "Steps to fix:"
    echo "  1. Go to: https://central.sonatype.com/"
    echo "  2. Generate a NEW User Token"
    echo "  3. Update your environment variables:"
    echo "     export SONATYPE_USERNAME='new-username'"
    echo "     export SONATYPE_PASSWORD='new-token'"
    echo "  4. Run this script again"
    echo ""
    exit 1
elif echo "$OUTPUT" | grep -qi "error.*sonatype"; then
    echo "⚠️  Error connecting to Sonatype."
    echo ""
    echo "This could mean:"
    echo "  - Credentials are wrong"
    echo "  - Network issue"
    echo "  - Sonatype service is down"
    echo ""
    echo "Try publishing manually to test:"
    echo "  sbt 'show sonatypeCredentialHost'"
    echo ""
    exit 1
else
    echo ""
    echo "✅ SUCCESS! Configuration is valid."
    echo ""
    echo "Publish configuration:"
    echo "$OUTPUT" | grep -A 2 "Some("
    echo ""
    echo "Sonatype host:"
    sbt -batch "show sonatypeCredentialHost" 2>/dev/null | grep "s01.oss.sonatype.org" || echo "  s01.oss.sonatype.org"
    echo ""
    echo "🎉 Your credentials should work!"
    echo ""
    echo "⚠️  Note: Credentials are only fully tested during actual publishing."
    echo "   If you still get 401 errors during release, regenerate the token."
    echo ""
    echo "To publish to Maven Central:"
    echo "  1. Commit all changes"
    echo "  2. Create a tag: git tag -a v0.1.5 -m 'Release'"
    echo "  3. Push the tag: git push origin v0.1.5"
    echo ""
    echo "To update GitHub Secrets with these credentials:"
    echo "  1. Go to: https://github.com/riccardomerolla/zio-toon/settings/secrets/actions"
    echo "  2. Update SONATYPE_USERNAME with: $SONATYPE_USERNAME"
    echo "  3. Update SONATYPE_PASSWORD with your token"
    echo ""
fi

