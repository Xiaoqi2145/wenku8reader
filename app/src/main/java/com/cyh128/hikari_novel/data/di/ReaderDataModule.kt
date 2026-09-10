package com.cyh128.hikari_novel.data.di

import com.cyh128.hikari_novel.data.repository.reader.ChapterCache
import com.cyh128.hikari_novel.data.repository.reader.ChapterLoader
import com.cyh128.hikari_novel.data.repository.reader.ChapterPrefetcher
import com.cyh128.hikari_novel.data.repository.reader.ChapterRemoteSource
import com.cyh128.hikari_novel.data.repository.reader.DefaultChapterLoader
import com.cyh128.hikari_novel.data.repository.reader.DefaultChapterPrefetcher
import com.cyh128.hikari_novel.data.repository.reader.RoomChapterCache
import com.cyh128.hikari_novel.data.repository.reader.RoomReaderProgressStore
import com.cyh128.hikari_novel.data.repository.reader.ReaderProgressStore
import com.cyh128.hikari_novel.data.repository.reader.ReaderRepositorySessionDataSource
import com.cyh128.hikari_novel.data.repository.reader.Wenku8ChapterRemoteSource
import com.cyh128.hikari_novel.reader.domain.ReaderSessionDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderDataModule {
    @Binds
    @Singleton
    abstract fun bindChapterCache(implementation: RoomChapterCache): ChapterCache

    @Binds
    @Singleton
    abstract fun bindChapterRemoteSource(implementation: Wenku8ChapterRemoteSource): ChapterRemoteSource

    @Binds
    @Singleton
    abstract fun bindChapterLoader(implementation: DefaultChapterLoader): ChapterLoader

    @Binds
    @Singleton
    abstract fun bindChapterPrefetcher(implementation: DefaultChapterPrefetcher): ChapterPrefetcher

    @Binds
    @Singleton
    abstract fun bindReaderProgressStore(implementation: RoomReaderProgressStore): ReaderProgressStore

    /** Unscoped on purpose: every reader ViewModel gets an isolated session. */
    @Binds
    abstract fun bindReaderSessionDataSource(
        implementation: ReaderRepositorySessionDataSource
    ): ReaderSessionDataSource
}
