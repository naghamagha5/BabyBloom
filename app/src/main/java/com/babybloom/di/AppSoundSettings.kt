package com.babybloom.di

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSoundSettings @Inject constructor() {
    val soundEnabled = MutableStateFlow(true)
}