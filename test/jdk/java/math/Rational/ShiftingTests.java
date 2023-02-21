
/*
 * @test
 * @summary Tests of shiftLeft and shiftRight on Integer.MIN_VALUE
 * @requires os.maxMemory >= 1g
 * @run main/othervm -Xmx512m ExtremeShiftingTests
 */
import java.math.Rational;
import java.util.Random;

import static java.math.Rational.*;

import java.math.BigInteger;

public class ShiftingTests {
    public static void main(String... args) {
        Rational r = ONE.shiftRight(1);

        if (!r.equals(new Rational("0.5")))
            throw new RuntimeException("1 >> 1 result: " + r);

        r = new Rational(10).shiftRight(2);

        if (!r.equals(new Rational("2.5")))
            throw new RuntimeException("10 >> 2 result: " + r);

        r = new Rational("2.5").shiftRight(1);

        if (!r.equals(new Rational("1.25")))
            throw new RuntimeException("2.5 >> 1 result: " + r);

        r = new Rational("3.5").shiftRight(1);

        if (!r.equals(new Rational("1.75")))
            throw new RuntimeException("3.5 >> 1 result: " + r);

        r = new Rational(Long.MAX_VALUE).shiftRight(32);

        if (!r.equals(new Rational(Long.MAX_VALUE).divide(TWO.pow(32))))
            throw new RuntimeException("Long.MAX_VALUE >> 32 result: " + r);

        r = new Rational(Long.MAX_VALUE).shiftRight(33);

        if (!r.equals(new Rational(Long.MAX_VALUE).divide(TWO.pow(33))))
            throw new RuntimeException("Long.MAX_VALUE >> 33 result: " + r);

        r = new Rational("0.5").shiftLeft(1);

        if (!r.equals(ONE))
            throw new RuntimeException("0.5 << 1 result: " + r);

        r = new Rational("2.5").shiftLeft(2);

        if (!r.equals(new Rational(10)))
            throw new RuntimeException("2.5 << 2 result: " + r);

        r = new Rational("1.25").shiftLeft(1);

        if (!r.equals(new Rational("2.5")))
            throw new RuntimeException("1.25 << 1 result: " + r);

        r = new Rational("1.75").shiftLeft(1);

        if (!r.equals(new Rational("3.5")))
            throw new RuntimeException("1.75 << 1 result: " + r);

        r = new Rational(Long.MAX_VALUE).divide(TWO.pow(32)).shiftLeft(32);

        if (!r.equals(new Rational(Long.MAX_VALUE)))
            throw new RuntimeException("(Long.MAX_VALUE / 2^32) << 32 result: " + r);

        r = new Rational(Long.MAX_VALUE).divide(TWO.pow(33)).shiftLeft(33);

        if (!r.equals(new Rational(Long.MAX_VALUE)))
            throw new RuntimeException("(Long.MAX_VALUE / 2^33) << 33 result: " + r);

        BigInteger bi = new BigInteger(96, new Random());
        r = new Rational(bi).shiftRight(63);

        if (!r.equals(new Rational(bi).divide(TWO.pow(63))))
            throw new RuntimeException(bi + " >> 63 result: " + r);

        if (!r.shiftLeft(63).equals(new Rational(bi)))
            throw new RuntimeException(r + " << 63 result: " + r);

        bi = new BigInteger(96, new Random());
        r = new Rational(bi).shiftRight(64);

        if (!r.equals(new Rational(bi).divide(TWO.pow(64))))
            throw new RuntimeException(bi + " >> 64 result: " + r);

        if (!r.shiftLeft(64).equals(new Rational(bi)))
            throw new RuntimeException(r + " << 64 result: " + r);

        try {
            ONE.shiftLeft(Integer.MIN_VALUE);
            throw new RuntimeException("1 << " + Integer.MIN_VALUE);
        } catch (ArithmeticException ae) {
            // Expected
        }

        r = ZERO.shiftLeft(Integer.MIN_VALUE);
        if (!r.equals(ZERO))
            throw new RuntimeException("0 << " + Integer.MIN_VALUE);

        try {
            valueOf(-1).shiftLeft(Integer.MIN_VALUE);
            throw new RuntimeException("-1 << " + Integer.MIN_VALUE);
        } catch (ArithmeticException ae) {
            // Expected
        }

        try {
            ONE.shiftRight(Integer.MIN_VALUE);
            throw new RuntimeException("1 >> " + Integer.MIN_VALUE);
        } catch (ArithmeticException ae) {
            ; // Expected
        }

        r = ZERO.shiftRight(Integer.MIN_VALUE);
        if (!r.equals(ZERO))
            throw new RuntimeException("0 >> " + Integer.MIN_VALUE);

        try {
            valueOf(-1).shiftRight(Integer.MIN_VALUE);
            throw new RuntimeException("-1 >> " + Integer.MIN_VALUE);
        } catch (ArithmeticException ae) {
            ; // Expected
        }

    }
}
