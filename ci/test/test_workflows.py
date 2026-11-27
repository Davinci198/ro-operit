from __future__ import annotations

import os
import re
import unittest
from collections import Counter
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_DIR = REPO_ROOT / ".github" / "workflows"
NAME_LINE_RE = re.compile(r"^name:[ \t]*(.+?)[ \t]*$")
WORKFLOW_REFERENCE_RE = re.compile(r"\.github/workflows/[A-Za-z0-9._-]+\.ya?ml")
STEP_NAME_RE = re.compile(r"^(?P<indent>[ \t]*)-[ \t]+name:[ \t]*(?P<name>.+?)[ \t]*$")
APP_TASK_RE = re.compile(r":app:([A-Za-z0-9_]+)")
FLAVOUR_RE = re.compile(r"Standard|Clone")
UNIT_TEST_TASK_RE = re.compile(r"test(?:Standard|Clone)DebugUnitTest")
# A Gradle invocation is either a `./gradlew` command line or one of the bash arrays the JVM lane
# builds its task list in; both carry the literal `:app:` task names.
TASK_ARRAY_RE = re.compile(r"^[ \t]*tasks\+?=\((?P<tasks>.*)\)[ \t]*$")
ANDROID_COMPILE_LANES = ("Run Android JVM checks", "Run full Android checks")
# `providers.gradleProperty("key").get()` throws when the key is absent, so a workflow that
# replaces gradle.properties has to write every such key back. `>` replaces the file, `>>` appends
# to it and leaves the checked-out keys in place.
GRADLE_PROPERTY_REQUIRE_RE = re.compile(r'gradleProperty\(\s*"(?P<key>[^"]+)"\s*\)\s*\.get\(\)')
GRADLE_PROPERTIES_REDIRECT_RE = re.compile(r"(?P<redirect>>{1,2})\s*gradle\.properties\b")
# A key counts as written only when a line assigns it (heredoc body or `echo "key=..."`); a comment
# that merely mentions the key would otherwise satisfy the guard without writing anything.
WRITTEN_PROPERTY_RE = re.compile(
    r'^[ \t]*(?:echo[ \t]+["\']?)?(?P<key>[A-Za-z0-9._-]+)[ \t]*=', re.MULTILINE
)
SKIP_DIRECTORY_NAMES = frozenset({".git", ".gradle", "build", "node_modules"})


def workflow_files() -> list[Path]:
    return sorted(
        path
        for path in WORKFLOW_DIR.iterdir()
        if path.is_file() and path.suffix in {".yml", ".yaml"}
    )


def workflow_name(path: Path) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        match = NAME_LINE_RE.match(line)
        if match:
            return match.group(1).strip().strip("'\"")
    raise AssertionError(f"workflow declares no top-level name: {path.name}")


def workflow_references() -> list[str]:
    references: list[str] = []
    for root in (REPO_ROOT / ".github", REPO_ROOT / "ci"):
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.suffix not in {".py", ".yml", ".yaml"}:
                continue
            text = path.read_text(encoding="utf-8")
            references.extend(WORKFLOW_REFERENCE_RE.findall(text))
    return references


def step_blocks(text: str) -> dict[str, str]:
    """Raw text of every workflow step, keyed by the value of its `- name:` line."""
    lines = text.splitlines()
    starts: list[tuple[int, int, str]] = []
    for index, line in enumerate(lines):
        match = STEP_NAME_RE.match(line)
        if match:
            starts.append((index, len(match.group("indent")), match.group("name")))

    blocks: dict[str, str] = {}
    for position, (index, indent, name) in enumerate(starts):
        end = len(lines)
        for next_index, next_indent, _ in starts[position + 1 :]:
            if next_indent <= indent:
                end = next_index
                break
        blocks[name] = "\n".join(lines[index:end])
    return blocks


def task_invocations(block: str) -> list[list[str]]:
    """`:app:` task names of every Gradle invocation in a step, continuation lines joined."""
    invocations: list[list[str]] = []
    lines = block.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index]
        array = TASK_ARRAY_RE.match(line)
        if array:
            invocations.append(APP_TASK_RE.findall(array.group("tasks")))
            index += 1
            continue
        if "./gradlew" in line:
            command = line.rstrip()
            while command.endswith("\\") and index + 1 < len(lines):
                index += 1
                command = f"{command[:-1]} {lines[index].strip()}"
            invocations.append(APP_TASK_RE.findall(command))
        index += 1
    return invocations


def build_scripts() -> list[Path]:
    """Every Gradle build script in the repository, skipping dependency and output trees."""
    scripts: list[Path] = []
    for root, directories, files in os.walk(REPO_ROOT):
        directories[:] = [name for name in directories if name not in SKIP_DIRECTORY_NAMES]
        scripts.extend(Path(root) / name for name in files if name.endswith(".gradle.kts"))
    return sorted(scripts)


def required_gradle_properties() -> set[str]:
    """Gradle property keys read with `.get()`: their absence fails configuration outright."""
    keys: set[str] = set()
    for path in build_scripts():
        keys.update(GRADLE_PROPERTY_REQUIRE_RE.findall(path.read_text(encoding="utf-8")))
    return keys


def gradle_properties_redirects(block: str) -> list[str]:
    """Redirect operators aimed at gradle.properties in one step: `>` replaces, `>>` appends."""
    return [match.group("redirect") for match in GRADLE_PROPERTIES_REDIRECT_RE.finditer(block)]


class WorkflowLayoutTest(unittest.TestCase):
    def test_workflow_names_are_unique(self) -> None:
        # GitHub lists workflows by name, so duplicates make `gh workflow run <name>` ambiguous.
        names = Counter(workflow_name(path) for path in workflow_files())
        duplicates = sorted(name for name, count in names.items() if count > 1)

        self.assertEqual([], duplicates)

    def test_no_two_workflows_share_a_body(self) -> None:
        bodies: dict[str, list[str]] = {}
        for path in workflow_files():
            bodies.setdefault(path.read_text(encoding="utf-8"), []).append(path.name)
        duplicates = sorted(names for names in bodies.values() if len(names) > 1)

        self.assertEqual([], duplicates)

    def test_workflow_references_point_to_existing_files(self) -> None:
        existing = {path.name for path in workflow_files()}
        missing = sorted(
            {
                reference
                for reference in workflow_references()
                if Path(reference).name not in existing
            }
        )

        self.assertEqual([], missing)


class AndroidGateCoverageTest(unittest.TestCase):
    """The PR gate has to run the JVM unit tests, not only compile the app.

    `ci/script/pr_check.py` sets `android_jvm = android_jvm_change and not android_full`, so a
    candidate that touches a build input skips the JVM lane entirely and lands in `Run full Android
    checks`. That lane used to run `:app:compileStandardDebugKotlin` alone: the 400+ tests in
    `app/src/test` were compiled and executed by no lane for exactly those PRs. The checks below read
    the workflow as text because no YAML parser is available to `ci/test`.
    """

    def setUp(self) -> None:
        self.workflow = (WORKFLOW_DIR / "pr-check.yml").read_text(encoding="utf-8")
        self.blocks = step_blocks(self.workflow)

    def gradle_steps(self) -> list[tuple[str, str]]:
        return [
            (name, block)
            for name, block in self.blocks.items()
            if "./gradlew" in block and APP_TASK_RE.search(block)
        ]

    def test_every_lane_that_compiles_the_app_also_runs_its_unit_tests(self) -> None:
        # Packaging workflows (`release-clone.yml`, `android-build-optimized.yml`) assemble a
        # flavour without a gate, so the invariant is scoped to the PR workflow.
        checked: list[str] = []
        for name, block in self.gradle_steps():
            tasks = APP_TASK_RE.findall(block)
            compiled = {
                flavour.group(0)
                for task in tasks
                if task.startswith("compile")
                and task.endswith("DebugKotlin")
                and (flavour := FLAVOUR_RE.search(task))
            }
            if not compiled:
                continue
            checked.append(name)
            tested = {
                flavour.group(0)
                for task in tasks
                if UNIT_TEST_TASK_RE.fullmatch(task)
                and (flavour := FLAVOUR_RE.search(task))
            }
            self.assertEqual(
                compiled,
                tested,
                f"step {name!r} compiles {sorted(compiled)} but runs unit tests for "
                f"{sorted(tested) or 'nothing'}",
            )

        self.assertEqual(sorted(ANDROID_COMPILE_LANES[1:]), sorted(checked))

    def test_no_gradle_invocation_mixes_product_flavors(self) -> None:
        # Both lanes carry the same warning: one invocation must not build Standard and Clone
        # together (runner heap, PR #9). Task arrays count as invocations too.
        mixed: list[tuple[str, list[str]]] = []
        for name, block in self.gradle_steps():
            for tasks in task_invocations(block):
                flavours = {
                    flavour.group(0) for task in tasks if (flavour := FLAVOUR_RE.search(task))
                }
                if len(flavours) > 1:
                    mixed.append((name, tasks))

        self.assertEqual([], mixed)

    def test_the_jvm_lane_keeps_running_the_unit_tests(self) -> None:
        block = self.blocks[ANDROID_COMPILE_LANES[0]]

        self.assertTrue(UNIT_TEST_TASK_RE.search(block))
        # The flavor-qualified name is required: the unqualified `testDebugUnitTest` is ambiguous
        # with two product flavors and fails task selection.
        self.assertNotRegex(block, re.compile(r":app:test(?:Debug)?UnitTest\b"))


class GradlePropertiesRewriteTest(unittest.TestCase):
    """A workflow that replaces `gradle.properties` has to write back every required key.

    `release-clone.yml` overwrote the file with a heredoc of CI memory settings. Six build scripts
    read `operit.ndkVersion` through `providers.gradleProperty(...).get()`, which throws when the key
    is missing, so the signed release build died during Gradle configuration instead of producing an
    APK - and it died only for releases, never for a PR, because `pr-check.yml` leaves the file alone.
    """

    def test_required_keys_survive_a_rewrite(self) -> None:
        required = required_gradle_properties()
        self.assertTrue(required, "expected a build script reading a Gradle property with .get()")

        rewrites = 0
        for path in workflow_files():
            for name, block in step_blocks(path.read_text(encoding="utf-8")).items():
                if ">" not in gradle_properties_redirects(block):
                    continue
                rewrites += 1
                written = set(WRITTEN_PROPERTY_RE.findall(block))
                missing = sorted(required - written)
                self.assertEqual(
                    [],
                    missing,
                    f"{path.name} step {name!r} rewrites gradle.properties and drops {missing}",
                )

        self.assertGreaterEqual(rewrites, 1, "no workflow rewrites gradle.properties; guard is moot")

    def test_every_writer_of_gradle_properties_is_classified(self) -> None:
        # An append leaves the checked-out keys in place, a rewrite does not. Pinning which workflow
        # does which keeps a new writer from slipping in without the guard above seeing it.
        writers: dict[str, str] = {}
        for path in workflow_files():
            redirects = [
                redirect
                for _, block in step_blocks(path.read_text(encoding="utf-8")).items()
                for redirect in gradle_properties_redirects(block)
            ]
            if not redirects:
                continue
            writers[path.name] = "rewrite" if ">" in redirects else "append"

        self.assertEqual(
            {
                "android-build-optimized.yml": "append",
                "release-clone.yml": "rewrite",
            },
            writers,
        )

    def test_the_ndk_version_is_taken_from_the_workflow_environment(self) -> None:
        # One value feeds both sdkmanager and gradle.properties; a literal in either place would let
        # the installed toolchain and the requested `ndkVersion` drift apart.
        text = (WORKFLOW_DIR / "release-clone.yml").read_text(encoding="utf-8")
        written = [line.strip() for line in text.splitlines() if "operit.ndkVersion=" in line]
        installed = [line.strip() for line in text.splitlines() if "sdkmanager --install" in line]

        self.assertEqual(1, len(written), written)
        self.assertIn("ANDROID_NDK_VERSION", written[0])
        self.assertTrue(installed)
        self.assertTrue(all("ANDROID_NDK_VERSION" in line for line in installed), installed)


if __name__ == "__main__":
    unittest.main()
