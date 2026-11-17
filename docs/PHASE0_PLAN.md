# Unidoc — Phase 0 Plan (Architect)

Status: **APPROVED — Rev 2 (hybrid online + offline, free only, sideload APK)** · Date: 2026-11-15 · Author: Rajdeep Biswas

---

## 1. Final Stack

| Area | Decision | Why / Rejected |
|---|---|---|
| Language | Kotlin 2.x, K2, KSP | Kotlin-only requirement |
| Build | AGP latest stable, Gradle version catalog, convention plugins (`build-logic/`) | Shared config across modules |
| SDK | minSdk 26 · compile/target 37 | Spec |
| UI | Jetpack Compose (BOM), Material 3, M3 Expressive motion, type-safe Navigation Compose | — |
| Arch | Clean Architecture + MVI (single `UiState` + `Intent` + `Effect` per screen) | Predictable state, survives process death via `SavedStateHandle` |
| DI | Hilt + hilt-navigation-compose + hilt-work | Standard, ViewModel/Worker injection |
| Async | Coroutines + Flow; WorkManager (foreground, `dataSync`/`shortService`) for long converter jobs | Survives app kill, progress via `setProgress` |
| Camera | CameraX (`camera2`, `lifecycle`, `view`), `ImageAnalysis` YUV → OpenCV | Full control for overlay, modes, auto-capture |
| Scanning | **Custom OpenCV pipeline (primary)**. ML Kit Document Scanner **not used** | See §3 comparison |
| CV lib | OpenCV Android (official Maven AAR, core+imgproc used) | Apache 2.0, NDK-free usage |
| OCR | ML Kit Text Recognition v2 **bundled** (Latin; Devanagari/Bengali opt-in) | Fully offline, no GMS dependency |
| QR | ML Kit Barcode Scanning **bundled** | Offline |
| PDF write/edit | PdfBox-Android (Apache 2.0) | Merge/split/encrypt/forms/text extract |
| PDF render | `android.graphics.pdf.PdfRenderer` (+ API 35 password support); PdfBox renderer fallback for encrypted on <35 | Fast native thumbnails |
| Office | **Cloud LibreOffice (primary, high fidelity)** + custom lightweight OOXML parsers as offline fallback — no Apache POI | POI adds ~15–20 MB and still renders poorly |
| HTML/Office→PDF render | Offscreen `WebView` → `PrintDocumentAdapter` → PDF file | Offline, good CSS layout engine |
| Storage | Room (documents, pages, folders, jobs, FTS4 OCR index), DataStore (prefs), app-private files for working copies | — |
| I/O | SAF (`OpenDocument`, `CreateDocument`, `OpenDocumentTree`), Photo Picker, MediaStore Downloads for export | **No storage permissions** needed |
| Images | Coil 3 (thumbnails), `Bitmap.compress` JPEG/WebP, OpenCV ops | — |
| Quality | ktlint + detekt, JUnit5 + Turbine + MockK, Compose UI tests, Roborazzi screenshots, Baseline Profiles, R8 full mode | — |
| Network | Ktor client (OkHttp engine), kotlinx.serialization | Unidoc Cloud jobs |
| AI | ML Kit GenAI (Gemini Nano) / Gemini API free tier, LiteRT | §11 |
| Crash/analytics | None | No tracking |

Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `POST_NOTIFICATIONS` (job progress), `FOREGROUND_SERVICE(_DATA_SYNC)`, `VIBRATE`. Nothing else.

---

## 2. Module Structure

```
:app                      → Application, MainActivity, NavHost, bottom nav, splash
:build-logic              → convention plugins
:core:common              → Result/AppError, dispatchers, file utils, logging
:core:model               → pure Kotlin domain models
:core:database            → Room DB, DAOs, FTS
:core:datastore           → preferences
:core:storage             → SAF/MediaStore, workspace files, temp-file manager
:core:imaging             → OpenCV wrappers, filters, compression encoders (no Android UI)
:core:pdf                 → PdfBox/PdfRenderer facade, PDF writer (MRC, 1-bit, JPEG)
:core:ocr                 → ML Kit OCR facade, hOCR → invisible text layer
:core:office              → OOXML read (docx/xlsx/pptx→HTML) + write (docx/xlsx/pptx)
:core:work                → WorkManager job framework (progress, cancel, notifications)
:core:network             → Unidoc Cloud client
:core:ai                  → Gemini Nano / Gemini API facade, LiteRT segmentation
:core:ui-kit              → design system: theme, tokens, gradient, components, icons, haptics
:feature:scanner:home     → library grid, folders, search, multi-select
:feature:scanner:capture  → CameraX, live detection, modes, tray
:feature:scanner:editor   → crop/loupe, filters, adjust, annotate, pages, undo/redo
:feature:scanner:export   → PDF/image export options, size preview
:feature:converter:hub    → categorized tool grid
:feature:converter:tools  → generic tool flow (pick → options → progress → result) + tool configs
:baselineprofile          → macrobenchmark + profile generator
/server (separate repo: unidoc-cloud) → Docker: Gotenberg + FastAPI tools
```

Dependency rule: `feature → core:*` only; features never depend on each other; `core:imaging/pdf/office/ocr` contain no Compose.

---

## 3. Scanner: ML Kit Document Scanner vs Custom

| Criterion | ML Kit Document Scanner | Custom (CameraX + OpenCV) |
|---|---|---|
| UI control | Google's fixed UI, can't restyle | Full, matches brand |
| Live overlay / auto-capture tuning | Built-in, not tunable | Tunable (stability, sharpness) |
| Modes (ID, Book, Receipt, Whiteboard, QR) | No | Yes |
| Filters / compression control | Basic | Full pipeline |
| Requires Play services | **Yes** (unbundled module download) | No |
| Effort | Low | High |
| **Decision** | ✗ | **✓ Primary** |

---

## 4. Scanning Pipeline

| # | Stage | Technique |
|---|---|---|
| 1 | Live detection (ImageAnalysis, ~640px, 10–15 fps) | Gray → GaussianBlur → Canny (auto thresholds from median) → dilate → `findContours` → `approxPolyDP` → largest convex quad (area/angle checks); fallback: `HoughLinesP` intersections |
| 2 | Smoothing | Kalman/EMA on corners for jitter-free overlay |
| 3 | Auto-capture | Quad IoU > 0.95 across 12 frames + Laplacian variance > threshold + low motion → haptic + capture |
| 4 | Full-res capture | `ImageCapture` max quality, corners rescaled; re-detect on full-res for precision |
| 5 | Perspective warp | `getPerspectiveTransform` + `warpPerspective`; output aspect from edge lengths (or snapped to A4/Letter/ID-1 per mode) |
| 6 | Deskew | Projection-profile angle search ±5° on binarized text |
| 7 | Illumination / shadow removal | Background estimate (dilate 7×7 + medianBlur 21) → divide → normalize |
| 8 | Glare reduction | Highlight mask (V>240, low S) → `inpaint` (Telea) |
| 9 | Contrast | CLAHE on L channel (Lab) |
| 10 | Denoise | Bilateral (color) / median 3 (B&W) |
| 11 | Sharpen | Unsharp mask (σ 1.0, amount 0.6) |
| 12 | Binarize (B&W) | **Sauvola** (integral images via `boxFilter`, k=0.34, w≈ DPI/10) |

Filters: Original · Auto/Magic (7+9+11) · B&W (7+12) · Grayscale · Enhanced color (7+9+saturation) · Lighten · Whiteboard (7 + white-balance + saturation boost).

Modes:
| Mode | Behavior |
|---|---|
| Document | Default pipeline |
| ID card | Front + back captured, ID-1 aspect, composited on one A4 page |
| Book | Gutter detection (vertical intensity valley) → split into 2 pages, curvature-lite correction |
| Receipt | Narrow aspect allowed, B&W default, long-receipt stitching ✗ (deferred) |
| Whiteboard | Whiteboard filter, glare removal on |
| QR | ML Kit barcode, result sheet (copy/open/share) |

Non-destructive editing: original JPEG + edit stack (corners, rotation, filter, adjustments, annotations as vector JSON) stored per page; rendered on demand → undo/redo trivial, process-death safe.

---

## 5. Smart Compression Strategy

Page classifier (after warp): saturation histogram + edge density → `TEXT_BW` / `GRAY` / `MIXED` / `PHOTO`.

| Preset | DPI | TEXT_BW | GRAY | MIXED | PHOTO |
|---|---|---|---|---|---|
| High | 300 | 1-bit Flate | JPEG q85 | JPEG q85 | JPEG q88 |
| Balanced (default) | 200 | 1-bit Flate | JPEG q75 | **MRC** | JPEG q78 |
| Small | 150 | 1-bit Flate | JPEG q60 | MRC | JPEG q62 |

| Technique | Detail |
|---|---|
| DPI normalization | Resample to target DPI from physical page size (A4 = 8.27×11.69 in) with `INTER_AREA` |
| 1-bit text pages | Sauvola → 1-bit DeviceGray Flate image (≈30–90 KB/page @200 DPI) |
| MRC (mixed) | Foreground: 1-bit text mask at full DPI as `/ImageMask` with color fill; background: text-inpainted, 100 DPI JPEG q50. Typical 3–5× smaller than single JPEG at same legibility |
| JPEG | libjpeg-turbo via `Bitmap.compress`, 4:2:0, quality chosen by preset; optional binary search to hit target KB |
| WebP | Used only for JPG/PNG/WebP exports (PDF can't embed WebP) |
| JBIG2 | ✗ Deferred (would need NDK build of jbig2enc; flag for Phase 7) |
| Live size estimate | Encode current page in background → show per-page + total size per preset before export |

Expected: 7 MB phone photo → B&W ~50–120 KB · Gray ~150–300 KB · Color ~250–500 KB (Balanced).

---

## 6. Conversion Feasibility Matrix (hybrid: on-device + free cloud)

Execution: **D** = on-device · **C** = Unidoc Cloud (§10) · **D→C** = on-device default, cloud for max quality · **C→D** = cloud default, on-device fallback when offline.
Fidelity: **H** = near-identical · **M** = content + basic layout · **L** = approximate

| Tool | Exec | Fidelity | Implementation |
|---|---|---|---|
| Merge / Split / Remove / Extract / Reorder / Rotate | D | H | PdfBox page tree ops |
| Scan to PDF | D | H | Scanner pipeline |
| Compress PDF | D→C | H | D: image recompress/downsample, strip metadata. C: Ghostscript + qpdf object streams (extra 10–30%) |
| Repair PDF | C→D | H/M | C: qpdf + Ghostscript rebuild. D: PdfBox lenient resave |
| OCR PDF | D (+C for Bengali/others) | H | ML Kit Latin/Devanagari on-device; C: OCRmyPDF/Tesseract for Bengali + 100 languages |
| JPG/PNG/WebP/HEIC → PDF | D | H | PdfBox |
| TXT → PDF | D | H | Embedded font layout |
| HTML / URL → PDF | D→C | H | D: WebView print. C: Gotenberg Chromium (full JS, web fonts) |
| Word (DOC/DOCX/ODT/RTF) → PDF | C→D | **H** / M | C: LibreOffice (Gotenberg) + MS-metric-compatible fonts (Carlito, Caladea, Liberation). D fallback: OOXML→HTML→PDF |
| PowerPoint (PPT/PPTX/ODP) → PDF | C→D | **H** / M | C: LibreOffice. D fallback: slide XML→HTML |
| Excel (XLS/XLSX/ODS/CSV) → PDF | C→D | **H** / M | C: LibreOffice (recalculates formulas, renders charts). D fallback: cached values → HTML |
| PDF → JPG/PNG | D | H | PdfRenderer @ chosen DPI |
| PDF → TXT | D | H | PdfBox text stripper; scanned → OCR first |
| PDF → Word | C→D | **M–H** / L | C: pdf2docx (layout, tables, images, fonts). D fallback: text runs → DOCX writer |
| PDF → PowerPoint | C→D | M | C: page→slide with editable text boxes + background image. D fallback same, simpler |
| PDF → Excel | C→D | **M–H** / L | C: Camelot (lattice + stream) / pdfplumber → openpyxl. D fallback: ruling-line heuristic |
| PDF → PDF/A (1b/2b/3b) | C→D | **H + validated** | C: Ghostscript/OCRmyPDF conversion + **veraPDF validation report**. D fallback: PdfBox best-effort |
| Page numbers / Watermark / Crop | D | H | PdfBox |
| Edit PDF | D | M | Add text/image/shapes/whiteout overlays. Editing existing embedded text ✗ (font subsets; no free reliable engine) |
| Sign PDF | D | H | Visual signature **+ cryptographic PAdES/PKCS#7** via PdfBox + BouncyCastle with user-generated self-signed cert in Android Keystore |
| Protect / Unlock | D | H | AES-256; unlock needs known password |
| Redact | D | H (secure) | Burned-in rasterized redaction + optional OCR layer |
| Compare PDFs | D | H | Word-level text diff + visual pixel diff |
| Image → Text | D | H | ML Kit OCR (C for extra languages) |
| Text → Image / Image convert / compress / resize | D | H | Canvas, JPG/PNG/WebP/AVIF(API 34+) |

Every cloud tool shows a **Cloud** badge, uploads only after consent (one-time + per-tool toggle), and degrades to the on-device path when offline or server unreachable.

---

## 7. Design System (Phase 1–2 preview)

| Token | Value |
|---|---|
| Brand gradient | Indigo `#3D2CFF` → Violet `#8B3DFF` → Cyan `#22D3EE` (135°) |
| Neutrals | Slate scale; dark surface `#0E0F1A` |
| Shape | 8 / 16 / 28 dp radii |
| Type | Inter (bundled, variable) |
| Motion | M3 Expressive springs; shared-element transitions page↔editor |
| Haptics | Capture, auto-capture lock, drag snap, delete |
| Dynamic color | Applied to surfaces/containers; brand gradient fixed on primary CTAs, FAB, logo, progress |
| Logo | Adaptive + monochrome (themed) icon: stylized folded page forming a "U" with gradient |
| Splash | Android 12 SplashScreen API, animated vector icon |

---

## 8. Cross-cutting Rules

| Concern | Approach |
|---|---|
| Large files | PdfBox `MemoryUsageSetting.setupTempFileOnly()`, page-by-page processing, bitmap `inSampleSize`, recycle Mats in `use{}` |
| Low memory | Max working resolution capped by `ActivityManager.memoryClass`; `onTrimMemory` cache eviction |
| Process death | `SavedStateHandle` for UI, scan session persisted per page to Room/disk immediately, jobs in WorkManager |
| Errors | Typed `AppError` (InvalidFile, Encrypted, Unsupported, OutOfSpace, Cancelled…) → user-readable messages |
| Cancellation | Cooperative `ensureActive()` per page; temp files cleaned |
| APK size | ABI split APKs (arm64-v8a, armeabi-v7a, x86_64) + universal; R8; target ≤ 35 MB per ABI. OpenCV slim custom build considered in Phase 7 |
| Accessibility | Content descriptions, 48 dp targets, TalkBack labels for corner handles, contrast AA |

---

## 9. Decisions (approved 2026-11-15)

| # | Topic | Decision |
|---|---|---|
| 1 | OCR languages | English + Hindi, Marathi, Nepali, Sanskrit (ML Kit Devanagari, on-device) · Bengali, Assamese, Tamil, Telugu, Kannada, Malayalam, Gujarati, Punjabi (Gurmukhi), Odia, Urdu (Tesseract `script/*` + `best` models, cloud). Auto script detection; language picker |
| 2 | Cloud host | **Hugging Face Spaces (primary)** — no card, 16 GB RAM, reliable signup. Oracle Always Free rejected as primary: A1 capacity frequently unavailable in Mumbai/Hyderabad regions, Indian card verification often fails, idle-instance reclamation. Same Docker image runs on Oracle; server URL is a setting |
| 3 | Gemini / AI features (§11 AI row) | **Pending** — decide later. Not built until confirmed |
| 4 | Google Drive backup | **Dropped** |
| 5 | Distribution | **Sideload APK only**: signed release APKs — `arm64-v8a` (main), `armeabi-v7a`, `x86_64`, plus universal. No AAB / Play features |
| 6 | Package | `com.rajdeep.unidoc` |
| 7 | Accounts / profile | None |

---

## 10. Unidoc Cloud — free backend (₹0)

| Item | Decision |
|---|---|
| Host (primary) | **Hugging Face Spaces, Docker, free CPU** (2 vCPU, 16 GB RAM, private Space, no card needed). Sleeps after 48 h idle → app wakes it with progress UI (cold start ~1–2 min) |
| Host (fallback) | Oracle Cloud Always Free (Ampere A1, 4 OCPU / 24 GB) — card verification only, no charge. Config switch in app |
| Container | One Docker image: **Gotenberg** (LibreOffice + Chromium) + FastAPI sidecar (pdf2docx, Camelot, pdfplumber, OCRmyPDF/Tesseract, Ghostscript, qpdf, veraPDF) |
| API | `POST /v1/jobs` (multipart) → `GET /v1/jobs/{id}` (progress) → `GET /v1/jobs/{id}/result`; resumable uploads for >50 MB |
| Client | Ktor client / OkHttp, WorkManager (network constraint, retry + exponential backoff), upload/download progress |
| Security | HTTPS only, API key header (HF Space secret) + per-device rate limit, files in tmpfs, deleted on completion or after 10 min, no logs of content |
| Limits | Max 100 MB/file, queue of 2 concurrent jobs (free CPU) |
| Licenses | Ghostscript + PyMuPDF (used by pdf2docx) are **AGPL** → server source published in a public repo (`unidoc-cloud`) to stay compliant. All else Apache/MIT/MPL/LGPL |

## 11. Additional Premium Features (free)

| Feature | Tech | Exec |
|---|---|---|
| ML document segmentation for edge detection (more robust than Canny on low-contrast/cluttered backgrounds) | LiteRT (TFLite) lightweight segmentation model, GPU delegate; OpenCV fallback | D |
| AI: auto-title, summary, key fields (dates, totals on receipts), proofread OCR | **ML Kit GenAI (Gemini Nano)** on supported devices; else **Gemini API free tier** (opt-in, user notified free tier may use data for model improvement) | D / C |
| Smart document search | Room FTS over OCR text + AI tags | D |
| Receipt/ID field extraction | ML Kit entity extraction (offline) + regex | D |
| Handwriting signature capture | ML Kit Digital Ink (optional) / Compose stroke smoothing | D |
