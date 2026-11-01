-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn javax.servlet.**
-dontwarn org.apache.**
-dontwarn coil.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.**
-dontwarn org.slf4j.impl.**

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Timber's DebugTree takes its tag from the calling class name, and R8 renames
# classes in release. Without these the sync/transfer log lines arrive as
# `a.b.c` and the diagnosability this fork depends on is gone. Class names only -
# members stay obfuscated, so the size cost is negligible.
-keepnames class com.ivy.data.sync.**
-keepnames class com.ivy.domain.usecase.transfer.**
-keepnames class com.ivy.transaction.pending.**
-keepnames class com.ivy.transaction.answers.**