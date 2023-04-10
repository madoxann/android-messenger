package com.github.madoxann.hw6.network

import android.graphics.Bitmap
import com.fasterxml.jackson.annotation.JsonProperty

data class Message(
    val id: Long? = null,
    val from: String,
    val to: String,
    val data: Data?,
    val time: String?,
)

data class Data(
    var Image: Image? = null,
    val Text: Text? = null,
)

data class Text(
    @JsonProperty("text")
    val text: String = "",
)

data class Image(
    val link: String = "",
    var imageSrc: Bitmap? = null,
)
