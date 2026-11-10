#!/usr/bin/env python3
import shutil
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parent
dest = Path("/tmp/life-watch-sim")
zeus = Path("/tmp/zeus-cli/package/bin/main.js")
if not zeus.is_file():
    print("Zeus CLI is missing. Install @zeppos/zeus-cli@1.9.3 first.", file=sys.stderr)
    raise SystemExit(1)
if dest.exists():
    shutil.rmtree(dest)
shutil.copytree(root, dest, ignore=shutil.ignore_patterns("dist", "node_modules"))
(dest / "app.json").write_text((root / "app.sim.json").read_text())
subprocess.check_call(["node", str(zeus), "build", "-t", "Amazfit Bip Max"], cwd=dest)
subprocess.check_call([sys.executable, "stamp_expiry.py"], cwd=dest)
built = sorted((dest / "dist").glob("*.zab"))
if not built:
    print("Simulator build produced no zab.", file=sys.stderr)
    raise SystemExit(1)
print(built[-1])
