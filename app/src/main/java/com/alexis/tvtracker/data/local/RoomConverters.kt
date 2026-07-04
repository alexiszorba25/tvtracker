package com.alexis.tvtracker.data.local

import androidx.room.TypeConverter
import com.alexis.tvtracker.model.MediaType

class RoomConverters {
    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)
}
