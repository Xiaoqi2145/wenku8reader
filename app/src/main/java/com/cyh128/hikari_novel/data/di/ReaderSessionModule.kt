package com.cyh128.hikari_novel.data.di

import com.cyh128.hikari_novel.reader.domain.ReaderSessionCoordinator
import com.cyh128.hikari_novel.reader.domain.ReaderSessionDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Provides an isolated coordinator for each ViewModel injection site. */
@Module
@InstallIn(SingletonComponent::class)
object ReaderSessionModule {
    @Provides
    fun provideReaderSessionCoordinator(
        dataSource: ReaderSessionDataSource
    ): ReaderSessionCoordinator = ReaderSessionCoordinator(dataSource)
}
