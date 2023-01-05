
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

    /** use serialVersionUID from JDK 21 for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = 669815459941734258L;

    public BigDecimal toBigDecimal() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public byte byteValue() {
        // TODO Auto-generated method stub
        return super.byteValue();
    }

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
    public short shortValue() {
        // TODO Auto-generated method stub
        return super.shortValue();
    }

    @Override
    public boolean equals(Object obj) {
        // TODO Auto-generated method stub
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return super.hashCode();
    }

    @Override
    public String toString() {
        int capacity = BigInteger.numChars
        StringBuilder b = new StringBuilder(capacity);
        // TODO Auto-generated method stub
        return toBigDecimal().toString();
    }

    /**
     * Compares this Rational with the specified Rational.  This
     * method is provided in preference to individual methods for each
     * of the six boolean comparison operators ({@literal <}, ==,
     * {@literal >}, {@literal >=}, !=, {@literal <=}).  The suggested
     * idiom for performing these comparisons is: {@code
     * (x.compareTo(y)} &lt;<i>op</i>&gt; {@code 0)}, where
     * &lt;<i>op</i>&gt; is one of the six comparison operators.
     *
     * @param  val Rational to which this Rational is to be compared.
     * @return -1, 0 or 1 as this Rational is numerically less than, equal
     *         to, or greater than {@code r}.
     */
    @Override
    public int compareTo(Rational val) {
        if (signum != val.signum)
            return signum > val.signum ? 1 : -1;

        if (signum == 0) // values are both zero
            return 0;
        
        int absComp;

        // compare absolute values
        if (denominator.equals(val.denominator))
            absComp = numerator.compareTo(val.numerator); // a/b < c/b <=> a < c
        else if (numerator.equals(val.numerator))
            absComp = val.denominator.compareTo(denominator); // a/b < a/c <=> c < b
        else // a/b < c/d <=> a*d < b*c
            absComp = numerator.multiply(val.denominator).compareTo(denominator.multiply(val.numerator));

        return signum * absComp; // adjust comparison with signum
    }
}