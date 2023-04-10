package com.github.madoxann.hw6.database

import androidx.room.*

@Dao
interface MessageDao {
    @Query("SELECT * FROM messagedb")
    fun getAll(): List<MessageDB>

    @Insert
    fun insert(lst: List<MessageDB>)
}

@Dao
interface RequestDao {
    @Query("SELECT MIN(id) as id, request, isImage FROM requestdb")
    fun getFirst(): RequestDB

    @Insert
    fun insert(msg: RequestDB)

    @Delete
    fun delete(msg: RequestDB)
}

@Database(entities = [MessageDB::class], version = 1)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}

@Database(entities = [RequestDB::class], version = 1)
abstract class SentDatabase : RoomDatabase() {
    abstract fun sentDao(): RequestDao
}
