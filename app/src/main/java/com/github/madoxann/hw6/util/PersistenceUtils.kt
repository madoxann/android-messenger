package com.github.madoxann.hw6.util

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.*

fun loadImageFromStorage(name: String, ctx: Context): Bitmap? {
    val cw = ContextWrapper(ctx)

    with(
        File(
            cw.cacheDir,
            "$name.png"
        )
    ) {
        if (this.exists()) {
            try {
                return BitmapFactory.decodeFile(this.absolutePath)
            } catch (e: FileNotFoundException) {
                Log.d("debug-persist", "crashed NO FILE - msg = $e.message")
            } catch (e: IOException) {
                Log.d("debug-persist", "crashed IO - msg = $e.message")
            }
        }
    }

    return null
}

fun saveImageToStorage(name: String, bp: Bitmap, ctx: Context) {
    val cw = ContextWrapper(ctx)

    try {
        FileOutputStream(
            File(cw.cacheDir, "$name.png")
        ).use {
            bp.compress(
                Bitmap.CompressFormat.PNG,
                100, it
            )
            it.flush()
        }
    } catch (e: Exception) {
        Log.d("thread2", "crashed - msg = $e.message")
    }
}
