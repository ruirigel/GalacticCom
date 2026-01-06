package com.rmrbranco.galacticcom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val ACTION_SHOW_NOTIFICATION_BADGE = "com.rmrbranco.galacticcom.SHOW_BADGE"
        const val EXTRA_MESSAGE_TYPE = "message_type"
        private const val TAG = "MyFirebaseMsgService"
        // Changed Channel ID to force recreation with correct Importance
        private const val CHANNEL_ID = "fcm_channel_v3"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token generated: $token")
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message received from: ${remoteMessage.from}")
        processMessage(remoteMessage)
    }

    private fun processMessage(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        val messageType = data["type"]
        val senderId = data["senderId"]

        // Always broadcast for UI updates (badges, etc.)
        if (isAppInForeground()) {
            Log.d(TAG, "App is in the foreground. Broadcasting for UI update.")
            val intent = Intent(ACTION_SHOW_NOTIFICATION_BADGE).putExtra(EXTRA_MESSAGE_TYPE, messageType)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }

        when (messageType) {
            "pirate_arrival" -> {
                val pirateName = data["pirateName"] ?: "A mysterious pirate"
                val galaxy = data["galaxy"] ?: "a nearby galaxy"
                val notificationTitle = "Pirate Sighting!"
                val notificationBody = getPirateArrivalMessage(pirateName, galaxy)
                sendNotification(notificationTitle, notificationBody)
            }
            "private_message" -> {
                val recipientId = data["recipientId"]
                val conversationId = data["conversationId"]

                if (senderId.isNullOrEmpty() || recipientId.isNullOrEmpty()) {
                    Log.e(TAG, "Private message is missing senderId or recipientId.")
                    return
                }

                // Check if user is currently viewing this conversation
                if (isAppInForeground() && conversationId == AppLifecycleObserver.currentConversationId) {
                    Log.d(TAG, "User is currently in conversation $conversationId. Suppressing notification.")
                    return
                }

                val userRef = FirebaseDatabase.getInstance().getReference("users").child(recipientId)
                userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val blockedUsers = snapshot.child("blockedUsers").children.mapNotNull { it.key }.toSet()
                        if (senderId in blockedUsers) {
                            Log.d(TAG, "Sender $senderId is blocked. Notification suppressed.")
                            return
                        }
                        
                        val title = data["title"] ?: "New Private Message"
                        val body = data["body"] ?: ""
                        
                        if (conversationId.isNullOrEmpty()) {
                            Log.e(TAG, "Private message is missing 'conversationId'.")
                            sendNotification(title, "You received a new private message.")
                        } else {
                            decryptAndShowNotification(title, body, conversationId, senderId)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Database error when checking blocked users. Processing message anyway.", error.toException())
                        val title = data["title"] ?: "New Private Message"
                        val body = data["body"] ?: "You received a new message."
                        sendNotification(title, body)
                    }
                })
            }
            "public_message" -> {
                val title = data["title"] ?: "New Public Message"
                val body = data["body"] ?: "A new message was posted in a public channel."
                sendNotification(title, body)
            }
            else -> {
                Log.w(TAG, "Received a message with an unknown type: $messageType")
                val title = data["title"] ?: "New Notification"
                val body = data["body"] ?: "You have a new notification."
                sendNotification(title, body)
            }
        }
    }

    private fun getPirateArrivalMessage(pirateName: String, galaxy: String): String {
        val messages = listOf(
            "$pirateName has dropped anchor in $galaxy! They're rumored to have rare items for trade. Don't miss out!",
            "Word on the cosmic winds is that $pirateName is in $galaxy with a hold full of exotic goods. A perfect opportunity for a savvy captain!",
            "Ahoy! The infamous $pirateName has been spotted in $galaxy. Their treasure is your opportunity. Will you answer the call?",
            "A rare chance, captain! $pirateName's ship has appeared in $galaxy, offering goods not found anywhere else in the quadrant.",
            "Fortune favors the bold! $pirateName is trading in $galaxy. Seize the opportunity to acquire legendary gear!"
        )
        return messages.random()
    }

    private fun decryptAndShowNotification(title: String, encryptedBody: String, conversationId: String, senderId: String) {
        val myKeyPair = getKeyPairForConversation(conversationId)
        if (myKeyPair == null) {
            Log.e(TAG, "Could not retrieve KeyPair for conversation $conversationId. Cannot decrypt.")
            sendNotification(title, "You received an encrypted message.")
            return
        }

        val theirPublicKeyRef = FirebaseDatabase.getInstance().reference
            .child("conversations").child(conversationId)
            .child("public_keys").child(senderId)

        theirPublicKeyRef.get().addOnSuccessListener { theirPublicKeySnapshot ->
            val theirPublicKeyEncoded = theirPublicKeySnapshot.getValue(String::class.java)
            if (theirPublicKeyEncoded == null) {
                Log.e(TAG, "Could not find the public key for sender $senderId in conversation $conversationId.")
                sendNotification(title, "Key error: Cannot decrypt message.")
                return@addOnSuccessListener
            }

            try {
                val theirPublicKey = CryptoManager.decodePublicKeyFromBase64(theirPublicKeyEncoded)
                val sharedSecret = CryptoManager.getSharedSecret(myKeyPair.private, theirPublicKey)
                val decryptedText = CryptoManager.decrypt(encryptedBody, sharedSecret) ?: "[Message could not be decrypted]"
                sendNotification(title, decryptedText)
            } catch (e: Exception) {
                Log.e(TAG, "Decryption process failed for conversation $conversationId", e)
                sendNotification(title, "A decryption error occurred.")
            }
        }.addOnFailureListener { error ->
            Log.e(TAG, "Failed to retrieve public key for decryption.", error)
            sendNotification(title, "Database error while decrypting message.")
        }
    }
    
    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val truncatedBody = if (messageBody.length > 100) messageBody.take(100) + "..." else messageBody

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logotipo)
            .setColor(getColor(R.color.neon_cyan)) // Set accent color
            .setContentTitle(title)
            .setContentText(truncatedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensure Heads-up notification
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the channel if O or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fcm_channel_name),
                NotificationManager.IMPORTANCE_HIGH // High importance for Heads-up
            ).apply {
                description = "Channels for GalacticCom notifications"
                enableLights(true)
                lightColor = getColor(R.color.neon_cyan)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun getKeyPairForConversation(conversationId: String): KeyPair? {
        val prefs = getSharedPreferences("CryptoPrefs", Context.MODE_PRIVATE)
        val privateKeyPrefKey = "private_key_${conversationId}"
        val publicKeyPrefKey = "public_key_${conversationId}"

        val privateKeyEncoded = prefs.getString(privateKeyPrefKey, null)
        val publicKeyEncoded = prefs.getString(publicKeyPrefKey, null)

        return if (privateKeyEncoded != null && publicKeyEncoded != null) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyEncoded)))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyEncoded)))
                KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                Log.e("Crypto", "Error reconstructing KeyPair in Service", e)
                null
            }
        } else {
            null
        }
    }

    private fun sendRegistrationToServer(token: String?) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val tokenPath = "/users/$userId/fcmToken"
        FirebaseDatabase.getInstance().getReference(tokenPath).setValue(token)
    }

    private fun isAppInForeground(): Boolean {
        return AppLifecycleObserver.isAppInForeground
    }
}
