from __future__ import annotations

import re
import unittest
from collections import Counter
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_DIR = REPO_ROOT / ".github" / "workflows"
NAME_LINE_RE = re.compile(r"^name:[ \t]*(.+?)[ \t]*$")
WORKFLOW_REFERENCE_RE = re.compile(r"\.github/workflows/[A-Za-z0-9._-]+\.ya?ml")


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


if __name__ == "__main__":
    unittest.main()
