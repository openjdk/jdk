package org.openjdk.bench.java.lang.foreign.xor;

public class GetArrayRegionXorOpImpl implements XorOp {

    static {
        System.loadLibrary("jnitest");
    }

    public GetArrayRegionXorOpImpl(int len) {
    }

    // Uses {Get|Set}ByteArrayRegion to access the byte arrays
    public native void xor(byte[] src, int sOff, byte[] dst, int dOff, int len);

    public native void copy(int count, byte[] src, int sOff, byte[] dst, int dOff, int len);
}
