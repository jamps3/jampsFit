# Kospet TANK M1 Protocol (MoYoung/DaFit)

## Overview
The watch communicates using a proprietary BLE protocol (MoYoung/DaFit). It supports multiple protocol variants (`10` series and `20` series) across different characteristics.

Current status: **passive main-screen data is restored** when the app skips MTU negotiation and broadly subscribes to notify/indicate characteristics. Clock Sync is the only confirmed safe outbound watch command. Treat the rest of the send path as suspect until verified against captures from the vendor phone app and live watch responses.

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

### Series 20 (Modern/Native)
- **Header**: `FE EA 20`
- **Length Byte (Index 3)**: Defined as `Total Length` count.
- **Checksum**: Required for longer packets (e.g., Notifications), calculated as `Sum(all bytes) % 256`.
- **Main Pipe**: Write to `6387` (Handle 0x0047), Notify on `6487`.
- **Current risk**: Sending bursts or handshake-like commands on `6387` can reboot the watch. Avoid `6387` writes unless testing one exact packet at a time.

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
| `20` | `VAR` | `41` | **Extended Notification**| Large payload support (requires B4 sequence) |
| `20` | `06` | `B4` | Buffer Allocation | Part of extended data handshake (Params: 00, 12, 10, 20) |
| `20` | `06` | `F1` | Handshake Ready | Final signal before extended data push |

## Current Safety Notes

- `FE EA 10 09 31 [timestamp BE] 08` written to `FEE2` is the only confirmed good outbound command.
- Main screen passive data is restored through `FEE1`: `activityCount`, live distance, calories, and standard battery.
- The vendor phone capture confirms `MOYOUNG-V2`, `FEE2`, `FEE3`, `6387`, and `6487` are present. Use `btlog.txt` and `btsnoop_hci.log` as ground truth before promoting any command to verified.
- Real steps are still not decoded from the live `FEE1` packet. Candidate sources remain snapshot packets such as `33`/`59` from captures.
- Handshake, Find My Watch, standard notification send, long notification send, and `6387` query bursts are disabled/unsafe until tested carefully.
- Keep packets exact length. Do not pad variable commands to 20 bytes.

## Handshake Procedures

### Extended Data / Large Notification Handshake (Experimental)

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
