package com.example.athanly.connectivity.di

import com.example.athanly.connectivity.data.ConnectivityProviderImpl
import com.example.athanly.connectivity.domain.repository.ConnectivityProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// abstract class, not object — @Binds (interface-to-impl) requires an abstract function,
// which an object can't declare. No companion object needed here, unlike LocationModule —
// ConnectivityProviderImpl only needs @ApplicationContext, which Hilt already provides.
@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    @Binds
    @Singleton
    abstract fun bindConnectivityProvider(impl: ConnectivityProviderImpl): ConnectivityProvider
}