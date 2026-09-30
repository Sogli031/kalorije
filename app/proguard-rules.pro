# Gson - REQUIRED for TypeToken to work with R8
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep TypeToken anonymous classes in our code
-keep class com.kalorije.app.data.repository.FoodRepository$Companion$* { *; }

# Keep model classes for Gson serialization
-keep class com.kalorije.app.data.model.** { *; }
-keepclassmembers class com.kalorije.app.data.model.** { *; }

# Keep repository for TypeToken usage
-keep class com.kalorije.app.data.repository.FoodRepository { *; }
-keepclassmembers class com.kalorije.app.data.repository.FoodRepository { *; }

# Google Play Services Auth
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Google API Client
-keep class com.google.api.client.** { *; }
-keep class * extends com.google.api.client.json.GenericJson { *; }

# Google HTTP Client
-keep class com.google.http.** { *; }
-dontwarn com.google.http.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Drive
-keep class com.google.api.** { *; }
-dontwarn com.google.api.**

# Compose - sprecava probleme sa R8
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Apache HTTP Client (transitive dependency from Google API)
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
