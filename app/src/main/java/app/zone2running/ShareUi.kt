package app.zone2running

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Choosing how a run looks when it is shared: swipe between the designs, optionally put a photo
 * behind the numbers, then send it. Every card carries only distance, time and pace.
 */
@Composable
fun SharePicker(vm: AppVM, r: Run, close: () -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val pager = rememberPagerState { ShareCard.STYLES.size }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { photo = ShareCard.loadPhoto(ctx, uri); if (photo == null) vm.toast = "Couldn't open that picture" else scope.launch { pager.animateScrollToPage(ShareCard.PHOTO) } }
    }
    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(CircleShape).clickable(onClick = close).semantics { contentDescription = "Back" }.padding(10.dp)) { Glyph("left", p.ink, Modifier.size(22.dp)) }
        Head("Share this run", modifier = Modifier.weight(1f).padding(start = 4.dp))
    }
    HorizontalPager(pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 28.dp), pageSpacing = 14.dp) { i ->
        val bmp = remember(r.id, i, photo, U.miles) { ShareCard.render(ctx, r, i, photo) }
        Image(bmp.asImageBitmap(), ShareCard.STYLES[i], Modifier.fillMaxWidth().aspectRatio(ShareCard.W / ShareCard.H.toFloat()).clip(RoundedCornerShape(20.dp))
            .then(if (i == ShareCard.PHOTO && photo == null) Modifier.clickable(onClick = choose) else Modifier))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ShareCard.STYLES.indices.forEach { i -> Box(Modifier.padding(horizontal = 4.dp).size(if (pager.currentPage == i) 9.dp else 7.dp).clip(CircleShape).background(if (pager.currentPage == i) p.ink else p.line)) }
        Spacer(Modifier.width(10.dp)); Small(ShareCard.STYLES[pager.currentPage])
    }
    val needsPhoto = pager.currentPage == ShareCard.PHOTO && photo == null
    BigBtn(if (needsPhoto) "Choose a photo" else "Share", if (needsPhoto) null else "play") { if (needsPhoto) choose() else vm.shareRun(r, pager.currentPage, photo) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TextLink(if (photo == null) "Use my own photo" else "Change the photo") { choose() } }
    Small("The photo is only read to make this picture. It isn't stored by the app.", align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
