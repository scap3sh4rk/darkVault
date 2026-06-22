# Fix: MEDIUM-004 — ProGuard/R8 rules for darkVault release builds

# ── Crypto layer: must not be renamed/removed ──────────────────────────────────
-keep class com.darkvault.app.crypto.** { *; }
-keepclassmembers class com.darkvault.app.crypto.** { *; }

# ── DataStore preference keys ──────────────────────────────────────────────────
-keep class com.darkvault.app.data.** { *; }
-keepclassmembers class com.darkvault.app.data.** { *; }

# ── VaultKeyBundle (Gson serialization) ───────────────────────────────────────
-keep class com.darkvault.app.model.VaultKeyBundle { *; }
-keepclassmembers class com.darkvault.app.model.** { *; }

# ── Gson ──────────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── OkHttp ────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

# ── DataStore / Proto ──────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── Biometric ─────────────────────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── Fix: LOW-005 — Strip debug/verbose/info log calls in release builds ────────
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations
