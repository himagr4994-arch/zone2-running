package app.zone2running

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LiveEvent(val kind: String, val text: String)        // kind: step, km, cap, done

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p = Math.PI / 180
    val a = sin((lat2 - lat1) * p / 2).pow(2) + cos(lat1 * p) * cos(lat2 * p) * sin((lon2 - lon1) * p / 2).pow(2)
    return 2 * 6371000.0 * asin(sqrt(a))
}

/**
 * Everything a run in progress knows: time, distance, heart rate, the guided steps and the kilometre splits.
 * Plain Kotlin so it can be tested off the phone. Call [tick] once a second while the run is not paused.
 */
class RunTracker(private val steps: List<Step>, private val zb: List<Int>) {
    var elapsed = 0; private set
    var meters = 0.0; private set
    var hr = 0; private set
    var hrMax = 0; private set
    val zoneSec = IntArray(5)
    val splits = ArrayList<Split>()
    var stepIndex = 0; private set
    private var stepElapsed = 0
    private var hrSum = 0L; private var hrN = 0
    private var kmStart = 0; private var kmHrSum = 0L; private var kmHrN = 0
    private var lastLat = Double.NaN; private var lastLon = 0.0; private var lastFixMs = 0L
    private var overCap = 0; private var lastCapCue = -999
    private val recent = ArrayDeque<Pair<Int, Double>>()

    fun zoneOf(h: Int): Int { var z = 1; for (i in 1..4) if (h >= zb[i]) z++; return z }
    val step: Step? get() = steps.getOrNull(stepIndex)
    val next: Step? get() = steps.getOrNull(stepIndex + 1)
    val stepLeft: Int get() = step?.takeIf { it.sec > 0 }?.let { it.sec - stepElapsed } ?: 0
    val avgHr: Int get() = if (hrN == 0) 0 else (hrSum.toDouble() / hrN).roundToInt()

    /** Pace over the last 45 seconds, in seconds per km; null while standing still or before GPS settles. */
    fun paceNow(): Double? {
        val a = recent.firstOrNull() ?: return null; val b = recent.last()
        val dm = b.second - a.second; val dt = b.first - a.first
        return if (dm >= 20 && dt >= 10) dt / dm * 1000 else null
    }

    fun tick(hrNow: Int): List<LiveEvent> {
        val out = ArrayList<LiveEvent>()
        elapsed++; stepElapsed++
        hr = hrNow
        if (hrNow > 0) { hrSum += hrNow; hrN++; kmHrSum += hrNow; kmHrN++; if (hrNow > hrMax) hrMax = hrNow; zoneSec[zoneOf(hrNow) - 1]++ }
        recent.addLast(elapsed to meters); while (recent.size > 1 && elapsed - recent.first().first > 45) recent.removeFirst()
        val cur = step
        if (cur != null && cur.sec > 0 && stepElapsed >= cur.sec) {
            stepIndex++; stepElapsed = 0
            val nx = step
            out += if (nx == null) LiveEvent("done", "Session complete. Keep going or finish when you're ready.")
            else LiveEvent("step", nx.label + (if (nx.sec >= 60) ", ${nx.sec / 60} minute${if (nx.sec / 60 == 1) "" else "s"}" else if (nx.sec > 0) ", ${nx.sec} seconds" else "") + ".")
        }
        // On easy blocks, twenty seconds above the Zone 2 cap earns one cue a minute.
        val easyBlock = (step ?: steps.lastOrNull())?.let { it.zHi <= 2 } ?: true
        if (easyBlock && hrNow >= zb[2]) overCap++ else overCap = 0
        if (overCap >= 20 && elapsed - lastCapCue >= 60) { lastCapCue = elapsed; out += LiveEvent("cap", "Ease off. Heart rate $hrNow.") }
        return out
    }

    /** A GPS fix. Poor or impossible ones are dropped. Returns an event when a kilometre completes. */
    fun fix(lat: Double, lon: Double, accuracyM: Float, timeMs: Long): LiveEvent? {
        if (accuracyM > 30f) return null
        if (lastLat.isNaN()) { lastLat = lat; lastLon = lon; lastFixMs = timeMs; return null }
        val d = haversine(lastLat, lastLon, lat, lon); val dt = (timeMs - lastFixMs) / 1000.0
        if (dt <= 0 || d < 2.0) return null                           // standing still, or jitter
        if (d / dt > 8.0) { lastLat = lat; lastLon = lon; lastFixMs = timeMs; return null }   // faster than any runner: a GPS jump
        meters += d; lastLat = lat; lastLon = lon; lastFixMs = timeMs
        if ((meters / 1000).toInt() > splits.size) {
            val sec = elapsed - kmStart
            splits += Split(sec, if (kmHrN == 0) 0 else (kmHrSum.toDouble() / kmHrN).roundToInt())
            kmStart = elapsed; kmHrSum = 0; kmHrN = 0
            return LiveEvent("km", "Kilometre ${splits.size}. ${sec / 60} ${(sec % 60).toString().padStart(2, '0')}.")
        }
        return null
    }

    /** After a pause, the first fix must not bridge the gap. */
    fun resumed() { lastLat = Double.NaN; recent.clear() }

    fun toRun(date: LocalDate, type: String, startMin: Int) = Run(
        "live_${date.toString().replace("-", "")}_${System.currentTimeMillis().toString(36)}", date, type, (meters / 10).roundToInt() / 100.0, elapsed,
        avgHr, hrMax, source = "live", zoneSec = if (hrN >= 30) zoneSec.toList() else emptyList(), startMin = startMin, splits = splits.toList(),
    )
}

data class LiveSnap(
    val active: Boolean = false, val paused: Boolean = false, val elapsed: Int = 0, val meters: Double = 0.0, val hr: Int = 0, val zone: Int = 0,
    val stepIndex: Int = 0, val stepLabel: String = "", val stepLeft: Int = 0, val stepKind: String = "", val nextLabel: String = "", val pace: Double? = null,
    val gps: String = "Looking for GPS", val sensor: String = "", val splits: List<Split> = emptyList(), val avgHr: Int = 0, val lastCue: String = "",
)

/** The bridge between the run service and the screen. */
object Live {
    var snap by mutableStateOf(LiveSnap())
    var result by mutableStateOf<Run?>(null)
    var steps: List<Step> = emptyList(); var zb: List<Int> = listOf(0, 0, 0, 0, 0); var type = "easy"
    var sensorAddr = ""; var voice = true
    internal var service: RunService? = null

    fun start(ctx: Context, steps: List<Step>, zb: List<Int>, type: String, sensorAddr: String, voice: Boolean) {
        this.steps = steps; this.zb = zb; this.type = type; this.sensorAddr = sensorAddr; this.voice = voice; result = null
        snap = LiveSnap(active = true, stepLabel = steps.firstOrNull()?.label ?: "", sensor = if (sensorAddr.isEmpty()) "" else "Connecting")
        val i = Intent(ctx, RunService::class.java)
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
    }
    fun pause() { service?.setPaused(true) }
    fun resume() { service?.setPaused(false) }
    fun finish() { service?.finish(save = true) ?: run { snap = LiveSnap() } }
    fun discard() { service?.finish(save = false) ?: run { snap = LiveSnap() } }
}

private val HR_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HR_MEASURE = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * A Bluetooth heart-rate sensor that speaks the standard Heart Rate profile: chest straps, arm bands,
 * and watches with a "broadcast heart rate" mode. Every call is guarded, because Bluetooth can be off or refused.
 */
@SuppressLint("MissingPermission")
class HeartSensor(private val ctx: Context) {
    @Volatile var hr = 0; private set
    @Volatile var status = ""; private set
    @Volatile private var lastBeatMs = 0L
    private var gatt: BluetoothGatt? = null
    private var scanCb: ScanCallback? = null
    private val adapter: BluetoothAdapter? get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Latest heart rate, or 0 when nothing has arrived for ten seconds. */
    fun current() = if (System.currentTimeMillis() - lastBeatMs < 10_000) hr else 0

    fun scan(found: (name: String, address: String) -> Unit): Boolean = try {
        val sc = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false
        stopScan()
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) { try { found(r.device.name ?: "Heart rate sensor", r.device.address) } catch (e: Exception) { } }
        }
        scanCb = cb
        sc.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        true
    } catch (e: Exception) { false }

    fun stopScan() { try { scanCb?.let { adapter?.bluetoothLeScanner?.stopScan(it) } } catch (e: Exception) { }; scanCb = null }

    fun connect(address: String) {
        try {
            val dev: BluetoothDevice = adapter?.getRemoteDevice(address) ?: run { status = "Bluetooth is off"; return }
            status = "Connecting"
            gatt = dev.connectGatt(ctx, true, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
                    try { if (newState == BluetoothProfile.STATE_CONNECTED) { status = "Connected"; g.discoverServices() } else if (newState == BluetoothProfile.STATE_DISCONNECTED) status = "Reconnecting" } catch (e: Exception) { }
                }
                override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
                    try {
                        val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASURE) ?: run { status = "No heart rate on this device"; return }
                        g.setCharacteristicNotification(ch, true)
                        ch.getDescriptor(CCCD)?.let { d ->
                            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            else { @Suppress("DEPRECATION") d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; @Suppress("DEPRECATION") g.writeDescriptor(d) }
                        }
                    } catch (e: Exception) { status = "Sensor error" }
                }
                @Deprecated("Called on Android 12 and older")
                override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) { @Suppress("DEPRECATION") parse(ch.value) }
                override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) = parse(value)
            })
        } catch (e: Exception) { status = "Bluetooth not allowed" }
    }

    /** Heart Rate Measurement: bit 0 of the flags says whether the value is one byte or two. */
    private fun parse(v: ByteArray?) {
        if (v == null || v.size < 2) return
        val bpm = if (v[0].toInt() and 1 == 0) v[1].toInt() and 0xFF else if (v.size >= 3) (v[1].toInt() and 0xFF) or ((v[2].toInt() and 0xFF) shl 8) else return
        if (bpm in 30..240) { hr = bpm; lastBeatMs = System.currentTimeMillis() }
    }

    fun close() { stopScan(); try { gatt?.disconnect(); gatt?.close() } catch (e: Exception) { }; gatt = null; status = "" }
}

/** Keeps a run going with the screen off: GPS, the heart-rate sensor, the guided steps and the spoken cues. */
class RunService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tracker: RunTracker
    private var sensor: HeartSensor? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var paused = false
    private var gpsState = "Looking for GPS"
    private var lastFixAt = 0L
    private var startMin = 0
    private var lastCue = ""
    private val channel = "run"

    private val locListener = LocationListener { loc: Location ->
        lastFixAt = System.currentTimeMillis()
        gpsState = if (loc.accuracy <= 30f) "GPS locked" else "Weak GPS"
        if (!paused) tracker.fix(loc.latitude, loc.longitude, loc.accuracy, loc.time)?.let { cue(it) }
    }

    private val ticker = object : Runnable {
        override fun run() {
            try {
                if (!paused) tracker.tick(sensor?.current() ?: 0).forEach { cue(it) }
                if (lastFixAt > 0 && System.currentTimeMillis() - lastFixAt > 15_000) gpsState = "GPS lost"
                publish()
                if (tracker.elapsed % 15 == 0) notifyNow()
            } catch (e: Exception) { }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Live.service != null) return START_NOT_STICKY
        Live.service = this
        tracker = RunTracker(Live.steps, Live.zb)
        startMin = LocalTime.now().let { it.hour * 60 + it.minute }
        try {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(NotificationChannel(channel, "Run in progress", NotificationManager.IMPORTANCE_LOW))
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(7, notification())
        } catch (e: Exception) { /* without foreground status the run still works while the screen is on */ }
        try {
            @SuppressLint("MissingPermission")
            val unused = (getSystemService(Context.LOCATION_SERVICE) as LocationManager).requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper())
        } catch (e: Exception) { gpsState = "GPS not available" }
        if (Live.sensorAddr.isNotEmpty()) sensor = HeartSensor(this).also { it.connect(Live.sensorAddr) }
        if (Live.voice) try { tts = TextToSpeech(this) { ok -> ttsReady = ok == TextToSpeech.SUCCESS } } catch (e: Exception) { }
        handler.postDelayed(ticker, 1000)
        return START_NOT_STICKY
    }

    private fun cue(e: LiveEvent) {
        lastCue = e.text
        try {
            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val pattern = if (e.kind == "cap") longArrayOf(0, 250, 150, 250) else longArrayOf(0, 400)
            vib?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            if (Live.voice && ttsReady) tts?.speak(e.text, TextToSpeech.QUEUE_ADD, null, "cue${tracker.elapsed}")
        } catch (ex: Exception) { }
    }

    private fun publish() {
        val st = tracker.step
        Live.snap = LiveSnap(true, paused, tracker.elapsed, tracker.meters, tracker.hr, if (tracker.hr > 0) tracker.zoneOf(tracker.hr) else 0, tracker.stepIndex,
            st?.label ?: "Free running", tracker.stepLeft, st?.kind ?: "", tracker.next?.label ?: "", tracker.paceNow(), gpsState, sensor?.status ?: "", tracker.splits.toList(), tracker.avgHr, lastCue)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (::tracker.isInitialized) "${fmtDur(tracker.elapsed)} · ${U.dist(tracker.meters / 1000)}" + (if (tracker.hr > 0) " · ${tracker.hr} bpm" else "") else "Starting"
        return NotificationCompat.Builder(this, channel).setSmallIcon(R.drawable.ic_stat_heart).setContentTitle(if (paused) "Run paused" else "Run in progress")
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build()
    }
    private fun notifyNow() { try { getSystemService(NotificationManager::class.java)?.notify(7, notification()) } catch (e: Exception) { } }

    fun setPaused(p: Boolean) { paused = p; if (!p) tracker.resumed(); publish(); notifyNow() }

    fun finish(save: Boolean) {
        if (save && tracker.elapsed >= 60) Live.result = tracker.toRun(LocalDate.now(), Live.type, startMin)
        Live.snap = LiveSnap()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locListener) } catch (e: Exception) { }
        sensor?.close()
        try { tts?.shutdown() } catch (e: Exception) { }
        Live.service = null
        if (Live.snap.active) Live.snap = LiveSnap()
        super.onDestroy()
    }
}
