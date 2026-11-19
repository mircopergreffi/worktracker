package com.example.workhours.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** id | day (yyyy-MM-dd) | in_out ("IN"/"OUT") | time (HH:mm:ss) | client_id | project_id */
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) override val id: Long = 0,
    val day: String,
    @ColumnInfo(name = "in_out") override val inOut: String,
    val time: String,
    @ColumnInfo(name = "client_id") val clientId: Long? = null,
    @ColumnInfo(name = "project_id") val projectId: Long? = null
) : Marked
