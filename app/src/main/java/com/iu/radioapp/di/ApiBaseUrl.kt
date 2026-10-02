package com.iu.radioapp.di

import javax.inject.Qualifier

/** The configured base URL (see BuildConfig.API_BASE_URL), for anything that needs it as a plain String. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl
