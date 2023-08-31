package org.openjdk.bench.java.lang.foreign.xor;

public interface XorOp {

    void xor(byte[] src, int sOff, byte[] dst, int dOff, int len) throws Throwable;

}
