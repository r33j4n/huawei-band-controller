package com.example.bandcontroller.game

enum class EnemyType { SHIP, ASTEROID }
data class Enemy(var x: Float, var y: Float, val radius: Float, val speed: Float,
                 val type: EnemyType, var health: Int = if (type == EnemyType.ASTEROID) 2 else 1,
                 var age: Float = 0f)
