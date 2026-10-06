from pathlib import Path
p = Path(r"C:\Users\A.R.I\Desktop\app\mahava\app\src\main\java\com\mahava\app\reminders\ReminderScheduler.kt")
t = p.read_text(encoding="utf-8")
needle = "NotificationManagerCompat.from(applicationContext).notify(1001, notif)"
if "canNotify" in t:
    print("already fixed")
elif needle not in t:
    print("needle missing")
else:
    replacement = """val canNotify = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            try {
                NotificationManagerCompat.from(applicationContext).notify(1001, notif)
            } catch (_: SecurityException) { }
        }"""
    t = t.replace(needle, replacement)
    p.write_text(t, encoding="utf-8")
    print("fixed")
print("canNotify", "canNotify" in p.read_text(encoding="utf-8"))
