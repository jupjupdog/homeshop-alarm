package com.homeshop.alarm.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.homeshop.alarm.model.ProductItem
import kotlinx.coroutines.flow.Flow

/** 방송-상품을 한 줄로 펼쳐 저장한다 (조회가 단순해지고 화면도 상품 단위로 그린다). */
@Entity(tableName = "schedule_product")
data class ScheduleProductEntity(
    @PrimaryKey val key: String,
    val channel: String,
    val broadcastTitle: String,
    val startMillis: Long,
    val endMillis: Long,
    val productId: String,
    val name: String,
    val price: Int?,
    val imageUrl: String?,
    val productUrl: String?,
    val category: String,
    val searchQuery: String,
) {
    fun toModel() = ProductItem(
        key, channel, broadcastTitle, startMillis, endMillis, productId, name, price,
        imageUrl, productUrl, category, searchQuery,
    )
}

@Dao
abstract class ScheduleDao {
    /** 아직 끝나지 않은 방송만, 시작 시각 순. */
    @Query("SELECT * FROM schedule_product WHERE endMillis > :now ORDER BY startMillis, channel")
    abstract fun observeUpcoming(now: Long): Flow<List<ScheduleProductEntity>>

    @Query("SELECT * FROM schedule_product WHERE endMillis > :now ORDER BY startMillis")
    abstract suspend fun upcoming(now: Long): List<ScheduleProductEntity>

    @Query("SELECT * FROM schedule_product WHERE `key` = :key")
    abstract fun observe(key: String): Flow<ScheduleProductEntity?>

    @Query("DELETE FROM schedule_product WHERE channel IN (:channels)")
    abstract suspend fun deleteChannels(channels: List<String>)

    @Query("DELETE FROM schedule_product WHERE endMillis < :before")
    abstract suspend fun deleteEndedBefore(before: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<ScheduleProductEntity>)

    /**
     * 새 편성표 반영. 끝까지 받아 온 채널은 통째로 바꾸고(편성 변경·삭제 반영),
     * 일부 날짜만 받은 채널은 받은 만큼만 덮어써서 기존 캐시를 지킨다. 지난 방송은 정리.
     */
    @Transaction
    open suspend fun merge(completeChannels: List<String>, items: List<ScheduleProductEntity>, endedBefore: Long) {
        if (completeChannels.isNotEmpty()) deleteChannels(completeChannels)
        insertAll(items)
        deleteEndedBefore(endedBefore)
    }
}

@Database(entities = [ScheduleProductEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "homeshop.db")
                .fallbackToDestructiveMigration()   // 편성표는 언제든 다시 받을 수 있는 캐시
                .build()
    }
}
