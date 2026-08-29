# Needle eval (desktop only)

Phones never run Python. This folder is the shared **tool schema** plus a
tiny dispatcher you can run on a laptop to dump phrases.

On the phone:

- **Always:** `PatternNeedle` (Kotlin / Swift). Same 5 tools, no model, no
  network, English + Serbian.
- **Android optional:** Cactus Needle 2 + Whisper tiny via
  `com.cactuscompute:cactus`. One install from Settings, then airplane-mode.
- **iPhone:** Apple on-device speech (`requiresOnDeviceRecognition`). No
  cactus-kotlin on iOS — keep it native SwiftUI.

SDL is not used. A native capture composer is enough.

```bash
python3 tools/needle/try_dispatch.py --test
python3 tools/needle/try_dispatch.py "popio kreatin"
python3 tools/needle/try_dispatch.py --cactus "spent 450 dinara lidl"
```

`--cactus` needs `pip install cactus-needle` (manylinux wheel). Skip it
unless you are evaluating the model on a desktop.
