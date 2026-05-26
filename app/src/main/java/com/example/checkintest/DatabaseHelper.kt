package com.example.checkintest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

class DatabaseHelper {

    companion object {
        private const val HOST = "124.223.118.98"  // 目前是本地测试
        private const val PORT = "3306"
        private const val DATABASE = "checkin_db"
        private const val USER = "app_user"
        private const val XOR_KEY = 0x55
        private val ENCRYPTED_PASSWORD = byteArrayOf(101)

        private val PASSWORD: String by lazy {
            val decryptedBytes = ENCRYPTED_PASSWORD.map { (it.toInt() xor XOR_KEY).toByte() }.toByteArray()
            String(decryptedBytes, Charsets.UTF_8)
        }

        private const val URL = "jdbc:mysql://$HOST:$PORT/$DATABASE?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8"
    }

    suspend fun saveCheckinRecordSuspend(
        userName: String,
        phoneNumber: String,
        age: String,
        gender: String,
        checkinTime: String,
        latitude: Double,
        longitude: Double,
        city: String,
        address: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null

        try {
            Class.forName("com.mysql.jdbc.Driver")
            connection = DriverManager.getConnection(URL, USER, PASSWORD)

            val sql = """
            INSERT INTO checkin_records 
            (user_name, phone_number, age, gender, checkin_time, latitude, longitude, city, address)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

            preparedStatement = connection?.prepareStatement(sql)
            preparedStatement?.setString(1, userName)
            preparedStatement?.setString(2, phoneNumber)
            preparedStatement?.setInt(3, age.toInt())
            preparedStatement?.setString(4, gender)
            preparedStatement?.setString(5, checkinTime)
            preparedStatement?.setDouble(6, latitude)
            preparedStatement?.setDouble(7, longitude)
            preparedStatement?.setString(8, city)
            preparedStatement?.setString(9, address)

            val rowsAffected = preparedStatement?.executeUpdate() ?: 0

            if (rowsAffected > 0) {
                Pair(true, "打卡记录已保存到服务器")
            } else {
                Pair(false, "保存失败，未影响任何记录")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "数据库错误: ${e.message}")
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }
}