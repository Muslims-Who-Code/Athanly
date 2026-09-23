package com.example.athanly.connectivity.domain.repository

// Wraps ConnectivityManager behind a domain-friendly interface — callers never touch
// Android's NetworkCapabilities API directly.
interface ConnectivityProvider {

    // A synchronous snapshot, not a live Flow — checked at specific checkpoints (ViewModel
    // init, retry) rather than streamed, so a plain Boolean is all that's needed here.
    fun isOnline(): Boolean
}