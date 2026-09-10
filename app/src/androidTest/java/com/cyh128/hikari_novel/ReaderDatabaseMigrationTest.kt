package com.cyh128.hikari_novel

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyh128.hikari_novel.data.source.local.database.ReaderDatabaseMigrations
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderDatabase
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryDatabase
import com.cyh128.hikari_novel.data.source.local.database.read_history.vertical_read_history.VerticalReadHistoryDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Room schema checks for the forward, non-destructive phase-six migrations. */
@RunWith(AndroidJUnit4::class)
class ReaderDatabaseMigrationTest {
    @get:Rule
    val readerHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReaderDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @get:Rule
    val horizontalHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HorizontalReadHistoryDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @get:Rule
    val verticalHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VerticalReadHistoryDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun readerMigrationPreservesRowsAndAddsCompositeKeys() {
        readerHelper.createDatabase(READER_DB, 1).apply {
            execSQL("INSERT INTO chapter_cache VALUES ('c1','book-a',0,0,'One','text','[]',1,0,4)")
            execSQL("INSERT INTO reader_progress VALUES ('c1','book-a',0,0,0,2,20,NULL,2,1)")
            execSQL("INSERT INTO download_task VALUES ('c1','book-a',0,0,'One','QUEUED',0,NULL,3)")
            close()
        }

        val database = readerHelper.runMigrationsAndValidate(
            READER_DB,
            3,
            true,
            ReaderDatabaseMigrations.READER_1_2,
            ReaderDatabaseMigrations.READER_2_3
        )
        assertEquals(1, count(database, "chapter_cache", "aid = 'book-a' AND cid = 'c1'"))
        assertEquals(1, count(database, "reader_progress", "aid = 'book-a' AND cid = 'c1'"))
        assertEquals(1, count(database, "download_task", "aid = 'book-a' AND cid = 'c1'"))
        assertTrue(primaryKey(database, "reader_progress").containsAll(listOf("aid", "cid")))
        database.close()
    }

    @Test
    fun legacyHistoryMigrationPreservesRows() {
        horizontalHelper.createDatabase(HORIZONTAL_DB, 1).apply {
            execSQL("INSERT INTO horizontal_read_history VALUES ('c1','book-a',0,0,12,25,1)")
            close()
        }
        verticalHelper.createDatabase(VERTICAL_DB, 1).apply {
            execSQL("INSERT INTO vertical_read_history VALUES ('c1','book-a',0,0,34,45,1)")
            close()
        }

        val horizontal = horizontalHelper.runMigrationsAndValidate(
            HORIZONTAL_DB,
            2,
            true,
            ReaderDatabaseMigrations.HORIZONTAL_HISTORY_1_2
        )
        val vertical = verticalHelper.runMigrationsAndValidate(
            VERTICAL_DB,
            2,
            true,
            ReaderDatabaseMigrations.VERTICAL_HISTORY_1_2
        )
        assertEquals(1, count(horizontal, "horizontal_read_history", "aid = 'book-a' AND cid = 'c1'"))
        assertEquals(1, count(vertical, "vertical_read_history", "aid = 'book-a' AND cid = 'c1'"))
        horizontal.close()
        vertical.close()
    }

    private fun count(database: SupportSQLiteDatabase, table: String, where: String): Int =
        database.query("SELECT COUNT(*) FROM $table WHERE $where").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun primaryKey(database: SupportSQLiteDatabase, table: String): Set<String> {
        val result = linkedSetOf<String>()
        database.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val keyIndex = cursor.getColumnIndex("pk")
            while (cursor.moveToNext()) {
                if (cursor.getInt(keyIndex) > 0) result += cursor.getString(nameIndex)
            }
        }
        return result
    }

    private companion object {
        const val READER_DB = "migration_reader_data"
        const val HORIZONTAL_DB = "migration_horizontal_history"
        const val VERTICAL_DB = "migration_vertical_history"
    }
}
