# JSch instantiates its algorithm implementations by class name (see JSch.setConfig).
-keep class com.jcraft.jsch.** { *; }
# Optional JSch integrations that are not present on Android.
-dontwarn com.jcraft.jsch.**
-dontwarn org.newsclub.net.unix.**
-dontwarn com.sun.jna.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn javax.naming.**
# Bouncy Castle is used through the lightweight API from the JSch classes above.
-dontwarn org.bouncycastle.**
-keepattributes Signature,InnerClasses,EnclosingMethod
