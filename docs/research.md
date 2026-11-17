# What this app borrows, and why

Notes from reviewing the leading fitness and language apps and the research on Zone 2, November 2026. Each point lists what was learned and what it changed in Zone 2 Running.

## From language apps (Duolingo)

| What works there | What we built |
|---|---|
| The streak is the main reason people come back, and one small action keeps it alive | A weekly streak that needs about three-quarters of the planned runs, not all of them |
| Streak freezes are granted automatically and applied silently, so one miss doesn't end everything | Rest passes: one to start, one more per three completed weeks, two at most, used without asking |
| Big animations are saved for milestones, which keeps them meaningful | Confetti only for a new level, badge, record or streak milestone |
| New users pick a streak goal before anything else | The setup guide asks "how long will you give it?" (4, 8 or 12 weeks) |
| A mascot with moods makes the product feel alive | Dil, a heart whose mood follows the runner's training |
| Share cards spread the app | A picture of each run for the share sheet |
| Bite-sized lessons with a question at the end | Eight two-minute lessons on a path |

Sources: [Deconstructor of Fun on Duolingo streaks](https://duolingo.deconstructoroffun.com/mechanics/streaks), [Duolingo retention experiments](https://design.lazyweb.com/blog/duolingo-retention-experiments).

## From fitness apps

| App | What stands out | What we took |
|---|---|---|
| Gentler Streak | Supportive tone, a heart character, and a sick / injured / break status for "when life happens" | "Take week off" keeps the streak safe; copy that never scolds |
| Runna | Plans built from a goal, current fitness and available days, with pacing guidance for each session | Plan from target pace, current pace and chosen days; a heart-rate cap and expected pace on every session |
| Nike Run Club | Simple, guided, free | One clear next session on the home screen |
| Strava | Sharing and community; leaderboards can push people to race their easy runs | Sharing yes, pace leaderboards no |
| Hevy | A summary screen and record alerts the moment a workout ends | The reward screen after every run |
| unstrap | Everything on the phone, Health Connect, daily local backup | The same three choices |

Sources: [Sketch on Gentler Streak](https://go.sketch.com/blog/gentler-streak/), [Runner's Picks: best running apps 2026](https://www.runnerspicks.com/blog/best-running-apps-2026/), [Hevy features](https://www.hevyapp.com/features/gym-progress/).

## From onboarding research

- Show the outcome before explaining. The intro ends on the plan, not on a feature list.
- Ask only questions that change something, and show which answer produced which setting.
- Treat permissions as a choice: pick a reminder time and see a preview before Android asks.
- No account wall.

Source: [App onboarding trends 2026](https://screensdesign.com/articles/app-onboarding-trends-2026/).

## What the science does and doesn't say

- Elite endurance athletes do roughly 80% of their sessions at low intensity. This is observational evidence from elites.
- For recreational athletes the evidence is thinner. One 12-week study of recreational triathletes found polarized and pyramidal plans gave nearly identical race times.
- A 2025 review in *Sports Medicine* concludes that current evidence does not support Zone 2 as the optimal intensity for mitochondrial or fat-oxidation capacity in the general population, and that higher intensities matter, especially when training time is short.

What that means for the app: it does not claim Zone 2 is magic. The intro says easy running is the kind you can do a lot of. The plan keeps one harder session a week, and one lesson is about why the hard 20% matters.

Sources: [Much Ado About Zone 2, Sports Medicine 2025](https://pubmed.ncbi.nlm.nih.gov/40560504/), [TrainingPeaks on polarized training](https://trainingpeaks.com/blog/does-polarized-training-really-work).

## 0.7.0: engine changes from the research review

A review of 136 sources (kept outside the repository) led to eight changes. Each is a judgement on imperfect evidence; the limits are stated.

1. **Run-length cap.** No easy or long run is planned more than about 10% longer than the longest run of the previous 30 days (never less than 5 minutes more, floor of 30 minutes). After a month without running, sessions restart at 60%. Basis: one large observational cohort (5,205 runners) in which single-session spikes predicted injury and week-to-week change did not. The study measured distance; the plan works in minutes.
2. **Zone 2 pace.** Each run is read from its steady middle (roughly minutes 8 to 42 of the splits). The headline number is the median of up to eight steady easy runs from the last six weeks; a change is only reported as real when the new median sits outside the middle half of the earlier readings.
3. **Talk test.** A guided six-stage session; the highest heart rate at which speech was still comfortable replaces the formula's Zone 2 ceiling. The app also offers a higher max heart rate once one has been reached twice.
4. **Effort rating.** One tap after a run. It stands in for heart rate when there is none, and easy-heart-rate runs that feel hard produce a tiredness note.
5. **Race forecast.** Built from the best sustained hard effort of the last eight weeks (whole run or best 3, 5 or 10 km from splits), shown as a range. Beyond the half marathon the exponent rises from 1.06 to 1.08, or 1.10 under 40 km a week. Those two exponents are our own conservative choice, not published coefficients.
6. **Taper.** One week for races up to 12 km, two up to 30 km, three beyond. Volume steps down to about half; run frequency stays and the hard session becomes a short tune-up. The one-week figure for short races is an assumption.
7. **Run-walk.** Triggered only by cool-weather easy days with 40% or more of the time above the ceiling: two of the last three gives "slow down first", four of the last five gives run-walk. It is the default for someone just starting. The 4:1 ratio is a convention with no trial behind it.
8. **Screening.** Two questions in setup and Settings. A yes to the first removes hard sessions; a yes to the second (heart-rate medication) switches guidance to effort. Every heart-rate target carries an effort cue.
