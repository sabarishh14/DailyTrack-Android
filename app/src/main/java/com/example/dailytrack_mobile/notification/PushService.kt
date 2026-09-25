package com.example.dailytrack_mobile.notification

import com.example.dailytrack_mobile.data.repository.MoneyRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Server pushes. Today only low-balance alerts, sent whenever a transaction —
 * added here, on the web, anywhere — leaves an account under its minimum.
 */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {

    @Inject
    lateinit var moneyRepository: MoneyRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] == "low_balance") {
            NotificationsHelper(this).showBalanceAlert(
                title = data["title"] ?: "Balance below minimum",
                body = data["body"].orEmpty()
            )
        }
    }

    override fun onNewToken(token: String) {
        scope.launch { moneyRepository.registerPushToken(token) }
    }
}
