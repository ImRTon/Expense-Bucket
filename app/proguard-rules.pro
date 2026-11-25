# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations

# Google ML Kit & Firebase Component discovery (prevents R8 from stripping DI fields like zzo.zza)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep @com.google.android.gms.common.annotation.KeepName class *
-keepclasseswithmembers class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-keep @com.google.android.gms.common.annotation.KeepForSdk class * { *; }
-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Cloud backups use stable Gson field names across debug and release builds.
-keep class com.rton.expensebucket.data.backup.LedgerSnapshot { *; }
-keep class com.rton.expensebucket.data.backup.BackupPreferences { *; }
-keep class com.rton.expensebucket.data.model.Category { *; }
-keep class com.rton.expensebucket.data.model.PaymentMethod { *; }
-keep class com.rton.expensebucket.data.model.StoredValueBalance { *; }
-keep class com.rton.expensebucket.data.model.Project { *; }
-keep class com.rton.expensebucket.data.model.Transaction { *; }
-keep class com.rton.expensebucket.data.model.TransactionItem { *; }
-keep class com.rton.expensebucket.data.model.NotificationCapture { *; }
