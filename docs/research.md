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
