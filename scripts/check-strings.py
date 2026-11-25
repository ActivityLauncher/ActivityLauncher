#!/usr/bin/env python3
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
RES_DIR = PROJECT_ROOT / "app/src/main/res"
BASE_PATH = RES_DIR / "values/strings.xml"
SORT_SCRIPT = SCRIPT_DIR / "sort-strings.py"

# Technical terms and proper nouns that are intentionally identical to English.
LIKELY_SAME = {
    "action_view_paid",
    "app_name",
    "label_class",
    "label_intent_action",
    "label_intent_categories",
    "label_intent_data",
    "label_intent_extra_key",
    "label_intent_extra_value",
    "label_intent_extras",
    "label_intent_mime_type",
    "label_package",
    "settings_pro_version",
}

# Locale-specific words that are identical to English in the target language.
ALLOWED_IDENTICAL = {
    "values-ca-rES": {"error"},
    "values-de-rDE": {"label_name"},
    "values-es-rES": {"error"},
}


def get_strings(path):
    try:
        tree = ET.parse(path)
        root = tree.getroot()
        strings = {}
        for string in root.findall('string'):
            name = string.get('name')
            text = string.text or ""
            # Handle cases where there are child elements (like <b>)
            if len(string) > 0:
                text = ET.tostring(string, encoding='unicode', method='xml')
                # Remove <string ...> and </string>
                text = re.sub(r'^<string[^>]*>', '', text)
                text = re.sub(r'</string>$', '', text)
            strings[name] = text
        return strings
    except Exception as e:
        print(f"Error parsing {path}: {e}")
        return None


def get_placeholders(text):
    # Matches %d, %s, %1$d, %2$s, etc.
    return re.findall(r'%[0-9]*\$?[a-zA-Z]', text)


def unescaped_apostrophe_issues():
    """Android string resources require apostrophes to be escaped as \\'."""
    issues = []
    paths = [BASE_PATH] + sorted(RES_DIR.glob("values-*/strings.xml"))
    for path in paths:
        text = path.read_text(encoding='utf-8')
        for match in re.finditer(
            r'<string\b[^>]*name="([^"]+)"[^>]*>(.*?)</string>', text, re.DOTALL
        ):
            if re.search(r"(?<!\\)'", match.group(2)):
                locale = path.parent.name
                issues.append(f"[{locale}] Unescaped apostrophe in '{match.group(1)}'")
    return issues


def sync_strings():
    """Run sort-strings.py without flooding output with one line per file."""
    result = subprocess.run(
        [sys.executable, str(SORT_SCRIPT)],
        cwd=PROJECT_ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        sys.stdout.write(result.stdout)
        sys.stderr.write(result.stderr)
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description="Check Android string translations.")
    parser.add_argument(
        "--sync",
        action="store_true",
        help="sort/sync translations from the base strings.xml before checking",
    )
    args = parser.parse_args()

    if args.sync:
        return_code = sync_strings()
        if return_code != 0:
            return return_code

    base_strings = get_strings(BASE_PATH)
    if base_strings is None:
        return 1

    translation_files = sorted(RES_DIR.glob("values-*/strings.xml"))
    issues = unescaped_apostrophe_issues()

    for trans_path in translation_files:
        locale = trans_path.parent.name
        trans_strings = get_strings(trans_path)
        if trans_strings is None:
            issues.append(f"[{locale}] FAILED TO PARSE (invalid XML)")
            continue

        # 1. Check for missing/extra keys
        missing = set(base_strings.keys()) - set(trans_strings.keys())
        if missing:
            issues.append(f"[{locale}] Missing keys: {', '.join(sorted(missing))}")

        extra = set(trans_strings.keys()) - set(base_strings.keys())
        if extra:
            issues.append(f"[{locale}] Extra keys: {', '.join(sorted(extra))}")

        # 2. Check for placeholder mismatches
        for key in base_strings.keys():
            if key in trans_strings:
                base_placeholders = sorted(get_placeholders(base_strings[key]))
                trans_placeholders = sorted(get_placeholders(trans_strings[key]))
                if base_placeholders != trans_placeholders:
                    issues.append(f"[{locale}] Placeholder mismatch for '{key}': base={base_placeholders}, trans={trans_placeholders}")

        # 3. Check for suspicious untranslated strings (identical to English)
        if locale not in ["values-en-rUS", "values-en-rGB"]:
            allowed = ALLOWED_IDENTICAL.get(locale, set())
            for key, base_text in base_strings.items():
                if key in trans_strings and key not in LIKELY_SAME and key not in allowed:
                    trans_text = trans_strings[key]
                    if base_text == trans_text and len(base_text.strip()) > 0:
                        issues.append(f"[{locale}] Untranslated string '{key}' (matches English)")

    if not issues:
        print("No issues found in strings.xml files.")
        return 0

    for issue in issues:
        print(issue)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
