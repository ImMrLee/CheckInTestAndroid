
-keep class com.amap.api.location.** { *; }
-keep class com.amap.api.location.core.** { *; }

-keep class com.mysql.jdbc.** { *; }
-dontwarn com.mysql.jdbc.**

-keep class androidx.datastore.** { *; }

-keep class kotlinx.coroutines.** { *; }

-keep class com.example.checkintest.data.** { *; }
-keep class com.example.checkintest.database.** { *; }

-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.ThreadInfo
-dontwarn java.lang.management.ThreadMXBean
-dontwarn java.rmi.server.UID
-dontwarn javax.management.MBeanServer
-dontwarn javax.management.ObjectInstance
-dontwarn javax.management.ObjectName
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.SupportedAnnotationTypes