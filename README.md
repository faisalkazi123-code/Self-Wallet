# Self Wallet

**A free, private Android expense tracker that fills itself in from your bank SMS.**

Self Wallet reads the debit/credit messages your bank and card issuers already send you and turns them into transactions automatically: amount, currency, merchant and card. You decide whether each expense is **personal** or **corporate** (and exactly where it goes: yourself, family, your company, a client). Everything stays on your phone.

<p align="center">
  <a href="https://github.com/faisalkazi123-code/Self-Wallet/releases/latest/download/SelfWallet.apk"><b>⬇️ Download the latest APK</b></a>
</p>

---

## Features

### Core
| | |
|---|---|
| 📩 **SMS auto-capture** | Bank/card SMS become transactions instantly: amount, currency, merchant, card. OTPs, promotions, reminders and failed payments are ignored. Import past SMS from your inbox too. |
| 🏢 **Personal & corporate** | Big Personal / Corporate toggle, your own destinations (Family, ACME Corp, Client X…), swipe right in Activity to flip, or tap *Personal / Corporate* right on the SMS notification. |
| 💱 **Multiple currencies** | 40+ currencies detected; totals converted to your base currency with offline exchange rates you control. |
| ⚠️ **Duplicate detection** | Same SMS twice, or same amount at the same merchant/card within 15 minutes: you get an alert to check with your bank before anything is double-counted. |
| 🧠 **Learns as you go** | “Remember for this merchant” turns your choices into auto-categorise rules. |

### Corporate & money control
| | |
|---|---|
| 🎯 **Monthly budgets** | Overall, personal/corporate or per category, with alerts at 80% and 100%. |
| 🧾 **Receipt photos** | Snap or pick a receipt for any expense; stored privately on the phone. |
| 💼 **Claim tracker** | Not claimed → Submitted → Paid back for corporate expenses; “to claim” total on Home and bulk actions. |
| 📄 **PDF expense report** | One tap: summary, itemised table and receipt pages, with your name, for any period, company or trip. CSV export too. |
| 🔒 **App lock** | Fingerprint / face / screen lock; the app preview is hidden in recent apps. |

### Smarter SMS
| | |
|---|---|
| 🏦 **Balances & limits** | “Avl Bal” / “Avl Limit” from SMS shown per account and card on Home. |
| 📅 **Bills & subscriptions** | Card bill reminder SMS are saved; Netflix, rent and other repeating payments are detected. Reminders 2 days before and on the day. |
| 🚨 **Unusual spending** | Alert when a payment is far larger than you normally spend. |
| 👥 **Split expenses** | Split with friends, see who owes you, send a reminder message, mark as settled. |

### Insights & quick entry
| | |
|---|---|
| 📊 **Trends** | Month-over-month change, 6-month chart, spending calendar, category & destination breakdowns. |
| 🗓️ **Monthly summary** | A notification on the 1st with last month's total, top category and amount still to claim. |
| 🧩 **Home-screen widget** | Today's and this month's spending with a quick “+” button. |
| ⚡ **Fast entry** | Long-press the app icon → *Add expense*, voice entry (“Lunch 45 dirhams corporate”) and one-tap templates. |

### Practical extras
| | |
|---|---|
| ✈️ **Travel mode** | Create a trip; expenses in those dates are tagged automatically and can go to a client. Reports per trip. |
| 👀 **Shared ledger** | Open a colleague's backup file (e.g. from a shared OneDrive folder) read-only. |
| 🎨 **Two styles** | *Calm Teal* or *Paper Ledger*, each in light and dark. |
| ☁️ **Your storage** | Private database on the phone, with optional auto-backup to a file on the phone, Google Drive or OneDrive. **No INTERNET permission, no account, no ads.** |

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
