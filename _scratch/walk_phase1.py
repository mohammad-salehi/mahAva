# -*- coding: utf-8 -*-
import ui_walk as u
import time

LOG = []
def step(msg):
    print(msg)
    LOG.append(msg)

u.adb("logcat", "-c")
# Ensure app foreground
u.adb("shell", "am", "start", "-n", "com.mahava.app/.MainActivity")
time.sleep(2)

# === ONBOARDING ===
u.shot("01_onboarding_welcome")
step("tap start")
assert u.tap_text("شروع کنیم"), "start btn"
time.sleep(1)
u.shot("02_onboarding_cycle")

# Cycle info - tap continue (maybe need scroll)
if not u.tap_text("ادامه", scroll=True):
    # try dump
    xml = u.dump()
    open("ui_cycle.xml","w",encoding="utf-8").write(xml)
    raise SystemExit("no continue on cycle")
time.sleep(1)
u.shot("03_onboarding_conditions")

# Select regular if needed, then continue
u.tap_text("منظم")
time.sleep(0.3)
if not u.tap_text("ادامه", scroll=True):
    u.tap_text("فعلاً رد می‌کنم")
time.sleep(1)
u.shot("04_onboarding_history")

# Enter app
assert u.tap_text("ورود به برنامه", scroll=True), "enter"
time.sleep(2)
u.shot("05_today")
open("ui_today.xml","w",encoding="utf-8").write(u.dump())

# Check pid
p = u.pid()
step("PID after onboarding: " + p)
cr = u.crashes_since()
open("crash_so_far.txt","w",encoding="utf-8").write(cr or "(none)")
step("done phase1")
