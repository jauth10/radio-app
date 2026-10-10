package com.iu.radioapp.ui.common

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

// java.time is available from minSdk 26 without desugaring.
fun Instant.toClockText(zone: ZoneId = ZoneId.systemDefault()): String =
    clockFormat.format(toJavaInstant().atZone(zone))
