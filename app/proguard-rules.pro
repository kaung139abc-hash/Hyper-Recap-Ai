# Keep JNI/native entry points used by sherpa-onnx.
-keep class com.k2fsa.** { *; }
-keep class org.k2fsa.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
