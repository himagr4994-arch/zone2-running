package app.easyseven

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
        setContent { EasyTheme { App(vm) } }
    }

    override fun onResume() { super.onResume(); vm.refreshHealth() }
}

/** What the run form opens with: an existing run to edit, or a date and type to pre-fill. */
data class FormSeed(val run: Run? = null, val date: LocalDate = LocalDate.now(), val type: String = "easy")

@Composable
fun App(vm: AppVM) {
    val p = LocalPal.current
    val e = remember(vm.data) { vm.engine() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var form by remember { mutableStateOf<FormSeed?>(null) }
    var rerunGuide by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }

    val guide = !vm.settings.onboarded || rerunGuide
    val reward = vm.celebration
    val chrome = !guide && reward == null && form == null
    BackHandler(enabled = form != null || reward != null || (guide && vm.settings.onboarded)) {
        when { form != null -> form = null; reward != null -> vm.celebration = null; else -> rerunGuide = false }
    }

    Scaffold(containerColor = p.bg, snackbarHost = { SnackbarHost(snack) }, bottomBar = { if (chrome) TabBar(tab) { tab = it } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when {
                guide -> Page { Onboarding(vm, canClose = vm.settings.onboarded) { rerunGuide = false } }
                reward != null -> Page { RewardScreen(reward, vm.settings.gameOn) { vm.celebration = null; tab = 0 } }
                form != null -> Page { RunForm(vm, e, form!!) { form = null } }
                else -> key(tab) {
                    Page {
                        TopBar(e, vm.settings.gameOn) { tab = 3 }
                        when (tab) {
                            0 -> TodayScreen(vm, e, openPlan = { tab = 1 }, openSetup = { tab = 4 }) { form = it }
                            1 -> PlanScreen(e) { form = it }
                            2 -> LogScreen(vm, e) { form = it }
                            3 -> ProgressScreen(e, vm.settings.gameOn)
                            else -> SetupScreen(vm, e) { rerunGuide = true }
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
        Row {
            listOf("Today", "Plan", "Log", "Progress", "Setup").forEachIndexed { i, name ->
                Column(Modifier.weight(1f).clickable { onTab(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (i == tab) p.accent else p.surface))
                    Text(name.uppercase(), Modifier.padding(vertical = 14.dp), fontFamily = Display, fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp, letterSpacing = .8.sp, color = if (i == tab) p.ink else p.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun TopBar(e: Engine, gameOn: Boolean, openProgress: () -> Unit) {
    val p = LocalPal.current
    Text("EASY SEVEN", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.sp, color = p.ink)
    if (gameOn) {
        val g = e.game
        val shape = RoundedCornerShape(14.dp)
        Row(Modifier.fillMaxWidth().clip(shape).background(p.surface).border(1.dp, p.line, shape).clickable(onClick = openProgress).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val c = if (g.streak > 0) p.hard else p.muted
                Flame(c)
                Text("${g.streak}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = c)
                Text("week\nstreak", fontSize = 11.sp, lineHeight = 12.sp, color = p.muted, fontFamily = BodyFont)
            }
            LevelBar(g, Modifier.weight(1f))
        }
    }
}
