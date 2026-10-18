#!/usr/bin/env python3
"""Package a local development build or a tagged release in the release CI."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys
import zipfile

from build_info import CI_BASE, ROOT, ci_number, ios_build, load_current, metadata


def reserve_local(root=ROOT):
    # Checkout-local, ignored state survives branch switches; never edits Git refs.
    state = root / ".suirenx-build"
    state.mkdir(exist_ok=True)
    with (state / "package-counter").open("a+") as counter:
        fcntl.flock(counter, fcntl.LOCK_EX)
        counter.seek(0)
        previous = counter.read().strip()
        number = max(4, int(previous) + 1 if previous else 4)
        if number >= CI_BASE:
            raise ValueError("Local development number range exhausted")
        counter.seek(0)
        counter.truncate()
        counter.write(str(number))
        counter.flush()
        os.fsync(counter.fileno())
        config = state / "development.xcconfig"
        pending = config.with_suffix(".tmp")
        pending.write_text(f"SUIRENX_BUILD_NUMBER = {number}\nCURRENT_PROJECT_VERSION = {ios_build(number)}\n")
        pending.replace(config)
    return number


def package(args):
    if args.release:
        if os.environ.get("GITHUB_ACTIONS") != "true":
            raise ValueError("Use the release CI for distributable release numbers")
        number = ci_number(int(os.environ["GITHUB_RUN_NUMBER"]), int(os.environ["GITHUB_RUN_ATTEMPT"]))
    else:
        number = reserve_local()
    data = metadata(number, "release" if args.release else "development", tag=args.tag)
    record = ROOT / "build" / "packages" / f"{args.platform}-{number}"
    record.mkdir(parents=True, exist_ok=False)
    identity = record / "suirenx-build.json"
    identity.write_text(json.dumps(data, indent=2) + "\n")
    env = dict(os.environ, SUIRENX_BUILD_INFO=str(identity))
    if args.platform == "android":
        variant = "release" if args.release else "debug"
        command = [str(ROOT / "gradlew"), f":apps:android:app:assemble{variant.title()}", "--no-daemon"]
    else:
        command = ["xcodebuild", "-project", "apps/ios/SuirenX.xcodeproj", "-scheme", "SuirenX",
                   "-configuration", "Release" if args.release else "Debug", "-destination", args.destination,
                   "-derivedDataPath", str(ROOT / "apps/ios/.build/packaging"),
                   f"CONFIGURATION_BUILD_DIR={record / 'products'}",
                   f"CURRENT_PROJECT_VERSION={data['ios_build']}",
                   f"SUIRENX_BUILD_INFO={identity}", "CODE_SIGNING_ALLOWED=NO", "build"]
    print(f"Packaging {data['version']} ({number}), {data['commit'][:12]}, dirty={data['dirty']}", flush=True)
    subprocess.run(command, cwd=ROOT, env=env, check=True)
    # Detect edits made while packaging instead of handing out a misleading record.
    os.environ["SUIRENX_BUILD_INFO"] = str(identity)
    load_current()
    if args.platform == "android":
        apks = list((ROOT / f"apps/android/app/build/outputs/apk/{variant}").glob("*.apk"))
        if not apks:
            raise ValueError("Build completed without an APK")
        for apk in apks:
            retained = record / apk.name
            shutil.copy2(apk, retained)
            with zipfile.ZipFile(retained) as bundle:
                if json.loads(bundle.read("res/raw/suirenx_build.json")) != data:
                    raise ValueError("APK identity differs from the frozen package record")
            digest = hashlib.sha256(retained.read_bytes()).hexdigest()
            retained.with_suffix(retained.suffix + ".sha256").write_text(f"{digest}  {retained.name}\n")
    else:
        app = record / "products/SuirenX.app"
        info = plistlib.loads((app / "Info.plist").read_bytes())
        if (json.loads((app / "suirenx-build.json").read_text()) != data
                or info["CFBundleShortVersionString"] != data["version"]
                or info["CFBundleVersion"] != data["ios_build"]):
            raise ValueError("iOS bundle identity differs from the frozen package record")
    print(f"Package record: {record}")
    if output := os.environ.get("GITHUB_OUTPUT"):
        with open(output, "a") as stream:
            stream.write(f"build_number={number}\nmetadata={identity.relative_to(ROOT)}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android", "ios"])
    parser.add_argument("--release", action="store_true", help="Release CI only; clean matching tag required")
    parser.add_argument("--tag")
    parser.add_argument("--destination", default="generic/platform=iOS Simulator")
    args = parser.parse_args()
    (ROOT / ".suirenx-build").mkdir(exist_ok=True)
    # Platform builds share intermediates; serialize packaging through final copy.
    with (ROOT / ".suirenx-build" / f"package-{args.platform}.lock").open("a+") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        package(args)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
