package com.timeflux.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.timeflux.db.TimeFluxDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = TimeFluxDatabase.Schema,
            context = context,
            name = "timeflux.db",
            factory = RequerySQLiteOpenHelperFactory(),
            callback = object : AndroidSqliteDriver.Callback(TimeFluxDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.enableWriteAheadLogging()          // journal_mode=WAL returns a row — can't use execSQL on Android 14+
                    db.setForeignKeyConstraintsEnabled(true)
                    db.execSQL("PRAGMA synchronous=NORMAL")
                    db.execSQL("PRAGMA cache_size=-8000")
                    db.execSQL("PRAGMA temp_store=MEMORY")
                }

                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    FTS_TRIGGER_SQL.forEach { db.execSQL(it) }
                }
            }
        )
    }
}
