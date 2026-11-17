package app.zone2running

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Choosing how a run looks when it is shared: pick a look, the shape (story or post), optionally a photo,
 * then send it or save it. Every card carries only distance, time and pace.
 */
@Composable
fun SharePicker(vm: AppVM, e: Engine, r: Run, close: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var story by remember { mutableStateOf(true) }
    val pager = rememberPagerState { ShareCard.STYLES.size }
    val zone: (Int) -> Int = remember(e) { { hr -> e.zoneOf(hr) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { photo = ShareCard.loadPhoto(ctx, uri); if (photo == null) vm.toast = "Couldn't open that picture" else if (pager.currentPage < 5) scope.launch { pager.animateScrollToPage(6) } }
    }
    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val style = pager.currentPage

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(CircleShape).clickable(onClick = close).semantics { contentDescription = "Back" }.padding(10.dp)) { Glyph("left", p.ink, Modifier.size(22.dp)) }
        Head("Share this run", modifier = Modifier.weight(1f).padding(start = 4.dp))
        Pick("Story", story) { story = true }; Spacer(Modifier.width(6.dp)); Pick("Post", !story) { story = false }
    }
    HorizontalPager(pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = if (story) 62.dp else 28.dp), pageSpacing = 14.dp) { i ->
        // The look on screen plays its animation once when you land on it, so you can see what the video will do.
        val play = remember(i, story, photo) { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(pager.currentPage, story, photo) { if (pager.currentPage == i && Motion.on && ShareCard.canAnimate(i, photo != null)) { play.snapTo(0f); play.animateTo(1f, androidx.compose.animation.core.tween(3200, easing = androidx.compose.animation.core.LinearEasing)) } }
        val step = (play.value * 48).toInt()
        val bmp = remember(r.id, i, photo, story, U.miles, step) { ShareCard.render(ctx, r, i, photo, story, .4f, step / 48f, zone) }
        Box(Modifier.fillMaxWidth().aspectRatio(ShareCard.W / ShareCard.height(i, story, photo != null).toFloat()).clip(RoundedCornerShape(20.dp))
            .then(if (i in ShareCard.USES_PHOTO && photo == null) Modifier.clickable(onClick = choose) else Modifier)) {
            if (i == ShareCard.STAMP && photo == null) Checker(Modifier.fillMaxSize())           // shows that the sticker really has nothing behind it
            Image(bmp.asImageBitmap(), ShareCard.STYLES[i], Modifier.fillMaxSize())
        }
    }
    // Every look as a thumbnail: tap to jump to it.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShareCard.STYLES.indices.forEach { i ->
            val thumb = remember(r.id, i, photo, U.miles) { ShareCard.render(ctx, r, i, photo, false, .12f, 1f, zone) }
            Column(Modifier.width(62.dp).clickable { scope.launch { pager.animateScrollToPage(i) } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(62.dp, 78.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, if (style == i) p.accent else Color.Transparent, RoundedCornerShape(8.dp)).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(p.surface2)) {
                    if (i == ShareCard.STAMP && photo == null) Checker(Modifier.fillMaxSize())
                    Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Small(ShareCard.STYLES[i], if (style == i) p.ink else p.muted)
            }
        }
    }
    Small(when {
        style == 0 -> "Your heart rate wound once round a circle, coloured by zone. No two runs draw the same one."
        style == 1 -> "Your run as ranges of hills, start in the distance and finish up close. Height is heart rate, colour is zone."
        style == 2 -> "The path you ran. Runs without a recorded route show the heart-rate line."
        style == ShareCard.STAMP && photo == null -> "A see-through sticker. Save it, then in an Instagram story tap the sticker icon, choose the photo sticker and pick it. Or add a photo here and it is stamped on for you."
        style == ShareCard.STAMP -> "Stamped on your photo."
        style in ShareCard.USES_PHOTO && photo == null -> "This look needs a photo of yours."
        style == 5 -> if (photo == null) "A picture shows only through the numbers. Add your own photo to see it through them." else "Your photo shows only through the numbers."
        style == 6 -> "Your photo as a magazine cover, with the distance as the masthead."
        style == 7 -> "Your photo untouched, with the numbers on a pane of frosted glass."
        style == 3 -> "The sun comes up as the video plays."
        style == 4 -> "A lap of the track."
        else -> ""
    }, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    val needsPhoto = style in ShareCard.USES_PHOTO && photo == null
    val video = ShareCard.canAnimate(style, photo != null)
    val making = vm.videoProgress
    if (making != null) {
        // Making the video takes a few seconds: say so, and how far along it is.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Meter(making); Small("Making the video… ${(making * 100).toInt()}%", align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    } else if (needsPhoto) BigBtn("Choose a photo") { choose() }
    else {
        if (video) BigBtn("Share as video", "play") { vm.shareVideo(r, style, photo, story, save = false) }
        BigBtn("Share as picture", ghost = video) { vm.shareRun(r, style, photo, story) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        if (!needsPhoto && making == null) {
            if (video) TextLink("Save video") { vm.shareVideo(r, style, photo, story, save = true) }
            TextLink("Save picture") { vm.saveShare(r, style, photo, story) }
        }
        TextLink(if (photo == null) "Add my photo" else "Change photo") { choose() }
    }
}

/** The grey chequerboard that means "see-through". */
@Composable
private fun Checker(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.width / 14f
        drawRect(Color(0xFF8A9390))
        for (y in 0..(size.height / s).toInt()) for (x in 0..14) if ((x + y) % 2 == 0) drawRect(Color(0xFF6F7A77), Offset(x * s, y * s), Size(s, s))
    }
}
