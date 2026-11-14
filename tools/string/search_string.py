#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import argparse
import os
import sys
import xml.etree.ElementTree as ET

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


def _iter_strings(file_path: str):
    tree = _safe_xml_parse(file_path)
    root = tree.getroot()
    for e in root.findall("string"):
        name = e.get("name")
        value = e.text or ""
        yield name, value


def main() -> int:
    parser = argparse.ArgumentParser(prog="search_string.py")
    parser.add_argument("--lang", choices=["zh", "en"], help="target language file")
    parser.add_argument("--file", help="explicit strings.xml path")
    parser.add_argument("--name", help="match key (substring, case-insensitive)")
    parser.add_argument("--value", help="match value (substring, case-insensitive)")
    args = parser.parse_args()

    if not args.name and not args.value:
        print("[X] need --name and/or --value")
        return 2

    repo_root = _repo_root()
    defaults = _default_files(repo_root)

    file_paths = []
    if args.file:
        file_paths = [args.file]
    elif args.lang:
        file_paths = [defaults[args.lang]]
    else:
        file_paths = [defaults["zh"], defaults["en"]]

    name_q = (args.name or "").lower()
    value_q = (args.value or "").lower()

    total = 0
    for file_path in file_paths:
        if not os.path.exists(file_path):
            print(f"[X] file not found: {file_path}")
            continue

        try:
            matches = []
            for name, value in _iter_strings(file_path):
                if not name:
                    continue
                if name_q and name_q not in name.lower():
                    continue
                if value_q and value_q not in (value or "").lower():
                    continue
                matches.append((name, value))

            if matches:
                print(file_path)
                for name, value in matches:
                    print(f"  {name} = {value}")
                total += len(matches)
        except Exception as e:
            print(f"[X] failed to parse: {file_path}: {e}")

    print(f"\nTotal matches: {total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
