#!/usr/bin/env python3
"""Checks the generated map resources against DESIGN 3.2/3.3. Exit code 1 on failure.

    python tools/mapgen/verify.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mapgen  # noqa: E402

errors = []


def check(cond, msg):
    if not cond:
        errors.append(msg)


with open(os.path.join(mapgen.OUT, "index.json"), encoding="utf-8") as f:
    idx = json.load(f)

check(idx["tileSize"] == 256, "tileSize")
check(idx["levels"] == [2, -1], "levels")
layers = {l["id"]: l for l in idx["layers"]}

# every listed tile exists, every shipped file is listed, every PNG is <= 256x256
for z in map(str, idx["levels"]):
    listed = set(idx["tiles"][z])
    files = {fn[:-4] for fn in os.listdir(os.path.join(mapgen.OUT, z)) if fn.endswith(".png")}
    check(listed == files, "level %s: listed %d vs files %d" % (z, len(listed), len(files)))
    for key in idx["solid"].get(z, {}):
        check(key not in files, "solid tile %s/%s is also shipped" % (z, key))
    for fn in files:
        with Image.open(os.path.join(mapgen.OUT, z, fn + ".png")) as im:
            check(im.size == (256, 256), "%s/%s size %s" % (z, fn, im.size))
            check(im.mode in ("P", "RGB"), "%s/%s mode %s" % (z, fn, im.mode))
            if im.mode == "P":
                a = np.asarray(im.convert("RGB"))
            else:
                a = np.asarray(im)
            check(not np.all(a == a[0, 0]), "%s/%s is a single colour" % (z, fn))

# every ring is inside its layer's bounds, and the bounds hold at least one shipped z=2 tile
for r in mapgen.load_rings():
    b = layers[r["layer"]]["bounds"]
    check(b[0] <= r["x"] < b[2] and b[1] <= r["y"] < b[3], "ring %s outside %s" % (r, r["layer"]))
    check("%d_%d" % (r["x"] // 64, r["y"] // 64) in idx["tiles"]["2"], "no z=2 tile under ring %s" % r)

# labels / icons inside some layer
for l in idx["labels"]:
    b = layers[l["layer"]]["bounds"]
    check(b[0] <= l["x"] < b[2] and b[1] <= l["y"] < b[3], "label outside its layer: %s" % l)
    check(l["s"] in (0, 1, 2), "label size %s" % l)
for i in idx["icons"]:
    check(any(L["bounds"][0] <= i["x"] < L["bounds"][2] and L["bounds"][1] <= i["y"] < L["bounds"][3] for L in idx["layers"]),
          "icon outside every layer: %s" % i)

# z=-1 is the 8x8 area average of z=2: pixel (px,py) of tile (tx,ty) shows game tiles
# x = tx*512 + 2px .. +1, y = (ty+1)*512 - 1 - 2py .. -1. Compare at Lumbridge castle.
x, y = 3208, 3216
tx, ty = x // 512, y // 512
px, py = (x - tx * 512) // 2, ((ty + 1) * 512 - 1 - y) // 2
zm1 = np.asarray(Image.open(os.path.join(mapgen.OUT, "-1", "%d_%d.png" % (tx, ty))).convert("RGB")).astype(float)
z2 = np.asarray(Image.open(os.path.join(mapgen.OUT, "2", "%d_%d.png" % (x // 64, y // 64))).convert("RGB")).astype(float)
qx, qy = (x - (x // 64) * 64) * 4, ((y // 64 + 1) * 64 - 1 - y) * 4
blk = z2[qy - qy % 8:qy - qy % 8 + 8, qx - qx % 8:qx - qx % 8 + 8].reshape(-1, 3).mean(axis=0)
check(np.max(np.abs(zm1[py, px] - blk)) < 24, "z=-1 vs z=2 mismatch at Lumbridge: %s vs %s" % (zm1[py, px], blk))

ring_bytes = os.path.getsize(mapgen.RINGS_JSON) if os.path.exists(mapgen.RINGS_JSON) else 0
total = sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(mapgen.OUT) for f in fs) + ring_bytes
print("map + rings.json: %d bytes (%.3f MiB)%s" % (total, total / 1048576, "" if ring_bytes else " (rings.json missing)"))
check(total <= mapgen.BUDGET, "over budget")

if errors:
    print("FAILED:")
    for e in errors[:50]:
        print("  " + e)
    sys.exit(1)
print("OK: %d z=2 tiles, %d z=-1 tiles, %d layers, %d labels, %d icons"
      % (len(idx["tiles"]["2"]), len(idx["tiles"]["-1"]), len(idx["layers"]), len(idx["labels"]), len(idx["icons"])))
