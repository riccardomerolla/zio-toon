#!/bin/bash

# Script to properly encode PGP key for GitHub Secrets
# This ensures the key is base64 encoded in a single line without breaks

set -e

echo "🔐 PGP Key Encoder for GitHub Secrets"
echo "======================================"
echo ""

# Check if gpg is installed
if ! command -v gpg &> /dev/null; then
    echo "❌ Error: gpg is not installed"
    echo "   Install it with: brew install gpg (macOS) or apt-get install gnupg (Linux)"
    exit 1
fi

# List available keys
echo "Available PGP keys:"
gpg --list-secret-keys --keyid-format LONG

echo ""
echo "Enter the email associated with your PGP key:"
read -r EMAIL

if [ -z "$EMAIL" ]; then
    echo "❌ Error: Email cannot be empty"
    exit 1
fi

# Check if key exists
if ! gpg --list-secret-keys "$EMAIL" &> /dev/null; then
    echo "❌ Error: No secret key found for $EMAIL"
    exit 1
fi

echo ""
echo "Encoding PGP key for: $EMAIL"
echo ""

# Export and encode the key
# The key must be base64 encoded WITHOUT line breaks
ENCODED_KEY=$(gpg --armor --export-secret-keys "$EMAIL" | base64 | tr -d '\n')

if [ -z "$ENCODED_KEY" ]; then
    echo "❌ Error: Failed to encode the key"
    exit 1
fi

# Save to file
OUTPUT_FILE="pgp-secret-encoded.txt"
echo "$ENCODED_KEY" > "$OUTPUT_FILE"

echo "✅ Success! Your PGP key has been encoded."
echo ""
echo "📄 The encoded key has been saved to: $OUTPUT_FILE"
echo ""
echo "📋 Next steps:"
echo "   1. Copy the contents of $OUTPUT_FILE"
echo "   2. Go to: https://github.com/riccardomerolla/zio-toon/settings/secrets/actions"
echo "   3. Click 'New repository secret'"
echo "   4. Name: PGP_SECRET"
echo "   5. Value: Paste the encoded key (should be one very long line)"
echo "   6. Click 'Add secret'"
echo ""
echo "⚠️  IMPORTANT: The encoded key is ONE LINE with no breaks or spaces"
echo ""
echo "🔒 Security Note: Delete $OUTPUT_FILE after adding the secret to GitHub"
echo "   Run: rm $OUTPUT_FILE"
echo ""

# Copy to clipboard if available
if command -v pbcopy &> /dev/null; then
    echo "$ENCODED_KEY" | pbcopy
    echo "✨ Bonus: The encoded key has been copied to your clipboard!"
elif command -v xclip &> /dev/null; then
    echo "$ENCODED_KEY" | xclip -selection clipboard
    echo "✨ Bonus: The encoded key has been copied to your clipboard!"
elif command -v clip &> /dev/null; then
    echo "$ENCODED_KEY" | clip
    echo "✨ Bonus: The encoded key has been copied to your clipboard!"
fi

echo ""
echo "Key length: ${#ENCODED_KEY} characters"
echo ""

