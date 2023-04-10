package com.github.madoxann.hw6.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.madoxann.hw6.network.Data
import com.github.madoxann.hw6.network.Message
import com.github.madoxann.hw6.network.Text
import com.github.madoxann.hw6.network.Image

@Entity
data class MessageDB(
    @PrimaryKey val id: Long? = null,
    val from: String,
    val to: String,
    val text: String,
    val link: String,
    val time: String,
)

@Entity
data class RequestDB(
    @PrimaryKey val id: Long,
    val request: String?,
    val isImage: Boolean,
)

fun Message.toDB(): MessageDB = MessageDB(
    id,
    from,
    to,
    data?.Text?.text ?: "",
    data?.Image?.link ?: "",
    time ?: ""
)

fun MessageDB.toMessage() = Message(
    id,
    from,
    to,
    if (link.isBlank()) Data(null, Text(text))
    else Data(
        Image(link, null),
        null
    ),
    time
)
