# SQLCipher memakai JNI
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
# osmdroid
-dontwarn org.osmdroid.**
# Hilangkan log debug di rilis
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
