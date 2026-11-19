package com.example.workhours.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE day = :day ORDER BY time ASC, id ASC")
    fun observeDay(day: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY day ASC, time ASC, id ASC")
    suspend fun getAll(): List<Entry>

    @Insert
    suspend fun insert(entry: Entry)

    @Query("DELETE FROM entries WHERE day = :day")
    suspend fun deleteDay(day: String)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}
