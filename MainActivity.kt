package com.example.monethelper

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.widget.Toast

class MainActivity : ComponentActivity() {
    private var playerThread: Thread? = null
    private var playing = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContent {
            MonetHelperApp()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MonetReminders"
            val desc = "Reminders"
            val channel = NotificationChannel("monet_channel", name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = desc
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopSound()
        super.onDestroy()
    }

    // Simple procedural ASMR generator: waves (low-frequency noise), hum (sine), tapping (short bursts)
    private fun generateBuffer(sampleRate:Int, durationSec:Double, waveLevel:Double, humFreq:Double, tap:Boolean): ShortArray {
        val samples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(samples)
        val rand = java.util.Random()
        for (i in 0 until samples) {
            val t = i.toDouble()/sampleRate
            // low-frequency noise for waves
            val noise = (rand.nextDouble() * 2.0 - 1.0) * waveLevel * 0.3
            // hum sine
            val hum = sin(2.0 * PI * humFreq * t) * 0.4
            var sample = noise + hum
            // simple tapping: occasional short impulse
            if (tap && (i % (sampleRate/2) == 0)) {
                sample += 0.8 * (rand.nextDouble())
            }
            // clamp
            val s = (sample * Short.MAX_VALUE).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
            buffer[i] = s.toInt().toShort()
        }
        return buffer
    }

    private fun playLoop(sampleRate:Int = 44100, loopSec:Double = 5.0, waveLevel:Double=0.8, humFreq:Double=60.0, tap:Boolean=false) {
        stopSound()
        playing = true
        playerThread = thread {
            val buffer = generateBuffer(sampleRate, loopSec, waveLevel, humFreq, tap)
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf, AudioTrack.MODE_STREAM)
            track.play()
            while (playing) {
                val byteBuf = ByteBuffer.allocate(buffer.size * 2)
                byteBuf.order(ByteOrder.LITTLE_ENDIAN)
                for (s in buffer) byteBuf.putShort(s)
                track.write(byteBuf.array(), 0, byteBuf.position())
            }
            track.stop()
            track.release()
        }
    }

    private fun stopSound() {
        playing = false
        playerThread?.join(100)
        playerThread = null
    }

    private fun saveWav(buffer: ShortArray, sampleRate:Int, filename:String): String? {
        try {
            val file = File(getExternalFilesDir(null), filename)
            val fos = FileOutputStream(file)
            val byteRate = 16 * sampleRate * 1 / 8
            val dataSize = buffer.size * 2
            // RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToByteArray(36 + dataSize))
            fos.write("WAVE".toByteArray())
            fos.write("fmt ".toByteArray())
            fos.write(intToByteArray(16)) // Subchunk1Size
            fos.write(shortToByteArray(1)) // AudioFormat PCM
            fos.write(shortToByteArray(1)) // NumChannels
            fos.write(intToByteArray(sampleRate))
            fos.write(intToByteArray(byteRate))
            fos.write(shortToByteArray((1 * 16 / 8).toShort()))
            fos.write(shortToByteArray(16))
            fos.write("data".toByteArray())
            fos.write(intToByteArray(dataSize))
            val bb = ByteBuffer.allocate(dataSize)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            for (s in buffer) bb.putShort(s)
            fos.write(bb.array())
            fos.flush()
            fos.close()
            return file.absolutePath
        } catch (e:Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun intToByteArray(i:Int): ByteArray {
        return byteArrayOf((i and 0xff).toByte(), ((i shr 8) and 0xff).toByte(), ((i shr 16) and 0xff).toByte(), ((i shr 24) and 0xff).toByte())
    }
    private fun shortToByteArray(s:Short): ByteArray {
        return byteArrayOf((s.toInt() and 0xff).toByte(), ((s.toInt() shr 8) and 0xff).toByte())
    }

    // Compose UI
    @Composable
    fun MonetHelperApp() {
        Surface {
            Column(Modifier.padding(16.dp)) {
                Text("Monet helper", style = androidx.compose.material.MaterialTheme.typography.h5)
                Spacer(Modifier.height(8.dp))
                ChecklistSection()
                Spacer(Modifier.height(8.dp))
                IdeaGeneratorSection()
                Spacer(Modifier.height(8.dp))
                TitleTagGeneratorSection()
                Spacer(Modifier.height(8.dp))
                SchedulerSection()
                Spacer(Modifier.height(8.dp))
                AnalyticsSection()
                Spacer(Modifier.height(8.dp))
                ThumbnailTemplateSection()
                Spacer(Modifier.height(12.dp))
                Text("ASMR Generator", style = androidx.compose.material.MaterialTheme.typography.h6)
                ASMRSection()
            }
        }
    }

    @Composable
    fun ChecklistSection() {
        val prefs = LocalContext.getSharedPreferences("monet_prefs", Context.MODE_PRIVATE)
        val items = listOf(
            "1. 1,000 Subscribers",
            "2. 4,000 Watch Hours (last 12 months)",
            "3. No active copyright strikes",
            "4. Compliant content & ad-friendly",
            "5. Channel verified & 2FA enabled",
            "6. Consistent upload schedule"
        )
        Column {
            Text("Monetization Checklist")
            Spacer(Modifier.height(6.dp))
            items.forEachIndexed { idx, text ->
                var checked by remember { mutableStateOf(prefs.getBoolean("chk_\$idx", false)) }
                Row {
                    Checkbox(checked = checked, onCheckedChange = {
                        checked = it
                        prefs.edit().putBoolean("chk_\$idx", it).apply()
                    })
                    Text(text)
                }
            }
        }
    }

    @Composable
    fun IdeaGeneratorSection() {
        val coastal = listOf("Coastal Night", "Shrimp Farm", "Beach Dawn", "Tidal Pool", "Aerator Close-up")
        val format = listOf("ASMR Short", "1-2 min Tutorial", "10+ min Ambient", "POV Repair", "Time-lapse")
        val concepts = listOf("Aerator sound + waves", "Panel repair POV", "Night patrol ambience", "Tool cleaning ASMR", "Waves + tool tapping")
        var idea by remember { mutableStateOf("Press GENERATE to get an idea") }
        val ctx = LocalContext
        Column {
            Text("Idea Generator")
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = {
                    val c = coastal.random()
                    val f = format.random()
                    val co = concepts.random()
                    idea = "\$c • \$f • Concept: \$co"
                }) { Text("GENERATE IDEA") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // save to file
                    val file = File(getExternalFilesDir(null), "saved_ideas.txt")
                    file.appendText(idea + "\n")
                    Toast.makeText(this@MainActivity, "Idea saved", Toast.LENGTH_SHORT).show()
                }) { Text("SAVE") }
            }
            Spacer(Modifier.height(8.dp))
            Text(idea)
        }
    }

    @Composable
    fun TitleTagGeneratorSection() {
        var seed by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("") }
        Column {
            Text("Title & Tag Generator")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = seed, onValueChange = { seed = it }, label = { Text("Keyword / Idea") })
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = {
                    val base = if (seed.isBlank()) "Coastal Shrimp Farm ASMR" else seed.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                    title = "$base — Relaxing Night Sounds (No Face)"
                    desc = "Recorded at a real shrimp farm. Relax with coastal aerator sounds, waves, and gentle tools. Perfect for sleep & study.\n\nSubscribe for more coastal ASMR."
                    tags = listOf("#ShrimpFarm","#Aquaculture","#ASMR","#WaterSounds","#CoastalLife").joinToString(" ")
                }) { Text("GENERATE") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // copy title to clipboard
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("yt_title", title))
                    Toast.makeText(this@MainActivity, "Title copied", Toast.LENGTH_SHORT).show()
                }) { Text("COPY TITLE") }
            }
            Spacer(Modifier.height(6.dp))
            Text("Title: \$title")
            Text("Description: \$desc")
            Text("Tags: \$tags")
        }
    }

    @Composable
    fun SchedulerSection() {
        val prefs = getSharedPreferences("monet_prefs", Context.MODE_PRIVATE)
        var scheduleText by remember { mutableStateOf(prefs.getString("next_schedule", "No schedule set") ?: "No schedule set") }
        Column {
            Text("Upload Scheduler")
            Spacer(Modifier.height(6.dp))
            Text("Next schedule: \$scheduleText")
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = {
                    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, 1) }
                    setReminder(this@MainActivity, cal.timeInMillis, "Time to record/upload your ASMR video!")
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    val formatted = fmt.format(java.util.Date(cal.timeInMillis))
                    prefs.edit().putString("next_schedule", formatted).apply()
                    scheduleText = formatted
                    Toast.makeText(this@MainActivity, "Reminder set", Toast.LENGTH_SHORT).show()
                }) { Text("REMIND IN 1 MIN (DEMO)") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val csv = "idea,title,tags\nAerator Night,\"Aerator Night ASMR\",\"#ShrimpFarm #ASMR\"\n"
                    val file = File(getExternalFilesDir(null), "export_ideas.csv")
                    file.writeText(csv)
                    Toast.makeText(this@MainActivity, "Exported to \${file.absolutePath}", Toast.LENGTH_LONG).show()
                }) { Text("EXPORT CSV") }
            }
        }
    }

    @Composable
    fun AnalyticsSection() {
        val prefs = getSharedPreferences("monet_prefs", Context.MODE_PRIVATE)
        var subs by remember { mutableStateOf(prefs.getInt("subs", 0)) }
        var watchHours by remember { mutableStateOf(prefs.getFloat("watchHours", 0f)) }
        Column {
            Text("Manual Analytics (input your real numbers)")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = subs.toString(), onValueChange = { subs = it.toIntOrNull() ?: 0 }, label = { Text("Subscribers") })
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = watchHours.toString(), onValueChange = { watchHours = it.toFloatOrNull() ?: 0f }, label = { Text("Watch Hours (last 12 months)") })
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = {
                    prefs.edit().putInt("subs", subs).putFloat("watchHours", watchHours).apply()
                    Toast.makeText(this@MainActivity, "Analytics saved", Toast.LENGTH_SHORT).show()
                }) { Text("SAVE") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val ready = subs >= 1000 && watchHours >= 4000f
                    Toast.makeText(this@MainActivity, if (ready) "Channel eligible (by numbers)!" else "Not yet eligible", Toast.LENGTH_SHORT).show()
                }) { Text("CHECK ELIGIBILITY") }
            }
        }
    }

    @Composable
    fun ThumbnailTemplateSection() {
        Column {
            Text("Thumbnail Text Templates")
            Spacer(Modifier.height(6.dp))
            Text("Use bold short text + close-up image. Examples:")
            Text("- \"Deep Sleep Aerator\"")
            Text("- \"No-Face Technician ASMR\"")
            Text("- \"Coastal Night Sounds\"")
        }
    }

    @Composable
    fun ASMRSection() {
        var playing by remember { mutableStateOf(false) }
        var selection by remember { mutableStateOf(0) }
        Column {
            Row {
                Button(onClick = {
                    // Waves preset
                    playLoop(loopSec = 5.0, waveLevel = 1.0, humFreq = 40.0, tap = false)
                    playing = true
                }) { Text("Play Waves") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // Aerator hum + waves
                    playLoop(loopSec = 5.0, waveLevel = 0.8, humFreq = 120.0, tap = false)
                    playing = true
                }) { Text("Play Aerator") }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = {
                    // Tapping preset
                    playLoop(loopSec = 5.0, waveLevel = 0.2, humFreq = 80.0, tap = true)
                }) { Text("Play Tapping") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    stopSound()
                }) { Text("Stop") }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                // Export 10s WAV of current preset (simple demo)
                val buf = generateBuffer(44100, 10.0, 0.8, 60.0, false)
                val path = saveWav(buf, 44100, "asmr_export.wav")
                Toast.makeText(this@MainActivity, "Saved: \$path", Toast.LENGTH_LONG).show()
            }) { Text("Export 10s WAV") }
        }
    }

    // Reminder helper
    private fun setReminder(context: Context, whenMillis: Long, message: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("msg", message)
        }
        val pending = PendingIntent.getBroadcast(context, (Math.random()*100000).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        }
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val msg = intent?.getStringExtra("msg") ?: "Reminder"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = Notification.Builder(context, "monet_channel")
                .setContentTitle("Monet helper")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
            nm.notify((Math.random()*100000).toInt(), notif)
        }
    }
}