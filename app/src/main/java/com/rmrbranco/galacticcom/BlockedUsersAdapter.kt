package com.rmrbranco.galacticcom

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BlockedUserItem(
    val uid: String,
    val nickname: String,
    val avatarSeed: String
)

class BlockedUsersAdapter(
    private val blockedUsers: List<BlockedUserItem>,
    private val onUnblockClick: (BlockedUserItem) -> Unit
) : RecyclerView.Adapter<BlockedUsersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarImageView: ImageView = view.findViewById(R.id.iv_blocked_user_avatar)
        val nameTextView: TextView = view.findViewById(R.id.tv_blocked_user_name)
        val unblockButton: Button = view.findViewById(R.id.btn_unblock_user)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = blockedUsers[position]
        holder.nameTextView.text = user.nickname
        holder.unblockButton.setOnClickListener { onUnblockClick(user) }

        // Generate Avatar
        CoroutineScope(Dispatchers.Main).launch {
            val avatar = withContext(Dispatchers.Default) {
                AlienAvatarGenerator.generate(user.avatarSeed, 100, 100)
            }
            holder.avatarImageView.setImageBitmap(avatar)
        }
    }

    override fun getItemCount() = blockedUsers.size
}
