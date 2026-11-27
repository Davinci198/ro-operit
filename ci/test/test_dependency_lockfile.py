"""Consistency checks for the pnpm workspace manifest and its lockfile.

These run without pnpm and without PyYAML (CI installs neither): `pnpm-lock.yaml` had drifted
before, where a snapshot listed `zod: 3.25.76` while no `zod@3.25.76` entry existed in
`packages:`/`snapshots:`, and `pnpm-workspace.yaml` allowed the vulnerable 3.x line even though
`SECURITY.md` documents 4.6.7 as the pinned version. `pnpm install --frozen-lockfile` (used by
`.github/workflows/pr-check.yml`) rejects a lockfile in that state, so the drift breaks CI for an
unrelated change instead of being caught where it was introduced.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import unittest
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
LOCKFILE_PATH = REPO_ROOT / "pnpm-lock.yaml"
WORKSPACE_PATH = REPO_ROOT / "pnpm-workspace.yaml"
DEPENDENCY_BLOCKS = ("dependencies", "optionalDependencies")
OFFICIAL_NPM_REGISTRY_HOST = "registry.npmjs.org"


def tracked_npm_lockfiles() -> list[Path]:
    """Every `package-lock.json` Git tracks, wherever it lives in the tree."""
    result = subprocess.run(
        ["git", "ls-files", "-z", "*package-lock.json"],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
    )
    return [REPO_ROOT / os.fsdecode(raw) for raw in result.stdout.split(b"\0") if raw]


def resolved_hosts(lockfile: Path) -> Counter[str]:
    data = json.loads(lockfile.read_text(encoding="utf-8"))
    hosts: Counter[str] = Counter()
    for entry in (data.get("packages") or {}).values():
        resolved = entry.get("resolved")
        if resolved:
            hosts[resolved.split("/")[2]] += 1
    return hosts


@dataclass
class Lockfile:
    """The parts of a pnpm v9 lockfile these tests need.

    `snapshots` maps a snapshot key (`name@version`, plus any `(peer@version)` suffix) to the
    `name@version` references it depends on; `importer_versions` holds the same reference shape for
    the direct dependencies declared under `importers:`.
    """

    overrides: dict[str, str] = field(default_factory=dict)
    packages: set[str] = field(default_factory=set)
    snapshots: dict[str, list[str]] = field(default_factory=dict)
    importer_versions: list[str] = field(default_factory=list)


def unquote(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
        return value[1:-1]
    return value


def reference(name: str, version: str) -> str:
    return f"{unquote(name)}@{unquote(version)}"


def base_key(ref: str) -> str:
    """`express@5.2.1(supports-color@7.2.0)` -> `express@5.2.1`."""
    return ref.split("(", 1)[0]


def parse_lockfile(text: str) -> Lockfile:
    """Parse the lockfile by indentation.

    Debug intent: only the shapes pnpm writes for v9 lockfiles are understood — column-0 section
    headers, two-space keys, four-space blocks with six-space `name: version` entries. Anything else
    is ignored rather than guessed at, so an unexpected layout surfaces as a missing reference
    instead of being silently accepted.
    """
    lockfile = Lockfile()
    section: str | None = None
    current_key: str | None = None
    importer_dependency: str | None = None
    in_dependency_block = False

    for raw_line in text.splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        line = raw_line.strip()

        if indent == 0:
            section = line[:-1] if line.endswith(":") else None
            current_key = None
            importer_dependency = None
            in_dependency_block = False
            continue

        if section is None:
            continue

        if indent == 2:
            current_key = None
            importer_dependency = None
            in_dependency_block = False
            if section == "overrides" and ":" in line:
                name, _, value = line.partition(":")
                lockfile.overrides[unquote(name)] = unquote(value)
            else:
                # pnpm writes a leaf entry with no dependencies as `zod@4.6.7: {}`; it is still a key.
                current_key = unquote(line[:-1] if line.endswith(":") else line.removesuffix(": {}"))
                if section == "packages":
                    lockfile.packages.add(current_key)
                elif section == "snapshots":
                    lockfile.snapshots.setdefault(current_key, [])
            continue

        if indent == 4:
            in_dependency_block = line.endswith(":") and line[:-1] in DEPENDENCY_BLOCKS
            continue

        if indent == 6:
            if section == "snapshots" and in_dependency_block and current_key is not None and ":" in line:
                name, _, value = line.partition(":")
                value = unquote(value)
                if value:
                    lockfile.snapshots[current_key].append(reference(name, value))
            elif section == "importers" and line.endswith(":"):
                # importers -> <package dir> -> dependencies -> <dependency name>
                importer_dependency = unquote(line[:-1])
            continue

        if indent == 8 and section == "importers" and importer_dependency is not None:
            name, _, value = line.partition(":")
            if unquote(name) == "version" and unquote(value):
                lockfile.importer_versions.append(reference(importer_dependency, value))
                importer_dependency = None

    return lockfile


def parse_workspace_overrides(text: str) -> dict[str, str]:
    overrides: dict[str, str] = {}
    inside = False
    for raw_line in text.splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        line = raw_line.strip()
        if indent == 0:
            inside = line == "overrides:"
            continue
        if inside and indent == 2 and ":" in line:
            name, _, value = line.partition(":")
            overrides[unquote(name)] = unquote(value)
    return overrides


def major_versions_allowed(range_text: str) -> set[int]:
    """Collect the major versions a simple npm range expression admits."""
    majors: set[int] = set()
    for alternative in range_text.split("||"):
        match = re.search(r"\d+", alternative)
        if match:
            majors.add(int(match.group(0)))
    return majors


class LockfileConsistencyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.lockfile = parse_lockfile(LOCKFILE_PATH.read_text(encoding="utf-8"))
        cls.workspace_overrides = parse_workspace_overrides(WORKSPACE_PATH.read_text(encoding="utf-8"))

    def test_lockfile_parsed_the_expected_sections(self) -> None:
        # If the parser ever stops following pnpm's layout, the assertions below would "pass" on empty
        # collections instead of failing.
        self.assertGreater(len(self.lockfile.packages), 100)
        self.assertGreater(len(self.lockfile.snapshots), 50)
        self.assertTrue("zod@4.6.7" in self.lockfile.packages, "zod@4.6.7 missing from packages")
        self.assertTrue("zod@4.6.7" in self.lockfile.snapshots, "zod@4.6.7 missing from snapshots")
        self.assertTrue(
            "@modelcontextprotocol/sdk@1.31.0" in self.lockfile.packages,
            "@modelcontextprotocol/sdk@1.31.0 missing from packages",
        )
        self.assertTrue(self.lockfile.overrides)
        self.assertTrue(self.lockfile.importer_versions)

    def test_every_snapshot_dependency_resolves_to_a_package(self) -> None:
        unresolved = sorted(
            {
                dependency
                for dependencies in self.lockfile.snapshots.values()
                for dependency in dependencies
                if base_key(dependency) not in self.lockfile.packages
            }
        )
        self.assertEqual([], unresolved, "snapshot dependencies without a packages entry")

    def test_every_peer_specialized_snapshot_is_declared(self) -> None:
        missing = sorted(
            {
                dependency
                for dependencies in self.lockfile.snapshots.values()
                for dependency in dependencies
                if "(" in dependency and dependency not in self.lockfile.snapshots
            }
        )
        self.assertEqual([], missing, "peer-specialized snapshots without a snapshots entry")

    def test_importer_versions_resolve_to_a_package(self) -> None:
        unresolved = sorted(
            version for version in self.lockfile.importer_versions if base_key(version) not in self.lockfile.packages
        )
        self.assertEqual([], unresolved, "importer versions without a packages entry")

    def test_lockfile_overrides_match_the_workspace_manifest(self) -> None:
        self.assertEqual(self.workspace_overrides, self.lockfile.overrides)


class NpmRegistryTest(unittest.TestCase):
    """Tarballs must come from the official registry.

    `web-chat/package-lock.json` had 175 of its 415 `resolved` URLs pointing at
    `registry.npmmirror.com` and `repo.huaweicloud.com`, and `examples/dsh_toolpkg` was pinned
    entirely to the mirror. `npm ci` then only works while those third-party mirrors are reachable
    from your network, which is how a frontend install ends up failing for an unrelated reason.
    Rewriting the host is safe: the `integrity` hash still decides what may be unpacked.
    """

    def test_lockfiles_exist_and_only_use_the_official_registry(self) -> None:
        lockfiles = tracked_npm_lockfiles()
        self.assertIn(REPO_ROOT / "web-chat" / "package-lock.json", lockfiles)

        offenders = {
            str(path.relative_to(REPO_ROOT)): hosts
            for path in lockfiles
            for hosts in [resolved_hosts(path)]
            if set(hosts) - {OFFICIAL_NPM_REGISTRY_HOST}
        }
        self.assertEqual({}, offenders)


class SupplyChainPinTest(unittest.TestCase):
    """`SECURITY.md` records the pinning decisions; keep the manifests honest about them."""

    def setUp(self) -> None:
        self.lockfile = parse_lockfile(LOCKFILE_PATH.read_text(encoding="utf-8"))
        self.overrides = parse_workspace_overrides(WORKSPACE_PATH.read_text(encoding="utf-8"))

    def test_zod_override_does_not_admit_the_vulnerable_3x_line(self) -> None:
        self.assertEqual({4}, major_versions_allowed(self.overrides["zod"]))

    def test_resolved_zod_satisfies_the_pin(self) -> None:
        resolved = sorted(
            base_key(dependency).partition("@")[2]
            for dependencies in self.lockfile.snapshots.values()
            for dependency in dependencies
            if base_key(dependency).startswith("zod@")
        )
        self.assertTrue(resolved, "the lockfile should still resolve zod")
        for version in resolved:
            self.assertEqual("4", version.split(".", 1)[0], f"zod {version} predates the 4.x pin")

    def test_fast_uri_and_picomatch_overrides_still_hold(self) -> None:
        self.assertEqual("^3.1.6", self.overrides["fast-uri"])
        self.assertEqual("^2.3.2", self.overrides["picomatch@<4.0.0"])


if __name__ == "__main__":
    unittest.main()
