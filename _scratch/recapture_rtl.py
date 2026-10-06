import subprocess, time
from pathlib import Path
ADB = r"C:\Users\A.R.I\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "32c08acb0013"
SHOT = Path(r"C:\Users\A.R.I\Desktop\app\mahava\screenshots")
SHOT.mkdir(parents=True, exist_ok=True)

def open_uri(uri):
    subprocess.run([ADB, "-s", DEV, "shell", "am", "force-stop", "com.mahava.app"], check=False)
    time.sleep(0.7)
    subprocess.run([ADB, "-s", DEV, "shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", uri, "com.mahava.app"], check=False)
    time.sleep(4.0)

def shot(name):
    data = subprocess.check_output([ADB, "-s", DEV, "exec-out", "screencap", "-p"])
    path = SHOT / f"{name}.png"
    path.write_bytes(data)
    print(name, len(data))

open_uri("mahava://qa/seed")
shot("00_seed_rtl")

screens = [
    ("mahava://screen/onboarding", "01_onboarding_welcome"),
    ("mahava://screen/today", "05_today"),
    ("mahava://screen/calendar", "06_calendar"),
    ("mahava://screen/daily", "08_daily_log"),
    ("mahava://screen/reports", "10_reports"),
    ("mahava://screen/body", "13_body_home"),
    ("mahava://screen/late", "11_late_test"),
    ("mahava://screen/care", "24_care"),
    ("mahava://screen/settings", "21_settings"),
]
for uri, name in screens:
    open_uri(uri)
    shot(name)

pid = subprocess.check_output([ADB, "-s", DEV, "shell", "pidof", "com.mahava.app"]).decode().strip()
print("PID", pid)
