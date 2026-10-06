from pathlib import Path

p = Path(r"C:\Users\A.R.I\Desktop\app\mahava\app\src\main\java\com\mahava\app\ui\screens\TodayScreen.kt")
t = p.read_text(encoding="utf-8")
# Find the body card and move it: if already has comment MOST IMPORTANT, skip
if "MOST IMPORTANT" in t:
    print("today already reordered")
else:
    # Cut body+care cards from lower position and insert after prediction section
    start = t.find("            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                QuickStat")
    body = t.find("            MahavaCard(Modifier.clickable { onPhaseDetail(phaseSel.item.id) })")
    care_end = t.find("            QuietInfo(dayCtx.uncertaintyNoteFa)")
    if start < 0 or body < 0 or care_end < 0:
        print("markers", start, body, care_end)
        raise SystemExit(1)
    # From QuickStat row through care card (exclusive of QuietInfo)
    lower = t[start:care_end]
    # Split lower into quickstat_block and body_care
    body_idx = lower.find("            MahavaCard(Modifier.clickable { onPhaseDetail")
    quick = lower[:body_idx]
    bodycare = lower[body_idx:]
    # Find insertion point: after the prediction if/else block closes — right before QuickStat
    # Insert bodycare before quick
    t2 = t[:start] + "            // MOST IMPORTANT: phase card high on screen\n" + bodycare + quick + t[care_end:]
    p.write_text(t2, encoding="utf-8")
    print("today reordered", len(t), "->", len(t2))

p2 = Path(r"C:\Users\A.R.I\Desktop\app\mahava\app\src\main\java\com\mahava\app\ui\MahavaAppRoot.kt")
t2 = p2.read_text(encoding="utf-8")
if 'path == "phase"' in t2 and "return@LaunchedEffect" in t2[t2.find("phase"):t2.find("phase")+800]:
    print("approot already has phase handle")
else:
    # Replace the when branches for phase
    t2 = t2.replace(
        '"phase" -> Routes.bodyItem(vm.phaseTodaySelection().item.id)\n                    "care_today" -> Routes.bodyItem(vm.careTodayItem().id)\n                    else -> null',
        '"phase", "care_today" -> null\n                    else -> null'
    )
    needle = '            if (route != null) {'
    insert = '''            if (path == "phase" || path == "care_today") {
                if (BuildConfig.DEBUG && state.profile?.onboardingDone != true) {
                    val today = vm.today()
                    vm.updateProfile {
                        it.copy(
                            onboardingDone = true,
                            goal = "track_period",
                            lastPeriodStartEpochDay = today.minusDays(14).toEpochDay(),
                            typicalCycleLength = 28,
                            typicalBleedLength = 5,
                            regularCycles = true,
                            fertilityTrackingEnabled = true,
                            qaSampleData = true
                        )
                    }
                    vm.savePeriod(today.minusDays(14), today.minusDays(10), false)
                    kotlinx.coroutines.delay(700)
                } else {
                    kotlinx.coroutines.delay(400)
                }
                val id = if (path == "phase") vm.phaseTodaySelection().item.id else vm.careTodayItem().id
                nav.navigate(Routes.bodyItem(id))
                return@LaunchedEffect
            }
            if (route != null) {'''
    if needle not in t2:
        print("navigate needle missing")
    else:
        # only first occurrence inside screen host block — replace once
        t2 = t2.replace(needle, insert, 1)
        p2.write_text(t2, encoding="utf-8")
        print("approot phase fixed")
print("phase in file", 'path == "phase"' in p2.read_text(encoding="utf-8"))
