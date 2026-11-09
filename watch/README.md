# Life on BIP Max

Zepp OS app that logs habits and short notes into Life through Gadgetbridge watch-app messages.

- App id: `3266101`
- Target: Amazfit Bip Max, `deviceSource` `11206915`
- API: minimum 3.0, target 4.0
- `app.json` `expiredTime` is `31622400` (366 days). Zeus treats this as a duration, not a calendar timestamp: `172800` is the 48-hour default.

Zeus (`@zeppos/zeus-cli` 1.9.3, the same CLI as the message test app) still writes `expiredTime: 172800` into `packageInfo` in the built package. After `zeus build`, run the stamp script so the sideload lasts a year instead of vanishing overnight.

```sh
npm install -g @zeppos/zeus-cli@1.9.3
cd watch
zeus build -t bipmax
python3 stamp_expiry.py
```

The installer file is `dist/*.zab`. Copy it to the phone and install with Gadgetbridge (Zepp test): long-press the BIP Max card, **File Installer**, choose the `.zab`. Leave Gadgetbridge Nightly installed. Life’s listener defaults to package `nodomain.freeyourgadget.gadgetbridge.zepptest`.

This target was written for the Bip Max. It is not a claim that the same package runs on every Zepp OS watch.
