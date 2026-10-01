package com.example.dailytrack_mobile.data.local.auth

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.dailytrack_mobile.data.local.datastore.InvestPreferencesManager
import com.example.dailytrack_mobile.data.local.datastore.SyncPreferencesManager
import com.example.dailytrack_mobile.data.local.datastore.TransactionDraftStore
import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.data.local.routines.RoutineReminders
import com.example.dailytrack_mobile.data.repository.ActivitiesRepository
import com.example.dailytrack_mobile.data.repository.InvestmentsRepository
import com.example.dailytrack_mobile.data.repository.MoneyRepository
import com.example.dailytrack_mobile.data.repository.SabdekhoRepository
import com.example.dailytrack_mobile.notification.routines.RoutineAlarmService
import com.example.dailytrack_mobile.notification.routines.RoutineCheckInScheduler
import com.example.dailytrack_mobile.widget.RoutinesWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each person's data stays theirs on a shared phone. When someone other than the
 * last person signs in, everything the last one left behind goes first: cached
 * money, films and investments, unsaved drafts, reminders and their alarms. The
 * same person signing back in keeps all of it.
 */
@Singleton
class PersonalDataReset @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val moneyRepository: MoneyRepository,
    private val investmentsRepository: InvestmentsRepository,
    private val sabdekhoRepository: SabdekhoRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val drafts: TransactionDraftStore,
    private val syncPreferences: SyncPreferencesManager,
    private val reminders: RoutineReminders,
    private val checkInSettings: RoutineCheckInSettings,
    private val scheduler: RoutineCheckInScheduler
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /**
     * Call before [email]'s session is saved (and once more when access is
     * confirmed), so nothing of anyone else's ever shows for them.
     */
    suspend fun claimFor(email: String) {
        val person = email.trim().lowercase()
        if (person.isEmpty()) return
        val last = prefs.getString(KEY_LAST_PERSON, null)
        when {
            last == person -> return
            // First run of a build that keeps people apart: the caches may hold
            // data from when everyone saw the owner's. Their own drafts and
            // reminders were always theirs, so those stay.
            last == null -> forgetCaches()
            else -> forgetLastPerson()
        }
        prefs.edit().putString(KEY_LAST_PERSON, person).apply()
    }

    private fun forgetCaches() {
        moneyRepository.forgetPerson()
        investmentsRepository.clearCache()
        sabdekhoRepository.forgetPerson()
        activitiesRepository.clearCache()
    }

    private suspend fun forgetLastPerson() {
        forgetCaches()
        drafts.clear()
        syncPreferences.setLetterboxdUsername("")
        runCatching { InvestPreferencesManager(context).setAllCategoriesVisibility(true) }

        // Their reminders and nightly check-in were about their routines.
        reminders.all().keys.forEach { scheduler.cancelReminder(it) }
        reminders.clearAll()
        scheduler.cancel()
        checkInSettings.update { RoutineCheckInSettings.Settings() }
        RoutineAlarmService.stop(context)
        NotificationManagerCompat.from(context).cancelAll()
        RoutinesWidget.refreshAll(context)
    }

    private companion object {
        const val PREFS_NAME = "signed_in_person"
        const val KEY_LAST_PERSON = "last_person"
    }
}
