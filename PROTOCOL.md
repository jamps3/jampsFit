# Kospet TANK M1 Protocol (MoYoung/DaFit)

## Overview
The watch communicates using a proprietary BLE protocol (MoYoung/DaFit). It supports multiple protocol variants (`10` series and `20` series) across different characteristics.

Current status: **passive main-screen data is restored** when the app skips MTU negotiation and broadly subscribes to notify/indicate characteristics. Clock Sync, Find My Watch, and Alarm writes are now confirmed working through `FEE2`. Treat the `6387` send path as session-dependent until verified against full Da Fit connect-preamble captures and live watch responses.

Da Fit notification mirroring is not a product workaround. It proves the watch accepts notifications when Da Fit owns the native session, but jampsFit must replace Da Fit entirely and cannot rely on Da Fit being connected in parallel.

## External References To Cross-Check

These references are useful clues, but they may describe different watches or older protocol variants:

- [krzys-h/Gadgetbridge-MT863](https://github.com/krzys-h/Gadgetbridge-MT863): Gadgetbridge fork described as Da Fit / `MOYOUNG-V2` support for MT863-class watches. Use it as the closest known Android implementation reference, especially for command routing, init sequence, and characteristic selection.
- [kabbi/uwatch2-protocol.md](https://gist.github.com/kabbi/854a541c1a32e15fb0dfa3338f4ee4a9): Uwatch2 reverse-engineering notes. It documents `FE EA 10 [LEN] [CMD]` packets and many command IDs. The packet format matches our working clock sync pattern closely enough to be high-value.
- [rogerdahl/uwatch2-client `_uwatch2ble.py`](https://github.com/rogerdahl/uwatch2-client/blob/master/_uwatch2ble.py): Python BLE client reference for the same Uwatch2 family. Use this as a secondary check for command ordering and BLE write/notify setup, not as proof for Kospet TANK M1.

## Protocol Variants

### Series 10 (Legacy/Sync)
- **Header**: `FE EA 10`
- **Length Byte (Index 3)**: External Uwatch2 notes describe this as `payload length + 4`, which is equivalent to `Total Length - 1`.
- **Checksum**: None observed for basic commands.
- **Main Pipe**: Write to `FEE2`, Notify on `FEE3`.
- **Known-good local behavior**: Clock sync works on `FEE2` using exact-length packets.
- **Live activity path**: `FEE1` notifications stream while walking after broad passive subscription.

### Series 20 (Modern/Da Fit)
- **Header**: `FE EA 20`
- **Length Byte (Index 3)**: Defined as `Total Length` count.
- **Checksum**: Required for longer packets (e.g., Notifications), calculated as `Sum(all bytes) % 256`.
- **Captured Da Fit Pipe**: Write to `FEE2` (ATT handle `0x0047` in current captures), Notify on `FEE3` (ATT handle `0x0049`).
- **Important correction**: Earlier notes treated handle `0x0047` as `6387`. The captured service discovery shows handle `0x0047` is the value handle for `FEE2`; `6387` is a later handle in service `6287`. Many reboots were likely caused by sending Da Fit `FE EA 20` traffic to `6387` instead of `FEE2`.
- **Current risk**: `6387` should not be used for Da Fit `FE EA 20` packets unless a capture proves that exact packet was written to `6387`.

## Passive Listening Findings

The current stable receive setup:

1. Do **not** request MTU before subscribing.
2. Discover services and log every service/characteristic.
3. Subscribe to every notify/indicate characteristic except standard Service Changed `2A05`.
4. Read standard battery `2A19`.
5. Treat incoming packets as the source of truth and log all raw `RX` packets.

Confirmed subscribed channels on Kospet TANK M1 include:

- `2A19`: standard battery read/notify.
- `2A37`: standard heart-rate notify.
- `FEE1`: live walking/activity notify.
- `FEE3`: legacy notify.
- `FEA1`: mirrored activity/health notify. Walking frames observed as `07` plus the same 9-byte payload from `FEE1`.
- `6487`: native MoYoung notify.

### `FEE1` Live Activity Packet

Observed examples:

```text
0A 01 00 DA 00 00 0C 00 00
26 01 00 F1 00 00 0D 00 00
5A 01 00 1C 01 00 0F 00 00
```

Current decode:

| Offset | Size | Meaning | Notes |
| :--- | :--- | :--- | :--- |
| `0` | 1 | Stream sequence/counter | Rolls forward with incoming notifications. |
| `1..2` | 2 LE | `activityCount` | Not steps; currently remains low/slow-moving. Possibly active minutes, motion state, or segment count. |
| `3..4` | 2 LE | Live distance meters | Confirmed by smooth increase while walking. |
| `5` | 1 | Unknown/reserved | Observed `00`. |
| `6..7` | 2 LE | Calories | Confirmed by slow increase while walking. |
| `8` | 1 | Unknown/reserved | Observed `00`. |

`FEA1` mirror examples:

```text
FEE1: 5A 02 00 EE 01 00 1B 00 00
FEA1: 07 5A 02 00 EE 01 00 1B 00 00
```

Known `FEE1` walking frames are kept out of the Unknown tab and logged as decoded activity. The app also suppresses duplicate `FEA1` mirror frames from the System Log and Unknown tab, and avoids duplicate activity updates when the same sequence was already received on `FEE1`. If a mirror arrives with a new sequence, it is still accepted as a fallback activity update and logged.

## Verified Commands

| Header | Length | CMD | Description | Payload / Notes |
| :--- | :--- | :--- | :--- | :--- |
| `10` | `09` | `31` | **Clock Sync** | `[4-byte Big-Endian Local Timestamp] 08` |
| `10` | `04` | `2F` | Query Sensors | Uwatch2 reference lists `2F` as timing HR query; local code uses it as a general data/health query, but this is not yet verified for TANK M1 main-screen data. |
| `10` | `05` | `32` | Sync Sleep | From Uwatch2 outgoing logs. Needs local verification. |
| `10` | `05` | `34` | Query Dynamic HR | From Uwatch2 outgoing logs. Needs local verification. |
| `10` | `06` | `35` | Query HR | Payload examples: `04`, `05`, `06`, `07` in Uwatch2 logs. Needs local verification. |
| `10` | `06` | `59` | Query Steps | Payload examples: `00`, `02` in Uwatch2 logs. This may be a better candidate for restoring main-screen step data than `20 33 01`. |
| `10` | `05` | `0D` | **Find My Watch** | `01 01` (Vibrate trigger) |
| `20` | `0F` | `36` | Memory Sync / Handshake | `00 36 00 DA 01 00 00 11 03 20 31` (Can reboot if sent in live mode) |
| `20` | `06` | `5A 00` | Handshake | Device Info Request ("MOYOUNG-V2") |
| `20` | `06` | `5A 01` | Firmware Info | Firmware Version ("MOY-QGF3-2.0.3") |
| `20` | `0F` | `33 01` | Daily Totals | Steps, Distance, Calories (Little Endian). **Suspect**: local writes may not elicit a response now. |
| `20` | `1E` | `33 04` | Sleep Summary | Total, Deep, Light minutes. **Suspect** until confirmed. |
| `20` | `VAR` | `08` | Notification Push | `[Type] [TitleLen] [Title] [TextLen] [Text] [Checksum]` |
| `20` | `VAR` | `41` | **Notification Push** | Confirmed working for small direct payloads on `FEE2`. Payload starts with `80`, followed by UTF-8 text. Watch displays these as `Other: ...`. Large payload support still needs length-limit testing. |
| `20` | `06` | `B4` | Buffer Allocation | Part of extended data handshake (Params: 00, 12, 10, 20) |
| `20` | `06` | `F1` | Handshake Ready | Final signal before extended data push |
| `20` | `05` | `61` | **Find My Watch** | Confirmed working from jampsFit on `FEE2` / handle `0x0047`. Previous reboot was from wrong characteristic. |
| `20` | `0D` | `11` | **Alarm Record** | Confirmed working for alarm slots 1 and 3 from jampsFit on `FEE2`. Payload: `[slot] [enabled] [mode] [hour] [minute] [extra1] [extra2] [repeatMask]`. |
| `20` | `06` | `7D` | **Auto-lock seconds** | Confirmed working from jampsFit on `FEE2`. Example: `FE EA 20 06 7D 14` for 20 seconds. |
| `20` | `09` | `16` | Step goal | Captured as `FE EA 20 09 16 00 00 23 28` for 9000 steps. Added as a connected-only experimental control; still needs live confirmation. |
| `20` | `1A` | `42` | **Forecast data** | Confirmed working from jampsFit on `FEE2`. Seven triples: `[icon/weatherCode] [high C] [low C]`. First triple updates today's range; next six appear as future forecast days. |
| `20` | `0C` | `45` | Weather city name | Captured as the final city-name packet in the weather sequence. Added as a connected-only experimental control with the captured surrounding weather packets; still needs live confirmation. |

## Current Safety Notes

- `FE EA 10 09 31 [timestamp BE] 08` written to `FEE2` is confirmed good for clock sync.
- `FE EA 20 05 61` written to `FEE2` is confirmed good for Find My Watch.
- `FE EA 20 0D 11 ...` written to `FEE2` is confirmed good for alarm writes; alarm slots 1 and 3 were live-tested.
- Main screen passive data is restored through `FEE1`: `activityCount`, live distance, calories, and standard battery.
- The vendor phone capture confirms `MOYOUNG-V2`, `FEE2`, `FEE3`, `6387`, and `6487` are present. Use `btlog.txt` and `btsnoop_hci.log` as ground truth before promoting any command to verified.
- Real steps are still not decoded from the live `FEE1` packet. Candidate sources remain snapshot packets such as `33`/`59` from captures.
- Handshake, weather writes, step-goal writes, standard notification send, long notification diagnostic send, and any `6387` query bursts are still experimental or unsafe until tested carefully on the correct characteristic.
- Keep packets exact length. Do not pad variable commands to 20 bytes.

## Da Fit Native Session State

Fresh HCI capture with Bluetooth toggled off/on showed that Da Fit sends a large `FE EA 20` preamble on `FEE2` after connect before commands such as Find My Watch. Earlier jampsFit tests sent these packets to `6387`, which is not what Da Fit did in the capture.

Initial minimal ready cluster from earlier connect preamble:

```text
FE EA 20 05 84
FE EA 20 06 B4 00
FE EA 20 06 B4 12
FE EA 20 06 B4 10
FE EA 20 06 B4 20
FE EA 20 09 12 A8 55 29 00
FE EA 20 06 F1 00
```

Live testing showed that the full cluster above reboots the watch when sent by jampsFit, likely because `12 A8...` / `F1` is only safe after additional Da Fit state. A later single-action capture showed the immediate pre-Find cluster excludes those two packets and repeats only:

```text
FE EA 20 05 84
FE EA 20 06 B4 00
FE EA 20 06 B4 12
FE EA 20 06 B4 10
FE EA 20 06 B4 20
```

Live testing showed that this stripped `84/B4` cluster rebooted the watch when sent by jampsFit to `6387`. This result is now considered invalid for judging the Da Fit sequence because the target characteristic was wrong. The prep buttons remain disabled until reintroduced on `FEE2`.

Current staged startup experiments:

```text
Start P1:
FE EA 20 06 5A 00
FE EA 20 06 B7 0E
FE EA 20 0A 31 [local timestamp LE] 08
FE EA 20 07 BB 16 00
FE EA 20 0B BB 07 00 30 2A 00 00
FE EA 20 0B BB 07 00 30 2A 00 00

Start P2:
FE EA 20 08 5A 02 00 00
FE EA 20 07 67 0C 00
FE EA 20 07 67 0D 1E
FE EA 20 06 7B 00
FE EA 20 06 5A 01
```

Test P1 alone first. P2 should only be tried if P1 does not reboot.

Live result: **Start P1 rebooted the watch** when sent to `6387`, so both P1 and P2 are disabled in the app. This result is also considered invalid for judging Da Fit startup because the captured Da Fit traffic was actually on `FEE2`.

Captured action packets. Find My Watch and alarms now work from jampsFit when sent to `FEE2`; weather remains experimental:

```text
Find My Watch: FE EA 20 05 61
Alarm 1 on:   FE EA 20 0D 11 00 01 00 07 0F B5 11 00
Alarm 1 off:  FE EA 20 0D 11 00 00 00 07 0F B5 11 00
Weather city: FE EA 20 0C 45 4A 6F 65 6E 73 75 75
Forecast:     FE EA 20 1A 42 03 0E 07 00 0E 06 03 13 0A 03 10 0C 00 0F 0A 03 0D 09 03 09 07
```

Live jampsFit forecast sample result on 2026-05-17:

```text
Sent: FE EA 20 1A 42 00 1C 12 01 1A 10 02 18 0E 03 16 0C 04 14 0A 05 12 08 06 10 06

Today 2026-05-17: range 28C-18C
2026-05-19: 26C-16C, icon code 01, shown like two lines under a cloud
2026-05-20: 24C-14C, icon code 02, shown like two clouds
2026-05-21: 22C-12C, icon code 03, shown like raining cloud
2026-05-22: 20C-10C, icon code 04, shown like snowing cloud
2026-05-23: 18C-8C, icon code 05, shown like sun
2026-05-24: 16C-6C, icon code 06, shown like tornado/wind
```

The current-day actual temperature (`7C` in the live test) did not come from the `0x42` sample packet. It likely comes from the preceding captured weather/status packet, probably `0x43` or `0xB5`.

Alarm record fields currently decode as:

| Field | Meaning |
| :--- | :--- |
| `slot` | `00..02` for alarm 1..3 |
| `enabled` | `00` off, `01` on |
| `mode` | `00` once/no repeat, `01` every day, `02` custom repeat |
| `hour/minute` | 24-hour alarm time |
| `repeatMask` | `00` once, `3E` weekdays, `7F` every day |

## Handshake Procedures

### Extended Data / Large Notification Handshake (Experimental)

Current jampsFit notification probe buttons:

| Button | Packet family | Notes |
| :--- | :--- | :--- |
| Legacy Short | `FE EA 10` / `0x08` | Short title/text format already used by the notification mirroring code. |
| Legacy Call | `FE EA 10` / `0x08` | Same format with type `0x02`. |
| Type 1/2/3/5 | `FE EA 20` / `0x08` | Da Fit-style native notification candidates without checksum. |
| Csum 1/3 | `FE EA 20` / `0x08` | Same native `0x08` format with one trailing sum checksum byte. |
| Tiny 0x41 | `FE EA 20` / `0x41` | Confirmed working. Displayed on watch as `Other: jampsFit tiny 41`. |

Test one notification button at a time and capture the exact log line. If the watch reboots, the last `Notification probe ... ->` packet is the failing candidate.

Live result: Only the `Tiny 0x41` probe worked. The confirmed packet was:

```text
FE EA 20 16 41 80 6A 61 6D 70 73 46 69 74 20 74 69 6E 79 20 34 31
```

The watch rendered it as:

```text
Other: jampsFit tiny 41
```

Current implication: direct `0x41` on `FEE2` is the best notification path. Next tests should vary only one factor at a time: payload length, subtype byte (`80`), and text encoding/content. Avoid the older B4/F1 prep sequence unless a later capture proves it is required for longer payloads.

Implementation update: jampsFit notification mirroring now formats incoming phone notifications as direct `0x41` messages:

```text
FE EA 20 [len] 41 80 [UTF-8 title/text]
```

The Controls tab includes direct `0x41` length probes at 20, 40, 60, and 80 text bytes. Live result: all tested lengths through 80 bytes display successfully without reboot.

The vendor capture shows long AccuBattery notifications using `6387` / handle `0x0047`. This is now implemented behind the experimental `Exp Notif` button only.

Observed sequence:

1. `FE EA 20 06 B4 00`
2. `FE EA 20 06 B4 12`
3. `FE EA 20 06 B4 10`
4. `FE EA 20 06 B4 20`
5. `FE EA 20 09 12 A8 4B 29 00`
6. `FE EA 20 06 F1 00`
7. Send `0x41` notification packet.

Observed timing between writes was roughly `150-180ms` for the first five writes and about `300ms` before `F1`, not a strict ACK-based flow. The current experimental implementation uses timed delays and exact-length packets.

Captured `0x41` example:

```text
FE EA 20 44 41 80 4E 79 74 3A ... 29
```

Current interpretation:

| Offset | Meaning |
| :--- | :--- |
| `0..2` | Header `FE EA 20` |
| `3` | Total packet length |
| `4` | Command `41` |
| `5` | Notification subtype/flags, observed `80` |
| `6..end` | UTF-8 notification text |

The final byte in the captured sample (`29`) is the ASCII/UTF-8 `)` from the notification text, not proven to be a checksum.

## BLE UUIDs

- **Standard Battery**: `00002a19-0000-1000-8000-00805f9b34fb`
- **Standard Heart Rate**: `00002a37-0000-1000-8000-00805f9b34fb`

### MoYoung Custom Pipes
- **Control (Native Write)**: `00006387-3c17-d293-8e48-14fe2e4da212` (Write No Response / Handle 0x0047)
- **Data (Native Notify)**: `00006487-3c17-d293-8e48-14fe2e4da212`
- **Legacy Write**: `0000fee2-0000-1000-8000-00805f9b34fb`
- **Legacy Notify**: `0000fee3-0000-1000-8000-00805f9b34fb`

## Implementation Notes
- **Timezone**: The watch expects **Local Time** (UTC + Offset) in Big Endian for the timestamp.
- **Stability**: Sending 20-byte padded frames to variable-length endpoints (like 6387) causes immediate firmware reboots. Commands must be sent with their exact length.
- **Debug logging**: App debug log entries are also emitted to Android Logcat under tag `WatchManager`, so `adb logcat -s WatchManager NotificationReceiver` can collect packet logs without manual copy/paste.
- **Threading**: `updateDebugLog()` must synchronize access to its in-memory buffer. BLE callbacks can arrive concurrently and previously caused `ConcurrentModificationException`.
