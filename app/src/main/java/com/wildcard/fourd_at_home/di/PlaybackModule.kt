package com.wildcard.fourd_at_home.di

import android.content.Context
import com.wildcard.fourd_at_home.ble.CommandSender
import com.wildcard.fourd_at_home.playback.PlaybackSyncEngine
import com.wildcard.fourd_at_home.playback.TimelineParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 再生関連の依存性注入モジュール
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun provideTimelineParser(
        @ApplicationContext context: Context
    ): TimelineParser {
        return TimelineParser(context)
    }

    @Provides
    @Singleton
    fun providePlaybackSyncEngine(
        commandSender: CommandSender
    ): PlaybackSyncEngine {
        return PlaybackSyncEngine(commandSender)
    }
}
