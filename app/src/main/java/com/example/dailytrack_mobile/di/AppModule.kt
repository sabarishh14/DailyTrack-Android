package com.example.dailytrack_mobile.di

import android.content.Context
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.local.datastore.ThemeManager
import com.example.dailytrack_mobile.data.local.demo.DemoDataManager
import com.example.dailytrack_mobile.data.local.security.AppLockManager
import com.example.dailytrack_mobile.data.remote.api.DailyTrackApi
import com.example.dailytrack_mobile.data.repository.ActivitiesRepository
import com.example.dailytrack_mobile.data.repository.InvestmentsRepository
import com.example.dailytrack_mobile.data.repository.MoneyRepository
import com.example.dailytrack_mobile.data.repository.SabdekhoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideThemeManager(@ApplicationContext context: Context): ThemeManager =
        ThemeManager(context)

    @Provides
    @Singleton
    fun provideInvestPreferencesManager(@ApplicationContext context: Context): com.example.dailytrack_mobile.data.local.datastore.InvestPreferencesManager =
        com.example.dailytrack_mobile.data.local.datastore.InvestPreferencesManager(context)

    @Provides
    @Singleton
    fun provideAppLockManager(@ApplicationContext context: Context): AppLockManager =
        AppLockManager(context)

    @Provides
    @Singleton
    fun provideDemoModeManager(@ApplicationContext context: Context): DemoModeManager =
        DemoModeManager(context)

    @Provides
    @Singleton
    fun provideDemoDataManager(
        @ApplicationContext context: Context,
        demoModeManager: DemoModeManager
    ): DemoDataManager = DemoDataManager(context, demoModeManager)

    @Provides
    @Singleton
    fun provideMoneyRepository(
        api: DailyTrackApi,
        demoDataManager: DemoDataManager,
        @ApplicationContext context: Context
    ): MoneyRepository = MoneyRepository(api, demoDataManager, context)

    @Provides
    @Singleton
    fun provideActivitiesRepository(
        api: DailyTrackApi,
        demoDataManager: DemoDataManager
    ): ActivitiesRepository = ActivitiesRepository(api, demoDataManager)

    @Provides
    @Singleton
    fun provideInvestmentsRepository(
        api: DailyTrackApi,
        demoDataManager: DemoDataManager
    ): InvestmentsRepository = InvestmentsRepository(api, demoDataManager)

    @Provides
    @Singleton
    fun provideSabdekhoRepository(
        api: DailyTrackApi,
        demoDataManager: DemoDataManager,
        moshi: com.squareup.moshi.Moshi
    ): SabdekhoRepository = SabdekhoRepository(api, demoDataManager, moshi)

    @Provides
    @Singleton
    fun provideReminderManager(@ApplicationContext context: Context): com.example.dailytrack_mobile.data.local.reminder.ReminderManager =
        com.example.dailytrack_mobile.data.local.reminder.ReminderManager(context)

    @Provides
    @Singleton
    fun provideReminderScheduler(@ApplicationContext context: Context): com.example.dailytrack_mobile.domain.reminder.ReminderScheduler =
        com.example.dailytrack_mobile.data.reminder.ReminderSchedulerImpl(context)
}
