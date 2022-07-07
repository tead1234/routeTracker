package com.gojungparkjo.routetracker.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [RecentGuideItem::class], version = 1)
abstract class RecentGuideDatabase : RoomDatabase() {
    abstract fun recentGuideItemDao(): RecentGuideItemDao
}