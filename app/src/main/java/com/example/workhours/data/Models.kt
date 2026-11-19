package com.example.workhours.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int // ARGB
)

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id") val clientId: Long,
    val name: String
)

/** An entry joined with the current client / project names. */
data class EntryView(
    val id: Long,
    val day: String,
    @ColumnInfo(name = "in_out") val inOut: String,
    val time: String,
    @ColumnInfo(name = "client_id") val clientId: Long?,
    @ColumnInfo(name = "project_id") val projectId: Long?,
    @ColumnInfo(name = "client_name") val clientName: String?,
    @ColumnInfo(name = "client_color") val clientColor: Int?,
    @ColumnInfo(name = "project_name") val projectName: String?
)
