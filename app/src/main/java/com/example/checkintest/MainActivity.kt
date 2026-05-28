package com.example.checkintest

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.example.checkintest.ui.theme.CheckInTestTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "checkin_prefs")

class CheckInRepository(private val context: Context) {
    companion object {
        val LAST_CHECK_IN_DATE = stringPreferencesKey("last_check_in_date")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val USER_AGE = stringPreferencesKey("user_age")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val SAVED_CITY = stringPreferencesKey("saved_city")
        val SAVED_DISTRICT = stringPreferencesKey("saved_district")
        val SAVED_PROVINCE = stringPreferencesKey("saved_province")
    }
    suspend fun saveCheckIn(date: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CHECK_IN_DATE] = date
        }
    }

    fun getLastCheckInDate(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[LAST_CHECK_IN_DATE]
        }
    }
    fun isFirstLaunch(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[IS_FIRST_LAUNCH] ?: true
        }
    }

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { preferences ->
            preferences[IS_FIRST_LAUNCH] = false
        }
    }

    suspend fun saveUserInfo(name: String, phone: String, age: String, gender: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
            preferences[USER_PHONE] = phone
            preferences[USER_AGE] = age
            preferences[USER_GENDER] = gender
        }
    }

    fun getUserInfo(): Flow<AppUserInfo> {
        return context.dataStore.data.map { preferences ->
            AppUserInfo(
                name = preferences[USER_NAME] ?: "",
                phone = preferences[USER_PHONE] ?: "",
                age = preferences[USER_AGE] ?: "",
                gender = preferences[USER_GENDER] ?: ""
            )
        }
    }

    suspend fun saveCurrentCity(city: String, district: String, province: String) {
        context.dataStore.edit { preferences ->
            if (city.isNotEmpty()) preferences[SAVED_CITY] = city
            if (district.isNotEmpty()) preferences[SAVED_DISTRICT] = district
            if (province.isNotEmpty()) preferences[SAVED_PROVINCE] = province
        }
    }

}
data class AppUserInfo(
    val name: String,
    val phone: String,
    val age: String,
    val gender: String
)
class CheckInViewModel(private val repository: CheckInRepository) : ViewModel() {
    private val _isCheckedInToday = MutableLiveData<Boolean>()
    val isCheckedInToday: LiveData<Boolean> = _isCheckedInToday
    private val _lastCheckInDate = MutableLiveData<String?>()
    val lastCheckInDate: LiveData<String?> = _lastCheckInDate
    private val _locationText = MutableLiveData<String?>()
    val locationText: LiveData<String?> = _locationText
    private var locationClient: AMapLocationClient? = null
    private var currentLocation: AMapLocation? = null
    private val _userInfo = MutableLiveData<AppUserInfo?>()
    val userInfo: LiveData<AppUserInfo?> = _userInfo
    private val dbHelper = DatabaseHelper()
    private val _showOfflineDialog = MutableLiveData(false)
    val showOfflineDialog: LiveData<Boolean> = _showOfflineDialog

    private var pendingLocation: AMapLocation? = null
    private var pendingOnResult: ((Boolean, String) -> Unit)? = null
    private fun performCheckin(location: AMapLocation, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val today = getTodayDate()
                repository.saveCheckIn(today)
                _lastCheckInDate.value = today
                _isCheckedInToday.value = true

                val userInfo = _userInfo.value
                val userName = userInfo?.name ?: "用户"
                val phoneNumber = userInfo?.phone ?: ""
                val age = userInfo?.age ?: ""
                val gender = userInfo?.gender ?: ""
                val checkinTime = getCurrentDateTime()

                val result = dbHelper.saveCheckinRecordSuspend(
                    userName = userName,
                    phoneNumber = phoneNumber,
                    age = age,
                    gender = gender,
                    checkinTime = checkinTime,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    city = location.city ?: "",
                    address = location.address ?: ""
                )
                syncOfflineData()
                updateOfflineCount()

                if (result.first) {
                    onResult(true, "打卡成功！")
                } else {
                    saveToOffline(location)
                    onResult(true, "打卡成功，但数据本地保存，联网后自动同步")
                }

            } catch (e: Exception) {
                Log.e("OfflineCheck", "打卡异常: ${e.message}")
                onResult(false, "打卡失败: ${e.message}")
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.getLastCheckInDate().collect { date ->
                _lastCheckInDate.value = date
                _isCheckedInToday.value = (date == getTodayDate())
            }
        }

        viewModelScope.launch {
            repository.getUserInfo().collect { info ->
                _userInfo.value = info
            }
        }
    }

    fun initLocationClient(context: Context) {
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)

        locationClient = AMapLocationClient(context)

        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isNeedAddress = true
            isMockEnable = false
            interval = 2000
            isOnceLocation = false
        }
        locationClient?.setLocationOption(option)
    }

    fun startLocationUpdates() {
        if (locationClient == null) {
            updateLocationText("定位未初始化")
            return
        }

        updateLocationText("正在获取位置...")

        locationClient?.setLocationListener { location ->
            onLocationChanged(location)
        }

        locationClient?.startLocation()

        viewModelScope.launch {
            delay(10000)
            if (_locationText.value == "正在获取位置...") {
                updateLocationText("定位超时，请检查 GPS 和网络")
            }
        }
    }

    fun stopLocationUpdates() {
        locationClient?.stopLocation()
    }

    fun destroyLocationClient() {
        locationClient?.onDestroy()
        locationClient = null
    }

    fun getCurrentLocation(onResult: (AMapLocation?) -> Unit) {
        if (currentLocation != null) {
            onResult(currentLocation)
            return
        }

        startLocationUpdates()

        viewModelScope.launch {
            delay(5000)
            onResult(currentLocation)
        }
    }

    fun updateLocationText(text: String) {
        _locationText.value = text
    }

    private fun onLocationChanged(location: AMapLocation?) {
        if (location != null && location.errorCode == 0) {
            currentLocation = location

            val lat = location.latitude
            val lng = location.longitude
            val accuracy = location.accuracy

            val city = location.city ?: ""
            val district = location.district ?: ""
            val province = location.province ?: ""
            val address = location.address ?: ""

            val provider = when (location.provider) {
                "gps" -> "GPS卫星"
                "network" -> "网络定位"
                else -> "高德定位"
            }

            val displayText = buildString {
                if (address.isNotEmpty()) {
                    append("$address\n\n")
                } else {
                    if (city.isNotEmpty()) {
                        append(city)
                        if (district.isNotEmpty()) append(" $district")
                        append("\n\n")
                    }
                }
                append("经纬度：${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}\n")
                append("精度：±${String.format("%.1f", accuracy)}米\n")
                append("来源：$provider")
            }

            updateLocationText(displayText)

            if (city.isNotEmpty()) {
                viewModelScope.launch {
                    repository.saveCurrentCity(city, district, province)
                }
            }

            stopLocationUpdates()

        } else if (location != null) {
            val errorInfo = location.errorInfo
            updateLocationText("定位失败: $errorInfo\n错误码: ${location.errorCode}")
        }
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private lateinit var offlineDataManager: OfflineDataManager
    private val _offlineCount = MutableLiveData(0)
    val offlineCount: LiveData<Int> = _offlineCount

    fun initOfflineManager(context: Context) {
        offlineDataManager = OfflineDataManager(context)
        // 启动时自动同步
        viewModelScope.launch {
            delay(3000)
            syncOfflineData()
            updateOfflineCount()
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            return info != null && info.isConnected
        }
    }

    private fun getCurrentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun createOfflineRecord(location: AMapLocation): OfflineCheckinRecord {
        val userInfo = _userInfo.value
        return OfflineCheckinRecord(
            userName = userInfo?.name ?: "用户",
            phoneNumber = userInfo?.phone ?: "",
            age = userInfo?.age ?: "",
            gender = userInfo?.gender ?: "",
            checkinTime = getCurrentDateTime(),
            latitude = location.latitude,
            longitude = location.longitude,
            city = location.city ?: "",
            address = location.address ?: ""
        )
    }

    private suspend fun saveToOffline(location: AMapLocation) {
        val record = createOfflineRecord(location)
        offlineDataManager.saveOfflineRecord(record)
        updateOfflineCount()
    }

    suspend fun syncOfflineData(): SyncResult {
        if (!::offlineDataManager.isInitialized) return SyncResult(0, 0)
        return offlineDataManager.syncToServer { record ->
            dbHelper.saveCheckinRecordSuspend(
                userName = record.userName,
                phoneNumber = record.phoneNumber,
                age = record.age,
                gender = record.gender,
                checkinTime = record.checkinTime,
                latitude = record.latitude,
                longitude = record.longitude,
                city = record.city,
                address = record.address
            )
        }
    }

    fun updateOfflineCount() {
        viewModelScope.launch {
            val count = if (::offlineDataManager.isInitialized) {
                offlineDataManager.getOfflineCount()
            } else 0
            _offlineCount.value = count
        }
    }


    fun checkInWithOfflineSupport(
        context: Context,
        location: AMapLocation?,
        onResult: (Boolean, String) -> Unit
    ) {
        if (_isCheckedInToday.value == true) {
            onResult(false, "今天已经打卡过了")
            return
        }

        val isNetAvailable = isNetworkAvailable(context)
        Log.d("OfflineCheck", "网络状态: $isNetAvailable")

        if (!isNetAvailable) {
            pendingLocation = location
            pendingOnResult = onResult
            _showOfflineDialog.value = true
            return
        }

        if (location == null) {
            startGPSAndCheckin(context, onResult)
            return
        }

        performCheckin(location, onResult)
    }
    private val _isGPSLocating = MutableLiveData(false)
    val isGPSLocating: LiveData<Boolean> = _isGPSLocating
    private fun startGPSAndCheckin(
        context: Context,
        onResult: (Boolean, String) -> Unit
    ) {
        _isGPSLocating.value = true

        startGPSLocationOnly(context) { gpsLocation ->
            _isGPSLocating.value = false

            if (gpsLocation != null) {
                performCheckinWithGPS(gpsLocation, onResult)
            } else {
                performCheckinWithoutLocation(onResult)
            }
        }
    }
    private fun performCheckinWithoutLocation(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val today = getTodayDate()
                repository.saveCheckIn(today)
                _lastCheckInDate.value = today
                _isCheckedInToday.value = true

                val userInfo = _userInfo.value
                val checkinTime = getCurrentDateTime()

                val record = OfflineCheckinRecord(
                    userName = userInfo?.name ?: "用户",
                    phoneNumber = userInfo?.phone ?: "",
                    age = userInfo?.age ?: "",
                    gender = userInfo?.gender ?: "",
                    checkinTime = checkinTime,
                    latitude = 0.0,
                    longitude = 0.0,
                    city = "GPS定位失败",
                    address = "无法获取位置信息"
                )
                offlineDataManager.saveOfflineRecord(record)
                updateOfflineCount()

                updateLocationText("GPS定位失败，打卡成功但无位置信息")

                onResult(true, "打卡成功！GPS定位失败，无位置信息")

            } catch (e: Exception) {
                Log.e("OfflineCheck", "无位置打卡失败: ${e.message}")
                onResult(false, "打卡失败: ${e.message}")
            }
        }
    }
    private fun startGPSLocationOnly(
        context: Context,
        callback: (AMapLocation?) -> Unit
    ) {
        val gpsClient = AMapLocationClient(context)
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Device_Sensors
            isNeedAddress = false
            isMockEnable = false
            interval = 2000
            isOnceLocation = true
        }
        gpsClient.setLocationOption(option)

        gpsClient.setLocationListener { location ->
            gpsClient.stopLocation()
            gpsClient.onDestroy()
            callback(location)
        }

        gpsClient.startLocation()

        viewModelScope.launch {
            delay(30000)
            gpsClient.stopLocation()
            gpsClient.onDestroy()
            callback(null)
        }
    }

    private fun performCheckinWithGPS(
        location: AMapLocation,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val today = getTodayDate()
                repository.saveCheckIn(today)
                _lastCheckInDate.value = today
                _isCheckedInToday.value = true

                val userInfo = _userInfo.value
                val checkinTime = getCurrentDateTime()

                val gpsCity = "GPS定位（无法获取城市信息）"
                val gpsAddress = "GPS定位: 纬度 ${
                    String.format(
                        "%.6f",
                        location.latitude
                    )
                }, 经度 ${String.format("%.6f", location.longitude)}"

                // 保存到离线队列
                val record = OfflineCheckinRecord(
                    userName = userInfo?.name ?: "用户",
                    phoneNumber = userInfo?.phone ?: "",
                    age = userInfo?.age ?: "",
                    gender = userInfo?.gender ?: "",
                    checkinTime = checkinTime,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    city = gpsCity,
                    address = gpsAddress
                )
                offlineDataManager.saveOfflineRecord(record)
                updateOfflineCount()

                updateLocationText(
                    "GPS定位打卡成功\n纬度: ${
                        String.format(
                            "%.6f",
                            location.latitude
                        )
                    }\n经度: ${String.format("%.6f", location.longitude)}\n城市: GPS定位"
                )

                onResult(true, "打卡成功！使用GPS定位，数据已本地保存，联网后自动同步")

            } catch (e: Exception) {
                Log.e("OfflineCheck", "GPS打卡失败: ${e.message}")
                onResult(false, "打卡失败: ${e.message}")
            }
        }
    }

    fun confirmOfflineCheckin(context: Context) {
        val location = pendingLocation
        val onResult = pendingOnResult

        if (onResult == null) return

        startGPSAndCheckin(context, onResult)

        pendingLocation = null
        pendingOnResult = null
        _showOfflineDialog.value = false
    }
    fun cancelOfflineCheckin() {
        pendingLocation = null
        pendingOnResult = null
        _showOfflineDialog.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterComplete: (name: String, phone: String, age: String, gender: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("男") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name) {
        if (nameError != null) {
            delay(500)
            nameError = null
        }
    }

    LaunchedEffect(phone) {
        if (phoneError != null) {
            delay(500)
            phoneError = null
        }
    }

    LaunchedEffect(age) {
        if (ageError != null) {
            delay(500)
            ageError = null
        }
    }

    val genderOptions = listOf("男", "女")

    fun validateName(): Boolean {
        val chineseRegex = Regex("^[\u4e00-\u9fa5]{2,4}$")  // 2-4个中文字符
        return if (name.isBlank()) {
            nameError = "请输入姓名"
            false
        } else if (!chineseRegex.matches(name)) {
            nameError = "姓名必须是2-4个中文字符"
            false
        } else {
            true
        }
    }

    fun validatePhone(): Boolean {
        val phoneRegex = Regex("^1[3-9]\\d{9}$")  // 1开头，第二位3-9，后面9位数字
        return if (phone.isBlank()) {
            phoneError = "请输入手机号"
            false
        } else if (!phoneRegex.matches(phone)) {
            phoneError = "请输入11位有效手机号"
            false
        } else {
            true
        }
    }

    fun validateAge(): Boolean {
        return if (age.isBlank()) {
            ageError = "请输入年龄"
            false
        } else {
            val ageInt = age.toIntOrNull()
            when (ageInt) {
                null -> {
                    ageError = "年龄必须是数字"
                    false
                }
                !in 1..120 -> {
                    ageError = "年龄必须在1-120岁之间"
                    false
                }
                else -> {
                    true
                }
            }
        }
    }

    fun validateAll(): Boolean {
        val isNameValid = validateName()
        val isPhoneValid = validatePhone()
        val isAgeValid = validateAge()
        return isNameValid && isPhoneValid && isAgeValid
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "欢迎使用打卡助手",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "请填写以下信息完成注册",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = { Text("姓名") },
                placeholder = { Text("请输入真实姓名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = nameError != null,
                supportingText = {
                    if (nameError != null) {
                        Text(
                            text = nameError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    val filtered = it.filter { char -> char.isDigit() }
                    if (filtered.length <= 11) {
                        phone = filtered
                        phoneError = null
                    }
                },
                label = { Text("手机号") },
                placeholder = { Text("请输入11位手机号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phoneError != null,
                supportingText = {
                    if (phoneError != null) {
                        Text(
                            text = phoneError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "${phone.length}/11",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = age,
                onValueChange = {
                    val filtered = it.filter { char -> char.isDigit() }
                    age = filtered
                    ageError = null
                },
                label = { Text("年龄") },
                placeholder = { Text("请输入年龄") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = ageError != null,
                supportingText = {
                    if (ageError != null) {
                        Text(
                            text = ageError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "性别",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                genderOptions.forEach { option ->
                    FilterChip(
                        selected = gender == option,
                        onClick = { gender = option },
                        label = { Text(option) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (validateAll()) {
                        onRegisterComplete(name, phone, age, gender)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("完成注册", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "注意事项：\n• 姓名必须是2-10个中文字符\n• 手机号必须是11位有效号码\n• 年龄必须在1-120岁之间",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun CheckInScreen(viewModel: CheckInViewModel, onNavigateToEmergencyContacts: () -> Unit) {
    val context = LocalContext.current
    val isCheckedInToday by viewModel.isCheckedInToday.observeAsState(false)
    val lastCheckInDate by viewModel.lastCheckInDate.observeAsState(null)
    val locationText by viewModel.locationText.observeAsState(null)
    val userInfo by viewModel.userInfo.observeAsState()
    val userName = userInfo?.name ?: "用户"
    val offlineCount by viewModel.offlineCount.observeAsState(0)
    val showOfflineDialog by viewModel.showOfflineDialog.observeAsState(false)
    val isGPSLocating by viewModel.isGPSLocating.observeAsState(false)
    fun getGreeting(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 0..4 -> "夜深了"
            in 5..8 -> "早上好"
            in 9..11 -> "上午好"
            in 12..13 -> "中午好"
            in 14..17 -> "下午好"
            in 18..20 -> "晚上好"
            in 21..23 -> "晚安"
            else -> "你好"
        }
    }
    if (showOfflineDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelOfflineCheckin() },
            title = { Text("断网打卡") },
            text = {
                Text(
                    "当前未连接网络，将使用GPS定位进行打卡。\n\n" +
                            "注意：\n" +
                            "• GPS定位可能需要10-30秒，甚至更久\n" +
                            "• 请确保在开阔地带\n" +
                            "• 无法获取城市信息，将标记为\"GPS定位\""
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmOfflineCheckin(context) }) {  // 传入 context
                    Text("开始打卡")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelOfflineCheckin() }) {
                    Text("取消")
                }
            }
        )
    }
    if (isGPSLocating) {
        AlertDialog(
            onDismissRequest = {},  // 不可取消
            title = { Text("GPS定位中") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在使用GPS定位，请稍候...",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "首次定位可能需要10-30秒",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCheckedInToday)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${getGreeting()}，${userName}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isCheckedInToday) "今日已打卡" else "请打卡",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCheckedInToday)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "上次打卡：${lastCheckInDate ?: "暂无打卡记录"}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (offlineCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF9800).copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "有 $offlineCount 条打卡数据待同步",
                            fontSize = 14.sp,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(context, "请授予定位权限", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!isNetworkAvailable(context)) {
                        viewModel.checkInWithOfflineSupport(
                            context = context,
                            location = null,
                            onResult = { _, message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "打卡中...", Toast.LENGTH_SHORT).show()
                        viewModel.getCurrentLocation { location ->
                            viewModel.checkInWithOfflineSupport(
                                context = context,
                                location = location,
                                onResult = { _, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                },
                enabled = !isCheckedInToday,
                modifier = Modifier.size(256.dp, 256.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCheckedInToday) Color.Green else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isCheckedInToday) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已打卡",
                        modifier = Modifier.size(60.dp),
                        tint = Color.White
                    )
                } else {
                    Text("打卡", fontSize = 64.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "当前位置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = locationText ?: "等待定位...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNavigateToEmergencyContacts,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("紧急呼叫", fontSize = 18.sp)
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        return capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected && networkInfo.isAvailable
    }
}
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CheckInViewModel
    private var isFirstLaunch = mutableStateOf(true)
    private lateinit var updateManager: UpdateManager
    private var downloadReceiver: android.content.BroadcastReceiver? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.initLocationClient(applicationContext)
            viewModel.startLocationUpdates()
            Toast.makeText(this, "定位权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.updateLocationText("定位权限被拒绝")
            Toast.makeText(this, "定位权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "通知权限已拒绝，将无法收到提醒", Toast.LENGTH_SHORT).show()
        }
    }

    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isNetworkAvailable(context)) {
                lifecycleScope.launch {
                    viewModel.syncOfflineData()
                    viewModel.updateOfflineCount()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            networkReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(networkReceiver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = CheckInRepository(applicationContext)
        viewModel = CheckInViewModel(repository)
        viewModel.initOfflineManager(applicationContext)
        viewModel.updateOfflineCount()

        // 检查是否首次启动
        lifecycleScope.launch {
            repository.isFirstLaunch().collect { firstLaunch ->
                isFirstLaunch.value = firstLaunch
            }
        }

        // 请求定位权限
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.initLocationClient(applicationContext)
                viewModel.startLocationUpdates()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // 请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        updateManager = UpdateManager(this)
        registerDownloadReceiver()

        lifecycleScope.launch {
            checkForAppUpdate()
        }

        setContent {
            CheckInTestTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = if (isFirstLaunch.value) "register" else "checkin"
                ) {
                    composable("register") {
                        RegisterScreen { name, phone, age, gender ->
                            lifecycleScope.launch {
                                repository.saveUserInfo(name, phone, age, gender)
                                repository.setFirstLaunchCompleted()
                                isFirstLaunch.value = false
                                navController.navigate("checkin") {
                                    popUpTo("register") { inclusive = true }
                                }
                                Toast.makeText(this@MainActivity, "注册成功！", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    composable("checkin") {
                        CheckInScreen(
                            viewModel = viewModel,
                            onNavigateToEmergencyContacts = {
                                navController.navigate("emergency_contacts")
                            }
                        )
                    }

                    composable("emergency_contacts") {
                        EmergencyContactsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (downloadId != -1L) {
                        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val uri = downloadManager.getUriForDownloadedFile(downloadId)
                        uri?.let {
                            showInstallDialog(it)
                        }
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun showInstallDialog(uri: Uri) {
        android.app.AlertDialog.Builder(this)
            .setTitle("下载完成")
            .setMessage("新版本已下载完成，是否立即安装？")
            .setPositiveButton("安装") { _, _ ->
                updateManager.installApk(uri)
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun checkForAppUpdate() {
        lifecycleScope.launch {
            updateManager.checkForUpdate(
                onNewVersion = { apkUrl, changelog, versionName ->
                    showUpdateDialog(apkUrl, changelog, versionName)
                },
                onError = { error ->
                    Log.d("UpdateCheck", "检查更新失败: $error")
                }
            )
        }
    }
    private fun showUpdateDialog(apkUrl: String, changelog: String, versionName: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("发现新版本 $versionName")
            .setMessage(changelog)
            .setPositiveButton("立即更新") { _, _ ->
                updateManager.downloadAndInstall(apkUrl)
                Toast.makeText(this, "开始下载新版本，完成后将提示安装", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let { unregisterReceiver(it) }
        viewModel.destroyLocationClient()
    }
}