package com.example.bandcontroller.game

data class Bullet(var x: Float, var y: Float, val radius: Float = 4f, val speed: Float = 670f)
data class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val hue: Int)
enum class GameSound { LASER, EXPLOSION, HIT }
