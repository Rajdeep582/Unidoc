# Publishing Unidoc 1.0.0

## Release files (on your PC)
- `Desktop/UniDoc/release/Unidoc-1.0.0-universal.apk`: upload this to every store (all phones).
- `Unidoc-1.0.0-arm64-v8a.apk` and `-armeabi-v7a.apk`: smaller per-device builds, for stores that accept multiple APKs.
- Signing key: `C:\Users\brajd\UnidocKeys\unidoc-release.jks` + `keystore.properties` (passwords).
  Every future update MUST be signed with this key. Back the folder up to 2 places, such as a USB drive and a cloud drive.
  Certificate SHA-256: `41:4E:C1:DC:1F:89:A6:50:40:CA:0B:1C:8B:40:3F:3C:31:76:D4:A5:2D:F2:D7:B8:DD:1D:9E:33:6B:60:B4:C7`

## Building future releases
1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Copy `C:\Users\brajd\UnidocKeys\keystore.properties` into the project root. It is ignored by git. Run
   `gradlew :app:assembleRelease`. The APKs in `app/build/outputs/apk/release/` are then signed with your key.

## Legal pages (GitHub Pages, served from `/docs` on `main`)
- Site: https://rajdeep582.github.io/Unidoc/
- Privacy: https://rajdeep582.github.io/Unidoc/legal/privacy-policy.html
- Terms: https://rajdeep582.github.io/Unidoc/legal/terms-of-use.html
The pages are generated from the in-app text by `ReleaseAssetsTest`, so the app and the site always match. After editing
`LegalDocuments.kt`, run `gradlew :app:testDebugUnitTest --tests "*ReleaseAssetsTest*"` and commit `docs/legal`.

## Branches
- `main`: released code only. GitHub Pages serves from here. Tag every store release (`v1.0.0`, `v1.0.1`, …).
- `develop`: day-to-day work. Merge into `main` when you ship.

## Store listing
See `docs/store/listing.md` and the graphics in `docs/store/`.
