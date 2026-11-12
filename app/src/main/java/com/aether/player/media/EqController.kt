package com.aether.player.media

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

object EqController {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var appCtx: Context? = null
    private fun sp() = appCtx?.getSharedPreferences("eq", Context.MODE_PRIVATE)

    fun attach(ctx: Context, sessionId: Int) {
        appCtx = ctx.applicationContext
        try { eq?.release() } catch (e: Exception) {}
        try { bass?.release() } catch (e: Exception) {}
        eq = try {
            Equalizer(0, sessionId).also { e ->
                e.enabled = enabled()
                for (i in 0 until e.numberOfBands) e.setBandLevel(i.toShort(), level(i).toShort())
            }
        } catch (e: Exception) { null }
        bass = try {
            BassBoost(0, sessionId).also { b ->
                if (b.strengthSupported) { b.setStrength(bassStrength().toShort()); b.enabled = bassStrength() > 0 }
            }
        } catch (e: Exception) { null }
    }

    fun available(): Boolean = eq != null
    fun bands(): Int = eq?.numberOfBands?.toInt() ?: 0
    fun minLevel(): Int = eq?.bandLevelRange?.get(0)?.toInt() ?: -1500
    fun maxLevel(): Int = eq?.bandLevelRange?.get(1)?.toInt() ?: 1500
    fun freqHz(i: Int): Int = (eq?.getCenterFreq(i.toShort()) ?: 0) / 1000
    fun level(i: Int): Int = sp()?.getInt("b$i", 0) ?: 0
    fun setLevel(i: Int, v: Int) {
        sp()?.edit()?.putInt("b$i", v)?.apply()
        try { eq?.setBandLevel(i.toShort(), v.toShort()) } catch (e: Exception) {}
    }
    fun enabled(): Boolean = sp()?.getBoolean("on", false) ?: false
    fun setEnabled(b: Boolean) {
        sp()?.edit()?.putBoolean("on", b)?.apply()
        try { eq?.enabled = b } catch (e: Exception) {}
    }
    fun presets(): List<String> = eq?.let { e -> List(e.numberOfPresets.toInt()) { e.getPresetName(it.toShort()) } } ?: emptyList()
    fun usePreset(i: Int) {
        val e = eq ?: return
        try {
            e.usePreset(i.toShort())
            for (b in 0 until e.numberOfBands) sp()?.edit()?.putInt("b$b", e.getBandLevel(b.toShort()).toInt())?.apply()
        } catch (ex: Exception) {}
    }
    fun bassSupported(): Boolean = bass?.strengthSupported == true
    fun bassStrength(): Int = sp()?.getInt("bass", 0) ?: 0
    fun setBass(v: Int) {
        sp()?.edit()?.putInt("bass", v)?.apply()
        try { bass?.setStrength(v.toShort()); bass?.enabled = v > 0 } catch (e: Exception) {}
    }
}
