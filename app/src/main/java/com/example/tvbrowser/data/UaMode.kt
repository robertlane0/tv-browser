package com.example.tvbrowser.data

import androidx.room.TypeConverter

enum class UaMode {
    DESKTOP,
    MOBILE,
    NATIVE_TV
}

class UaModeConverters {

    @TypeConverter
    fun fromUaMode(mode: UaMode): String = mode.name

    @TypeConverter
    fun toUaMode(value: String): UaMode = runCatching { UaMode.valueOf(value) }
        .getOrDefault(UaMode.DESKTOP)
}
