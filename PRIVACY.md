# Privacy Policy — ReplyMind

ReplyMind is a final-year academic project, not a published product.
This document describes how it handles message data so anyone evaluating
the system can audit the behaviour.

## What is collected

Nothing is sent off-device to any third party by default. There are no
analytics SDKs, no telemetry pings, no crash reporters, and no advertising
identifiers in the code.

When the user enables ReplyMind for a messenger, the app receives
notifications from that messenger via the Android `NotificationListenerService`
permission. Each notification's body and sender title is read in memory.

## What is stored, and where

| Data                          | Where                                             | Encrypted? |
|-------------------------------|---------------------------------------------------|------------|
| Bearer token (backend auth)   | Android `EncryptedSharedPreferences`              | yes (AES-GCM via Android Keystore) |
| BYOK API key (if user opts in)| Android `EncryptedSharedPreferences`              | yes |
| User profile (name, occupation, relationships) | Android `EncryptedSharedPreferences` | yes |
| Audit-log row per message     | SQLite on the user's laptop (`backend/data/app.db`) | no — relies on the user's disk encryption (FileVault/LUKS) |
| Per-user message embeddings   | ChromaDB on the user's laptop (`backend/data/chroma/`) | no — same as above |
| Inbox snippet on the device   | Local Room database on Android                    | no — relies on Android's full-disk encryption |

Embeddings are 384-dimensional float vectors produced by
`sentence-transformers/all-MiniLM-L6-v2`. They are not directly invertible
to the original text.

## What leaves the device

- **Default mode (local backend)**: the inbound message body, sender title,
  and the user's saved profile are sent to the backend over HTTP loopback
  (`127.0.0.1:8765` via `adb reverse`, or `10.0.2.2:8765` on the emulator).
  This traffic does not leave the host machine.
- **BYOK mode (opt-in)**: the same fields are additionally sent to the LLM
  provider the user has configured (OpenAI / Groq / OpenRouter / Anthropic /
  custom). This is the only path on which message content leaves the user's
  network, and it is explicitly enabled by the user entering an API key.

## Sensitive content (OTPs, banking codes)

Messages matching promotional or spam patterns (which includes most OTPs
and verification codes by training-data construction) default to the
**SUPPRESS** action — no auto-reply is sent. The message body is still
processed by the classifier so the routing decision can be made, but no
reply leaves the device for those categories.

Users can disable ReplyMind for any specific messenger from the main screen.

## Deletion

All ReplyMind data on a device:

```
adb shell pm clear com.parishod.watomatic
```

All ReplyMind data on the laptop:

```
rm -rf backend/data/chroma backend/data/app.db
```

## Datasets used for training

The classifier is trained offline on publicly available datasets. The
user's messages are not used to train or fine-tune the model. See
[`backend/backend/data/mapping.md`](backend/backend/data/mapping.md) for
the per-source licence and filtering rules.

## Source code

GPLv3, full source published with the project submission.
