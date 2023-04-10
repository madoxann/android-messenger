package com.github.madoxann.hw6.ui

import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.madoxann.hw6.R
import com.github.madoxann.hw6.databinding.ActivityMainBinding
import com.github.madoxann.hw6.network.MessageService
import com.github.madoxann.hw6.util.Constants
import java.net.HttpURLConnection

class MainActivity : AppCompatActivity() {
    lateinit var mainActivity: ActivityMainBinding

    private lateinit var bm: LocalBroadcastManager
    private val serviceReceiver = ServiceReceiver()

    private var service: MessageService? = null

    private var layoutState: Parcelable? = null
    private var isBound = false
    private var imgUri: Uri? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val msgService = (service as MessageService.MessageBinder).service
            this@MainActivity.service = msgService

            if (mainActivity.messageList.adapter == null)
                mainActivity.messageList.adapter = MessageAdapter(this@MainActivity.service!!.data)

            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            this@MainActivity.service = null
        }
    }

    private var imageChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data.let {
                mainActivity.clipButton.setImageURI(it)
                imgUri = it
                disableInput()
            }
        }
    }

    private inner class ServiceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                Constants.REQUEST_GET_MESSAGE -> {
                    val sizeBefore = intent.getIntExtra(Constants.RESPONSE, 0)

                    mainActivity.messageList.adapter!!.notifyItemRangeInserted(
                        sizeBefore,
                        (mainActivity.messageList.adapter as MessageAdapter).itemCount - sizeBefore
                    )
                }

                Constants.DB_READY -> mainActivity.messageList.adapter!!.notifyDataSetChanged() // fine, since called once


                Constants.IMAGE_READY -> {
                    val rec = intent.getIntExtra(Constants.RESPONSE, 0)
                    mainActivity.messageList.adapter!!.notifyItemChanged(rec)
                }

                Constants.REQUEST_SEND_MESSAGE -> {
                    when (val response = intent.getIntExtra(
                        Constants.RESPONSE_CODE,
                        Constants.MESSAGE_GET_FAILED
                    )) {
                        HttpURLConnection.HTTP_OK -> Log.d("debug", "SENT SUCCESS")
                        else -> Log.d("debug", "SENT FAILED, code $response")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainActivity = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainActivity.root)

        bm = LocalBroadcastManager.getInstance(applicationContext)

        mainActivity.messageList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            if (savedInstanceState != null)
                (layoutManager as LinearLayoutManager).onRestoreInstanceState(
                    savedInstanceState.getParcelable(
                        Constants.POSITION
                    )
                )
        }

        mainActivity.sendButton.setOnClickListener {
            val intent = Intent().setAction(Constants.SEND_MSG)
                .addCategory(Intent.CATEGORY_DEFAULT)

            if (imgUri != null) {
                bm.sendBroadcast(
                    intent.putExtra(Constants.MSG, imgUri.toString())
                        .putExtra(Constants.IS_IMAGE, true)
                )
                imgUri = null
                restoreDefaultInputState()

                return@setOnClickListener
            }

            if (mainActivity.messageInput.text.isNotBlank()) {
                bm.sendBroadcast(
                    intent.putExtra(Constants.MSG, mainActivity.messageInput.text.toString())
                        .putExtra(Constants.IS_IMAGE, false)
                )

                mainActivity.messageInput.text = null
            }
        }

        with(mainActivity.scrollButton) {
            mainActivity.messageList.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (scrollY - oldScrollY > 0 && (mainActivity.messageList.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                    != (mainActivity.messageList.adapter as MessageAdapter).getLastIndex()
                ) this.visibility = View.VISIBLE
                else this.visibility = View.INVISIBLE
            }

            this.setOnClickListener {
                val last = (mainActivity.messageList.adapter as MessageAdapter).getLastIndex()

                if (last - (mainActivity.messageList.layoutManager as LinearLayoutManager).findLastVisibleItemPosition() > 30)
                    (mainActivity.messageList.layoutManager as LinearLayoutManager).scrollToPosition(
                        if (last - 30 > 0) last - 30 else last
                    )

                (mainActivity.messageList.layoutManager as LinearLayoutManager).smoothScrollToPosition(
                    mainActivity.messageList,
                    RecyclerView.State(),
                    last
                )
            }
        }

        mainActivity.clipButton.setOnClickListener {
            imageChooser.launch(Intent().apply {
                type = "image/*"
                action = Intent.ACTION_GET_CONTENT
            })
        }
    }

    override fun onStart() {
        super.onStart()

        Intent(this, MessageService::class.java).also {
            startService(it)
            bindService(it, connection, BIND_AUTO_CREATE)
        }

        IntentFilter(Constants.REQUEST_GET_MESSAGE).apply {
            this.addCategory(Intent.CATEGORY_DEFAULT)
            bm.registerReceiver(serviceReceiver, this)
        }

        IntentFilter(Constants.DB_READY).apply {
            this.addCategory(Intent.CATEGORY_DEFAULT)
            bm.registerReceiver(serviceReceiver, this)
        }

        IntentFilter(Constants.REQUEST_SEND_MESSAGE).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            bm.registerReceiver(serviceReceiver, this)
        }

        IntentFilter(Constants.IMAGE_READY).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            bm.registerReceiver(serviceReceiver, this)
        }
    }

    override fun onPause() {
        super.onPause()

        layoutState = (mainActivity.messageList.layoutManager as LinearLayoutManager).onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()

        (mainActivity.messageList.layoutManager as LinearLayoutManager).onRestoreInstanceState(layoutState)
    }

    override fun onStop() {
        super.onStop()

        if (isBound) unbindService(connection)
        bm.unregisterReceiver(serviceReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.apply {
            putString(Constants.MSG, mainActivity.messageInput.text.toString())
            putParcelable(Constants.IMAGE, imgUri)
            putParcelable(
                Constants.POSITION,
                layoutState
            )
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        mainActivity.messageInput.setText(savedInstanceState.getString(Constants.MSG))
        imgUri = savedInstanceState.getParcelable(Constants.IMAGE)
        mainActivity.clipButton.setImageURI(imgUri)
    }

    private fun disableInput() {
        mainActivity.messageInput.text = null
        mainActivity.messageInput.isEnabled = false
        mainActivity.messageInput.hint = getString(R.string.input_disabled)
    }

    private fun restoreDefaultInputState() {
        mainActivity.clipButton.setImageBitmap(
            BitmapFactory.decodeResource(
                resources,
                R.drawable.paper_clip_6_xxl
            )
        )

        mainActivity.messageInput.isEnabled = true
        mainActivity.messageInput.hint = getString(R.string.enter_message)
    }
}
