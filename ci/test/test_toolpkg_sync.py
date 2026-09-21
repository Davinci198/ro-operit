from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT / "tools" / "example_packages"))

from sync_example_packages import (  # noqa: E402
    _extract_ro_metadata_fields,
    _manifest_runtime_files,
    _pack_toolpkg_folder,
    _preserve_ro_metadata_fields,
)


class ToolPkgRuntimeFilesTest(unittest.TestCase):
    def test_ignored_runtime_files_are_included_in_archive(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            subprocess.run(
                ["git", "init", "-b", "main"],
                cwd=repository,
                check=True,
                capture_output=True,
            )
            package = repository / "example"
            package.mkdir()
            (package / "modules").mkdir()
            (package / ".gitignore").write_text("main.js\nmodules/\n", encoding="utf-8")
            (package / "manifest.json").write_text(
                json.dumps(
                    {
                        "toolpkg_id": "com.operit.test",
                        "main": "main.js",
                        "wasm_modules": [{"id": "core", "path": "modules/core.wasm"}],
                    }
                ),
                encoding="utf-8",
            )
            (package / "main.js").write_text("exports.test = true;\n", encoding="utf-8")
            (package / "modules" / "core.wasm").write_bytes(b"\x00asm")
            archive = repository / "example.toolpkg"

            _pack_toolpkg_folder(repository, package, archive)

            with zipfile.ZipFile(archive) as stream:
                names = set(stream.namelist())
            self.assertIn("main.js", names)
            self.assertIn("modules/core.wasm", names)

    def test_missing_runtime_file_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory)
            (package / "manifest.json").write_text(
                json.dumps({"toolpkg_id": "com.operit.test", "main": "dist/main.js"}),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(FileNotFoundError, "Missing ToolPkg runtime file"):
                _manifest_runtime_files(package)

    def test_runtime_path_cannot_escape_package(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            package = Path(directory)
            (package / "manifest.json").write_text(
                json.dumps({"toolpkg_id": "com.operit.test", "main": "../outside.js"}),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "escapes the package directory"):
                _manifest_runtime_files(package)

    def test_runtime_symlink_cannot_escape_package(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            package = root / "package"
            package.mkdir()
            outside = root / "outside.js"
            outside.write_text("outside\n", encoding="utf-8")
            (package / "main.js").symlink_to(outside)
            (package / "manifest.json").write_text(
                json.dumps({"toolpkg_id": "com.operit.test", "main": "main.js"}),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "symbolic link"):
                _manifest_runtime_files(package)


class RoMetadataPreservationTest(unittest.TestCase):
    """Fork-only behavior: assets carry ro translations that examples/ lacks."""

    HEADER_TEMPLATE = (
        "/* METADATA\n"
        "{\n"
        '    "name": "demo",\n'
        '    "display_name": {\n'
        '        "zh": "演示",\n'
        '        "en": "Demo"\n'
        "    },\n"
        '    "description": {\n'
        '        "zh": "演示包。",\n'
        '        "en": "Demo package."\n'
        "    },\n"
        '    "enabledByDefault": true\n'
        "}\n"
        "*/\n"
        "console.log('demo');\n"
    )

    def _write(self, path: Path, text: str) -> None:
        path.write_text(text, encoding="utf-8")

    def test_ro_fields_are_reinjected_after_overwrite(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dest = root / "demo.js"
            source = root / "upstream.js"
            self._write(
                dest,
                self.HEADER_TEMPLATE.replace(
                    '"zh": "演示",', '"ro": "Demonstrație",\n        "zh": "演示",'
                ).replace(
                    '"zh": "演示包。",', '"ro": "Pachet demonstrativ.",\n        "zh": "演示包。",'
                ),
            )
            self._write(source, self.HEADER_TEMPLATE)

            _preserve_ro_metadata_fields(dest, source)

            merged = dest.read_text(encoding="utf-8")
            fields = _extract_ro_metadata_fields(merged)
            self.assertEqual(
                fields,
                {"display_name": "Demonstrație", "description": "Pachet demonstrativ."},
            )
            self.assertIn("console.log('demo');", merged)

    def test_injected_header_stays_valid_json(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dest = root / "demo.js"
            source = root / "upstream.js"
            self._write(
                dest,
                self.HEADER_TEMPLATE.replace(
                    '"zh": "演示",', '"ro": "Demonstrație",\n        "zh": "演示",'
                ),
            )
            self._write(source, self.HEADER_TEMPLATE)

            _preserve_ro_metadata_fields(dest, source)

            merged = dest.read_text(encoding="utf-8")
            block = merged[merged.find("{") : merged.find("*/")]
            data = json.loads(block)
            self.assertEqual(data["display_name"]["ro"], "Demonstrație")
            self.assertEqual(data["display_name"]["en"], "Demo")
            # Repo hygiene forbids trailing whitespace; injected lines must not add any.
            self.assertFalse(any(line != line.rstrip() for line in merged.splitlines()))

    def test_skipped_when_source_already_has_ro(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dest = root / "demo.js"
            source = root / "upstream.js"
            self._write(
                dest,
                self.HEADER_TEMPLATE.replace(
                    '"zh": "演示",', '"ro": "Veche",\n        "zh": "演示",'
                ),
            )
            self._write(
                source,
                self.HEADER_TEMPLATE.replace(
                    '"zh": "演示",', '"ro": "Nouă",\n        "zh": "演示",'
                ),
            )

            _preserve_ro_metadata_fields(dest, source)

            # Source already declares ro: destination keeps its previous value.
            fields = _extract_ro_metadata_fields(dest.read_text(encoding="utf-8"))
            self.assertEqual(fields["display_name"], "Veche")

    def test_skipped_when_source_has_unquoted_hjson_ro(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dest = root / "demo.js"
            source = root / "upstream.js"
            self._write(
                dest,
                self.HEADER_TEMPLATE.replace(
                    '"zh": "演示",', '"ro": "Demonstrație",\n        "zh": "演示",'
                ),
            )
            self._write(
                source,
                self.HEADER_TEMPLATE.replace(
                    '"zh": "演示",', 'ro: "Nouă",\n        "zh": "演示",'
                ),
            )

            _preserve_ro_metadata_fields(dest, source)

            # Unquoted hjson ro in source is still a declaration: keep dest value.
            fields = _extract_ro_metadata_fields(dest.read_text(encoding="utf-8"))
            self.assertEqual(fields["display_name"], "Demonstrație")

    def test_noop_when_destination_has_no_ro(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dest = root / "demo.js"
            source = root / "upstream.js"
            self._write(dest, self.HEADER_TEMPLATE)
            self._write(source, self.HEADER_TEMPLATE)

            _preserve_ro_metadata_fields(dest, source)

            self.assertEqual(
                dest.read_text(encoding="utf-8"),
                self.HEADER_TEMPLATE,
            )

    def test_ro_value_closing_comment_is_not_injected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dest = root / "demo.js"
            source = root / "upstream.js"
            self._write(
                dest,
                self.HEADER_TEMPLATE.replace(
                    '"zh": "演示",', '"ro": "Rău */ înjectat",\n        "zh": "演示",'
                ),
            )
            self._write(source, self.HEADER_TEMPLATE)

            before = dest.read_text(encoding="utf-8")
            _preserve_ro_metadata_fields(dest, source)

            # A ro value containing the comment terminator must block the rewrite.
            self.assertEqual(dest.read_text(encoding="utf-8"), before)


if __name__ == "__main__":
    unittest.main()
