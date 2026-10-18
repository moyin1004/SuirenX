#!/usr/bin/env python3
"""Shared package identity. No network, credentials, or source-file mutations."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CI_BASE = 1_000_000
MAX_BUILD = 99_989_999  # iOS: at most 9999.99.99; also below Android's limit.


def git(*args, root=ROOT):
    return subprocess.check_output(["git", "-C", str(root), *args])


def version_settings(root=ROOT):
    values = {}
    sources = [root / "version.xcconfig", root / ".suirenx-build/development.xcconfig"]
    for source in sources:
        if not source.exists():
            continue
        for line in source.read_text().splitlines():
            if line.strip() and not line.lstrip().startswith(("//", "#")):
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+", values["MARKETING_VERSION"]):
        raise ValueError("MARKETING_VERSION must be MAJOR.MINOR.PATCH")
    if values["CURRENT_PROJECT_VERSION"] != ios_build(int(values["SUIRENX_BUILD_NUMBER"])):
        raise ValueError("IDE build baseline and CURRENT_PROJECT_VERSION disagree")
    return values


def ios_build(number):
    if not 1 <= number <= MAX_BUILD:
        raise ValueError("Build number outside supported Android/iOS range")
    return f"{number // 10000 + 1}.{number // 100 % 100}.{number % 100}"


def ci_number(run_number, run_attempt):
    if run_number < 1 or not 1 <= run_attempt <= 99:
        raise ValueError("CI needs a positive run number and attempt 1..99")
    number = CI_BASE + (run_number - 1) * 100 + run_attempt
    ios_build(number)
    return number


def source_state(root=ROOT):
    commit = git("rev-parse", "HEAD", root=root).decode().strip()
    # Includes staged, unstaged and non-ignored untracked files; ignored build output
    # cannot turn a clean source tree dirty. Never embed paths or diffs in the app.
    status = git("status", "--porcelain=v1", "--untracked-files=all", root=root)
    digest = hashlib.sha256(commit.encode())
    digest.update(git("diff", "--binary", "HEAD", "--", root=root))
    for name in sorted(git("ls-files", "--others", "--exclude-standard", "-z", root=root).split(b"\0")):
        if name:
            path = root / os.fsdecode(name)
            digest.update(name + b"\0")
            digest.update(os.readlink(path).encode() if path.is_symlink() else path.read_bytes())
    return commit, bool(status), digest.hexdigest()


def metadata(number=None, channel="development", root=ROOT, tag=None):
    settings = version_settings(root)
    version = settings["MARKETING_VERSION"]
    number = int(settings["SUIRENX_BUILD_NUMBER"]) if number is None else number
    apple_number = ios_build(number)
    commit, dirty, fingerprint = source_state(root)
    if channel == "release":
        if dirty:
            raise ValueError("Release packaging requires a clean Git worktree")
        if tag != f"v{version}":
            raise ValueError(f"Release tag must match v{version}")
        if git("rev-parse", f"refs/tags/{tag}^{{commit}}", root=root).decode().strip() != commit:
            raise ValueError("Release tag does not point to HEAD")
        if number < CI_BASE:
            raise ValueError("Release build numbers are allocated by the release CI")
    return {"schema": 1, "version": version, "build_number": number,
            "ios_build": apple_number, "channel": channel, "commit": commit,
            "tag": tag or "",
            "dirty": dirty, "source_fingerprint": fingerprint,
            "built_at": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}


def load_current(root=ROOT):
    frozen = os.environ.get("SUIRENX_BUILD_INFO")
    if not frozen:
        return metadata(root=root)
    data = json.loads(Path(frozen).read_text())
    commit, dirty, fingerprint = source_state(root)
    if (data["schema"] != 1 or data["commit"] != commit or data["dirty"] != dirty
            or data["source_fingerprint"] != fingerprint
            or data["version"] != version_settings(root)["MARKETING_VERSION"]
            or data["ios_build"] != ios_build(data["build_number"])):
        raise ValueError("Source changed since package identity was frozen; start a new package")
    if data["channel"] not in ("development", "release"):
        raise ValueError("Unknown package channel")
    if data["channel"] == "release":
        metadata(data["build_number"], "release", root=root, tag=data["tag"])
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--ios", action="store_true")
    args = parser.parse_args()
    data = load_current()
    if args.ios:
        # The bundle and its trace record must agree even for direct Xcode builds.
        if (os.environ["MARKETING_VERSION"] != data["version"]
                or os.environ["CURRENT_PROJECT_VERSION"] != data["ios_build"]):
            raise ValueError("Xcode version settings disagree with the package identity")
    result = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result)
    else:
        print(result, end="")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
