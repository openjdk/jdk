package org.openjdk.bench.java.lang.foreign.xor;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Param;
<<<<<<< HEAD
import org.openjdk.jmh.annotations.TearDown;
=======
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
<<<<<<< HEAD
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "--enable-preview" })
=======
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004

public class XorTest {

    XorOp impl = null;
<<<<<<< HEAD
    int count;
=======
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
    int alen;
    int off;
    int len;
    byte[] src, dst;

    @Param
    SizeKind sizeKind;

    @Param
    ArrayKind arrayKind;

    public enum SizeKind {
        SMALL,
        MEDIUM,
        LARGE;
    }

    public enum ArrayKind {
<<<<<<< HEAD
        ELEMENTS,
        REGION,
        CRITICAL,
        FOREIGN;
=======
        JNI_ELEMENTS,
        JNI_REGION,
        JNI_CRITICAL,
        FOREIGN_NO_INIT,
        FOREIGN_INIT,
        FOREIGN_CRITICAL,
        UNSAFE;
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
    }

    @Setup
    public void setup() throws Throwable {
<<<<<<< HEAD
        switch (sizeKind) {
            case SMALL:
                count = 1000;
=======
        switch (arrayKind) {
            case JNI_CRITICAL:
                impl = new GetArrayCriticalXorOpImpl();
                break;
            case JNI_ELEMENTS:
                impl = new GetArrayElementsXorOpImpl();
                break;
            case JNI_REGION:
                impl = new GetArrayRegionXorOpImpl();
                break;
            case FOREIGN_NO_INIT:
                impl = new GetArrayForeignXorOpImpl();
                break;
            case FOREIGN_INIT:
                impl = new GetArrayForeignXorOpInitImpl();
                break;
            case FOREIGN_CRITICAL:
                impl = new GetArrayForeignXorOpCriticalImpl();
                break;
            case UNSAFE:
                impl = new GetArrayUnsafeXorOpImpl();
                break;
            default:
                throw new UnsupportedOperationException(arrayKind.toString());
        }

        switch (sizeKind) {
            case SMALL:
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
                alen = 1048576;             // 1 MB
                off = 1024 * 10;
                len = 1024 * 100;           // 100 KB
                break;
            case MEDIUM:
<<<<<<< HEAD
                count = 50;
=======
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
                alen = 1048576 * 8;         // 8 MB
                off = 1048576 * 1;
                len = 1048576 * 2;          // 2 MB
                break;
            case LARGE:
<<<<<<< HEAD
                count = 10;
=======
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
                alen = 1048576 * 100;       // 100 MB
                off = 1048576 * 5;
                len = 1048576 * 10;         // 10 MB
                break;
            default:
                throw new UnsupportedOperationException(sizeKind.toString());
        }

<<<<<<< HEAD
        switch (arrayKind) {
            case CRITICAL:
                impl = new GetArrayCriticalXorOpImpl();
                break;
            case ELEMENTS:
                impl = new GetArrayElementsXorOpImpl();
                break;
            case REGION:
                impl = new GetArrayRegionXorOpImpl();
                break;
            case FOREIGN:
                impl = new GetArrayForeignXorOpImpl(alen);
                break;
            default:
                throw new UnsupportedOperationException(arrayKind.toString());
        }

=======
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
        src = new byte[alen];
        dst = new byte[alen];
        Arrays.fill(src, off, off + len, (byte)0xaa);
        Arrays.fill(dst, off, off + len, (byte)0x5a);
        check();
    }

    void check() throws Throwable {
<<<<<<< HEAD
        impl.copy(count, src, off, dst, off, len);
        if (arrayKind != ArrayKind.CRITICAL && !verify(dst, off, off + len, (byte)0xaa)) {
            throw new IllegalStateException("Copy failed to verify");
        }
        Arrays.fill(src, off, off + len, (byte)0xaa);
        Arrays.fill(dst, off, off + len, (byte)0x5a);
        impl.xor(src, off, dst, off, len);
        if (!verify(dst, off, off + len, (byte)0xf0)) {
            throw new IllegalStateException("Xor failed to verify");
=======
        impl.xor(src, off, dst, off, len);
        if (!verify(dst, off, off + len, (byte)0xf0)) {
            throw new IllegalStateException("Failed to verify");
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
        }
    }


    @Benchmark
    public void xor() throws Throwable {
<<<<<<< HEAD
        for (int i = 0; i < count; ++i) {
            impl.xor(src, off, dst, off, len);
        }
    }

    @Benchmark
    public void copy() throws Throwable {
        impl.copy(count, src, off, dst, off, len);
=======
        impl.xor(src, off, dst, off, len);
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
    }

    static boolean verify(byte[] buf, int start, int end, byte val) {
        for (int i = start; i < end; ++i) {
            if (buf[i] != val)
                return false;
        }
        return true;
    }

}
