#!/usr/bin/env python3
"""Real authenticated process termination/reboot checks; refuses physical devices.

Run after connectedDebugAndroidTest on a disposable emulator. Test configuration
is synthetic. No production component exposes a test PIN or authentication bypass.
"""
import json
import pathlib
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.nexvary.veil"
ACTIVITY = PACKAGE + "/.VeilLauncherActivity"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
FIXTURE = PACKAGE + ".LifecycleFixtureTest"
OUT = pathlib.Path("ui-proof")
OUT.mkdir(exist_ok=True)
report = {"scenarios": [], "passed": False}


def adb(*args, timeout=30, check=True):
    return subprocess.run(["adb", *args], capture_output=True, text=True,
                          timeout=timeout, check=check).stdout.strip()


def wait_for(predicate, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(.5)
    raise AssertionError("Lifecycle precondition timed out")


def hierarchy(name):
    adb("shell", "uiautomator", "dump", "/sdcard/veil-lifecycle.xml")
    xml = adb("shell", "cat", "/sdcard/veil-lifecycle.xml")
    (OUT / (name + ".xml")).write_text(xml)
    return ET.fromstring(xml)


def visible_text(root):
    return {node.get("text", "") for node in root.iter("node")}


def launch_concealed(name):
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    root = hierarchy(name)
    text = visible_text(root)
    assert "Private Canary" not in text, "Private identity survived process restart"
    assert "Veil Control Center" not in text, "Authenticated controls survived restart"
    assert "Set up profiles" not in text, "Encrypted configuration was lost"
    assert not any("Configuration unavailable" in value for value in text), "Configuration could not decrypt"
    assert {"Calculator", "Notes", "Clock", "Settings"}.issubset(text), "Concealed home failed to render"
    return root


def instrument(method, prepare=False):
    args = ["adb", "shell", "am", "instrument", "-w", "-r", "-e", "class",
            FIXTURE + "#" + method, "-e", "veil.lifecycle", "true"]
    if prepare:
        args += ["-e", "prepare", "true"]
    return args + [RUNNER]


def hold(name, prepare=False):
    adb("shell", "rm", "-f", "/data/local/tmp/veil-lifecycle-ready")
    log = (OUT / (name + "-instrumentation.txt")).open("w")
    process = subprocess.Popen(instrument("holdAuthenticatedSession", prepare), stdout=log, stderr=log)
    try:
        wait_for(lambda: adb("shell", "ls", "/data/local/tmp/veil-lifecycle-ready", check=False).endswith("veil-lifecycle-ready"))
        # The running fixture owns UiAutomation until force-stop/reboot. Read its
        # snapshot instead of launching a second automation client that kills it.
        xml = adb("shell", "cat", "/data/local/tmp/veil-lifecycle-authenticated.xml")
        (OUT / (name + "-authenticated.xml")).write_text(xml)
        assert "Private Canary" in visible_text(ET.fromstring(xml)), "Fixture was never authenticated"
        return process, log
    except BaseException:
        process.terminate()
        log.close()
        raise


def verify_reauthentication(name):
    result = subprocess.run(instrument("reauthenticateWithoutReplacingConfiguration"),
                            capture_output=True, text=True, timeout=90)
    (OUT / (name + "-reauthentication.txt")).write_text(result.stdout + result.stderr)
    assert result.returncode == 0 and "OK (1 test)" in result.stdout, "Stored PIN could not reauthenticate"


def run():
    assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Use a disposable emulator, never a physical/customer device"
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk", timeout=90)
    adb("install", "-r", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk", timeout=90)
    adb("shell", "cmd", "role", "add-role-holder", "--user", "0", "android.app.role.HOME", PACKAGE)
    process, log = hold("force-stop", prepare=True)
    pid_before = adb("shell", "pidof", PACKAGE)
    assert pid_before
    adb("shell", "am", "force-stop", PACKAGE)
    process.wait(timeout=30)
    log.close()
    assert adb("shell", "pidof", PACKAGE, check=False) != pid_before, "Authenticated process survived force-stop"
    launch_concealed("after-force-stop")
    assert adb("shell", "pidof", PACKAGE) != pid_before, "Process was reused"
    verify_reauthentication("force-stop")
    report["scenarios"].append({"name": "authenticated-force-stop", "passed": True})

    process, log = hold("reboot")
    boot_before = adb("shell", "cat", "/proc/sys/kernel/random/boot_id")
    adb("reboot")
    adb("wait-for-device", timeout=120)
    wait_for(lambda: adb("shell", "cat", "/proc/sys/kernel/random/boot_id", check=False) not in ("",boot_before)
             and adb("shell", "getprop", "sys.boot_completed", check=False) == "1", seconds=120)
    process.wait(timeout=30)
    log.close()
    assert adb("shell", "cat", "/proc/sys/kernel/random/boot_id") != boot_before, "Device never rebooted"
    adb("shell", "settings", "put", "system", "screen_off_timeout", "2147483647")
    adb("shell", "svc", "power", "stayon", "true")
    launch_concealed("after-reboot")
    verify_reauthentication("reboot")
    report["scenarios"].append({"name": "authenticated-reboot", "passed": True})
    report["passed"] = True


try:
    run()
except BaseException as error:
    report["error"] = str(error)
    raise
finally:
    (OUT / "lifecycle-result.json").write_text(json.dumps(report, indent=2))
