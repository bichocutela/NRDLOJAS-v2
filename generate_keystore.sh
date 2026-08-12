#!/bin/bash
set -e

# Generate a strong random password
PASSWORD=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 20)

# Save to KEYSTORE_PASSWORD.txt
echo -n "$PASSWORD" > KEYSTORE_PASSWORD.txt

# Save alias to KEYSTORE_ALIAS.txt
echo -n "upload" > KEYSTORE_ALIAS.txt

# Generate a NEW release.keystore using RSA 2048
rm -f release.keystore
keytool -genkey -v -keystore release.keystore -alias upload -keyalg RSA -keysize 2048 -validity 10000 -storepass "$PASSWORD" -keypass "$PASSWORD" -dname "CN=NRD Codigos, OU=Dev, O=NRD, L=City, ST=State, C=BR" > /dev/null 2>&1

# Generate release.keystore.base64
base64 -w 0 release.keystore > release.keystore.base64

# Generate KEYSTORE_BASE64.txt
cp release.keystore.base64 KEYSTORE_BASE64.txt

# Extract SHA-256
keytool -list -v -keystore release.keystore -alias upload -storepass "$PASSWORD" | grep "SHA256"
