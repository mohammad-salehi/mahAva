import subprocess, time
from pathlib import Path
ADB = r"C:\Users\A.R.I\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "32c08acb0013"
SHOT = Path(r"C:\Users\A.R.I\Desktop\app\mahava\screenshots")
SHOT.mkdir(parents=True, exist_ok=True)

def run(args):
    return subprocess.run(args, check=False, capture_output=True)

def force_start(uri):
    run([ADB, "-s", DEV, "shell", "am", "force-stop", "com.mahava.app"])
    time.sleep(1.0)
    r = run([ADB, "-s", DEV, "shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", uri, "com.mahava.app"])
    print("start", uri, r.stdout.decode(errors="ignore")[:200].replace("\n"," "))
    time.sleep(5.0)

def soft_start(uri):
    r = run([ADB, "-s", DEV, "shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", uri, "com.mahava.app"])
    print("soft", uri, r.stdout.decode(errors="ignore")[:200].replace("\n"," "))
    time.sleep(4.5)

def shot(name):
    data = subprocess.check_output([ADB, "-s", DEV, "exec-out", "screencap", "-p"])
    # strip CR if Windows mangled
    if data[:8] != b"\x89PNG\r\n\x1a\n" and b"\x89PNG" in data[:20]:
        data = data.replace(b"\r\n", b"\n")
    path = SHOT / f"{name}.png"
    path.write_bytes(data)
    print(f"SHOT {name} {len(data)}")
    return len(data)

force_start("mahava://qa/seed")
shot("00_seed_rtl")

# soft navigations after seed so Room data stays
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
    soft_start(uri)
    n = shot(name)
    if n < 50000:
        print("retry with force", name)
        force_start(uri)
        shot(name)

pid = subprocess.check_output([ADB, "-s", DEV, "shell", "pidof", "com.mahava.app"]).decode().strip()
print("PID", pid)
