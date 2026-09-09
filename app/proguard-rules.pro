# jsoup can optionally use Google's re2j regex engine; it is not on the classpath and jsoup falls back to
# java.util.regex without it, so R8 may ignore the references.
-dontwarn com.google.re2j.**
