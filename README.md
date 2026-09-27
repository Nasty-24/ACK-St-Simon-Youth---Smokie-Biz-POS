# Church Youth POS — Android app

A native Android app for the youth business — smocha, eggs, and smokies.
Everything runs on the Sunmi V2 Pro terminal itself: no server, no
XAMPP, no Wi-Fi dependency for daily operation. Install one APK and it
works, including with no internet at all.

This replaces the earlier PHP/MySQL web version. The reasoning for the
rewrite: that version needed a separate computer running XAMPP, the two
devices had to share a Wi-Fi network, and printing went through a
WebView bridge. All of that is gone — one app, one device, direct
printer access.

## What changed from the web version, and why

| Web version | This version | Why |
|---|---|---|
| PHP + MySQL on a separate computer | SQLite, on-device | No server to install, no network dependency |
| XAMPP setup, `.htaccess`, PDO | One APK install | Removes the whole "Step 1" everyone got stuck on |
| WebView + JS bridge to print | Direct AIDL calls to the printer | Fewer moving parts, faster, more reliable |
| Needs Wi-Fi between till and server | Fully offline | Works even if the kiosk has no signal |

Every business rule carries over exactly: cost price is snapshotted per
sale so profit reports stay accurate after a price change, credit sales
grow a customer's balance by the unpaid portion, CSRF-equivalent
protections aren't needed (there's no network request to forge), but
password hashing, account lockout, and role-based access all still
apply.

## Before you build

Just **Android Studio** (free, from developer.android.com). Nothing to
download from Sunmi by hand.

Printer access comes from Sunmi's own library, already declared in
`app/build.gradle`:

```gradle
implementation 'com.sunmi:printerlibrary:1.0.24'
```

It resolves from Maven Central on first Gradle sync. The AAR bundles the
printer interfaces internally and picks the correct ones for whatever
Sunmi model the app runs on.

This replaces the older approach of copying `IWoyouService.aidl` and
`ICallback.aidl` into the project by hand. The reason to prefer the
library is the failure mode: a wrong version number here fails at
**Gradle sync**, on your laptop, with a message naming the problem. A
hand-copied AIDL file that doesn't match the device's firmware compiles
perfectly fine and then fails at **runtime**, at the kiosk, with a
cryptic binder error and nothing pointing at the cause.

If sync ever can't find the library, check
https://mvnrepository.com/artifact/com.sunmi/printerlibrary for the
current version and bump the number — that's the entire fix.

## Build & install

1. Open the `church-youth-pos-android` folder directly in Android
   Studio — let it sync (downloads Gradle, AndroidX, and the Sunmi
   printer library; needs internet the first time only).
2. Connect the Sunmi V2 Pro over USB. Enable USB debugging first:
   Settings → About → tap "Build number" seven times, then Settings →
   Developer options → USB debugging.
3. Click **Run**. Android Studio installs and launches the app.
4. From then on, the youth on till duty just taps the app icon.

No further setup — the database, the printer connection, everything is
created automatically the first time the app runs.

## First login

```
Username: admin
Password: ChangeMe123!
```

**Change this immediately** from Staff → tap the admin row → Reset
password. Then give every cashier their own login — shared logins make
the accountability trail (Reports, and the internal `audit_log` table)
useless.

## What was actually verified before shipping this

I don't have an Android emulator in the environment I built this in, so
I couldn't compile the final APK myself. To compensate, before writing
the UI I:

- Validated the exact SQL schema and every report query against a real
  SQLite database via the `sqlite3` command line tool, using the same
  cash-sale/credit-sale/payment scenario tested on the original web
  version — the profit math matched to the cent.
- Wrote and ran the password-hashing logic as a plain Java program
  (outside Android) to confirm it hashes, verifies, salts uniquely, and
  rejects wrong passwords correctly.
- Checked Android's own API-level documentation for every library call
  this app depends on, because the Sunmi V2 Pro runs an old Android
  version (7.1 / API 25) that's easy to accidentally target
  incorrectly:
  - `PBKDF2WithHmacSHA256` needs API 26+ — this app uses
    `PBKDF2WithHmacSHA1` instead, with a higher iteration count.
  - `java.util.Base64` also needs API 26+ — this app uses
    `android.util.Base64` instead.
  - `java.time.*` needs API 26+ (or extra desugaring config) — this app
    uses `Calendar`/`SimpleDateFormat` throughout instead.
  - `List.sort(Comparator)` needs API 24+ — confirmed safe at exactly
    this app's minimum.
- Ran a static cross-check script over every Java file confirming every
  `R.id` / `R.layout` / `R.color` / `R.drawable` / `R.style` reference
  resolves to a real resource, and every internal import points to a
  file that exists.
- Confirmed `com.sunmi:printerlibrary` is published on Maven Central
  with real artifacts (so the existing `mavenCentral()` declaration
  resolves it with no extra repository), and that 1.0.24 is current —
  older guides still quote 1.0.13.

The printer code is the part I'd check first on a real device. I wrote
it against Sunmi's documented `SunmiPrinterService` API, but since I
couldn't compile against the actual AAR, a method signature could differ.
It's all in one file (`util/PrinterHelper.java`) and the rest of the app
doesn't depend on its internals, so a fix there is contained.

What I couldn't verify without an actual build: that the project
compiles cleanly end-to-end in Gradle, and that the UI looks right on a
real screen. If Android Studio reports an error on first sync/build,
it's most likely a small thing — paste me the error and I'll fix it
immediately.

## Backing up the data

Since everything lives in one file on the device, losing the device
means losing the records unless you back up. The database file sits at
the app's private storage (`church_pos.db`); a "Backup" export feature
(sharing it out via email/Drive/WhatsApp) is scaffolded via the
FileProvider entry in the manifest but not yet wired to a button — ask
if you'd like that added.

## Project layout

```
app/src/main/java/co/churchyouth/pos/
├── data/     DbHelper + one DAO per table (UserDao, ProductDao, CustomerDao,
│             SaleDao, PaymentDao, SettingsDao, AuditDao)
├── model/    Plain data classes (User, Product, Customer, Sale, SaleItem,
│             Payment, CartLine)
├── util/     Money (cents-based math), PasswordUtil, PrinterHelper,
│             SessionManager
└── ui/       One package per screen (login, dashboard, pos, checkout,
              receipt, customers, products, users, reports), plus
              ui/common/BaseActivity shared by every screen after login
```

## Roles

- **Cashier** — sell, manage customers and payments, view reports
  (revenue only, no cost/profit figures).
- **Admin** — everything a cashier can do, plus product prices/costs,
  staff accounts, and full profit figures.
