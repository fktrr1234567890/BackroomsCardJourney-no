package com.backrooms.cardjourney

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlin.math.PI
import kotlin.math.sin

/**
 * 一套背景音乐参数：不同层级主题用不同的根音、音阶、节奏和噪声比例，
 * 这样每个大区听起来都不一样，不再是同一段嗡嗡声循环到底。
 */
data class BgmTheme(
    val name: String,
    val drone: List<Double>,   // 持续低音频率
    val scale: List<Double>,   // 旋律可用频率
    val interval: Double,      // 每个音符间隔（秒）
    val buzz: Double,          // 荧光灯电流嗡鸣强度
    val noise: Double,         // 底噪强度
    val density: Double        // 旋律触发概率
)

object BgmThemes {
    private val pentatonic = listOf(0.0, 3.0, 5.0, 7.0, 10.0, 12.0)

    private fun scaleOf(root: Double, steps: List<Double>): List<Double> =
        steps.map { root * Math.pow(2.0, it / 12.0) }

    fun forStage(index: Int, theme: Int): BgmTheme {
        // 同一主题内的不同关卡也做细微移调，避免"每六关重复一次"的听感
        val detune = ((index % 7) - 3) * 0.6
        return when (theme) {
            0 -> BgmTheme(
                "黄色走廊",
                listOf(55.0 + detune, 110.0 + detune * 2, 33.0),
                scaleOf(220.0 + detune, pentatonic),
                0.9, 0.35, 0.03, 0.35
            )
            1 -> BgmTheme(
                "工业腔体",
                listOf(45.0 + detune, 90.0 + detune * 2),
                scaleOf(196.0 + detune, listOf(0.0, 2.0, 5.0, 7.0, 9.0, 12.0)),
                0.6, 0.2, 0.06, 0.45
            )
            2 -> BgmTheme(
                "完全黑暗",
                listOf(33.0, 66.0 + detune),
                scaleOf(110.0, listOf(0.0, 5.0, 7.0)),
                2.2, 0.05, 0.02, 0.14
            )
            3 -> BgmTheme(
                "冷色办公",
                listOf(60.0, 120.0 + detune),
                scaleOf(261.6 + detune, listOf(0.0, 2.0, 4.0, 7.0, 9.0, 12.0)),
                0.75, 0.6, 0.03, 0.3
            )
            4 -> BgmTheme(
                "血肉有机",
                listOf(48.0 + detune, 72.0, 24.0),
                scaleOf(174.0, listOf(0.0, 1.0, 4.0, 6.0, 8.0, 11.0)),
                1.1, 0.1, 0.04, 0.28
            )
            else -> BgmTheme(
                "紫色虚空",
                listOf(40.0, 80.0 + detune, 160.0),
                scaleOf(329.6, listOf(0.0, 3.0, 7.0, 10.0, 14.0, 17.0)),
                1.4, 0.15, 0.02, 0.22
            )
        }
    }
}

class BgmPlayer {

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var volume = 70

    fun isPlaying(): Boolean = running

    fun start(theme: BgmTheme, vol: Int) {
        stop()
        volume = vol
        if (vol <= 0) return
        running = true
        thread = Thread {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            try {
                val loop = renderLoop(theme, sampleRate)
                val t = buildTrack(sampleRate, bufferSize)
                track = t
                t.setVolume(vol / 100f)
                t.play()
                var offset = 0
                while (running && !Thread.currentThread().isInterrupted) {
                    val chunk = if (offset >= loop.size) 0 else minOf(4096, loop.size - offset)
                    if (chunk <= 0) {
                        offset = 0
                        continue
                    }
                    t.write(loop, offset, chunk)
                    offset += chunk
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        thread?.start()
    }

    fun setVolume(vol: Int) {
        volume = vol
        try {
            track?.setVolume(vol / 100f)
        } catch (e: Exception) { }
        if (vol <= 0) stop()
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        try {
            track?.stop()
            track?.release()
        } catch (e: Exception) { }
        track = null
    }

    // AudioTrack.Builder 是 API 23 才提供的，minSdk 21 的机器必须走老构造函数，否则闪退
    private fun buildTrack(sampleRate: Int, bufferSize: Int): AudioTrack =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM
            )
        }

    /** 渲染一段可循环的 8 秒音乐：底噪 + 电流声 + 稀疏旋律 */
    private fun renderLoop(theme: BgmTheme, sampleRate: Int): ShortArray {
        val seconds = 8.0
        val n = (sampleRate * seconds).toInt()
        val out = ShortArray(n)
        val rand = java.util.Random(theme.name.hashCode().toLong())

        // 旋律事件
        val events = ArrayList<DoubleArray>()
        var t = 0.6 + rand.nextDouble() * 0.8
        while (t < seconds - 1.0) {
            if (rand.nextDouble() < theme.density) {
                val freq = theme.scale[rand.nextInt(theme.scale.size)]
                val amp = 0.25 + rand.nextDouble() * 0.35
                events.add(doubleArrayOf(t, freq, amp))
            }
            t += theme.interval * (0.7 + rand.nextDouble() * 0.6)
        }

        val buzzFreq = 120.0
        for (i in 0 until n) {
            val time = i.toDouble() / sampleRate
            var s = 0.0
            // 低音持续层
            for ((idx, f) in theme.drone.withIndex()) {
                val ph = 2.0 * PI * f * time
                s += sin(ph) * (0.05 + 0.02 * idx)
                // 缓慢起伏，让女声/低音不完全机械
                s += sin(ph * 1.005 + 0.7) * 0.02
            }
            // 荧光灯电流声
            if (theme.buzz > 0.0) {
                val ph = 2.0 * PI * buzzFreq * time
                s += sin(ph) * theme.buzz * 0.04
                s += sin(ph * 2.0) * theme.buzz * 0.012
            }
            // 底噪
            s += (rand.nextDouble() * 2 - 1) * theme.noise * 0.5
            // 旋律：每个音符带指数衰减包络
            for (e in events) {
                val dt = time - e[0]
                if (dt >= 0.0 && dt < 1.6) {
                    val env = Math.exp(-dt * 3.2) * e[2]
                    s += sin(2.0 * PI * e[1] * dt) * env * 0.55
                    s += sin(2.0 * PI * e[1] * 1.5 * dt) * env * 0.18
                }
            }
            val v = (s * Short.MAX_VALUE).coerceIn(-Short.MAX_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
            out[i] = v.toInt().toShort()
        }
        return out
    }
}
