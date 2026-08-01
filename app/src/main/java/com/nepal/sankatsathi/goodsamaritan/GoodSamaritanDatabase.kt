package com.nepal.sankatsathi.goodsamaritan

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Dao
interface GoodSamaritanDao {
    @Query("SELECT * FROM accident_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<AccidentReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: AccidentReport)

    @Query("SELECT SUM(CASE WHEN severity = 'MINOR_INJURY' THEN 50 ELSE 100 END) FROM accident_reports")
    fun getTotalPoints(): Flow<Int?>
}

class Converters {
    @TypeConverter
    fun fromSeverity(value: Severity) = value.name

    @TypeConverter
    fun toSeverity(value: String) = Severity.valueOf(value)

    @TypeConverter
    fun fromStatus(value: ReportStatus) = value.name

    @TypeConverter
    fun toStatus(value: String) = ReportStatus.valueOf(value)
}

@Database(entities = [AccidentReport::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GoodSamaritanDatabase : RoomDatabase() {
    abstract fun dao(): GoodSamaritanDao

    companion object {
        @Volatile
        private var INSTANCE: GoodSamaritanDatabase? = null

        fun getDatabase(context: Context): GoodSamaritanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GoodSamaritanDatabase::class.java,
                    "good_samaritan_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
