
package java.math;

import java.util.Objects;

public class Rational extends Number implements Comparable<Rational> {
    /**
     * The signum of this Rational: -1 for negative, 0 for zero, or
     * 1 for positive. Note that the Rational zero <em>must</em> have
     * a signum of 0. This is necessary to ensures that there is exactly one
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

    public int signum() {
        return signum;
    }

    /**
     * Returns the least non-negative denominator necessary to represent this Rational.
     * 
     * @return the least non-negative denominator necessary to represent this Rational.
     */
    public BigInteger getNumerator() {
        return numerator;
    }

    /**
     * Returns the least positive denominator necessary to represent this Rational.
     * 
     * @return the least positive denominator necessary to represent this Rational.
     */
    public BigInteger getDenominator() {
        return denominator;
    }

    /**
     * Converts this {@code Rational} to a {@code BigInteger}.
     * This conversion is analogous to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code long} as defined in
     * <cite>The Java Language Specification</cite>:
     * any fractional part of this
     * {@code Rational} will be discarded.  Note that this
     * conversion can lose information about the precision of the
     * {@code Rational} value.
     * <p>
     * To have an exception thrown if the conversion is inexact (in
     * other words if a nonzero fractional part is discarded), use the
     * {@link #toBigIntegerExact()} method.
     *
     * @return this {@code Rational} converted to a {@code BigInteger}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    public BigInteger toBigInteger() {
        // force to an integer, quietly
        return numerator.divide(denominator);
    }

    /**
     * Converts this {@code Rational} to a {@code BigInteger},
     * checking for lost information.  An exception is thrown if this
     * {@code Rational} has a nonzero fractional part.
     *
     * @return this {@code Rational} converted to a {@code BigInteger}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *         fractional part
     */
    public BigInteger toBigIntegerExact() {
        // round to an integer, with Exception if decimal part non-0
        BigInteger[] res = numerator.divideAndRemainder(denominator);

        if (res[1].signum() != 0)
            throw new ArithmeticException("Rounding necessary");
        
        return signum == -1 ? res[0].negate() : res[0];
    }

    /**
     * Converts this {@code Rational} to a {@code BigDecimal},
     * with rounding according to the context settings. Note that this
     * conversion can lose information about the {@code Rational} value.
     * <p>
     * To have an exception thrown if the conversion is inexact (in
     * other words if a nonzero fractional part is discarded), use the
     * {@link #toBigDecimalExact()} method.
     *
     * @param  mc the context to use.
     * @return this {@code Rational} converted to a {@code BigDecimal}.
     */
    public BigDecimal toBigDecimal(MathContext mc) {
        BigDecimal absVal = new BigDecimal(numerator).divide(denominator, mc);
        return signum == -1 ? absVal.negate() : absVal;
    }

    /**
     * Converts this {@code Rational} to a {@code BigDecimal},
     * checking for lost information.  An exception is thrown if
     * a nonzero fractional part is discarded.
     *
     * @return this {@code Rational} converted to a {@code BigDecimal}.
     * @throws ArithmeticException if a nonzero fractional part is discarded.
     * @since  1.5
     */
    public BigDecimal toBigDecimalExact() {
        return toBigDecimal(MathContext.UNLIMITED);
    }

    /**
     * Converts this {@code Rational} to a {@code double}.
     * This conversion is similar to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code float} as defined in
     * <cite>The Java Language Specification</cite>:
     * if this {@code Rational} has too great a
     * magnitude represent as a {@code double}, it will be
     * converted to {@link Double#NEGATIVE_INFINITY} or {@link
     * Double#POSITIVE_INFINITY} as appropriate.  Note that even when
     * the return value is finite, this conversion can lose
     * information about the {@code Rational} value.
     *
     * @return this {@code Rational} converted to a {@code double}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public double doubleValue() {
         return toBigDecimal(MathContext.DECIMAL64).doubleValue();
    }

    /**
     * Converts this {@code Rational} to a {@code float}.
     * This conversion is similar to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code float} as defined in
     * <cite>The Java Language Specification</cite>:
     * if this {@code Rational} has too great a
     * magnitude to represent as a {@code float}, it will be
     * converted to {@link Float#NEGATIVE_INFINITY} or {@link
     * Float#POSITIVE_INFINITY} as appropriate.  Note that even when
     * the return value is finite, this conversion can lose
     * information about the {@code Rational} value.
     *
     * @return this {@code Rational} converted to a {@code float}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public float floatValue() {
        return toBigDecimal(MathContext.DECIMAL32).floatValue();
    }

    /**
     * Converts this {@code Rational} to an {@code int}.
     * This conversion is analogous to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code short} as defined in
     * <cite>The Java Language Specification</cite>:
     * any fractional part of this
     * {@code Rational} will be discarded, and if the resulting
     * "{@code BigInteger}" is too big to fit in an
     * {@code int}, only the low-order 32 bits are returned.
     * Note that this conversion can lose information about the
     * overall magnitude and precision of this {@code Rational}
     * value as well as return a result with the opposite sign.
     *
     * @return this {@code Rational} converted to an {@code int}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public int intValue() {
        return (int) longValue();
    }

    /**
     * Converts this {@code Rational} to a {@code long}.
     * This conversion is analogous to the
     * <i>narrowing primitive conversion</i> from {@code double} to
     * {@code short} as defined in
     * <cite>The Java Language Specification</cite>:
     * any fractional part of this
     * {@code Rational} will be discarded, and if the resulting
     * "{@code BigInteger}" is too big to fit in a
     * {@code long}, only the low-order 64 bits are returned.
     * Note that this conversion can lose information about the
     * overall magnitude and precision of this {@code Rational} value as well
     * as return a result with the opposite sign.
     *
     * @return this {@code Rational} converted to a {@code long}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public long longValue() {
        return toBigInteger().longValue();
    }

    /**
     * Compares this Rational with the specified Object for equality.
     *
     * @param  obj Object to which this Rational is to be compared.
     * @return {@code true} if and only if the specified Object is a
     *         Rational whose value is numerically equal to this Rational.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (!(obj instanceof Rational r))
            return false;
        
        if (signum != r.signum)
            return false;
        
        if (signum == 0)
            return true;
        
        return numerator.equals(r.numerator) && denominator.equals(r.denominator);
    }

    /**
     * Returns the hash code for this Rational.
     *
     * @return hash code for this Rational.
     */
    @Override
    public int hashCode() {
        return signum * Objects.hash(numerator, denominator);
    }

    /**
     * Returns the decimal String representation of this Rational:
     * <i>numerator</i>/<i>denominator</i>.
     * 
     * The digit-to-character mapping provided by
     * {@code Character.forDigit} is used, and a minus sign is
     * prepended if appropriate. (This representation is compatible
     * with the {@link #Rational(String) (String)} constructor, and
     * allows for String concatenation with Java's + operator.)
     *
     * @return decimal String representation of this Rational.
     * @see Character#forDigit
     * @see #Rational(java.lang.String)
     */
    @Override
    public String toString() {
        final int radix = 10;
        int capacity = numerator.numChars(radix) + denominator.numChars(radix) + 1 + (signum < 0 ? 1 : 0);
        StringBuilder b = new StringBuilder(capacity);

        if (signum == -1)
            b.append('-');

        return b.append(numerator).append('/').append(denominator).toString();
    }

    /**
     * Compares this Rational with the specified Rational. This
     * method is provided in preference to individual methods for each
     * of the six boolean comparison operators ({@literal <}, ==,
     * {@literal >}, {@literal >=}, !=, {@literal <=}). The suggested
     * idiom for performing these comparisons is: {@code
     * (x.compareTo(y)} &lt;<i>op</i>&gt; {@code 0)}, where
     * &lt;<i>op</i>&gt; is one of the six comparison operators.
     *
     * @param val Rational to which this Rational is to be compared.
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