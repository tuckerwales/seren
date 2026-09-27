# Commons Compress refers to optional libraries for formats Seren Files doesn't use (zstd, brotli,
# LZ4 via aircompressor, Pack200 via ASM) and to OSGi; none are on Android, and none are needed.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn io.airlift.compress.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn org.apache.commons.compress.harmony.**
