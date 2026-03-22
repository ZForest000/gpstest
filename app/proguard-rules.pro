# Keep GNSS APIs
-keep class android.location.GnssMeasurement { *; }
-keep class android.location.GnssMeasurementsEvent { *; }

# Keep GNSS callbacks and listeners
-keep class android.location.GnssMeasurement$* { *; }
-keep class android.location.GnssMeasurementsEvent$* { *; }
-keep class android.location.GnssStatus { *; }
-keep class android.location.GnssStatus$* { *; }
-keep class android.location.GnssClock { *; }
-keep class android.location.GnssNavigationMessage { *; }
-keep class android.location.GnssNavigationMessage$* { *; }

# Keep all GNSS-related interfaces and callbacks
-keep interface android.location.GnssMeasurement$* { *; }
-keep interface android.location.GnssMeasurementsEvent$* { *; }
-keep interface android.location.GnssStatus$* { *; }
-keep interface android.location.GnssNavigationMessage$* { *; }

# Keep LocationManager and related GNSS methods
-keep class android.location.LocationManager {
    public *** registerGnssMeasurementsCallback(...);
    public *** unregisterGnssMeasurementsCallback(...);
    public *** registerGnssStatusCallback(...);
    public *** unregisterGnssStatusCallback(...);
    public *** registerGnssNavigationMessageCallback(...);
    public *** unregisterGnssNavigationMessageCallback(...);
    public *** addGnssMeasurementsListener(...);
    public *** removeGnssMeasurementsListener(...);
    public *** addGnssStatusListener(...);
    public *** removeGnssStatusListener(...);
    public *** addNmeaListener(...);
    public *** removeNmeaListener(...);
}

# Keep GnssMeasurementRequest and related builders
-keep class android.location.GnssMeasurementRequest { *; }
-keep class android.location.GnssMeasurementRequest$Builder { *; }

# Preserve reflection access for GNSS classes
-keepclassmembers class * {
    @android.annotation.SuppressLint *;
}

# Keep all enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
