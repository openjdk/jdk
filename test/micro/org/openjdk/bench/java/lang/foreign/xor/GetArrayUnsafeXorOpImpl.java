package org.openjdk.bench.java.lang.foreign.xor;

import org.openjdk.bench.java.lang.foreign.Utils;
import sun.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.Linker.Option.critical;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.openjdk.bench.java.lang.foreign.CLayouts.*;

public class GetArrayUnsafeXorOpImpl implements XorOp {

    static final Unsafe UNSAFE = Utils.unsafe;
    static final int BYTE_ARR_OFFSET = Utils.unsafe.arrayBaseOffset(byte[].class);

    static {
        System.loadLibrary("jnitest");

        Linker linker;
        linker = Linker.nativeLinker();
        FunctionDescriptor xor_op_func = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_INT);
        xor_op = linker.downcallHandle(SymbolLookup.loaderLookup().find("xor_op").orElseThrow(), xor_op_func, critical(false));
    }

    static final MethodHandle xor_op;
    GetArrayUnsafeXorOpImpl() {
    }

    public void xor(byte[] src, int sOff, byte[] dst, int dOff, int len) throws Throwable {
        long srcBuf = UNSAFE.allocateMemory(len);
        long dstBuf = UNSAFE.allocateMemory(len);
        UNSAFE.copyMemory(src, sOff + BYTE_ARR_OFFSET, null, srcBuf, len);
        UNSAFE.copyMemory(dst, dOff + BYTE_ARR_OFFSET, null, dstBuf, len);
        xorOp(srcBuf, dstBuf, len);
        UNSAFE.copyMemory(null, dstBuf, dst, dOff + BYTE_ARR_OFFSET, len);
        UNSAFE.freeMemory(srcBuf);
        UNSAFE.freeMemory(dstBuf);
    }

    native void xorOp(long src, long dst, int len);
}
