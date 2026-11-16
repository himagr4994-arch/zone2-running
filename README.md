# Zone 2 Running

**Get faster at an easy heart rate.**

Zone 2 Running is a small Android app for one goal: bringing your Zone 2 pace down to a target you choose (7:00 /km by default). It turns every steady run into "the pace you would hold at Zone 2 heart rate", tracks that number over time, and gives you a weekly plan built around it. Your runs stay on your phone.

## What it does

- **An opening story.** Dil, a small heart, explains in six animated scenes why most running should feel easy and what Zone 2 pace is.
- **A touchable pace chart.** Your Zone 2 pace sits on its own history; drag across it to read any run.
- **A home screen that follows your day.** It leads with one thing: the session in the morning, a nudge in the evening, the run itself once it is done, a shorter option when it gets late, tomorrow's plan on a rest day.
- **It learns your routine and asks first.** After a few timed runs it knows when you usually run, shifts the home screen to match, and offers to move the reminder or a plan day. Nothing changes without a yes, and all of it is worked out on the phone.
- **Setup guide.** Enter a target pace, any recent pace with the heart rate you saw at it, and your age or max heart rate. You get your zones, your current Zone 2 pace and a plan, with a note on which answer produced what.
- **Zone 2 estimate from any run.** A hard 10K still tells you something. Each run's pace is scaled along heart-rate reserve to your Zone 2 reference heart rate.
- **A plan that waits for you.** Three steps grow by about 8%, the fourth eases off and ends with a 30-minute Zone 2 test. The plan only moves on after a completed week, and switches easy runs to run-walk when your easy runs keep drifting into Zone 3.
- **Guided live runs.** Start a session and the phone tracks time, distance and pace by GPS, walks you through each block, and speaks or buzzes at block changes and each kilometre. With a Bluetooth heart-rate sensor it also tells you the moment you leave Zone 2. The route is never stored.
- **Kilometre splits** for live runs, and for imported runs when the recording app wrote distance in fine enough pieces.
- **Swap, move or skip any session** from the plan.
- **A race goal.** Pick an event and date; the plan tapers over the last two weeks and shows a projected finish time.
- **Heat adjustment (optional).** With weather switched on, hot and humid hours lower the expected pace and no longer count against your Zone 2 estimate.
- **Kilometres or miles.**
- **Health Connect import.** Runs recorded by your watch app, Strava, unstrap or anything else that writes to Health Connect arrive with distance, time, heart rate and time in each zone. Read-only.
- **Nine two-minute lessons**, each ending with one question.
- **Streaks that forgive.** A week counts at about three-quarters of its runs. Rest passes cover a missed week, and a week marked as time off never breaks the streak.
- **Weekly quests and badges.** One switch in Settings turns the whole game layer off.
- **A daily reminder** on run days, and a picture of each run to share.
- **Local backup.** Pick a folder for a daily backup, or export and restore a single JSON file.

The reasoning behind these choices, with sources, is in [docs/research.md](docs/research.md).

## Install

Download the APK from the latest release and open it on your phone (Android 8 or newer). Health Connect import needs Android 9 or newer with Health Connect available.

## Privacy

No account, no server, no analytics. The app goes online only if you switch on weather or the update check. See [docs/privacy.md](docs/privacy.md).

## How the numbers work

- **Zones** come from heart-rate reserve by default (`rest + % × (max − rest)`, Zone 2 = 60–70%). You can switch to percent of max, or copy the zones from your watch.
- **Zone 2 estimate** = run pace × (average HR − resting HR) ÷ (reference HR − resting HR). Intervals are left out because their average heart rate says little.
- **Current Zone 2 pace** is the average of your last three estimates.
- **Streak**: a week counts when about three-quarters of its planned runs are done.

All of this lives in [`Engine.kt`](app/src/main/java/app/zone2running/Engine.kt), which is plain Kotlin with unit tests.

## Build

You need JDK 17 or newer and the Android SDK (platform 36).

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The tests also render every screen on the JVM and save pictures to `app/build/shots/`, so you can see a change without a phone.

To switch on the in-app update check, set `UPDATE_REPO` in `app/build.gradle.kts` to your public `owner/repository`.

For a signed release, put a `keystore.properties` file in the project root (it is git-ignored):

```
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then run `./gradlew :app:assembleRelease`.

## Not medical advice

Heart-rate zones and pace estimates are rough guides for training. Talk to a doctor before starting a new plan if you have a health condition.

## Licence

MIT. See [LICENSE](LICENSE). The Barlow typefaces in `app/src/main/res/font` are licensed under the SIL Open Font License 1.1.
