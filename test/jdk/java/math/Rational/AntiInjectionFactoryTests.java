/*
 * @test
 * @summary Test constructors of Rational to replace BigInteger subclasses
 * @author Fabio Romano
 */

import java.math.*;

public class AntiInjectionFactoryTests {
    public static void main(String... args) {
        TestBigInteger tbi = new TestBigInteger(BigInteger.ONE);
        // Create Rational's using each of the factory methods
        // with guards on the class of the arguments
        Rational[] values = { new Rational(tbi), Rational.valueOf(tbi, tbi) };

        for (var r : values) {
            BigInteger floor = r.absFloor();

            if (floor.getClass() != BigInteger.class)
                throw new RuntimeException("Bad class for floor");

            if (!floor.equals(BigInteger.ONE))
                throw new RuntimeException("Bad value for floor");

            BigInteger num = r.numerator();

            if (num.getClass() != BigInteger.class)
                throw new RuntimeException("Bad class for numerator");

            if (!num.equals(BigInteger.ZERO))
                throw new RuntimeException("Bad value for numerator");

            BigInteger den = r.denominator();

            if (den.getClass() != BigInteger.class)
                throw new RuntimeException("Bad class for denominator");

            if (!den.equals(BigInteger.ONE))
                throw new RuntimeException("Bad value for denominator");
        }

        TestBigDecimal tbd = new TestBigDecimal(BigDecimal.ONE);
        // Create Rational's using each of the factory methods
        // with guards on the class of the arguments
        values = new Rational[] { new Rational(tbd), Rational.valueOf(tbd, tbd) };

        for (var r : values) {
            BigInteger floor = r.absFloor();

            if (floor.getClass() != BigInteger.class)
                throw new RuntimeException("Bad class for floor");

            if (!floor.equals(BigInteger.ONE))
                throw new RuntimeException("Bad value for floor");

            BigInteger num = r.numerator();

            if (num.getClass() != BigInteger.class)
                throw new RuntimeException("Bad class for numerator");

            if (!num.equals(BigInteger.ZERO))
                throw new RuntimeException("Bad value for numerator");

            BigInteger den = r.denominator();

            if (den.getClass() != BigInteger.class)
                throw new RuntimeException("Bad class for denominator");

            if (!den.equals(BigInteger.ONE))
                throw new RuntimeException("Bad value for denominator");
        }
    }

    private static class TestBigDecimal extends BigDecimal {
        private static final long serialVersionUID = -6639002206221495763L;

        public TestBigDecimal(BigDecimal bd) {
            super(bd.toString());
        }

        @Override
        public BigDecimal abs() {
            return new TestBigDecimal(super.abs());
        }

        @Override
        public BigDecimal subtract(BigDecimal subtrahend) {
            return new TestBigDecimal(super.subtract(subtrahend));
        }

        @Override
        public BigInteger unscaledValue() {
            return new TestBigInteger(super.unscaledValue());
        }

        @Override
        public BigInteger toBigInteger() {
            return new TestBigInteger(super.toBigInteger());
        }
    }

    private static class TestBigInteger extends BigInteger {
        private static final long serialVersionUID = -5036844692433316127L;

        public TestBigInteger(BigInteger bi) {
            super(bi.toByteArray());
        }

        @Override
        public BigInteger abs() {
            return new TestBigInteger(super.abs());
        }

        @Override
        public BigInteger[] divideAndRemainder(BigInteger val) {
            BigInteger[] res = super.divideAndRemainder(val);
            return new BigInteger[] { new TestBigInteger(res[0]), new TestBigInteger(res[1]) };
        }

        @Override
        public BigInteger gcd(BigInteger val) {
            return new TestBigInteger(super.gcd(val));
        }

        @Override
        public BigInteger shiftRight(int n) {
            return new TestBigInteger(super.shiftRight(n));
        }

        @Override
        public String toString() {
            return java.util.Arrays.toString(toByteArray());
        }
    }
}
