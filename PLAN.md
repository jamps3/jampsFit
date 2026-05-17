# jampsFit Development Plan

## Completed Features
- [x] BLE Scanning and Connection for Kospet TANK M1.
- [x] Real-time Health Data: Battery, Heart Rate, SpO2, Blood Pressure.
- [x] Activity Tracking: Steps and Distance graphs.
- [x] Debug Output and Unknown Packet Logging.
- [x] Multi-tab UI (Home, Graphs, Remote, Unknown, Settings).
- [x] Background Foreground Service for persistent connection.
- [x] Copy Unknown packets to clipboard.
- [x] Active Phone Control: Media and Camera/Find My Phone.
- [x] Data Persistence: Health data is saved to a local Room database.
- [x] Battery Monitoring: Graph, configurable low battery alerts, and time-remaining estimation.
- [x] Connection Resilience: Automatic retry (5 times) and notification on disconnect.
- [x] UI Controls: Disconnect button and configurable start/connect behavior.
- [x] Custom Button Mapping: Selectable actions for watch buttons (Media, Volume, Utility).
- [x] Sleek UI: Glassmorphism, animated shine effects, and modern layouts.
- [x] Remote Measurement: Start/Stop HR, SpO2, and BP measurement from the app.
- [x] Notification Mirroring: Push phone notifications (calls, SMS, apps) to the watch.
- [x] Extended Notifications: Support for long messages (AccuBattery, WhatsApp) using B4 handshake.
- [x] Precise Time Sync: Big-endian local time synchronization.
- [x] Restore passive Main screen data via no-MTU broad notification listening.
- [x] Decode live `FEE1` walking packets as activity count, distance, and calories.

## Planned Features
- [ ] **Decode Real Steps**: Identify the packet/channel that carries actual step count; `FEE1` field is now named `activityCount`.
- [ ] **Stabilize Watch Send Path**: Re-verify every outbound command; only Clock Sync is confirmed working right now.
- [ ] **Reconcile Captures With References**: Compare local phone captures against Gadgetbridge-MT863, Uwatch2 notes, and `_uwatch2ble.py`.
- [ ] **Sleep Tracking**: Re-verify sync and display of total, deep, and light sleep after send path is fixed.
- [ ] **Find My Watch**: Re-verify vibration command without using risky memory/handshake frames.
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
