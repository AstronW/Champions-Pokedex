# Data is decoded explicitly through org.json; no reflection models to preserve.

# Rhino's optional JavaBean-to-JSON converter references desktop-only java.beans.
# The calculator exposes only JSON strings and safe JS objects, denies all Java
# classes, and never selects the BEAN converter. Do not suppress other warnings.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Context/VMBridge and LazilyLoadedCtor use class names and reflected init methods.
# Preserve the interpreter and builtins; shell, debugger and JVM codegen are unused.
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.jdk18.* { *; }
-keep class org.mozilla.javascript.regexp.* { *; }
-keep class org.mozilla.javascript.typedarrays.* { *; }
-keep class org.mozilla.javascript.xmlimpl.* { *; }
