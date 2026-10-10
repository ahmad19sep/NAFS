package com.ahmad.nafs.core.di

import android.content.Context
import androidx.room.Room
import com.ahmad.nafs.core.database.CachedProfileDao
import com.ahmad.nafs.core.database.CachedHabitDao
import com.ahmad.nafs.core.database.CachedTaskDao
import com.ahmad.nafs.core.database.CachedGoalDao
import com.ahmad.nafs.core.database.CachedChallengeDao
import com.ahmad.nafs.core.database.CachedHealthDao
import com.ahmad.nafs.core.database.CachedDeenDao
import com.ahmad.nafs.core.database.CachedCheckInDao
import com.ahmad.nafs.core.database.CachedCoachDao
import com.ahmad.nafs.core.database.CachedReportDao
import com.ahmad.nafs.core.database.CachedLetterDao
import com.ahmad.nafs.core.database.CachedRecoveryDao
import com.ahmad.nafs.core.database.MIGRATION_12_13
import com.ahmad.nafs.core.database.MIGRATION_13_14
import com.ahmad.nafs.core.database.MIGRATION_14_15
import com.ahmad.nafs.core.database.RecordCacheDao
import com.ahmad.nafs.core.database.NafsDatabase
import com.ahmad.nafs.core.database.PendingMutationDao
import com.ahmad.nafs.core.database.MIGRATION_1_2
import com.ahmad.nafs.core.database.MIGRATION_2_3
import com.ahmad.nafs.core.database.MIGRATION_3_4
import com.ahmad.nafs.core.database.MIGRATION_4_5
import com.ahmad.nafs.core.database.MIGRATION_5_6
import com.ahmad.nafs.core.database.MIGRATION_6_7
import com.ahmad.nafs.core.database.MIGRATION_7_8
import com.ahmad.nafs.core.database.MIGRATION_8_9
import com.ahmad.nafs.core.database.MIGRATION_9_10
import com.ahmad.nafs.core.database.MIGRATION_10_11
import com.ahmad.nafs.core.database.MIGRATION_11_12
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NafsDatabase =
        Room.databaseBuilder(
            context,
            NafsDatabase::class.java,
            "nafs.db",
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
            )
            .build()

    @Provides
    fun provideCachedProfileDao(database: NafsDatabase): CachedProfileDao =
        database.cachedProfileDao()

    @Provides
    fun providePendingMutationDao(database: NafsDatabase): PendingMutationDao =
        database.pendingMutationDao()

    @Provides
    fun provideCachedTaskDao(database: NafsDatabase): CachedTaskDao =
        database.cachedTaskDao()

    @Provides
    fun provideCachedHabitDao(database: NafsDatabase): CachedHabitDao =
        database.cachedHabitDao()

    @Provides
    fun provideCachedGoalDao(database: NafsDatabase): CachedGoalDao =
        database.cachedGoalDao()

    @Provides
    fun provideCachedChallengeDao(database: NafsDatabase): CachedChallengeDao =
        database.cachedChallengeDao()

    @Provides
    fun provideCachedHealthDao(database: NafsDatabase): CachedHealthDao =
        database.cachedHealthDao()

    @Provides
    fun provideCachedDeenDao(database: NafsDatabase): CachedDeenDao = database.cachedDeenDao()

    @Provides
    fun provideCachedCheckInDao(database: NafsDatabase): CachedCheckInDao = database.cachedCheckInDao()

    @Provides
    fun provideCachedCoachDao(database: NafsDatabase): CachedCoachDao = database.cachedCoachDao()

    @Provides
    fun provideCachedReportDao(database: NafsDatabase): CachedReportDao = database.cachedReportDao()

    @Provides
    fun provideCachedLetterDao(database: NafsDatabase): CachedLetterDao = database.cachedLetterDao()

    @Provides
    fun provideCachedRecoveryDao(database: NafsDatabase): CachedRecoveryDao = database.cachedRecoveryDao()

    @Provides
    fun provideRecordCacheDao(database: NafsDatabase): RecordCacheDao = database.recordCacheDao()
}

