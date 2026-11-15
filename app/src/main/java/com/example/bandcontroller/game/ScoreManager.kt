package com.example.bandcontroller.game

class ScoreManager(initialHighScore: Int = 0) {
    var score = 0
        private set
    var highScore = initialHighScore.coerceAtLeast(0)
        private set
    fun destroyed(type: EnemyType) {
        score += if (type == EnemyType.ASTEROID) 30 else 20
        highScore = maxOf(highScore, score)
    }
    fun reset() { score = 0 }
}
