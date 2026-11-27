# Room instantiates generated database implementations through reflection.
-keep class * extends androidx.room3.RoomDatabase { <init>(); }

# SQLite's JNI library registers native methods, including unused ones.
-keep class androidx.sqlite.driver.bundled.** { *; }

# The native driver looks up this exception by name and constructs it with ThrowNew.
-keep class androidx.sqlite.SQLiteException { <init>(java.lang.String); }

# Ktor's atomic field updaters look up volatile fields by name.
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# The JVM looks up enum values by reflection when reading saved recurrence frequencies.
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
