#!/bin/bash

# Verification script for Maven Central publishing setup
# Run this script before attempting your first release

set -e

echo "🔍 Verifying Maven Central Publishing Setup..."
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check 1: Verify sbt-ci-release plugin
echo "1️⃣  Checking sbt-ci-release plugin..."
if grep -q "sbt-ci-release" project/plugins.sbt; then
    echo -e "${GREEN}✓${NC} sbt-ci-release plugin found"
else
    echo -e "${RED}✗${NC} sbt-ci-release plugin NOT found in project/plugins.sbt"
    exit 1
fi

# Check 2: Verify build.sbt has required metadata
echo "2️⃣  Checking build.sbt metadata..."
required_fields=("organization" "licenses" "developers" "scmInfo" "homepage")
for field in "${required_fields[@]}"; do
    if grep -q "$field" build.sbt; then
        echo -e "   ${GREEN}✓${NC} $field configured"
    else
        echo -e "   ${RED}✗${NC} $field NOT found"
        exit 1
    fi
done

# Check 3: Verify GitHub workflow exists
echo "3️⃣  Checking GitHub Actions workflow..."
if [ -f ".github/workflows/ci.yml" ]; then
    echo -e "${GREEN}✓${NC} CI workflow found"
else
    echo -e "${RED}✗${NC} GitHub Actions workflow NOT found"
    exit 1
fi

# Check 4: Verify LICENSE file
echo "4️⃣  Checking LICENSE file..."
if [ -f "LICENSE" ]; then
    echo -e "${GREEN}✓${NC} LICENSE file exists"
else
    echo -e "${RED}✗${NC} LICENSE file NOT found"
    exit 1
fi

# Check 5: Test SBT configuration loads
echo "5️⃣  Testing SBT configuration..."
if sbt -batch reload > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} SBT configuration loads successfully"
else
    echo -e "${RED}✗${NC} SBT configuration has errors"
    exit 1
fi

# Check 6: Run tests
echo "6️⃣  Running tests..."
if sbt -batch test > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} All tests pass"
else
    echo -e "${YELLOW}⚠${NC}  Tests failed - fix before releasing"
fi

# Check 7: Check formatting
echo "7️⃣  Checking code formatting..."
if sbt -batch scalafmtCheckAll > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} Code formatting is correct"
else
    echo -e "${YELLOW}⚠${NC}  Code formatting issues detected - run 'sbt scalafmtAll'"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo -e "${GREEN}✅ Setup verification complete!${NC}"
echo ""
echo "📋 Next steps before releasing:"
echo ""
echo "   1. Ensure GitHub Secrets are configured:"
echo "      • PGP_SECRET (base64 encoded PGP private key)"
echo "      • PGP_PASSPHRASE (your PGP passphrase)"
echo "      • SONATYPE_USERNAME (your Sonatype username)"
echo "      • SONATYPE_PASSWORD (your Sonatype password/token)"
echo ""
echo "   2. Push changes to main:"
echo "      git add ."
echo "      git commit -m 'Setup Maven Central publishing'"
echo "      git push origin main"
echo ""
echo "   3. Wait for CI to pass on GitHub Actions"
echo ""
echo "   4. Create and push a release tag:"
echo "      git tag -a v0.1.0 -m 'Initial release'"
echo "      git push origin v0.1.0"
echo ""
echo "   5. Monitor the release at:"
echo "      https://github.com/riccardomerolla/zio-toon/actions"
echo ""
echo "   6. Verify publication (10-30 min after release):"
echo "      https://search.maven.org/search?q=g:io.github.riccardomerolla"
echo ""
echo "📚 Documentation:"
echo "   • PUBLISHING.md - Complete publishing guide"
echo "   • RELEASE_CHECKLIST.md - Step-by-step release checklist"
echo "   • SETUP_SUMMARY.md - Summary of all changes made"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

