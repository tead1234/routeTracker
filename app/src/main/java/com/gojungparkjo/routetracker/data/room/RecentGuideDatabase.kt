package com.gojungparkjo.routetracker.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [RecentGuideItem::class], version = 2)
abstract class RecentGuideDatabase : RoomDatabase() {
    abstract fun recentGuideItemDao(): RecentGuideItemDao
}