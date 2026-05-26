# 保留高德定位
-keep class com.amap.api.location.** { *; }
-keep class com.amap.api.location.core.** { *; }

# 保留 MySQL 驱动
-keep class com.mysql.jdbc.** { *; }
-dontwarn com.mysql.jdbc.**

# 保留 DataStore
-keep class androidx.datastore.** { *; }

# 保留 Kotlin 协程
-keep class kotlinx.coroutines.** { *; }

# 保留你的数据模型
-keep class com.example.checkintest.data.** { *; }
-keep class com.example.checkintest.database.** { *; }

# 解决 R8 打包时的缺失类警告
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.ThreadInfo
-dontwarn java.lang.management.ThreadMXBean
-dontwarn java.rmi.server.UID
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.SupportedAnnotationTypes