package com.filestech.callhalt.di

import javax.inject.Qualifier

/** A scope that lives as long as the process: for work that must not die with a screen (DataStore). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
