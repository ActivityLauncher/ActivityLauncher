#!/usr/bin/env python3
import os
import re
import subprocess
import sys
import time

PACKAGE_NAME = "de.szalkowski.activitylauncher.oss"
TARGET_PACKAGE = "com.android.settings"

RELEASE_TAGS = ["2.3.1", "2.4.1"]
NEW_APK_PATH = "app/build/outputs/apk/ossNoads/debug/app-oss-noads-debug.apk"

SNAPSHOT_COUNTER = 0


def get_env():
    env = os.environ.copy()
    env["APPID"] = "de.szalkowski.activitylauncher.oss"
    env["HOME"] = os.path.expanduser("~")
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        env["JAVA_HOME"] = java_home
        bin_path = os.path.join(java_home, "bin")
        env["PATH"] = f"{bin_path}:{env.get('PATH', '')}"
    return env


def run_cmd(cmd, check=True):
    print(f"Executing: {cmd}")
    env = get_env()
    res = subprocess.run(
        cmd, shell=True, capture_output=True, text=True, env=env
    )
    if check and res.returncode != 0:
        print(
            f"Command failed with exit code {res.returncode}:\nSTDOUT: {res.stdout}\nSTDERR: {res.stderr}"
        )
        sys.exit(1)
    return res


def adb(args, check=True):
    return run_cmd(f"adb {args}", check=check)


def adb_shell(cmd, check=True):
    return adb(f'shell "{cmd}"', check=check)


def adb_install_with_retry(apk_path, retries=5):
    for attempt in range(retries):
        print(f"Installing {apk_path} (attempt {attempt+1}/{retries})...")
        res = adb(f"install -r -g {apk_path}", check=False)
        if res.returncode == 0:
            print("Installation succeeded!")
            time.sleep(1)
            return True
        time.sleep(2)

    print(f"ERROR: Failed to install {apk_path} after {retries} attempts!")
    sys.exit(1)


class UiDevice:
    def __init__(self):
        self.w, self.h = self._get_screen_size()

    def _get_screen_size(self):
        res = adb_shell("wm size", check=False)
        match = re.search(r"(\d+)x(\d+)", res.stdout)
        if match:
            return int(match.group(1)), int(match.group(2))
        return 1080, 1920

    def dump_hierarchy(self):
        adb_shell("uiautomator dump /sdcard/window_dump.xml >/dev/null", check=False)
        res = adb_shell("cat /sdcard/window_dump.xml", check=False)
        return res.stdout

    def screenshot(self, path):
        adb_shell("screencap -p /sdcard/screen.png", check=False)
        adb(f"pull /sdcard/screen.png {path}", check=False)

    def press(self, key):
        if key == "home":
            adb_shell("input keyevent KEYCODE_HOME")
        elif key == "back":
            adb_shell("input keyevent KEYCODE_BACK")
        elif key == "enter":
            adb_shell("input keyevent KEYCODE_ENTER")
        elif key == "tab":
            adb_shell("input keyevent KEYCODE_TAB")
        elif key == "escape":
            adb_shell("input keyevent KEYCODE_ESCAPE")

    def scroll_down(self):
        cx = self.w // 2
        start_y = int(self.h * 0.75)
        end_y = int(self.h * 0.25)
        print(f"Scrolling down: swiping from ({cx}, {start_y}) to ({cx}, {end_y}) on {self.w}x{self.h} screen")
        adb_shell(f"input swipe {cx} {start_y} {cx} {end_y} 300")

    def swipe_left(self):
        cy = int(self.h * 0.6)
        start_x = int(self.w * 0.85)
        end_x = int(self.w * 0.15)
        print(f"Swiping left: from ({start_x}, {cy}) to ({end_x}, {cy}) on {self.w}x{self.h} screen")
        adb_shell(f"input swipe {start_x} {cy} {end_x} {cy} 500")

    def swipe_right(self):
        cy = int(self.h * 0.6)
        start_x = int(self.w * 0.15)
        end_x = int(self.w * 0.85)
        print(f"Swiping right: from ({start_x}, {cy}) to ({end_x}, {cy}) on {self.w}x{self.h} screen")
        adb_shell(f"input swipe {start_x} {cy} {end_x} {cy} 500")

    def open_app_drawer(self):
        print("Opening App Drawer...")
        if self.click(text_contains="Apps list", wait=2):
            print("Clicked 'Apps list' button!")
            return

        cx = self.w // 2
        start_y = int(self.h * 0.85)
        end_y = int(self.h * 0.2)
        print(f"Swiping up to open App Drawer from ({cx}, {start_y}) to ({cx}, {end_y})")
        adb_shell(f"input swipe {cx} {start_y} {cx} {end_y} 300")

    def app_start(self, component_name):
        adb_shell(f"am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n {component_name}")

    def current_package(self):
        res = adb_shell("dumpsys window | grep mCurrentFocus", check=False)
        match = re.search(r"(\b[a-zA-Z0-9_.]+\b)/", res.stdout)
        return match.group(1) if match else ""

    def click(self, resource_id=None, text=None, text_contains=None, wait=1.5, retries=5):
        for attempt in range(retries):
            xml = self.dump_hierarchy()
            xml_lower = xml.lower()
            if "isn't responding" in xml_lower or "aerr_" in xml_lower or "stylus" in xml_lower:
                print("System ANR or overlay detected during click attempt, dismissing...")
                dismiss_system_prompts(self)
                xml = self.dump_hierarchy()

            for match in re.finditer(r"<node ([^>]+)>", xml):
                attr = match.group(1)
                if resource_id and f'resource-id="{resource_id}"' not in attr:
                    continue
                if text and f'text="{text}"' not in attr:
                    continue
                if text_contains:
                    tm = re.search(r'text="([^"]*)"', attr)
                    dm = re.search(r'content-desc="([^"]*)"', attr)
                    node_text = (tm.group(1) if tm else "") + " " + (dm.group(1) if dm else "")
                    if text_contains.lower() not in node_text.lower():
                        continue

                bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', attr)
                if bm:
                    x1, y1, x2, y2 = map(int, bm.groups())
                    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                    print(f"Clicking '{text or resource_id or text_contains}' at ({cx}, {cy})")
                    adb_shell(f"input tap {cx} {cy}")
                    time.sleep(wait)
                    return True
            time.sleep(1)
        return False


def disable_stylus_and_keyboard_prompts():
    adb_shell("settings put secure stylus_handwriting_enabled 0", check=False)
    adb_shell("settings put secure show_stylus_handwriting_pointer 0", check=False)
    adb_shell("settings put global stylus_handwriting_enabled 0", check=False)


def uninstall_all_activitylauncher_packages():
    res = adb_shell("pm list packages", check=False)
    for line in res.stdout.splitlines():
        if "activitylauncher" in line:
            pkg = line.replace("package:", "").strip()
            if pkg:
                print(f"Uninstalling existing package: {pkg}")
                adb(f"uninstall {pkg}", check=False)


def check_installed_packages_and_version():
    res = adb_shell("pm list packages --show-versioncode", check=False)
    al_packages = []
    for line in res.stdout.splitlines():
        if "activitylauncher" in line:
            pkg_info = line.replace("package:", "").strip()
            al_packages.append(pkg_info)
    return al_packages


def find_apksigner():
    android_home = (
        os.environ.get("ANDROID_HOME")
        or os.environ.get("ANDROID_SDK_ROOT")
        or os.path.expanduser("~/Android/Sdk")
    )
    build_tools_dir = os.path.join(android_home, "build-tools")
    if os.path.exists(build_tools_dir):
        versions = sorted(os.listdir(build_tools_dir), reverse=True)
        for ver in versions:
            path = os.path.join(build_tools_dir, ver, "apksigner")
            if os.path.exists(path):
                return path

    which_res = subprocess.run(
        "which apksigner", shell=True, capture_output=True, text=True
    )
    if which_res.returncode == 0:
        return which_res.stdout.strip()
    return "apksigner"


def strip_signatures_and_resign(apk_path, keystore):
    print(f"Stripping old signatures from {apk_path}...")
    run_cmd(
        f'zip -d {apk_path} "META-INF/*.SF" "META-INF/*.RSA" "META-INF/*.DSA" "META-INF/*.EC" "META-INF/MANIFEST.MF"',
        check=False,
    )
    apksigner = find_apksigner()
    sign_cmd = f'"{apksigner}" sign --ks {keystore} --ks-pass pass:android --key-pass pass:android {apk_path}'
    run_cmd(sign_cmd)


def setup_environment():
    print("=== Step 1: Ensuring debug keystore exists ===")
    keystore_dir = os.path.expanduser("~/.android")
    os.makedirs(keystore_dir, exist_ok=True)
    keystore = os.path.join(keystore_dir, "debug.keystore")
    if not os.path.exists(keystore):
        print(f"Creating debug keystore at {keystore}...")
        keytool_cmd = f"keytool -genkey -v -keystore {keystore} -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'"
        run_cmd(keytool_cmd)

    print("=== Step 2: Checking downloaded debug APK artifact or building current version APK ===")
    if not os.path.exists(NEW_APK_PATH):
        downloaded_debug_apk = "app/build/outputs/apk/ossNoads/debug/debug.apk"
        if os.path.exists(downloaded_debug_apk):
            print(f"Found downloaded artifact at {downloaded_debug_apk}, copying to {NEW_APK_PATH}...")
            os.makedirs(os.path.dirname(NEW_APK_PATH), exist_ok=True)
            run_cmd(f"cp {downloaded_debug_apk} {NEW_APK_PATH}")

    if not os.path.exists(NEW_APK_PATH):
        print(f"Current version APK not found at {NEW_APK_PATH}, building it now...")
        run_cmd(
            "./gradlew app:assembleOssNoadsDebug -PAPPID=de.szalkowski.activitylauncher.oss"
        )
    if not os.path.exists(NEW_APK_PATH):
        print(f"Error: Current version APK not found at {NEW_APK_PATH} after build/download!")
        sys.exit(1)

    print("=== Step 3: Re-signing current version APK with exact same debug keystore ===")
    strip_signatures_and_resign(NEW_APK_PATH, keystore)
    print(f"Current version APK verified and re-signed at {NEW_APK_PATH}")


def log_ui_summary(xml_content, label="", max_nodes=50):
    nodes = []
    for match in re.finditer(r"<node ([^>]+)>", xml_content):
        attr = match.group(1)
        tm = re.search(r'text="([^"]*)"', attr)
        dm = re.search(r'content-desc="([^"]*)"', attr)
        rid = re.search(r'resource-id="([^"]*)"', attr)
        cls = re.search(r'class="([^"]*)"', attr)
        pkg = re.search(r'package="([^"]*)"', attr)
        bm = re.search(r'bounds="([^"]*)"', attr)

        text = tm.group(1) if tm else ""
        desc = dm.group(1) if dm else ""
        res_id = rid.group(1) if rid else ""
        class_name = cls.group(1).split(".")[-1] if cls else ""
        package = pkg.group(1) if pkg else ""
        bounds = bm.group(1) if bm else ""

        displayed_text = f"text='{text}'" if text else ""
        if desc:
            displayed_text += (
                f" desc='{desc}'" if displayed_text else f"desc='{desc}'"
            )

        if text or desc or res_id:
            short_id = res_id.split("/")[-1] if "/" in res_id else res_id
            nodes.append(
                f"  - [{package}] {class_name} ({short_id}) {displayed_text} {bounds}"
            )

    print(f"--- UI Screen Summary [{label}] ({len(nodes)} labeled nodes) ---")
    if nodes:
        for n in nodes[:max_nodes]:
            print(n)
        if len(nodes) > max_nodes:
            print(f"  ... and {len(nodes) - max_nodes} more nodes")
    else:
        print("  (No text/resource-id nodes found in XML dump)")
    print("---------------------------------------------------------")


def save_snapshot(d, label="snapshot"):
    global SNAPSHOT_COUNTER
    SNAPSHOT_COUNTER += 1
    artifacts_dir = "upgrade-test-artifacts"
    os.makedirs(artifacts_dir, exist_ok=True)

    sanitized_label = re.sub(r"[^a-zA-Z0-9_-]", "_", str(label))[:50]
    prefix = os.path.join(
        artifacts_dir, f"{SNAPSHOT_COUNTER:02d}_{sanitized_label}"
    )

    xml_path = f"{prefix}.xml"
    png_path = f"{prefix}.png"

    try:
        xml_content = d.dump_hierarchy()
        with open(xml_path, "w", encoding="utf-8") as f:
            f.write(xml_content)
        log_ui_summary(xml_content, label=sanitized_label)
    except Exception as e:
        print(f"Error dumping hierarchy: {e}")

    try:
        d.screenshot(png_path)
    except Exception as e:
        print(f"Error taking screenshot: {e}")


def dismiss_system_prompts(d):
    disable_stylus_and_keyboard_prompts()
    for loop in range(3):
        xml = d.dump_hierarchy()
        xml_lower = xml.lower()
        if not any(
            k in xml_lower
            for k in [
                "stylus",
                "got it",
                "skip",
                "allow",
                "permission",
                "welcome",
                "keyboard",
                "isn't responding",
                "is not responding",
                "close app",
                "aerr_close",
                "aerr_wait",
                "wait",
            ]
        ):
            break

        print(f"System prompt/ANR dialog detected (attempt {loop+1}), clearing...")
        if "aerr_close" in xml or "close app" in xml_lower:
            d.click(resource_id="android:id/aerr_close") or d.click(text="Close app")
        elif "aerr_wait" in xml or "wait" in xml_lower:
            d.click(resource_id="android:id/aerr_close") or d.click(text="Close app") or d.click(resource_id="android:id/aerr_wait")

        d.click(text_contains="Got it") or \
        d.click(text_contains="SKIP") or \
        d.click(text_contains="Allow") or \
        d.click(resource_id="android:id/button1") or \
        d.click(text="OK")

        adb_shell("input keyevent KEYCODE_ESCAPE", check=False)
        time.sleep(1)


def ensure_app_launched(d, package_name, component_name):
    disable_stylus_and_keyboard_prompts()
    for _ in range(8):
        adb_shell("input keyevent KEYCODE_WAKEUP", check=False)
        adb_shell("wm dismiss-keyguard", check=False)
        d.scroll_down()
        time.sleep(1)

        d.app_start(component_name)
        time.sleep(2)

        xml = d.dump_hierarchy()
        xml_lower = xml.lower()
        if 'package="android"' in xml or "application error" in xml_lower or "isn't responding" in xml_lower or "stylus" in xml_lower:
            print("System/ANR dialog detected, clearing...")
            dismiss_system_prompts(d)
            time.sleep(1)

        focus = d.current_package()
        if package_name in focus:
            print(f"App {package_name} is in focus!")
            return True
        time.sleep(1)
    print(f"Warning: Could not focus {package_name}")
    save_snapshot(d, "cannot_focus_app")
    return False


class VersionShortcutCreator:
    """Base class for version-specific shortcut creation workflows."""

    def __init__(self, device: UiDevice, release_tag: str):
        self.d = device
        self.release_tag = release_tag
        self.package_name = PACKAGE_NAME
        self.component_name = self.get_component_name()

    def get_component_name(self) -> str:
        return f"{self.package_name}/de.szalkowski.activitylauncher.entrypoint.MainActivity"

    def launch_app(self) -> bool:
        print(f"=== Launching version {self.release_tag} ({self.component_name}) ===")
        return ensure_app_launched(self.d, self.package_name, self.component_name)

    def dismiss_disclaimer(self):
        print(f"=== Dismissing disclaimer dialog for version {self.release_tag} ===")
        self.d.click(resource_id="android:id/button1", text="OK", wait=2) or self.d.click(text="OK", wait=2)
        save_snapshot(self.d, f"{self.release_tag}_disclaimer_done")

    def search_package(self, target_package: str):
        print(f"=== Searching for {target_package} in version {self.release_tag} ===")
        dismiss_system_prompts(self.d)
        save_snapshot(self.d, f"{self.release_tag}_before_search")
        if self.d.click(resource_id=f"{self.package_name}:id/tiSearch", wait=1.5):
            time.sleep(1)
            dismiss_system_prompts(self.d)
            print(f"Typing {target_package} into search field...")
            adb_shell(f"input text {target_package}")
            time.sleep(2)
            self.d.press("enter")
            time.sleep(1)
            self.d.press("back")
            time.sleep(1)
        save_snapshot(self.d, f"{self.release_tag}_after_search")

    def select_package(self, target_package: str):
        print(f"=== Selecting package {target_package} in version {self.release_tag} ===")
        time.sleep(2)
        dismiss_system_prompts(self.d)
        if not self.d.click(resource_id=f"{self.package_name}:id/tvClass", text=target_package, wait=2, retries=5):
            if not self.d.click(text=target_package, wait=2, retries=5):
                print(f"ERROR: Could not find package {target_package} in list!")
                save_snapshot(self.d, f"{self.release_tag}_error_package_not_found")
                sys.exit(1)
        save_snapshot(self.d, f"{self.release_tag}_after_package_select")

    def select_activity(self, activity_title: str):
        print(f"=== Selecting activity {activity_title} in version {self.release_tag} ===")
        time.sleep(2)
        dismiss_system_prompts(self.d)
        if not self.d.click(resource_id=f"{self.package_name}:id/tvName", text=activity_title, wait=2, retries=5):
            if not self.d.click(text=activity_title, wait=2, retries=5):
                print(f"ERROR: Could not find activity {activity_title} in list!")
                save_snapshot(self.d, f"{self.release_tag}_error_activity_not_found")
                sys.exit(1)
        save_snapshot(self.d, f"{self.release_tag}_after_activity_select")

    def click_create_shortcut(self):
        print(f"=== Clicking Create Shortcut button in version {self.release_tag} ===")
        dismiss_system_prompts(self.d)
        save_snapshot(self.d, f"{self.release_tag}_before_create_shortcut")
        if not self.d.click(resource_id=f"{self.package_name}:id/btCreateShortcut", wait=2, retries=3):
            print("Scrolling down to find Create Shortcut button...")
            self.d.scroll_down()
            time.sleep(1.5)
            save_snapshot(self.d, f"{self.release_tag}_after_swipe")
            if not self.d.click(resource_id=f"{self.package_name}:id/btCreateShortcut", wait=2, retries=3):
                print("ERROR: Could not find Create Shortcut button!")
                save_snapshot(self.d, f"{self.release_tag}_error_btCreateShortcut_not_found")
                sys.exit(1)

    def confirm_pin_shortcut_dialog(self):
        print(f"=== Confirming Pin Shortcut dialog in version {self.release_tag} ===")
        time.sleep(2)
        dismiss_system_prompts(self.d)
        save_snapshot(self.d, f"{self.release_tag}_pin_dialog_check")
        xml = self.d.dump_hierarchy()

        if "Complete action using" in xml or "ResolverActivity" in xml:
            print("ERROR: App selector/ResolverActivity shown during shortcut creation!")
            save_snapshot(self.d, f"{self.release_tag}_error_resolver_activity_in_pin")
            sys.exit(1)

        clicked_pin = False
        for attempt in range(10):
            time.sleep(1.5)
            xml = self.d.dump_hierarchy()
            for match in re.finditer(r'<node ([^>]+)>', xml):
                attr = match.group(1)
                node_text = ""
                tm = re.search(r'text="([^"]*)"', attr)
                if tm: node_text += tm.group(1)
                dm = re.search(r'content-desc="([^"]*)"', attr)
                if dm: node_text += " " + dm.group(1)

                if (re.search(r'(?i)(add|allow|ok|pin)', node_text) or
                    'button1' in attr or 'btn_add' in attr):
                    bounds_match = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', attr)
                    if bounds_match:
                        x1, y1, x2, y2 = map(int, bounds_match.groups())
                        if y1 > 200 and (x2 - x1) > 20:
                            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                            print(f"Pin dialog button '{node_text.strip()}' found at ({cx}, {cy}). Clicking...")
                            adb_shell(f"input tap {cx} {cy}")
                            clicked_pin = True
                            time.sleep(2)
                            break
            if clicked_pin:
                break

        if not clicked_pin:
            print("Fallback pin dialog navigation...")
            save_snapshot(self.d, f"{self.release_tag}_fallback_pin_navigation")
            self.d.press("tab")
            self.d.press("tab")
            self.d.press("enter")
            time.sleep(1.5)

        save_snapshot(self.d, f"{self.release_tag}_after_pin")

    def create_shortcut(self, target_package: str, activity_title: str):
        """Template method orchestrating the shortcut creation flow."""
        self.launch_app()
        self.dismiss_disclaimer()
        self.search_package(target_package)
        self.select_package(target_package)
        self.select_activity(activity_title)
        self.click_create_shortcut()
        self.confirm_pin_shortcut_dialog()


class Version231ShortcutCreator(VersionShortcutCreator):
    """Specific implementation for Release v2.3.1."""

    def get_component_name(self) -> str:
        # In v2.3.1, the activity was de.szalkowski.activitylauncher.MainActivity
        return f"{self.package_name}/de.szalkowski.activitylauncher.MainActivity"


class Version241ShortcutCreator(VersionShortcutCreator):
    """Specific implementation for Release v2.4.1."""

    def get_component_name(self) -> str:
        # In v2.4.1+, the activity was moved to de.szalkowski.activitylauncher.entrypoint.MainActivity
        return f"{self.package_name}/de.szalkowski.activitylauncher.entrypoint.MainActivity"


def get_shortcut_creator(device: UiDevice, release_tag: str) -> VersionShortcutCreator:
    if release_tag.startswith("2.3"):
        return Version231ShortcutCreator(device, release_tag)
    elif release_tag.startswith("2.4"):
        return Version241ShortcutCreator(device, release_tag)
    else:
        return VersionShortcutCreator(device, release_tag)


def find_and_click_shortcut_on_home(d: UiDevice, release_tag: str) -> bool:
    print(f"=== Searching for 'Settings' shortcut on Home screen for upgraded v{release_tag} ===")

    # Reset to home screen page 1
    d.press("home")
    time.sleep(1)
    d.swipe_right()
    d.swipe_right()
    time.sleep(1)

    for page in range(5):
        save_snapshot(d, f"{release_tag}_home_page_{page+1}")
        xml = d.dump_hierarchy()
        for match in re.finditer(r"<node ([^>]+)>", xml):
            attr = match.group(1)
            if any(p in attr for p in ["com.android.systemui", "com.google.android.googlequicksearchbox"]):
                continue

            tm = re.search(r'text="([^"]*)"', attr)
            dm = re.search(r'content-desc="([^"]*)"', attr)
            node_text = (tm.group(1) if tm else "").strip() or (dm.group(1) if dm else "").strip()

            if node_text.lower() == "settings":
                bounds_match = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', attr)
                if bounds_match:
                    x1, y1, x2, y2 = map(int, bounds_match.groups())
                    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                    print(f"Found workspace shortcut '{node_text}' at ({cx}, {cy}) for upgraded v{release_tag}. Clicking...")
                    adb_shell(f"input tap {cx} {cy}")
                    return True

        print(f"Page {page+1}: Shortcut 'Settings' not found on workspace, swiping left...")
        d.swipe_left()
        time.sleep(2)

    # Fallback: Check App Drawer / All Apps
    print("Shortcut not found on workspace pages. Trying App Drawer...")
    d.press("home")
    time.sleep(1)
    d.open_app_drawer()
    time.sleep(2)
    save_snapshot(d, f"{release_tag}_app_drawer")
    xml = d.dump_hierarchy()
    for match in re.finditer(r"<node ([^>]+)>", xml):
        attr = match.group(1)
        if any(p in attr for p in ["com.android.systemui", "com.google.android.googlequicksearchbox"]):
            continue

        tm = re.search(r'text="([^"]*)"', attr)
        dm = re.search(r'content-desc="([^"]*)"', attr)
        node_text = (tm.group(1) if tm else "").strip() or (dm.group(1) if dm else "").strip()

        if node_text.lower() == "settings":
            bounds_match = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', attr)
            if bounds_match:
                x1, y1, x2, y2 = map(int, bounds_match.groups())
                cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                print(f"Found shortcut '{node_text}' in App Drawer at ({cx}, {cy}) for upgraded v{release_tag}. Clicking...")
                adb_shell(f"input tap {cx} {cy}")
                return True

    return False


def test_upgrade_flow_for_version(d: UiDevice, release_tag: str):
    print(f"\n=======================================================")
    print(f" TESTING UPGRADE FROM VERSION {release_tag} TO CURRENT ")
    print(f"=======================================================\n")

    download_dir = f"/tmp/release_{release_tag}_dir"
    tag_debug_apk = f"/tmp/release_{release_tag}_debug.apk"

    print(f"Downloading release asset for tag {release_tag}...")
    run_cmd(
        f"gh release download {release_tag} --repo ActivityLauncher/ActivityLauncher --pattern '*.apk' --dir {download_dir} --clobber"
    )

    downloaded_files = os.listdir(download_dir)
    apk_files = [f for f in downloaded_files if f.endswith(".apk")]
    if not apk_files:
        print(f"ERROR: No APK file found in download directory for tag {release_tag}!")
        sys.exit(1)

    downloaded_apk = os.path.join(download_dir, apk_files[0])
    run_cmd(f"cp {downloaded_apk} {tag_debug_apk}")

    keystore = os.path.expanduser("~/.android/debug.keystore")
    strip_signatures_and_resign(tag_debug_apk, keystore)

    uninstall_all_activitylauncher_packages()

    print(f"Installing version {release_tag}...")
    adb_install_with_retry(tag_debug_apk)

    packages_v1 = check_installed_packages_and_version()
    print(f"Installed Activity Launcher packages (v{release_tag}): {packages_v1}")
    if len(packages_v1) != 1 or PACKAGE_NAME not in packages_v1[0]:
        print(f"ERROR: Expected exactly 1 package ({PACKAGE_NAME}), but found: {packages_v1}")
        save_snapshot(d, f"{release_tag}_error_v1_packages_mismatch")
        sys.exit(1)

    creator = get_shortcut_creator(d, release_tag)
    creator.create_shortcut(TARGET_PACKAGE, "Settings")

    d.press("home")
    time.sleep(2)
    save_snapshot(d, f"{release_tag}_home_screen_before_upgrade")

    print(f"Upgrading in-place from v{release_tag} to current debug version...")
    adb_install_with_retry(NEW_APK_PATH)
    time.sleep(2)

    packages_v2 = check_installed_packages_and_version()
    print(f"Installed Activity Launcher packages (after upgrading v{release_tag}): {packages_v2}")
    if len(packages_v2) != 1:
        print(f"ERROR: Multiple or zero Activity Launcher packages found after upgrading v{release_tag}! Packages: {packages_v2}")
        save_snapshot(d, f"{release_tag}_error_multiple_packages_after_upgrade")
        sys.exit(1)

    d.press("home")
    time.sleep(1)

    shortcut_found = find_and_click_shortcut_on_home(d, release_tag)
    if not shortcut_found:
        print(f"ERROR: Could not locate shortcut on Home screen or App Drawer for upgraded v{release_tag}!")
        save_snapshot(d, f"{release_tag}_error_shortcut_not_found")
        sys.exit(1)

    time.sleep(3)

    save_snapshot(d, f"{release_tag}_before_validation")
    success = False
    for _ in range(5):
        current_pkg = d.current_package()
        print(f"Current package: {current_pkg}")
        if current_pkg == TARGET_PACKAGE:
            success = True
            break
        if current_pkg == "android":
            print("ERROR: App selector/ResolverActivity shown when clicking shortcut!")
            save_snapshot(d, f"{release_tag}_error_app_selector_shown")
            sys.exit(1)
        time.sleep(1)

    save_snapshot(d, f"{release_tag}_after_validation")

    if success or d.current_package() == TARGET_PACKAGE:
        print(f"\n==========================================")
        print(f" SUCCESS: Upgrade test passed for v{release_tag}!")
        print(f"==========================================\n")
    else:
        current_pkg = d.current_package()
        print(f"\n==========================================")
        print(f" FAILURE (v{release_tag}): Expected {TARGET_PACKAGE}, got '{current_pkg}'")
        print(f"==========================================\n")
        sys.exit(1)

    print(f"Cleaning up after v{release_tag} test...")
    uninstall_all_activitylauncher_packages()
    d.press("home")
    time.sleep(2)


if __name__ == "__main__":
    try:
        setup_environment()
        d = UiDevice()
        disable_stylus_and_keyboard_prompts()
        for tag in RELEASE_TAGS:
            test_upgrade_flow_for_version(d, tag)
        print("\n==========================================")
        print(" ALL RELEASE UPGRADE TESTS PASSED 100%! ")
        print(" Tested versions:", RELEASE_TAGS)
        print("==========================================\n")
    except Exception as e:
        print(f"FATAL ERROR in test execution: {e}")
        sys.exit(1)
