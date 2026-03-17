package com.example.locationshare.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.locationshare.R
import com.example.locationshare.model.Friend

class FriendAdapter(
    private val onFriendClick: (Friend) -> Unit,
    private val onDeleteClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    private var friends: List<Friend> = emptyList()
    private var selectedFriendId: String? = null

    fun setFriends(newFriends: List<Friend>) {
        friends = newFriends
        notifyDataSetChanged()
    }

    fun setSelected(friendId: String?) {
        selectedFriendId = friendId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        holder.bind(friend, friend.friendId == selectedFriendId)
    }

    override fun getItemCount(): Int = friends.size

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvFriendName)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteFriend)

        fun bind(friend: Friend, isSelected: Boolean) {
            tvName.text = friend.friendName

            itemView.setOnClickListener {
                onFriendClick(friend)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(friend)
            }

            // 选中状态高亮 - 未选中黑底白字，选中蓝底白字
            if (isSelected) {
                itemView.setBackgroundColor(itemView.context.getColor(android.R.color.holo_blue_light))
            } else {
                itemView.setBackgroundColor(itemView.context.getColor(android.R.color.black))
            }
            tvName.setTextColor(itemView.context.getColor(android.R.color.white))
        }
    }
}
