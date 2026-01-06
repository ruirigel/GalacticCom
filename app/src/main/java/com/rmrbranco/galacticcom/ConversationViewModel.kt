package com.rmrbranco.galacticcom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.flow.Flow

class ConversationViewModel : ViewModel() {

    fun getMessages(
        dbRef: DatabaseReference,
        currentUserId: String,
        currentUserNickname: String,
        recipientNickname: String,
        currentUserAvatarSeed: String,
        recipientAvatarSeed: String
    ): Flow<PagingData<DisplayMessage>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                FirebasePagingSource(
                    dbRef = dbRef,
                    currentUserId = currentUserId,
                    currentUserNickname = currentUserNickname,
                    recipientNickname = recipientNickname,
                    currentUserAvatarSeed = currentUserAvatarSeed,
                    recipientAvatarSeed = recipientAvatarSeed
                )
            }
        ).flow.cachedIn(viewModelScope)
    }
}
