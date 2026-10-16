# SMS permissions declaration (Play Console › App content › Sensitive app permissions)

**Permissions:** `READ_SMS`, `RECEIVE_SMS`

**Core functionality / use case to select:** *SMS-based money management* (budgeting / expense tracking from transaction SMS). If that exact option isn't listed, choose the closest finance/money-management option and explain below.

**Description of core functionality (paste):**

> Self Wallet is a personal expense tracker whose core feature is automatically recording the user's spending from the transaction SMS their bank and card issuers send (for example "Rs 250 debited from A/c XX1234 at Swiggy"). RECEIVE_SMS is used to create an expense record the moment a bank transaction SMS arrives; READ_SMS is used once, at the user's request, to import past bank transaction messages so the user starts with their history. Messages are parsed entirely on the device; non-financial messages (personal chats, OTPs, promotions) are ignored and not stored. The app has no INTERNET permission, no account and no analytics, so SMS content can never leave the device. Without SMS access the app's main purpose – automatic expense tracking – does not work; users can still add expenses manually.

**Is the permission used only while the app is the default handler?** No – it is the core feature.

**Demo video (required):** record 30–60 s on your phone with screen recording:
1. Open Self Wallet → onboarding explains SMS use → tap *Allow SMS access* → system permission dialog.
2. Send yourself (or use the emulator's SMS panel) a sample bank SMS, e.g. `AED 45.50 spent on your Credit Card ending 5678 at CARREFOUR DUBAI`.
3. Show the notification and the transaction appearing on Home.
4. Show Settings explaining that data stays on the device.

Upload it to YouTube as **Unlisted** and paste the link.
