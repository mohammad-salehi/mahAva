import subprocess, time
from pathlib import Path
ADB = r"C:\Users\A.R.I\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "32c08acb0013"
SHOT = Path(r"C:\Users\A.R.I\Desktop\app\mahava\screenshots")
SHOT.mkdir(exist_ok=True)

def adb(*a, timeout=30):
    return subprocess.run([ADB,"-s",DEV,*a], capture_output=True, timeout=timeout)

def force(uri):
    adb("shell","am","force-stop","com.mahava.app")
    time.sleep(0.9)
    adb("shell","am","start","-a","android.intent.action.VIEW","-d",uri,"com.mahava.app")
    time.sleep(5.2)

def shot(name):
    adb("shell","screencap","-p","/sdcard/mh.png")
    dest = str(SHOT / f"{name}.png")
    adb("pull","/sdcard/mh.png", dest)
    n = (SHOT/f"{name}.png").stat().st_size
    print(f"SHOT {name} {n}", flush=True)

force("mahava://qa/seed")
shot("00_seed_sample")
force("mahava://screen/today")
shot("05_today")
# scroll today: can't inject; open phase detail via deep link
force("mahava://screen/phase")
shot("05b_phase_detail")
force("mahava://screen/care_today")
shot("05c_care_detail")
force("mahava://screen/daily")
shot("08_daily_log")
force("mahava://screen/calendar")
shot("06_calendar")
force("mahava://screen/settings")
shot("21_settings")
print("DONE", flush=True)
