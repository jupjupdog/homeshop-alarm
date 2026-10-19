package com.homeshop.alarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.homeshop.alarm.AppContainer
import com.homeshop.alarm.HomeshopApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as HomeshopApp).container
