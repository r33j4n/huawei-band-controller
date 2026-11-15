package com.example.bandcontroller.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.bandcontroller.R

/** Short local effects only; touch play never requests or steals audio focus. */
class GameAudio(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val ids: Map<GameSound, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        ids = mapOf(GameSound.LASER to pool.load(context, R.raw.laser, 1),
            GameSound.EXPLOSION to pool.load(context, R.raw.explosion, 1), GameSound.HIT to pool.load(context, R.raw.hit, 1))
    }
    fun play(sound: GameSound) { ids[sound]?.takeIf { it in loaded }?.let { pool.play(it, .4f, .4f, 1, 0, 1f) } }
    fun release() { pool.release(); loaded.clear() }
}
