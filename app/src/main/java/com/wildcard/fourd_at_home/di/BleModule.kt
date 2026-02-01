package com.wildcard.fourd_at_home.di

import android.content.Context
import com.wildcard.fourd_at_home.ble.BleDeviceManager
import com.wildcard.fourd_at_home.ble.BleScanner
import com.wildcard.fourd_at_home.ble.CommandSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * BLE関連の依存性注入モジュール
 */
@Module
@InstallIn(SingletonComponent::class)
object BleModule {

    @Provides
    @Singleton
    fun provideBleScanner(
        @ApplicationContext context: Context
    ): BleScanner {
        return BleScanner(context)
    }

    @Provides
    @Singleton
    fun provideBleDeviceManager(
        @ApplicationContext context: Context
    ): BleDeviceManager {
        return BleDeviceManager(context)
    }

    @Provides
    @Singleton
    fun provideCommandSender(
        deviceManager: BleDeviceManager
    ): CommandSender {
        return CommandSender(deviceManager)
    }
}
