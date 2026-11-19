package com.example.workhours.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single table: id | day (yyyy-MM-dd) | in_out ("IN"/"OUT") | time (HH:mm:ss) */
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    @ColumnInfo(name = "in_out") val inOut: String,
    val time: String
)
