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
        a = a << (byte)-32;
        a = a >> (short)-32;
        a = a >>> -32;
        a <<= -32L;
        a >>= (byte)-32;
        a >>>= (short)-32;

        // These should not generate warnings
        a = a << (byte)-31;
        a = a >> (short)-23;
        a = a >>> -17;
        a <<= -13L;
        a >>= (byte)-1;
        a >>>= (short)0;
        a = a << (byte)0;
        a = a >> (char)7;
        a = a >>> (short)13;
        a <<= 17;
        a >>= (long)23;
        a >>>= (byte)31;
        a <<= hashCode();
        a >>= hashCode();
        a >>>= hashCode();

        // These should generate warnings
        a = a << (byte)32;
        a = a >> (char)32;
        a = a >>> (short)32;
        a <<= 32;
        a >>= (long)32;
        a >>>= (byte)32;
    }

    public void shiftLong() {
        long a = 123;

        // These should generate warnings
        a = a << (byte)-64;
        a = a >> (short)-64;
        a = a >>> -64;
        a <<= -64L;
        a >>= (byte)-64L;
        a >>>= (short)-64;

        // These should not generate warnings
        a = a << (byte)-63;
        a = a >> (short)-47;
        a = a >>> -34;
        a <<= -25L;
        a >>= (byte)-15;
        a >>>= (short)0;
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
