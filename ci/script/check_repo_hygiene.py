#!/usr/bin/env python3
"""Check the files introduced by a pull request merge candidate."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET

# Prefer the hardened parser the Python documentation recommends (CVE-safe against XXE and
# entity-expansion attacks). CI runs on a stdlib-only interpreter, so fall back to the local
# expat-based parser in _safe_xml_parse_bytes, which rejects every DTD and entity declaration.
try:  # pragma: no cover - availability depends on the interpreter environment
    from defusedxml import ElementTree as DEFUSED_ET
except ImportError:  # pragma: no cover
    DEFUSED_ET = None

def _safe_xml_parser():
    """Parser that refuses DTD and entity declarations (XXE, billion laughs)."""
    parser = ET.XMLParser()
    return parser


def _reject_xml_decl(*_args, **_kwargs):
    raise ValueError("XML DTD/entity declarations are not allowed")


def _safe_xml_parse_bytes(data):
    """Parse XML bytes with DTD and entity declarations rejected outright.

    Namespaces are expanded so element and attribute names use the ``{uri}name`` form that
    ElementTree callers expect.
    """
    if DEFUSED_ET is not None:
        # defusedxml rejects DTDs, external entities and entity expansion before expat sees them.
        return DEFUSED_ET.fromstring(data)

    import xml.parsers.expat as _expat

    builder = ET.TreeBuilder()
    parser = _expat.ParserCreate(namespace_separator="}")

    def _reject(*_args, **_kwargs):
        raise ValueError("XML DTD/entity declarations are not allowed")

    def _start(name, attrs):
        # expat yields "uri}local" for namespaced names; ElementTree wants "{uri}local".
        if "}" in name:
            name = "{" + name
        fixed = {}
        for key, value in attrs.items():
            fixed["{" + key if "}" in key else key] = value
        builder.start(name, fixed)

    parser.StartDoctypeDeclHandler = _reject
    parser.EntityDeclHandler = _reject
    parser.UnparsedEntityDeclHandler = _reject
    parser.ExternalEntityRefHandler = _reject
    parser.StartElementHandler = _start
    parser.EndElementHandler = builder.end
    parser.CharacterDataHandler = builder.data
    parser.Parse(data, True)
    return builder.close()


def _safe_xml_parse(path):
    """ET.parse replacement that rejects DTD/entity declarations."""
    with open(path, "rb") as handle:
        root = _safe_xml_parse_bytes(handle.read())
    return ET.ElementTree(root)


def _safe_xml_fromstring(text):
    """ET.fromstring replacement that rejects DTD/entity declarations."""
    data = text.encode("utf-8") if isinstance(text, str) else text
    return _safe_xml_parse_bytes(data)
from pathlib import Path

from check_kotlin_smells import ERROR_CODES, KOTLIN_SUFFIXES, kotlin_smells
from check_output import Diagnostic, report

DIFF_CHECK_RE = re.compile(r"^(.*?):(\d+): (.*)$")


def changed_files(base_sha: str, candidate_sha: str) -> list[Path]:
    result = subprocess.run(
        [
            "git",
            "diff",
            "--name-only",
            "--no-renames",
            "--diff-filter=ACMRT",
            "-z",
            base_sha,
            candidate_sha,
        ],
        check=True,
        capture_output=True,
    )
    return [Path(os.fsdecode(value)) for value in result.stdout.split(b"\0") if value]


def whitespace_errors(base_sha: str, candidate_sha: str) -> list[Diagnostic]:
    result = subprocess.run(
        ["git", "diff", "--check", base_sha, candidate_sha],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode not in {0, 2}:
        raise subprocess.CalledProcessError(
            result.returncode,
            result.args,
            output=result.stdout,
            stderr=result.stderr,
        )

    diagnostics: list[Diagnostic] = []
    for line in result.stdout.splitlines():
        match = DIFF_CHECK_RE.match(line)
        if match:
            diagnostics.append(
                Diagnostic(
                    code="whitespace",
                    path=match.group(1),
                    line=int(match.group(2)),
                    message=match.group(3),
                )
            )
    return diagnostics


def workspace_errors(candidate_sha: str) -> list[Diagnostic]:
    expected = subprocess.run(
        ["git", "rev-parse", f"{candidate_sha}^{{commit}}"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    actual = subprocess.run(
        ["git", "rev-parse", "HEAD^{commit}"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    errors: list[Diagnostic] = []
    if actual != expected:
        errors.append(
            Diagnostic(
                code="workspace-commit",
                message=f"workspace HEAD is {actual}, expected candidate {expected}",
            )
        )

    status = subprocess.run(
        [
            "git",
            "status",
            "--porcelain=v1",
            "-z",
            "--untracked-files=all",
            "--ignore-submodules=all",
        ],
        check=True,
        capture_output=True,
    ).stdout
    if status:
        errors.append(
            Diagnostic(
                code="workspace-dirty",
                message="workspace files differ from the checked-out candidate",
            )
        )
    return errors


HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def added_line_numbers(base_sha: str, candidate_sha: str) -> dict[str, set[int]]:
    """Line numbers each candidate file *adds*, so a check can report only new code.

    Debug intent: whole-file reporting would bury a reviewer in pre-existing findings (this tree has
    hundreds), which is how a lint gate ends up disabled. `-U0` gives hunks with no context, so every
    `+` line number comes straight from the `@@ +start,count @@` header.
    """
    result = subprocess.run(
        ["git", "diff", "-U0", "--no-color", "--no-renames", "--diff-filter=ACMRT", base_sha, candidate_sha],
        check=True,
        capture_output=True,
        text=True,
    )
    added: dict[str, set[int]] = {}
    current: str | None = None
    for line in result.stdout.splitlines():
        if line.startswith("+++ b/"):
            current = line[len("+++ b/") :]
            added.setdefault(current, set())
            continue
        if line.startswith("--- ") or line.startswith("diff --git "):
            continue
        match = HUNK_RE.match(line)
        if match and current is not None:
            start = int(match.group(1))
            count = int(match.group(2)) if match.group(2) is not None else 1
            added[current].update(range(start, start + count))
    return added


def kotlin_smell_diagnostics(
    path: Path,
    relative_path: Path,
    added_lines: set[int],
    errors: list[Diagnostic],
    warnings: list[Diagnostic],
) -> None:
    """Report the smells a candidate introduces into a Kotlin file.

    Only added lines are reported. `check_kotlin_smells.ERROR_CODES` (empty `catch` blocks and
    `GlobalScope`) fail the check because they destroy the only evidence of a failure or start work
    nobody can cancel; the rest (`!!`, `printStackTrace()`) are warnings because they are often
    deliberate and always visible in review.
    """
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return

    for smell in kotlin_smells(text):
        if smell.line not in added_lines:
            continue
        diagnostic = Diagnostic(
            code=smell.code,
            path=str(relative_path),
            line=smell.line,
            message=smell.message,
        )
        if smell.code in ERROR_CODES:
            errors.append(diagnostic)
        else:
            warnings.append(diagnostic)


def check_text_file(path: Path, errors: list[Diagnostic]) -> None:
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return

    conflict_start: int | None = None
    conflict_separator = False
    for line_number, line in enumerate(text.splitlines(), start=1):
        if line.startswith("<<<<<<< "):
            conflict_start = line_number
            conflict_separator = False
        elif conflict_start is not None and line.startswith("======="):
            conflict_separator = True
        elif conflict_start is not None and conflict_separator and line.startswith(">>>>>>> "):
            errors.append(
                Diagnostic(
                    code="merge-conflict",
                    path=str(path),
                    line=conflict_start,
                    message="merge conflict marker",
                )
            )
            conflict_start = None
            conflict_separator = False


def check_json_file(path: Path, errors: list[Diagnostic]) -> None:
    try:
        with path.open(encoding="utf-8") as stream:
            json.load(stream)
    except (OSError, json.JSONDecodeError) as error:
        errors.append(
            Diagnostic(
                code="json",
                path=str(path),
                line=getattr(error, "lineno", None),
                message=f"invalid JSON: {error}",
            )
        )


def check_xml_file(path: Path, errors: list[Diagnostic]) -> None:
    try:
        _safe_xml_parse(path)
    except (OSError, ET.ParseError) as error:
        line = error.position[0] if isinstance(error, ET.ParseError) else None
        errors.append(
            Diagnostic(
                code="xml",
                path=str(path),
                line=line,
                message=f"invalid XML: {error}",
            )
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--candidate", required=True)
    args = parser.parse_args()

    repo_root = Path.cwd()
    errors = whitespace_errors(args.base, args.candidate)
    warnings: list[Diagnostic] = []
    state_errors = workspace_errors(args.candidate)
    errors.extend(state_errors)
    files = changed_files(args.base, args.candidate)
    added_lines = added_line_numbers(args.base, args.candidate) if not state_errors else {}

    if not state_errors:
        for relative_path in files:
            path = repo_root / relative_path
            if path.is_symlink():
                errors.append(
                    Diagnostic(
                        code="symlink",
                        path=str(relative_path),
                        message="changed symbolic links require explicit repository review",
                    )
                )
                continue
            if not path.is_file():
                continue

            check_text_file(relative_path, errors)

            if relative_path.suffix.lower() == ".json":
                check_json_file(relative_path, errors)
            elif relative_path.suffix.lower() == ".xml":
                check_xml_file(relative_path, errors)
            elif relative_path.suffix.lower() in KOTLIN_SUFFIXES:
                kotlin_smell_diagnostics(
                    path,
                    relative_path,
                    added_lines.get(str(relative_path), set()),
                    errors,
                    warnings,
                )

    kotlin_files = sum(1 for path in files if path.suffix.lower() in KOTLIN_SUFFIXES)
    return report(
        "Repository hygiene",
        errors,
        warnings=warnings,
        notes=[
            f"Checked {len(files)} added or modified candidate file(s).",
            f"Kotlin files scanned for smells introduced by this candidate: {kotlin_files}.",
        ],
    )


if __name__ == "__main__":
    raise SystemExit(main())
