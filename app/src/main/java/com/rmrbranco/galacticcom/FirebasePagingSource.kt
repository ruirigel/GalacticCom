package com.rmrbranco.galacticcom

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import kotlinx.coroutines.tasks.await

class FirebasePagingSource(
    private val dbRef: DatabaseReference,
    private val currentUserId: String,
    private val currentUserNickname: String,
    private val recipientNickname: String,
    private val currentUserAvatarSeed: String,
    private val recipientAvatarSeed: String
) : PagingSource<String, DisplayMessage>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, DisplayMessage> {
        try {
            var query = dbRef.orderByKey()
            val currentPageKey = params.key
            if (currentPageKey != null) {
                query = query.endBefore(currentPageKey)
            }

            val dataSnapshot = query.limitToLast(params.loadSize).get().await()
            val messages = dataSnapshot.children.mapNotNull { it.toMessage() }

            if (messages.isEmpty()) {
                return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }

            val prevKey = messages.first().chatMessage.id

            return LoadResult.Page(
                data = messages.reversed(),
                prevKey = prevKey,
                nextKey = null // We only page backwards
            )
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, DisplayMessage>): String? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestItemToPosition(anchorPosition)?.chatMessage?.id
        }
    }

    private fun DataSnapshot.toMessage(): DisplayMessage? {
        val message = this.getValue(ChatMessage::class.java)?.apply { id = key } ?: return null
        val isSentByCurrentUser = message.senderId == currentUserId
        return DisplayMessage(
            chatMessage = message,
            isSentByCurrentUser = isSentByCurrentUser,
            senderNickname = if (isSentByCurrentUser) currentUserNickname else recipientNickname,
            senderAvatarSeed = if (isSentByCurrentUser) currentUserAvatarSeed else recipientAvatarSeed,
            recipientAvatarSeed = if (isSentByCurrentUser) recipientAvatarSeed else currentUserAvatarSeed
        )
    }
}
