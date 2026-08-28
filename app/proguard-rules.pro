# Kotlin Serialization
-keepattributes *Annotation*, Signature
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep Supabase Models
-keep class com.example.villasportmanager.data.model.** { *; }

# Ktor rules
-keep class io.ktor.** { *; }
-dontwarn java.lang.management.**
-dontwarn javax.management.**

# Supabase rules
-keep class io.github.jan.supabase.** { *; }
