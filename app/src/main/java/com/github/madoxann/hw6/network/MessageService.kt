package com.github.madoxann.hw6.network

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.madoxann.hw6.database.*
import com.github.madoxann.hw6.util.Constants
import com.github.madoxann.hw6.util.loadImageFromStorage
import com.github.madoxann.hw6.util.saveImageToStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.*


class MessageService : Service() {
    val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var bm: LocalBroadcastManager
    private val serviceReceiver = ServiceReceiver()
    private var images: MutableList<Pair<Int, Bitmap?>> = mutableListOf()
    var data: MutableList<Message> = mutableListOf()

    val mutex = Mutex()

    private var getDelay = 0L
    private var resendDelay = 0L
    private var imageDelay = 5000L
    private var connectionProblemIsKnown = false

    private lateinit var database: MessageDao
    lateinit var sentDatabase: RequestDao

    private lateinit var srv: ServerAPI

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onBind(intent: Intent): Binder = MessageBinder()

    inner class MessageBinder : Binder() {
        val service: MessageService
            get() = this@MessageService
    }

    override fun onCreate() {
        super.onCreate()

        bm = LocalBroadcastManager.getInstance(applicationContext)

        IntentFilter(Constants.SEND_MSG).apply {
            this.addCategory(Intent.CATEGORY_DEFAULT)
            bm.registerReceiver(serviceReceiver, this)
        }

        IntentFilter(Constants.GET_IMAGE).apply {
            this.addCategory(Intent.CATEGORY_DEFAULT)
            bm.registerReceiver(serviceReceiver, this)
        }

        srv = Retrofit.Builder().baseUrl(Constants.HOST).addConverterFactory(
            JacksonConverterFactory.create(
                JsonMapper.builder()
                    .serializationInclusion(JsonInclude.Include.NON_NULL)
                    .build()
                    .setPropertyNamingStrategy(object : PropertyNamingStrategy() {
                        override fun nameForSetterMethod(
                            config: MapperConfig<*>?,
                            method: AnnotatedMethod?,
                            defaultName: String?,
                        ): String =
                            if (defaultName == "text") "Text" else super.nameForSetterMethod(
                                config,
                                method,
                                defaultName
                            )

                        override fun nameForGetterMethod(
                            config: MapperConfig<*>?,
                            method: AnnotatedMethod?,
                            defaultName: String?,
                        ): String =
                            if (defaultName == "text") "Text" else super.nameForGetterMethod(
                                config,
                                method,
                                defaultName
                            )
                    })
                    .registerModule(KotlinModule.Builder().build())
            )
        ).build().create(ServerAPI::class.java)

        scope.launch {
            readyDb()
            startJobs()
        }
    }

    private fun readyDb() {
        database = Room.databaseBuilder(
            applicationContext,
            MessageDatabase::class.java,
            "message-database-hw6"
        ).build().messageDao()

        database.getAll().forEach {
            data += it.toMessage()
        }

        var imgCnt: Long = 1
        data.forEachIndexed { cnt, _ ->
            if (data[cnt].data!!.Image != null) {
                scope.launch {
                    delay(imgCnt++ * 60) // we don't want UI to lag, right?
                    getImage(cnt)
                }
            }
        }

        bm.sendBroadcast(
            Intent().setAction(Constants.DB_READY)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra(Constants.RESPONSE, data.size)
        )

        sentDatabase = Room.databaseBuilder(
            applicationContext,
            SentDatabase::class.java,
            "sent-database-hw6"
        ).build().sentDao()
    }

    private val messageGetJob = scope.launch (start = CoroutineStart.LAZY) {
        while (isActive) {
            getMsg()
            delay(getDelay)
        }
    }

    private val imageSetJob = scope.launch (start = CoroutineStart.LAZY) {
        while (isActive) {
            setAllImages()
            delay(imageDelay)
        }
    }

    private val messageResendJob = scope.launch (start = CoroutineStart.LAZY) {
        while (isActive) {
            sendEnqueued()
            delay(resendDelay)
        }
    }

    private fun startJobs() {
        messageGetJob.start()
        messageResendJob.start()
        imageSetJob.start()
    }

    override fun onDestroy() {
        super.onDestroy()

        bm.unregisterReceiver(serviceReceiver)
        messageGetJob.cancel()
        messageResendJob.cancel()
        imageSetJob.cancel()
        scope.cancel()
    }


    private suspend fun getMsg() {
        kotlin.runCatching {
            val sizeBefore = data.size
            val ans = srv.getMsg(Constants.CHANNEL, 100, if (data.isNotEmpty()) data.last().id!! else 0)

            mutex.withLock {
                data += ans
            }

            val dbList = mutableListOf<MessageDB>()
            ans.forEachIndexed { cnt, msg ->
                dbList += msg.toDB()

                if (msg.data?.Image != null && msg.data.Image!!.link.isNotBlank())
                    scope.launch { getImage(sizeBefore + cnt) }

            }

            database.insert(dbList)

            bm.sendBroadcast(
                Intent().setAction(Constants.REQUEST_GET_MESSAGE)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .putExtra(Constants.RESPONSE_CODE, Constants.MESSAGE_GET_SUCCESS)
                    .putExtra(Constants.RESPONSE, sizeBefore)
            )

            connectionProblemIsKnown = false
            getDelay = if (ans.isEmpty()) Constants.MESSAGE_DELAY else 0
        }.onFailure {
            getDelay = Constants.MESSAGE_DELAY
            when (it) {
                is SocketTimeoutException, is IOException -> {
                    if (!connectionProblemIsKnown) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                applicationContext,
                                "Connection timeout! Tag @faerytea",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        connectionProblemIsKnown = true
                    }
                }
                else -> Log.d(
                    "debug",
                    "Uncaught exception ${it.message ?: ""}}"
                )
            }
        }
    }

    private suspend fun getImage(cnt: Int) {
        val img = data[cnt].data!!.Image!!
        val out: Bitmap?

        try {
            out = loadImageFromStorage(data[cnt].time!!, applicationContext)
                ?: BitmapFactory.decodeStream(
                    srv.getImgThumb(img.link).byteStream()
                )

            if (out != null)
                saveImageToStorage(
                    data[cnt].time!!,
                    out,
                    applicationContext
                )
        } catch (e: Exception) {
            Log.d("debug", "crashed $cnt - msg = $e.message") // Lots of server errors
            return
        }

        mutex.withLock {
            images += Pair(cnt, out)
        }
    }

    private suspend fun setAllImages() {
        mutex.withLock {
            images.forEach {
                if (data[it.first].data!!.Image!!.imageSrc == null) {
                    data[it.first].data!!.Image!!.imageSrc = it.second

                    bm.sendBroadcast(
                        Intent().setAction(Constants.IMAGE_READY)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .putExtra(Constants.RESPONSE, it.first)
                    )
                }
            }
        }
    }

    private suspend fun loadFullImageToMemory(name: String, link: String?) {
        if (link == null) throw IllegalArgumentException()

        kotlin.runCatching {
            var out = loadImageFromStorage(name, applicationContext)

            if (out == null) {
                out = BitmapFactory.decodeStream(srv.getImgFull(link).byteStream())
                saveImageToStorage(name, out, applicationContext)
            }
        }.onSuccess {
            bm.sendBroadcast(
                Intent().setAction(Constants.IMAGE_FULL_READY)
                    .addCategory(Intent.CATEGORY_DEFAULT)
            )
        }.onFailure {
            when (it) {
                is SocketException, is IllegalArgumentException -> {
                    bm.sendBroadcast(
                        Intent().setAction(Constants.IMAGE_FULL_FAILED)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                    )
                }
                else -> Log.d(
                    "debug",
                    "Uncaught exception ${it.message ?: ""}\n${it.printStackTrace()} in MessageService#loadFullImageToMemory"
                )
            }
        }
    }

    private suspend fun trySending(request: RequestDB): Int {
        if (request.isImage) {
            val tmp = with(File(applicationContext.cacheDir, "${request.id}.png")) {
                if (!this.exists())
                    try {
                        val stream =
                            applicationContext.contentResolver.openInputStream(Uri.parse(request.request))
                        org.apache.commons.io.FileUtils.copyInputStreamToFile(stream, this)
                        this@with
                    } catch (e: IOException) {
                        e.message?.let { Log.d("debug", it) }
                        null
                    }
                else this
            } ?: return -1

            val response = srv.sendImg(
                Constants.CHANNEL,
                Message(
                    null, Constants.SENDER_NAME, "1@channel", null, null
                ),
                MultipartBody.Part.createFormData(
                    "picture", tmp.absolutePath, RequestBody.create(
                        MediaType.parse("multipart/form-data"), tmp
                    )
                )
            )

            if (response.code() == HttpURLConnection.HTTP_OK) tmp.delete()
            return response.code()
        }

        val send = srv.sendMsg(
            Constants.CHANNEL,
            Message(
                null,
                Constants.SENDER_NAME,
                "1@channel",
                Data(null, Text(request.request ?: "")),
                null
            )
        )
        return send.code()
    }

    private suspend fun sendEnqueued() {
        resendDelay = Constants.MESSAGE_DELAY
        kotlin.runCatching {
            mutex.withLock {
                with(sentDatabase.getFirst()) {
                    if (this.request != null && trySending(this) == HttpURLConnection.HTTP_OK) {
                        sentDatabase.delete(this)
                        resendDelay = 0
                    }
                }
            }
        }.onFailure {
            Log.d(
            "debug",
            "Uncaught exception ${it.message ?: ""}\n${it.printStackTrace()} MessageService#ServiceReceiver#onReceive"
            )
        }
    }

    private inner class ServiceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                Constants.SEND_MSG -> {
                    val isImage = intent.getBooleanExtra(Constants.IS_IMAGE, false)
                    val request = intent.getStringExtra(Constants.MSG) ?: ""

                    scope.launch {
                        val toRequestDB = RequestDB(
                            Date().time,
                            request,
                            isImage
                        )

                        kotlin.runCatching {
                            when (val response = trySending(toRequestDB)) {
                                HttpURLConnection.HTTP_OK -> {
                                    bm.sendBroadcast(
                                        Intent().setAction(Constants.REQUEST_SEND_MESSAGE)
                                            .addCategory(Intent.CATEGORY_DEFAULT)
                                            .putExtra(Constants.RESPONSE_CODE, HttpURLConnection.HTTP_OK)
                                    )
                                }
                                else -> {
                                    bm.sendBroadcast(
                                        Intent().setAction(Constants.REQUEST_SEND_MESSAGE)
                                            .addCategory(Intent.CATEGORY_DEFAULT)
                                            .putExtra(Constants.RESPONSE_CODE, response)
                                    )
                                }
                            }
                        }.onFailure {
                            when (it) {
                                is SocketException, is IOException -> {
                                    bm.sendBroadcast(
                                        Intent().setAction(Constants.REQUEST_SEND_MESSAGE)
                                            .addCategory(Intent.CATEGORY_DEFAULT)
                                            .putExtra(Constants.RESPONSE_CODE, Constants.MESSAGE_GET_FAILED)
                                    )
                                }
                                else -> Log.d(
                                    "debug",
                                    "Uncaught exception ${it.message ?: ""}\n${it.printStackTrace()} MessageService#ServiceReceiver#onReceive"
                                )
                            }

                            mutex.withLock {
                                sentDatabase.insert(toRequestDB)
                            }
                        }

                    }
                }

                Constants.GET_IMAGE -> scope.launch {
                    loadFullImageToMemory(
                        intent.getStringExtra(
                            Constants.IMG_NAME,
                        )!!, intent.getStringExtra(Constants.IMG_SRC)
                    )
                }
            }
        }
    }
}
