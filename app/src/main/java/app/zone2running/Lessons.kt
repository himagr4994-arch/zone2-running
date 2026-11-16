package app.zone2running

data class Card(val head: String, val body: String)
data class Lesson(val id: String, val title: String, val cards: List<Card>, val question: String, val options: List<String>, val answer: Int, val why: String)

/** Eight two-minute lessons. Claims are kept modest and match what the research supports. */
val LESSONS = listOf(
    Lesson("zone2", "What is Zone 2?", listOf(
        Card("Five gears", "Heart-rate zones split effort into five gears, from a stroll in Zone 1 to all-out in Zone 5. Zone 2 is the second gear: clearly running, clearly comfortable."),
        Card("What it feels like", "Breathing is steady. You could hold a conversation in full sentences. When you finish, you feel like you could go again."),
        Card("Why it's the base", "Because it costs so little, you can do a lot of it and come back tomorrow. Most endurance plans are built on a large amount of easy running for exactly that reason."),
    ), "Which describes Zone 2?", listOf("Gasping, one word at a time", "Comfortable, full sentences", "Walking pace only"), 1, "Zone 2 is running you can talk through."),
    Lesson("talk", "The talk test", listOf(
        Card("No watch needed", "Say a full sentence out loud. If you have to stop for air halfway through, you are above Zone 2."),
        Card("Watches lag", "Wrist heart rate can take a minute or two to catch up, and it can misread on cold or bumpy days. If the number and your breathing disagree, trust your breathing."),
        Card("Use both", "Heart rate keeps you honest on good days. The talk test keeps you honest when the watch is wrong."),
    ), "Your watch says Zone 2 but you can't finish a sentence. What do you do?", listOf("Speed up", "Slow down", "Ignore it"), 1, "Breathing beats the number. Ease off."),
    Lesson("grey", "Why not hard every day?", listOf(
        Card("The grey zone", "Many runners do every run comfortably hard: too fast to be easy, too slow to be a real workout. It feels productive and leaves you tired for the sessions that count."),
        Card("Fatigue adds up", "Hard running needs more recovery. Stack hard days and you end up running less overall, or running tired."),
        Card("Easy makes room", "Keeping easy days truly easy lets you run more often and arrive fresh for the hard session."),
    ), "What's the problem with medium-hard running every day?", listOf("It is too slow to count", "It tires you without the payoff of a real hard session", "Nothing, it's ideal"), 1, "It costs recovery but never reaches a true workout."),
    Lesson("8020", "The 80/20 split", listOf(
        Card("What the best do", "Studies of elite endurance athletes keep finding a similar split: roughly 80% of sessions easy and 20% hard."),
        Card("For the rest of us", "Research on recreational runners is thinner and less clear-cut. Doing most of your training easy with a little hard work is still a sound, widely used starting point."),
        Card("In this app", "The gold ring on Today tracks your easy share over 14 days. Aim to fill it at 80%."),
    ), "In an 80/20 week with five runs, how many are hard?", listOf("One", "Three", "Five"), 0, "One in five is 20%."),
    Lesson("drift", "Heart rate drifts", listOf(
        Card("Same pace, rising heart rate", "On a long or hot run your heart rate creeps up even when pace stays the same. That is cardiac drift: you are heating up and losing fluid."),
        Card("Heat costs beats", "A warm, humid evening can add several beats at the same pace, sometimes ten or more. That is not lost fitness."),
        Card("What to do", "Go by heart rate, not pace. Slow down or take a walk break late in the run, and don't judge your fitness by a hot day."),
    ), "Heart rate climbs late in a steady easy run. Best response?", listOf("Hold the pace no matter what", "Ease off to stay in Zone 2", "Sprint to finish sooner"), 1, "Effort is the target. Pace follows."),
    Lesson("runwalk", "Run-walk is running", listOf(
        Card("Walking isn't failing", "If jogging pushes you past Zone 2, walk for a minute and start again. You are still training the same system."),
        Card("A simple pattern", "Try 4 minutes of easy jogging, then 1 minute of brisk walking. Stretch the jog as your heart rate settles over the weeks."),
        Card("It fades by itself", "As fitness grows you need fewer walk breaks at the same heart rate. That is the progress this app measures."),
    ), "Jogging puts you in Zone 3 every time. What now?", listOf("Give up on Zone 2", "Add walk breaks", "Run faster to get it over with"), 1, "Walk breaks keep the effort where it should be."),
    Lesson("pace", "Reading your Zone 2 pace", listOf(
        Card("One number", "Zone 2 pace is how fast you run at an easy, fixed heart rate. As you get fitter, it gets faster."),
        Card("How the app estimates it", "Each steady run is scaled to your Zone 2 reference heart rate. A hard run gives a rough estimate. An easy run gives a good one."),
        Card("The clean reading", "Every fourth week, the Zone 2 test asks for 30 minutes at one heart rate on a flat route. Compare tests, not single runs. Swings of 10 to 15 seconds between days are noise."),
    ), "Which run gives the most reliable Zone 2 reading?", listOf("A race", "The 30-minute Zone 2 test", "Hill sprints"), 1, "Steady heart rate on a flat route is the cleanest measure."),
    Lesson("hard", "The hard 20%", listOf(
        Card("Easy isn't everything", "Easy running builds the base, but hard running is a strong signal too. Reviews of the research find high-intensity work is very effective for fitness, especially when training time is limited."),
        Card("One good session", "Your plan includes one harder session a week: short intervals or a tempo. Do it properly in Zone 4 and recover between reps."),
        Card("Keep them apart", "The mistake is blending the two. Easy days easy, hard day hard."),
    ), "How many harder sessions does your plan include each week?", listOf("None", "One", "Four"), 1, "One quality session, the rest easy."),
)
