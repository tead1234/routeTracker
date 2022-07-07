package com.gojungparkjo.routetracker.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentGuideItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(recentGuideItem: RecentGuideItem)

    @Query("DELETE FROM RecentGuideItem WHERE uid not in (SELECT uid FROM RecentGuideItem LIMIT 10)")
    suspend fun deleteIfMoreThanTen()

    @Query("SELECT * FROM RecentGuideItem ORDER BY uid DESC LIMIT 10")
    suspend fun getTenRecentGuideItem():List<RecentGuideItem>
}