package org.phenoapps.cotton.database

import android.content.Context
import androidx.room.*
import org.phenoapps.cotton.database.dao.SampleDao
import org.phenoapps.cotton.database.entities.SampleEntity

@Database(entities = [SampleEntity::class],
    version = 2, exportSchema = true, autoMigrations = [AutoMigration(1, 2)]
)
abstract class CottonDatabase : RoomDatabase() {

    abstract fun sampleDao(): SampleDao

    companion object {

        private const val DATABASE_NAME = "cotton.db"

        //singleton pattern
        @Volatile private var instance: CottonDatabase? = null

        fun getInstance(ctx: Context): CottonDatabase {

            return instance ?: synchronized(this) {

                instance ?: buildDatabase(ctx).also { instance = it }
            }
        }

        private fun buildDatabase(ctx: Context): CottonDatabase {

            return Room.databaseBuilder(ctx, CottonDatabase::class.java, DATABASE_NAME)
                .build()

        }
    }
}