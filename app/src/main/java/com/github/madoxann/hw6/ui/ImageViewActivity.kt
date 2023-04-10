package com.github.madoxann.hw6.ui

import android.content.*
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.madoxann.hw6.R
import com.github.madoxann.hw6.databinding.ActivityImageViewBinding
import com.github.madoxann.hw6.util.loadImageFromStorage
import com.github.madoxann.hw6.util.Constants

class ImageViewActivity : AppCompatActivity() {
    private val serviceReceiver = ServiceReceiver()
    private lateinit var bm: LocalBroadcastManager
    private lateinit var fullscreenImageBinding: ActivityImageViewBinding

    private var savedOut: Bitmap? = null
    private var isSingleInstance = true

    private lateinit var savedName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fullscreenImageBinding = ActivityImageViewBinding.inflate(layoutInflater)
        setContentView(fullscreenImageBinding.root)


        bm = LocalBroadcastManager.getInstance(applicationContext)
        savedName = intent.getStringExtra(Constants.TIME) + "FULL"
    }

    override fun onStart() {
        super.onStart()

        IntentFilter(Constants.IMAGE_FULL_READY).apply {
            this.addCategory(Intent.CATEGORY_DEFAULT)
            bm.registerReceiver(serviceReceiver, this)
        }

        IntentFilter(Constants.IMAGE_FULL_FAILED).apply {
            this.addCategory(Intent.CATEGORY_DEFAULT)
            bm.registerReceiver(serviceReceiver, this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.apply {
            putParcelable(Constants.SAVED_BITMAP, savedOut)
            putBoolean(Constants.IS_SINGLE, isSingleInstance)
        }

        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        savedOut = savedInstanceState.getParcelable(Constants.SAVED_BITMAP)
        isSingleInstance = savedInstanceState.getBoolean(Constants.IS_SINGLE)

        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        if (savedOut == null && !trySetting() && isSingleInstance) {
            isSingleInstance = false
            bm.sendBroadcast(
                Intent().setAction(Constants.GET_IMAGE)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .putExtra(Constants.IMG_NAME, savedName)
                    .putExtra(Constants.IMG_SRC, intent.getStringExtra(Constants.IMG_SRC)!!)
            )
        } else findViewById<ImageView>(R.id.message_image_full).setImageBitmap(savedOut)
    }

    override fun onStop() {
        super.onStop()

        bm.unregisterReceiver(serviceReceiver)
    }

    private fun trySetting(): Boolean {
        savedOut = loadImageFromStorage(
            savedName,
            applicationContext
        )
        if (savedOut == null) return false

        fullscreenImageBinding.messageImageFull.setImageBitmap(savedOut)
        return true
    }

    private inner class ServiceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                Constants.IMAGE_FULL_READY -> trySetting()
                Constants.IMAGE_FULL_FAILED -> {
                    Toast.makeText(
                        applicationContext,
                        "Couldn't load full image! Tag @faerytea",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }
}
