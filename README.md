# Zone 2 Running

**Get faster at an easy heart rate.**

Zone 2 Running is a small Android app for one goal: bringing your Zone 2 pace down to a target you choose (7:00 /km by default). It turns every steady run into "the pace you would hold at Zone 2 heart rate", tracks that number over time, and gives you a weekly plan built around it. Everything stays on your phone.

## What it does

- **An opening story.** Dil, a small heart, explains in six animated scenes why most running should feel easy and what Zone 2 pace is.
- **Setup guide.** Enter a target pace, any recent pace with the heart rate you saw at it, and your age or max heart rate. You get your zones, your current Zone 2 pace and a plan, with a note on which answer produced what.
- **Zone 2 estimate from any run.** A hard 10K still tells you something. Each run's pace is scaled along heart-rate reserve to your Zone 2 reference heart rate.
- **A plan that waits for you.** Three steps grow by about 8%, the fourth eases off and ends with a 30-minute Zone 2 test. The plan only moves on after a completed week, and switches easy runs to run-walk when your easy runs keep drifting into Zone 3.
- **Health Connect import.** Runs recorded by your watch app, Strava, unstrap or anything else that writes to Health Connect arrive with distance, time, heart rate and time in each zone. Read-only.
- **Eight two-minute lessons**, each ending with one question.
- **Streaks that forgive.** A week counts at about three-quarters of its runs. Rest passes cover a missed week, and a week marked as time off never breaks the streak.
- **Weekly quests, levels and badges** that reward easy minutes twice as much as hard ones. One switch in Setup turns the whole game layer off.
- **A daily reminder** on run days, and a picture of each run to share.
- **Local backup.** Pick a folder for a daily backup, or export and restore a single JSON file.

The reasoning behind these choices, with sources, is in [docs/research.md](docs/research.md).

## Install

Download the APK from the latest release and open it on your phone (Android 8 or newer). Health Connect import needs Android 9 or newer with Health Connect available.

## Privacy

No account, no server, no analytics, and no internet permission. See [docs/privacy.md](docs/privacy.md).

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
