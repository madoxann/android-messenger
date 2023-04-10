package com.github.madoxann.hw6.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.madoxann.hw6.R
import com.github.madoxann.hw6.network.Message
import com.github.madoxann.hw6.util.Constants

class MessageAdapter(
    private var data: List<Message>,
) : RecyclerView.Adapter<BaseMessageViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseMessageViewHolder {
        return if (viewType == Constants.SENT_MESSAGE)
            SentMessageViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.messeage_item, parent, false)
            )
        else
            ReceivedMessageViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.message_item_other, parent, false)
            )
    }

    // well, logic should be ID-dependant because two users can easily have same name...
    // but let's not think 'bout that
    override fun getItemViewType(position: Int) =
        synchronized(data) {
            if (data[position].from == Constants.SENDER_NAME)
                Constants.SENT_MESSAGE
            else Constants.RECEIVED_MESSAGE
        }

    override fun getItemCount(): Int = synchronized(data) { data.size }

    override fun onBindViewHolder(holder: BaseMessageViewHolder, position: Int) {
        synchronized(data) {
            holder.bind(data[position])

            holder.messageView.visibility = if (data[position].data!!.Image?.imageSrc != null) {
                holder.imageView.setOnClickListener {
                    it.context.startActivity(
                        Intent(it.context, ImageViewActivity::class.java)
                            .putExtra(Constants.IMG_SRC, data[position].data!!.Image!!.link)
                            .putExtra(Constants.TIME, data[position].time)
                    )
                }
                View.GONE // it's either picture or message
            } else View.VISIBLE
        }
    }

    fun getLastIndex(): Int = synchronized(data) { data.lastIndex }
}
