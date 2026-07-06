# Kospet TANK M1 Protocol (MoYoung/DaFit)

## Overview
The watch communicates using a proprietary BLE protocol. It supports multiple protocol variants (`10` series and `20` series) across different characteristics.

Current status: **passive main-screen data is restored** when the app skips MTU negotiation and broadly subscribes to notify/indicate characteristics. Clock Sync, Find My Watch, and Alarm writes are now confirmed working through `FEE2`. The watch identifies as `MOYOUNG-V2`, so it is using the Gadgetbridge-documented Moyoung V2 protocol or a very close variant.

## Development Gotchas
- **MTU Negotiation**: **Do NOT call `requestMtu()`**. The watch may hang or reboot. Subscribing to characteristics should be done with the default MTU.
- **Characteristic Routing**: Always use `FEE2` for writes. Sending Series 20 commands to `6387` often causes reboots.
- **Packet Padding**: Do NOT pad packets to 20 bytes. Commands must be sent with their **exact length**.
- **Timing**: When sending sequences (like weather), include a `~180ms` delay between packets to prevent buffer overflow or reboots.

## Packet Anatomy

### Series 10 (Legacy/Sync)
- **Header**: `FE EA 10`
- **Structure**: `[0..2] Header | [3] Length (Total - 1) | [4] Command | [5..] Payload`
- **Checksum**: None observed for basic commands.
- **Main Pipe**: Write to `FEE2`, Notify on `FEE3`.

### Series 20 (Modern/Da Fit)
- **Header**: `FE EA 20`
- **Structure**: `[0..2] Header | [3] Length (Total) | [4] Command | [5..] Payload`
- **Checksum**: Required for longer packets (e.g., Notifications), calculated as `Sum(all bytes) % 256`.
- **Main Pipe**: Write to `FEE2`, Notify on `FEE3`.

---

## Master Command Registry

| Series | CMD | Name | Status | Description / Payload |
|:-------|:----|:-----|:-------|:----------------------|
| 10 | `31` | **Clock Sync** | ✅ Verified | `[4-byte Big-Endian Local Timestamp] 08` |
| 10 | `0D` | **Find My Watch** | ✅ Verified | `01 01` (Vibrate trigger) |
| 20 | `08` | **Legacy Notif** | ✅ Verified | `[Type] [TitleLen] [Title] [TextLen] [Text]`. Type `01`=Short, `02`=Call. |
| 20 | `11` | **Alarm Record** | ✅ Verified | `[slot] [enabled] [mode] [hour] [minute] [month] [day] [repeatMask]` |
| 20 | `16` | **Step Goal** | ✅ Verified | `00 [hi] [lo]` (Big Endian) |
| 20 | `17` | **Time Format** | ✅ Verified | `00` (12h), `01` (24h) |
| 20 | `18` | **Quick View** | ✅ Verified | `00` (Off), `01` (On) |
| 20 | `1F` | **Auto HR** | ✅ Verified | `00` (Off), `01` (5m), `02` (10m), `05` (60m) |
| 20 | `21` | **Get Alarms** | ✅ Verified | Returns 8-byte records per slot. |
| 20 | `26` | **Get Step Goal** | ✅ Verified | Returns `00 00 [hi] [lo]` or `00 [hi] [lo] 00`. |
| 20 | `32` | **Sleep Sync** | ✅ Verified | Payload is 3-byte markers `[state] [hour] [minute]`. |
| 20 | `41` | **Notification** | ✅ Verified | Payload: `80 [UTF-8 text]`. Max text: 238 bytes. |
| 20 | `42` | **Forecast Data** | ✅ Verified | Seven triples: `[icon] [high C] [low C]`. |
| 20 | `45` | **Weather City** | ✅ Verified | UTF-8 city name. |
| 20 | `59` | **Step Buckets** | ✅ Verified | Query `00`, `01` etc. for 16-bit category sums. |
| 20 | `5A` | **Device Info** | ✅ Verified | `00` (Name), `01` (Firmware). |
| 20 | `61` | **Find My Watch** | ✅ Verified | Trigger watch "Finding Phone" UI. |
| 20 | `64` | **Heartbeat** | ✅ Verified | Probable keep-alive / heartbeat (no payload). |
| 20 | `66` | **Shutter Event** | ✅ Verified | **RX only**: Sent when watch shutter button is pressed. |
| 20 | `67` | **Music Control** | ✅ Verified | **RX only**: `01`=Prev, `02`=Next, `06`=Play/Pause. |
| 20 | `6D` | **Heart Rate** | ✅ Verified | RX manual measurement result. |
| 20 | `6B` | **SpO2** | ✅ Verified | RX manual measurement result. |
| 20 | `69` | **Blood Pressure**| ✅ Verified | RX manual measurement result. |
| 20 | `72` | **Quick View Win**| ✅ Verified | `[start H] [start M] [end H] [end M]`. |
| 20 | `7D` | **Auto-lock** | ✅ Verified | `[seconds]` (5-60). |
| 20 | `8D` | **Get Auto-lock** | ✅ Verified | Query current auto-lock duration. |
| 20 | `A4` | **DND / Pwr Save**| ✅ Verified | `00` (Off), `01` (On). |
| 20 | `33` | Daily Totals | 🧪 Exp | Steps, Distance, Calories snapshots. |
| 20 | `B4` | Buffer Alloc | 🧪 Exp | Part of handshake: `00, 12, 10, 20`. |
| 20 | `F1` | Handshake Ready| 🧪 Exp | Final signal before extended data push. |
| 20 | `B9` | Adv. Namespace | 🧪 Exp | Used for Weather/eCard (`19 00` for weather). |

---

## Passive Listening Findings

Confirmed subscribed channels on Kospet TANK M1 include:

- `2A19`: standard battery read/notify.
- `2A37`: standard heart-rate notify.
- `FEE1`: live walking/activity notify.
- `FEE3`: legacy notify. Confirmed to carry manual measurement updates (HR, SpO2, BP) and remote events.
- `FEA1`: mirrored activity/health notify.
- `6487`: native MoYoung notify.

Decoder policy: jampsFit should decode, persist, and surface every watch value once the payload meaning is known. Packets should be filtered out of Unknown only when they are confirmed no-data/control chatter or already handled by a known channel. Recent examples:

- `RX 2A37 raw=00 00 00`: standard heart-rate notification with flags `00` and BPM `00`; this is an idle/no-reading sample, not usable heart-rate data.
- `RX 2A37 raw=00 5E 00`: standard heart-rate notification with flags `00` and BPM `0x5E = 94`; the 2026-07 Dancing exercise capture confirms watch workouts stream usable BPM values through `2A37`.
- `RX FEE3 raw=FE EA 20 06 29 03`: short Series 20 `0x29` packet with one-byte payload `03`; currently treated as known control/ack-style chatter because it carries no mapped health/activity value.

Workout note: the attached Dancing exercise capture lasted 3m48s, burned 15 kcal, and showed 94 average BPM on-watch. The unknown log only contained the `2A37` heart-rate stream, so jampsFit can already decode and persist the BPM samples. No duration, sport type, or calorie summary packet was present in that log; workout summaries remain a future sync target unless another characteristic emits those fields. Until then, the app infers short workout summaries from contiguous `2A37` samples: stream duration, average/min/max BPM, and estimated active calories. During watch workouts, the watch-face step counter may stay flat while streamed distance/calories move; Dancing Event therefore falls back to estimated steps from distance when no explicit step delta is available.

### `FEE1` Live Activity Packet Breakdown

| Offset | Size | Meaning | Notes |
|:-------|:-----|:--------|:------|
| `0` | 1 | Sequence Counter | Rolls forward with notifications. |
| `1..2` | 2 LE | `activityCount` | Possibly movement intensity or segment count. |
| `3..4` | 2 LE | Live distance (m) | Confirmed by smooth increase. |
| `5` | 1 | Reserved | Observed `00`. |
| `6..7` | 2 LE | Calories | Confirmed by slow increase. |
| `8` | 1 | Reserved | Observed `00`. |

---

## Remote Event Details

### Music Control (`0x67`)
Received on `FEE3` when music buttons are pressed on the watch:
- `FE EA 20 06 67 01`: Previous Track
- `FE EA 20 06 67 02`: Next Track
- `FE EA 20 06 67 06`: Play/Pause

### Shutter (`0x66`)
Received on `FEE3` when the watch shutter button is pressed:
- `FE EA 20 05 66`: Shutter event.

---

## Sleep Data Mapping

Sleep markers arrive via `0x32` on `FEE3`. States:
- `00`: Hereillä (Awake)
- `01`: Kevyt (Light/REM provisional)
- `02`: Syvä (Deep)
- `03`: REM (Rarely observed directly)

Internal markers (repeating `01` state) likely indicate movement intensity within a sleep phase.

---

## BLE UUIDs

- **Standard Battery**: `00002a19-0000-1000-8000-00805f9b34fb`
- **Standard Heart Rate**: `00002a37-0000-1000-8000-00805f9b34fb`

### MoYoung Custom Pipes
- **Control (Native Write)**: `0000fee2-0000-1000-8000-00805f9b34fb` (Handle 0x0047)
- **Data (Native Notify)**: `0000fee3-0000-1000-8000-00805f9b34fb` (Handle 0x0049)
- **Alt Control**: `00006387-3c17-d293-8e48-14fe2e4da212` (Use with caution)
- **Alt Data**: `00006487-3c17-d293-8e48-14fe2e4da212`

---

## Implementation Notes
- **Timezone**: The watch expects **Local Time** in Big Endian for the Series 10 timestamp.
- **Persistence**: Local data is stored in Room (`jampsfit_database`).
- **Debugging**: Use `adb logcat -s WatchManager` for packet logs.
