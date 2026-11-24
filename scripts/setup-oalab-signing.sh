#!/usr/bin/env bash
# Run once on YOUR computer after gh auth login. Never commit the generated key.
set -Eeuo pipefail
umask 077
repo="insurgut/OALab"
alias_name="oalab"
directory="$HOME/.local/share/oalab/private-signing"
mkdir -p "$directory"
command -v keytool >/dev/null || { echo "Install Java JDK / keytool first."; exit 1; }
command -v gh >/dev/null || { echo "Install GitHub CLI (gh) first."; exit 1; }
gh auth status -h github.com >/dev/null || { echo "Run gh auth login first."; exit 1; }

if [[ "$#" -gt 0 && "$1" == "--existing" ]]; then
  if [[ "$#" -lt 2 || ! -f "$2" ]]; then
    echo "Usage: bash scripts/setup-oalab-signing.sh --existing ORIGINAL_KEYSTORE [alias]" >&2
    exit 1
  fi
  keystore="$2"
  alias_name="oalab"
  if [[ "$#" -gt 2 ]]; then alias_name="$3"; fi
else
  keystore="$directory/oalab-release.jks"
  if [[ ! -f "$keystore" ]]; then
    echo "WARNING: A new key cannot update old APKs signed by another key."
    echo "If you have the original signing key, cancel and pass --existing FILE ALIAS."
    keytool -genkeypair -alias "$alias_name" -keyalg RSA -keysize 4096 \
      -validity 10000 -storetype PKCS12 -keystore "$keystore" \
      -dname "CN=OAlab, OU=Android, O=OAlab"
    chmod 600 "$keystore"
  else
    echo "Reusing original permanent keystore; never regenerating: $keystore"
  fi
fi

IFS= read -r -s -p "Keystore password: " store_pass
printf '\n'
IFS= read -r -s -p "Key password (Enter = same): " key_pass
printf '\n'
key_pass="$(printf '%s' "$key_pass")"
if [[ -z "$key_pass" ]]; then key_pass="$store_pass"; fi
if [[ -z "$store_pass" ]]; then echo "Empty password forbidden." >&2; exit 1; fi
export OALAB_SETUP_STOREPASS="$store_pass"
keytool -list -v -keystore "$keystore" -alias "$alias_name" \
  -storepass:env OALAB_SETUP_STOREPASS >/dev/null
fingerprint="$(keytool -list -v -keystore "$keystore" -alias "$alias_name" \
  -storepass:env OALAB_SETUP_STOREPASS | grep -m1 'SHA256:' \
  | sed -E 's/.*SHA256:[[:space:]]*//' | tr -d ':' \
  | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
if [[ ! "$fingerprint" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Failed to read key fingerprint." >&2
  exit 1
fi

# GitHub CLI encrypts all submitted secret data before upload.
base64 < "$keystore" | tr -d '\n' | gh secret set OALAB_KEYSTORE_B64 -R "$repo"
printf '%s' "$store_pass" | gh secret set OALAB_KEYSTORE_PASSWORD -R "$repo"
printf '%s' "$alias_name" | gh secret set OALAB_KEY_ALIAS -R "$repo"
printf '%s' "$key_pass" | gh secret set OALAB_KEY_PASSWORD -R "$repo"
printf '%s' "$fingerprint" | gh secret set OALAB_CERT_SHA256 -R "$repo"
unset store_pass key_pass OALAB_SETUP_STOREPASS

echo "All five permanent signing secrets configured in $repo."
echo "Public signing certificate fingerprint: $fingerprint"
echo "Keep a PRIVATE OFFLINE backup of this original keystore: $keystore"
echo "Do not generate a new key for future OAlab versions."
echo "Starting one verified release build using this signing identity..."
gh workflow run android.yml -R "$repo" --ref main
echo "Workflow dispatched. Inspect its result on GitHub Actions; no APK is published if signing verification fails."
