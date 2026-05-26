package com.example.checkintest

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*

class OfflineDataManager(private val context: Context) {

    private val dataFile = File(context.filesDir, "offline_checkins.json")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun saveOfflineRecord(record: OfflineCheckinRecord) {
        withContext(Dispatchers.IO) {
            val records = getOfflineRecords().toMutableList()
            records.add(record)
            saveRecordsToFile(records)
        }
    }
    suspend fun getOfflineRecords(): List<OfflineCheckinRecord> {
        return withContext(Dispatchers.IO) {
            if (!dataFile.exists()) return@withContext emptyList()
            try {
                val json = dataFile.readText()
                parseRecordsFromJson(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    suspend fun getOfflineCount(): Int {
        return getOfflineRecords().size
    }
    suspend fun removeSyncedRecords(ids: List<String>) {
        withContext(Dispatchers.IO) {
            val records = getOfflineRecords().toMutableList()
            records.removeAll { it.id in ids }
            saveRecordsToFile(records)
        }
    }
    suspend fun syncToServer(
        uploadFunction: suspend (OfflineCheckinRecord) -> Pair<Boolean, String>
    ): SyncResult {
        val records = getOfflineRecords()
        if (records.isEmpty()) return SyncResult(0, 0)

        var successCount = 0
        var failCount = 0
        val syncedIds = mutableListOf<String>()

        for (record in records) {
            val result = uploadFunction(record)
            if (result.first) {
                successCount++
                syncedIds.add(record.id)
            } else {
                failCount++
            }
        }

        removeSyncedRecords(syncedIds)
        return SyncResult(successCount, failCount)
    }

    private fun saveRecordsToFile(records: List<OfflineCheckinRecord>) {
        val jsonArray = JSONArray()
        records.forEach { record ->
            jsonArray.put(record.toJson())
        }
        dataFile.writeText(jsonArray.toString())
    }

    private fun parseRecordsFromJson(json: String): List<OfflineCheckinRecord> {
        val records = mutableListOf<OfflineCheckinRecord>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                records.add(OfflineCheckinRecord.fromJson(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return records
    }
}

data class OfflineCheckinRecord(
    val id: String = UUID.randomUUID().toString(),
    val userName: String,
    val phoneNumber: String,
    val age: String,
    val gender: String,
    val checkinTime: String,
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val address: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("user_name", userName)
            put("phone_number", phoneNumber)
            put("age", age)
            put("gender", gender)
            put("checkin_time", checkinTime)
            put("latitude", latitude)
            put("longitude", longitude)
            put("city", city)
            put("address", address)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): OfflineCheckinRecord {
            return OfflineCheckinRecord(
                id = obj.getString("id"),
                userName = obj.getString("user_name"),
                phoneNumber = obj.getString("phone_number"),
                age = obj.getString("age"),
                gender = obj.getString("gender"),
                checkinTime = obj.getString("checkin_time"),
                latitude = obj.getDouble("latitude"),
                longitude = obj.getDouble("longitude"),
                city = obj.getString("city"),
                address = obj.getString("address"),
                timestamp = obj.getLong("timestamp")
            )
        }
    }
}
data class SyncResult(val successCount: Int, val failCount: Int)