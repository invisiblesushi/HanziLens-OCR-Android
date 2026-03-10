# HanziLens

Android app that uses the camera to read Chinese characters and show pinyin on top of them in real time. Point it at a sign, book, menu — whatever — and it'll overlay the pronunciation. Freeze the frame to get a proper word-by-word dictionary lookup.

Built for my own use learning Mandarin. Open sourced in case it's useful to anyone else.

---

## What it does

- Live OCR overlay with pinyin labels aligned over detected characters
- Resizable ROI box so you can limit detection to one part of the screen
- Freeze frame — captures the image at full resolution, re-runs OCR, and auto-looks up every word it finds
- CC-CEDICT offline dictionary with word segmentation and tone-marked pinyin
- Lookup sheet shows pinyin + characters per block, tap a word to expand its definition

---

## Building

Standard Android project. One manual step required before you can build:

**Download the CC-CEDICT dictionary file:**
1. Go to https://www.mdbg.net/chinese/dictionary?page=cedict
2. Download the `.u8` archive and extract `cedict_ts.u8`
3. Place it at `app/src/main/assets/cedict_ts.u8`

On first launch the app parses the dictionary into a Room database (~30–60 seconds, one-time only). After that it loads from a binary cache instantly.

The dictionary file is not included in this repo — it's ~10 MB and licensed separately under CC BY-SA 3.0.

---

## Stack

- Kotlin + Jetpack Compose
- CameraX for camera preview and frame analysis
- ML Kit Chinese Text Recognition (on-device, no internet needed)
- Room for the dictionary database
- CC-CEDICT for dictionary data

Min SDK 26 (Android 8.0).

---

## License

App source code: MIT

CC-CEDICT dictionary data: Creative Commons Attribution-ShareAlike 3.0 — see the About screen in the app for full attribution.
