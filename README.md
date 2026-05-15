# jampsFit ⌚

A sleek, feature-rich Android companion application for the **Kospet TANK M1** smartwatch. Built with modern Android technologies (Jetpack Compose, Room, Kotlin Coroutines), this app serves as both a health dashboard and a powerful tool for reverse-engineering proprietary smartwatch protocols.

## ✨ Key Features

### 📊 Real-Time Health & Activity
- **Live Dashboard**: Monitor Battery, Steps, Heart Rate, SpO2, and Blood Pressure at a glance.
- **Dynamic Trends**: Real-time multi-line graphs for all health metrics with smooth animations and area-glow effects.
- **Battery Intelligence**: High-resolution discharge graph and time-remaining estimation based on current usage.

### 🎮 Watch-to-Phone Remote
- **Unified Triggers**: Handles both physical button presses and wrist-shake events.
- **Custom Mapping**: Configure watch buttons to control:
    - **Media**: Play/Pause, Next, Previous tracks.
    - **Volume**: Adjust system volume or toggle mute.
    - **Utility**: Toggle Flashlight, trigger Google Assistant, or take Screenshots.
- **Find My Phone**: Trigger a high-volume alarm and vibration directly from your wrist.

### 🛠️ Reverse Engineering Toolkit
- **Live Debug Log**: Full visibility into the BLE communication lifecycle (GATT operations, service discovery, notifications).
- **Unknown Packet Sniffer**: Dedicated tab for capturing and displaying unrecognized raw hex data from the watch.
- **Easy Export**: Long-press any log to copy captured packets to the clipboard for further analysis.

### 🛡️ Reliability & Background Support
- **Persistent Connection**: Uses an Android Foreground Service to maintain the link even when the app is in the background or the screen is off.
- **Auto-Reconnect**: Intelligent retry logic (5 attempts with incremental delay) to recover from Bluetooth dropouts.
- **Autostart on Boot**: Optionally starts the connection service as soon as your phone finishes booting.
- **Data Persistence**: All health metrics are automatically saved to a local Room database for long-term history.

## 🎨 Design
- **Modern UI**: Material 3 design with a custom "Glassmorphism" aesthetic.
- **Visual Effects**: Hardare-accelerated "shine" animations on dashboard cards and sleek top-border highlights.
- **Responsive**: Fully supports orientation changes without losing connection or UI state.

## 🚀 Technical Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room Persistence Library
- **Background**: Android Services (Foreground)
- **Communication**: Bluetooth Low Energy (BLE) / GATT
- **Concurrency**: Kotlin Coroutines & Flow
- **Build System**: Gradle (Version Catalog & KSP)

## 📋 Roadmap
- [ ] **Notification Mirroring**: Push SMS, calls, and app alerts directly to the watch screen.
- [ ] **Data Export**: Export collected health history to CSV/JSON files.
- [ ] **Workout Sync**: Track specific exercise sessions with GPS data.

---
*Developed with ❤️ for the Kospet TANK M1 community.*
