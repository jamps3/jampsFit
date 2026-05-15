# Kospet TANK M1 Protocol (MoYoung/DaFit)

## Overview
The watch communicates using a proprietary BLE protocol (MoYoung/DaFit). It supports multiple protocol variants (`10` series and `20` series) across different characteristics.

## Protocol Variants

### Series 10 (Legacy/Sync)
- **Header**: `FE EA 10`
- **Length Byte (Index 3)**: Defined as `Total Length - 1` (Last Index).
- **Checksum**: None observed for basic commands.
- **Main Pipe**: Write to `FEE2`, Notify on `FEE3`.

### Series 20 (Modern/Native)
- **Header**: `FE EA 20`
- **Length Byte (Index 3)**: Defined as `Total Length` count.
- **Checksum**: Required for longer packets (e.g., Notifications), calculated as `Sum(all bytes) % 256`.
- **Main Pipe**: Write to `6387` (Handle 0x0047), Notify on `6487`.

## Verified Commands

| Header | Length | CMD | Description | Payload / Notes |
| :--- | :--- | :--- | :--- | :--- |
| `10` | `09` | `31` | **Clock Sync** | `[4-byte Big-Endian Local Timestamp] 08` |
| `10` | `04` | `2F` | Query Sensors | Triggers HR/Health update |
| `20` | `0F` | `36` | **Find My Device** | `00 36 00 DA 01 00 00 11 03 20 31` (Fixed 15-byte frame) |
| `20` | `06` | `5A 00` | Handshake | Device Info Request ("MOYOUNG-V2") |
| `20` | `06` | `5A 01` | Firmware Info | Firmware Version ("MOY-QGF3-2.0.3") |
| `20` | `0F` | `33 01` | Daily Totals | Steps, Distance, Calories (Little Endian) |
| `20` | `1E` | `33 04` | Sleep Summary | Total, Deep, Light minutes |
| `20` | `VAR` | `08` | Notification Push | `[Type] [TitleLen] [Title] [TextLen] [Text] [Checksum]` |

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
