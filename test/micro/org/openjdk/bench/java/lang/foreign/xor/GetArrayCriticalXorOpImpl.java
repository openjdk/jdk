package org.openjdk.bench.java.lang.foreign.xor;

public class GetArrayCriticalXorOpImpl implements XorOp {

    static {
        System.loadLibrary("jnitest");
    }

    // Uses {Get|Release}PrimitiveArrayCritical to access the byte arrays
    public native void xor(byte[] src, int sOff, byte[] dst, int dOff, int len);
}
