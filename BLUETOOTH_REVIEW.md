# Bluetooth device review

Open Tools → Bluetooth review, immediately after WhatsApp bot.

Install Dhalam on both Android devices. On Android TV, open **Dhalam device review** from the TV launcher. Bluetooth Classic RFCOMM support is required on both devices; some TVs do not expose it. Use **Pair a device** in Dhalam on the TV to open Android's companion chooser; successful pairing returns directly to Dhalam and starts a fresh sharing code. Make the phone discoverable in its Bluetooth settings first. TVs without the system chooser fall back to **Bluetooth settings**; press the remote's Back button to return to Dhalam, then show a fresh code.

1. On the TV, choose **Show pairing code**.
2. On the phone, choose **Paired devices**, select the TV, enter its six-digit code and choose **Read device**.
3. Approve **Share once** on the TV. The code expires after two minutes.
4. Review the received apps and tap an app for permission details. **Gemini brief** separately asks permission to send selected metadata to Gemini.

The companion collects only after approval and shares once over authenticated, encrypted Bluetooth. It does not change permissions, install apps or control the other device. Leaving the screen or backgrounding cancels a pending transfer. System pairing/permission UI preserves typed input, and configuration changes retain the active session. Opening Settings deliberately ends an old sharing offer; generate a fresh code after returning. There is no background listener.

Android package visibility limits coverage. Transfers contain up to 256 visible apps and 256 requested permissions per app; larger permission lists and inventories are sampled to fit the transfer frame, with explicit shown/total coverage counts. The brief uses at most 100 apps and 12 selected sensitive permission types. Granted access does not prove actual use. Devices without Dhalam cannot expose this inventory merely by pairing.

Gemini uses the existing authenticated backend's /device-report endpoint and its configured Google credentials. The pairing code and Bluetooth address are never submitted for the brief. Local review remains usable if Gemini is unavailable.

Validation requires two physical devices with Bluetooth Classic. A single emulator cannot verify a real phone-to-TV exchange.
