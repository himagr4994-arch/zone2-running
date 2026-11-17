#!/usr/bin/env python3
"""
Smoke test on a real Android emulator: install an APK, walk through first-run setup, open the main
screens, start and discard a live run, and fail if the app crashes anywhere along the way.

Usage: smoke.py <apk> <name>     Screenshots are written to shots/<name>-*.png.
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

PKG = "app.zone2running"
apk, name = sys.argv[1], sys.argv[2]
os.makedirs("shots", exist_ok=True)
step_no = 0


def adb(*a, check=False, timeout=120):
    r = subprocess.run(["adb", *a], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise SystemExit(f"adb {' '.join(a)} failed: {r.stderr or r.stdout}")
    return r.stdout


def shot(label):
    global step_no
    step_no += 1
    with open(f"shots/{name}-{step_no:02d}-{label}.png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)


def nodes():
    """Every visible piece of text on screen with the centre of its box."""
    why = ""
    for _ in range(40):
        why = adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("shell", "cat", "/sdcard/ui.xml") if "dumped" in why.lower() else ""
        if xml.strip().startswith("<?xml"):
            out = []
            for n in ET.fromstring(xml).iter("node"):
                label = n.get("text") or n.get("content-desc") or ""
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
                if label and m:
                    x0, y0, x1, y1 = map(int, m.groups())
                    out.append((label, (x0 + x1) // 2, (y0 + y1) // 2))
            # A slow emulator sometimes says a system app has hung. That is the machine, not this app: wait and look again.
            if any("isn't responding" in t for t, _, _ in out):
                for t, x, y in out:
                    if t == "Wait":
                        adb("shell", "input", "tap", str(x), str(y))
                time.sleep(8)
                continue
            return out
        time.sleep(2)
    fail(f"could not read the screen: {why.strip()[:200]}")


def texts():
    return [t for t, _, _ in nodes()]


def has(label):
    return any(label.lower() in t.lower() for t in texts())


def tap(label, last=False, must=True):
    def find():
        seen = nodes()
        return [(t, x, y) for t, x, y in seen if t.lower() == label.lower()] or [(t, x, y) for t, x, y in seen if label.lower() in t.lower()]
    hits = find()
    if not hits and must:                         # it may be below the fold: scroll once and look again
        adb("shell", "input", "swipe", "500", "1600", "500", "700", "300"); time.sleep(1.2)
        hits = find()
    if not hits:
        if must:
            fail(f"nothing to tap called '{label}'. On screen: {texts()[:40]}")
        return False
    _, x, y = hits[-1] if last else hits[0]
    if y > SCREEN_H - 170:                        # reported under the system's own buttons: bring it up first, or the tap lands on Home
        adb("shell", "input", "swipe", "500", "1500", "500", "800", "300"); time.sleep(1.2)
        hits = find() or hits
        _, x, y = hits[-1] if last else hits[0]
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.2)
    alive(f"after tapping '{label}'")
    return True


def alive(where):
    if not adb("shell", "pidof", PKG).strip():
        fail(f"the app is not running {where}")
    if "FATAL EXCEPTION" in adb("logcat", "-d", "-s", "AndroidRuntime:E"):
        fail(f"the app crashed {where}")


def expect(label, where):
    for attempt in range(8):
        if has(label):
            return
        if attempt in (3, 5):                     # it may be below the fold
            adb("shell", "input", "swipe", "500", "1600", "500", "600", "300")
        time.sleep(1)
    fail(f"expected '{label}' {where}. On screen: {texts()[:40]}")


def fail(msg):
    shot("FAILED")
    print(f"\nFAILED [{name}]: {msg}\n")
    print(adb("logcat", "-d", "-s", "AndroidRuntime:E")[-6000:])
    sys.exit(1)


time.sleep(20)          # let a freshly booted emulator settle
adb("shell", "input", "keyevent", "82")
SCREEN_H = int(re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).group(2))
adb("uninstall", PKG)
adb("install", "-r", apk, check=True, timeout=300)
for p in ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS"]:
    adb("shell", "pm", "grant", PKG, f"android.permission.{p}")
adb("logcat", "-c")
adb("emu", "geo", "fix", "78.0", "20.0")
adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity", check=True)
time.sleep(4)
alive("after launch")
shot("intro")

# First run: Dil's intro, then the setup questions. Tap whichever answer is on screen until the tabs appear.
answers = ["Skip", "Start training", "I understand", "Not now", "Running regularly", "8 weeks", "Sunday", "Next"]
for i in range(30):
    seen = texts()
    if any(t == "Plan" for t in seen) and any(t == "Progress" for t in seen):
        break
    pick = next((a for a in answers if any(a.lower() in t.lower() for t in seen)), None)
    if pick is None:
        # The button may be below the fold: scroll down and look again.
        adb("shell", "input", "swipe", "500", "1700", "500", "500", "300"); time.sleep(1)
        seen = texts(); pick = next((a for a in answers if any(a.lower() in t.lower() for t in seen)), None)
        if pick is None:
            fail(f"setup is stuck. On screen: {seen[:40]}")
    if pick == "Start training":
        shot("plan-preview")
    tap(pick)
else:
    fail("setup never finished")
print(f"[{name}] setup finished")
shot("today")

tap("Plan"); shot("plan")
tap("Progress"); shot("progress")
tap("Today")

# A live run: briefing, GPS meter, start, the full-screen view, pause, discard.
tap("Start run")
expect("GPS signal", "on the briefing screen")
shot("briefing")
adb("shell", "input", "swipe", "500", "1500", "500", "500", "300")
time.sleep(1)
tap("Start run", last=True)
expect("Pause", "on the live run screen")
for i in range(10):
    adb("emu", "geo", "fix", str(78.0 + i * 0.00006), "20.0")      # about 6 m every 2 s: a believable jog
    time.sleep(2)
alive("during the live run")
shot("live")
tap("Pause")
expect("Finish", "after pausing")
shot("paused")
tap("Discard this run")
expect("Plan", "after discarding the run")
alive("after the live run")
shot("back-home")
# A free run: no plan, no blocks.
tap("Free run")
expect("GPS signal", "on the free run briefing")
adb("shell", "input", "swipe", "500", "1500", "500", "500", "300")
time.sleep(1)
tap("Start run", last=True)
expect("Pause", "on the free run screen")
time.sleep(6)
alive("during the free run")
shot("free-run")
tap("Pause")
tap("Discard this run")
expect("Plan", "after discarding the free run")
shot("after-free-run")
# Log a run by hand, then make its share picture and its animated video and pull both off the emulator.
tap("Log it instead")
for label, value in [("Distance", "6.2"), ("Time", "41:30"), ("Average HR", "141")]:
    for attempt in range(4):                      # the keyboard can cover the lower fields: scroll them into view
        if tap(label, must=attempt == 3):
            break
        adb("shell", "input", "swipe", "500", "900", "500", "450", "300"); time.sleep(1)
    adb("shell", "input", "text", value)
    time.sleep(.6)
adb("shell", "input", "keyevent", "4")          # close the keyboard
time.sleep(1)
if not tap("Save run", must=False):
    adb("shell", "input", "swipe", "500", "1500", "500", "700", "300"); time.sleep(1); tap("Save run")
expect("Share", "on the screen after logging a run")
shot("logged")
tap("Share")
expect("Share this run", "on the share screen")
time.sleep(4)
shot("share-picker")
tap("Save picture")
time.sleep(4)
alive("after saving the picture")
tap("Save video")
video = ""
for i in range(120):                              # a software encoder on a slow emulator can take a couple of minutes
    video = adb("shell", "ls", "/sdcard/Movies/Zone 2 Running/").strip()
    if video.endswith(".mp4") and "No such" not in video:
        break
    alive("while making the video")
    time.sleep(3)
else:
    fail(f"no video appeared. On screen: {texts()[:30]}")
time.sleep(3)
adb("pull", f"/sdcard/Movies/Zone 2 Running/{video.splitlines()[0]}", f"shots/{name}-video.mp4")
for pic in adb("shell", "ls", "/sdcard/Pictures/Zone 2 Running/").split("\n"):
    if pic.strip().endswith(".png"):
        adb("pull", f"/sdcard/Pictures/Zone 2 Running/{pic.strip()}", f"shots/{name}-picture.png")
size = os.path.getsize(f"shots/{name}-video.mp4") if os.path.exists(f"shots/{name}-video.mp4") else 0
if size < 20000:
    fail(f"the video is only {size} bytes")
print(f"[{name}] video made: {size} bytes")
alive("after making the video")
shot("video-done")
print(f"[{name}] PASSED")
