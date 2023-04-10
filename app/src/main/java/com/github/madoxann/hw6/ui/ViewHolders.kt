package com.github.madoxann.hw6.ui

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.madoxann.hw6.R
import com.github.madoxann.hw6.network.Message
import java.text.SimpleDateFormat
import java.util.*

sealed class BaseMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract val senderView: TextView?
    abstract val messageView: TextView
    abstract val timeView: TextView
    abstract val imageView: ImageView

    fun bind(msg: Message) {
        senderView?.text = msg.from
        messageView.text = msg.data!!.Text?.text ?: ""
        imageView.setImageBitmap(msg.data.Image?.imageSrc)

        with(SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)) {
            timeView.text = this.format(Date(msg.time!!.toLong()))
        }
    }
}

class SentMessageViewHolder(itemView: View) : BaseMessageViewHolder(itemView) {
    override val senderView: TextView? = null
    override val messageView: TextView = itemView.findViewById(R.id.message)
    override val timeView: TextView = itemView.findViewById(R.id.timestamp)
    override val imageView: ImageView = itemView.findViewById(R.id.message_image)
}

class ReceivedMessageViewHolder(itemView: View) : BaseMessageViewHolder(itemView) {
    override val senderView: TextView = itemView.findViewById(R.id.sender_name)
    override val messageView: TextView = itemView.findViewById(R.id.message_other)
    override val timeView: TextView = itemView.findViewById(R.id.timestamp_other)
    override val imageView: ImageView = itemView.findViewById(R.id.message_image_other)
}
