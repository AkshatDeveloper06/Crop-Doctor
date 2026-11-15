# CropDoctor: project overview (reference for the mentoring-round deck)

> How to use this file: each `##` section maps to roughly one slide. Items marked **[FILL]** are numbers you
> must take from your own device tests before presenting. Nothing in this file is an invented metric.

---

## 1. One-line pitch

**CropDoctor is an offline-first Android app that diagnoses crop leaf diseases from a phone photo and tells the
farmer, in Hindi or English, what to do today, what it costs, and when to check again.**

Tagline options: "A crop doctor in every pocket, even without internet." / "Photo in, plan out."

---

## 2. Problem

- Small farmers lose a large share of yield to leaf diseases that are cheap to treat if caught early.
- Expert help (KVK, agriculture officer) is far away and slow; village connectivity is unreliable.
- Existing apps need internet, speak only English, give a label but no action plan, and are trained on clean lab
  photos so they fail on real field photos.
- Pesticide labels are hard to read and doses are easy to get wrong (units, per-acre vs per-litre).

## 3. Solution in one picture (suggested diagram)

```
 Leaf photo ──► Quality check ──► On-device model ──► Verdict ──► Plan
 (CameraX)      (blur, dark,      (LiteRT, GPU /      (confident / unsure /   (actions, organic,
                 glare, leaf %)    CPU fallback)       unknown / not a leaf)   chemical, re-check date)
                                                                  │
 Pesticide label ─► OCR (ML Kit, Hindi+English) ─► dose per tank / per acre   │
 Question (voice/text) ─► offline Q&A over the knowledge base ──(online? optional AI)
                                                                  ▼
                              Saved to field history ─► trend chart ─► PDF report ─► reminder
```

---

## 4. Key features (one slide, or two if you demo)

| Feature | What it does | Why it matters |
|---|---|---|
| **Scan** | Live camera; checks photo quality first (blur, darkness, glare, how much is plant); reads the same region the on-screen box shows, with live smoothing | Bad photos are the top cause of wrong answers, so we say "retake" instead of guessing |
| **Honest verdicts** | Four outcomes: Confident, Unsure (shows top alternatives), Unknown, Not a leaf | Never forces a wrong disease name on a farmer |
| **Crop-first ranking** | Pools probability per crop, then picks the disease within that crop | Fewer cross-crop mistakes |
| **Action plan** | Severity, cause, symptoms, what to do today, organic option, chemical option, prevention, re-check date | The farmer needs a plan, not a label |
| **Hindi + English** | Whole UI, advice, voice read-out, voice input | Reaches the actual user |
| **Fields and trend** | Group scans by field, see health trend over time | Shows whether treatment is working |
| **Label scanner** | Reads a pesticide label with on-device OCR, parses dose, converts to per-acre / per-tank, supports Hindi labels | Prevents dosing mistakes |
| **Ask CropDoctor** | Voice or text questions answered from the on-device guide; contextual ("what about this one?") | Works offline |
| **Spray advisor** | Weather-based "spray now / wait" using wind, rain, temperature (only optional network call besides the AI helper) | Avoids wasted and washed-off sprays |
| **Reminders** | Local notifications to re-scan after treatment | Closes the loop |
| **PDF report** | Shareable diagnosis report with photo | Useful to show a KVK or shopkeeper |
| **Optional online helper** | If a key is configured and the phone is online, Ask uses an AI model grounded in the app's own guide; falls back to the offline answer silently | Better free-form answers, never a single point of failure |

---

## 5. What makes it different (the "innovation" slide)

1. **Offline-first, privacy-first.** Photos, scans and fields never leave the phone. Network is used only for the
   optional weather card (approximate coordinates) and the optional AI answer (question text only).
2. **Honest verdicts.** The app does not force an answer: it reports Confident, Unsure (with alternatives),
   Unknown, or Not a leaf, and checks photo quality first.
3. **Crop-first ranking.** Probability is pooled per crop before the disease is chosen, which cuts cross-crop mistakes.
4. **Says what the model cannot do.** For crops where the model learned only healthy or only diseased leaves, the
   result screen warns the farmer instead of over-claiming.
5. **Action plan, not just a label.** Bilingual cause, symptoms, what to do today, organic and chemical options,
   prevention and a re-check date for 44 diseases across 17 crops.
6. **Dose literacy.** Label OCR + unit/area conversion, including Hindi labels.
7. **Tiny and fast.** A 2.8 MB INT8 model that runs in about 10 to 17 ms per image on a mid-range phone.
---

## 6. Architecture

**Platform:** native Android (Kotlin), min SDK 26, target SDK 35.

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation-Compose |
| Camera | CameraX |
| On-device ML | LiteRT (TensorFlow Lite runtime) with GPU delegate and CPU fallback |
| OCR | ML Kit text recognition, Devanagari (bundled, works offline) |
| Voice | Android speech recognition (input), Android TTS (output) |
| Storage | JSON store in app-private storage, SharedPreferences, no cloud database |
| Background | WorkManager (local reminders) |
| Reports | Android PdfDocument + FileProvider |
| Weather | Open-Meteo (free, no key) |
| Optional AI | Chat-completion API over plain HTTPS, key injected at build time |

**Model files (app/src/main/assets/):**
- `model.tflite`: INT8 MobileNetV2; input size and uint8/float input are read from the model at startup.
- `labels.txt`: the model's own output order (54 labels).
- `label_map.json`: model label to the app's class id (`Crop___disease_id`); duplicate labels are merged.
- `model_meta.json`: thresholds (confident 0.70, unsure 0.40) and the numbers shown in Settings.
- Preprocessing: centre-square crop, resized to the model's input size.
- Class ids are shared with `knowledge_base.json`; a unit test fails the build if they drift apart.

**Knowledge base:** bilingual JSON, **44 disease entries across 17 crops** (plus a generic healthy entry per crop).
Each entry: severity, follow-up days, cause, symptoms, actions, organic, chemical, prevention.
*Doses are typical label values and must be reviewed by an agronomist / KVK before real use.*

---

## 7. The model

**Model:** MobileNetV2 (1.0), INT8 quantised, 2.8 MB, 54 output labels (53 distinct classes after merging a duplicate
raspberry label) covering 17 crops plus a "not a leaf" class. Input is a raw 0-255 RGB photo (rescaling is inside
the model); output is softmax probabilities.

**How the app feeds and reads it (this matters: the model is very sensitive to it):**
- The camera scan reads the middle 72% of the frame, sampling pixels nearest-neighbour at the live-frame size, and the
  on-screen yellow box is drawn at exactly that region. Big captured photos are first brought down to live-frame size.
- A region with too little plant colour is reported as "not a leaf" before the model runs.
- Live results are smoothed with a running average (0.35 weight on the newest frame).
- Model labels are mapped to the app's class ids (`label_map.json`, duplicate labels merged), then a crop-first ranker
  and certainty thresholds (confident 0.60, unsure 0.35) produce the verdict.
- We compared preprocessing variants on the phone: feeding the whole frame (smooth) made unrelated leaves come out as
  "raspberry", while this feed gives confident, correct answers on test images.

**Measured on the target phone (moto g62 5G, Android 13):**

| Metric | Value |
|---|---|
| Size | 2.8 MB |
| Inference, GPU delegate | 16.6 ms / image |
| Inference, CPU (4 threads) | 10.6 ms / image |
| Accuracy (model's own validation split) | 91.4% INT8, 93.2% float |

**Be careful with the accuracy number.** It comes from the model's own validation split, which is not a
field-photo test, and the probabilities are not temperature-calibrated. Say "91.4% on the validation split", never
"91.4% accurate in the field". Add a number from your own test photos before presenting: **[FILL: N phone photos,
X correct]**.

**Known coverage gaps (shown in the app):**
- Rice, citrus (orange / lemon) and squash have no "healthy" class, so a healthy leaf of these crops can be
  mislabelled with a disease.
- Blueberry, raspberry and soybean have only a "healthy" class, so the model cannot detect their diseases; the
  result screen tells the farmer not to rely on a "healthy" answer for them.

## 8. Data and knowledge base

- The classifier was trained on public plant-disease datasets covering the classes listed in `labels.txt`.
  **[FILL: dataset names, licences and sizes, taken from your model's documentation]**
- The advice shown to farmers comes from the app's own bilingual knowledge base (`knowledge_base.json`), 44 diseases
  across 17 crops. Doses are general label-style values and need expert (KVK) review before real use.
---

## 9. Demo script (3 to 4 minutes)

1. Open app → choose Hindi → show home (spray card, recent scans).
2. **Scan** a leaf (use a printed or on-screen leaf photo if there is no real plant): quality hint, then verdict.
3. **Result**: severity, action plan, tap "listen", create PDF, set a reminder.
4. Scan something that is **not a leaf** → show "not a leaf" instead of a false diagnosis.
5. **Label scanner**: point at a pesticide label → dose and per-acre conversion.
6. **Ask** by voice in Hindi: "टमाटर पछेती झुलसा का इलाज".
7. **Fields**: show the health trend chart.
8. Turn on airplane mode and repeat one scan to prove it is offline.

Have a backup screen recording in case the camera or lighting misbehaves.

---

## 10. Verification done so far (be precise about this)

- Unit tests (37, all passing): label dose parser, ranking and smoothing, image-quality checks, spray advisor,
  knowledge-base integrity, and that the model labels, label map and knowledge base agree with each other.
- On a physical phone (moto g62 5G): the app installs, the model loads on the GPU delegate, Settings shows the model
  details, and the benchmark runs (16.6 ms GPU, 10.6 ms CPU) with no crashes.
- **Not yet verified:** accuracy on real leaf photos taken with the app, label OCR on real labels, PDF share,
  reminder delivery. Test a handful of real or on-screen leaf images and note how many are right.
---

## 11. Limitations and honest caveats (mentors respect this)

- First-level advice only; it can be wrong. Always confirm with KVK / agriculture officer and follow the label.
- The model's accuracy figure is from its own validation split, not a field test; real-world accuracy will be lower, especially on cluttered field photos and on crops without a healthy class.
- The knowledge-base doses are general values and need expert review.
- The optional AI helper is text-only, cannot diagnose from images, and its answers are labelled "AI answer, verify with KVK".
- A key embedded in an APK can be extracted; fine for a demo, not for production (a proxy server would be needed).
- A diagnosis from one photo cannot replace field scouting for multi-factor problems (nutrient deficiency, pests, water stress).

## 12. Future scope

- Collect and label our own Indian field photos with KVK partners (biggest accuracy lever).
- Add more Indian crops and diseases (wheat, mango, chilli, groundnut, banana, tea).
- Pest and nutrient-deficiency detection; severity estimation from lesion area.
- Regional-language expansion (Marathi, Telugu, Tamil, Bengali, Punjabi).
- Federated / opt-in feedback loop: farmer marks "correct / wrong" to improve the model with privacy.
- Integration with mandi prices, crop calendars and government schemes.
- Server-side proxy for the AI helper; KVK expert escalation ("send to expert").

---

## 13. Likely mentor questions (prepare short answers)

| Question | Short answer |
|---|---|
| Why on-device? | Rural connectivity, privacy, latency, zero running cost. |
| Why a small MobileNetV2 model? | It is 2.8 MB, runs in 10 to 17 ms on a mid-range phone and works fully offline; the app wraps it with quality checks, certainty thresholds and honest warnings. |
| How accurate is it? | 91.4% on the model's validation split. We say plainly that this is not a field test, we show the accuracy caveat in Settings, and we tested on **[FILL: N own photos]**. |
| What if the leaf is of a crop you do not know? | Low confidence → "Unsure/Unknown" or "Not a leaf", with advice to consult KVK. |
| How are doses made safe? | Reference doses are label-style values, always "follow the label", mask and gloves reminder; label scanner double-checks; expert review planned. |
| Business / impact model? | Free for farmers; partner with KVKs, FPOs, agri-input retailers; B2B dashboards later. |
| What is the AI fallback and is it safe? | Optional, text-only, grounded in our own guide, clearly labelled, never replaces the offline answer. |
| Data privacy? | Photos, fields and history stay on the phone; network only for weather coordinates and optional AI question text. |
| What did you build vs reuse? | The app, knowledge base, label scanner, ranking and safety logic were built by us; the classifier is a pretrained MobileNetV2 plant-disease model, and open-source libraries (CameraX, LiteRT, ML Kit) are used under their licences. |

---

## 14. Suggested slide outline (12 slides)

1. Title + tagline
2. Problem (farmer pain, with one striking stat you can cite from a source **[FILL + cite]**)
3. Solution overview (the pipeline diagram from section 3)
4. Live demo (or demo video)
5. Key features (section 4 table, trimmed to 6 rows)
6. Why it is different (section 5)
7. Architecture and tech stack (section 6)
8. The model: what it is, speed on a real phone, honest accuracy and gaps (section 7)
9. Safety net: quality gate, certainty levels, coverage warnings, your own test results **[FILL]**
10. Safety, privacy and limitations (sections 10–11)
11. Impact and future scope (section 12)
12. Team, thanks, Q&A

**Design tips:** one idea per slide; screenshots of Scan, Result, Label scanner and Fields; one clean diagram for
the pipeline; a single results table with the field-accuracy row highlighted; keep Hindi UI screenshots, they
carry the "built for India" message.

---

## 15. Repository map (for a backup slide)

```
CropDoctor/
  app/                    Android app (Kotlin, Compose)
    ai/                   classifier, ranking, smoothing, image quality
    knowledge/            knowledge base + offline Q&A
    online/               optional AI helper (silent fallback to offline)
    ocr/                  label reader + dose parser
    weather/              spray advisor
    work/                 reminders
    report/               PDF export
    ui/                   Compose screens (home, scan, result, fields, label, ask, settings)
    assets/               model.tflite, labels.txt, label_map.json, model_meta.json, knowledge_base.json
```
