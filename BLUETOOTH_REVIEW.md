# Bluetooth device review

Open Tools → Bluetooth review, immediately after WhatsApp bot.

Install Dhalam on both Android devices. On Android TV, open **Dhalam device review** from the TV launcher. Bluetooth Classic RFCOMM support is required on both devices; some TVs do not expose it. Pair the devices in Android's Bluetooth settings first.

1. On the TV, choose **Show pairing code**.
2. On the phone, choose **Paired devices**, select the TV, enter its six-digit code and choose **Read device**.
3. Approve **Share once** on the TV. The code expires after two minutes.
4. Review the received apps and tap an app for permission details. **Gemini brief** separately asks permission to send selected metadata to Gemini.

The companion collects only after approval and shares once over authenticated, encrypted Bluetooth. It does not change permissions, install apps or control the other device. Leaving the screen or backgrounding cancels a pending transfer. There is no background listener.

Android package visibility limits coverage. Transfers contain up to 256 visible apps and 256 requested permissions per app; oversized transfers fail safely. The brief uses at most 100 apps and 12 selected sensitive permission types. Granted access does not prove actual use. Devices without Dhalam cannot expose this inventory merely by pairing.

Gemini uses the existing authenticated backend's /device-report endpoint and its configured Google credentials. The pairing code and Bluetooth address are never submitted for the brief. Local review remains usable if Gemini is unavailable.

Validation requires two physical devices with Bluetooth Classic. A single emulator cannot verify a real phone-to-TV exchange.
