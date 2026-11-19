package com.example.workhours.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

internal const val VIEW_SELECT = """
SELECT e.id AS id, e.day AS day, e.in_out AS in_out, e.time AS time,
       e.client_id AS client_id, e.project_id AS project_id,
       c.name AS client_name, c.color AS client_color, p.name AS project_name
FROM entries e
LEFT JOIN clients c ON c.id = e.client_id
LEFT JOIN projects p ON p.id = e.project_id
"""

@Dao
interface EntryDao {
    // ---- entries ----
    @Query(VIEW_SELECT + " WHERE e.day = :day ORDER BY e.time ASC, e.id ASC")
    fun observeDay(day: String): Flow<List<EntryView>>

    @Query(VIEW_SELECT + " WHERE e.day >= :since ORDER BY e.day ASC, e.time ASC, e.id ASC")
    fun observeSince(since: String): Flow<List<EntryView>>

    @Query(VIEW_SELECT + " ORDER BY e.day ASC, e.time ASC, e.id ASC")
    suspend fun getAllViews(): List<EntryView>

    @Query("SELECT * FROM entries ORDER BY day DESC, time DESC, id DESC LIMIT 1")
    suspend fun lastEntry(): Entry?

    @Insert
    suspend fun insert(entry: Entry)

    @Query("DELETE FROM entries WHERE day = :day")
    suspend fun deleteDay(day: String)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    // ---- clients ----
    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE")
    fun observeClients(): Flow<List<Client>>

    @Insert
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClient(id: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE client_id = :id")
    suspend fun entriesForClient(id: Long): Int

    // ---- projects ----
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    fun observeProjects(): Flow<List<Project>>

    @Insert
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Long)

    @Query("DELETE FROM projects WHERE client_id = :clientId")
    suspend fun deleteProjectsOfClient(clientId: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE project_id = :id")
    suspend fun entriesForProject(id: Long): Int
}
