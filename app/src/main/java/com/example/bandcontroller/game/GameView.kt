package com.example.bandcontroller.game

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/** Rendering and frame scheduling only. Touch buttons and MediaSession feed the same engine. */
class GameView(context: Context, val engine: GameEngine) : View(context), Choreographer.FrameCallback {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val cyan = Color.rgb(77, 241, 255)
    private val pink = Color.rgb(255, 89, 158)
    private val gold = Color.rgb(255, 200, 94)
    private val stars = Random(26).let { r -> List(85) { floatArrayOf(r.nextFloat() * 420f, r.nextFloat() * 720f, .5f + r.nextFloat() * 1.5f) } }
    private val nebula = RadialGradient(360f, 290f, 340f, intArrayOf(Color.rgb(30, 21, 65), Color.rgb(5, 10, 24)), null, Shader.TileMode.CLAMP)
    private var lastFrame = 0L
    private var visualTime = 0f
    private var active = false
    private var audio: GameAudio? = null
    var soundEnabled = true
    var onHighScore: ((Int) -> Unit)? = null
    private var savedHigh = engine.scores.highScore

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; setLayerType(LAYER_TYPE_HARDWARE, null) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); audio = GameAudio(context); schedule() }
    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this); audio?.release(); audio = null
        super.onDetachedFromWindow()
    }
    fun setActive(value: Boolean) {
        active = value; lastFrame = 0L
        Choreographer.getInstance().removeFrameCallback(this)
        if (value) schedule() else { engine.drainSounds(); audio?.pause() }
    }
    private fun schedule() { if (active && isAttachedToWindow) Choreographer.getInstance().postFrameCallback(this) }
    override fun doFrame(frameTimeNanos: Long) {
        if (!active) return
        val dt = if (lastFrame == 0L) 0f else ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceIn(0f, .1f)
        lastFrame = frameTimeNanos; visualTime += dt
        engine.advance(dt)
        engine.drainSounds().forEach { if (soundEnabled) audio?.play(it) }
        if (savedHigh != engine.scores.highScore) { savedHigh = engine.scores.highScore; onHighScore?.invoke(savedHigh) }
        invalidate(); schedule()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(5, 10, 24))
        val scale = min(width / 420f, height / 720f)
        if (scale <= 0f) return
        val save = canvas.save()
        canvas.translate((width - 420f * scale) / 2f, (height - 720f * scale) / 2f); canvas.scale(scale, scale)
        ink.shader = nebula; canvas.drawRect(0f, 0f, 420f, 720f, ink); ink.shader = null
        stars.forEach { s ->
            ink.color = Color.argb((90 + s[2] * 70).toInt().coerceAtMost(255), 183, 213, 255)
            val y = (s[1] + visualTime * (12f + s[2] * 16f)) % 720f
            canvas.drawCircle(s[0], y, s[2] * .65f, ink)
            if (engine.state == GameState.RUNNING && engine.level > 2) canvas.drawLine(s[0], y, s[0], y + s[2] * 3, ink)
        }
        ink.color = Color.rgb(18, 31, 54); ink.strokeWidth = 1f
        canvas.drawLine(18f, 80f, 402f, 80f, ink)
        text(canvas, "SCORE", 20f, 23f, 11f, cyan)
        text(canvas, "%05d".format(engine.scores.score), 20f, 52f, 26f, Color.WHITE)
        text(canvas, "BEST %05d".format(engine.scores.highScore), 210f, 28f, 12f, Color.LTGRAY, Paint.Align.CENTER)
        text(canvas, "WAVE ${engine.level}", 210f, 51f, 12f, cyan, Paint.Align.CENTER)
        text(canvas, "SHIELDS", 400f, 23f, 11f, pink, Paint.Align.RIGHT)
        repeat(3) { i ->
            ink.color = if (i < engine.player.lives) pink else Color.rgb(47, 37, 61)
            canvas.drawRoundRect(346f + i * 19f, 37f, 358f + i * 19f, 52f, 3f, 3f, ink)
        }
        val field = canvas.save()
        canvas.clipRect(0f, 82f, 420f, 675f)
        engine.bullets.forEach {
            ink.color = Color.argb(60, 77, 241, 255); canvas.drawRoundRect(it.x - 7f, it.y - 10f, it.x + 7f, it.y + 25f, 7f, 7f, ink)
            ink.color = cyan; canvas.drawRoundRect(it.x - 2f, it.y - 9f, it.x + 2f, it.y + 12f, 2f, 2f, ink)
        }
        engine.enemies.forEach { enemy -> drawEnemy(canvas, enemy) }
        engine.particles.forEach {
            ink.color = if (it.hue == 0) gold else pink; ink.alpha = (it.life / .75f * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(it.x, it.y, 1.5f + it.life * 3, ink)
        }
        ink.alpha = 255
        val p = engine.player
        if (p.invulnerable <= 0f || (visualTime * 12).toInt() % 2 == 0 || engine.state != GameState.RUNNING) drawPlayer(canvas, p.x, p.y)
        canvas.restoreToCount(field)
        text(canvas, "BAND MUSIC CONTROLS  /  TOUCH CONTROLS", 210f, 697f, 10f, Color.rgb(104, 130, 164), Paint.Align.CENTER)
        when (engine.state) {
            GameState.TITLE -> overlay(canvas, "NEON ORBIT", "A BAND-CONTROLLED ARCADE", listOf("Defend the orbit. Keep three shields.", "Next / Previous = move", "Play or Pause = fire", "Touch controls always work."), "TAP LAUNCH BELOW")
            GameState.PAUSED -> overlay(canvas, "FLIGHT PAUSED", "TAKE A BREATH, PILOT", listOf("Your run is safely paused.", "Media inputs are logged,", "but cannot move or fire here."), "TAP RESUME BELOW")
            GameState.GAME_OVER -> overlay(canvas, "SIGNAL LOST", "FINAL SCORE  ${engine.scores.score}", listOf("Best orbit: ${engine.scores.highScore}", "Every destroyed ship: +20", "Every destroyed asteroid: +30"), "TAP PLAY AGAIN BELOW")
            else -> Unit
        }
        canvas.restoreToCount(save)
    }
    private fun drawPlayer(c: Canvas, x: Float, y: Float) {
        ink.color = cyan; ink.alpha = 35; c.drawCircle(x, y, 39f, ink); ink.alpha = 255
        ink.color = gold
        path.reset(); path.moveTo(x - 8f, y + 18f); path.lineTo(x, y + 42f + sin(visualTime * 35) * 7f); path.lineTo(x + 8f, y + 18f); path.close(); c.drawPath(path, ink)
        path.reset(); path.moveTo(x, y - 28f); path.lineTo(x + 23f, y + 21f); path.lineTo(x + 7f, y + 15f); path.lineTo(x, y + 21f); path.lineTo(x - 7f, y + 15f); path.lineTo(x - 23f, y + 21f); path.close()
        ink.color = Color.rgb(23, 83, 112); c.drawPath(path, ink)
        ink.color = cyan; ink.style = Paint.Style.STROKE; ink.strokeWidth = 2f; c.drawPath(path, ink); ink.style = Paint.Style.FILL
        ink.color = Color.WHITE; c.drawOval(x - 4f, y - 10f, x + 4f, y + 7f, ink)
    }
    private fun drawEnemy(c: Canvas, e: Enemy) {
        val save = c.save(); c.translate(e.x, e.y)
        if (e.type == EnemyType.ASTEROID) {
            c.rotate(e.age * 35f)
            path.reset()
            repeat(9) { i ->
                val a = i * 2 * PI / 9
                val r = e.radius * if (i % 2 == 0) 1f else .76f
                val x = cos(a).toFloat() * r; val y = sin(a).toFloat() * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); ink.color = Color.rgb(80, 57, 63); c.drawPath(path, ink)
            ink.color = if (e.health == 1) pink else gold
            ink.style = Paint.Style.STROKE; ink.strokeWidth = 2f; c.drawPath(path, ink); ink.style = Paint.Style.FILL
            ink.color = Color.rgb(37, 29, 47); c.drawCircle(-6f, 3f, 5f, ink); c.drawCircle(6f, -8f, 3f, ink)
        } else {
            path.reset(); path.moveTo(0f, 22f); path.lineTo(23f, -12f); path.lineTo(10f, -7f); path.lineTo(0f, -18f); path.lineTo(-10f, -7f); path.lineTo(-23f, -12f); path.close()
            ink.color = Color.rgb(91, 27, 73); c.drawPath(path, ink)
            ink.color = pink; ink.style = Paint.Style.STROKE; ink.strokeWidth = 2f; c.drawPath(path, ink); ink.style = Paint.Style.FILL
            ink.color = Color.WHITE; c.drawCircle(0f, 0f, 4f, ink)
        }
        c.restoreToCount(save)
    }
    private fun overlay(c: Canvas, title: String, subtitle: String, lines: List<String>, footer: String) {
        ink.color = Color.argb(165, 3, 7, 20); c.drawRect(0f, 81f, 420f, 675f, ink)
        ink.color = Color.rgb(12, 22, 43); c.drawRoundRect(24f, 207f, 396f, 481f, 20f, 20f, ink)
        ink.color = cyan; ink.style = Paint.Style.STROKE; ink.strokeWidth = 1.2f; c.drawRoundRect(24f, 207f, 396f, 481f, 20f, 20f, ink); ink.style = Paint.Style.FILL
        text(c, subtitle, 210f, 240f, 11f, cyan, Paint.Align.CENTER)
        text(c, title, 210f, 285f, 31f, Color.WHITE, Paint.Align.CENTER)
        lines.forEachIndexed { i, line -> text(c, line, 210f, 326f + i * 24f, 14f, Color.rgb(178, 197, 219), Paint.Align.CENTER) }
        text(c, footer, 210f, 455f, 12f, gold, Paint.Align.CENTER)
    }
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        ink.color = color; ink.textSize = size; ink.textAlign = align; ink.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        c.drawText(value, x, y, ink)
    }
}
