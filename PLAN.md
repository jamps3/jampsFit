# jampsFit Development Plan

Living feature plans belong in `docs/plans/`. See `docs/plans/GAMIFICATION-PLAN.md` for the active gamification plan.

## Completed Features
- [x] BLE Scanning and Connection for Kospet TANK M1.
- [x] Real-time Health Data: Battery, Heart Rate, SpO2, Blood Pressure.
- [x] Activity Tracking: Steps and Distance graphs.
- [x] Debug Output and Unknown Packet Logging.
- [x] Multi-tab UI (Home, Graphs, Controls, Remote, Unknown, Settings).
- [x] Background Foreground Service for persistent connection.
- [x] Copy Unknown packets to clipboard.
- [x] **Unknown Packet Persistence**: Persist unknown packets to the database and add a reset button to the Unknown tab.
- [x] Active Phone Control: Media and Camera/Find My Phone.
- [x] **Visual Find My Phone**: Show a full-screen overlay with a stop button when "Find My Phone" is triggered from the watch.
- [x] **Background Prominence**: Use high-priority notifications and full-screen intents to ensure "Find My Phone" UI appears even when the app is in the background or the device is locked.
- [x] Data Persistence: Health data is saved to a local Room database.
- [x] **Graph Persistence**: Load historical health data from the database into the Graphs screen to ensure data persists across app restarts.
- [x] **Blood Pressure Dual-Graph**: Display both Systolic and Diastolic values in a single unified chart with support for single-point markers.
- [x] Battery Monitoring: Graph, configurable low battery alerts, and time-remaining estimation.
- [x] Connection Resilience: Automatic retry (5 times) and notification on disconnect.
- [x] UI Controls: Disconnect button and configurable start/connect behavior.
- [x] Custom Button Mapping: Selectable actions for watch buttons (Media, Volume, Utility).
- [x] OLED-dark UI: true black app background/surfaces with low-glow borders and no animated shine.
- [x] **Sub-tabs for Graphs**: Daily, Weekly, Monthly, and Today stats.
- [x] **Persistent Scroll Position**: Remembers scroll position when changing tabs.
- [x] **Graph Axes and Labels**: Added full X/Y axis markings to all charts.
- [x] **Notification Deduplication**: Ignore duplicate notifications for 30 days.
- [x] **Notification Filtering**: Exclude apps by package name (e.g., AccuBattery).
- [x] **Customizable Borders**: Adjustable thickness, brightness, and color for all UI borders.
- [x] **Start on Boot**: Option to automatically start the service when the phone restarts.
- [x] **Persistent Background Service**: Toggle for a reliable, always-on connection.
- [x] **Last 24h View**: Hourly granularity health stats for the previous 24 hours including Battery, Distance, Sleep, Blood Pressure, and Activity Count.
- [x] **Hourly Calories Graph**: Calculates active burn in 1-hour slices.
- [x] **Non-destructive Database Migrations**: Switched from destructive updates to explicit Room migrations (5 -> 6) and enabled schema exporting for better reliability.
- [x] **Custom Branding**: Integrated jampsFit logo and adaptive launcher icon with OLED optimization.
- [x] **Watch-tab Connection Controls**: Scan/connect, clock sync, battery refresh, and queue clearing live with watch controls instead of App settings.
- [x] **Retire Notification Probes**: Removed dead notification probe controls after they produced no useful watch behavior.
- [x] Remote Measurement: Start/Stop HR, SpO2, and BP measurement from the app.
- [x] Data Export: Save session data to CSV files.
- [x] Notification Mirroring: Push phone notifications (calls, SMS, apps) to the watch from jampsFit's own BLE connection using the confirmed direct `0x41` path with automatic app discovery and friendly name filtering.
- [x] Extended Notifications: Direct `0x41` display is confirmed through 238 bytes; 240 bytes truncates on-watch.
- [x] Precise Time Sync: Big-endian local time synchronization.
- [x] Restore passive Main screen data via no-MTU broad notification listening.
- [x] Decode live `FEE1` walking packets as activity count, distance, and calories.
- [x] Add Logcat mirroring for watch debug logs.
- [x] Add guarded experimental long notification sender from vendor capture.
- [x] Capture Da Fit native connect preamble and identify session-dependent command risk.
- [x] Correct Da Fit handle mapping: handle `0x0047` is `FEE2`, not `6387`, in captured sessions.
- [x] Working Controls tab for confirmed Find My Watch and Alarm writes.
- [x] Confirm Find My Watch through `FEE2`.
- [x] Confirm Alarm 1 and Alarm 3 writes through `FEE2`.
- [x] Confirm Auto-lock through `FEE2`.
- [x] Confirm Time format 12h/24h through `FEE2`.
- [x] Confirm Quick View / wrist raise on/off through `FEE2`.
- [x] Confirm Weather forecast packet `0x42` through `FEE2`.
- [x] Confirm Legacy Short and Legacy Call notification packet formats.
- [x] Confirm short notification push with direct `0x41` through `FEE2`.
- [x] Confirm direct `0x41` notification payloads through 80 text bytes.
- [x] **Gamification V1**: Add Progress-tab daily goal progress, XP levels, step streaks, and deterministic achievements backed by local watch data.

## Planned Features
- [x] **Decode Real Steps**: The 2026-05-22 15:51 Da Fit sync showed current watch-face steps are `total(59 00) + total(59 01)`. In that capture, `6492 + 837 = 7329`, matching the watch face and Da Fit weekly total. `FEE1` remains `activityCount`, not true steps.
- [x] **Manual/Automatic Step Fetch**: Home exposes a Steps play button for the `59 00 + 59 01` fetch, and Controls > App can poll steps automatically at a selected interval.
- [ ] **Stabilize Watch Send Path**: Continue re-verifying captured Da Fit `FE EA 20` commands on `FEE2`; Clock Sync, Find My Watch, and alarms are now stable enough to keep in the Controls tab.
- [ ] **Gamification Expansion**: Continue weekly challenges, quest cards, milestone timeline, personal bests, and avatar ideas in `docs/plans/GAMIFICATION-PLAN.md`.
- [ ] **Reconcile Captures With References**: Compare local phone captures against Gadgetbridge-MT863, Uwatch2 notes, and `_uwatch2ble.py`.
- [ ] **Use Gadgetbridge Moyoung Notes**: Cross-check packet layout and command IDs against https://gadgetbridge.org/internals/specifics/moyoung-protocol/.
- [ ] **Step Query Probes**: Continue validating step history and offsets. Current known results: `59 00 + 59 01` matches current steps; `59 02` is close to the Da Fit daily-screen value but not exact in the 15:51 capture; `59 03`, `33 01`, and `33 02` remain history/page candidates; `33 00` does nothing.
- [ ] **Gadgetbridge-Derived Queries**: Keep `0xB9` advanced-command probes available; `0x21` get alarms, `0x26` get step goal, and `0x8D` get auto-lock are confirmed and now auto-query the controls. `0x64` heartbeat is retired from UI after no visible effect.
- [x] **Unified Controls/Logs UI**: Controls owns watch/app/manual settings; Logs owns Unknown and System Log.
- [ ] **Sleep Tracking**: `20/32` sleep boundaries are now decoded and displayed with internal markers. Continue mapping `01` into Da Fit's Kevyt vs REM distinction and validate `02=Syva`, `00=Hereilla/end` across another sleep capture.
- [ ] **Sleep Score**: Da Fit showed score `40` for the 2026-05-22 sleep. Treat as a future app-calculated feature unless a raw score packet is found.
- [ ] **Heart Rate Retrieval**: Avoid app-origin `0x6D 01` start writes because they reboot this watch. `0x6D` no-payload and `0x6D 00` both trigger visible HR measurement with vibration/display wake, so they are retired from UI. Controls > App now exposes the captured Da Fit auto-HR interval writes (`1F 01` = 5m, `1F 02` = 10m) plus marked interval candidates; live-test whether scheduled HR arrives silently via `2A37` or `FEE3`.
- [ ] **Settings Tests**: Live-test Weather city/current conditions and Step goal from the corrected `FEE2` route.
- [ ] **Weather Current Conditions**: Weather On sends a partly working Joensuu sample sequence. Isolated `0x43`/`0xB5` probes did not move current temp; capture or test complete weather transaction variants.
- [ ] **Da Fit Settings Probes**: Time format (`0x17`) and Quick View (`0x18`) are confirmed; Quick View now uses dedicated Watch > Display commands instead of generic probe buttons. Live-test auto-HR interval (`0x1F`) and move reminder (`0x1D`) controls from the 2026-05-18 Da Fit settings capture.
- [ ] **Da Fit Session Prep**: Test the minimal `84/B4/12/F1` ready cluster before any native Find/Alarm/Weather command is re-enabled.
- [ ] **Replace Da Fit Completely**: Use Da Fit captures only as protocol reference. jampsFit must own the BLE connection and implement notification/control sends itself.
- [ ] **Sending Weather to the Watch**: Push current weather and forecast to the watch.
- [ ] **Extended Reverse Engineering**: Decode more proprietary Kospet packets.
- [ ] **Code Refactoring**: Split large files into modular components.

## Ideas
- [ ] Customizable Watch Faces (if supported).
- [ ] Workout mode tracking.
- [ ] Sleep data sync.
- [ ] Presentation Mode: Use watch to scroll slides/pages.
- [ ] Call Management: Answer/End calls from watch.
- [ ] Smart Home Integration: Trigger Tasker/IFTTT events.
