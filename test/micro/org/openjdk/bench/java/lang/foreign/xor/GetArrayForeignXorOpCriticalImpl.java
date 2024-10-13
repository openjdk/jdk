package org.openjdk.bench.java.lang.foreign.xor;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.Linker.Option.critical;
import static org.openjdk.bench.java.lang.foreign.CLayouts.*;

public class GetArrayForeignXorOpCriticalImpl implements XorOp {

    static {
        System.loadLibrary("jnitest");

        Linker linker;
        linker = Linker.nativeLinker();
        FunctionDescriptor xor_op_func = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_INT);
        xor_op = linker.downcallHandle(SymbolLookup.loaderLookup().findOrThrow("xor_op"), xor_op_func, critical(true));
    }

    static final MethodHandle xor_op;
    GetArrayForeignXorOpCriticalImpl() {
    }

    public void xor(byte[] src, int sOff, byte[] dst, int dOff, int len) throws Throwable {
        xor_op.invokeExact(MemorySegment.ofArray(src).asSlice(sOff), MemorySegment.ofArray(dst).asSlice(dOff), len);
    }
}
