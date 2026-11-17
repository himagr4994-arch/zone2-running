package app.zone2running

import android.os.Bundle
import android.provider.Settings.Global
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Respect the phone's "remove animations" setting.
        Motion.on = Global.getFloat(contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        setContent { AppTheme { App(vm) } }
    }

    override fun onResume() { super.onResume(); vm.refreshHealth() }
}

/** What the run form opens with: an existing run to edit, or a date and type to pre-fill. */
data class FormSeed(val run: Run? = null, val date: LocalDate = LocalDate.now(), val type: String = "easy", val fresh: Boolean = false)

val TABS = listOf("Today" to "heart", "Plan" to "calendar", "Progress" to "chart")

@Composable
fun App(vm: AppVM) {
    val p = LocalPal.current
    val e = remember(vm.data, vm.wxTick) { vm.engine() }
    val pager = rememberPagerState { TABS.size }
    val scope = rememberCoroutineScope()
    val go: (Int) -> Unit = { scope.launch { pager.animateScrollToPage(it) } }
    var form by remember { mutableStateOf<FormSeed?>(null) }
    var sharing by remember { mutableStateOf<Run?>(null) }
    var live by remember { mutableStateOf<Session?>(null) }
    var lesson by remember { mutableStateOf<Lesson?>(null) }
    var runId by remember { mutableStateOf<String?>(null) }       // the run whose page is open
    var rerunGuide by remember { mutableStateOf(false) }
    var replayIntro by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf<String?>(null) }        // the pages behind the cog: menu, learn, settings, about, privacy, licences
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }
    // A finished live run arrives here: save it, or ask for the distance when GPS recorded none.
    LaunchedEffect(Live.result) {
        Live.result?.let { r -> Live.result = null; live = null; // A free run had no label going in: name it from how hard it turned out to be.
            val run = if (r.kind == "free") r.copy(type = e.guessType(r.durSec, r.avgHr)) else r
            if (run.distKm >= 0.1) vm.saveRun(run, isNew = true) else form = FormSeed(run = run, fresh = true) }
    }

    // A discarded run goes back home, not to the briefing it started from.
    var wasLive by remember { mutableStateOf(false) }
    LaunchedEffect(Live.snap.active) { if (wasLive && !Live.snap.active) live = null; wasLive = Live.snap.active }

    if (!vm.settings.introSeen || replayIntro) {
        BackHandler(enabled = replayIntro) { replayIntro = false }
        IntroStory { if (!vm.settings.introSeen) vm.updateSettings { it.copy(introSeen = true) }; replayIntro = false }
        return
    }

    val guide = !vm.settings.onboarded || rerunGuide
    val reward = vm.celebration
    val running = Live.snap.active || live != null
    val run = runId?.let { id -> e.runs.firstOrNull { it.id == id } }
    val chrome = sharing == null && !guide && reward == null && form == null && lesson == null && !running && more == null && run == null
    BackHandler(enabled = !chrome && !(guide && !vm.settings.onboarded) && !Live.snap.active) {
        when {
            sharing != null -> sharing = null
            guide -> rerunGuide = false; live != null -> live = null; reward != null -> vm.celebration = null; form != null -> form = null
            lesson != null -> lesson = null; run != null -> runId = null
            more == "menu" -> more = null; more == "privacy" || more == "licences" -> more = "about"; more?.startsWith("settings/") == true -> more = "settings"; else -> more = "menu"
        }
    }

    Scaffold(containerColor = if (Live.snap.active && reward == null && form == null) liveGround(e) else p.bg, snackbarHost = { SnackbarHost(snack) }, bottomBar = { if (chrome) TabBar(pager.currentPage, go) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when {
                guide -> Page { Onboarding(vm, canClose = vm.settings.onboarded) { rerunGuide = false } }
                Live.snap.active && reward == null && form == null -> LiveRunning(e, vm.settings.mapTiles)
                running && reward == null && form == null -> Page(arrive = true) { LiveScreen(vm, e, live) { live = null } }
                sharing != null -> Page(arrive = true) { SharePicker(vm, e, sharing!!) { sharing = null } }
                reward != null -> Box {
                    Page(arrive = true) { RewardScreen(reward, feel = vm.data.runs.firstOrNull { it.id == reward.run.id }?.feel ?: 0, rate = { vm.setFeel(reward.run.id, it) }, share = { sharing = reward.run }) { vm.celebration = null; go(0) } }
                    if (reward.celebrate) Confetti()
                }
                form != null -> Page(arrive = true) { RunForm(vm, e, form!!) { form = null } }
                lesson != null -> Page(arrive = true) { LessonScreen(lesson!!, already = lesson!!.id in vm.settings.lessonsDone, close = { lesson = null }) { vm.finishLesson(lesson!!.id); lesson = null } }
                run != null -> Page(arrive = true) { RunPage(e, run, rate = { vm.setFeel(run.id, it) }, edit = { form = FormSeed(run = run) }, streets = vm.settings.mapTiles, addRoute = { vm.addRoute(run, it) }, share = { sharing = run }) { runId = null } }
                more != null -> key(more) {
                    Page(arrive = true) { MoreScreen(vm, e, more!!, go = { more = it }, openLesson = { lesson = it }, replayIntro = { more = null; replayIntro = true }, rerunGuide = { more = null; rerunGuide = true }) }
                }
                // The three tabs sit side by side: swipe between them, or tap the bar.
                else -> HorizontalPager(pager, Modifier.fillMaxSize()) { t ->
                    Page(refresh = if (t == 0) ({ vm.pulled() }) else null, busy = vm.importing) {
                        TopBar(e, vm.settings.gameOn, openMore = { more = "menu" }) { go(2) }
                        when (t) {
                            0 -> TodayScreen(vm, e, openPlan = { go(1) }, openSetup = { more = "settings" }, openLesson = { lesson = it }, openLive = { live = it }, openRun = { runId = it.id }) { form = it }
                            1 -> PlanScreen(vm, e, openLive = { live = it }, openRun = { runId = it.id }) { form = it }
                            else -> ProgressScreen(vm, e, openRun = { runId = it.id }) { form = it }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One scrolling screen. Pages that open on top of the tabs [arrive] with a short slide;
 * a page with [refresh] can be pulled down to bring in new runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(arrive: Boolean = false, refresh: (() -> Unit)? = null, busy: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val v = if (arrive) enter(260) else 1f
    val column: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = v; translationX = (1 - v) * 36.dp.toPx() }.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
    if (refresh != null) PullToRefreshBox(isRefreshing = busy, onRefresh = refresh, modifier = Modifier.fillMaxSize()) { column() } else column()
}

@Composable
fun TabBar(tab: Int, onTab: (Int) -> Unit) {
    val p = LocalPal.current
    val tick = haptic()
    Column(Modifier.background(p.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.padding(vertical = 6.dp)) {
            TABS.forEachIndexed { i, (name, glyph) ->
                val on = i == tab
                Column(Modifier.weight(1f).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { tick(false); onTab(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) p.goodBg else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 20.dp, vertical = 5.dp)) {
                        Glyph(glyph, if (on) p.goodInk else p.muted, Modifier.size(22.dp))
                    }
                    Text(name, fontFamily = BodyFont, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp, color = if (on) p.ink else p.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun TopBar(e: Engine, gameOn: Boolean, openMore: () -> Unit, openProgress: () -> Unit) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ZONE 2 RUNNING", Modifier.weight(1f), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.2.sp, color = p.ink)
        if (gameOn) {
            val g = e.game
            val c = if (g.streak > 0) p.hard else p.muted
            Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = openProgress).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Flame(c)
                Text("${g.streak}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c)
            }
        }
        Gear(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = openMore).padding(9.dp))
    }
}
