import subprocess, time
from pathlib import Path
ADB = r"C:\Users\A.R.I\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "32c08acb0013"
SHOT = Path(r"C:\Users\A.R.I\Desktop\app\mahava\screenshots")

def adb(*args, timeout=25):
    return subprocess.run([ADB,"-s",DEV,*args], check=False, capture_output=True, timeout=timeout)

def force_start(uri):
    adb("shell","am","force-stop","com.mahava.app")
    time.sleep(0.9)
    adb("shell","am","start","-a","android.intent.action.VIEW","-d",uri,"com.mahava.app")
    time.sleep(5.0)

def shot(name):
    # pull via sdcard to avoid exec-out issues
    adb("shell","screencap","-p","/sdcard/mh.png")
    dest = str(SHOT / f"{name}.png")
    adb("pull","/sdcard/mh.png", dest)
    n = (SHOT/f"{name}.png").stat().st_size
    print(f"SHOT {name} {n}", flush=True)
    return n

force_start("mahava://qa/seed")
shot("00_seed_rtl")
for uri,name in [
 ("mahava://screen/onboarding","01_onboarding_welcome"),
 ("mahava://screen/today","05_today"),
 ("mahava://screen/calendar","06_calendar"),
 ("mahava://screen/log","07_log_hub"),
 ("mahava://screen/daily","08_daily_log"),
 ("mahava://screen/period","09_period_log"),
 ("mahava://screen/reports","10_reports"),
 ("mahava://screen/late","11_late_test"),
 ("mahava://screen/doctor","12_doctor_report"),
 ("mahava://screen/body","13_body_home"),
 ("mahava://screen/body_period","14_body_period_pain"),
 ("mahava://screen/body_cycle","15_body_cycle_hormones"),
 ("mahava://screen/body_sleep","16_body_sleep_mood"),
 ("mahava://screen/body_fertility","17_body_fertility"),
 ("mahava://screen/body_digestion","18_body_digestion"),
 ("mahava://screen/body_care","19_body_pattern_care"),
 ("mahava://screen/care","24_care"),
 ("mahava://screen/settings","21_settings"),
]:
    force_start(uri)
    shot(name)
print("DONE", flush=True)
