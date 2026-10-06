# OkHttp 5 ships consumer rules; add app-specific keeps here.
-keep class com.Lia.assistant.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
