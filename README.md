# Meet Your Friend

Meet Your Friend is a native Android app for capturing details about people you meet. Speak a short introduction to fill in contact fields, take a selfie, generate a sourced public-information brief, and keep connects organized on one screen.

## Features

- Voice capture with offline field extraction and optional Gemini structured extraction.
- Editable name, organization, role, and mobile fields with automatic local saving.
- Selfie capture, sourced Gemini briefs with optional spoken notes, and connect search.
- LinkedIn people-search handoff, pre-filled WhatsApp messages, contact insertion, and CSV export.
- Encrypted Gemini key storage and privacy-conscious, local-first persistence.

## Build

Requires JDK 17 and Android SDK platform 35.

```sh
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Gemini API key

Open **Settings**, enter your Gemini API key, and use **Test key** to check it. The key is stored encrypted on the device and is never included in exports.

## Constraints

- The app does not scrape LinkedIn; it opens LinkedIn people search and lets you choose a profile.
- WhatsApp opens a pre-filled message; you tap **Send** yourself.
