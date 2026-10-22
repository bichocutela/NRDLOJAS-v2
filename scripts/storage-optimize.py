"""Lossless PNG optimization. Originals must be archived before the apply command."""
import base64
from concurrent.futures import ThreadPoolExecutor
import io
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image, UnidentifiedImageError

BASE = "https://kkayksyzksexoarpfxyj.supabase.co"
WORK = Path("storage-optimization")
TOKEN = (0, "")


def call(body):
    global TOKEN
    if time.time() - TOKEN[0] > 180:
        url = os.environ["ACTIONS_ID_TOKEN_REQUEST_URL"] + "&audience=nrd-storage-optimizer"
        request = urllib.request.Request(url, headers={"Authorization": "Bearer " + os.environ["ACTIONS_ID_TOKEN_REQUEST_TOKEN"]})
        with urllib.request.urlopen(request, timeout=30) as response:
            TOKEN = (time.time(), json.load(response)["value"])
        print("::add-mask::" + TOKEN[1], flush=True)
    request = urllib.request.Request(BASE + "/functions/v1/nrd-storage-optimizer", data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json", "x-github-oidc-token": TOKEN[1]})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def compress(data):
    try:
        return _compress(data)
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError):
        # Wrong MIME metadata and corrupt images must stay untouched.
        return None


def _compress(data):
    with Image.open(io.BytesIO(data)) as original:
        if original.width * original.height > 16_000_000:
            return None
        if original.format != "PNG" or getattr(original, "is_animated", False) or original.getexif().get(274, 1) != 1:
            return None
        # Do not reduce bit depth, dimensions, color gamut, or animated media.
        if original.mode not in ("RGB", "RGBA", "P", "L", "LA") or "gamma" in original.info or "chromaticity" in original.info:
            return None
        pixels = original.convert("RGBA")
        output = io.BytesIO()
        original.convert("RGBA").save(output, "WEBP", lossless=True, exact=True, method=6,
                                      icc_profile=original.info.get("icc_profile", b""), exif=original.info.get("exif", b""))
        result = output.getvalue()
        with Image.open(io.BytesIO(result)) as restored:
            if restored.size != original.size or restored.convert("RGBA").tobytes() != pixels.tobytes():
                raise ValueError("Lossless pixel verification failed")
            if restored.info.get("icc_profile", b"") != original.info.get("icc_profile", b""):
                raise ValueError("Color profile verification failed")
        return result if len(result) < len(data) * 0.9 else None


def prepare_one(item):
    try:
        path = item["path"]
        if Path(path).is_absolute() or ".." in Path(path).parts:
            raise ValueError("Unsafe storage path")
        url = BASE + "/storage/v1/object/public/nrdlojas-images/" + urllib.parse.quote(path, safe="/")
        with urllib.request.urlopen(url, timeout=60) as response:
            source = response.read(8 * 1024 * 1024 + 1)
        if len(source) != item["bytes"]:
            print("Skip changed source:", path, flush=True)
            return None
        optimized = compress(source)
        if optimized is None:
            print("Preserved without conversion:", path, flush=True)
            return None
        for folder, data in (("originals", source), ("optimized", optimized)):
            destination = WORK / folder / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
        print(f"Prepared {path}: {len(source)} -> {len(optimized)} bytes", flush=True)
        return {**item, "optimized_bytes": len(optimized)}
    except (urllib.error.URLError, OSError, ValueError) as error:
        print("Preserved unreadable source:", item["path"], type(error).__name__, flush=True)
        return None


def prepare():
    files = call({"operation": "manifest"})["files"]
    with ThreadPoolExecutor(max_workers=4) as pool:
        plan = [item for item in pool.map(prepare_one, files) if item is not None]
    WORK.mkdir(exist_ok=True)
    (WORK / "plan.json").write_text(json.dumps(plan, indent=2))
    print(f"Prepared {len(plan)} files; potential savings {sum(i['bytes'] - i['optimized_bytes'] for i in plan)} bytes", flush=True)


def apply():
    # GitHub Actions archives originals and plan.json successfully before this command.
    saved = 0
    failures = []
    for item in json.loads((WORK / "plan.json").read_text()):
        try:
            result = call({"operation": "replace", "path": item["path"], "etag": item["etag"],
                           "base64": base64.b64encode((WORK / "optimized" / item["path"]).read_bytes()).decode()})
            saved += result["saved"]
            print("Optimized:", item["path"], flush=True)
        except urllib.error.HTTPError as error:
            if error.code == 409:
                print("Skip changed source:", item["path"], flush=True)
                continue
            failures.append(item["path"])
            print("Failed:", item["path"], "HTTP", error.code, flush=True)
    summary = f"Lossless optimization saved {saved / 1_000_000:.1f} MB. Failed files: {len(failures)}. URLs and pixels preserved.\n"
    print(summary, flush=True)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as output:
            output.write(summary)
    if failures:
        raise RuntimeError("Some replacements failed; originals are archived")


if __name__ == "__main__":
    {"prepare": prepare, "apply": apply}[sys.argv[1]]()
