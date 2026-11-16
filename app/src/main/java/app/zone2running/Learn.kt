package app.zone2running

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/** A winding path of short lessons, unlocked one after another. */
@Composable
fun LearnScreen(s: Settings, open: (Lesson) -> Unit) {
    val p = LocalPal.current
    val done = LESSONS.count { it.id in s.lessonsDone }
    Panel(Modifier.rise(0)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dil(if (done == LESSONS.size) "proud" else "hello", Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Head("Learn the easy way")
                Small(if (done == LESSONS.size) "All lessons done. You know more about easy running than most runners." else "Eight two-minute lessons. Each ends with one question.")
                Meter(done.toFloat() / LESSONS.size * enter(800, 200))
            }
        }
    }
    val next = LESSONS.indexOfFirst { it.id !in s.lessonsDone }
    val pulse = loop(1600)
    val offsets = listOf(0, 56, 104, 56)
    Column(Modifier.rise(1)) {
        LESSONS.forEachIndexed { i, l ->
            val isDone = l.id in s.lessonsDone; val isNext = i == next; val locked = !isDone && !isNext
            if (i > 0) Canvas(Modifier.padding(start = ((offsets[(i - 1) % 4] + offsets[i % 4]) / 2 + 30).dp).size(4.dp, 22.dp)) {
                drawLine(p.line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(6f, 10f)))
            }
            Row(Modifier.fillMaxWidth().padding(start = offsets[i % 4].dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = !locked) { open(l) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val grow = if (isNext && Motion.on) 1f + .06f * sin(pulse * 2 * PI).toFloat() else 1f
                Box(Modifier.size(64.dp).graphicsLayer { scaleX = grow; scaleY = grow }.clip(CircleShape)
                    .background(when { isDone -> p.accent; isNext -> p.heroMark; else -> p.surface2 })
                    .border(3.dp, when { isDone -> p.easy; isNext -> p.hero; else -> p.line }, CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (isDone) "✓" else "${i + 1}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp,
                        color = when { isDone -> p.accentInk; isNext -> p.hero; else -> p.muted })
                }
                Column {
                    Text(l.title, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (locked) p.muted else p.ink)
                    Small(when { isDone -> "Done · tap to reread"; isNext -> "Up next · 2 min"; else -> "Locked" })
                }
            }
        }
    }
}

@Composable
fun LessonScreen(l: Lesson, already: Boolean, close: () -> Unit, finish: () -> Unit) {
    val p = LocalPal.current
    var step by remember { mutableIntStateOf(0) }
    var picked by remember { mutableIntStateOf(-1) }
    val total = l.cards.size + 1
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("✕", Modifier.clip(CircleShape).clickable(onClick = close).padding(8.dp), fontSize = 20.sp, color = p.muted)
        Meter(animFloat((step + 1f) / total, 350), Modifier.weight(1f).height(10.dp))
    }
    Label(l.title)
    if (step < l.cards.size) {
        val c = l.cards[step]
        key(step) {
            Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (step == 0) Dil("hello", Modifier.size(88.dp))
                Text(c.head, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 38.sp, color = p.ink)
                Text(c.body, fontFamily = BodyFont, fontSize = 18.sp, lineHeight = 27.sp, color = p.ink)
            }
        }
    } else {
        Text(l.question, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp, color = p.ink)
        l.options.forEachIndexed { i, o ->
            val right = i == l.answer; val chosen = picked == i
            val shape = RoundedCornerShape(12.dp)
            val (bg, line) = when { chosen && right -> p.goodBg to p.accent; chosen -> p.warnBg to p.warnInk; else -> p.surface to p.line }
            Text(o, Modifier.fillMaxWidth().clip(shape).background(bg).border(if (chosen) 2.dp else 1.dp, line, shape).clickable(enabled = picked != l.answer) { picked = i }.padding(16.dp),
                fontFamily = BodyFont, fontSize = 16.5.sp, color = p.ink)
        }
        when {
            picked == l.answer -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Dil("proud", Modifier.size(56.dp)); Body("Right. ${l.why}", modifier = Modifier.weight(1f)) }
            picked >= 0 -> NoteBox(Note("warn", "Not quite. Have another go."))
        }
    }
    Row {
        if (step > 0) Btn("Back", ghost = true) { step--; picked = -1 }
        Spacer(Modifier.weight(1f))
        if (step < l.cards.size) Btn(if (step == l.cards.size - 1) "Quick question" else "Next") { step++ }
        else if (picked == l.answer) Btn(if (already) "Done" else "Finish · +40 XP") { if (already) close() else finish() }
    }
}
