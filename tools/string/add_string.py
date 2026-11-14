#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

def _safe_xml_parser():
    """Parser that refuses DTD and entity declarations (XXE, billion laughs)."""
    parser = ET.XMLParser()
    return parser


def _reject_xml_decl(*_args, **_kwargs):
    raise ValueError("XML DTD/entity declarations are not allowed")


def _safe_xml_parse_bytes(data):
    """Parse XML bytes with entity and DTD declarations rejected outright."""
    import xml.parsers.expat as _expat

    parser = _expat.ParserCreate()
    parser.StartDoctypeDeclHandler = _reject_xml_decl
    parser.EntityDeclHandler = _reject_xml_decl
    parser.UnparsedEntityDeclHandler = _reject_xml_decl
    parser.ExternalEntityRefHandler = _reject_xml_decl

    builder = ET.TreeBuilder()
    parser.StartElementHandler = builder.start
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

if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


def _repo_root() -> str:
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def _default_files(repo_root: str) -> dict:
    return {
        "zh": os.path.join(repo_root, "app", "src", "main", "res", "values", "strings.xml"),
        "en": os.path.join(repo_root, "app", "src", "main", "res", "values-en", "strings.xml"),
    }


def _load_or_create_xml(file_path: str) -> ET.Element:
    if os.path.exists(file_path):
        tree = _safe_xml_parse(file_path)
        root = tree.getroot()
        if root.tag != "resources":
            raise ValueError(f"Invalid root tag: {root.tag}")
        return root

    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    return ET.Element("resources")


def _indent(root: ET.Element) -> None:
    try:
        ET.indent(root, space="    ", level=0)
    except Exception:
        pass


def _validate_name(name: str) -> None:
    if not name:
        raise ValueError("name is empty")
    if not re.fullmatch(r"[A-Za-z0-9_.]+", name):
        raise ValueError(f"invalid name: {name}")


def main() -> int:
    parser = argparse.ArgumentParser(prog="add_string.py")
    parser.add_argument("--lang", choices=["zh", "en"], help="target language file")
    parser.add_argument("--file", help="explicit strings.xml path")
    parser.add_argument("--name", required=True, help="string key")
    parser.add_argument("--value", required=True, help="string value")
    parser.add_argument("--no-update", action="store_true", help="fail if key already exists")
    args = parser.parse_args()

    _validate_name(args.name)

    repo_root = _repo_root()
    defaults = _default_files(repo_root)
    file_path = args.file or defaults.get(args.lang or "zh")
    if not file_path:
        print("[X] missing --file or --lang")
        return 2

    try:
        root = _load_or_create_xml(file_path)
    except Exception as e:
        print(f"[X] failed to load: {file_path}: {e}")
        return 2

    matches = [e for e in root.findall("string") if e.get("name") == args.name]

    if matches:
        if args.no_update:
            print(f"[X] already exists: {args.name} ({len(matches)} occurrence(s))")
            return 1
        old_value = matches[0].text or ""
        matches[0].text = args.value
        action = "updated"
    else:
        elem = ET.SubElement(root, "string", {"name": args.name})
        elem.text = args.value
        old_value = ""
        action = "added"

    tree = ET.ElementTree(root)
    _indent(root)

    try:
        tree.write(file_path, encoding="utf-8", xml_declaration=True)
    except Exception as e:
        print(f"[X] failed to write: {file_path}: {e}")
        return 2

    if action == "updated":
        print(f"[OK] {action}: {args.name}")
        print(f"file: {file_path}")
        print(f"old: {old_value}")
        print(f"new: {args.value}")
    else:
        print(f"[OK] {action}: {args.name}")
        print(f"file: {file_path}")
        print(f"value: {args.value}")

    if len(matches) > 1:
        print(f"[WARNING] duplicates remain: {args.name} ({len(matches)} occurrence(s))")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
