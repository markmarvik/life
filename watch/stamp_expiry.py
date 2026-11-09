#!/usr/bin/env python3
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path

YEAR_SECONDS = 366 * 24 * 60 * 60


def stamp_tree(obj, expiry: int) -> None:
    if isinstance(obj, dict):
        if "expiredTime" in obj:
            obj["expiredTime"] = expiry
        for value in obj.values():
            stamp_tree(value, expiry)
    elif isinstance(obj, list):
        for value in obj:
            stamp_tree(value, expiry)


def patch_json(data: bytes, expiry: int) -> bytes:
    try:
        obj = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return data
    if not isinstance(obj, dict):
        return data
    before = json.dumps(obj, ensure_ascii=False, separators=(",", ":"))
    stamp_tree(obj, expiry)
    if "app" in obj or "configVersion" in obj:
        obj["expiredTime"] = expiry
    after = json.dumps(obj, ensure_ascii=False, separators=(",", ":"))
    if after == before:
        return data
    return after.encode("utf-8")


def patch_zip(blob: bytes, expiry: int, rewrite_names: bool) -> bytes:
    src = zipfile.ZipFile(io.BytesIO(blob))
    out = io.BytesIO()
    manifest = None
    replaced = {}
    with zipfile.ZipFile(out, "w") as dst:
        for info in src.infolist():
            payload = src.read(info)
            name = info.filename
            if name.endswith(".zpk") or name.endswith(".zip"):
                payload = patch_zip(payload, expiry, False)
                if rewrite_names:
                    digest = hashlib.md5(payload).hexdigest()
                    new_name = digest + ".zpk"
                    replaced[name] = (new_name, digest)
                    name = new_name
            elif name.endswith(".json"):
                if name.endswith("manifest.json"):
                    manifest = json.loads(payload.decode("utf-8"))
                    continue
                payload = patch_json(payload, expiry)
            new_info = zipfile.ZipInfo(filename=name, date_time=info.date_time)
            new_info.compress_type = info.compress_type
            new_info.external_attr = info.external_attr
            dst.writestr(new_info, payload)
        if manifest is not None:
            for zpk in manifest.get("zpks", []):
                old = zpk.get("name")
                if old in replaced:
                    zpk["name"] = replaced[old][0]
                    zpk["md5"] = replaced[old][1]
            raw = json.dumps(manifest, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            dst.writestr("manifest.json", raw)
    return out.getvalue()


def main() -> int:
    dist = Path(__file__).resolve().parent / "dist"
    zabs = sorted(dist.glob("*.zab"))
    if not zabs:
        print("No dist/*.zab. From watch/: npm install -g @zeppos/zeus-cli && zeus build -t bipmax", file=sys.stderr)
        return 1
    expiry = YEAR_SECONDS
    for zab in zabs:
        zab.write_bytes(patch_zip(zab.read_bytes(), expiry, True))
        print(f"{zab.name} expiredTime={expiry}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
