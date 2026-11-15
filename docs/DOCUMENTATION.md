# CropDoctor: complete documentation

CropDoctor is an Android app that looks at a photo of a crop leaf, names the disease (or says it is not sure),
and tells the farmer what to do today, what it costs, and when to check again. It works in Hindi and English, and
its core features work with no internet.

This document explains how to use every feature, how the app works, how to build and configure it, what has been
verified, and where its limits are.

**Contents**

1. [Quick start for a farmer](#1-quick-start-for-a-farmer)
2. [Feature guide](#2-feature-guide)
3. [How a diagnosis is made](#3-how-a-diagnosis-is-made)
4. [Supported crops and diseases](#4-supported-crops-and-diseases)
5. [What needs internet](#5-what-needs-internet)
6. [Privacy and stored data](#6-privacy-and-stored-data)
7. [Install, build and configure](#7-install-build-and-configure)
8. [Changing the model or the knowledge base](#8-changing-the-model-or-the-knowledge-base)
9. [Project structure](#9-project-structure)
10. [Testing and verification status](#10-testing-and-verification-status)
11. [Known limitations](#11-known-limitations)
12. [Troubleshooting](#12-troubleshooting)
13. [Demo walkthrough](#13-demo-walkthrough)

---

## 1. Quick start for a farmer

1. Open **CropDoctor**. Pick **EN** or **हि** at the top for English or Hindi.
2. Tap **Scan a leaf**. Hold the phone so **one leaf fills the yellow box**, in daylight, then tap the **green button**.
3. Read the result: the disease name, how sure the app is, **What to do today**, organic and chemical options, and
   when to scan again. Tap **Listen** to hear it, **Share report** to send a PDF, **Remind me** to get a notification
   when it is time to re-check.

Always confirm with your nearest KVK (Krishi Vigyan Kendra) or agriculture officer, and follow the dose printed on the
product label. CropDoctor gives first-level advice and can be wrong.

---

## 2. Feature guide

### 2.1 Home screen

| Item | What it does |
|---|---|
| **EN / हि** | Switches the whole app between English and Hindi, instantly. The choice is remembered. |
| **Settings** (gear) | Opens Settings (section 2.9). |
| **Works offline** badge | Reminds the farmer that scanning needs no internet. |
| **Scan a leaf** | Opens the camera scanner (2.2). |
| **My fields** | Groups scans by field and shows whether a field is getting better or worse (2.5). |
| **Pesticide dose** | Reads or takes a pesticide label dose and works out how much to mix (2.6). |
| **Ask a question** / **Ask by voice** | Question and answer in Hindi or English (2.7). |
| **Spray weather** card | Whether this is a good time to spray, and the disease risk from the weather (2.8). |
| **Recent scans** | Your last scans. Tap one to reopen its result. |

If the disease model cannot load, a red notice appears at the top of Home and Scan.

### 2.2 Scan a leaf

Tap **Scan a leaf** and allow camera access the first time.

**What you see**

- The live camera picture and a **yellow rounded box**. The box shows exactly the part of the picture the app reads;
  anything outside it is ignored. The box changes colour with what the app sees:
  - white: no leaf found yet
  - green: looks healthy
  - amber: a moderate problem, or the app is not sure
  - red: a severe disease
- A label above the box with the current guess, for example `Corn (Maize) · Common rust 96%`. While the app is unsure
  it shows `Not sure yet... move closer`.
- Hints about the photo: `Move closer: fill the frame with the leaf`, too blurry, too dark, too bright, glare.

**How to take a good scan**

1. Daylight, no strong shadow on the leaf, no flash glare.
2. One leaf, filling the yellow box, with the diseased part visible.
3. Hold steady until the label stops changing, then tap the **green button**.

**Other controls**

| Control | What it does |
|---|---|
| **Back arrow** | Leaves the scanner. |
| **No field ▾** | Chooses which field this scan belongs to (optional). |
| **Flash icon** | Turns the torch on or off. |
| **Gallery icon** (bottom left) | Diagnoses a photo you already have. The whole picture is used, not just the middle. |
| **Green button** | Takes the photo and opens the result. |

If no leaf is found the app says `No leaf found. Fill the frame with one leaf and try again.` and does not save
anything. If the camera permission is refused, the screen offers **Pick a photo instead**.

### 2.3 The result screen

Opened after a scan, from Recent scans, from a field, or from a reminder notification.

**Top to bottom**

1. **The photo** you scanned.
2. **Crop and disease name**, a **severity chip** (Mild, Moderate, Severe, or Healthy), `Spreads fast` when the disease
   moves quickly, the field name if you chose one, and a **confidence bar** with a percentage.
3. **Honesty notices** (only when they apply):
   - *"I am not fully sure..."*: medium confidence. Check the **Other possibilities** list and retake the photo.
   - *"Low confidence: treat this as a guess..."*: low confidence. Show the leaf to a KVK or agriculture officer.
   - A photo-quality note, for example *"The leaf is small in the photo. Move closer and retake."*
   - A **crop coverage** note when the model cannot judge that crop both ways (section 4).
4. **Listen**: reads the **whole result** aloud in the app language, in this order: crop and disease, how sure the app is,
   severity, why it happens, what it looks like, what to do today, organic options, chemical options, how to prevent it,
   when to scan again, and any AI advice you have already fetched. Units are spoken as words (*2.5 grams per litre*,
   *2 to 3 years*). A referral to a KVK officer is spoken **only when it is really needed**: low confidence, or a problem the
   saved guide has no entry for. Chemical advice is followed by one short safety line (follow the label dose, wear gloves and
   a mask). A full result takes about a minute; the button becomes **Stop**. Hindi needs a Hindi voice on the phone; if it is
   missing the app says how to install it.
5. **Share report**: builds a one-page A4 **PDF** with the photo and the advice and opens the phone's share sheet
   (WhatsApp, Telegram, email, and so on). Useful for showing a shopkeeper or a KVK officer.
6. **Re-check reminder**: `Remind me in N days` schedules a local notification (N depends on the disease: 3 to 14 days).
   It works offline and survives a phone restart. Tapping the notification opens this result.
7. **Advice cards** from the app's own guide: **Why it happens**, **What to do today**, **Organic / low-cost options**,
   **Chemical options** (with a **Scan label & calculate dose** shortcut), **Prevent it next time**.
8. **More advice from AI** (needs internet and is on by default; see 2.10).
9. **Spray weather**, for diseases that need treatment.
10. **Other possibilities**: the next most likely diseases with percentages, shown when the app is not confident.
11. **Ask a question about this**: opens Ask with this diagnosis as context.
12. The trash icon at the top deletes this scan.

### 2.4 Re-checking and reminders

- On a result, `Remind me in N days` schedules the notification. The first time, Android asks to allow notifications.
- The reminder says it is time to scan the plant again and opens that scan when tapped.
- Settings has **Send a test reminder in 1 minute** to check notifications work on your phone.

### 2.5 My fields

1. **My fields → Add field**, enter a **name** (for example *North plot*), choose the **crop**, and the **area in acres**,
   then **Save**.
2. Open a field to see its crop and area, **Scan a leaf in this field**, the field's scans, and the
   **Health over time** chart.
3. The chart plots severity for each scan: **higher means more severe**, a line going down means improving. The app
   labels the trend **Improving**, **Getting worse**, **No change**, or **Unknown**. It needs **at least two scans**.
4. **Delete field** removes the field; **its scans stay in your history**.

### 2.6 Pesticide dose (label scanner and calculator)

Works out how much product to put in a spray tank. Fully on the phone, English and Hindi labels.

1. **Pesticide dose** opens the camera. Aim at the line that says how much to mix per litre, for example
   `2 ml per litre`, and hold steady. When the same dose is read twice in a row it is accepted (this filters out
   misreads).
2. Other ways in: **Photo** (pick a picture of the label) or **Type the dose** (enter the amount and choose `ml per
   litre` or `g per litre`, then **Next**).
3. Choose the **spray-tank size**: 10, 15, 16, 20 or 200 litres, or **Other size** to type your own.
4. The app shows the amount to add, for example `2.5 g per litre` in a 16 L tank gives **40 g**, and in a 20 L tank
   **50 g**. **Say it aloud** reads it out. **Scan another label** starts again.

How doses are read:

- Understands `2 ml / 1 l`, `2-2.5 g per 1 l`, `20 ml per 10 l`, Hindi words (`प्रति`, `से`, `मिली`) and Devanagari digits.
- If a **range** is printed, the **lower** value is used (the safer one).
- `500 ml per acre in 200 l` is converted to per litre. **Per-hectare-only doses are not converted.**
- A safety reminder is always shown: gloves and a mask, clean water, never exceed the label dose, keep children and
  animals away, confirm with a KVK if unsure.

### 2.7 Ask a question

A chat for crop questions, by typing or by voice, in English or Hindi.

- **Type** in the box and press send, tap a **suggestion chip** (for example *How to treat tomato late blight*), or tap
  the **microphone** and speak. Voice uses the phone's own speech recognizer; Hindi uses `hi-IN`.
- Every answer is **read aloud automatically**; tap an answer to hear it again. The stock "confirm with your KVK" clause is left out of the spoken version (it stays on screen).
- It understands the crop, the disease and what you want to know: cause, symptoms, treatment, organic options,
  prevention, spread, or timing. Phrases like *"this"*, *"my plant"*, *"इस"* refer to the scan you came from.
- Questions such as *"when should I spray"* or *"is it safe"* get general safety and timing answers.

**Where the answer comes from** (shown under each answer):

| Label | Meaning |
|---|---|
| **Offline answer · tap to hear** | From the guide saved on the phone. No internet. |
| **AI answer, verify with your KVK · tap to hear** | From the online AI helper, grounded in the saved guide (section 2.10). |
| **Thinking…** | Waiting for the AI helper. |

When internet is available and **Online answers** is on in Settings, the app asks the AI helper first and quietly falls
back to the offline answer if anything goes wrong. The header line says **Online** or **Answers come from the guide
saved on your phone**.

### 2.8 Spray weather

A card on Home and on diagnosed results with two judgements from the next hours of forecast:

- **Spray timing** (next 6 hours): **Good time to spray**, **Caution**, or **Avoid**. Rain chance of 50% or more, or wind of
  20 km/h or more, gives *Avoid*; 30% rain, 12 km/h wind, or 35 °C or more gives *Caution*. Early morning or evening is best.
- **Disease risk** (next 24 hours): **low, medium or high**, from humidity, temperature and the number of very wet hours.
  Cool and humid weather for many hours favours blights, mildews and blast, so the card says *Scout today*.

It uses the free Open-Meteo service and your **approximate** location (rounded to about 1 km). Offline, the card says the
forecast is unavailable. The scan itself never needs it.

### 2.9 Settings

| Section | What it does |
|---|---|
| **Language** | English or हिन्दी. |
| **Disease model** | Shows the model (architecture, number of classes, size, precision), whether it runs on the **GPU** or **CPU**, and its validation accuracy with a note that this is not a field test. **Benchmark this phone** measures milliseconds per image on GPU and CPU. |
| **Online answers** | Shown only when an AI key is built in. Turns the AI helper on or off (it is on by default). |
| **Reminders** | **Send a test reminder in 1 minute** checks that notifications work. |
| **Privacy** | Explains what stays on the phone. **Delete all scan history** removes all scans and photos after a confirmation. |

### 2.10 AI helper (optional, online)

When an API key is built into the app, the phone is online, and **Online answers** is on, the app can use an AI text model in
two places:

1. **Ask**: free-form questions are answered by the AI, told to follow the app's own guide for the relevant disease.
2. **Result screen, "More advice from AI"**: tap **Get AI advice** for a four-part plan (do today, low-cost or organic option,
   prevent next time, safety and cost). If the saved guide has **no entry** for a diagnosis, this is fetched
   automatically. **Refresh** fetches it again.

Advice you fetched once is **saved on the phone**, so it still shows without internet later. The AI never sees photos. It only
reads text, so it cannot diagnose from an image. AI text is always labelled and the farmer is told to verify with a KVK.

---

## 3. How a diagnosis is made

```
camera frame ─► photo-quality check ─► plant-colour check ─► on-device model ─► crop-first ranking ─► verdict ─► advice
 (live, ~5 fps)  (blur, dark, glare)    (is there a leaf?)    (MobileNetV2 INT8)   (crop, then disease)   (sure / unsure / unknown / not a leaf)
```

1. **Reading the picture.** The app reads the **middle 72%** of the camera frame, sampling one source pixel per model pixel.
   Large captured photos are first brought down to live-frame size. This matches how the model was trained to read pictures,
   and the on-screen yellow box is drawn on exactly that region. (Gallery photos use the whole centre square, shrunk
   smoothly.)
2. **No leaf.** If the region has almost no plant colour (under 12%) the result is **not a leaf** and nothing is saved.
3. **Model.** A MobileNetV2 INT8 network (2.8 MB) returns a probability for each of 54 model labels. Duplicate labels that
   mean the same thing are merged (53 distinct classes, 52 plus a "not a leaf" class).
4. **Smoothing.** The live label is a running average of recent frames (0.35 weight on the newest), so it stays calm.
5. **Crop-first ranking.** The probability of every disease of a crop is added up to decide the **crop** first, then the best
   disease inside that crop is chosen. This avoids a lucky single class from the wrong crop beating the crop actually in view.
6. **Certainty levels** from the probability of the chosen disease:

   | Level | When | What the farmer sees |
   |---|---|---|
   | **Confident** | 60% or more | The diagnosis, without a warning. |
   | **Unsure** | 35% to 60% | The diagnosis, a *"not fully sure"* notice and alternatives. |
   | **Unknown** | below 35% | A red *"treat this as a guess"* notice and alternatives. |
   | **Not a leaf** | background class wins, or no plant colour | Nothing is saved; the farmer is asked to try again. |

7. **Photo quality** is checked separately (blur, darkness, too bright, glare, leaf too small) and shown as a hint.

The model is **not temperature-calibrated**, so a high percentage means "the model is sure", not "it is right 96% of the time".
The model is also sensitive to how the leaf is framed and lit, so **fill the yellow box with one leaf**.

---

## 4. Supported crops and diseases

The model has 52 leaf classes across **17 crops**, plus a *not a leaf* class. The app's guide has advice for every disease below.

| Crop | Diseases the model can name (typical severity) | Has a "healthy" class | Note |
|---|---|---|---|
| Apple | Apple scab (moderate); Black rot (frogeye leaf spot) (moderate); Cedar apple rust (moderate) | yes |  |
| Blueberry | (none) | yes | Healthy class only |
| Cherry | Powdery mildew (moderate) | yes |  |
| Citrus (Orange / Lemon) | Citrus greening (HLB) (severe) | no | No healthy class |
| Corn (Maize) | Gray leaf spot (moderate); Common rust (moderate); Northern leaf blight (moderate) | yes |  |
| Cotton | Alternaria leaf spot (moderate); Wilt (Fusarium / Verticillium) (severe) | yes |  |
| Grape | Black rot (severe); Esca (black measles) (severe); Leaf blight (Isariopsis leaf spot) (moderate) | yes |  |
| Peach | Bacterial spot (moderate) | yes |  |
| Chilli / Capsicum | Bacterial spot (moderate) | yes |  |
| Potato | Early blight (moderate); Late blight (severe) | yes |  |
| Raspberry | (none) | yes | Healthy class only |
| Rice (Paddy) | Bacterial leaf blight (severe); Bacterial leaf streak (moderate); Bacterial panicle blight (severe); Brown spot (moderate); Leaf blast (severe); Leaf smut (mild) | no | No healthy class |
| Soybean | (none) | yes | Healthy class only |
| Squash / Gourd | Powdery mildew (moderate) | no | No healthy class |
| Strawberry | Leaf scorch (moderate) | yes |  |
| Sugarcane | Mosaic (moderate); Red rot (severe); Rust (moderate); Yellow leaf disease (moderate) | yes |  |
| Tomato | Bacterial spot (moderate); Early blight (moderate); Late blight (severe); Leaf mould (moderate); Septoria leaf spot (moderate); Spider mites (moderate); Target spot (moderate); Yellow leaf curl virus (severe); Mosaic virus (severe) | yes |  |

**Coverage gaps, and what the app does about them**

- **No healthy class** (Rice, Citrus, Squash): a healthy leaf of these crops can be labelled with a disease. The result screen
  tells the farmer so.
- **Healthy class only** (Blueberry, Raspberry, Soybean): the model cannot detect their diseases, so a "healthy" answer is not
  reliable. The result screen says *"Do not rely on a healthy result"*.
- The guide also has entries for rice tungro, hispa, dead heart, downy mildew, and cotton bacterial blight and leaf curl virus.
  The model does not detect them, but **Ask** can still answer questions about them.

---

## 5. What needs internet

| Feature | Offline | Notes |
|---|---|---|
| Scan, diagnosis, result, advice cards | Works | The whole model and guide are inside the app. |
| Fields, history, trend chart | Works | Stored on the phone. |
| Pesticide dose (camera, photo, typed) | Works | Reading is done on the phone. |
| Ask (offline answers) | Works | From the saved guide. |
| Listen, Say it aloud | Works | The phone's text-to-speech; Hindi needs a Hindi voice installed. |
| Share report (PDF) | Works | Building the PDF is offline; sending needs the chosen app's network. |
| Re-check reminders | Works | Local notifications. |
| Spray weather card | Online only | Free forecast service; offline shows *unavailable*. |
| Ask (AI answers), More advice from AI | Online only | Optional; needs the built-in key; cached advice shows offline. |

---

## 6. Privacy and stored data

**Stays on the phone:** leaf photos, scans, fields, history, reminders, language choice, and any AI advice you fetched.
All of it lives in the app's private storage:

- `files/data/store.json`: fields and scan records
- `files/scans/`: scan photos
- app preferences: language, settings, location rounded to about 1 km, cached AI advice

**Sent over the internet, only when you use these optional features:**

- Spray weather: approximate coordinates, to the free Open-Meteo service.
- AI helper: the question text, or the crop and disease name, plus the text of one guide entry. **Never** photos, location, or
  scan history.

Delete everything with **Settings → Delete all scan history**, or uninstall the app.

---

## 7. Install, build and configure

**Requirements:** Android 8.0 (API 26) or newer, a camera, about 100 MB free. To build: JDK 17, Android SDK 35, and
internet for the first Gradle download.

**Build and install (Windows)**

```bash
./gradlew.bat :app:testDebugUnitTest          # run the 48 unit tests
./gradlew.bat :app:assembleDebug              # builds app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` (never commit it; copy `local.properties.example` to start) holds the SDK path and the optional key:

```
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
DEEPSEEK_API_KEY=<your key>       # optional; without it the app is fully offline
```

The key can also come from the environment variable `DEEPSEEK_API_KEY`. **A key compiled into an APK can be extracted**, so use a
demo key and do not publish that APK. A real deployment should call a small server that holds the key.

**Permissions the app asks for**

| Permission | Why |
|---|---|
| Camera | Scanning leaves and labels. |
| Internet / network state | Spray weather and the AI helper only. |
| Approximate location | Spray weather only. |
| Notifications | Re-check reminders. |

**Release builds** have code shrinking switched on (`app/proguard-rules.pro`). A signed release build has not been produced or
tested yet.

---

## 8. Changing the model or the knowledge base

### 8.1 The model files (`app/src/main/assets/`)

| File | Content |
|---|---|
| `model.tflite` | A LiteRT image classifier. Any size; input is read from the model. Input is raw 0 to 255 RGB, **uint8 or float32**, rescaling inside the model. Output is probabilities (or logits, which are converted). |
| `labels.txt` | The model's own output order, one label per line. |
| `label_map.json` | Model label → app class id `Crop___disease_id`. Several labels may share one id and are added together. |
| `model_meta.json` | Display name and numbers for Settings, and the thresholds `confident_threshold` (0.60) and `unsure_threshold` (0.35). |

To use a different model, replace these four files and rebuild. The class ids in `label_map.json` must match the knowledge base.
A unit test (`ModelAssetsTests`) fails the build if a label is unmapped or a disease has no guide entry.
The 72% reading region is the constant `CROP_FRACTION` in `ai/CropClassifier.kt`, and it also sizes the yellow box.

### 8.2 The knowledge base (`knowledge_base.json`)

One entry per class id, in English and Hindi. To add a disease, add an entry under `diseases` and, for a new crop, a name under
`crops`:

```json
"Wheat___yellow_rust": {
  "name":     { "en": "Yellow rust", "hi": "पीला रतुआ" },
  "severity": 2,                      // 1 mild, 2 moderate, 3 severe
  "followUpDays": 7,
  "spreadsFast": true,
  "cause":    { "en": "...", "hi": "..." },
  "symptoms": { "en": "...", "hi": "..." },
  "actions":  { "en": ["..."], "hi": ["..."] },
  "organic":  { "en": ["..."], "hi": ["..."] },
  "chemical": { "en": ["..."], "hi": ["..."] },
  "prevent":  { "en": ["..."], "hi": ["..."] },
  "keywords": ["rust", "रतुआ"]
}
```

The English and Hindi lists must have the same length (a test checks this). **Chemical doses are general label values and must
be reviewed by an agronomist or KVK before real use.** Add crop and disease words to `knowledge/OfflineAdvisor.kt` so Ask
recognises them.

For a class with **no entry**, the app shows a safe *consult your KVK* message, and fetches AI advice when online.

---

## 9. Project structure

```
CropDoctor/
  README.md                  start here
  docs/DOCUMENTATION.md      this file
  docs/PROJECT_OVERVIEW.md   pitch and slide material
  benchmark/                 accuracy, F1 and confusion-matrix tooling (see its README)
  local.properties.example   template for the git-ignored local.properties
  app/src/main/
    AndroidManifest.xml
    assets/                  model.tflite, labels.txt, label_map.json, model_meta.json, knowledge_base.json
    java/com/cropdoctor/app/
      CropDoctorApp.kt       creates the shared objects once
      MainActivity.kt        entry point; opens a result from a notification
      ai/                    CropClassifier (model + preprocessing), Verdict (ranking, smoothing), ImageQuality
      knowledge/             KnowledgeBase (bilingual guide), OfflineAdvisor (offline Q&A)
      online/                OnlineAdvisor (AI helper, cache)
      ocr/                   LabelReader (camera text), LabelParser (dose maths)
      weather/               SprayAdvisor (rules + forecast)
      work/                  Reminders (notifications)
      report/                ReportPdf (A4 report)
      audio/                 Speaker (text to speech)
      data/                  Store (scans, fields), Prefs, Models
      ui/                    screens: home, scan, result, fields, label, ask, settings; theme and shared components
  app/src/test/              unit tests
```

**Technology:** Kotlin, Jetpack Compose (Material 3), CameraX, LiteRT (TensorFlow Lite) with a GPU delegate and CPU fallback,
ML Kit text recognition (Devanagari, bundled, offline), WorkManager, Android text-to-speech and speech recognizer,
Open-Meteo, and a plain HTTPS call for the optional AI helper.

---

## 10. Testing and verification status

**Automated:** 48 unit tests pass. They cover the dose parser, ranking and smoothing, photo-quality checks, the spray rules,
the knowledge base (every entry complete and bilingual), and that the model labels, label map and knowledge base agree.

**Checked by hand on a physical phone** (moto g62 5G, Android 13) and, earlier, on an emulator:

| Feature | Result |
|---|---|
| Install, launch, model loads on GPU | Works |
| Home screen, recent scans, Hindi and English switch | Works |
| Live scan with the guide box, capture, result screen | Works |
| Result: Listen (and Stop), advice cards | Works |
| Result: Share report | Works. The share sheet opens with a valid one-page PDF containing the photo. |
| Result: Remind me | A background job is scheduled. |
| More advice from AI (online) | Works. The advice is shown and saved. |
| Ask, online | Works. Answers follow the saved guide. |
| Ask, offline answer | Works (emulator, earlier session). |
| Pesticide dose, typed dose | Works. 2.5 g per litre gives 40 g in 16 L and 50 g in 20 L. |
| Say it aloud | Works. No crash. |
| My fields: add, open, delete | Works. |
| Field trend chart | Works with several scans (emulator). |
| Spray weather card | Works. |
| Settings, benchmark | Works. 16.6 ms on GPU, 10.6 ms on CPU. |
| Crash log during all of the above | Clean. |

**Not verified yet**

- Voice input (the microphone) and Hindi speech output.
- Reading a real pesticide label with the camera or a photo (OCR). Only the typed path and the parser tests were run.
- Diagnosing a picked **gallery photo** end to end.
- The Settings **test reminder** and a reminder notification actually arriving.
- Turning **Online answers** off and confirming Ask stays offline; AI advice in Hindi.
- **Accuracy on real leaves.** The model's 91.4% is on its own validation split, which is not a field test. In informal tests with images shown on a
  laptop screen, a corn common-rust image was read correctly, but a sugarcane rust image was read as corn rust. Screens also add pattern noise that
  real leaves do not, so test with real leaves.
- A signed release build.

---

## 11. Known limitations

- **First-level advice only.** It can be wrong; always confirm with a KVK or agriculture officer.
- **Real-world accuracy is lower than the validation accuracy,** especially on cluttered field photos and for crops the model
  saw few examples of. The model can also confuse look-alike crops (for example rusts on corn and sugarcane).
- **The model is sensitive to framing.** It only reads the middle 72% of the frame, so the leaf must fill the yellow box.
- **Coverage gaps** for Rice, Citrus, Squash (no healthy class) and Blueberry, Raspberry, Soybean (healthy class only).
- **Doses are general values** and need expert review before real use.
- **The AI helper is text only.** It cannot look at a photo, can still make mistakes, and its key is extractable from the APK.
- **Hindi speech needs a Hindi voice** installed on the phone; voice input needs a speech recognizer.
- **Per-hectare label doses are not converted** by the dose calculator.
- Photo-quality thresholds are starting points and have not been tuned on many real phone photos.

---

## 12. Troubleshooting

| Problem | What to do |
|---|---|
| Red notice *"The disease model could not be loaded"* | Reinstall the app. If it continues, the files in `assets/` are damaged or do not match (check `labels.txt` has as many lines as the model has outputs). |
| Everything is diagnosed as one crop, or answers jump around | Fill the yellow box with one leaf, in daylight, and hold steady. Avoid photographing a screen. If it persists after a model change, check the model's input range and that `CROP_FRACTION` suits it. |
| *"Not fully sure"* or *"Low confidence"* often | Retake closer and sharper; remove shadows; check the crop is one the model knows (section 4). |
| *"No leaf found"* | The region has too little plant colour. Move closer so the leaf fills the box. |
| Ask always says *Offline answer* | The phone is offline, **Online answers** is off in Settings, or the build has no AI key. |
| No **Online answers** switch in Settings | The build has no AI key (set `DEEPSEEK_API_KEY` in `local.properties` and rebuild). |
| Spray weather says unavailable | No internet, or location permission was refused. |
| No sound from **Listen** | Raise the volume; for Hindi install a Hindi text-to-speech voice in the phone's settings. |
| No reminder notification | Allow notifications for CropDoctor; use **Send a test reminder** to check; battery savers can delay background work. |
| `adb` shows no device (wireless debugging) | Turn Wireless debugging off and on, reconnect, and run `adb devices`. A very large install can stall on a weak Wi-Fi link; USB is more reliable. |
| `adb install` hangs | Check the phone for an install or Play Protect prompt, or use a USB cable. |

---

## 13. Demo walkthrough

About four minutes:

1. **Home**: switch to Hindi and back to show both languages. Point out the spray-weather card and *Works offline*.
2. **Scan a leaf**: fill the yellow box with a leaf image. Show the live label and the box colour, then tap the green button.
3. **Result**: read the disease, severity and confidence. Tap **Listen**. Show **What to do today**, **Organic**, **Chemical**.
   Tap **Get AI advice**. Tap **Remind me**. Tap **Share report** and show the PDF.
4. **Honesty**: scan something that is not a leaf and show that nothing is guessed. Show a low-confidence result and its notice.
5. **Pesticide dose**: type `2.5 g per litre`, choose 16 L, show **40 g**, tap **Say it aloud**.
6. **Ask**: tap *How to treat tomato late blight*; show the online answer. Switch on airplane mode and ask again to show the offline answer.
7. **My fields**: add a field, attach scans, show the trend chart.
8. **Settings**: show the model details and the benchmark number, and the privacy statement.
