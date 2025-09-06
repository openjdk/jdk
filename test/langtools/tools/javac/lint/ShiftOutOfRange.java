/*
 * @test /nodynamiccopyright/
 * @bug 5038439
 * @summary Verify warnings about bit shifts using out-of-range shift amounts
 * @compile/ref=ShiftOutOfRange.out -XDrawDiagnostics -Xlint:lossy-conversions ShiftOutOfRange.java
 */

public class ShiftOutOfRange {

    public void shiftInt() {
        int a = 123;

        // These should generate warnings
        a = a << (byte)-1;
        a = a >> (char)-1;
        a = a >>> (short)-1;
        a <<= -1;
        a >>= -1L;          // also generates "implicit cast from long to int in compound assignment is possibly lossy"
        a >>>= (byte)-1;

        // These should not generate warnings
        a = a << (byte)16;
        a = a >> (char)16;
        a = a >>> (short)16;
        a <<= 16;
        a >>= (long)16;     // also generates "implicit cast from long to int in compound assignment is possibly lossy"
        a >>>= (byte)16;

        // These should generate warnings
        a = a << (byte)32;
        a = a >> (char)32;
        a = a >>> (short)32;
        a <<= 32;
        a >>= (long)32;     // also generates "implicit cast from long to int in compound assignment is possibly lossy"
        a >>>= (byte)32;
    }

    public void shiftLong() {
        long a = 123;

        // These should generate warnings
        a = a << (byte)-1;
        a = a >> (char)-1;
        a = a >>> (short)-1;
        a <<= -1;
        a >>= -1L;
        a >>>= (byte)-1;

        // These should not generate warnings
        a = a << (byte)32;
        a = a >> (char)32;
        a = a >>> (short)32;
        a <<= 32;
        a >>= (long)32;
        a >>>= (byte)32;

        // These should generate warnings
        a = a << (byte)64;
        a = a >> (char)64;
        a = a >>> (short)64;
        a <<= 64;
        a >>= (long)64;
        a >>>= (byte)64;
    }
}
