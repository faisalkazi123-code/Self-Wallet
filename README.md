# Self Wallet

**A free, private Android expense tracker that fills itself in from your bank SMS.**

Self Wallet reads the debit/credit messages your bank and card issuers already send you and turns them into transactions automatically: amount, currency, merchant and card. You decide whether each expense is **personal** or **corporate** (and exactly where it goes: yourself, family, your company, a client). Everything stays on your phone.

<p align="center">
  <a href="https://github.com/faisalkazi123-code/Self-Wallet/releases/latest/download/SelfWallet.apk"><b>⬇️ Download the latest APK</b></a>
</p>

---

## Features

| | |
|---|---|
| 📩 **SMS auto-capture** | New bank/card SMS are parsed the moment they arrive and added as transactions. OTPs, promotions, reminders and failed payments are ignored. You can also import past SMS from your inbox (30 days to everything); duplicates are skipped. |
| 🏢 **Personal & corporate** | Create your own "Expense for" destinations (e.g. *Personal*, *Family*, *ACME Corp*, *Client X trip*), each marked Personal or Corporate. Home, reports and CSV exports split spending between them, so expense claims are easy. |
| 💱 **Multiple currencies** | 40+ currencies are detected from SMS (₹, Rs, AED, SAR, $, €, £, KWD, …). Each transaction keeps its original currency, and totals are converted to your base currency using exchange rates you set (they're stored offline). |
| 🧠 **Learns as you go** | Categorise an SMS transaction once and tick *Remember*: future messages from that merchant get the same category and destination. You can also manage rules by hand. |
| 📊 **Reports** | Monthly and yearly charts, breakdowns by category, by destination and by currency, and a **CSV export** for any filter (e.g. "ACME Corp, October"). |
| 🔒 **Private by design** | The app has **no INTERNET permission**. No account, no ads, no analytics. You can also hide amounts on screen and in notifications. |
| ☁️ **Your storage, your choice** | Data lives in a private database on the phone. Optionally keep an auto-updated backup file **on the phone, in Google Drive or in OneDrive**. The file is written through Android's file picker and uploaded by the Drive/OneDrive app, never by Self Wallet. |
| 🎨 **Modern UI** | Material 3 design, light/dark theme, optional Material You colours, swipe to delete with undo, search and filters. |

## Install

1. On your Android phone (Android 8.0+), open the **[latest release](https://github.com/faisalkazi123-code/Self-Wallet/releases/latest)** and download `SelfWallet.apk`.
2. Open the file. If Android asks, allow your browser or file manager to **install unknown apps**.
3. Launch **Self Wallet** and follow the 4-step setup: pick a base currency, allow SMS access, and choose where to keep your data.

> **"Restricted setting" when allowing SMS?** On Android 13+ apps installed from outside the Play Store need one extra tap: *Settings › Apps › Self Wallet › ⋮ (top-right) › Allow restricted settings*. Then grant SMS permission again.

**Updating:** install a newer APK over the old one. Your data is kept.

## Backups and changing phones

* **Settings › Your data**: tap *Google Drive* or *OneDrive*, then *Create new file*. In the system picker, open ☰ and choose Drive/OneDrive (the app must be installed and signed in). The backup updates automatically after changes and once a day.
* **New phone:** install Self Wallet, go to *Settings › Your data*, tap Drive/OneDrive → *Use existing file*, pick `self-wallet-backup.json` and choose *Restore & sync*.
* You can also restore any backup file manually with *Restore from a backup file*.

## How SMS detection works

The parser (`app/src/main/java/com/selfwallet/app/sms/SmsParser.kt`) is conservative. A message is only used when it contains:

1. an amount next to a currency (`Rs.250`, `AED 45.50`, `150.000 KWD`, `$12.99`, …). Balances and credit limits are skipped, and
2. a transaction verb (`debited`, `spent`, `paid`, `purchase`, `withdrawn`, `credited`, `received`, `refund`, …),

and does **not** look like an OTP, promotion, payment reminder or failed transaction. The merchant comes from phrases like *at …*, *to VPA …*, *from …*. If no merchant is found, the sender ID is used. Unit tests with real-world style messages live in `app/src/test`.

If your bank's format isn't recognised, please open an issue with an example SMS (**remove personal numbers first**).

## Build it yourself

Requirements: JDK 17 and the Android SDK (or just Android Studio).

```bash
./gradlew testDebugUnitTest   # run parser tests
./gradlew assembleRelease     # APK in app/build/outputs/apk/release/
```

Every push runs the **Build APK** GitHub Action. Pushes to the default branch publish a new GitHub Release with the APK attached.

### Signing

Release APKs are signed with the bundled public `app/community.keystore` so that every APK from this repository can upgrade the previous one. If you fork the project and want your own private key, add these repository secrets and the workflow will use them instead:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 your.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |

(Switching keys means users must uninstall the old build once, so back up first.)

## Tech

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · WorkManager · kotlinx.serialization. No network libraries, no Google Play Services.

## License

MIT. Free to use, modify and share, with no restrictions. See [LICENSE](LICENSE).
