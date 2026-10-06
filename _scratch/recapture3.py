import subprocess, time
from pathlib import Path
ADB = r"C:\Users\A.R.I\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "32c08acb0013"
SHOT = Path(r"C:\Users\A.R.I\Desktop\app\mahava\screenshots")
SHOT.mkdir(parents=True, exist_ok=True)

def adb(*args, timeout=30):
    try:
        return subprocess.run([ADB, "-s", DEV, *args], check=False, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        print("TIMEOUT", args, flush=True)
        return None

def force_start(uri):
    adb("shell", "am", "force-stop", "com.mahava.app")
    time.sleep(0.8)
    adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", uri, "com.mahava.app")
    time.sleep(4.5)

def soft_start(uri):
    adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", uri, "com.mahava.app")
    time.sleep(3.5)

def shot(name):
    r = adb("exec-out", "screencap", "-p", timeout=20)
    if r is None or not r.stdout:
        print("FAIL shot", name, flush=True)
        return 0
    data = r.stdout
    if data[:8] != b"\x89PNG\r\n\x1a\n" and b"PNG" in data[:16]:
        data = data.replace(b"\r\n", b"\n")
    (SHOT / f"{name}.png").write_bytes(data)
    print(f"SHOT {name} {len(data)}", flush=True)
    return len(data)

print("SEED", flush=True)
force_start("mahava://qa/seed")
shot("00_seed_rtl")
screens = [
    ("mahava://screen/onboarding", "01_onboarding_welcome"),
    ("mahava://screen/today", "05_today"),
    ("mahava://screen/calendar", "06_calendar"),
    ("mahava://screen/log", "07_log_hub"),
    ("mahava://screen/daily", "08_daily_log"),
    ("mahava://screen/period", "09_period_log"),
    ("mahava://screen/reports", "10_reports"),
    ("mahava://screen/late", "11_late_test"),
    ("mahava://screen/doctor", "12_doctor_report"),
    ("mahava://screen/body", "13_body_home"),
    ("mahava://screen/body_period", "14_body_period_pain"),
    ("mahava://screen/body_cycle", "15_body_cycle_hormones"),
    ("mahava://screen/body_sleep", "16_body_sleep_mood"),
    ("mahava://screen/body_fertility", "17_body_fertility"),
    ("mahava://screen/body_digestion", "18_body_digestion"),
    ("mahava://screen/body_care", "19_body_pattern_care"),
    ("mahava://screen/care", "24_care"),
    ("mahava://screen/settings", "21_settings"),
]
for uri, name in screens:
    print("NAV", name, flush=True)
    soft_start(uri)
    n = shot(name)
    if n < 40000:
        print("RETRY", name, flush=True)
        force_start(uri)
        shot(name)
r = adb("shell", "pidof", "com.mahava.app")
print("PID", (r.stdout or b"").decode().strip() if r else "?", flush=True)
print("DONE", flush=True)
