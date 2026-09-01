#!/usr/bin/env python3
"""Download and verify one explicitly approved Hugging Face model artifact set."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import shutil
import sys
import tempfile
import urllib.parse
import urllib.request


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalog", required=True, type=pathlib.Path)
    parser.add_argument("--model", required=True)
    parser.add_argument("--destination", required=True, type=pathlib.Path)
    return parser.parse_args()


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return f"sha256:{digest.hexdigest()}"


def main() -> int:
    args = parse_args()
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    matches = [model for model in catalog["models"] if model["name"] == args.model]
    if len(matches) != 1:
        raise ValueError("catalog must contain exactly one requested model")

    model = matches[0]
    if model.get("status") != "STAGED_APPROVED" or not model.get("approval"):
        raise ValueError("only a human-approved STAGED_APPROVED model may be downloaded")
    if model["approval"].get("deploymentEnvironment") != "AWS_STAGING":
        raise ValueError("model approval is not valid for AWS_STAGING")

    target_root = args.destination / model["localPath"]
    target_root.mkdir(parents=True, exist_ok=True)
    base_url = (
        "https://huggingface.co/"
        f"{urllib.parse.quote(model['modelId'], safe='/')}/resolve/{model['revision']}/"
    )

    for artifact in model["files"]:
        relative = pathlib.PurePosixPath(artifact["path"])
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError(f"unsafe artifact path: {relative}")
        destination = target_root.joinpath(*relative.parts)
        destination.parent.mkdir(parents=True, exist_ok=True)
        url = base_url + urllib.parse.quote(relative.as_posix(), safe="/") + "?download=true"

        with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as temporary:
            temporary_path = pathlib.Path(temporary.name)
            try:
                with urllib.request.urlopen(url, timeout=120) as response:
                    shutil.copyfileobj(response, temporary, length=1024 * 1024)
            except Exception:
                temporary_path.unlink(missing_ok=True)
                raise

        actual_size = temporary_path.stat().st_size
        actual_sha = sha256(temporary_path)
        if actual_size != artifact["sizeBytes"] or actual_sha != artifact["sha256"]:
            temporary_path.unlink(missing_ok=True)
            raise ValueError(
                f"artifact verification failed: {relative} size={actual_size} sha256={actual_sha}"
            )
        os.chmod(temporary_path, 0o444)
        temporary_path.replace(destination)
        print(f"verified {relative} {actual_sha}")

    print(f"model ready: {target_root} revision={model['revision']}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"model download failed: {error}", file=sys.stderr)
        raise
