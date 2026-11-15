package com.example.bandcontroller.game

object CollisionManager {
    fun circles(ax: Float, ay: Float, ar: Float, bx: Float, by: Float, br: Float): Boolean {
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy <= (ar + br) * (ar + br)
    }
    fun hits(bullet: Bullet, enemy: Enemy) = circles(bullet.x, bullet.y, bullet.radius, enemy.x, enemy.y, enemy.radius)
    fun hits(player: Player, enemy: Enemy) = circles(player.x, player.y, player.radius, enemy.x, enemy.y, enemy.radius)
}
