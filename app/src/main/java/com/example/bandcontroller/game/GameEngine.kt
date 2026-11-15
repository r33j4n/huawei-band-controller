package com.example.bandcontroller.game

import kotlin.math.*
import kotlin.random.Random

enum class GameState { TITLE, RUNNING, PAUSED, GAME_OVER }

/** Android-free simulation in a 420 x 720 world. Fixed steps avoid frame-rate dependent physics. */
class GameEngine(highScore: Int = 0, private val random: Random = Random.Default) {
    companion object { const val WIDTH = 420f; const val HEIGHT = 720f; private const val STEP = 1f / 120f }
    val player = Player()
    val enemies = mutableListOf<Enemy>()
    val bullets = mutableListOf<Bullet>()
    val particles = mutableListOf<Particle>()
    val scores = ScoreManager(highScore)
    private val sounds = mutableListOf<GameSound>()
    var state = GameState.TITLE
        private set
    var elapsed = 0f
        private set
    val level get() = 1 + (elapsed / 20f).toInt()
    var moveDirection = 0
    var fireHeld = false
    private var targetX = player.x
    private var accumulator = 0f
    private var spawnIn = 1f
    private var cooldown = 0f

    fun start() {
        if (state != GameState.TITLE && state != GameState.GAME_OVER) return
        player.x = WIDTH / 2; player.y = 610f; player.lives = 3; player.invulnerable = 1.2f
        targetX = player.x; elapsed = 0f; spawnIn = 1f; cooldown = 0f; accumulator = 0f
        clearInput(); enemies.clear(); bullets.clear(); particles.clear(); sounds.clear(); scores.reset()
        state = GameState.RUNNING
    }
    fun pause() { if (state == GameState.RUNNING) { state = GameState.PAUSED; clearInput(); accumulator = 0f } }
    fun resume() { if (state == GameState.PAUSED) { state = GameState.RUNNING; accumulator = 0f } }
    fun clearInput() { moveDirection = 0; fireHeld = false }
    fun moveBy(direction: Int) {
        if (state == GameState.RUNNING) targetX = (targetX + direction.coerceIn(-1, 1) * 68f).coerceIn(24f, WIDTH - 24f)
    }
    fun fire() {
        if (state != GameState.RUNNING || cooldown > 0f) return
        bullets.add(Bullet(player.x, player.y - 25f)); cooldown = .18f; sounds.add(GameSound.LASER)
    }
    fun drainSounds(): List<GameSound> = sounds.toList().also { sounds.clear() }
    fun advance(seconds: Float) {
        if (!seconds.isFinite() || seconds <= 0f) return
        if (state == GameState.GAME_OVER) { updateParticles(seconds.coerceAtMost(.1f)); return }
        if (state != GameState.RUNNING) return
        // Never catch up through a long background gap or debugger stop.
        accumulator += seconds.coerceAtMost(.1f)
        while (accumulator >= STEP && state == GameState.RUNNING) { step(STEP); accumulator -= STEP }
    }
    private fun step(dt: Float) {
        elapsed += dt
        cooldown = (cooldown - dt).coerceAtLeast(0f)
        player.invulnerable = (player.invulnerable - dt).coerceAtLeast(0f)
        if (moveDirection != 0) targetX = (targetX + moveDirection.coerceIn(-1, 1) * 300f * dt).coerceIn(24f, WIDTH - 24f)
        val delta = targetX - player.x
        player.x += delta.coerceIn(-350f * dt, 350f * dt)
        if (fireHeld) fire()
        spawnIn -= dt
        if (spawnIn <= 0f) {
            val type = if (random.nextFloat() < .35f) EnemyType.ASTEROID else EnemyType.SHIP
            val radius = if (type == EnemyType.ASTEROID) 24f else 18f
            enemies.add(Enemy(random.nextFloat() * (WIDTH - 70f) + 35f, -30f, radius,
                85f + min(elapsed * 2.2f, 240f) + random.nextFloat() * 35f, type))
            spawnIn = max(.32f, 1.15f - elapsed * .009f)
        }
        bullets.forEach { it.y -= it.speed * dt }
        bullets.removeAll { it.y < -30f }
        enemies.forEach { it.y += it.speed * dt; it.age += dt }
        val bulletIterator = bullets.iterator()
        while (bulletIterator.hasNext()) {
            val bullet = bulletIterator.next()
            val enemy = enemies.firstOrNull { CollisionManager.hits(bullet, it) } ?: continue
            bulletIterator.remove(); enemy.health--
            if (enemy.health <= 0) {
                enemies.remove(enemy); scores.destroyed(enemy.type)
                explode(enemy.x, enemy.y, 0); sounds.add(GameSound.EXPLOSION)
            } else explode(bullet.x, bullet.y, 1, 5)
        }
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemy = iterator.next()
            if (CollisionManager.hits(player, enemy) || enemy.y > HEIGHT + enemy.radius) {
                iterator.remove()
                if (player.invulnerable <= 0f) {
                    player.lives--; player.invulnerable = 1.25f
                    explode(player.x, player.y, 1); sounds.add(GameSound.HIT)
                    if (player.lives <= 0) { state = GameState.GAME_OVER; clearInput(); break }
                }
            }
        }
        updateParticles(dt)
    }
    private fun updateParticles(dt: Float) {
        particles.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.life -= dt }
        particles.removeAll { it.life <= 0f }
    }
    private fun explode(x: Float, y: Float, hue: Int, count: Int = 24) {
        repeat(count) {
            val a = random.nextFloat() * (2 * PI).toFloat()
            val speed = 35f + random.nextFloat() * 150f
            particles.add(Particle(x, y, cos(a) * speed, sin(a) * speed, .3f + random.nextFloat() * .45f, hue))
        }
        while (particles.size > 240) particles.removeAt(0)
    }
}
