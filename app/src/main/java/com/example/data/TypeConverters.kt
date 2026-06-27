package com.example.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mealListType = Types.newParameterizedType(List::class.java, Meal::class.java)
    private val mealListAdapter = moshi.adapter<List<Meal>>(mealListType)

    @TypeConverter
    fun stringToMealList(value: String?): List<Meal>? {
        if (value == null) return null
        return try {
            mealListAdapter.fromJson(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun mealListToString(list: List<Meal>?): String? {
        if (list == null) return null
        return mealListAdapter.toJson(list)
    }

    @TypeConverter
    fun stringToDayType(value: String?): DayType? {
        return value?.let { DayType.valueOf(it) }
    }

    @TypeConverter
    fun dayTypeToString(dayType: DayType?): String? {
        return dayType?.name
    }

    @TypeConverter
    fun stringToSyncStatus(value: String?): SyncStatus? {
        return value?.let { SyncStatus.valueOf(it) }
    }

    @TypeConverter
    fun syncStatusToString(syncStatus: SyncStatus?): String? {
        return syncStatus?.name
    }
}
