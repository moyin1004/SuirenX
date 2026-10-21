#!/usr/bin/env python3
"""Validate native Android translation coverage and typed format arguments."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FORMAT = re.compile(r"%(?:(\d+)\$)?([sdf])")


def resources(directory):
    result = {}
    for path in sorted(directory.glob("*.xml")):
        for item in ET.parse(path).getroot():
            if item.tag not in ("string", "plurals") or item.get("translatable") == "false":
                continue
            name = item.attrib["name"]
            if name in result:
                raise ValueError(f"Duplicate resource {name} in {directory}")
            values = {child.attrib["quantity"]: "".join(child.itertext()) for child in item} if item.tag == "plurals" else {"string": "".join(item.itertext())}
            result[name] = (item.tag, {key: sorted(FORMAT.findall(value)) for key, value in values.items()})
    return result


def main():
    count = 0
    android = ROOT / "apps/android"
    modules = [android / "app", *(android / "core").iterdir(), *(android / "feature").iterdir()]
    for directory in sorted(module / "src/main/res/values" for module in modules):
        default = resources(directory)
        if not default:
            continue
        english = resources(directory.with_name("values-en"))
        if default.keys() != english.keys():
            raise ValueError(f"{directory}: missing English keys {sorted(default.keys() - english.keys())}; extra keys {sorted(english.keys() - default.keys())}")
        for key, expected in default.items():
            if expected != english[key]:
                raise ValueError(f"{directory}: mismatched plural categories or format arguments for {key}")
        count += len(default)
    print(f"Android localization: {count} resources have matching Chinese/English keys, plural categories and format arguments.")


if __name__ == "__main__":
    main()
