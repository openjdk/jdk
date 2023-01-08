
package java.math;

import java.util.Arrays;
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

    // Constants
    /**
     * The value 0.
     */
    public static final Rational ZERO = new Rational(0, BigInteger.ZERO, BigInteger.ONE);

    /**
     * The value 1.
     */
    public static final Rational ONE = new Rational(1, BigInteger.ONE, BigInteger.ONE);

    /**
     * The value 2.
     */
    public static final Rational TWO = new Rational(1, BigInteger.TWO, BigInteger.ONE);

    /**
     * The value 10.
     */
    public static final Rational TEN = new Rational(1, BigInteger.TEN, BigInteger.ONE);

    // Constructors

    /**
     * Translates a {@code double} into a {@code Rational} which
     * is the exact fractional representation of the {@code double}'s
     * binary floating-point value.
     * <p>
     * <b>Notes:</b>
     * <ol>
     * <li>
     * The results of this constructor can be somewhat unpredictable.
     * One might assume that writing {@code new Rational(0.1)} in
     * Java creates a {@code Rational} which is exactly equal to
     * 0.1 (1/10), but it is
     * actually equal to
     * 0.1000000000000000055511151231257827021181583404541015625.
     * This is because 0.1 cannot be represented exactly as a
     * {@code double} (or, for that matter, as a binary fraction of
     * any finite length). Thus, the value that is being passed
     * <em>in</em> to the constructor is not exactly equal to 0.1,
     * appearances notwithstanding.
     *
     * <li>
     * The {@code String} constructor, on the other hand, is
     * perfectly predictable: writing {@code new Rational("0.1")}
     * creates a {@code Rational} which is <em>exactly</em> equal to
     * 0.1, as one would expect. Therefore, it is generally
     * recommended that the {@linkplain #Rational(String)
     * String constructor} be used in preference to this one.
     *
     * <li>
     * When a {@code double} must be used as a source for a
     * {@code Rational}, note that this constructor provides an
     * exact conversion; it does not give the same result as
     * converting the {@code double} to a {@code String} using the
     * {@link Double#toString(double)} method and then using the
     * {@link #Rational(String)} constructor. To get that result,
     * use the {@code static} {@link #valueOf(double)} method.
     * </ol>
     *
     * @param val {@code double} value to be converted to
     *            {@code Rational}.
     * @throws NumberFormatException if {@code val} is infinite or NaN.
     */
    public Rational(double val) {
        if (Double.isInfinite(val) || Double.isNaN(val))
            throw new NumberFormatException("Infinite or NaN");
        // Translate the double into sign, exponent and significand, according
        // to the formulae in JLS, Section 20.10.22.
        long valBits = Double.doubleToLongBits(val);
        int sign = ((valBits >> 63) == 0 ? 1 : -1);
        int exponent = (int) ((valBits >> 52) & 0x7ffL);
        long significand = (exponent == 0
                ? (valBits & ((1L << 52) - 1)) << 1
                : (valBits & ((1L << 52) - 1)) | (1L << 52));
        exponent -= 1075;
        // At this point, val == sign * significand * 2**exponent.

        if (significand == 0) {
            signum = 0;
            numerator = BigInteger.ZERO;
            denominator = BigInteger.ONE;
        } else if (exponent < 0) {
            // Simplify even significands
            int zeros = Long.numberOfTrailingZeros(significand);
            significand >>= zeros;
            exponent += zeros;

            // now the significand and the denominator are relative primes
            // the significand is odd and the denominator is a power of two
            signum = sign;
            numerator = BigInteger.valueOf(significand);
            denominator = BigInteger.ONE.shiftLeft(-exponent);
        } else {
            signum = sign;
            numerator = BigInteger.valueOf(significand).shiftLeft(exponent);
            denominator = BigInteger.ONE;
        }
    }

    /**
     * Translates a {@code long} into a {@code Rational}.
     *
     * @param val {@code long} value to be converted to
     *            {@code Rational}.
     */
    public Rational(long val) {
        denominator = BigInteger.ONE;

        if (val > 0) {
            signum = 1;
            numerator = BigInteger.valueOf(val);
        } else if (val < 0) {
            signum = -1;
            numerator = BigInteger.valueOf(-val);
        } else {
            signum = 0;
            numerator = BigInteger.ZERO;
        }
    }

    /**
     * Translates a {@code BigInteger} into a {@code Rational}.
     *
     * @param val {@code BigInteger} value to be converted to
     *            {@code Rational}.
     */
    public Rational(BigInteger val) {
        signum = val.signum();
        numerator = val.abs();
        denominator = BigInteger.ONE;
    }

    /**
     * Translates a {@code BigDecimal} into a {@code Rational}.
     *
     * @param val {@code BigDecimal} value to be converted to
     *            {@code Rational}.
     */
    public Rational(BigDecimal val) {
        signum = val.signum();

        if (signum == 0) {
            numerator = BigInteger.ZERO;
            denominator = BigInteger.ONE;
            return;
        }

        final int scale = val.scale();
        final BigInteger intVal = val.unscaledValue().abs();
        final Rational res;

        if (scale > 0)
            res = valueOf(signum, intVal, BigDecimal.bigTenToThe(scale));
        else if (scale < 0)
            res = valueOf(signum, BigDecimal.bigMultiplyPowerTen(intVal, -scale), BigInteger.ONE);
        else
            res = valueOf(signum, intVal, BigInteger.ONE);

        numerator = res.numerator;
        denominator = res.denominator;
    }

    /**
     * Translates a character array representation of a
     * decimal number into a {@code Rational}, accepting the
     * same sequence of characters as the {@link #Rational(String)}
     * constructor, while allowing a sub-array to be specified.
     *
     * @implNote If the sequence of characters is already available
     * within a character array, using this constructor is faster than
     * converting the {@code char} array to string and using the
     * {@code Rational(String)} constructor.
     *
     * @param  in {@code char} array that is the source of characters.
     * @param  offset first character in the array to inspect.
     * @param  len number of characters to consider.
     * @throws NumberFormatException if {@code in} is not a valid
     *         representation of a decimal number or the defined subarray
     *         is not wholly within {@code in}.
     */
    public Rational(char[] in, int offset, int len) {
        this(new BigDecimal(in, offset, len));
    }

    /**
     * Translates a character array representation of a
     * decimal number into a {@code Rational}, accepting the
     * same sequence of characters as the {@link #Rational(String)}
     * constructor.
     *
     * @implNote If the sequence of characters is already available
     * as a character array, using this constructor is faster than
     * converting the {@code char} array to string and using the
     * {@code Rational(String)} constructor.
     *
     * @param in {@code char} array that is the source of characters.
     * @throws NumberFormatException if {@code in} is not a valid
     *         representation of a decimal number.
     */
    public Rational(char[] in) {
        this(in, 0, in.length);
    }

    /**
     * Translates the string representation of a decimal number
     * into a {@code Rational}.  The string representation consists
     * of an optional sign, {@code '+'} (<code> '&#92;u002B'</code>) or
     * {@code '-'} (<code>'&#92;u002D'</code>), followed by a sequence of
     * zero or more decimal digits ("the integer"), optionally
     * followed by a fraction, optionally followed by an exponent.
     *
     * <p>The fraction consists of a decimal point followed by zero
     * or more decimal digits.  The string must contain at least one
     * digit in either the integer or the fraction.  The number formed
     * by the sign, the integer and the fraction is referred to as the
     * <i>significand</i>.
     *
     * <p>The exponent consists of the character {@code 'e'}
     * (<code>'&#92;u0065'</code>) or {@code 'E'} (<code>'&#92;u0045'</code>)
     * followed by one or more decimal digits.
     *
     * <p>More formally, the strings this constructor accepts are
     * described by the following grammar:
     * <blockquote>
     * <dl>
     * <dt><i>DecimalString:</i>
     * <dd><i>Sign<sub>opt</sub> Significand Exponent<sub>opt</sub></i>
     * <dt><i>Sign:</i>
     * <dd>{@code +}
     * <dd>{@code -}
     * <dt><i>Significand:</i>
     * <dd><i>IntegerPart</i> {@code .} <i>FractionPart<sub>opt</sub></i>
     * <dd>{@code .} <i>FractionPart</i>
     * <dd><i>IntegerPart</i>
     * <dt><i>IntegerPart:</i>
     * <dd><i>Digits</i>
     * <dt><i>FractionPart:</i>
     * <dd><i>Digits</i>
     * <dt><i>Exponent:</i>
     * <dd><i>ExponentIndicator SignedInteger</i>
     * <dt><i>ExponentIndicator:</i>
     * <dd>{@code e}
     * <dd>{@code E}
     * <dt><i>SignedInteger:</i>
     * <dd><i>Sign<sub>opt</sub> Digits</i>
     * <dt><i>Digits:</i>
     * <dd><i>Digit</i>
     * <dd><i>Digits Digit</i>
     * <dt><i>Digit:</i>
     * <dd>any character for which {@link Character#isDigit}
     * returns {@code true}, including 0, 1, 2 ...
     * </dl>
     * </blockquote>
     *
     * <p>The scale of the decimal number represented by the returned
     * {@code Rational} will be the number of digits in the fraction,
     * or zero if the string contains no decimal point, subject to adjustment
     * for any exponent; if the string contains an exponent, the exponent is
     * subtracted from the scale.  The value of the resulting scale
     * must lie between {@code Integer.MIN_VALUE} and
     * {@code Integer.MAX_VALUE}, inclusive.
     *
     * <p>The character-to-digit mapping is provided by {@link
     * java.lang.Character#digit} set to convert to radix 10.  The
     * String may not contain any extraneous characters (whitespace,
     * for example).
     *
     * <p><b>Examples:</b><br>
     * The value of the returned {@code Rational} is equal to
     * <i>significand</i> &times; 10<sup>&nbsp;<i>exponent</i></sup>.
     * For each string on the left, the resulting representation
     * [{@code denominator}/{@code numerator}] is shown on the right.
     * <pre>
     * "0"            [0/1]
     * "0.00"         [0/1]
     * "123"          [123/1]
     * "-123"         [-123/1]
     * "1.23E3"       [1230/1]
     * "1.23E+3"      [1230/1]
     * "12.3E+7"      [123000000/1]
     * "12.0"         [12/1]
     * "12.3"         [123/10]
     * "0.00123"      [123/100000]
     * "-1.23E-12"    [-123/100000000000000]
     * "1234.5E-4"    [2469/20000]
     * "0E+7"         [0/1]
     * "-0"           [0/1]
     * </pre>
     *
     * @apiNote For values other than {@code float} and
     * {@code double} NaN and &plusmn;Infinity, this constructor is
     * compatible with the values returned by {@link Float#toString}
     * and {@link Double#toString}.  This is generally the preferred
     * way to convert a {@code float} or {@code double} into a
     * Rational, as it doesn't suffer from the unpredictability of
     * the {@link #Rational(double)} constructor.
     *
     * @param val String representation of a decimal number.
     *
     * @throws NumberFormatException if {@code val} is not a valid
     *         representation of a decimal number.
     */
    public Rational(String val) {
        this(val.toCharArray(), 0, val.length());
    }

    /**
     * Returns a Rational whose value is represented by the fraction
     * with the specified numerator and denominator.
     * 
     * @param num the numerator of the fraction to represent
     * @param den the denominator of the fraction to represent
     * @return a Rational whose value is represented by the fraction
     *         with the specified numerator and denominator
     * @throws ArithmeticException if the specified denominator is zero
     */
    public static Rational valueOf(long num, long den) {
        if (den == 0)
            throw new ArithmeticException("Denominator is zero");

        if (num == 0)
            return ZERO;

        return valueOf(Math.signum(num) * Math.signum(den), BigInteger.valueOf(Math.abs(num)), BigInteger.valueOf(Math.abs(den)));
    }

    /**
     * Returns a Rational whose value is represented by the fraction
     * with the specified numerator and denominator.
     * 
     * @param num the numerator of the fraction to represent
     * @param den the denominator of the fraction to represent
     * @return a Rational whose value is represented by the fraction
     *         with the specified numerator and denominator
     * @throws ArithmeticException if the specified denominator is zero
     */
    public static Rational valueOf(BigInteger num, BigInteger den) {
        if (den.signum == 0)
            throw new ArithmeticException("Denominator is zero");

        if (num.signum == 0)
            return ZERO;

        return valueOf(num.signum * den.signum, num.abs(), den.abs());
    }

    /**
     * Returns a Rational whose value is represented by the specified parameters.
     * Assumes that {@code signum != 0} and that the denominator and
     * the numerator are positive.
     */
    private static Rational valueOf(int sign, BigInteger num, BigInteger den) {
        // An optimization if the fraction is unitary or an integer
        if (num.equals(BigInteger.ONE) || den.equals(BigInteger.ONE))
            return new Rational(sign, num, den);

        BigInteger[] frac = simplify(num, den);
        return new Rational(sign, frac[0], frac[1]);
    }

    /**
     * Constructs a new Rational with the specified values.
     * Assumes that the passed values are always valid.
     */
    private Rational(int sign, BigInteger num, BigInteger den) {
        signum = sign;
        numerator = num;
        denominator = den;
    }

    /**
     * Returns the simplification of the specified fraction.
     */
    private static BigInteger[] simplify(BigInteger num, BigInteger den) {
        BigInteger gcd = num.gcd(den);
        return new BigInteger[] { num.divide(gcd), den.divide(gcd) };
    }

    /**
     * Returns the signum function of this {@code Rational}.
     *
     * @return -1, 0, or 1 as the value of this {@code Rational}
     *         is negative, zero, or positive.
     */
    public int signum() {
        return signum;
    }

    /**
     * Returns the least non-negative denominator necessary to represent this
     * Rational.
     * 
     * @return the least non-negative denominator necessary to represent this
     *         Rational.
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
     * {@code Rational} will be discarded. Note that this
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
     * checking for lost information. An exception is thrown if this
     * {@code Rational} has a nonzero fractional part.
     *
     * @return this {@code Rational} converted to a {@code BigInteger}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *                             fractional part
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
     * @param mc the context to use.
     * @return this {@code Rational} converted to a {@code BigDecimal}.
     */
    public BigDecimal toBigDecimal(MathContext mc) {
        BigDecimal absVal = new BigDecimal(numerator).divide(denominator, mc);
        return signum == -1 ? absVal.negate() : absVal;
    }

    /**
     * Converts this {@code Rational} to a {@code BigDecimal},
     * checking for lost information. An exception is thrown if
     * a nonzero fractional part is discarded.
     *
     * @return this {@code Rational} converted to a {@code BigDecimal}.
     * @throws ArithmeticException if a nonzero fractional part is discarded.
     * @since 1.5
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
     * Double#POSITIVE_INFINITY} as appropriate. Note that even when
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
     * Float#POSITIVE_INFINITY} as appropriate. Note that even when
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
        return toBigInteger().intValue();
    }

    /**
     * Converts this {@code Rational} to an {@code int}, checking
     * for lost information. If this {@code Rational} has a
     * nonzero fractional part or is out of the possible range for an
     * {@code int} result then an {@code ArithmeticException} is
     * thrown.
     *
     * @return this {@code Rational} converted to an {@code int}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *                             fractional part, or will not fit in an
     *                             {@code int}.
     */
    public int intValueExact() {
        return toBigIntegerExact().intValueExact();
    }

    /**
     * Converts this {@code Rational} to a {@code short}, checking
     * for lost information. If this {@code Rational} has a
     * nonzero fractional part or is out of the possible range for a
     * {@code short} result then an {@code ArithmeticException} is
     * thrown.
     *
     * @return this {@code Rational} converted to a {@code short}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *                             fractional part, or will not fit in a
     *                             {@code short}.
     */
    public short shortValueExact() {
        return toBigIntegerExact().shortValueExact();
    }

    /**
     * Converts this {@code Rational} to a {@code byte}, checking
     * for lost information. If this {@code Rational} has a
     * nonzero fractional part or is out of the possible range for a
     * {@code byte} result then an {@code ArithmeticException} is
     * thrown.
     *
     * @return this {@code Rational} converted to a {@code byte}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *                             fractional part, or will not fit in a
     *                             {@code byte}.
     */
    public byte byteValueExact() {
        return toBigIntegerExact().byteValueExact();
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
     * Converts this {@code Rational} to a {@code long}, checking
     * for lost information. If this {@code Rational} has a
     * nonzero fractional part or is out of the possible range for a
     * {@code long} result then an {@code ArithmeticException} is
     * thrown.
     *
     * @return this {@code Rational} converted to a {@code long}.
     * @throws ArithmeticException if {@code this} has a nonzero
     *                             fractional part, or will not fit in a
     *                             {@code long}.
     */
    public long longValueExact() {
        return toBigIntegerExact().longValueExact();
    }

    /**
     * Compares this Rational with the specified Object for equality.
     *
     * @param obj Object to which this Rational is to be compared.
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
        return signum == 0 ? 0 : signum * Objects.hash(numerator, denominator);
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

        final int absComp;

        // compare absolute values
        if (denominator.equals(val.denominator))
            absComp = numerator.compareTo(val.numerator); // a/b < c/b <=> a < c
        else if (numerator.equals(val.numerator))
            absComp = val.denominator.compareTo(denominator); // a/b < a/c <=> c < b
        else {
            // compare to one
            int unitComp = numerator.compareTo(denominator);
            int valUnitComp = val.numerator.compareTo(val.denominator);

            if (unitComp != valUnitComp)
                absComp = unitComp > valUnitComp ? 1 : -1;
            else {
                // compare using least common denominator
                // trying to pospone the overflow as as late as possible
                BigInteger gcd = denominator.gcd(val.denominator);
                BigInteger lcdNum = lcdNumerator(val.denominator, gcd);
                BigInteger valLcdNum = val.lcdNumerator(denominator, gcd);
                absComp = lcdNum.compareTo(valLcdNum);
            }
        }

        return signum * absComp; // adjust comparison with signum
    }

    /**
     * Computes the numerator of this rational, relative to the least common
     * denominator
     * of {@code this.denominator} and {@code otherDenominator}
     * 
     * @param gcd the greatest common divisor of
     *            {@code this.denominator} and {@code otherDenominator}
     */
    private BigInteger lcdNumerator(BigInteger otherDenominator, BigInteger gcd) {
        // lcm(a, b) == a * b / gcd(a, b) => n/a == n * (b / gcd(a, b)) / lcm(a, b)
        return otherDenominator.divide(gcd).multiply(numerator);
    }
}