#!/usr/bin/env python3
"""The hotbuild tools and the app's updater have to agree on the release repository.

`tools/hotbuild/*.py` publish patch releases, and `UpdateManager.tryFetchLatestPatchUpdate` reads
them back. The two sides carried different repository values once already - the publisher defaulted
to `Davinci198/fix-operitNightlyRelease`, which is not a repository at all and answered 404, so
every run without an explicit `--repo` failed. These tests keep the two ends of that path in sync.
"""

from __future__ import annotations

import ast
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
HOTBUILD_DIR = REPO_ROOT / "tools" / "hotbuild"
HOTBUILD_SCRIPTS = ("apply_patch.py", "build_patch.py", "nightly_auto.py")
UPDATE_MANAGER = (
    REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "ai" / "assistance" / "operit"
    / "data" / "updates" / "UpdateManager.kt"
)
EXPECTED_REPO = "Davinci198/ro-operit"


def argparse_default(source: str, option: str) -> str | None:
    """Value of `default=` in the `add_argument("<option>", ...)` call, or None when absent."""
    for node in ast.walk(ast.parse(source)):
        if not isinstance(node, ast.Call):
            continue
        callee = node.func.attr if isinstance(node.func, ast.Attribute) else None
        if callee != "add_argument":
            continue
        if not node.args or not isinstance(node.args[0], ast.Constant):
            continue
        if node.args[0].value != option:
            continue
        for keyword in node.keywords:
            if keyword.arg == "default" and isinstance(keyword.value, ast.Constant):
                return str(keyword.value.value)
    return None


class HotbuildRepoDefaultTest(unittest.TestCase):
    def test_every_hotbuild_script_defaults_to_one_repository(self) -> None:
        for name in HOTBUILD_SCRIPTS:
            with self.subTest(script=name):
                source = (HOTBUILD_DIR / name).read_text(encoding="utf-8")
                self.assertEqual(EXPECTED_REPO, argparse_default(source, "--repo"))

    def test_no_hotbuild_script_still_names_the_renamed_repository(self) -> None:
        for path in sorted(HOTBUILD_DIR.glob("*.py")):
            with self.subTest(script=path.name):
                # Report line numbers instead of the whole file: a failed assertNotIn would dump
                # thousands of characters into the CI log.
                stale = [
                    number
                    for number, line in enumerate(
                        path.read_text(encoding="utf-8").splitlines(), start=1
                    )
                    if "fix-operit" in line
                ]
                self.assertEqual([], stale, f"{path.name} still names the old repository")

    def test_the_app_reads_patches_from_the_same_repository(self) -> None:
        source = UPDATE_MANAGER.read_text(encoding="utf-8")
        owner, _, name = EXPECTED_REPO.partition("/")

        # UpdateManager hardcodes the owner and the repository it queries for patch releases. If
        # either literal changes, the hotbuild default has to change with it, otherwise published
        # patches are never seen by the app.
        self.assertIn(f'"{owner}"', source)
        self.assertIn(f'"{name}"', source)

    def test_the_expected_repository_slug_is_well_formed(self) -> None:
        owner, separator, name = EXPECTED_REPO.partition("/")

        self.assertEqual("/", separator)
        self.assertTrue(owner)
        self.assertTrue(name)
        # The old default appended a release name to the repository, producing a slug that 404s.
        self.assertNotIn(" ", EXPECTED_REPO)
        self.assertEqual(name, name.strip())


if __name__ == "__main__":
    unittest.main()
