package org.openjdk.bench.java.lang.foreign.xor;

public interface XorOp {

    void xor(byte[] src, int sOff, byte[] dst, int dOff, int len) throws Throwable;

<<<<<<< HEAD
    void copy(int count, byte[] src, int sOff, byte[] dst, int dOff, int len);
=======
>>>>>>> 9727f4bdddc071e6f59806087339f345405ab004
}
