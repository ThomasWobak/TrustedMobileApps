# Trusted Audio Editing

An Android app for recording and **verifiably editing** WAV audio. Every change is tracked, deletions are provable, and the final file carries cryptographic proofs (Merkle root + optional OpenPGP signature) so you can audit authenticity end-to-end.

---

## Key Features

- **Record & Import** `.wav` audio.
- **Block-wise editing**: mark blocks as deleted, move/reorder, play selections.
- **Tamper-evident format**:
    - **Merkle root** over all stored blocks (including deleted markers).
    - **Edit history** embedded into the file.
    - **Encryption** for deleted blocks (password-based).
    - **Detached OpenPGP signature** embedded in a RIFF chunk.
- **Rich metadata**: user/device/context metadata stored as a block.

---

## Project Structure (high level)

- `audio/` – WAV IO, custom RIFF chunks, block splitting/merging, metadata collection, edit history logic.
- `components/` – Bottombar and WaveFormView.
- `navigation/` – Logic for Navigation.
- `cryptography/`
    - `MerkleHasher` – computes SHA-256 Merkle roots.
    - `WavBlockDecrypter` / `…Encrypter` – password-based AES for deleted blocks.
    - `DigitalSignatureUtils` – OpenPGP (PGPainless) signing/verification.
- `screens/`
    - `Editing` – Main editing screen.
    - `Debug` – Editing with additional details but worse user experience.
    - `RecordAudioScreen` – Main screen for recording and importing audio.
- `protobuf/` – `.proto` definitions for:
    - `WavBlock` and related messages (edit history, metadata).

---

## The Custom WAV Layout (what the app writes)

Standard RIFF/WAVE header + **extra custom chunks**:

- `data` — concatenated **length-delimited** `WavBlock` protobuf messages (each block holds original PCM or marks a deletion).
- `meta` — collected Metadata about recording.
- `omrh` — “Open Merkle Root Hash” (32-byte SHA-256 Merkle root).
- `edit` — serialized `EditHistory` protobuf (append-only log of actions).
- `dsig` — **detached OpenPGP signature** over the logical audio payload + header fields + `omrh` (so signature binds the Merkle root).

---

## Building the Project

### Requirements
- Android Studio (Koala or newer recommended).
- JDK 17.
- Gradle wrapper included.

```bash
./gradlew generateReleaseProto
```
--- 

## Importing Private/Public Keys (OpenPGP)

The app supports **OpenPGP** keys (not legacy RSA/X.509). Keys may be **ASCII-armored** (`.asc`) or binary (`.gpg`). ASCII is recommended.

### Supported Formats
- **Secret key (private)**: ASCII-armored OpenPGP secret key ring (`.asc`) — used to sign.  
- **Public key**: ASCII-armored OpenPGP public key (`.asc`) — used to verify.  

### Exporting From GnuPG

```bash
# Public key (for verification)
gpg --export --armor YOUR_KEY_ID > public.asc

# Secret key (for signing) - keep safe!
gpg --export-secret-keys --armor YOUR_KEY_ID > secret.asc
```
---

## Installation

There are two ways you can install the app on your Android device:

---

### Option A — GitHub Releases (recommended)
1. Go to the [Releases](../../releases) page of this repository.
2. Download the latest `app-release.apk`.
3. On your Android device, open the downloaded file.
4. If prompted, allow your browser or file manager to install apps from unknown sources.
5. Tap **Install** and wait for the process to finish.

---

### Option B — Direct APK from repository
1. Navigate to the [`apk/`](./apk) folder in this repository.
2. Download the `app-release.apk` file.
3. On your Android device, open the file.
4. If prompted, allow your browser or file manager to install apps from unknown sources.
5. Tap **Install** and wait for the process to finish.  
