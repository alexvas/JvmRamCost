# Disable obfuscation - keep original package and class names
-dontobfuscate

# Disable optimization (only shrinking is needed)
-dontoptimize

-dontwarn javax.lang.model.element.Modifier

# Note: We intentionally don't add the flags we'd need to make Enums work.
# That's because the Proguard configuration required to make it work on
# optimized code would preclude lots of optimization, like converting enums
# into ints.

# Throwables uses internal APIs for lazy stack trace resolution
-dontnote sun.misc.SharedSecrets
-keep class sun.misc.SharedSecrets {
  *** getJavaLangAccess(...);
}
-dontnote sun.misc.JavaLangAccess
-keep class sun.misc.JavaLangAccess {
  *** getStackTraceElement(...);
  *** getStackTraceDepth(...);
}

# FinalizableReferenceQueue calls this reflectively
# Proguard is intelligent enough to spot the use of reflection onto this, so we
# only need to keep the names, and allow it to be stripped out if
# FinalizableReferenceQueue is unused.
-keepnames class com.google.common.base.internal.Finalizer {
  *** startFinalizer(...);
}
# However, it cannot "spot" that this method needs to be kept IF the class is.
-keepclassmembers class com.google.common.base.internal.Finalizer {
  *** startFinalizer(...);
}
-keepnames class com.google.common.base.FinalizableReference {
  void finalizeReferent();
}
-keepclassmembers class com.google.common.base.FinalizableReference {
  void finalizeReferent();
}

# Striped64, LittleEndianByteArray, UnsignedBytes, AbstractFuture
-dontwarn sun.misc.Unsafe

# Striped64 appears to make some assumptions about object layout that
# really might not be safe. This should be investigated.
-keepclassmembers class com.google.common.cache.Striped64 {
  *** base;
  *** busy;
}
-keepclassmembers class com.google.common.cache.Striped64$Cell {
  <fields>;
}

-dontwarn java.lang.SafeVarargs

-keep class java.lang.Throwable {
  *** addSuppressed(...);
}

# Futures.getChecked, in both of its variants, is incompatible with proguard.

# Used by AtomicReferenceFieldUpdater and sun.misc.Unsafe
-keepclassmembers class com.google.common.util.concurrent.AbstractFuture** {
  *** waiters;
  *** value;
  *** listeners;
  *** thread;
  *** next;
}
-keepclassmembers class com.google.common.util.concurrent.AtomicDouble {
  *** value;
}
-keepclassmembers class com.google.common.util.concurrent.AggregateFutureState {
  *** remaining;
  *** seenExceptions;
}

# Since Unsafe is using the field offsets of these inner classes, we don't want
# to have class merging or similar tricks applied to these classes and their
# fields. It's safe to allow obfuscation, since the by-name references are
# already preserved in the -keep statement above.
-keep,allowshrinking,allowobfuscation class com.google.common.util.concurrent.AbstractFuture** {
  <fields>;
}

# Futures.getChecked (which often won't work with Proguard anyway) uses this. It
# has a fallback, but again, don't use Futures.getChecked on Android regardless.
-dontwarn java.lang.ClassValue

# MoreExecutors references AppEngine
-dontnote com.google.appengine.api.ThreadManager
-keep class com.google.appengine.api.ThreadManager {
  static *** currentRequestThreadFactory(...);
}
-dontnote com.google.apphosting.api.ApiProxy
-keep class com.google.apphosting.api.ApiProxy {
  static *** getCurrentEnvironment (...);
}

# Entry point
-keep class jvmram.dist.Main {
    public static void main(java.lang.String[]);
}

# Protobuf generated classes (use reflection)
-keep class jvmram.proto.** { *; }
-keep class com.google.protobuf.** { *; }

# gRPC services and infrastructure
-keep class io.grpc.** { *; }
-keep class * extends io.grpc.BindableService { *; }
-keepclassmembers class * extends io.grpc.stub.AbstractStub { *; }

# SLF4J logging
-keep class org.slf4j.** { *; }
-keep class org.slf4j.Logger { *; }
-keep class org.slf4j.LoggerFactory { *; }

# Guava BaseEncoding is needed by gRPC
-keep class com.google.common.io.BaseEncoding { *; }
-keep class com.google.common.io.BaseEncoding$* { *; }

# Suppress warnings for known safe cases
-dontwarn com.google.protobuf.**
-dontwarn io.grpc.**
-dontwarn com.google.gson.**
-dontwarn com.google.common.io.**

# VarHandle methods are available at runtime but ProGuard can't see them
-dontwarn java.lang.invoke.VarHandle

# com.sun.tools.attach classes are in jdk.attach module
-dontwarn com.sun.tools.attach.**

# Exclude JNDI resolver (not needed, causes issues with java.naming)
-assumenosideeffects class io.grpc.internal.JndiResourceResolverFactory { *; }
-assumenosideeffects class io.grpc.internal.JndiResourceResolverFactory$* { *; }

# JNA (Java Native Access) - required for Windows platform support
# Keep core JNA classes (but not platform.* which is handled separately)
-keep class com.sun.jna.* { *; }
-keep class com.sun.jna.ptr.* { *; }
-keep class com.sun.jna.win32.* { *; }
-keep interface com.sun.jna.Library { *; }
-keep interface com.sun.jna.Callback { *; }
-keepclassmembers class com.sun.jna.* { *; }
# Keep native methods in JNA
-keepclasseswithmembernames class com.sun.jna.Native {
    native <methods>;
    static <methods>;
}

# Keep Windows API classes - only what's actually used
-keep class com.sun.jna.platform.win32.Kernel32 { *; }
-keep class com.sun.jna.platform.win32.Kernel32$* { *; }
-keep class com.sun.jna.platform.win32.WinNT { *; }
-keep class com.sun.jna.platform.win32.WinNT$* { *; }
-keep class com.sun.jna.platform.win32.BaseTSD { *; }
-keep class com.sun.jna.platform.win32.BaseTSD$* { *; }
# WinDef - keep only used inner classes (DWORD, ULONGLONG), not RECT
-keep class com.sun.jna.platform.win32.WinDef { *; }
-keep class com.sun.jna.platform.win32.WinDef$DWORD { *; }
-keep class com.sun.jna.platform.win32.WinDef$DWORDLONG { *; }
-keep class com.sun.jna.platform.win32.WinDef$ULONGLONG { *; }
-keep class com.sun.jna.platform.win32.WinDef$ULONG { *; }
-keep class com.sun.jna.platform.win32.WinDef$LONG { *; }
-keep class com.sun.jna.platform.win32.WinDef$WORD { *; }
-keep class com.sun.jna.platform.win32.WinDef$BOOL { *; }

# Exclude JNA platform classes that depend on AWT/Swing
# Use -dontwarn to suppress warnings about missing AWT/Swing methods
-dontwarn com.sun.jna.platform.WindowUtils
-dontwarn com.sun.jna.platform.WindowUtils$**
-dontwarn com.sun.jna.platform.DesktopWindow
-dontwarn com.sun.jna.platform.KeyboardUtils
-dontwarn com.sun.jna.platform.KeyboardUtils$**
-dontwarn com.sun.jna.platform.RasterRangesUtils
-dontwarn com.sun.jna.platform.RasterRangesUtils$**
-dontwarn com.sun.jna.platform.win32.GDI32Util
-dontwarn com.sun.jna.platform.win32.GDI32Util$**
-dontwarn com.sun.jna.Native$AWT
-dontwarn com.sun.jna.Native$AWT$**

# Suppress warnings for AWT/Swing classes (not available in headless environment)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn java.awt.event.**
-dontwarn java.awt.image.**
-dontwarn java.awt.geom.**
