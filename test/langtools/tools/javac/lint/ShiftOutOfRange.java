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
        a = a << (byte)0;
        a = a >> (char)7;
        a = a >>> (short)13;
        a <<= 17;
        a >>= (long)23;     // also generates "implicit cast from long to int in compound assignment is possibly lossy"
        a >>>= (byte)31;
        a <<= hashCode();
        a >>= hashCode();
        a >>>= hashCode();

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
        a = a << (byte)0;
        a = a >> (char)15;
        a = a >>> (short)25;
        a <<= 34;
        a >>= (long)47;
        a >>>= (byte)63;
        a <<= hashCode();
        a >>= hashCode();
        a >>>= hashCode();

        // These should generate warnings
        a = a << (byte)64;
        a = a >> (char)64;
        a = a >>> (short)64;
        a <<= 64;
        a >>= (long)64;
        a >>>= (byte)64;
    }
}
