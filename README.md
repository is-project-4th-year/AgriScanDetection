# AgriScan 🌿📱
**An Intelligent Tomatoes, Potatoes and Pepper Bell Disease Detection Using a CNN Model and RAG Agent for Advisory.**

[![Android](https://img.shields.io/badge/Android-15%20(targetSdk%2036)-3DDC84?logo=android&logoColor=white)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.x-7F52FF?logo=kotlin&logoColor=white)]()
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4?logo=jetpackcompose&logoColor=white)]()
[![TensorFlow Lite](https://img.shields.io/badge/TensorFlow%20Lite-MobileNetV2-F9AB00?logo=tensorflow&logoColor=white)]()

AgriScan is an Android app that helps smallholder farmers diagnose crop leaf diseases **offline** using an on-device **TensorFlow Lite** model fine-tuned from **MobileNetV2**. Beyond prediction, AgriScan ships with an **offline RAG (Retrieval-Augmented Generation) knowledge base** to explain likely causes, give actionable advice, and link the prediction to best-practice field guidance—without internet.

---

## Table of Contents
- [Key Features](#key-features)
- [App Screens](#app-screens)
- [Architecture](#architecture)
- [Model & Dataset](#model--dataset)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration: Assets](#configuration-assets)
- [Room Database](#room-database)
- [Build & Run](#build--run)
- [Results](#results)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)
- [Acknowledgements](#acknowledgements)
- [License](#license)

---

## Key Features
- **On-device disease classification** (TFLite): Tomato, Potato, Pepper (bell) classes from a curated PlantVillage subset.
- **Top-3 predictions with confidence** for better decision support on visually similar symptoms.
- **Offline Knowledge Assistant (RAG)**: Looks up disease pages from `kb.jsonl` and renders concise advice (cause, spread, control, what to do next).
- **Capture → Diagnose → Explain → Save** flow with per-field organization.
- **Local-first data**: Images stored in app storage; metadata & references in **Room** (`agriscan.db`).
- **Modern Android stack**: Kotlin, Jetpack Compose (Material 3), Navigation, Room, Coil.

---

## App Screens
> 📸 **Where to place images:** Put PNG/JPG screenshots in `docs/screens/` and keep the file names from the placeholders below (or update the paths here).

- **Capture** — take/select leaf photo  
  `docs/screens/capture.png`  
  ![Capture](docs/screens/capture.png)

- **Diagnosis** — top-3 predictions + confidence + quick actions  
  `docs/screens/diagnosis.png`  
  ![Diagnosis](docs/screens/diagnosis.png)

- **Knowledge Assistant (RAG)** — offline guidance tied to prediction  
  `docs/screens/knowledge.png`  
  ![Knowledge Assistant](docs/screens/knowledge.png)

- **Fields** — organize observations by field/plot; view history  
  `docs/screens/fields.png`  
  ![Fields](docs/screens/fields.png)

- **Library** — gallery of captured images and saved diagnoses  
  `docs/screens/library.png`  
  ![Library](docs/screens/library.png)

---

## Architecture
- **UI**: Jetpack Compose (M3), single-activity, Navigation Compose.  
- **Domain**: Use cases for capture, inference, save, retrieve.  
- **Data**:
  - **TFLite Inference** (MobileNetV2 head: GAP → Dropout(0.25) → Dense softmax).
  - **RAG**: `KBLoader` loads `kb.jsonl` (assets) and serves content by disease key/title.
  - **Persistence**: **Room** for metadata; image files stored under app’s internal files dir; DB stores file paths + attributes.

Feature flow:
Capture → Inference (TFLite) → Top-3 → (optional) RAG lookup → Save to Room → View in Library/Fields


---

## Model & Dataset
- **Base**: MobileNetV2 (ImageNet weights, `include_top=False`).
- **Training**: Google Colab; Transfer Learning:
  - **Warm-up** (base frozen): Adam `lr=3e-4`, 8 epochs
  - **Fine-tune** (unfreeze base; freeze bottom ~100 layers): Adam `lr=1e-5`, 10 epochs
- **Dataset**: PlantVillage (subset)
  - Tomato: Early/Late blight, Leaf mold, Septoria leaf spot, Bacterial spot, Target spot, Mosaic virus, Yellow Leaf Curl Virus, Two-spotted spider mite, Healthy
  - Potato: Early/Late blight, Healthy
  - Pepper (bell): Bacterial spot, Healthy
- **Exports** (on Colab):
  - `model_fp32.tflite`
  - `model_dr.tflite` (dynamic range)
  - `model_int8.tflite` (full INT8 with ~200 representative images)
- **Labels**: `labels.txt` (one class per line, order fixed from training)

---

## Project Structure
Agriscan/
├─ app/
│ ├─ src/main/
│ │ ├─ java/com/example/agriscan/
│ │ │ ├─ ui/… (Compose screens: Capture, Diagnosis, Knowledge, Fields, Library)
│ │ │ ├─ data/db/… (Room entities, DAO, Database)
│ │ │ ├─ rag/KBLoader.kt (loads kb.jsonl from assets, title mapping, retrieval)
│ │ │ ├─ ml/TFLiteClassifier.kt (model load/infer/top-3)
│ │ │ └─ MainActivity.kt
│ │ ├─ assets/
│ │ │ ├─ model_int8.tflite
│ │ │ ├─ labels.txt
│ │ │ └─ kb.jsonl
│ │ └─ AndroidManifest.xml
│ └─ build.gradle(.kts)
├─ docs/
│ └─ screens/ (README images: capture.png, diagnosis.png, knowledge.png, fields.png, library.png)
└─ README.md


---

## Getting Started
### Prerequisites
- **Android Studio** (Giraffe+ recommended), **AGP 8.x**, **JDK 17**
- Android SDK Platform **34/35/36** (targetSdk=36)
- **Minimum SDK**: 26+ (adjust as needed)

### Clone
```bash
git clone https://github.com/<your-username>/Agriscan.git
cd Agriscan

```
## Configuration: Assets

### Place the three assets here:
app/src/main/assets/
├─ model_int32.tflite      # preferred on-device model
├─ labels.txt             # class names (line-delimited; order = training order)
└─ kb.jsonl               # offline knowledge base (JSON Lines)

### kb.jsonl format (one JSON per line):
{"id":"tomato_late_blight","title":"Tomato — Late Blight","symptoms":["water-soaked lesions","white mold"],"advice":["Remove infected leaves","Use copper-based fungicide"],"notes":"Short, farmer-facing description"}
{"id":"potato_early_blight","title":"Potato — Early Blight","symptoms":["concentric rings"],"advice":["Rotate crops","Improve airflow"],"notes":"…"}

---
## Room Database

-> Name: agriscan.db

-> Location at runtime:
/data/data/com.example.agriscan/databases/agriscan.db (use Device File Explorer to inspect)

-> Entities (example):

  . Observation(id, imagePath, crop, top1Label, top1Conf, top3Json, adviceIds, fieldId, notes, createdAt, lat, lon)

  . Field(id, name, crop, location, notes, createdAt)

-> Images: Saved to the app’s internal files directory; Room stores file paths, not blobs.

---
### Build & Run

### Open project in Android Studio.

-> Ensure assets/ contains model_int32.tflite, labels.txt, kb.jsonl.

-> Click Run ▶ (physical device recommended).

-> Gradle (library hints) — already in project, but for reference:
  // Compose
  implementation(platform("androidx.compose:compose-bom:2024.10.01"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.navigation:navigation-compose:2.8.0")

  // Image loading
  implementation("io.coil-kt:coil-compose:2.6.0")

  // Room
  implementation("androidx.room:room-runtime:2.6.1")
  kapt("androidx.room:room-compiler:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")

  // TFLite
  implementation("org.tensorflow:tensorflow-lite:2.14.0")
  implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

---
## Results

### Validation (best checkpoint): ~0.9373 accuracy

### Held-out Test Set: 0.9345 accuracy
-> Per-class precision/recall/F1 and confusion matrix are exported by the Colab script:

  . agriscan_runs/<timestamp>/classification_report.txt
  
  . agriscan_runs/<timestamp>/confusion_matrix.png

### Why Top-3: plant diseases often exhibit overlapping visual symptoms; presenting the top-3 with calibrated confidence reduces false certainty and gives farmers safer options.

---
## Roadmap

. On-device explanations (Grad-CAM-like heatmaps)

. Batch capture and offline queueing

. Per-field trend analytics and seasonal insights

. Localization and low-literacy UX modes

---
## Acknowledgements

. PlantVillage dataset (research use)

. TensorFlow Lite & MobileNetV2

. Jetpack libraries (Compose, Navigation, Room)

---
## License

This project is licensed under the MIT License.
