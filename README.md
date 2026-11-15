# CropDoctor

An offline-first Android app that looks at a photo of a crop leaf, names the disease (or says it is not sure), and tells
the farmer what to do today, what it costs, and when to check again. Hindi and English. The core features need no internet.

> CropDoctor gives first-level advice and can be wrong. Always confirm with a Krishi Vigyan Kendra (KVK) or agriculture
> officer and follow the dose printed on the product label.

## Features

- **Scan a leaf** with the live camera. The yellow guide box is exactly the part of the picture the model reads.
- **Honest verdicts**: confident, unsure, unknown, or not a leaf. A separate photo-quality check asks for a retake instead of guessing.
- **Action plan** in Hindi or English: cause, what to do today, organic and chemical options, prevention, re-check date. Listen to it read aloud.
- **Pesticide dose calculator**: reads a label (English or Hindi) on the device and turns it into an amount per spray tank.
- **Ask**: type or speak questions; answered from the saved guide, optionally with an online AI helper.
- **Fields and trend**: group scans by field and see whether it is getting better or worse.
- **Share** a one-page PDF report, and set a **re-check reminder** that works offline.
- **Spray weather** card (optional, online).

17 crops and 52 leaf classes are recognised; the guide has advice for 44 diseases. See [docs/DOCUMENTATION.md](docs/DOCUMENTATION.md)
for how to use every feature, the supported crops, and the known gaps.

## Build and run

Requirements: JDK 17, Android SDK 35 (Android Studio installs both), and a device or emulator running Android 8.0 (API 26) or newer.

```bash
git clone <this repository> && cd CropDoctor
cp local.properties.example local.properties      # then set sdk.dir (Android Studio does this for you)
./gradlew :app:testDebugUnitTest                  # 48 unit tests
./gradlew :app:assembleDebug                      # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`. Or just open the folder in Android Studio and press Run.

### Optional: online AI helper

Put a key in the git-ignored `local.properties`, or set the `DEEPSEEK_API_KEY` environment variable:

```
DEEPSEEK_API_KEY=<your key>
```

Without a key the app is fully offline and the matching Settings switch is hidden. **A key compiled into an APK can be
extracted**: use a demo key, never publish an APK built with a real one, and put a small proxy server in front of the key for any real deployment.

## How it works

```
camera frame -> quality check -> plant-colour check -> on-device model -> crop-first ranking -> verdict -> advice
```

The model is a compact MobileNetV2 INT8 classifier (about 2.8 MB) run with LiteRT, on the GPU with a CPU fallback. Label
reading uses the bundled ML Kit Devanagari text recognizer. Everything else (guide, history, reminders, PDF) is local.
Details, thresholds and the model file contract are in the documentation.

## Benchmarking the model

`benchmark/` has tools that produce accuracy, F1 and confusion matrices on the exact model that ships, either on a PC over a
labelled image folder or on a phone over your own photos. See [benchmark/README.md](benchmark/README.md). No benchmark results are
committed: generate them yourself on data you can describe honestly.

## Project layout

```
app/src/main/java/com/cropdoctor/app/   ai, knowledge, online, ocr, weather, work, report, audio, data, ui
app/src/main/assets/                    model.tflite, labels.txt, label_map.json, model_meta.json, knowledge_base.json
app/src/debug/                          debug-only benchmark screen
app/src/test/                           unit tests
benchmark/                              evaluation scripts
docs/                                   full documentation and pitch material
```

## Privacy

Photos, scans and fields stay on the phone. Two optional features use the network: spray weather (approximate coordinates, to
the free Open-Meteo service) and the AI helper (question text, or crop and disease name; never photos or location).

## Known limitations

- The model's reported accuracy comes from its own validation split, not a field test; real-world accuracy is lower.
- Rice, citrus and squash have no "healthy" class in the model, and blueberry, raspberry and soybean have only "healthy". The app warns about both.
- Doses are general label values and need expert review before real use.
- The classifier is a pretrained model; check its licence and the licences of the datasets it was trained on before redistributing.

## License

No license has been chosen yet. Add a `LICENSE` file before making the repository public, otherwise the code is
"all rights reserved" by default.
