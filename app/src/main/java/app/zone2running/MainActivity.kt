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

val TABS = listOf("Today", "Plan", "Progress")

@Composable
fun App(vm: AppVM) {
    val p = LocalPal.current
    val e = remember(vm.data, vm.wxTick) { vm.engine() }
    var form by remember { mutableStateOf<FormSeed?>(null) }
    var live by remember { mutableStateOf<Session?>(null) }
    // A finished live run arrives here: save it, or ask for the distance when GPS recorded none.
    LaunchedEffect(Live.result) {
        Live.result?.let { r -> Live.result = null; live = null; if (r.distKm >= 0.1) vm.saveRun(r, isNew = true) else form = FormSeed(run = r, fresh = true) }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var lesson by remember { mutableStateOf<Lesson?>(null) }
    var rerunGuide by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf<String?>(null) }      // the pages behind the gear: menu, learn, settings, about, privacy, licences
    var replayIntro by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }

    if (!vm.settings.introSeen || replayIntro) {
        BackHandler(enabled = replayIntro) { replayIntro = false }
        IntroStory { if (!vm.settings.introSeen) vm.updateSettings { it.copy(introSeen = true) }; replayIntro = false }
        return
    }

    val guide = !vm.settings.onboarded || rerunGuide
    val reward = vm.celebration
    val running = Live.snap.active || live != null
    val chrome = !guide && reward == null && form == null && lesson == null && !running && more == null
    BackHandler(enabled = form != null || lesson != null || reward != null || more != null || (live != null && !Live.snap.active) || (guide && vm.settings.onboarded)) {
        when { live != null -> live = null; form != null -> form = null; lesson != null -> lesson = null; reward != null -> vm.celebration = null
            guide -> rerunGuide = false; more == "menu" -> more = null; more == "privacy" || more == "licences" -> more = "about"; more != null -> more = "menu" }
    }

    Scaffold(containerColor = p.bg, snackbarHost = { SnackbarHost(snack) }, bottomBar = { if (chrome) TabBar(tab) { tab = it } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when {
                guide -> Page { Onboarding(vm, canClose = vm.settings.onboarded) { rerunGuide = false } }
                running && reward == null && form == null -> Page { LiveScreen(vm, e, live) { live = null } }
                reward != null -> Box {
                    Page { RewardScreen(reward, share = { vm.shareRun(reward) }) { vm.celebration = null; tab = 0 } }
                    if (reward.celebrate) Confetti()
                }
                form != null -> Page { RunForm(vm, e, form!!) { form = null } }
                lesson == null && more != null -> Page {
                    MoreScreen(vm, e, more!!, go = { more = it }, openLesson = { lesson = it }, replayIntro = { more = null; replayIntro = true }, rerunGuide = { more = null; rerunGuide = true })
                }
                lesson != null -> Page { LessonScreen(lesson!!, already = lesson!!.id in vm.settings.lessonsDone, close = { lesson = null }) { vm.finishLesson(lesson!!.id); lesson = null } }
                else -> AnimatedContent(tab, transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { dir * it / 10 }) togetherWith fadeOut(tween(120))
                }, label = "tab") { t ->
                    Page {
                        TopBar(e, vm.settings.gameOn, openMore = { more = "menu" }) { tab = 2 }
                        when (t) {
                            0 -> TodayScreen(vm, e, openPlan = { tab = 1 }, openSetup = { more = "settings" }, openLesson = { lesson = it }, openLive = { live = it }) { form = it }
                            1 -> PlanScreen(vm, e) { form = it }
                            else -> ProgressScreen(vm, e) { form = it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun TabBar(tab: Int, onTab: (Int) -> Unit) {
    val p = LocalPal.current
    Column(Modifier.background(p.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        BoxWithConstraints {
            val w = maxWidth / TABS.size
            val x = animFloat(tab.toFloat(), 260)
            Box(Modifier.offset(x = w * x).width(w).height(3.dp).background(p.accent))   // the marker slides between tabs
            Row {
                TABS.forEachIndexed { i, name ->
                    Box(Modifier.weight(1f).clickable { onTab(i) }, contentAlignment = Alignment.Center) {
                        Text(name.uppercase(), Modifier.padding(top = 17.dp, bottom = 14.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp, letterSpacing = .8.sp, color = if (i == tab) p.ink else p.muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun TopBar(e: Engine, gameOn: Boolean, openMore: () -> Unit, openProgress: () -> Unit) {
    val p = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ZONE 2 RUNNING", Modifier.weight(1f), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = p.ink)
        if (gameOn) {
            val g = e.game
            val shape = RoundedCornerShape(50)
            val c = if (g.streak > 0) p.hard else p.muted
            Row(Modifier.clip(shape).background(p.surface).border(1.dp, p.line, shape).clickable(onClick = openProgress).padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Flame(c)
                Text("${g.streak}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c)
                Text(if (g.streak == 1) "week" else "weeks", fontSize = 12.5.sp, color = p.muted, fontFamily = BodyFont)
            }
        }
        Gear(Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(p.surface).border(1.dp, p.line, androidx.compose.foundation.shape.CircleShape).clickable(onClick = openMore).padding(9.dp))
    }
}
