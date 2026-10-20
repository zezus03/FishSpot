# 1. ZABRANA: Ne diraj kod aplikacije. Modeli za Firestore i Gson (FishSpot, Korisnici,
# WeatherResponse, OsmResponse...) su rasporedjeni po feature paketima i citaju se refleksijom.
-keep class com.example.fishspot.core.** { *; }
-keep class com.example.fishspot.feature.** { *; }

# 2. UNIVERZALNI ŠTIT: Zaštiti SVE interfejse (da API komande poput getWeather ne puknu)
-keep interface com.example.fishspot.** { *; }

# 3. UNIVERZALNI ŠTIT: Zaštiti SVE Kotlin 'data' klase u aplikaciji (gde god da su)
-keepclasseswithmembers class * {
    *** component1();
}

# 4. Zaštita za Gson (da bi umeo da pročita JSON sa interneta)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 5. Zaštita za Google Login i Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
}

# 6. Zaštita za Retrofit (OSM API i Vremenska prognoza)
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# 7. Očuvanje strukture i putokaza (Ovo direktno sprečava ClassCastException)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# 8. Retrofit 2.9 + suspend funkcije: R8 inače briše generičke tipove (Continuation, Response, Call),
# pa poziv API-ja pukne sa "Class cannot be cast to ParameterizedType" (npr. vremenska prognoza)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes InnerClasses,EnclosingMethod
