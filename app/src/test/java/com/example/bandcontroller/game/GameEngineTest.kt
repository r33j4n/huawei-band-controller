package com.example.bandcontroller.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    @Test fun circlesIncludeTouchingEdgesAndExcludeNearMisses() {
        assertTrue(CollisionManager.circles(0f, 0f, 10f, 20f, 0f, 10f))
        assertFalse(CollisionManager.circles(0f, 0f, 10f, 20.01f, 0f, 10f))
        assertTrue(CollisionManager.circles(7f, 9f, 10f, 7f, 9f, 1f))
    }
    @Test fun diagonalCollisionUsesDistanceRatherThanBoundingBoxes() {
        assertFalse(CollisionManager.circles(0f, 0f, 10f, 18f, 18f, 10f))
        assertTrue(CollisionManager.circles(0f, 0f, 10f, 12f, 12f, 10f))
    }
    @Test fun scoreValuesAndHighScoreSurviveNewRun() {
        val scores = ScoreManager(40)
        scores.destroyed(EnemyType.SHIP); assertEquals(20, scores.score); assertEquals(40, scores.highScore)
        scores.destroyed(EnemyType.ASTEROID); assertEquals(50, scores.score); assertEquals(50, scores.highScore)
        scores.reset(); assertEquals(0, scores.score); assertEquals(50, scores.highScore)
    }
    @Test fun titlePauseAndResumeGateSimulationAndInput() {
        val g = GameEngine()
        g.fire(); g.advance(1f); assertTrue(g.bullets.isEmpty()); assertEquals(GameState.TITLE, g.state)
        g.start(); g.pause(); val x = g.player.x
        g.moveBy(1); g.fire(); g.advance(.1f)
        assertEquals(x, g.player.x, 0f); assertTrue(g.bullets.isEmpty()); assertEquals(0f, g.elapsed, 0f)
        g.resume(); g.fire(); g.advance(.02f)
        assertEquals(GameState.RUNNING, g.state); assertEquals(1, g.bullets.size); assertTrue(g.elapsed > 0)
    }
    @Test fun oneBulletCanDestroyOnlyOneOverlappingShip() {
        val g = GameEngine(); g.start()
        repeat(2) { g.enemies.add(Enemy(150f, 200f, 18f, 0f, EnemyType.SHIP)) }
        g.bullets.add(Bullet(150f, 200f)); g.advance(.009f)
        assertEquals(1, g.enemies.size); assertTrue(g.bullets.isEmpty()); assertEquals(20, g.scores.score)
    }
    @Test fun asteroidNeedsTwoHitsAndAwardsScoreOnlyOnDestruction() {
        val g = GameEngine(); g.start()
        g.enemies.add(Enemy(150f, 200f, 24f, 0f, EnemyType.ASTEROID))
        g.bullets.add(Bullet(150f, 200f)); g.advance(.009f)
        assertEquals(1, g.enemies.single().health); assertEquals(0, g.scores.score)
        g.bullets.add(Bullet(150f, 200f)); g.advance(.009f)
        assertTrue(g.enemies.isEmpty()); assertEquals(30, g.scores.score)
    }
    @Test fun invulnerabilityPreventsRepeatedLifeLoss() {
        val g = GameEngine(); g.start(); g.player.invulnerable = 0f
        repeat(2) {
            g.enemies.add(Enemy(g.player.x, g.player.y, 18f, 0f, EnemyType.SHIP)); g.advance(.009f)
        }
        assertEquals(2, g.player.lives)
    }
    @Test fun threeHitsEndRunAndRestartResetsIt() {
        val g = GameEngine(); g.start()
        repeat(3) {
            g.player.invulnerable = 0f
            g.enemies.add(Enemy(g.player.x, g.player.y, 18f, 0f, EnemyType.SHIP)); g.advance(.009f)
        }
        assertEquals(0, g.player.lives); assertEquals(GameState.GAME_OVER, g.state)
        g.fire(); assertTrue(g.bullets.isEmpty())
        g.resume(); assertEquals(GameState.GAME_OVER, g.state)
        g.start(); assertEquals(3, g.player.lives); assertEquals(GameState.RUNNING, g.state); assertTrue(g.enemies.isEmpty())
    }
    @Test fun escapedThreatCostsAShield() {
        val g = GameEngine(); g.start(); g.player.invulnerable = 0f
        g.enemies.add(Enemy(10f, 760f, 18f, 0f, EnemyType.SHIP)); g.advance(.009f)
        assertEquals(2, g.player.lives); assertEquals(0, g.scores.score)
    }
    @Test fun fixedStepsProduceSameMovementAtDifferentFrameRates() {
        fun simulate(fps: Int): GameEngine {
            val g = GameEngine(random = Random(8)); g.start(); g.moveDirection = 1; g.fireHeld = true
            repeat(fps * 2) { g.advance(1f / fps) }; return g
        }
        val a = simulate(30); val b = simulate(120)
        assertEquals(a.player.x, b.player.x, .01f); assertEquals(a.elapsed, b.elapsed, .01f)
        assertEquals(a.bullets.size, b.bullets.size); assertEquals(a.enemies.size, b.enemies.size)
    }
    @Test fun longGapsAreClampedAndHeldInputsClearOnPause() {
        val g = GameEngine(); g.start(); g.moveDirection = 1; g.fireHeld = true
        g.advance(30f); assertTrue(g.elapsed <= .101f)
        g.pause(); assertEquals(0, g.moveDirection); assertFalse(g.fireHeld)
    }
    @Test fun mediaStyleNudgesClampToBothWorldEdges() {
        val g = GameEngine(); g.start()
        repeat(20) { g.moveBy(1) }; repeat(30) { g.advance(.05f) }
        assertEquals(396f, g.player.x, .01f)
        repeat(20) { g.moveBy(-1) }; repeat(30) { g.advance(.05f) }
        assertEquals(24f, g.player.x, .01f)
    }
}
