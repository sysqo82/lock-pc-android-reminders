# Lock PC – Reminders App for Android

A simple companion app for **Lock PC**. It puts the reminders you set on the dashboard onto someone's phone – things like "homework before games" or "take the bins out" – and pops up a notification when they're due.

## What it does

- **Shows today's reminders** in a clear list.
- **Sends notifications** when a reminder is due.
- **Mark as done** – tick reminders off, and the dashboard sees they've been completed.
- **Keeps itself up to date** – reminders sync automatically, and the app starts again by itself when the phone is restarted.
- **Shares the phone's location** with the Lock PC account, so it can be seen on a map in the admin app.

## Getting started

1. Install the app and log in with the Lock PC account.
2. Allow notifications and location when asked.
3. Reminders added on the dashboard will start appearing.

## The Lock PC family

| Project | What it is |
|---|---|
| [lock-pc](https://github.com/sysqo82/lock-pc) | The Windows app that does the locking |
| [lock-pc-local](https://github.com/sysqo82/lock-pc-local) | The server and web dashboard, run from home |
| [lock-pc-android](https://github.com/sysqo82/lock-pc-android) | Phone app for managing schedules on the go |
| **lock-pc-android-reminders** | Phone app that receives reminders (this one) |
| [lock-pc-server](https://github.com/sysqo82/lock-pc-server) | The earlier cloud-hosted version of the server |

## How it's built

- **Language:** Kotlin, native Android.
- **Talking to the server:** **Retrofit** and **OkHttp**, using these endpoints on [lock-pc-local](https://github.com/sysqo82/lock-pc-local):
  - `GET /api/reminders` – fetch reminders
  - `POST /api/reminders/sync` – report which ones are done
  - `POST /api/location/update` – send the phone's location
- **Location:** **Google Play Services** location.
- **Running in the background:** foreground services and **WorkManager**, plus a start-up receiver so it restarts after the phone reboots.
- **Security:** login details kept in Android's **encrypted storage**.

### Permissions it asks for

Internet, location, notifications, running in the background, and starting when the phone turns on.

## Building it yourself

1. Open the project in **Android Studio** and let it sync.
2. By default it points at `http://10.0.2.2:3000` (your computer, as seen from the Android emulator). Change this to your server's address for a real phone.
3. Press **Run**, then allow the permissions when asked.
