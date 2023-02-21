/*
 * @test
 * @summary Verify Rational objects with collapsed values are serialized properly.
 */

import java.math.*;
import java.io.*;
import java.util.List;

public class SerializationTests {

    public static void main(String... args) throws Exception {
        checkRationalSerialRoundTrip();
        checkRationalSubSerialRoundTrip();
    }

    private static void checkSerialForm(Rational r) throws Exception  {
        checkSerialForm0(r);
        checkSerialForm0(r.negate());
    }

    private static void checkSerialForm0(Rational r) throws Exception  {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try(ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(r);
            oos.flush();
        }

        ObjectInputStream ois = new
            ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        Rational tmp = (Rational) ois.readObject();

        if (!r.equals(tmp) ||
                r.hashCode() != tmp.hashCode() ||
                r.getClass() != tmp.getClass() ||
                // Directly test equality of components
                r.signum() != tmp.signum() ||
                !r.absFloor().equals(tmp.absFloor()) ||
                !r.numerator().equals(tmp.numerator()) ||
                !r.denominator().equals(tmp.denominator())) {
            System.err.print("  original : " + r);
            System.err.println(" (hash: 0x" + Integer.toHexString(r.hashCode()) + ")");
            System.err.print("serialized : " + tmp);
            System.err.println(" (hash: 0x" + Integer.toHexString(tmp.hashCode()) + ")");
            throw new RuntimeException("Bad serial roundtrip");
        }

        // If the class of the deserialized number is Rational,
        // verify the implementation constraint on the fields
        // having BigInteger class
        if (tmp.getClass() == Rational.class) {
            if (tmp.absFloor().getClass() != BigInteger.class ||
                    tmp.numerator().getClass() != BigInteger.class ||
                    tmp.denominator().getClass() != BigInteger.class) {
                throw new RuntimeException("Not using genuine BigInteger as type field");
            }
        }
    }

    private static class BigIntegerSub extends BigInteger {
        private static final long serialVersionUID = 2700606720458995174L;

        public BigIntegerSub(BigInteger bi) {
            super(bi.toByteArray());
        }

        @Override
        public String toString() {
            return java.util.Arrays.toString(toByteArray());
        }
    }
    
    private static void checkRationalSerialRoundTrip() throws Exception {
        var values =
            List.of(Rational.ZERO,
                    Rational.ONE,
                    Rational.TEN,
                    new Rational(0),
                    new Rational(1),
                    new Rational(10),
                    new Rational(Integer.MAX_VALUE),
                    new Rational(Long.MAX_VALUE - 1),
                    new Rational("0.1"),
                    new Rational("100e-50"),
                    new Rational(new BigDecimal(new BigIntegerSub(BigInteger.ONE), 2)));

        for(Rational value : values) {
            checkSerialForm(value);
        }
    }

    private static class RationalSub extends Rational {
        
        private Rational val;
        
        public RationalSub(Rational r) {
            super(0);
            val = r;
        }

        @Override
        public String toString() {
            return val.toString();
        }
    }

    // Subclass defining a serialVersionUID
    private static class RationalSubSVUID extends Rational {
        @java.io.Serial
        private static final long serialVersionUID = 0x0123_4567_89ab_cdefL;
        
        private Rational val;

        public RationalSubSVUID(Rational r) {
            super(0);
            val = r;
        }
        
        @Override
        public String toString() {
            return val.toString();
        }
    }

    private static void checkRationalSubSerialRoundTrip() throws Exception {
        var values =
            List.of(Rational.ZERO,
                    Rational.ONE,
                    Rational.TEN,
                    new Rational("10e-1234"));

        for(var value : values) {
            checkSerialForm(new RationalSub(value));
            checkSerialForm(new RationalSubSVUID(value));
        }
    }
}
