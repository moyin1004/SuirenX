import concurrent.futures
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from unittest.mock import patch

import build_info
from package import reserve_local, verify_android_identity


class ApkIdentityTests(unittest.TestCase):
    def test_debug_identity_uses_raw_resource(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "debug.apk"
            with zipfile.ZipFile(apk, "w") as bundle:
                bundle.writestr("res/raw/suirenx_build.json", '{"build_number":11}')
            verify_android_identity(apk, {"build_number": 11})

    def test_optimized_release_resolves_resource_table_and_rejects_decoy(self):
        resources = '''
    resource 0x7f0d0000 io.suirenx.app:raw/suirenx_build: t=0x03
      (string8) "res/kv.json"
    resource 0x7f0e0000 io.suirenx.app:raw/other: t=0x03
      (string8) "res/other.json"
'''
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "release.apk"
            with zipfile.ZipFile(apk, "w") as bundle:
                bundle.writestr("res/kv.json", '{"build_number":1000502}')
                bundle.writestr("res/other.json", '{"build_number":1000501}')
            with patch("package.subprocess.check_output", return_value=resources):
                verify_android_identity(apk, {"build_number": 1000502}, aapt="aapt")
                with self.assertRaisesRegex(ValueError, "differs"):
                    verify_android_identity(apk, {"build_number": 1000501}, aapt="aapt")

    def test_optimized_release_rejects_missing_resource_mapping(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "release.apk"
            with zipfile.ZipFile(apk, "w") as bundle:
                bundle.writestr("res/unrelated.json", '{"build_number":11}')
            with patch("package.subprocess.check_output", return_value=""):
                with self.assertRaisesRegex(ValueError, "missing or ambiguous"):
                    verify_android_identity(apk, {"build_number": 11}, aapt="aapt")


class BuildInfoTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.run_git("init", "-q")
        self.run_git("config", "user.name", "Build Test")
        self.run_git("config", "user.email", "build@example.test")
        (self.root / "version.xcconfig").write_text(
            "MARKETING_VERSION = 0.1.3\nSUIRENX_BUILD_NUMBER = 4\nCURRENT_PROJECT_VERSION = 1.0.4\n")
        (self.root / ".gitignore").write_text("build/\n.suirenx-build/\n")
        (self.root / "code.txt").write_text("original\n")
        self.run_git("add", ".")
        self.run_git("commit", "-qm", "baseline")
        self.run_git("tag", "v0.1.3")

    def run_git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args])

    def test_staged_unstaged_untracked_and_ignored_source(self):
        clean = build_info.metadata(root=self.root)
        self.assertFalse(clean["dirty"])
        (self.root / "build").mkdir()
        (self.root / "build/output").write_text("ignored")
        self.assertEqual(clean["source_fingerprint"], build_info.metadata(root=self.root)["source_fingerprint"])
        (self.root / "code.txt").write_text("edited\n")
        changed = build_info.metadata(root=self.root)
        self.assertTrue(changed["dirty"])
        self.assertNotEqual(clean["source_fingerprint"], changed["source_fingerprint"])
        self.run_git("add", "code.txt")
        self.assertEqual(changed["source_fingerprint"], build_info.metadata(root=self.root)["source_fingerprint"])
        (self.root / "new.txt").write_text("untracked")
        untracked = build_info.metadata(root=self.root)
        self.assertNotEqual(changed["source_fingerprint"], untracked["source_fingerprint"])
        (self.root / "new.txt").write_text("untracked changed")
        self.assertNotEqual(untracked["source_fingerprint"], build_info.metadata(root=self.root)["source_fingerprint"])

    def test_release_rejects_dirty_mismatched_tag_and_wrong_commit(self):
        valid = dict(number=build_info.CI_BASE + 1, channel="release", root=self.root, tag="v0.1.3")
        self.assertEqual(build_info.metadata(**valid)["channel"], "release")
        with self.assertRaisesRegex(ValueError, "match"):
            build_info.metadata(**{**valid, "tag": "v0.1.2"})
        with self.assertRaisesRegex(ValueError, "CI"):
            build_info.metadata(**{**valid, "number": 4})
        (self.root / "code.txt").write_text("dirty")
        with self.assertRaisesRegex(ValueError, "clean"):
            build_info.metadata(**valid)
        self.run_git("add", ".")
        self.run_git("commit", "-qm", "next")
        with self.assertRaisesRegex(ValueError, "HEAD"):
            build_info.metadata(**valid)

    def test_frozen_identity_detects_changes(self):
        data = build_info.metadata(root=self.root)
        frozen = self.root / "build/suirenx-build.json"
        frozen.parent.mkdir()
        frozen.write_text(json.dumps(data))
        with patch.dict(os.environ, {"SUIRENX_BUILD_INFO": str(frozen)}):
            self.assertEqual(build_info.load_current(self.root), data)
            (self.root / "code.txt").write_text("changed during packaging")
            with self.assertRaisesRegex(ValueError, "Source changed"):
                build_info.load_current(self.root)

    def test_local_reservations_are_unique_and_increase_under_concurrency(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            numbers = list(pool.map(lambda _: reserve_local(self.root), range(24)))
        self.assertEqual(sorted(numbers), list(range(4, 28)))
        self.assertEqual(reserve_local(self.root), 28)
        self.assertEqual(build_info.metadata(root=self.root)["build_number"], 28)
        self.assertEqual(build_info.metadata(root=self.root)["ios_build"], "1.0.28")
        (self.root / ".suirenx-build/package-counter").write_text(str(build_info.CI_BASE - 1))
        with self.assertRaisesRegex(ValueError, "exhausted"):
            reserve_local(self.root)

    def test_cleaning_build_output_does_not_reset_package_numbers(self):
        self.assertEqual(reserve_local(self.root), 4)
        (self.root / "build").mkdir()
        (self.root / "build/output.apk").write_text("test output")
        shutil.rmtree(self.root / "build")
        self.assertEqual(reserve_local(self.root), 5)
        self.assertEqual(build_info.metadata(root=self.root)["build_number"], 5)
        self.assertFalse(build_info.metadata(root=self.root)["dirty"])

    def test_ci_retry_next_run_and_ios_mapping_preserve_order(self):
        self.assertGreater(build_info.ci_number(1, 1), build_info.CI_BASE - 1)
        self.assertLess(build_info.ci_number(23, 99), build_info.ci_number(24, 1))
        self.assertLess(build_info.ci_number(23, 1), build_info.ci_number(23, 2))
        previous = (0, 0, 0)
        for number in [4, 99, 100, 9999, 10000, build_info.CI_BASE + 1, build_info.MAX_BUILD]:
            parts = tuple(map(int, build_info.ios_build(number).split(".")))
            self.assertGreater(parts, previous)
            self.assertLessEqual(parts[0], 9999)
            self.assertTrue(all(0 <= value <= 99 for value in parts[1:]))
            previous = parts
        for run, attempt in [(0, 1), (1, 0), (1, 100), (999999999, 1)]:
            with self.assertRaises(ValueError):
                build_info.ci_number(run, attempt)


if __name__ == "__main__":
    unittest.main()
