package org.openjdk.bench.java.lang.foreign.xor;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

// import static java.lang.foreign.Linker.Option.isTrivial;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.ValueLayout.OfBoolean;
import static java.lang.foreign.ValueLayout.OfByte;
import static java.lang.foreign.ValueLayout.OfDouble;
import static java.lang.foreign.ValueLayout.OfFloat;
import static java.lang.foreign.ValueLayout.OfInt;
import static java.lang.foreign.ValueLayout.OfLong;
import static java.lang.foreign.ValueLayout.OfShort;
import static org.openjdk.bench.java.lang.foreign.CLayouts.*;

public class GetArrayForeignXorOpImpl implements XorOp {

    static {
        System.loadLibrary("jnitest");

        Linker linker;
        linker = Linker.nativeLinker();
        FunctionDescriptor xor_op_func = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_INT);
        xor_op = linker.downcallHandle(SymbolLookup.loaderLookup().find("xor_op").orElseThrow(), xor_op_func/*, isTrivial()*/);
    }

    static final MethodHandle xor_op;
    static final Arena arena = Arena.ofConfined();
    MemorySegment srcBuf;
    MemorySegment dstBuf;

    GetArrayForeignXorOpImpl(int len) {
        srcBuf = arena.allocate(len);
        dstBuf = arena.allocate(len);
    }

    public void xor(byte[] src, int sOff, byte[] dst, int dOff, int len) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment srcBuf = arena.allocate(len);
            MemorySegment.copy(src, sOff, srcBuf, JAVA_BYTE, 0, len);
            MemorySegment dstBuf = arena.allocate(len);
            MemorySegment.copy(dst, dOff, dstBuf, JAVA_BYTE, 0, len);
            xor_op.invokeExact(srcBuf, dstBuf, len);
            MemorySegment.copy(dstBuf, JAVA_BYTE, 0, dst, dOff, len);
        }
    }

    public void copy(int count, byte[] src, int sOff, byte[] dst, int dOff, int len) {
        for (int i = 0; i < count; i++) {
            MemorySegment.copy(src, sOff, srcBuf, JAVA_BYTE, 0, len);
            MemorySegment.copy(dst, dOff, dstBuf, JAVA_BYTE, 0, len);
            MemorySegment.copy(srcBuf, JAVA_BYTE, 0, dst, dOff, len);
        }
    }
}
