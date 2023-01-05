
package java.math;

public class Rational extends Number implements Comparable<Rational> {
    /**
     * The signum of this Rational: -1 for negative, 0 for zero, or
     * 1 for positive.  Note that the Rational zero <em>must</em> have
     * a signum of 0.  This is necessary to ensures that there is exactly one
     * representation for each Rational value.
     */
    private final int signum;

    /**
     * The least non-negative numerator necessary to represent this Rational.
     */
    private final BigInteger numerator;

    /**
     * The least positive denominator necessary to represent this Rational.
     */
    private final BigInteger denominator;

    @Override
    public double doubleValue() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float floatValue() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int intValue() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long longValue() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int compareTo(Rational o) {
        // TODO Auto-generated method stub
        return 0;
    }
}