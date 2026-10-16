package com.byteclub.meetyourfriend.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectDao {
    @Query("SELECT * FROM connects ORDER BY metAt DESC")
    fun observeAll(): Flow<List<Connect>>

    @Query(
        "SELECT * FROM connects WHERE name LIKE '%' || :query || '%' " +
            "OR organization LIKE '%' || :query || '%' ORDER BY metAt DESC",
    )
    fun search(query: String): Flow<List<Connect>>

    @Query("SELECT * FROM connects WHERE id = :id")
    suspend fun getById(id: Long): Connect?

    @Query("SELECT * FROM connects WHERE mobileE164 = :mobile LIMIT 1")
    suspend fun findByMobile(mobile: String): Connect?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connect: Connect): Long

    @Delete
    suspend fun delete(connect: Connect)

    @Query("DELETE FROM connects WHERE id = :id")
    suspend fun deleteById(id: Long)
}
