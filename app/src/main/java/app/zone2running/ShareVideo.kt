package app.zone2running

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * The animated share: the same picture drawing itself over three seconds, then holding. Each frame is drawn
 * the way the still is and handed to the phone's own H.264 encoder; nothing is uploaded anywhere to make it.
 */
object ShareVideo {
    const val FPS = 30
    private const val DRAW = 96        // 3.2 s of drawing
    private const val HOLD = 42        // 1.4 s on the finished picture
    private const val SCALE = 2 / 3f   // 720 wide: plenty for a phone screen, and a third of the work

    /** Makes the video in the app's private cache and returns it. [onProgress] gets 0..1 as frames are finished. */
    fun make(ctx: Context, r: Run, style: Int, photo: Bitmap?, story: Boolean, zone: (Int) -> Int, onProgress: (Float) -> Unit): File {
        val out = File(File(ctx.cacheDir, "share").apply { mkdirs() }, "zone2-run.mp4")
        if (out.exists()) out.delete()
        val first = ShareCard.render(ctx, r, style, photo, story, SCALE, 0f, zone)
        val w = first.width; val h = first.height; first.recycle()
        val frames = DRAW + HOLD
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 7_000_000); setInteger(MediaFormat.KEY_FRAME_RATE, FPS); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            val info = MediaCodec.BufferInfo(); val pixels = IntArray(w * h); val line = ByteArray(w)
            var track = -1; var muxing = false; var i = 0; var fed = false; var done = false; var held: Bitmap? = null
            while (!done) {
                if (!fed) {
                    val at = codec.dequeueInputBuffer(10_000)
                    if (at >= 0) {
                        if (i >= frames) { codec.queueInputBuffer(at, 0, 0, i * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM); fed = true }
                        else {
                            // The held frames are all the same picture: draw it once.
                            val bmp = if (i < DRAW) ShareCard.render(ctx, r, style, photo, story, SCALE, i / (DRAW - 1f), zone) else held ?: ShareCard.render(ctx, r, style, photo, story, SCALE, 1f, zone).also { held = it }
                            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                            if (i < DRAW) bmp.recycle()
                            val image = codec.getInputImage(at) ?: throw IllegalStateException("This phone's video encoder can't take frames this way")
                            toYuv(image, pixels, w, h, line)
                            codec.queueInputBuffer(at, 0, w * h * 3 / 2, i * 1_000_000L / FPS, 0)
                            i++; onProgress(i / frames.toFloat())
                        }
                    }
                }
                val got = codec.dequeueOutputBuffer(info, 10_000)
                if (got == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(codec.outputFormat); muxer.start(); muxing = true }
                else if (got >= 0) {
                    val buf = codec.getOutputBuffer(got)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (buf != null && info.size > 0 && muxing) { buf.position(info.offset); buf.limit(info.offset + info.size); muxer.writeSampleData(track, buf, info) }
                    codec.releaseOutputBuffer(got, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                }
            }
            held?.recycle()
            muxer.stop()
        } finally {
            try { codec.stop() } catch (e: Exception) { }
            codec.release(); try { muxer.release() } catch (e: Exception) { }
        }
        return out
    }

    /**
     * Colour pixels into the encoder's own layout. Encoders differ in how they interleave the two colour
     * planes, so each value is placed using the strides the encoder reports instead of assuming one layout.
     */
    private fun toYuv(image: android.media.Image, px: IntArray, w: Int, h: Int, line: ByteArray) {
        val y = image.planes[0]; val u = image.planes[1]; val v = image.planes[2]
        val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
        for (row in 0 until h) {
            val o = row * w
            for (col in 0 until w) { val c = px[o + col]; line[col] = (((66 * ((c shr 16) and 255) + 129 * ((c shr 8) and 255) + 25 * (c and 255) + 128) shr 8) + 16).toByte() }
            if (y.pixelStride == 1) { yb.position(row * y.rowStride); yb.put(line, 0, w) } else for (col in 0 until w) yb.put(row * y.rowStride + col * y.pixelStride, line[col])
        }
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            val c = px[row * 2 * w + col * 2]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            ub.put(row * u.rowStride + col * u.pixelStride, (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte())
            vb.put(row * v.rowStride + col * v.pixelStride, (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte())
        }
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share run").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Copies the video into the phone's gallery, under Movies/Zone 2 Running. False where Android needs a storage permission for that. */
    fun save(ctx: Context, file: File, name: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Zone 2 Running")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } } ?: return false
        return true
    }
}
