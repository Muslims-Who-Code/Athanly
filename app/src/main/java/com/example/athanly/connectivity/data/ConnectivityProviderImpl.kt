package com.example.athanly.connectivity.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.athanly.connectivity.domain.repository.ConnectivityProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ConnectivityProviderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ConnectivityProvider {

    // NET_CAPABILITY_VALIDATED is checked alongside NET_CAPABILITY_INTERNET so a captive-portal
    // Wi-Fi network (connected, but no real internet) doesn't get reported as online — the
    // Qibla bearing fetch needs an actual working connection, not just a declared-capable one.
    override fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}