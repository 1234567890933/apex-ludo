# Apex Ludo 1.1 - conservative keep rules (app is reflection-free, but keep safe)
-keep class com.apexludo.** { *; }
-keepclassmembers class com.apexludo.** { *; }
-keepattributes *Annotation*
-dontwarn kotlin.**
-dontoptimize
