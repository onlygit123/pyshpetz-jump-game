package com.pyzhpets.jump

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

// true  — нарисованный пингвин из assets/art/penguin_*.png
// false — прежний пингвин из геометрических фигур
private const val NEW_PENGUIN = true

// Физика прыжка (используется и в update(), и в генераторе платформ).
private const val GRAVITY = 950f
private const val JUMP_V = 545f      // обычный прыжок: макс. подъём = v²/(2g) ≈ 156
private const val SPRING_V = 850f

class MainActivity : Activity(), SensorEventListener {
    private data class Platform(
        val x: Float, val y: Float, val width: Float, val type: Int, var phase: Float,
        var broken: Boolean = false, val amp: Float = 45f, val speed: Float = 2.4f
    )
    private data class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)
    private lateinit var game: GameView
    private var sensorManager: SensorManager? = null
    private var tilt = 0f
    private var keyboardAxis = 0f
    private var leftHeld = false
    private var rightHeld = false
    private fun updateKeyboard() { keyboardAxis = (if (rightHeld) 1f else 0f) - (if (leftHeld) 1f else 0f) }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                leftHeld = event.action == KeyEvent.ACTION_DOWN; updateKeyboard(); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                rightHeld = event.action == KeyEvent.ACTION_DOWN; updateKeyboard(); return true
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && ::game.isInitialized) game.actionKey()
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && ::game.isInitialized) game.pauseKey()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { leftHeld = true; updateKeyboard(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { rightHeld = true; updateKeyboard(); return true }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER -> { if (::game.isInitialized) game.actionKey(); return true }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P -> { if (::game.isInitialized) game.pauseKey(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { leftHeld = false; updateKeyboard(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { rightHeld = false; updateKeyboard(); return true }
        }
        return super.onKeyUp(keyCode, event)
    }
    private var music: MediaPlayer? = null
    private var playingMenu = true
    private var pool: SoundPool? = null
    private val effects = hashMapOf<String, Int>()
    private val prefs by lazy { getSharedPreferences("pyzhpets", Context.MODE_PRIVATE) }
    private val musicVolume get() = prefs.getFloat("music", 0.28f)
    private val effectsVolume get() = prefs.getFloat("effects", 0.35f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        // Показываем игру ДО инициализации звукового движка: отсутствие аудио
        // или несовместимый декодер на эмуляторе не должны закрывать приложение.
        game = GameView(this)
        game.isFocusable = true
        game.isFocusableInTouchMode = true
        setContentView(game)
        game.requestFocus()
        initializeAudioSafely()
    }
    private fun initializeAudioSafely() {
        try {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val newPool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attr).build()
            pool = newPool
            listOf(
                "bounce" to R.raw.bounce, "powerup" to R.raw.powerup,
                "crack" to R.raw.crack, "fail" to R.raw.fail, "tap" to R.raw.tap
            ).forEach { (name, res) ->
                try { effects[name] = newPool.load(this, res, 1) }
                catch (e: Exception) { android.util.Log.w("PyzhpetsAudio", "SFX $name unavailable", e) }
            }
            startMusic(true)
        } catch (e: Exception) {
            android.util.Log.e("PyzhpetsAudio", "Audio unavailable; continuing silently", e)
        }
    }
    private fun startMusic(menu: Boolean) {
        try {
            if (music != null && playingMenu == menu) {
                music?.setVolume(musicVolume, musicVolume)
                if (music?.isPlaying == false) music?.start()
                return
            }
            playingMenu = menu
            music?.release()
            music = null
            music = MediaPlayer.create(this, if (menu) R.raw.menu_theme else R.raw.play_theme)
            music?.apply {
                isLooping = true
                setVolume(musicVolume, musicVolume)
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("PyzhpetsAudio", "Music unavailable; continuing silently", e)
            try { music?.release() } catch (_: Exception) { }
            music = null
        }
    }
    private fun refreshVolume() {
        try { music?.setVolume(musicVolume, musicVolume) } catch (_: Exception) { }
    }
    private fun sfx(name: String) {
        try {
            effects[name]?.let { id ->
                if (id > 0) pool?.play(id, effectsVolume, effectsVolume, 1, 0, 1f)
            }
        } catch (e: Exception) {
            android.util.Log.w("PyzhpetsAudio", "SFX playback failed", e)
        }
    }
    override fun onResume() {
        super.onResume()
        try {
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        } catch (e: Exception) {
            android.util.Log.w("Pyzhpets", "Accelerometer unavailable", e)
        }
        try { if (music?.isPlaying == false) music?.start() } catch (_: Exception) { }
    }
    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        try { if (music?.isPlaying == true) music?.pause() } catch (_: Exception) { }
        if (::game.isInitialized) game.pauseFromSystem()
    }
    override fun onDestroy() {
        try { music?.release() } catch (_: Exception) { }
        try { pool?.release() } catch (_: Exception) { }
        super.onDestroy()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent) { if(event.sensor.type == Sensor.TYPE_ACCELEROMETER) tilt = (tilt * 0.8f + (-event.values[0] / 6.0f).coerceIn(-1.5f, 1.5f) * 0.2f) }
    override fun onBackPressed() { if (game.state != 0) game.state = 0.also { startMusic(true) } else super.onBackPressed() }

    private inner class GameView(ctx: Context) : View(ctx) {
        var state = 0 // 0 menu, 1 playing, 2 pause, 3 dead, 4 settings, 5 records
        private val W = 480f; private val H = 800f
        private val p = Paint(3)
        private val doodleFont: Typeface by lazy { try { Typeface.createFromAsset(context.assets, "fonts/overdoze_sans.ttf") } catch (_: Exception) { Typeface.create("casual", Typeface.NORMAL) } }
        private var rng = Random(System.nanoTime())
        private val platforms = mutableListOf<Platform>()
        private val sparks = mutableListOf<Spark>()
        private var x = 240f; private var y = 155f; private var vy = 0f
        private var camera = 0f; private var highest = 0f; private var score = 0
        private var best = prefs.getInt("best", 0)
        private var t = 0f; private var lastFrame = 0L; private var nextPlatform = 90f
        private var touchX: Float? = null
        private var scrollSky = 0f
        private var stageMessage = ""
        private var stageTimer = 0f
        private var lastStage = -1
        // «Опорная цепочка» платформ — по ней всегда можно подняться (см. spawn()).
        private var chainX = 240f
        private var chainAmp = 0f
        private var breakRun = 0
        // Поза и направление пингвина.
        private var facing = -1       // -1 смотрит влево (как нарисован), 1 — вправо
        private var lean = 0f
        private var landTimer = 0f
        private val starField = Array(105) { Triple(Random(it * 197 + 31).nextFloat() * W, Random(it * 307 + 11).nextFloat() * H, 0.7f + Random(it * 47 + 3).nextFloat() * 1.7f) }
        private val cloudField = Array(7) { Triple(Random(it * 17 + 4).nextFloat() * 600f - 60f, Random(it * 43 + 6).nextFloat() * H, 0.75f + Random(it * 67 + 9).nextFloat() * 1.5f) }
        private var landingCount = 0
        private var newBestFlash = 0f
        private val rect = RectF()
        private val white = Color.WHITE
        private val ink = Color.rgb(47, 40, 33)
        private val red = Color.rgb(166, 51, 47)
        private val green = Color.rgb(86, 160, 70)
        private val gold = Color.rgb(255, 209, 81)

        // ---------- рисованные спрайты (assets/art/*.png) ----------
        private val artCache = HashMap<String, Bitmap?>()
        private val dstR = RectF()
        private fun art(name: String): Bitmap? {
            if (artCache.containsKey(name)) return artCache[name]
            val b: Bitmap? = try {
                context.assets.open("art/$name.png").use { BitmapFactory.decodeStream(it) }
            } catch (e: Throwable) { null }
            artCache[name] = b
            return b
        }
        private fun drawArt(name: String, l: Float, top: Float, w: Float, h: Float, alpha: Int = 255): Boolean {
            val b = art(name) ?: return false
            dstR.set(l, top, l + w, top + h)
            p.shader = null; p.style = Paint.Style.FILL; p.alpha = alpha.coerceIn(0, 255)
            activeCanvas.drawBitmap(b, null, dstR, p)
            p.alpha = 255
            return true
        }

        fun pauseFromSystem() { if(state == 1) state = 2 }
        fun pauseKey() { if (state == 1) state = 2 else if (state == 2) state = 1 else if (state == 4 || state == 5) state = 0 }
        fun actionKey() {
            when(state) {
                0 -> { reset(); state = 1; startMusic(false); sfx("tap") }
                2 -> { state = 1; sfx("tap") }
                3 -> { reset(); state = 1; startMusic(false); sfx("tap") }
                4 -> { state = 0; sfx("tap") }
            }
        }
        private fun zone(height: Float): Int = when {
            height < 2400f -> 0 // Земля
            height < 6500f -> 1 // Нижние облака
            height < 11500f -> 2 // Высокое небо
            height < 19000f -> 3 // Стратосфера
            height < 29000f -> 4 // Мезосфера
            height < 42000f -> 5 // Термосфера
            else -> 6 // Космос
        }
        private val zoneNames = arrayOf("ЗЕМЛЯ", "ОБЛАКА", "ВЫСОКОЕ НЕБО", "СТРАТОСФЕРА", "МЕЗОСФЕРА", "ТЕРМОСФЕРА", "КОСМОС")
        // reset() вызывает enterZone(), поэтому массив zoneNames должен быть готов раньше.
        init { reset() }
        private fun enterZone() {
            val current = zone(highest)
            if (current != lastStage) {
                lastStage = current
                stageMessage = zoneNames[current]
                stageTimer = 3.2f
                if (current > 0) sfx("powerup")
            }
        }
        private fun reset() {
            platforms.clear(); sparks.clear(); rng=Random(System.nanoTime()); x=240f; y=155f; vy=520f; camera=0f; highest=y; score=0; nextPlatform=90f
            chainX=240f; chainAmp=0f; breakRun=0; facing=-1; lean=0f; landTimer=0f
            lastStage=-1; landingCount=0; newBestFlash=0f; enterZone()
            platforms.add(Platform(155f, 90f, 170f, 0, 0f))
            while(nextPlatform < 1200f) spawn()
        }

        // ---------- генерация платформ ----------
        private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

        /** Насколько по горизонтали можно сместиться за прыжок на подъём [gap] (с запасом: 70% от скорости 280 px/с). */
        private fun reach(gap: Float): Float {
            val d = (JUMP_V * JUMP_V - 2f * GRAVITY * gap).coerceAtLeast(0f)
            val tDown = (JUMP_V + sqrt(d)) / GRAVITY   // момент, когда пингвин снова проходит эту высоту вниз
            return 0.7f * 280f * tDown
        }

        /** Тип платформы по высоте: чем выше, тем больше разновидностей. 3 («исчезающая») в опорную цепочку не попадает. */
        private fun pickType(h: Float, chain: Boolean): Int {
            val roll = rng.nextInt(100)
            val type = when {
                h < 1100f -> 0
                h < 3300f -> if (roll < 88) 0 else 4
                h < 6200f -> when { roll < 72 -> 0; roll < 87 -> 1; else -> 4 }
                h < 10000f -> when { roll < 59 -> 0; roll < 77 -> 1; roll < 90 -> 2; else -> 4 }
                h < 15500f -> when { roll < 51 -> 0; roll < 68 -> 1; roll < 82 -> 2; roll < 93 -> 3; else -> 4 }
                else -> when { roll < 45 -> 0; roll < 61 -> 1; roll < 75 -> 2; roll < 87 -> 3; roll < 94 -> 4; else -> 5 }
            }
            return if (chain && type == 3) 0 else type
        }

        private fun platformWidth(h: Float): Float {
            val prog = (h / 36000f).coerceIn(0f, 1f)
            return lerp(120f, 84f, prog) * (0.82f + rng.nextFloat() * 0.34f)
        }

        /** Свободно ли место под платформу (с учётом размаха движущихся). */
        private fun spotFree(left: Float, w: Float, yy: Float, amp: Float, pad: Float): Boolean {
            for (o in platforms) {
                if (abs(o.y - yy) >= 40f) continue
                val oa = if (o.type == 1) o.amp else 0f
                if (left - amp - pad < o.x + o.width + oa && o.x - oa < left + w + amp + pad) return false
            }
            return true
        }

        private fun spawn() {
            // Опорная цепочка: каждая следующая платформа гарантированно достижима —
            // по высоте (подъём прыжка ≈156, берём не больше 130) и по горизонтали (reach()).
            // Поверх цепочки добавляются «лишние» платформы: внизу их много, выше — всё меньше.
            val prevY = nextPlatform
            val prog = (prevY / 36000f).coerceIn(0f, 1f)
            val gap = lerp(66f, 104f, prog) + rng.nextFloat() * lerp(30f, 26f, prog)
            nextPlatform += gap
            val h = nextPlatform

            var type = pickType(h, true)
            if (type == 2 || type == 5) { if (breakRun >= 2) type = 0 else breakRun++ } else breakRun = 0
            val width = platformWidth(h)
            val amp = if (type == 1) 34f + rng.nextFloat() * 22f else 0f
            val speed = 1.6f + rng.nextFloat() * 1.4f

            val dMax = (reach(gap) - amp - chainAmp).coerceAtLeast(60f)
            val minX = 24f + width / 2 + amp
            val maxX = W - 24f - width / 2 - amp
            val lo = max(minX, chainX - dMax)
            val hi = max(lo, min(maxX, chainX + dMax))
            var cx = lo + rng.nextFloat() * (hi - lo)
            var tries = 0
            while (tries < 6 && abs(cx - chainX) < 28f) { cx = lo + rng.nextFloat() * (hi - lo); tries++ }
            chainX = cx; chainAmp = amp
            platforms.add(Platform(cx - width / 2, h, width, type, rng.nextFloat() * 6.28f, false, amp, speed))

            val extraChance = lerp(0.45f, 0f, (h / 20000f).coerceIn(0f, 1f))
            var extras = 0
            if (rng.nextFloat() < extraChance) extras++
            if (h < 3500f && rng.nextFloat() < 0.2f) extras++
            repeat(extras) { addExtra(prevY, h) }
        }

        private fun addExtra(y0: Float, y1: Float) {
            val type = pickType(y1, false)
            val width = platformWidth(y1)
            val amp = if (type == 1) 34f + rng.nextFloat() * 22f else 0f
            repeat(8) {
                val ey = y0 + (y1 - y0) * (0.25f + rng.nextFloat() * 0.5f)
                val ex = (24f + width / 2 + amp) + rng.nextFloat() * (W - 48f - width - 2f * amp)
                if (spotFree(ex - width / 2, width, ey, amp, 14f)) {
                    platforms.add(Platform(ex - width / 2, ey, width, type, rng.nextFloat() * 6.28f, false, amp, 1.6f + rng.nextFloat() * 1.4f))
                    return
                }
            }
        }

        private fun sy(worldY: Float) = H - (worldY - camera)
        private fun rounded(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
            p.color=color; p.shader=null; p.style=Paint.Style.FILL; rect.set(left,top,right,bottom); drawRound(rect,radius)
        }
        private lateinit var activeCanvas: Canvas
        private fun drawRound(bounds: RectF, r: Float) = activeCanvas.drawRoundRect(bounds, r, r, p)
        /** Текст шрифтом игры. maxW > 0 — ужать, если не помещается; outline — цвет обводки. */
        private fun label(text: String, cx: Float, cy: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER,
                          maxW: Float = 0f, outline: Int = 0, outlineW: Float = 0f) {
            p.shader=null; p.style=Paint.Style.FILL; p.typeface=doodleFont
            p.textSize=size; p.textAlign=align; p.setShadowLayer(0f,0f,0f,Color.TRANSPARENT)
            if (maxW > 0f) { val mw = p.measureText(text); if (mw > maxW) p.textSize = size * maxW / mw }
            if (outlineW > 0f) {
                p.style=Paint.Style.STROKE; p.strokeWidth=outlineW; p.strokeJoin=Paint.Join.ROUND; p.color=outline
                activeCanvas.drawText(text,cx,cy,p)
                p.style=Paint.Style.FILL
            }
            p.color=color
            activeCanvas.drawText(text,cx,cy,p)
        }
        private fun circle(cx:Float, cy:Float,r:Float,color:Int) { p.shader=null;p.color=color;p.style=Paint.Style.FILL;activeCanvas.drawCircle(cx,cy,r,p) }
        private fun bg() {
            val h = highest
            val anchors = floatArrayOf(0f, 2400f, 6500f, 11500f, 19000f, 29000f, 42000f, 54000f)
            val tops = intArrayOf(0xFF64B9F4.toInt(), 0xFF55ACEE.toInt(), 0xFF347DCE.toInt(), 0xFF214A96.toInt(), 0xFF183377.toInt(), 0xFF101F53.toInt(), 0xFF070F2F.toInt(), 0xFF040919.toInt())
            val bottoms = intArrayOf(0xFFE0F8FC.toInt(), 0xFFADDDF7.toInt(), 0xFF80BBEE.toInt(), 0xFF6F96D9.toInt(), 0xFF5263B2.toInt(), 0xFF394782.toInt(), 0xFF111B49.toInt(), 0xFF0A102E.toInt())
            var idx = 0
            while(idx < anchors.size-2 && h > anchors[idx+1]) idx++
            val f = ((h-anchors[idx])/(anchors[idx+1]-anchors[idx])).coerceIn(0f,1f)
            val top = blend(tops[idx], tops[idx+1], f)
            val bottom = blend(bottoms[idx], bottoms[idx+1], f)
            p.shader = LinearGradient(0f,0f,0f,H,top,bottom,Shader.TileMode.CLAMP)
            activeCanvas.drawRect(0f,0f,W,H,p);p.shader=null
            val stars = ((h-15500f)/21000f).coerceIn(0f,1f)
            if(stars > 0f) {
                // Broad translucent nebula bands give depth; stars remain scattered, not in rows.
                p.style = Paint.Style.FILL
                p.color = Color.argb((stars * 23f).toInt(), 80, 133, 211)
                val nebula = Path().apply { moveTo(-40f, 115f); cubicTo(100f, 205f, 285f, 35f, 535f, 160f); lineTo(535f, 300f); cubicTo(315f, 155f, 120f, 330f, -40f, 245f); close() }
                activeCanvas.drawPath(nebula, p)
                p.color = Color.argb((stars * 17f).toInt(), 134, 102, 205)
                val nebula2 = Path().apply { moveTo(-30f, 430f); cubicTo(125f, 340f, 270f, 560f, 510f, 415f); lineTo(510f, 520f); cubicTo(280f, 660f, 120f, 470f, -30f, 550f); close() }
                activeCanvas.drawPath(nebula2, p)
                starField.forEachIndexed { i, star ->
                    val xx = star.first
                    val yy = (star.second + camera * (0.018f + (i % 4) * 0.006f)) % H
                    val twinkle = 0.78f + 0.22f * sin(t * (0.55f + i % 5 * 0.13f) + i)
                    val radius = star.third * if(i % 13 == 0) 1.5f else 1f
                    circle(xx, yy, radius, Color.argb((stars * 225f * twinkle).toInt().coerceIn(0,255), 242, 247, 255))
                    if (i % 17 == 0 && stars > .55f) {
                        p.color = Color.argb((stars * 95f).toInt(), 225, 239, 255); p.strokeWidth = 1f
                        activeCanvas.drawLine(xx-radius*2.2f, yy, xx+radius*2.2f, yy, p)
                        activeCanvas.drawLine(xx, yy-radius*2.2f, xx, yy+radius*2.2f, p)
                    }
                }
            }
            // Горы стоят на земле: при подъёме они не тают, а уезжают вниз за экран с параллаксом
            // (дальний слой медленнее, ближний быстрее). Рисуются ПЕРЕД облаками, т.е. облака всегда впереди.
            val farTop = H - 300f + camera * 0.22f
            if (farTop < H) mountains("mountains_far", farTop, 300f, 0xFF98C2D4.toInt())
            val nearTop = H - 330f + camera * 0.36f
            if (nearTop < H) mountains("mountains_near", nearTop, 330f, 0xFF6FAE9C.toInt())
            if(h < 18500f) {
                val cloudAlpha = ((19000f-h)/14000f).coerceIn(0f,1f)
                cloudField.forEachIndexed { i, cloud ->
                    val s = cloud.third * 0.5f
                    val cw = 220f * s; val ch = 120f * s
                    val cx = cloud.first
                    val cy = (cloud.second + camera * (0.055f + i * 0.009f)) % (H + 200f) - 100f
                    val a = (cloudAlpha * 235f).toInt()
                    if (!drawArt("cloud_${i % 3 + 1}", cx - cw / 2, cy - ch / 2, cw, ch, a)) {
                        p.shader = null; p.style = Paint.Style.FILL; p.color = Color.argb(a, 255, 255, 255)
                        rect.set(cx - cw / 2, cy - ch / 3, cx + cw / 2, cy + ch / 3); activeCanvas.drawOval(rect, p)
                    }
                }
            }
            if(h > 34000f) {
                val alpha=((h-34000f)/7500f).coerceIn(0f,1f)
                circle(385f,152f,54f,Color.argb((alpha*210).toInt(),212,205,190))
                circle(365f,135f,12f,Color.argb((alpha*115).toInt(),145,142,146))
                circle(400f,175f,9f,Color.argb((alpha*105).toInt(),143,139,144))
            }
            p.shader = null; p.style = Paint.Style.FILL
        }
        private fun mountains(name: String, top: Float, height: Float, fallback: Int) {
            if (drawArt(name, 0f, top, W, height)) return
            p.shader = null; p.style = Paint.Style.FILL; p.color = fallback
            val path = Path().apply {
                moveTo(0f, top + height); lineTo(0f, top + height * 0.5f); lineTo(110f, top + height * 0.2f)
                lineTo(200f, top + height * 0.55f); lineTo(330f, top + height * 0.1f); lineTo(W, top + height * 0.5f)
                lineTo(W, top + height); close()
            }
            activeCanvas.drawPath(path, p)
        }
        private fun blend(a: Int, b: Int, f: Float): Int {
            fun m(sh: Int): Int {
                val ca = (a shr sh) and 255
                val cb = (b shr sh) and 255
                return (ca * (1f - f) + cb * f).toInt()
            }
            return Color.rgb(m(16), m(8), m(0))
        }
        private fun update(dt:Float) {
            t+=dt; scrollSky+=dt; stageTimer=(stageTimer-dt).coerceAtLeast(0f); newBestFlash=(newBestFlash-dt).coerceAtLeast(0f)
            for(z in sparks) { z.x+=z.vx*dt;z.y+=z.vy*dt;z.vy-=230f*dt;z.life-=dt };sparks.removeAll{it.life<=0}
            if(state!=1)return
            val previousY=y
            val desired= if(keyboardAxis != 0f) keyboardAxis else if(touchX!=null) ((touchX!!-x)/95f).coerceIn(-1.25f,1.25f) else tilt
            if (desired > 0.08f) facing = 1 else if (desired < -0.08f) facing = -1
            lean += (desired.coerceIn(-1f, 1f) * 7f - lean) * min(1f, dt * 10f)
            landTimer = (landTimer - dt).coerceAtLeast(0f)
            x+=desired*345f*dt
            if(x< -22f)x=W+22f else if(x>W+22f)x=-22f
            vy-=GRAVITY*dt; y+=vy*dt
            platforms.forEach { a ->
                if(a.type==1) a.phase+=dt*a.speed
                if(vy<0 && !a.broken && (a.type != 3 || sin(t*3 + a.phase)>-.15) && previousY-36 >= a.y && y-36 <= a.y) {
                    val px= if(a.type==1) a.x+sin(a.phase)*a.amp else a.x
                    if(x+19>px && x-19<px+a.width) {
                        landingCount++
                        y=a.y+36
                        vy=if(a.type==4) SPRING_V else JUMP_V
                        landTimer = 0.13f
                        sfx(if(a.type==4)"powerup" else if(a.type==2||a.type==5)"crack" else "bounce")
                        if(a.type==2||a.type==5)a.broken=true
                        repeat(9){ sparks.add(Spark(x, a.y, rng.nextFloat()*180-90, rng.nextFloat()*170, .35f+rng.nextFloat()*.35f,gold)) }
                    }
                }
            }
            highest=max(highest,y)
            enterZone()
            score=max(score,((highest-155)/10).toInt())
            if (score > best && newBestFlash <= 0f && score > 30) newBestFlash = 2f
            camera=max(camera,y-380f)
            while(nextPlatform < camera+1200f)spawn()
            platforms.removeAll { it.y < camera-90f }
            if(y < camera-120f) {
                state=3;sfx("fail")
                if(score>best){best=score;prefs.edit().putInt("best",best).apply()}
                startMusic(true)
            }
        }
        private fun platform(a:Platform) {
            val xx=if(a.type==1)a.x+sin(a.phase)*a.amp else a.x
            val yy=sy(a.y)
            if(yy < -35 || yy>H+35 || a.broken)return
            if(a.type==3 && sin(t*3+a.phase)<-.15f)return
            val base=when(a.type){1->Color.rgb(70,185,219);2->Color.rgb(177,126,83);3->Color.rgb(178,151,211);4->Color.rgb(250,210,82);5->Color.rgb(225,132,111);else->Color.rgb(111,194,75)}
            p.shader=null; p.style=Paint.Style.FILL
            p.color=Color.argb(45,0,0,0); rect.set(xx+2,yy+7,xx+a.width+4,yy+26); activeCanvas.drawRoundRect(rect,10f,10f,p)
            p.color=Color.rgb(37,35,31)
            val outer=Path().apply { moveTo(xx+4,yy+5); quadTo(xx+a.width*.23f,yy-2,xx+a.width*.51f,yy+1); quadTo(xx+a.width*.81f,yy-3,xx+a.width-3,yy+4); quadTo(xx+a.width+4,yy+13,xx+a.width-2,yy+20); quadTo(xx+a.width*.65f,yy+24,xx+a.width*.34f,yy+21); quadTo(xx+a.width*.1f,yy+25,xx+2,yy+18); quadTo(xx-3,yy+10,xx+4,yy+5); close() }
            activeCanvas.drawPath(outer,p)
            p.color=base
            val inner=Path().apply { moveTo(xx+6,yy+6); quadTo(xx+a.width*.24f,yy+1,xx+a.width*.5f,yy+4); quadTo(xx+a.width*.8f,yy+1,xx+a.width-5,yy+6); quadTo(xx+a.width,yy+12,xx+a.width-5,yy+17); quadTo(xx+a.width*.6f,yy+21,xx+7,yy+17); quadTo(xx+1,yy+12,xx+6,yy+6); close() }
            activeCanvas.drawPath(inner,p)
            p.style=Paint.Style.STROKE; p.strokeWidth=1.05f; p.color=Color.argb(205,255,255,255)
            activeCanvas.drawArc(xx+8,yy+4,xx+a.width-10,yy+15,192f,153f,false,p)
            for(i in 0..5) { val hx=xx+12f+i*(a.width-29f)/5f; p.color=Color.argb(135,255,255,255); activeCanvas.drawLine(hx,yy+7f,hx+4f,yy+4f,p) }
            p.style=Paint.Style.FILL
            // Значки на платформах — крупнее, чтобы читались на телефоне.
            val mid = xx + a.width / 2
            when(a.type){1->label("M",mid,yy+18f,30f,ink);2,5->label("X",mid,yy+18f,30f,ink);3->label("+",mid,yy+19f,32f,white,outline=ink,outlineW=3f);4->label("!",mid,yy+19f,32f,ink)}
        }

        /**
         * Нарисованный пингвин. (cx, feetY) — центр и низ лап, k — масштаб, pose: 0 взлёт, 1 падение, 2 приземление.
         * Спрайт нарисован лицом влево; для движения вправо зеркалится.
         */
        private fun penguinSprite(cx: Float, feetY: Float, k: Float, pose: Int, tiltDeg: Float): Boolean {
            val b = art(when (pose) { 1 -> "penguin_fall"; 2 -> "penguin_land"; else -> "penguin_rise" }) ?: return false
            val c = activeCanvas
            c.save()
            c.translate(cx, feetY)
            c.rotate(tiltDeg)
            if (facing > 0) c.scale(-1f, 1f)
            dstR.set(-52.5f * k, -88f * k, 47.5f * k, 2f * k)   // спрайт 100x90, лапы на y≈88
            p.shader = null; p.style = Paint.Style.FILL; p.alpha = 255
            c.drawBitmap(b, null, dstR, p)
            c.restore()
            return true
        }
        private fun penguin(px:Float, py:Float,scale:Float) {
            activeCanvas.save(); activeCanvas.translate(px,py); activeCanvas.scale(scale,scale)
            val flap=sin(t*11f) * if(vy>0f) 12f else 5f
            val ink=Color.rgb(15,24,32)
            val belly=Color.rgb(249,250,240)
            val orange=Color.rgb(255,147,49)
            // Характерный длинный силуэт и крылья.
            p.shader=null; p.style=Paint.Style.FILL
            p.color=ink
            activeCanvas.save();activeCanvas.rotate(-9f-flap*.7f,-24f,-16f)
            val wingLeft=Path().apply {moveTo(-24f,-27f);cubicTo(-49f,-40f,-42f,18f,-36f,44f);cubicTo(-20f,35f,-19f,0f,-24f,-27f);close()}
            activeCanvas.drawPath(wingLeft,p);activeCanvas.restore()
            activeCanvas.save();activeCanvas.rotate(9f+flap*.7f,24f,-16f)
            val wingRight=Path().apply {moveTo(24f,-27f);cubicTo(49f,-40f,42f,18f,36f,44f);cubicTo(20f,35f,19f,0f,24f,-27f);close()}
            activeCanvas.drawPath(wingRight,p);activeCanvas.restore()
            // Переваливающиеся оранжевые лапы.
            p.color=orange
            rect.set(-32f,32f,-2f,44f);activeCanvas.drawOval(rect,p)
            rect.set(2f,32f,32f,44f);activeCanvas.drawOval(rect,p)
            p.color=ink
            val body=Path().apply { moveTo(-26f,-35f); cubicTo(-37f,-18f,-29f,22f,-19f,38f); cubicTo(-10f,45f,12f,45f,21f,36f); cubicTo(33f,17f,33f,-18f,24f,-35f); cubicTo(12f,-51f,-14f,-51f,-26f,-35f); close() }
            activeCanvas.drawPath(body,p)
            p.color=belly
            val chest=Path().apply { moveTo(-18f,-31f); cubicTo(-25f,-5f,-19f,23f,-12f,33f); cubicTo(-5f,40f,11f,39f,16f,30f); cubicTo(23f,8f,23f,-15f,16f,-31f); cubicTo(6f,-40f,-9f,-40f,-18f,-31f); close() }
            activeCanvas.drawPath(chest,p)
            p.color=ink
            val mask=Path().apply { moveTo(-27f,-37f); quadTo(-15f,-53f,-2f,-43f); quadTo(12f,-53f,27f,-36f); lineTo(22f,-10f); lineTo(-23f,-10f); close() }
            activeCanvas.drawPath(mask,p)
            p.color=Color.rgb(240,247,235); rect.set(-21f,-39f,-2f,-13f); activeCanvas.drawOval(rect,p)
            rect.set(2f,-39f,21f,-13f); activeCanvas.drawOval(rect,p)
            circle(-11f,-26f,7.2f,Color.rgb(103,177,188)); circle(12f,-26f,7.2f,Color.rgb(103,177,188))
            circle(-10f,-26f,4.1f,ink);circle(13f,-26f,4.1f,ink)
            circle(-11.2f,-28f,1.7f,Color.WHITE);circle(11.8f,-28f,1.7f,Color.WHITE)
            // Вытянутый оранжевый клюв с улыбкой.
            p.color=orange
            val beak=Path().apply {moveTo(-17f,-15f);quadTo(0f,-22f,17f,-14f);quadTo(12f,-1f,1f,5f);quadTo(-9f,3f,-17f,-15f);close()}
            activeCanvas.drawPath(beak,p)
            p.color=Color.rgb(255,190,74); p.strokeWidth=1.5f; activeCanvas.drawLine(-11f,-13f,10f,-13f,p)
            p.color=Color.rgb(92,37,34)
            val smile=Path().apply {moveTo(-9f,-8f);quadTo(0f,3f,9f,-8f);quadTo(0f,-1f,-9f,-8f);close()}
            activeCanvas.drawPath(smile,p)
            p.color=ink
            val tuft=Path().apply {moveTo(-8f,-42f);lineTo(0f,-58f);lineTo(4f,-46f);lineTo(12f,-55f);lineTo(10f,-43f);close()}
            activeCanvas.drawPath(tuft,p)
            activeCanvas.restore()
        }
        private fun drawPlayer() {
            val sx = x; val sY = sy(y)
            if (NEW_PENGUIN) {
                val pose = when { landTimer > 0f -> 2; vy > 0f -> 0; else -> 1 }
                // feetY = sY + 40: лапы чуть заходят на платформу, как раньше.
                if (penguinSprite(sx, sY + 40f, 1.25f, pose, lean)) return
            }
            penguin(sx, sY, 1.0f + if(vy>0) .045f else -.025f)
        }

        // ---------- интерфейс в рисованном стиле ----------
        private fun panel(name: String, l: Float, top: Float, w: Float, h: Float, alpha: Int = 255) {
            if (!drawArt(name, l, top, w, h, alpha)) {
                rounded(l, top, l + w, top + h, 30f, Color.argb(alpha, 255, 247, 226))
                p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Color.argb(alpha, 47, 40, 33)
                rect.set(l, top, l + w, top + h); activeCanvas.drawRoundRect(rect, 30f, 30f, p); p.style = Paint.Style.FILL
            }
        }
        /** Кнопка 300x76 с нарисованной обводкой; top — верхний край. */
        private fun button(title: String, top: Float, primary: Boolean) {
            panel(if (primary) "btn_primary" else "btn_secondary", 90f, top, 300f, 76f)
            label(title, 240f, top + 52f, 62f, ink, maxW = 232f)
        }
        private fun hud(){
            // HUD всегда сохраняет контраст на любых этапах неба: непрозрачная бумажная плашка.
            panel("panel_hud", 110f, 4f, 260f, 124f)
            label(zoneNames[zone(highest)], 240f, 36f, 40f, Color.rgb(46, 112, 160), maxW = 214f)
            label("$score", 240f, 91f, 92f, ink, maxW = 214f)
            label("РЕКОРД  ${max(best,score)}", 240f, 117f, 36f, Color.rgb(176, 112, 24), maxW = 214f)
            if (!drawArt("btn_pause", 12f, 12f, 56f, 56f)) {
                rounded(14f,20f,70f,74f,17f,Color.argb(165,10,28,57))
                rounded(32f,35f,39f,59f,3f,white); rounded(45f,35f,52f,59f,3f,white)
            }
            if(stageTimer>0f && state==1) {
                val alpha = (stageTimer.coerceAtMost(1f)*255).toInt()
                panel("panel_banner", 64f, 150f, 352f, 72f, alpha)
                label(stageMessage, 240f, 150f + 48f, 66f, Color.argb(alpha, 47, 40, 33), maxW = 300f)
            }
            if(newBestFlash>0f && score>best) label("НОВЫЙ РЕКОРД!", 240f, 262f, 58f, gold, maxW = 360f, outline = ink, outlineW = 7f)
        }
        private fun paperBackground() {
            if (!drawArt("paper_bg", 0f, 0f, W, H)) {
                p.shader=LinearGradient(0f,0f,0f,H,Color.rgb(255,251,239),Color.rgb(246,232,208),Shader.TileMode.CLAMP)
                activeCanvas.drawRect(0f,0f,W,H,p); p.shader=null
            }
        }
        private fun menu(){
            paperBackground()
            label("Пыжпец", 243f, 116f, 132f, Color.argb(120, 240, 150, 50), maxW = 400f)   // «цветной карандаш» под буквами
            label("Пыжпец", 240f, 112f, 132f, ink, maxW = 400f)
            label("JUMP", 240f, 190f, 100f, green, maxW = 300f, outline = ink, outlineW = 7f)
            drawArt("title_scribble", 90f, 198f, 300f, 26f)
            val bob = sin(t * 2f) * 6f
            val pose = if (sin(t * 3.2f) > 0f) 0 else 1
            val savedFacing = facing; facing = -1
            if (!(NEW_PENGUIN && penguinSprite(240f, 396f + bob, 1.9f, pose, 0f))) penguin(240f, 318f + bob, 1.45f)
            facing = savedFacing
            button("ИГРАТЬ", 410f, true)
            button("НАСТРОЙКИ", 500f, false)
            button("РЕКОРДЫ", 590f, false)
            label("Наклоняй телефон или води пальцем", 240f, 735f, 40f, Color.rgb(96, 82, 62), maxW = 420f)
        }
        private fun overlay(title:String, sub:String, extra:String){
            p.shader=null; p.style=Paint.Style.FILL; p.color=Color.argb(150,20,28,50);activeCanvas.drawRect(0f,0f,W,H,p)
            panel("panel_big", 28f, 160f, 424f, 420f)
            label(title, 240f, 250f, 112f, red, maxW = 340f)
            label(sub, 240f, 308f, 70f, ink, maxW = 340f)
            if (extra.isNotEmpty()) label(extra, 240f, 348f, 48f, Color.rgb(176, 112, 24), maxW = 340f)
            button(if(state==2)"ПРОДОЛЖИТЬ" else "ЕЩЁ РАЗ!", 365f, true)
            button("В МЕНЮ", 459f, false)
        }
        private fun settings(){
            paperBackground()
            label("НАСТРОЙКИ", 240f, 108f, 112f, red, maxW = 420f, outline = ink, outlineW = 0f)
            panel("panel_settings", 25f, 140f, 430f, 470f)
            label("Музыка", 70f, 207f, 58f, ink, Paint.Align.LEFT)
            label("${(musicVolume*100).toInt()}%", 410f, 207f, 54f, Color.rgb(90,80,64), Paint.Align.RIGHT)
            slider(232f, musicVolume)
            label("Звуки", 70f, 327f, 58f, ink, Paint.Align.LEFT)
            label("${(effectsVolume*100).toInt()}%", 410f, 327f, 54f, Color.rgb(90,80,64), Paint.Align.RIGHT)
            slider(352f, effectsVolume)
            label("Управление", 70f, 447f, 58f, ink, Paint.Align.LEFT)
            label("Наклоняй телефон", 240f, 497f, 46f, Color.rgb(96, 82, 62), maxW = 340f)
            label("или води пальцем по экрану", 240f, 545f, 46f, Color.rgb(96, 82, 62), maxW = 340f)
            button("НАЗАД", 650f, true)
        }
        private fun records(){
            paperBackground()
            label("РЕКОРДЫ", 240f, 108f, 112f, red, maxW = 420f)
            panel("panel_records", 35f, 150f, 410f, 380f)
            label("ЛУЧШИЙ РЕЗУЛЬТАТ", 240f, 232f, 60f, ink, maxW = 330f)
            label("$best", 240f, 360f, 200f, green, maxW = 320f, outline = ink, outlineW = 8f)
            label("Так держать!", 240f, 416f, 56f, ink, maxW = 330f)
            label("Рекорд хранится", 240f, 464f, 40f, Color.rgb(110, 96, 76), maxW = 330f)
            label("на этом устройстве", 240f, 497f, 40f, Color.rgb(110, 96, 76), maxW = 330f)
            button("НАЗАД", 650f, true)
        }
        /** Ползунок: нарисованная дорожка 312x22, заполненная часть — зелёная, ручка — «пуговица». */
        private fun slider(top: Float, volume: Float){
            val knobX = 88f + 304f * volume
            if (!drawArt("slider_empty", 84f, top, 312f, 22f)) rounded(88f, top + 6f, 392f, top + 16f, 5f, Color.rgb(218,218,208))
            activeCanvas.save()
            activeCanvas.clipRect(80f, top - 4f, knobX, top + 26f)
            if (!drawArt("slider_full", 84f, top, 312f, 22f)) rounded(88f, top + 6f, 392f, top + 16f, 5f, Color.rgb(101,169,111))
            activeCanvas.restore()
            if (!drawArt("slider_knob", knobX - 24f, top + 11f - 24f, 48f, 48f)) circle(knobX, top + 11f, 14f, Color.rgb(255,255,249))
        }
        override fun onDraw(c:Canvas){
            super.onDraw(c)
            activeCanvas=c
            val now=System.nanoTime();val dt=if(lastFrame==0L) .016f else ((now-lastFrame)/1_000_000_000.0f).coerceIn(0.001f,0.035f);lastFrame=now
            update(dt)
            c.save();c.scale(width/W,height/H)
            bg()
            if(state==0) menu() else if(state==4) settings() else if(state==5) records() else {
                platforms.forEach { platform(it) }
                for(s in sparks)circle(s.x,sy(s.y),s.life*5f,s.color)
                drawPlayer()
                hud()
            }
            when(state){2->overlay("ПАУЗА","Передохни немного","");3->overlay("КОНЕЦ ИГРЫ","СЧЁТ: $score","РЕКОРД: ${max(best,score)}")}
            c.restore();postInvalidateDelayed(16)
        }
        override fun onTouchEvent(event:MotionEvent):Boolean {
            if (!hasFocus()) requestFocus()
            val px=event.x*W/width; val py=event.y*H/height
            if(event.action==MotionEvent.ACTION_UP || event.action==MotionEvent.ACTION_CANCEL) {
                if(state==1) {touchX=null;return true}
                if(state==0){ when {py in 400f..490f->{reset();state=1;startMusic(false);sfx("tap")};py in 492f..582f->{state=4;sfx("tap")};py in 584f..674f->{state=5;sfx("tap")}} }
                else if(state==2 || state==3) {when {py in 357f..449f->{if(state==3)reset();state=1;startMusic(false);sfx("tap")};py in 451f..545f->{state=0;startMusic(true);sfx("tap")}}}
                else if(state==4 || state==5){if(py in 640f..740f){state=0;startMusic(true);sfx("tap")}}
                return true
            }
            if(event.action==MotionEvent.ACTION_DOWN || event.action==MotionEvent.ACTION_MOVE){
                if(state==1){if(py<90 && px<80 && event.action==MotionEvent.ACTION_DOWN){state=2;sfx("tap");touchX=null} else touchX=px}
                if(state==4 && py in 212f..282f){prefs.edit().putFloat("music",((px-88f)/304f).coerceIn(0f,1f)).apply();refreshVolume()}
                if(state==4 && py in 332f..402f){prefs.edit().putFloat("effects",((px-88f)/304f).coerceIn(0f,1f)).apply()}
            }
            return true
        }
    }
}
