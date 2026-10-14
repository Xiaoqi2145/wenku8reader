package com.cyh128.hikari_novel.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Marks the process-wide scope used for work that has to outlive a screen.
 *
 * The login screen finishes as soon as the session is established, which
 * cancels its `viewModelScope`. Anything that must still be running after that
 * point — the first bookshelf sync, for example — has to be launched here
 * instead.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    /**
     * One supervisor scope for the whole process.
     *
     * `SupervisorJob` keeps one failed child from cancelling the others, and the
     * scope is never cancelled: it lives exactly as long as the application
     * process does.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
