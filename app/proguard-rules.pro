# =============================================================================
# GNSS reflection / platform APIs
# =============================================================================
-keep class android.location.GnssMeasurement { *; }
-keep class android.location.GnssMeasurementsEvent { *; }
-keep class android.location.GnssMeasurement$* { *; }
-keep class android.location.GnssMeasurementsEvent$* { *; }
-keep class android.location.GnssStatus { *; }
-keep class android.location.GnssStatus$* { *; }
-keep class android.location.GnssClock { *; }
-keep class android.location.GnssNavigationMessage { *; }
-keep class android.location.GnssNavigationMessage$* { *; }
-keep class android.location.GnssAntennaInfo { *; }
-keep class android.location.GnssAntennaInfo$* { *; }

-keep interface android.location.GnssMeasurement$* { *; }
-keep interface android.location.GnssMeasurementsEvent$* { *; }
-keep interface android.location.GnssStatus$* { *; }
-keep interface android.location.GnssNavigationMessage$* { *; }

-keep class android.location.LocationManager {
    public *** registerGnssMeasurementsCallback(...);
    public *** unregisterGnssMeasurementsCallback(...);
    public *** registerGnssStatusCallback(...);
    public *** unregisterGnssStatusCallback(...);
    public *** registerGnssNavigationMessageCallback(...);
    public *** unregisterGnssNavigationMessageCallback(...);
    public *** registerAntennaInfoListener(...);
    public *** unregisterAntennaInfoListener(...);
    public *** getGnssAntennaInfos(...);
    public *** addGnssMeasurementsListener(...);
    public *** removeGnssMeasurementsListener(...);
    public *** addGnssStatusListener(...);
    public *** removeGnssStatusListener(...);
    public *** addNmeaListener(...);
    public *** removeNmeaListener(...);
    public *** sendExtraCommand(...);
    public *** getGnssCapabilities(...);
    public *** getGnssYearOfHardware(...);
    public *** getGnssHardwareModelName(...);
}

-keep class android.location.GnssMeasurementRequest { *; }
-keep class android.location.GnssMeasurementRequest$Builder { *; }
-keep class android.location.GnssCapabilities { *; }

-keepclassmembers class * {
    @android.annotation.SuppressLint *;
}

# =============================================================================
# Kotlin / general Android
# =============================================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exception
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# =============================================================================
# kotlinx.serialization
# =============================================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# App models annotated with @Serializable (+ generated serializers)
-keep,includedescriptorclasses class com.example.gpstest.**$$serializer { *; }
-keepclassmembers class com.example.gpstest.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.gpstest.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.example.gpstest.** { *; }

# =============================================================================
# OkHttp / Okio
# =============================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# =============================================================================
# WorkManager
# =============================================================================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keep class com.example.gpstest.service.AGpsUpdateWorker { *; }

# =============================================================================
# Shizuku + AIDL UserService
# =============================================================================
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# AIDL stubs / UserService implementations (binder reflection)
-keep class com.example.gpstest.data.source.IDumpsysService { *; }
-keep class com.example.gpstest.data.source.IDumpsysService$Stub { *; }
-keep class com.example.gpstest.data.source.IDumpsysService$Stub$Proxy { *; }
-keep class * implements com.example.gpstest.data.source.IDumpsysService { *; }
-keep class com.example.gpstest.data.source.DumpsysServiceImpl { *; }
-keep class com.example.gpstest.data.source.ShizukuHelper { *; }
