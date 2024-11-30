package org.openjdk.bench.java.lang.foreign.xor;

public class GetArrayElementsXorOpImpl implements XorOp {

    static {
        System.loadLibrary("jnitest");
    }

    // Uses {Get|Release}ByteArrayElements to access the byte arrays
    public native void xor(byte[] src, int sOff, byte[] dst, int dOff, int len);
}
