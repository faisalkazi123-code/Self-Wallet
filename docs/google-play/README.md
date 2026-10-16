# Publishing Self Wallet on Google Play

Everything in this folder is ready to copy into the Play Console. Steps marked **(you)** need your Google account – nobody else can do them.

## 0. Before you start – the SMS permission decision ⚠️

Google Play only lets an app use `READ_SMS` / `RECEIVE_SMS` if it is the default SMS app **or** Google approves a **Permissions Declaration**. Self Wallet uses SMS for its core feature (turning bank SMS into expenses), which falls under Google's *"SMS-based money management"* use case, but approval is **not guaranteed** and the app is rejected if the declaration is refused. The text for that form is in [`sms-permission-declaration.md`](sms-permission-declaration.md).

If Google refuses, the fallback is a Play version that reads bank messages from **notifications** instead of SMS (no restricted permission). Ask for it and it can be built as a separate "play" flavor.

## 1. Developer account (you)

1. Go to https://play.google.com/console and sign up (one-time US$25 fee).
2. Choose **Personal** account and complete identity verification.
3. **New personal accounts must run a closed test with at least 12 testers for 14 days** before production. Gather 12 Gmail addresses (friends/colleagues) now.

## 2. Create your private upload key (you, once)

On any computer with Java installed:

```bash
keytool -genkeypair -v -keystore selfwallet-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 selfwallet-upload.jks > upload.b64      # macOS: base64 -i selfwallet-upload.jks -o upload.b64
```

Keep `selfwallet-upload.jks` and its passwords safe (password manager + a second copy). In GitHub open **Settings › Secrets and variables › Actions** and add:

| Secret | Value |
|---|---|
| `PLAY_KEYSTORE_BASE64` | contents of `upload.b64` |
| `PLAY_KEYSTORE_PASSWORD` | keystore password |
| `PLAY_KEY_ALIAS` | `upload` |
| `PLAY_KEY_PASSWORD` | key password |

The next build then produces **`SelfWallet-google-play-aab`** under *Actions › latest run › Artifacts*. Download and unzip it to get the `.aab` file. (The GitHub APK keeps its current key, so existing installs still update.)

## 3. Create the app in Play Console (you)

1. **Create app** → name *Self Wallet: Expense Tracker*, default language English, **App**, **Free**.
2. Package name is fixed in the bundle: `com.selfwallet.app`.
3. Turn on **Play App Signing** (default) when you upload the first bundle.

## 4. Fill in "App content" (copy from this folder)

| Console section | Use |
|---|---|
| Privacy policy | `https://github.com/faisalkazi123-code/Self-Wallet/blob/claude/mobile-expense-sms-tracker-ze0gxj/PRIVACY.md` (the repository must be public) |
| App access | "All functionality is available without special access" |
| Ads | No ads |
| Content rating | Questionnaire → category *Utility/Productivity*; answer **No** to everything → rated Everyone |
| Target audience | 18+ |
| Data safety | [`data-safety.md`](data-safety.md) |
| Financial features | "My app doesn't provide any financial features" → choose **Personal finance: budgeting/expense tracking** if listed; no loans, no payments |
| Sensitive permissions → SMS | [`sms-permission-declaration.md`](sms-permission-declaration.md) + a demo video |
| Government app / health / news | No |

## 5. Store listing

Text: [`store-listing.md`](store-listing.md). Graphics in [`graphics/`](graphics):

- `icon-512.png` – app icon (512×512)
- `feature-graphic.png` – 1024×500
- **Phone screenshots (you):** 4–8 screenshots from your phone (Home, Activity, Add expense with Personal/Corporate, Reports, Settings). Use demo data (Settings › Hide amounts off) and don't show real account numbers.

## 6. Testing → Production

1. **Testing › Closed testing** → create track → upload the `.aab` → add your 12+ testers' emails → roll out.
2. Testers must opt in via the link and keep the app installed for 14 days.
3. Then **Production › Apply for production access**, answer the questionnaire, and roll out.

Review usually takes a few days; SMS declarations can take longer.

## Updating later

Every push to the default branch builds a new bundle with a higher version code. Upload it in Play Console › Production › Create new release.
