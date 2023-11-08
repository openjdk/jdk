package org.openjdk.bench.java.lang.foreign.xor;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.TearDown;
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
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED", "--enable-preview" })

public class XorTest {

    XorOp impl = null;
    int count;
    int alen = 1048576 * 100;
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
        ELEMENTS,
        REGION,
        CRITICAL,
        FOREIGN;
    }

    @Setup
    public void setup() throws Throwable {
        switch (arrayKind) {
            case CRITICAL:
                impl = new GetArrayCriticalXorOpImpl();
                break;
            case ELEMENTS:
                impl = new GetArrayElementsXorOpImpl();
                break;
            case REGION:
                impl = new GetArrayRegionXorOpImpl(alen);
                break;
            case FOREIGN:
                impl = new GetArrayForeignXorOpImpl(alen);
                break;
            default:
                throw new UnsupportedOperationException(arrayKind.toString());
        }

        switch (sizeKind) {
            case SMALL:
                count = 1000;
                alen = 1048576;             // 1 MB
                off = 1024 * 10;
                len = 1024 * 100;           // 100 KB
                break;
            case MEDIUM:
                count = 50;
                alen = 1048576 * 8;         // 8 MB
                off = 1048576 * 1;
                len = 1048576 * 2;          // 2 MB
                break;
            case LARGE:
                count = 10;
                alen = 1048576 * 100;       // 100 MB
                off = 1048576 * 5;
                len = 1048576 * 10;         // 10 MB
                break;
            default:
                throw new UnsupportedOperationException(sizeKind.toString());
        }

        src = new byte[alen];
        dst = new byte[alen];
        Arrays.fill(src, off, off + len, (byte)0xaa);
        Arrays.fill(dst, off, off + len, (byte)0x5a);
        check();
    }

    void check() throws Throwable {
        impl.copy(count, src, off, dst, off, len);
        if (arrayKind != ArrayKind.CRITICAL && !verify(dst, off, off + len, (byte)0xaa)) {
            throw new IllegalStateException("Copy failed to verify");
        }
        Arrays.fill(src, off, off + len, (byte)0xaa);
        Arrays.fill(dst, off, off + len, (byte)0x5a);
        impl.xor(src, off, dst, off, len);
        if (!verify(dst, off, off + len, (byte)0xf0)) {
            throw new IllegalStateException("Xor failed to verify");
        }
    }


    @Benchmark
    public void xor() throws Throwable {
        for (int i = 0; i < count; ++i) {
            impl.xor(src, off, dst, off, len);
        }
    }

    @Benchmark
    public void copy() throws Throwable {
        impl.copy(count, src, off, dst, off, len);
    }

    static boolean verify(byte[] buf, int start, int end, byte val) {
        for (int i = start; i < end; ++i) {
            if (buf[i] != val)
                return false;
        }
        return true;
    }

}
