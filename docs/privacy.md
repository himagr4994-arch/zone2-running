# Privacy

Zone 2 Running has no account, no server of its own, no analytics and no trackers. Your runs and settings never leave the phone unless you export or share them yourself.

## Where your data lives

- Runs and settings are one JSON file in the app's private storage on your phone.
- If you choose a backup folder, the app writes one dated copy a day there and keeps the newest seven.
- Export and Restore read and write a file you pick.
- Sharing a run hands one picture to the app you choose in the share sheet. Nothing is shared unless you tap Share.

## When the app goes online

Never, unless you switch one of these on. Both are off by default.

| Feature | What is sent | To whom |
|---|---|---|
| Weather | The coordinates of the town you typed, a few times a day. The town name once, to look the coordinates up. | Open-Meteo |
| Street map | Requests for the map squares around a route. The tile server sees which area is being viewed and your internet address. Tiles are cached on the phone. | OpenStreetMap |
| Update check | A request for the latest release of this app. | GitHub |

No run, heart rate, name or device identifier is included in either request.

## Location

GPS is used while a live run is in progress, and on the screen before it to show signal strength. The route of a live run is stored on the phone with the run and in backups you make. It is never sent anywhere.

## Permissions

| Permission | Why |
|---|---|
| Health Connect: read exercise, heart rate, distance | Import runs recorded by other apps. Read-only. |
| Health Connect: read speed, steps, calories, elevation gained, resting heart rate, exercise routes (optional) | Extra detail on each run page: pace through the run, cadence, calories, climb. Read-only. The app works without them. |
| Location | Measure distance during a live run |
| Foreground service | Keep a live run going with the screen off |
| Bluetooth | Connect a heart-rate sensor during a live run (optional) |
| Notifications | The daily reminder, and the run-in-progress notice |
| Vibration | Cues during a live run |
| Run at startup | Re-arm the daily reminder after the phone restarts |
| Internet | Weather and the update check, only when switched on |

Every permission is asked for at the moment it is needed, and the app keeps working if you say no. Health Connect access can be removed at any time in Android's Health Connect settings.

## Crash reports

If the app crashes, the reason is saved to a file on the phone. Nothing is sent. You can copy it from Setup → Help and diagnostics to include in a bug report; it contains the app version, phone model and settings switches, and no runs, heart rates or locations.

Weather data by [Open-Meteo.com](https://open-meteo.com/), licensed CC BY 4.0. Map tiles and data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.
