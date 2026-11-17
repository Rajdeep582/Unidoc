package com.rajdeep.unidoc.feature.settings.legal

/**
 * Single source of truth for the in-app legal texts. The same content is exported to
 * docs/legal/{privacy-policy,terms-of-use}.html (see LegalExportTest) for the public URL the app
 * stores ask for, so the hosted page and the app can never disagree.
 */
object LegalInfo {
    const val APP_NAME = "Unidoc"
    const val DEVELOPER = "Rajdeep Biswas"
    const val LOCATION = "Kolkata, West Bengal, India"

    /** Change here to a dedicated support address if you create one; it is shown in the app and policies. */
    const val SUPPORT_EMAIL = "brajdeep029@gmail.com"
    const val EFFECTIVE_DATE = "17 November 2026"
}

data class LegalSection(val heading: String, val paragraphs: List<String>)

data class LegalDocument(val title: String, val intro: String, val sections: List<LegalSection>)

object LegalDocuments {
    private const val E = LegalInfo.SUPPORT_EMAIL

    val privacyPolicy = LegalDocument(
        title = "Privacy Policy",
        intro = "${LegalInfo.APP_NAME} (\"the app\") is developed by ${LegalInfo.DEVELOPER}, ${LegalInfo.LOCATION} " +
            "(\"we\", \"us\"). This policy explains what the app does with your information. In short: your scans " +
            "and files stay on your phone. We have no servers that receive your documents, we do not use accounts, " +
            "ads, analytics or tracking, and we never sell data. Effective ${LegalInfo.EFFECTIVE_DATE}.",
        sections = listOf(
            LegalSection(
                "1. Information the app handles",
                listOf(
                    "Camera images: when you scan, the camera captures pages that you choose to keep. Images are " +
                        "processed (cropping, enhancement, text recognition) entirely on your device.",
                    "Files you open: PDFs, images and Office documents you pick for the converter are read from the " +
                        "location you select. The app only accesses files you explicitly choose.",
                    "Documents you create: scans, converted files and recognised text are stored in the app's " +
                        "private storage on your device, and in places you choose when exporting or sharing.",
                    "Settings: your preferences (theme, quality, languages, optional server address) are stored " +
                        "locally on your device."
                )
            ),
            LegalSection(
                "2. What we do not collect",
                listOf(
                    "We do not collect, receive or store your documents, images, text, contacts, location, " +
                        "identifiers or usage history. The app has no sign-in, no advertising and no analytics SDK. " +
                        "We do not sell, rent or share personal data."
                )
            ),
            LegalSection(
                "3. Permissions",
                listOf(
                    "Camera — used only while you are scanning, to capture documents and read QR codes. You can deny " +
                        "it and still import images and PDFs.",
                    "Internet / network state — used only for the optional cloud feature described below, for the " +
                        "\"Web page to PDF\" tool (which downloads the page whose address you enter directly from that " +
                        "website), and by Google's on-device ML Kit libraries (section 5). Everything else works offline.",
                    "The app does not request access to your contacts, location, microphone, call logs or SMS, and " +
                        "it reads shared storage only through the system file picker."
                )
            ),
            LegalSection(
                "4. Optional cloud processing (off by default)",
                listOf(
                    "Some advanced conversions (for example Office documents, PDF to Word/Excel, PDF/A and OCR for " +
                        "some Indian scripts) can optionally use a processing server. This is switched off by default " +
                        "and is used only after you turn it on in Settings and confirm.",
                    "When enabled, only the files you submit to a tool marked \"Cloud\" are sent, over an encrypted " +
                        "HTTPS connection, to the server address entered in Settings. They are used solely to perform " +
                        "the requested conversion and are deleted from the server when the job finishes; the result " +
                        "is returned to your device. Nothing else is uploaded.",
                    "If you connect a server that you or someone else operates, that operator is responsible for " +
                        "how the server handles files. You can turn cloud processing off at any time."
                )
            ),
            LegalSection(
                "5. Third-party components",
                listOf(
                    "Text recognition and QR/barcode reading use Google ML Kit, which runs on your device. Google's ML " +
                        "Kit libraries may send limited diagnostic and usage information (such as API usage, " +
                        "performance and device information — not your images or recognised text) to Google to " +
                        "maintain and improve the service, as described in Google's ML Kit terms and privacy " +
                        "policy: https://developers.google.com/ml-kit/terms and https://policies.google.com/privacy.",
                    "Other libraries (for example OpenCV, PdfBox-Android, AndroidX/Jetpack) run fully on your device " +
                        "and do not transmit data."
                )
            ),
            LegalSection(
                "6. Storage, security and retention",
                listOf(
                    "Your documents remain on your device until you delete them in the app, clear the app's storage " +
                        "or uninstall the app. App data is excluded from cloud backups. Exported files are kept " +
                        "wherever you save them and are under your control.",
                    "Password-protected PDFs are encrypted with AES-256 on your device. Cloud traffic, when enabled, " +
                        "is restricted to HTTPS."
                )
            ),
            LegalSection(
                "7. Children",
                listOf(
                    "The app is a general productivity tool and is not directed to children. It does not knowingly " +
                        "collect personal data from anyone, including children."
                )
            ),
            LegalSection(
                "8. Your rights",
                listOf(
                    "Because we do not hold your personal data, there is nothing for us to access, correct or erase on " +
                        "our side; you control all data on your device. If you have a question or a request under the " +
                        "Digital Personal Data Protection Act, 2023 (India), the GDPR or any other applicable law, " +
                        "contact us at $E and we will respond within 30 days."
                )
            ),
            LegalSection(
                "9. Changes to this policy",
                listOf(
                    "We may update this policy when the app changes. The effective date above will change and " +
                        "significant changes will be noted in the app's release notes."
                )
            ),
            LegalSection(
                "10. Contact",
                listOf("${LegalInfo.DEVELOPER}, ${LegalInfo.LOCATION}. Email: $E")
            )
        )
    )

    val termsOfUse = LegalDocument(
        title = "Terms of Use",
        intro = "These terms govern your use of ${LegalInfo.APP_NAME}, provided by ${LegalInfo.DEVELOPER} " +
            "(\"we\"). By installing or using the app you agree to them. Effective ${LegalInfo.EFFECTIVE_DATE}.",
        sections = listOf(
            LegalSection(
                "1. Licence",
                listOf(
                    "We grant you a personal, non-exclusive, non-transferable, revocable licence to use the app on " +
                        "devices you own or control, free of charge. You may not reverse engineer, resell, " +
                        "redistribute modified copies of, or misuse the app, except as permitted by law or by the " +
                        "open-source licences of included components."
                )
            ),
            LegalSection(
                "2. Your content and responsibilities",
                listOf(
                    "You keep all rights to the documents you scan or convert. You are responsible for having the " +
                        "right to scan, copy, convert or share them, and for complying with applicable laws, including " +
                        "copyright and laws governing identity documents and personal data of others.",
                    "Do not use the app to forge, alter or misrepresent official documents, signatures or records."
                )
            ),
            LegalSection(
                "3. Optional cloud processing",
                listOf(
                    "Cloud features are optional and off by default. They depend on a server that may be unavailable, " +
                        "slow or discontinued at any time. Use of a server operated by a third party is also subject " +
                        "to that operator's terms."
                )
            ),
            LegalSection(
                "4. Accuracy and backups",
                listOf(
                    "Automatic edge detection, text recognition and file conversion may not be perfectly accurate. " +
                        "Always check important results. Keep your own copies of important documents; uninstalling " +
                        "the app or clearing its storage permanently removes documents stored inside it."
                )
            ),
            LegalSection(
                "5. Third-party components",
                listOf(
                    "The app includes open-source and third-party components that are provided under their own " +
                        "licences, listed in Settings → Open-source licenses."
                )
            ),
            LegalSection(
                "6. Disclaimer of warranties",
                listOf(
                    "The app is provided \"as is\" and \"as available\", without warranties of any kind, express or " +
                        "implied, including fitness for a particular purpose and non-infringement, to the extent " +
                        "permitted by law."
                )
            ),
            LegalSection(
                "7. Limitation of liability",
                listOf(
                    "To the maximum extent permitted by law, we are not liable for any indirect, incidental, special " +
                        "or consequential damages, or for loss of data, documents or profits, arising from your use of " +
                        "or inability to use the app."
                )
            ),
            LegalSection(
                "8. Changes and termination",
                listOf(
                    "We may update the app and these terms. Continued use after an update means you accept the " +
                        "revised terms. You may stop using the app at any time by uninstalling it."
                )
            ),
            LegalSection(
                "9. Governing law",
                listOf(
                    "These terms are governed by the laws of India. Courts at Kolkata, West Bengal, have exclusive " +
                        "jurisdiction, subject to any mandatory consumer-protection rights in your country."
                )
            ),
            LegalSection("10. Contact", listOf("Questions about these terms: $E"))
        )
    )
}

/** Third-party notices shown in Settings → Open-source licenses. */
val OpenSourceNotices = listOf(
    "AndroidX, Jetpack Compose, Material 3, CameraX, Room, DataStore, Navigation" to "Apache License 2.0 · Google LLC",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0 · JetBrains s.r.o.",
    "Dagger Hilt" to "Apache License 2.0 · Google LLC",
    "OpenCV" to "Apache License 2.0 · OpenCV team",
    "PdfBox-Android (port of Apache PDFBox)" to "Apache License 2.0 · Tom Roush, The Apache Software Foundation",
    "Bouncy Castle" to "Bouncy Castle Licence (MIT-style) · Legion of the Bouncy Castle Inc.",
    "OkHttp, Okio" to "Apache License 2.0 · Square, Inc.",
    "Coil" to "Apache License 2.0 · Coil Contributors",
    "Reorderable" to "Apache License 2.0 · Calvin Liang",
    "Google ML Kit (text recognition, barcode scanning)" to "ML Kit Terms of Service · Google LLC",
    "Inter typeface" to "SIL Open Font License 1.1 · The Inter Project Authors",
    "Space Grotesk typeface" to "SIL Open Font License 1.1 · The Space Grotesk Project Authors"
)
