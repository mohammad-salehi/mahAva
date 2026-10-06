# -*- coding: utf-8 -*-
import subprocess, re, sys, time, os
from pathlib import Path

ADB = r"C:\Users\A.R.I\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "32c08acb0013"
SHOT = Path(r"C:\Users\A.R.I\Desktop\app\mahava\screenshots")
SHOT.mkdir(parents=True, exist_ok=True)
LOG = Path(r"C:\Users\A.R.I\Desktop\app\mahava\device_walk.log")

def adb(*args, check=True):
    r = subprocess.run([ADB, "-s", DEV, *args], capture_output=True)
    out = r.stdout.decode("utf-8", "replace")
    err = r.stderr.decode("utf-8", "replace")
    if check and r.returncode != 0:
        print("ADB FAIL", args, err)
    return out, err, r.returncode

def log(msg):
    print(msg)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(msg + "\n")

def shot(name):
    # binary screencap without PowerShell corruption
    p = SHOT / f"{name}.png"
    r = subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"], capture_output=True)
    data = r.stdout
    # some Windows adb add CR before LF in PNG - fix
    if data[:8] != b"\x89PNG\r\n\x1a\n" and b"\x89PNG\r\n" in data[:16]:
        data = data.replace(b"\r\n", b"\n")
    p.write_bytes(data)
    log(f"SHOT {name} {len(data)} bytes")
    return p

def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/mahava_ui.xml")
    out, _, _ = adb("shell", "cat", "/sdcard/mahava_ui.xml")
    return out

def nodes(xml):
    # parse nodes with text/content-desc and bounds
    items = []
    for m in re.finditer(r'<node[^>]+>', xml):
        tag = m.group(0)
        text = re.search(r'text="([^"]*)"', tag)
        desc = re.search(r'content-desc="([^"]*)"', tag)
        bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        clickable = 'clickable="true"' in tag
        if not bounds:
            continue
        x1,y1,x2,y2 = map(int, bounds.groups())
        items.append({
            "text": text.group(1) if text else "",
            "desc": desc.group(1) if desc else "",
            "cx": (x1+x2)//2, "cy": (y1+y2)//2,
            "bounds": (x1,y1,x2,y2),
            "clickable": clickable,
            "raw": tag[:180]
        })
    return items

def find(xml, substr, clickable_only=False):
    for n in nodes(xml):
        blob = n["text"] + " " + n["desc"]
        if substr in blob:
            if clickable_only and not n["clickable"]:
                continue
            return n
    return None

def tap_xy(x,y, wait=0.7):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)

def tap_text(substr, scroll=False, wait=0.8):
    xml = dump()
    n = find(xml, substr)
    if not n and scroll:
        adb("shell", "input", "swipe", "540", "1800", "540", "800", "350")
        time.sleep(0.5)
        xml = dump()
        n = find(xml, substr)
    if not n:
        log(f"NOT FOUND: {substr}")
        # list texts
        texts = [n["text"] for n in nodes(xml) if n["text"]]
        log("VISIBLE: " + " | ".join(texts[:30]))
        return False
    log(f"TAP '{substr}' at {n['cx']},{n['cy']} text='{n['text']}'")
    tap_xy(n["cx"], n["cy"], wait)
    return True

def swipe_up():
    adb("shell", "input", "swipe", "540", "1800", "540", "700", "400")
    time.sleep(0.5)

def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.6)

def pid():
    out,_,_ = adb("shell", "pidof", "com.mahava.app")
    return out.strip()

def crashes_since():
    p = pid()
    if not p:
        return "NO PID - app dead?"
    out,_,_ = adb("logcat", "-d", "--pid", p, "*:E")
    lines = [l for l in out.splitlines() if "AndroidRuntime" in l or "FATAL" in l or "Exception" in l or "mahava" in l.lower()]
    return "\n".join(lines[-40:])

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv)>1 else "dump"
    if cmd == "dump":
        xml = dump()
        for n in nodes(xml):
            if n["text"] or n["desc"]:
                print(f"{n['text']!r} / {n['desc']!r} @ {n['cx']},{n['cy']} click={n['clickable']}")
    elif cmd == "tap":
        tap_text(sys.argv[2], scroll=True)
    elif cmd == "shot":
        shot(sys.argv[2])
    elif cmd == "pid":
        print(pid())
    elif cmd == "crash":
        print(crashes_since())
