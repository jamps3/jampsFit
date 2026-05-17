# jampsFit Development Plan

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
- [x] Sleek UI: Glassmorphism, animated shine effects, and modern layouts.
- [x] Remote Measurement: Start/Stop HR, SpO2, and BP measurement from the app.
- [ ] Notification Mirroring: Push phone notifications (calls, SMS, apps) to the watch from jampsFit's own BLE connection using the confirmed direct `0x41` path.
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
- [x] Confirm Weather forecast packet `0x42` through `FEE2`.
- [x] Confirm short notification push with direct `0x41` through `FEE2`.
- [x] Confirm direct `0x41` notification payloads through 80 text bytes.

## Planned Features
- [x] **Decode Real Steps**: `59 00` contains Steps Down, Steps Up, and a third step component; the app now computes Total Steps as `Steps Up + Steps Down + Steps Other`. `FEE1` remains `activityCount`, not true steps.
- [ ] **Stabilize Watch Send Path**: Continue re-verifying captured Da Fit `FE EA 20` commands on `FEE2`; Clock Sync, Find My Watch, and alarms are now stable enough to keep in the Controls tab.
- [ ] **Reconcile Captures With References**: Compare local phone captures against Gadgetbridge-MT863, Uwatch2 notes, and `_uwatch2ble.py`.
- [ ] **Use Gadgetbridge Moyoung Notes**: Cross-check packet layout and command IDs against https://gadgetbridge.org/internals/specifics/moyoung-protocol/.
- [ ] **Step Query Probes**: Continue validating step history and offsets. `59 00` gives current Total Steps from Steps Up + Steps Down + Steps Other; native `59 01`/`59 02` returned zero, native `59 03` returned only tail candidate values, and legacy `10/59 xx` collapses back to native `59 00`. `33 00` gives no visible reply, while `33 01` and `33 02` return stable daily-total-shaped values and likely represent day offsets; compare them against the watch history UI.
- [ ] **Gadgetbridge-Derived Queries**: Keep `0x64` heartbeat and `0xB9` advanced-command probes available; `0x21` get alarms, `0x26` get step goal, and `0x8D` get auto-lock are confirmed and now auto-query the controls.
- [x] **Unified Controls/Logs UI**: Controls owns watch/app/manual settings; Logs owns Unknown and System Log.
- [ ] **Sleep Tracking**: Re-verify sync and display of total, deep, and light sleep after send path is fixed.
- [ ] **Settings Tests**: Live-test Weather city/current conditions and Step goal from the corrected `FEE2` route.
- [ ] **Weather Current Conditions**: Isolated `0x43`/`0xB5` probes did not move current temp; capture or test complete weather transaction variants.
- [ ] **Da Fit Session Prep**: Test the minimal `84/B4/12/F1` ready cluster before any native Find/Alarm/Weather command is re-enabled.
- [ ] **Replace Da Fit Completely**: Use Da Fit captures only as protocol reference. jampsFit must own the BLE connection and implement notification/control sends itself.
- [ ] **Data Export**: Save session data to CSV files.
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
