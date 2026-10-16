package com.byteclub.meetyourfriend.di

import android.content.Context
import androidx.room.Room
import com.byteclub.meetyourfriend.data.AppDatabase
import com.byteclub.meetyourfriend.data.ConnectDao
import com.byteclub.meetyourfriend.voice.FieldExtractor
import com.byteclub.meetyourfriend.voice.SmartFieldExtractor
import dagger.Binds
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "connects.db").build()

    @Provides
    fun provideConnectDao(database: AppDatabase): ConnectDao = database.connectDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {
    @Binds
    abstract fun bindFieldExtractor(implementation: SmartFieldExtractor): FieldExtractor
}
