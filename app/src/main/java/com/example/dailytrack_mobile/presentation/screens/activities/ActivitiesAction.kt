package com.example.dailytrack_mobile.presentation.screens.activities

import java.time.Month

sealed class ActivitiesAction {
    data class OnMonthChanged(val month: Month, val year: Int) : ActivitiesAction()
    object Refresh : ActivitiesAction()
    /** The screen came back to the foreground. */
    object Resumed : ActivitiesAction()
}
