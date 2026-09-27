# Church Youth POS — Android app

A native Android app for the youth business — smocha, eggs, and smokies.
Everything runs on the Sunmi V2 Pro terminal itself: no server, no
XAMPP, no Wi-Fi dependency for daily operation. Install one APK and it
works, including with no internet at all.


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
