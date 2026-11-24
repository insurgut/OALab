# PUBLIC UNTRUSTED TEST SIGNING — NOT SECURE

At the repository owner's explicit request, this project uses a publicly
accessible developer signing key. Its PRIVATE key is stored as Base64 at
signing/oalab-public-dev.p12.b64, with the public certificate fingerprint
in signing/oalab-public-cert-sha256.txt.

Alias: oalab-public-dev
Public password: OAlab_PUBLIC_TEST_SIGNING_2026

GitHub Actions generates this identity exactly once and commits both
files; subsequent release builds reuse it and reject changed fingerprints.

WARNING: ANYONE who can access these files can forge an APK that Android
accepts as an OAlab update. This may enable malicious updates/data theft.
Base64 is not encryption. Do not use with real users or sensitive data.
The private key is not bundled in the APK itself.

An already installed APK signed by a different debug key CANNOT be
updated over the top with the new public signing key. Back up data before
the one-time reinstall. Never assume different signatures are compatible.
