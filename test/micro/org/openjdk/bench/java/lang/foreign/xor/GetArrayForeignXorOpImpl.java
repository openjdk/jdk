package org.openjdk.bench.java.lang.foreign.xor;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.Linker.Option.critical;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.openjdk.bench.java.lang.foreign.CLayouts.*;

public class GetArrayForeignXorOpImpl implements XorOp {

    static {
        System.loadLibrary("jnitest");

        Linker linker;
        linker = Linker.nativeLinker();
        FunctionDescriptor xor_op_func = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_INT);
        xor_op = linker.downcallHandle(SymbolLookup.loaderLookup().findOrThrow("xor_op"), xor_op_func, critical(false));
    }

    static final MethodHandle xor_op;
    GetArrayForeignXorOpImpl() {
    }

    public void xor(byte[] src, int sOff, byte[] dst, int dOff, int len) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment srcBuf = arena.allocateFrom(JAVA_BYTE, MemorySegment.ofArray(src), JAVA_BYTE, sOff, len);
            MemorySegment dstBuf = arena.allocateFrom(JAVA_BYTE, MemorySegment.ofArray(dst), JAVA_BYTE, dOff, len);
            xor_op.invokeExact(srcBuf, dstBuf, len);
            MemorySegment.copy(dstBuf, JAVA_BYTE, 0, dst, dOff, len);
        }
    }
}
