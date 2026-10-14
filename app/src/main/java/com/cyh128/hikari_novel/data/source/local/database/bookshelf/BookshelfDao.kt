package com.cyh128.hikari_novel.data.source.local.database.bookshelf

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookshelfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookshelfEntity: BookshelfEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<BookshelfEntity>)

    @Query("UPDATE bookshelf SET classid = (:newClassId) WHERE aid = (:aid)")
    suspend fun updateClassId(aid: String, newClassId: Int)

    @Query("DELETE FROM bookshelf WHERE aid = (:aid)")
    suspend fun delete(aid: String)

    @Query("DELETE FROM bookshelf")
    suspend fun deleteAll()

    /**
     * Swap the whole table for [list] in one transaction.
     *
     * A sync used to run `deleteAll()` followed by one insert per shelf page,
     * which left several separate commits. Room's invalidation tracker fires
     * after each of them, so observers saw the shelf empty itself and refill;
     * doing both halves in one transaction emits a single change and never
     * exposes the intermediate empty state.
     */
    @Transaction
    suspend fun replaceAll(list: List<BookshelfEntity>) {
        deleteAll()
        upsertAll(list)
    }

    @Query("SELECT * FROM bookshelf WHERE classid = (:classId)")
    fun getByClassId(classId: Int): Flow<List<BookshelfEntity>?>

    @Query("SELECT * FROM bookshelf")
    fun getAll(): Flow<List<BookshelfEntity>?>

    @Query("SELECT * FROM bookshelf WHERE aid = (:aid)")
    fun getByAid(aid: String): BookshelfEntity?
}
