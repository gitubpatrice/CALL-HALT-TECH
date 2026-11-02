package com.filestech.callhalt.di

import javax.inject.Qualifier

/** The DataStore that remembers the phone's last call state, apart from the settings it has nothing to do with. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PhoneStateStore
