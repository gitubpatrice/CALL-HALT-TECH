package com.filestech.callhalt.di

import javax.inject.Qualifier

/**
 * The DataStore that remembers the phone's own facts — its last call state, the last boot seen — apart from the
 * settings they have nothing to do with.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PhoneStateStore
