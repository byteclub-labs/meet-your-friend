# Meet Your Friend — Native Android App Spec (v0.2, decisions locked)

## 1. Summary
A single-screen native Android app for capturing the people you meet. One large button does everything:

- **Single tap** → voice capture. You speak the friend's details and the app fills in **Name, Organization, Role, Mobile**.
- **Double tap** → front camera opens so you can take a selfie with the friend.

After capture, the app:
1. Finds the person's public LinkedIn profile and writes a **short brief ("article")** about them, with an optional **voice note** (audio) version.
2. Prepares a **WhatsApp message** to their number: *"Hi {Name}, great meeting you today! — {MyName}"*.
3. Saves the connect locally so you can **track** everyone you've met.

## 2. Tech stack (native Android)
| Area | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 (single Activity, single screen) |
| Architecture | MVVM: `MainViewModel` + `StateFlow`, Hilt for DI |
| Voice to text | `android.speech.SpeechRecognizer` (on-device where available, `EXTRA_PREFER_OFFLINE`) |
| Field extraction | Gemini API structured-output call (see §6); on-device regex fallback |
| AI provider | **Google Gemini** — `gemini-2.5-flash` for extraction, `gemini-2.5-pro` + Google Search grounding for the brief (model IDs editable in Settings) |
| Camera / selfie | CameraX (`LENS_FACING_FRONT`), photo saved to app storage |
| Local storage | Room (SQLite) for connects; photos and audio in `filesDir` |
| Network | Retrofit + OkHttp + kotlinx.serialization |
| Text to speech | `android.speech.tts.TextToSpeech.synthesizeToFile()` → `.wav` voice note |
| Background work | WorkManager (brief generation, retry when offline) |
| WhatsApp | `Intent.ACTION_VIEW` → `https://wa.me/<E164>?text=<urlencoded>` |
| Phone parsing | `libphonenumber` (normalise to E.164 using the device's region as default) |
| Min / Target SDK | minSdk 26 (Android 8.0), targetSdk 35 |
| Build | Gradle Kotlin DSL, version catalog, single `:app` module |

## 3. Screen layout (single page)
```
┌──────────────────────────────────┐
│  Meet Your Friend                │
│                                  │
│          ┌──────────┐            │
│          │   🎤📷    │  ← big round button
│          │  MEET    │    tap = talk, double-tap = selfie
│          └──────────┘            │
│   status: "Listening…"           │
│                                  │
│  [selfie thumbnail]              │
│  Name          [ Priya Sharma  ] │
│  Organization  [ Acme Corp     ] │
│  Role          [ Product Lead  ] │
│  Mobile        [ +971 50 123…  ] │
│                                  │
│  [Find on LinkedIn] [WhatsApp ✉] │
│  Brief: "Priya leads product…" ▶ │  ← ▶ plays voice note
│                                  │
│  ── My connects (12) ──────────  │
│  • Priya Sharma · Acme · today ✓ │
│  • John Lee · Globex · 3 Oct  ✓  │
└──────────────────────────────────┘
```
- Every field is editable (voice recognition will make mistakes).
- **Save** happens automatically once Name and Mobile are present; a "New" action clears the form for the next person.
- The "My connects" list sits below on the same scrolling page (no extra screens). Tapping a row loads that person back into the fields above.

## 4. Button behaviour
| Gesture | Action |
|---|---|
| Single tap | Start voice capture (mic ripple animation, "Listening…"). Tap again to stop early. |
| Double tap | Open front camera preview overlay → shutter → selfie attached to the current connect. |
| Long press (optional) | Start a new, blank connect. |

Implementation detail: Compose `pointerInput { detectTapGestures(onTap, onDoubleTap) }`. A single tap only fires after the double-tap window (~300 ms) has passed, which is a small, acceptable delay. Haptic feedback on both gestures.

## 5. Voice capture flow
**Mode: free speech (decided).** One utterance, for example:
> "This is Priya Sharma, she's a Product Lead at Acme Corp, number plus nine seven one five zero one two three four five six seven."

1. `SpeechRecognizer` returns the transcript. Partial results are shown live under the button.
2. The transcript goes to extraction (§6) and comes back as `{name, organization, role, mobile}`.
3. Fields are filled in. Anything missing is highlighted, and tapping the button again records **only the missing fields** (the app prompts "Say their mobile number").

Guided prompts are out of scope for v1. Offline, the rule-based extractor (§6) handles the free sentence.

## 6. Field extraction
- **Primary:** Gemini `generateContent` with `responseMimeType: application/json` + `responseSchema` (`name`, `organization`, `role`, `mobile`). Short prompt, `temperature 0`.
- **Fallback (offline / no key):** on-device rules. A digit-sequence and spoken-number regex for the mobile ("double five" → 55, "plus" → +), and the keywords "at / works for / from" → organization, "is a / as" → role.
- The mobile number is normalised with libphonenumber. If it is invalid, the field is flagged red.

## 7. LinkedIn lookup + brief / voice note
**Important constraint:** LinkedIn has **no public API for searching people**, and scraping it breaks LinkedIn's Terms of Service (accounts get blocked). So the app will **not** scrape LinkedIn. Instead it does three things:

1. **Find on LinkedIn button:** opens the LinkedIn app or browser on a people search:
   `https://www.linkedin.com/search/results/people/?keywords=<Name Organization>`
   You pick the right profile, and can optionally paste/share its URL back into the app (the app registers a share target for `linkedin.com/in/...` links).
2. **Brief generation:** a Gemini call with the **`google_search` grounding tool** (public web only; Google indexes public LinkedIn profiles) using Name + Organization + Role (+ profile URL if known). It returns:
   - a **~150-word brief** (who they are, company context, 2–3 conversation follow-ups), with source links;
   - a confidence flag. If the person can't be confidently identified, the brief says so instead of guessing.
3. **Voice note:** the brief is turned into a `.wav` on the device with Android TTS (free and offline). A ▶ play button appears next to it, and the note can be shared.

The brief runs in WorkManager. If you're offline it is queued and retried later.

## 8. WhatsApp message
- **WhatsApp button** builds:
  `Hi {Name}, it was great meeting you today{ at {Event}}! Looking forward to staying in touch. – {MyName}`
- It opens `https://wa.me/{E164 without +}?text={encoded}` (falling back to the WhatsApp Business package if that's the one installed).
- **Constraint:** Android/WhatsApp does **not** allow sending silently from a personal account. WhatsApp opens with the message pre-filled, and you tap **Send** (one tap). Fully automatic sending would need the WhatsApp Business Cloud API (business number, Meta-approved templates), which is out of scope for v1.
- The message template can be edited in Settings. The connect is marked "WhatsApp sent ✓" once the intent is launched.

## 9. Tracking your connects
Room entity `Connect`:
| Field | Type |
|---|---|
| id | Long (PK) |
| name, organization, role | String |
| mobileE164 | String |
| selfiePath | String? |
| linkedinUrl | String? |
| briefText | String? |
| voiceNotePath | String? |
| transcript | String (raw voice text) |
| metAt | Instant |
| location | String? (optional, coarse, opt-in) |
| whatsappSentAt | Instant? |
| notes | String? |

- The list shows: newest first, a search box, and status chips (Brief ✓, WhatsApp ✓, Selfie ✓).
- Optional **"Save to Contacts"** action (`ContactsContract.Intents.Insert`, no extra permission needed).
- Export all connects as CSV via the share sheet.

## 10. API key (decided: in-app, no backend for v1)
- The Gemini API key is entered in Settings and stored in `EncryptedSharedPreferences` (Android Keystore-backed). The app calls `generativelanguage.googleapis.com` directly.
- The key is never logged or included in exports. A "Test key" button checks it works.
- This is meant for personal use. A backend proxy can be added later without changing the UI (the repository interface stays the same).

## 11. Permissions & privacy
| Permission | Why | When requested |
|---|---|---|
| `RECORD_AUDIO` | voice capture | first single tap |
| `CAMERA` | selfie | first double tap |
| `INTERNET` | extraction / brief | install time |
| `ACCESS_COARSE_LOCATION` | optional "where we met" | opt-in setting only |

- All data is stored on the device. Only the transcript and Name/Org/Role are sent to the Gemini API. Photos and phone numbers are never uploaded (the mobile number is extracted from the transcript, so the transcript does contain it; the brief request does not).
- A first-run notice explains what is sent where. "Delete connect" removes the DB row, the photo, and the audio.
- Room DB is excluded from cloud backup by default (`dataExtractionRules`).

## 12. Settings (bottom sheet, same page)
My name (used in the WhatsApp message) · WhatsApp template · Gemini API key · Model IDs · Default country code · Auto-generate brief (on/off) · Location tagging (on/off).

## 13. Error / edge cases
- No speech recognised → "Didn't catch that — tap to retry".
- WhatsApp not installed → fall back to SMS (`smsto:`) with the same text.
- Number not on WhatsApp → WhatsApp itself shows this; the app has no way to detect it beforehand.
- Duplicate mobile number → the app offers to merge with the existing connect.
- Offline → fields come from on-device rules; the brief is queued.
- Ambiguous LinkedIn identity → the brief is marked "unverified" and asks you to share the profile URL.

## 14. Project structure
```
meet-your-friend/
  app/src/main/java/.../
    MainActivity.kt
    ui/MeetScreen.kt, MeetButton.kt, ConnectList.kt, SelfieCamera.kt, SettingsSheet.kt
    ui/MainViewModel.kt
    voice/SpeechCapture.kt, FieldExtractor.kt (Gemini + rules)
    brief/BriefRepository.kt, BriefWorker.kt, VoiceNoteMaker.kt (TTS)
    share/WhatsAppLauncher.kt, LinkedInLauncher.kt
    data/AppDatabase.kt, ConnectDao.kt, Connect.kt
    di/AppModule.kt
  README.md
```

## 15. Testing & acceptance
- **Unit:** rule-based extractor (spoken numbers, keyword parsing), phone normalisation, WhatsApp URL encoding, DAO.
- **Compose UI tests:** single tap vs double tap routing, fields fill from a fake recogniser, list updates.
- **Manual on emulator/device:** voice → fields, selfie, brief + playback, WhatsApp opens pre-filled.
- **Acceptance criteria (v1):**
  1. One tap + one spoken sentence fills all 4 fields correctly for a clear utterance.
  2. Double tap takes a front-camera selfie and attaches it to the current connect.
  3. The WhatsApp button opens a chat with that number and the pre-filled message.
  4. A brief (text + playable voice note) appears within ~15 s when online.
  5. Connects persist across app restarts and appear in the list on the same page.

## 16. Delivery plan
1. Scaffold the Compose app, button gestures, editable fields, Room list.
2. Voice capture + rule-based extraction (works offline end-to-end).
3. CameraX selfie.
4. WhatsApp + LinkedIn intents, Save to Contacts.
5. Gemini extraction + grounded brief + TTS voice note (in-app key).
6. Settings, privacy notice, polish, tests, signed debug APK.

## 17. Decisions (locked)
1. AI provider: **Gemini** (best fit: Google Search grounding for public-profile lookup, fast Flash model for extraction).
2. API key: **entered in the app**, encrypted on device.
3. Voice: **one free sentence**.
4. Repo: new repo **`byteclub-labs/meet-your-friend`**.
5. WhatsApp: **pre-filled, user taps Send**.
