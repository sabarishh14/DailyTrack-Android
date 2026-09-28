package com.example.dailytrack_mobile

import android.app.Application
import com.example.dailytrack_mobile.data.repository.RoutinesRepository
import com.example.dailytrack_mobile.widget.RoutinesWidget
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DailyTrackApp : Application() {

    @Inject lateinit var routinesRepository: RoutinesRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // The home-screen widget follows every change to the routines, wherever it
        // came from: the app, a notification, an alarm or the widget itself.
        appScope.launch {
            routinesRepository.snapshot.filterNotNull().collectLatest {
                delay(WIDGET_REFRESH_DEBOUNCE_MS) // a burst of changes redraws once
                RoutinesWidget.refreshAll(this@DailyTrackApp)
            }
        }
    }

    private companion object {
        const val WIDGET_REFRESH_DEBOUNCE_MS = 400L
    }
}
