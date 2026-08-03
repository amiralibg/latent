package com.latent.camera.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.latent.camera.look.Recipe

/**
 * Local-only. There is no sync, no backup target and no remote copy of any of this
 * — see CLAUDE.md invariant 4.
 */
@Database(
    entities = [CaptureRecord::class, Recipe::class],
    version = 3,
    exportSchema = true,
)
abstract class LatentDatabase : RoomDatabase() {

    abstract fun captures(): CaptureDao

    abstract fun recipes(): RecipeDao

    companion object {
        @Volatile
        private var instance: LatentDatabase? = null

        /**
         * Phase 2 adds recipes alongside the existing captures table. A destructive
         * fallback here would take the capture provenance with it, and provenance is
         * the one thing in this database that cannot be regenerated.
         */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipes` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `lift` REAL NOT NULL,
                        `gamma` REAL NOT NULL,
                        `gain` REAL NOT NULL,
                        `contrast` REAL NOT NULL,
                        `clarity` REAL NOT NULL,
                        `clarityRadius` REAL NOT NULL,
                        `halation` REAL NOT NULL,
                        `halationThreshold` REAL NOT NULL,
                        `halationRadius` REAL NOT NULL,
                        `grain` REAL NOT NULL,
                        `grainSize` REAL NOT NULL,
                        `toning` REAL NOT NULL,
                        `vignette` REAL NOT NULL,
                        `mix_r` REAL NOT NULL,
                        `mix_g` REAL NOT NULL,
                        `mix_b` REAL NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Phase 5 records the DNG alongside the pair, and marks a row as the product
         * of a re-grade. Both are nullable additions to an existing table, so this is
         * two ALTERs rather than a rebuild — the capture provenance in this table is
         * the one thing here that cannot be regenerated.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `captures` ADD COLUMN `dng_uri` TEXT")
                db.execSQL("ALTER TABLE `captures` ADD COLUMN `regraded_from` TEXT")
            }
        }

        /** What ships. MigrationTest runs this exact list, not a copy of it. */
        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun get(context: Context): LatentDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        LatentDatabase::class.java,
                        "latent.db",
                    )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
