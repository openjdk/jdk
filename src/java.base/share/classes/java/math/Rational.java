
package java.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jdk.internal.math.DoubleConsts;
import jdk.internal.math.FloatConsts;

/**
 * Immutable, infinite-precision signed rational numbers.
 *
 * <p>
 * The {@code Rational} class provides operations for arithmetic, rounding,
 * comparison, hashing, and format conversion. The {@link #toString} method
 * provides a canonical representation of a {@code Rational}.
 *
 * <p>
 * All the calculations performed have an exact result, except for the square
 * and nth roots, in which the user can specify the rounding behavior.
 *
 * <p>
 * When a {@code MathContext} object is supplied with a precision setting of 0
 * (for example, {@link MathContext#UNLIMITED}), arithmetic operations are
 * exact, as are the arithmetic methods which take no {@code MathContext}
 * object. As a corollary of computing the exact result, the rounding mode
 * setting of a {@code MathContext} object with a precision setting of 0 is not
 * used and thus irrelevant. In the case of square and nth root, the exact
 * result could not be represented as a fraction; for example, sqrt(2). If the
 * root has a nonterminating decimal expansion and the operation is specified to
 * return an exact result, an {@code ArithmeticException} is thrown. Otherwise,
 * the exact result is returned, as done for other operations.
 *
 * <p>
 * In general the rounding modes and precision setting determine how the
 * operation returns results with a limited number of digits when the exact
 * result has more digits (perhaps infinitely many) than the number of digits
 * returned.
 *
 * First, the total number of digits to return is specified by the
 * {@code MathContext}'s {@code precision} setting; this determines the result's
 * <i>precision</i>. The digit count starts from the leftmost nonzero digit of
 * the exact result. The rounding mode determines how any discarded trailing
 * digits affect the returned result.
 *
 * <p>
 * The operation is carried out as though an exact intermediate result were
 * first calculated and then rounded to the number of digits specified by the
 * precision setting (if necessary), using the selected rounding mode. If the
 * exact result is not returned, some digit positions of the exact result are
 * discarded. When rounding increases the magnitude of the returned result, it
 * is possible for a new digit position to be created by a carry propagating to
 * a leading {@literal "9"} digit. For example, rounding the value 999.9 to
 * three digits rounding up would be numerically equal to one thousand. In such
 * cases, the new {@literal "1"} is the leading digit position of the returned
 * result.
 *
 * <p>
 * For methods and constructors with a {@code MathContext} parameter, if the
 * result is inexact but the rounding mode is {@link RoundingMode#UNNECESSARY
 * UNNECESSARY}, an {@code ArithmeticException} will be thrown.
 *
 * <p>
 * Before rounding, the precision of the logical exact intermediate result is
 * the preferred precision for the operation. If the exact numerical result
 * cannot be represented in {@code precision} digits, rounding selects the set
 * of digits to return and the precision of the result is reduced from the
 * precision of the intermediate result to the least precision which can
 * represent the {@code precision} digits actually returned. If the exact result
 * can be represented with at most {@code precision} digits, the representation
 * of the result with the precision closest to the preferred precision is
 * returned.
 *
 * <p>
 * One operation is provided for manipulating the precision of a
 * {@code Rational}: the rounding operation. Rounding operation {@link #round
 * round}) returns a {@code Rational} whose value is approximately (or exactly)
 * equal to that of the operand, but whose precision is the specified value;
 * that is, it decrease the precision of the stored number with minimal effect
 * on its value.
 *
 * <p>
 * Rational must support fractional values in the range
 * -2<sup>{@code Integer.MAX_VALUE}</sup> (exclusive) to
 * +2<sup>{@code Integer.MAX_VALUE}</sup> (exclusive) and may support values
 * outside of that range.
 *
 * The spacing between two neighbor supported values must be less than
 * 2<sup>-{@code Integer.MAX_VALUE}</sup>.
 *
 * An {@code ArithmeticException} is thrown when a Rational constructor or
 * method would generate a value outside of the supported range.
 *
 * <p>
 * For the sake of brevity and clarity, pseudo-code is used throughout the
 * descriptions of {@code Rational} methods. The pseudo-code expression
 * {@code (i + j)} is shorthand for "a {@code Rational} whose value is that of
 * the {@code Rational} {@code i} added to that of the {@code Rational}
 * {@code j}." The pseudo-code expression {@code (i == j)} is shorthand for
 * "{@code true} if and only if the {@code Rational} {@code i} represents the
 * same value as the {@code Rational} {@code j}." Other pseudo-code expressions
 * are interpreted similarly. Square brackets are used to represent the
 * particular fraction defining a @code Rational} value; for example [19/100] is
 * the {@code Rational} numerically equal to 0.19.
 *
 * <p>
 * All methods and constructors for this class throw
 * {@code NullPointerException} when passed a {@code null} object reference for
 * any input parameter.
 *
 * @apiNote
 *          <h2>Relation to IEEE 754 Decimal Arithmetic</h2>
 *
 *          Starting with its 2008 revision, the <cite>IEEE 754 Standard for
 *          Floating-point Arithmetic</cite> has covered decimal formats and
 *          operations. While there are broad similarities in the decimal
 *          arithmetic defined by IEEE 754 and by this class, there are notable
 *          differences as well. The fundamental similarity shared by {@code
 * Rational} and IEEE 754 decimal arithmetic is the conceptual operation of
 *          computing the mathematical infinitely precise real number value of
 *          an operation and then mapping that real number to a representable
 *          fractional value under a <em>rounding policy</em>. The rounding
 *          policy is called a {@linkplain RoundingMode rounding mode} for
 *          {@code Rational} and called a rounding-direction attribute in IEEE
 *          754-2019. When the exact value is not representable, the rounding
 *          policy determines which of the two representable decimal values
 *          bracketing the exact value is selected as the computed result. The
 *          notion of a <em>preferred precision</em> is also shared by both
 *          systems.
 *
 *          <p>
 *          For differences, IEEE 754 includes several kinds of values not
 *          modeled by {@code Rational} including negative zero, signed
 *          infinities, and NaN (not-a-number). IEEE 754 defines formats, which
 *          are parameterized by base (binary or decimal), number of digits of
 *          precision, and exponent range. A format determines the set of
 *          representable values. Most operations accept as input one or more
 *          values of a given format and produce a result in the same format.
 *          {@code Rational} values do not have a format in the same sense; all
 *          values have a unique representation. Instead, for the
 *          {@code Rational} operations taking a {@code MathContext} parameter,
 *          if the {@code MathContext} has a nonzero precision, the set of
 *          possible representable values for the result is determined by the
 *          precision of the {@code MathContext} argument. For example in
 *          {@code Rational}, if a nonzero three-digit number is square rooted
 *          in the context of a {@code MathContext} object having a precision of
 *          three, the result will have at most three digits (assuming no
 *          overflow or underflow, etc.).
 *
 *          <p>
 *          The rounding policies implemented by {@code Rational} operations
 *          indicated by {@linkplain RoundingMode rounding modes} are a proper
 *          superset of the IEEE 754 rounding-direction attributes.
 *
 *          <p>
 *          {@code Rational} arithmetic will most resemble IEEE 754 decimal
 *          arithmetic if a {@code MathContext} corresponding to an IEEE 754
 *          decimal format, such as {@linkplain MathContext#DECIMAL64 decimal64}
 *          or {@linkplain MathContext#DECIMAL128 decimal128} is used to round
 *          all starting values and intermediate operations. The numerical
 *          values computed can differ if the exponent range of the IEEE 754
 *          format being approximated is exceeded since a {@code
 * MathContext} does not constrain the precision of {@code Rational} results.
 *          Operations that would generate a NaN or exact infinity, such as
 *          dividing by zero, throw an {@code ArithmeticException} in
 *          {@code Rational} arithmetic.
 *
 * @see BigInteger
 * @see BigDecimal
 * @see MathContext
 * @see RoundingMode
 * @see java.util.SortedMap
 * @see java.util.SortedSet
 * @see <a href="https://standards.ieee.org/ieee/754/6210/"> <cite>IEEE Standard
 *      for Floating-Point Arithmetic</cite></a>
 *
 * @author Fabio Romano
 * @since 21
 */
public class Rational extends Number implements Comparable<Rational> {
    /**
     * The signum of this Rational: -1 for negative, 0 for zero, or 1 for positive.
     * Note that the Rational zero <em>must</em> have a signum of 0. This is
     * necessary to ensures that there is exactly one representation for each
     * Rational value.
     */
    private final int signum;

    /**
     * The integer part of the absolute value of this {@code Rational}.
     */
    private final BigInteger floor;

    /**
     * The least non-negative numerator necessary to represent the fractional part
     * of this Rational.
     */
    private final BigInteger numerator;

    /**
     * The least positive denominator necessary to represent the fractional part of
     * this Rational.
     */
    private final BigInteger denominator;
    
    /**
     * Used to store the canonical string representation, if computed.
     */
    private transient String stringCache;

    /** use serialVersionUID from JDK 21 for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = 669815459941734258L;

    // Constants
    /**
     * The value 0.
     */
    public static final Rational ZERO = new Rational(0, BigInteger.ZERO);

    /**
     * The value 1.
     */
    public static final Rational ONE = new Rational(1, BigInteger.ONE);

    /**
     * The value 2.
     */
    public static final Rational TWO = new Rational(1, BigInteger.TWO);

    /**
     * The value 10.
     */
    public static final Rational TEN = new Rational(1, BigInteger.TEN);

    /**
     * The value 0.1.
     */
    private static final Rational ONE_TENTH = new Rational(1, BigInteger.ZERO, BigInteger.ONE, BigInteger.TEN);

    /**
     * The value 0.5.
     */
    private static final Rational ONE_HALF = new Rational(1, BigInteger.ZERO, BigInteger.ONE, BigInteger.TWO);

    // Constructors

    /**
     * Translates a {@code double} into a {@code Rational} which is the exact
     * fractional representation of the {@code double}'s binary floating-point
     * value.
     * <p>
     * <b>Notes:</b>
     * <ol>
     * <li>The results of this constructor can be somewhat unpredictable. One might
     * assume that writing {@code new Rational(0.1)} in Java creates a
     * {@code Rational} which is exactly equal to 0.1 (1/10), but it is actually
     * equal to 0.1000000000000000055511151231257827021181583404541015625. This is
     * because 0.1 cannot be represented exactly as a {@code double} (or, for that
     * matter, as a binary fraction of any finite length). Thus, the value that is
     * being passed <em>in</em> to the constructor is not exactly equal to 0.1,
     * appearances notwithstanding.
     *
     * <li>The {@code String} constructor, on the other hand, is perfectly
     * predictable: writing {@code new Rational("0.1")} creates a {@code Rational}
     * which is <em>exactly</em> equal to 0.1, as one would expect. Therefore, it is
     * generally recommended that the {@linkplain #Rational(String) String
     * constructor} be used in preference to this one.
     *
     * <li>When a {@code double} must be used as a source for a {@code Rational},
     * note that this constructor provides an exact conversion; it does not give the
     * same result as converting the {@code double} to a {@code String} using the
     * {@link Double#toString(double)} method and then using the
     * {@link #Rational(String)} constructor. To get that result, use the
     * {@code static} {@link #valueOf(double)} method.
     * </ol>
     *
     * @param val {@code double} value to be converted to {@code Rational}.
     * @throws NumberFormatException if {@code val} is infinite or NaN.
     */
    public Rational(double val) {
        if (Double.isInfinite(val) || Double.isNaN(val))
            throw new NumberFormatException("Infinite or NaN");
        // Translate the double into sign, exponent and significand, according
        // to the formulae in Double.longBitsToDouble(long).
        long valBits = Double.doubleToLongBits(val);
        int sign = ((valBits >> 63) == 0 ? 1 : -1);
        int exponent = (int) ((valBits & DoubleConsts.EXP_BIT_MASK) >> 52);
        long significand = (exponent == 0 ? (valBits & DoubleConsts.SIGNIF_BIT_MASK) << 1
                : (valBits & DoubleConsts.SIGNIF_BIT_MASK) | (1L << 52));
        exponent -= 1075;
        // At this point, val == sign * significand * 2**exponent.

        if (significand == 0) {
            this.signum = 0;
            this.floor = BigInteger.ZERO;
            this.numerator = BigInteger.ZERO;
            this.denominator = BigInteger.ONE;
        } else {
            this.signum = sign;
            
            if (exponent < 0) {
                // Simplify even significands
                int shift = Math.min(-exponent, Long.numberOfTrailingZeros(significand));
                // shift can't be greater than 63
                significand >>>= shift;
                exponent += shift;
                // now exponent <= 0
                // and the significand and the denominator are relative primes
                // the significand is odd or the denominator is one
                long quot = exponent > -64 ? significand >>> -exponent : 0L; // avoid mod 64 shifting
                this.floor = BigInteger.valueOf(quot);
                this.numerator = BigInteger.valueOf(significand - (quot << -exponent));
                this.denominator = BigInteger.ONE.shiftLeft(-exponent);
            } else { // integer value
                this.floor = BigInteger.valueOf(significand).shiftLeft(exponent);
                this.numerator = BigInteger.ZERO;
                this.denominator = BigInteger.ONE;
            }
        }
    }

    /**
     * Translates a {@code long} into a {@code Rational}.
     *
     * @param val {@code long} value to be converted to {@code Rational}.
     */
    public Rational(long val) {
        this(BigInteger.valueOf(val));
    }

    /**
     * Translates a {@code BigInteger} into a {@code Rational}.
     *
     * @param val {@code BigInteger} value to be converted to {@code Rational}.
     */
    public Rational(BigInteger val) {
        this(val.signum, BigDecimal.toStrictBigInteger(val).abs());
    }

    /**
     * Translates a {@code BigInteger} into a {@code Rational}, with the specified
     * signum. Assumes that the {@code BigInteger} is non-negative and the signum
     * is correct.
     *
     * @param val {@code BigInteger} value to be converted to {@code Rational}.
     */
    private Rational(int sign, BigInteger val) {
        this(sign, val, BigInteger.ZERO, BigInteger.ONE);
    }

    /**
     * Translates a character array representation of a decimal number into a
     * {@code Rational}, accepting the same sequence of characters as the
     * {@link #Rational(String)} constructor, while allowing a sub-array to be
     * specified.
     *
     * @implNote If the sequence of characters is already available within a
     *           character array, using this constructor is faster than converting
     *           the {@code char} array to string and using the
     *           {@code Rational(String)} constructor.
     *
     * @param in     {@code char} array that is the source of characters.
     * @param offset first character in the array to inspect.
     * @param len    number of characters to consider.
     * @throws NumberFormatException if {@code in} is not a valid representation of
     *                               a decimal number or the defined subarray is not
     *                               wholly within {@code in}.
     */
    public Rational(char[] in, final int offset, final int len) {
        // protect against huge length, len == 0, negative values, and integer overflow
        try {
            Objects.checkFromIndexSize(offset, len, in.length);
        } catch (IndexOutOfBoundsException e) {
            throw new NumberFormatException
                ("Bad offset or len arguments for char[] input.");
        }
        
        if (len == 0)
            throw new NumberFormatException("Empty input.");

        // This is the primary String to Rational constructor; all
        // incoming strings end up here; it generates at most six
        // intermediate (temporary) object (strings and BigIntegers)
        // to store fraction components and the exponent.
        
        // Use locals for fields values until completion
        final BigInteger fl; // record floor
        BigInteger num, den; // record numerator and denominator
        
        final int end = offset + len;
        
        // handle the sign
        final boolean isneg = in[offset] == '-'; // leading minus means negative
        final int intStart = offset + (isneg || in[offset] == '+' ? 1 : 0);
        
        int i;
        for (i = intStart; i < end && in[i] != 'e' && in[i] != 'E'; i++);
        final int signifEnd = i;
        
        for (i = intStart; i < signifEnd && in[i] != '.'; i++);
        final int intEnd = i;
        
        // handle integer part
        if (intStart == intEnd) { // no integer part
            fl = BigInteger.ZERO;
        } else {
            // check for unexpected sign character
            char c = in[intStart];
            checkDigit(c, "Invalid character for integer part: " + c);
            
            try {
                fl = new BigInteger(new String(in, intStart, intEnd - intStart));
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Invalid integer part: " + e.getMessage());
            }
        }
        
        // handle fraction part
        final int fracStart = intEnd + 1;
        if (fracStart >= signifEnd) { // no fraction
            if (intStart == intEnd) // no integer part
                throw new NumberFormatException("No such significand.");
            
            num = BigInteger.ZERO;
            den = BigInteger.ONE;
        } else { // in[intEnd] == '.'
            for (i = fracStart; i < signifEnd && in[i] != '('; i++);
            final int prepEnd = i;
            
            // handle preperiod
            final String preperiod = new String(in, fracStart, prepEnd - fracStart);
            final BigInteger prepInt;
            if (preperiod.length() == 0) { // no preperiod
                prepInt = BigInteger.ZERO;
            } else {
                // check for unexpected sign character
                char c = preperiod.charAt(0);
                checkDigit(c, "Invalid character for preperiod: " + c);
                
                try {
                    prepInt = new BigInteger(preperiod);
                } catch (NumberFormatException e) {
                    throw new NumberFormatException("Invalid preperiod: " + e.getMessage());
                }
            }
            
            // handle period
            final int periodLen;
            if (prepEnd == signifEnd) { // no period
                num = prepInt;
                periodLen = 0;
            } else { // in[prepEnd] == '('
                // check for incorrect position of closing bracket
                if (in[signifEnd - 1] != ')')
                    throw new NumberFormatException("Incorrect position or missing closing bracket for period.");
                
                periodLen = (signifEnd - 1) - (prepEnd + 1);
                // periodLen >= 0
                // since prepEnd <= (signifEnd - 1) && in[signifEnd - 1] == ')'
                if (periodLen == 0)
                    throw new NumberFormatException("Round brackets surrounding empty period.");
                
                final String period = new String(in, prepEnd + 1, periodLen);
                // check for unexpected sign character
                char c = period.charAt(0);
                checkDigit(c, "Invalid character for period: " + c);
                
                try {
                    num = new BigInteger(preperiod + period).subtract(prepInt);
                } catch (NumberFormatException e) {
                    throw new NumberFormatException("Invalid period: " + e.getMessage());
                }
            }
            
            // compute the denominator
            if (num.signum == 0) { // no fraction
                den = BigInteger.ONE;
            } else {
                // 10^n == 2^n * 5^n
                int twoExp = preperiod.length();
                final AtomicLong fiveExp = new AtomicLong(twoExp);
                // remove common powers of two
                final int shift = Math.min(twoExp, num.getLowestSetBit());
                num = num.shiftRight(shift);
                twoExp -= shift;
                // remove common powers of five
                num = removePowersOfFive(num, fiveExp);
                
                // set the prefix of the denominator
                if (periodLen == 0) { // no period
                    den = BigInteger.ONE;
                } else {
                    den = new BigInteger("9".repeat(periodLen));
                    // simplify before multiply by (2^twoExp * 5^fiveExp)
                    BigInteger[] frac = simplify(num, den);
                    num = frac[0];
                    den = frac[1];
                }
                // multiply the prefix by (2^twoExp * 5^fiveExp)
                den = den.shiftLeft(twoExp).multiply(bigFiveToThe(fiveExp.intValue()));
            }
        }
        
        // set fields values
        this.signum = fl.signum == 0 && num.signum == 0 ? 0 : (isneg ? -1 : 1);
        if (signifEnd == end) { // no exponent
            this.floor = fl;
            this.numerator = num;
            this.denominator = den;
        } else { // handle exponent
            final int exp;
            try {
                exp = Integer.parseInt(new String(in, signifEnd + 1, end - (signifEnd + 1)));
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Invalid exponent: " + e.getMessage());
            }
            final Rational res = new Rational(this.signum, fl, num, den).scaleByPowerOfTen(exp);
            
            this.floor = res.floor;
            this.numerator = res.numerator;
            this.denominator = res.denominator;
        }
    }

    private void checkDigit(char c, String message) throws NumberFormatException {
        if (Character.digit(c, 10) == -1)
            throw new NumberFormatException(message);
    }

    /**
     * Translates a character array representation of a decimal number into a
     * {@code Rational}, accepting the same sequence of characters as the
     * {@link #Rational(String)} constructor.
     *
     * @implNote If the sequence of characters is already available as a character
     *           array, using this constructor is faster than converting the
     *           {@code char} array to string and using the {@code Rational(String)}
     *           constructor.
     *
     * @param in {@code char} array that is the source of characters.
     * @throws NumberFormatException if {@code in} is not a valid representation of
     *                               a decimal number.
     */
    public Rational(char[] in) {
        this(in, 0, in.length);
    }

    /**
     * Translates the string representation of a {@code Rational} into a
     * {@code Rational}. The string representation consists of an optional sign,
     * {@code '+'} (<code> '&#92;u002B'</code>) or {@code '-'}
     * (<code>'&#92;u002D'</code>), followed by a sequence of zero or more decimal
     * digits ("the integer"), optionally followed by a fraction, optionally
     * followed by an exponent.
     *
     * <p>
     * The fraction consists of a decimal point followed by zero or more decimal
     * digits ("the preperiod"), optionally followed by zero or more decimal digits
     * surrounded by round brackets {@code '(', ')'} ("the period"). The string must
     * contain at least one digit in either the integer or the fraction. The number
     * formed by the sign, the integer and the fraction is referred to as the
     * <i>significand</i>.
     *
     * <p>
     * The exponent consists of the character {@code 'e'}
     * (<code>'&#92;u0065'</code>) or {@code 'E'} (<code>'&#92;u0045'</code>)
     * followed by one or more decimal digits.
     *
     * <p>
     * More formally, the strings this constructor accepts are described by the
     * following grammar: <blockquote>
     * <dl>
     * <dt><i>RationalString:</i>
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
     * <dd><i>Preperiod Period<sub>opt</sub></i>
     * <dd><i>Period</i>
     * <dd><i>Preperiod</i>
     * <dt><i>Preperiod:</i>
     * <dd><i>Digits</i>
     * <dt><i>Period:</i>
     * <dd>{@code (} <i>Digits</i> {@code )}
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
     * <dd>any character for which {@link Character#isDigit} returns {@code true},
     * including 0, 1, 2 ...
     * </dl>
     * </blockquote>
     *
     * <p>
     * The value of the expopnent must lie between {@code Integer.MIN_VALUE} and
     * {@code Integer.MAX_VALUE}, inclusive.
     *
     * <p>
     * The character-to-digit mapping is provided by
     * {@link java.lang.Character#digit} set to convert to radix 10. The String may
     * not contain any extraneous characters (whitespace, for example).
     *
     * @apiNote For values other than {@code float} and {@code double} NaN and
     *          &plusmn;Infinity, this constructor is compatible with the values
     *          returned by {@link Float#toString} and {@link Double#toString}. This
     *          is generally the preferred way to convert a {@code float} or
     *          {@code double} into a Rational, as it doesn't suffer from the
     *          unpredictability of the {@link #Rational(double)} constructor.
     *
     * @param val String representation of {@code Rational}.
     *
     * @throws NumberFormatException if {@code val} is not a valid representation of
     *                               a {@code Rational}.
     */
    public Rational(String val) {
        this(val.toCharArray(), 0, val.length());
    }

    /**
     * Accept no subclasses.
     */
    private static BigDecimal toStrictBigDecimal(BigDecimal val) {
        return (val.getClass() == BigDecimal.class) ? val : new BigDecimal(val.toString());
    }

    /**
     * Translates a {@code BigDecimal} into a {@code Rational}.
     *
     * @param val {@code BigDecimal} value to be converted to {@code Rational}.
     * @return a {@code Rational} whose value is equal to {@code val}.
     */
    public static Rational valueOf(BigDecimal val) {
        val = toStrictBigDecimal(val);

        if (val.signum() == 0)
            return ZERO;

        int scale = val.scale();
        Rational unscaled = new Rational(val.unscaledValue());

        if (scale == 0)
            return unscaled;

        // Possible int overflow in (-scale) is not a trouble,
        // because leftScaleByPowerOfTen considers its argument unsigned
        return scale < 0 ? unscaled.leftScaleByPowerOfTen(-scale) : unscaled.rightScaleByPowerOfTen(scale);
    }

    /**
     * Translates a {@code double} into a {@code Rational}, using the
     * {@code double}'s canonical string representation provided by the
     * {@link Double#toString(double)} method.
     *
     * @apiNote This is generally the preferred way to convert a {@code double} (or
     *          {@code float}) into a {@code Rational}, as the value returned is
     *          equal to that resulting from constructing a {@code Rational} from
     *          the result of using {@link Double#toString(double)}.
     *
     * @param val {@code double} to convert to a {@code Rational}.
     * @return a {@code Rational} whose value is equal to or approximately equal to
     *         the value of {@code val}.
     * @throws NumberFormatException if {@code val} is infinite or NaN.
     */
    public static Rational valueOf(double val) {
        return new Rational(Double.toString(val));
    }

    /**
     * Returns a Rational whose value is equal to the fraction with the
     * specified numerator and denominator.
     *
     * @param num the numerator of the fraction to represent
     * @param den the denominator of the fraction to represent
     * @return a Rational whose value equal to by the fraction with the
     *         specified numerator and denominator
     * @throws ArithmeticException if the specified denominator is zero
     */
    public static Rational valueOf(BigDecimal num, BigDecimal den) {
        if (den.signum() == 0)
            throw new ArithmeticException("Denominator is zero");

        if (num.signum() == 0)
            return ZERO;

        return valueOf(num).divide(valueOf(den));
    }

    /**
     * Returns a Rational whose value is equal to the fraction with the
     * specified numerator and denominator.
     *
     * @param num the numerator of the fraction to represent
     * @param den the denominator of the fraction to represent
     * @return a Rational whose value is equal to the fraction with the
     *         specified numerator and denominator
     * @throws ArithmeticException if the specified denominator is zero
     */
    public static Rational valueOf(long num, long den) {
        if (den == 0)
            throw new ArithmeticException("Denominator is zero");

        if (num == 0)
            return ZERO;

        return valueOf((int) (Math.signum(num) * Math.signum(den)), BigInteger.valueOf(num).abs(),
                BigInteger.valueOf(den).abs());
    }

    /**
     * Returns a Rational whose value is equal to the fraction with the
     * specified numerator and denominator.
     *
     * @param num the numerator of the fraction to represent
     * @param den the denominator of the fraction to represent
     * @return a Rational whose value is equal to the fraction with the
     *         specified numerator and denominator
     * @throws ArithmeticException if the specified denominator is zero
     */
    public static Rational valueOf(BigInteger num, BigInteger den) {
        if (den.signum == 0)
            throw new ArithmeticException("Denominator is zero");

        if (num.signum == 0)
            return ZERO;

        return valueOf(num.signum * den.signum, BigDecimal.toStrictBigInteger(num).abs(),
                BigDecimal.toStrictBigInteger(den).abs());
    }

    /**
     * Returns a Rational whose value is equal to the specified parameters.
     * Assumes that {@code signum != 0} and that the denominator and the numerator
     * are positive.
     */
    private static Rational valueOf(int sign, BigInteger num, BigInteger den) {
        BigInteger[] quotAndRem = num.divideAndRemainder(den);
        BigInteger[] frac = simplify(quotAndRem[1], den);
        return new Rational(sign, quotAndRem[0], frac[0], frac[1]);
    }

    /**
     * Returns a Rational whose value is represented by the specified parameters.
     * Assumes that {@code signum != 0}, the denominator and the numerator are
     * positive and {@code num < den}
     */
    private static Rational valueOf(int sign, BigInteger floor, BigInteger num, BigInteger den) {
        BigInteger[] frac = simplify(num, den);
        return new Rational(sign, floor, frac[0], frac[1]);
    }

    /**
     * Constructs a new Rational with the specified values. Assumes that the specified
     * values are correct.
     */
    private Rational(int sign, BigInteger floor, BigInteger num, BigInteger den) {
        this.signum = sign;
        this.floor = floor;
        this.numerator = num;
        this.denominator = den;
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
     * @return -1, 0, or 1 as the value of this {@code Rational} is negative, zero,
     *         or positive.
     */
    public int signum() {
        return signum;
    }

    /**
     * Returns the integer part of the absolute value of this {@code Rational}.
     *
     * @return {@code floor(abs(this))}.
     */
    public BigInteger absFloor() {
        return floor;
    }

    /**
     * Returns the difference between the absolute value of this {@code Rational}
     * and {@code absFloor(this)}.
     *
     * @return {@code abs(this) - absFloor(this)}
     */
    public Rational absFraction() {
        return new Rational(numerator.signum, BigInteger.ZERO, numerator, denominator);
    }

    /**
     * Returns the least non-negative numerator necessary to represent the
     * fractional part of this {@code Rational}.
     *
     * @return {@code min n == d * absFraction()} that is a
     *         non-negative integer, for some positive integer {@code d}.
     * @see #absFloor()
     * @see #absFraction()
     */
    public BigInteger numerator() {
        return numerator;
    }

    /**
     * Returns the least positive denominator necessary to represent the fractional
     * part of this {@code Rational}.
     *
     * @return {@code min d == n / absFraction()} that is a positive
     *         integer, for some non-negative integer {@code n}.
     * @see #absFloor()
     * @see #absFraction()
     */
    public BigInteger denominator() {
        return denominator;
    }

    /**
     * Returns a {@code Rational} whose value is {@code signum() * (abs(this) +
     * abs(augend))}.
     */
    private Rational addAbs(Rational augend) {
        BigInteger gcd = denominator.gcd(augend.denominator);
        BigInteger[] lcdNums = lcdNumerators(augend, gcd);
        // less common denominator
        BigInteger resDen = denominator.divide(gcd).multiply(augend.denominator);
        // compute abs(this) + abs(augend)
        BigInteger resFloor = floor.add(augend.floor);
        BigInteger resNum = lcdNums[0].add(lcdNums[1]);

        // carry propagation
        BigInteger remainder = resNum.subtract(resDen);
        if (remainder.signum >= 0) { // decimal part >= 1
            resFloor = resFloor.add(BigInteger.ONE);
            resNum = remainder;
        }

        return valueOf(signum, resFloor, resNum, resDen);
    }

    /**
     * Returns a {@code Rational} whose value is {@code signum() * (abs(this) -
     * abs(augend))} if {@code abs(this) >= abs(augend)}, otherwise the returned
     * value is {@code -signum() * (abs(augend) - abs(this))}.
     */
    private Rational subtractAbs(Rational augend) {
        BigInteger gcd = denominator.gcd(augend.denominator);
        BigInteger[] lcdNums = lcdNumerators(augend, gcd);
        // less common denominator
        BigInteger resDen = denominator.divide(gcd).multiply(augend.denominator);
        // compute abs(this) - abs(augend)
        BigInteger resFloor = floor.subtract(augend.floor);
        BigInteger resNum = lcdNums[0].subtract(lcdNums[1]);
        final int resSign;

        if (resFloor.signum == 0) { // floor == augend.floor
            if (resNum.signum == 0) // abs(this) == abs(augend)
                return ZERO;

            resSign = signum * resNum.signum;
            resNum = resNum.abs();
        } else { // floor != augend.floor
            if (resFloor.signum == 1) { // abs(this) > abs(augend)
                resSign = signum;
            } else { // abs(this) < abs(augend)
                resSign = -signum;
                // abs(abs(this) - abs(augend)) = - (abs(this) - abs(augend))
                resFloor = resFloor.negate();
                resNum = resNum.negate();
            }

            // borrow propagation
            if (resNum.signum == -1) { // decimal part < 0
                // correct because abs(floor - augend.floor) >= 1
                resFloor = resFloor.subtract(BigInteger.ONE);
                resNum = resNum.add(resDen);
            }
        }

        return valueOf(resSign, resFloor, resNum, resDen);
    }

    // Arithmetic Operations
    /**
     * Returns a {@code Rational} whose value is {@code (this +
     * augend)}.
     *
     * @param augend value to be added to this {@code Rational}.
     * @return {@code this + augend}
     */
    public Rational add(Rational augend) {
        if (signum == 0)
            return augend;

        if (augend.signum == 0)
            return this;

        return signum == augend.signum ? addAbs(augend) : subtractAbs(augend);
    }

    /**
     * Returns a {@code Rational} whose value is {@code (this -
     * subtrahend)}.
     *
     * @param subtrahend value to be subtracted from this {@code Rational}.
     * @return {@code this - subtrahend}
     */
    public Rational subtract(Rational subtrahend) {
        if (signum == 0)
            return subtrahend.negate();

        if (subtrahend.signum == 0)
            return this;

        // do as if subtrahend were a negated augend
        return signum == subtrahend.signum ? subtractAbs(subtrahend) : addAbs(subtrahend);
    }

    /**
     * Computes the numerators of this {@code Rational} and the specified
     * {@code Rational}, relative to the least common denominator of
     * {@code denominator} and {@code val.denominator}
     *
     * @param val a {@code Rational}
     * @param gcd the greatest common divisor of {@code denominator} and
     *            {@code val.denominator}
     */
    private BigInteger[] lcdNumerators(Rational val, BigInteger gcd) {
        return  lcdNumerators(numerator, denominator, val.numerator, val.denominator, gcd);
    }

    /**
     * Computes the numerators of the specified fractions {@code (num1/den1)} and
     * {@code (num2/den2)}, relative to the least common denominator of {@code den1}
     * and {@code den2}
     *
     * @param num1 the numerator of the first fraction
     * @param den1 the denominator of the first fraction
     * @param num2 the numerator of the second fraction
     * @param den2 the denominator of the second fraction
     * @param gcd  the greatest common divisor of {@code den1} and {@code den2}
     */
    private static BigInteger[] lcdNumerators(BigInteger num1, BigInteger den1, BigInteger num2, BigInteger den2,
            BigInteger gcd) {
        // lcm(d1, d2) == d1 * d2 / gcd(d1, d2)
        // => n1/d1 == n1 * (d2 / gcd(d1, d2)) / lcm(d1, d2)
        // => n2/d2 == n2 * (d1 / gcd(d1, d2)) / lcm(d1, d2)
        // trying to pospone the overflow as as late as possible
        return new BigInteger[] { den2.divide(gcd).multiply(num1), den1.divide(gcd).multiply(num2) };
    }

    /**
     * Returns a {@code Rational} whose value is {@code (-this)}.
     *
     * @return {@code -this}.
     */
    public Rational negate() {
        return signum == 0 ? this : new Rational(-signum, floor, numerator, denominator);
    }

    /**
     * Returns a {@code Rational} whose value is {@code (1 / this)}. If
     * {@code (this == 0)} an {@code ArithmeticException} is thrown.
     *
     * @throws ArithmeticException if {@code this == 0}
     * @return {@code 1 / this}
     */
    public Rational invert() {
        if (signum == 0)
            throw new ArithmeticException("Divide by zero");

        BigInteger plainNum = wholeNumerator();
        BigInteger[] quotAndRem = denominator.divideAndRemainder(plainNum);
        return valueOf(signum, quotAndRem[0], quotAndRem[1], plainNum);
    }

    /**
     * Returns {@code (floor * denominator + numerator)}
     */
    private BigInteger wholeNumerator() {
        return floor.multiply(denominator).add(numerator);
    }

    /**
     * Returns a {@code Rational} whose value is the absolute value of this
     * {@code Rational}.
     *
     * @return {@code abs(this)}
     */
    public Rational abs() {
        return signum == -1 ? negate() : this;
    }

    /**
     * Returns a {@code Rational} whose value is <code>(this &times;
     * multiplicand)</code>.
     *
     * @param multiplicand value to be multiplied by this {@code Rational}.
     * @return {@code this * multiplicand}
     */
    public Rational multiply(Rational multiplicand) {
        if (signum == 0 || multiplicand.signum == 0)
            return ZERO;

        if (this == multiplicand)
            return square();

        final BigInteger a = floor, b = multiplicand.floor;
        final BigInteger n = numerator, m = denominator;
        final BigInteger k = multiplicand.numerator, l = multiplicand.denominator;
        // (a + n / m) * (b + k / l)
        // == a * b + a * (k / l) + b * (n / m) + (n / m) * (k / l)
        Rational absRes = ZERO;

        // try to pospone the overflow as as late as possible
        if (a.signum == 1) {
            if (b.signum == 1)
                absRes = absRes.add(new Rational(1, a.multiply(b))); // absRes += a * b

            if (k.signum == 1) {
                BigInteger gcdAL = a.gcd(l);
                Rational aug = valueOf(1, a.divide(gcdAL).multiply(k), l.divide(gcdAL));
                absRes = absRes.add(aug); // absRes += a * (k / l)
            }
        }

        if (n.signum == 1) {
            if (b.signum == 1) {
                BigInteger gcdBM = b.gcd(m);
                Rational aug = valueOf(1, b.divide(gcdBM).multiply(n), m.divide(gcdBM));
                absRes = absRes.add(aug); // absRes += b * (n / m)
            }

            if (k.signum == 1) {
                BigInteger gcdNL = n.gcd(l);
                BigInteger gcdMK = m.gcd(k);
                Rational aug = new Rational(1, BigInteger.ZERO, n.divide(gcdNL).multiply(k.divide(gcdMK)),
                        m.divide(gcdMK).multiply(l.divide(gcdNL)));
                absRes = absRes.add(aug); // absRes += (n / m) * (k / l)
            }
        }

        return signum * multiplicand.signum == 1 ? absRes : absRes.negate();
    }

    /**
     * Returns a {@code Rational} whose value is {@code (this / divisor)}. If
     * {@code (divisor == 0)} an {@code ArithmeticException} is thrown.
     *
     * @param divisor value by which this {@code Rational} is to be divided.
     * @throws ArithmeticException if {@code divisor == 0}
     * @return {@code this / divisor}
     */
    public Rational divide(Rational divisor) {
        return multiply(divisor.invert());
    }

    /**
     * Returns a two-element {@code Rational} array containing the result of
     * {@code divideToIntegralValue} followed by the result of {@code remainder} on
     * the two operands.
     *
     * <p>
     * Note that if both the integer quotient and remainder are needed, this method
     * is faster than using the {@code divideToIntegralValue} and {@code remainder}
     * methods separately because the division need only be carried out once.
     *
     * @param divisor value by which this {@code Rational} is to be divided, and the
     *                remainder computed.
     * @return a two element {@code Rational} array: the quotient (the result of
     *         {@code divideToIntegralValue}) is the initial element and the
     *         remainder is the final element.
     * @throws ArithmeticException if {@code divisor == 0}
     * @see #divideToIntegralValue(Rational)
     * @see #remainder(Rational)
     */
    public Rational[] divideAndRemainder(Rational divisor) {
        Rational quot = new Rational(signum * divisor.signum, divide(divisor).floor);
        return new Rational[] { quot, subtract(quot.multiply(divisor)) };
    }

    /**
     * Returns a {@code Rational} whose value is the integer part of the quotient
     * {@code (this / divisor)} rounded down.
     *
     * @param divisor value by which this {@code Rational} is to be divided.
     * @return The integer part of {@code this / divisor}.
     * @throws ArithmeticException if {@code divisor == 0}
     */
    public Rational divideToIntegralValue(Rational divisor) {
        return divideAndRemainder(divisor)[0];
    }

    /**
     * Returns a {@code Rational} whose value is {@code (this % divisor)}.
     *
     * <p>
     * The remainder is given by
     * {@code this.subtract(this.divideToIntegralValue(divisor).multiply(divisor))}.
     * Note that this is <em>not</em> the modulo operation (the result can be
     * negative).
     *
     * @param divisor value by which this {@code Rational} is to be divided.
     * @return {@code this % divisor}.
     * @throws ArithmeticException if {@code divisor==0}
     */
    public Rational remainder(Rational divisor) {
        return divideAndRemainder(divisor)[1];
    }

    private Rational square() {
        if (signum == 0)
            return ZERO;

        Rational res = ZERO;

        // (a + n / m)**2 == a**2 + 2 * a * (n / m) + (n / m)**2
        if (floor.signum == 1) {
            res = res.add(new Rational(1, floor.simpleSquare())); // res += a**2

            if (numerator.signum == 1) {
                BigInteger gcd = floor.gcd(denominator);
                BigInteger numAug = floor.divide(gcd).multiply(numerator);
                BigInteger denAug = denominator.divide(gcd);

                // double the product
                if (denAug.testBit(0)) {
                    numAug = numAug.shiftLeft(1); // denominator is odd
                } else {
                    denAug = denAug.shiftRight(1); // denominator is even
                }

                Rational aug = valueOf(1, numAug, denAug);
                res = res.add(aug); // res += 2 * a * (n / m)
            }
        }

        if (numerator.signum == 1) {
            Rational aug = new Rational(1, BigInteger.ZERO, numerator.simpleSquare(),
                    denominator.simpleSquare());
            res = res.add(aug); // res += (n / m)**2
        }

        return res;
    }

    /**
     * Returns a {@code Rational} whose value is <code>this<sup>n</sup></code>.
     *
     * {@code ZERO.pow(0)} returns {@link #ONE}.
     *
     * @param n power to raise this {@code Rational} to.
     * @return <code>this<sup>n</sup></code>
     * @throws ArithmeticException if {@code (this == 0 && n < 0)}
     * @throws ArithmeticException if {@code n == Integer.MIN_VALUE}
     */
    public Rational pow(int n) {
        // check for division by zero
        if (n < 0 && this.signum == 0)
            throw new ArithmeticException("Attempted to raise zero to a negative exponent");

        int mag = Math.absExact(n); // magnitude of n

        // handle easy cases
        if (n == 0)
            return ONE; // x**0 == 1

        if (n == 1 || this.signum == 0 || this.equals(ONE))
            return this; // x**1 == x, 0**n == 0, 1**n == 1
        // At this point, this != 0

        if (n == -1)
            return invert();

        if (n == 2)
            return square();

        if (numerator.signum == 0) { // integer base
            final int sign = (n & 1) == 1 ? signum : 1;
            final BigInteger floorPow = floor.pow(mag); // faster way
            return n > 0 ? new Rational(sign, floorPow) : new Rational(sign, BigInteger.ZERO, BigInteger.ONE, floorPow);
        }

        if (floor.signum == 0 && numerator.equals(BigInteger.ONE)) { // unitary fraction
            final int sign = (n & 1) == 1 ? signum : 1;
            final BigInteger denPow = denominator.pow(mag); // faster way
            return n > 0 ? new Rational(sign, floor, numerator, denPow) : new Rational(sign, denPow);
        }

        // ready to carry out power calculation...
        Rational acc = ONE; // accumulator
        boolean seenbit = false; // set once we've seen a 1-bit

        for (int i = 1; i < Integer.SIZE; i++) { // for each bit [top bit ignored]
            if (seenbit)
                acc = acc.square(); // acc=acc*acc [square]
            // if (!seenbit) no point in squaring ONE

            mag <<= 1; // shift left 1 bit
            if (mag < 0) { // top bit is set
                seenbit = true; // OK, we're off
                acc = acc.multiply(this); // acc=acc*x
            }
        }

        // if negative n, calculate the reciprocal
        return n >= 0 ? acc : acc.invert();
    }

    /**
     * Returns an approximation to the square root of {@code this} with rounding
     * according to the context settings.
     *
     * <p>
     * The value of the returned result is always within one ulp of the exact
     * decimal value for the precision in question. If the rounding mode is
     * {@link RoundingMode#HALF_UP HALF_UP}, {@link RoundingMode#HALF_DOWN
     * HALF_DOWN}, or {@link RoundingMode#HALF_EVEN HALF_EVEN}, the result is within
     * one half an ulp of the exact value.
     *
     * @param mc the context to use.
     * @return the square root of {@code this}.
     * @throws ArithmeticException if {@code this} is less than zero.
     * @throws ArithmeticException if an exact result is requested
     *                             ({@code mc.getPrecision()==0}) and there is no
     *                             fractional representation of the exact result.
     * @throws ArithmeticException if
     *                             {@code (mc.getRoundingMode()==RoundingMode.UNNECESSARY})
     *                             and the exact result cannot fit in
     *                             {@code mc.getPrecision()} digits.
     * @see BigDecimal#sqrt(MathContext)
     * @see BigInteger#sqrt()
     */
    public Rational sqrt(MathContext mc) {
        return root(2, mc);
    }

    /**
     * Returns an approximation to the {@code n}th root of {@code this} with
     * rounding according to the context settings.
     *
     * <p>
     * The value of the returned result is always within one ulp of the exact
     * decimal value for the precision in question. If the rounding mode is
     * {@link RoundingMode#HALF_UP HALF_UP}, {@link RoundingMode#HALF_DOWN
     * HALF_DOWN}, or {@link RoundingMode#HALF_EVEN HALF_EVEN}, the result is within
     * one half an ulp of the exact value.
     *
     * @param n  the root degree
     * @param mc the context to use.
     * @return the {@code n}th root of {@code this}.
     * @throws ArithmeticException if {@code n == 0} (Zeroth roots are not
     *                             defined.)
     * @throws ArithmeticException if {@code this == 0} and {@code n} is negative.
     *                             (This would cause a division by zero.)
     * @throws ArithmeticException if {@code n} is even and {@code this} is
     *                             negative. (This would cause the operation to
     *                             yield non-real roots.)
     * @throws ArithmeticException if an exact result is requested
     *                             ({@code mc.getPrecision() == 0}) and there is no
     *                             fractional representation of the exact result.
     * @throws ArithmeticException if
     *                             {@code (mc.getRoundingMode() == RoundingMode.UNNECESSARY})
     *                             and the exact result cannot fit in
     *                             {@code mc.getPrecision()} digits.
     * @throws ArithmeticException if {@code n == Integer.MIN_VALUE}
     * @see BigDecimal#sqrt(MathContext)
     * @see BigInteger#sqrt()
     * @see BigInteger#root(int)
     * @see #sqrt(MathContext)
     */
    public Rational root(int n, MathContext mc) {
        Rational x = this; // the radicand

        // even-degree root with negative radicand
        if ((n & 1) == 0 && x.signum == -1)
            throw new ArithmeticException("Attempted " + n + "th root of negative Rational");

        // check the sign of n
        if (n <= 0) {
            if (n == 0)
                throw new ArithmeticException("Attempted zero-degree root");
            // n < 0
            if (x.signum == 0) // radicand is zero
                throw new ArithmeticException("Attempted negative degree root of zero");

            if (n == Integer.MIN_VALUE)
                throw new ArithmeticException("Root degree overflow");

            // negate the degree and invert the radicand
            n = -n;
            x = x.invert();
        }

        // handle easy cases
        if (x.signum == 0 || x.equals(ONE))
            return x; // 0**n == 0, 1**n == 1
        // now x != 0

        if (n == 1)
            return x.round(mc); // x**1 == x

        // If an exact value is requested, it must be a rational number
        // so compute root(x, n) as root((x.floor * x.den + x.num) / x.den, n)
        // == root(x.floor * x.den + x.num, n) / root(x.den, n)
        if (mc.roundingMode == RoundingMode.UNNECESSARY || mc.precision == 0) {
            // simplify the radicand to improve the algorithm performance
            BigInteger[] radicand = simplify(x.wholeNumerator(), x.denominator);

            BigInteger[] rootRemNum = radicand[0].rootAndRemainderImpl(n);
            if (rootRemNum[1].signum != 0)
                throw new ArithmeticException("Computed " + n + "th root not exact.");

            BigInteger[] rootRemDen = radicand[1].rootAndRemainderImpl(n);
            if (rootRemDen[1].signum != 0)
                throw new ArithmeticException("Computed " + n + "th root not exact.");

            Rational result = valueOf(x.signum, rootRemNum[0], rootRemDen[0]);

            if (mc.precision != 0) {
                // => mc.roundingMode == UNNECESSARY, but user-specified number of digits
                result = result.round(new MathContext(mc.precision, RoundingMode.DOWN));

                // If result**n != x, the root isn't exact
                if (!result.pow(n).equals(x))
                    throw new ArithmeticException("Computed " + n + "th root not exact.");
            }

            return result;
        }
        // now (mc.roundingMode != UNNECESSARY && mc.precision > 0)

        /*
         * The code below use the following relations:
         *
         * x = y * 10 ^ exp
         *
         * root(x, n) = root(y * 10^(n - mod(exp, n)), n) * 10^(exp - (n -
         * mod(exp, n)) / n)
         */

        // Calculate the number of digits of the radicand
        // necessary to compute the root with the desired precision.

        final boolean halfWay = halfWay(mc.roundingMode);
        final int targetPrec = mc.precision + (halfWay ? 1 : 0);
        // To obtain a root with p digits,
        // the radicand must have at least n*(p-1)+1 digits.
        final long minWorkingPrec = (long) n * (targetPrec - 1) + 1;
        long scale;
        if (x.floor.signum == 1) { // non-zero integer part
            final int floorPrec = new BigDecimal(x.floor).precision();
            scale = minWorkingPrec - floorPrec;
        } else { // if integer part is zero, since x != 0 fraction must be non-zero
            final BigDecimal num = new BigDecimal(x.numerator), den = new BigDecimal(x.denominator);
            scale = den.precision() - num.precision(); // 0.1 < abs(x) * 10^scale < 10
            if (num.scaleByPowerOfTen((int) scale).compareTo(den) >= 0) {
                // 1 <= abs(x) * 10^scale < 10
                scale--;
            }
            // now 0.1 <= abs(x) * 10^scale < 1
            scale += minWorkingPrec;
        }
        // scale must be a multiple of n
        final int r = (int) (scale % n);
        if (r != 0) {
            final int offset = r > 0 ? n - r : -r;
            scale += offset;
        }
        // Assume that "extracted" is the integer composed by the extracted digits of x,
        // and so (extracted.precision() == minWorkingPrec + offset) holds.
        // Since (0 <= offset < n),
        // then minWorkingPrec <= extracted.precision() <= minWorkingPrec + (n - 1).
        // That is: n * (targetPrec - 1) + 1 <= extracted.precision() <= n * targetPrec
        // so, the unscaled root computed from "extracted" will have exactly targetPrec digits.

        final int scaleAdjust = (int) scale;
        if (scaleAdjust != scale)
            new ArithmeticException("Overflow");

        // Extract the digits from x and compute the unscaled root
        final Rational xScaled = x.scaleByPowerOfTen(scaleAdjust);
        BigInteger[] rootRem = xScaled.floor.rootAndRemainderImpl(n);
        BigInteger root = rootRem[0]; // root > 0
        int rootScale = scaleAdjust / n;
        final boolean error = rootRem[1].signum != 0 || xScaled.numerator.signum != 0;

        // Round the result with the specified settings
        if (halfWay) {
            // remove the one-tenth digit
            BigInteger[] divRem10 = root.divideAndRemainder(BigInteger.TEN);
            rootScale--;

            final BigInteger down = divRem10[0];
            int digit = divRem10[1].intValue();
            if (digit > 5 || digit == 5 && (error || mc.roundingMode == RoundingMode.HALF_UP
                    || mc.roundingMode == RoundingMode.HALF_EVEN && down.testBit(0))) {
                // - the one-tenth digit is bigger than five, or
                // - it is equal to five and:
                //      - the error is non-zero, or
                //      - the rounding mode is HALF_UP, or
                //      - the rounding mode is HALF_EVEN and the rounding down is odd
                root = down.add(BigInteger.ONE);
            } else { // round down
                root = down;
            }
        } else if (error && roundUp(mc.roundingMode, x.signum)) {
            root = root.add(BigInteger.ONE);
        } // else round down, since mc.roundingMode != UNNECESSARY, or no error,
        // so don't change the value

        // Adjust the sign
        if (x.signum == -1)
            root = root.negate();

       // Scale the result into the appropriate range.
       // if rounding mode is half-way, the least digit was discarded
       return valueOf(new BigDecimal(root, rootScale));
    }

    private static boolean halfWay(RoundingMode rm) {
        return switch (rm) {
        case HALF_DOWN, HALF_UP, HALF_EVEN -> true;
        default -> false;
        };
    }

    private static boolean roundUp(RoundingMode rm, int signum) {
        return switch (rm) {
        case UP -> true;
        case CEILING -> signum != -1;
        case FLOOR -> signum != 1;
        default -> false;
        };
    }

    // Shift Operations

    /**
     * Returns a Rational whose value is {@code (this << n)}.
     * The shift distance, {@code n}, may be negative, in which case
     * this method performs a right shift.
     * (Computes <code>this * 2<sup>n</sup></code>.)
     *
     * @param  n shift distance, in bits.
     * @return {@code this << n}
     * @see #shiftRight
     */
    public Rational shiftLeft(int n) {
        if (signum == 0 || n == 0)
            return this;
        // Possible int overflow in (-n) is not a trouble,
        // because shiftRightImpl considers its argument unsigned
        return n > 0 ? shiftLeftImpl(n) : shiftRightImpl(-n);
    }

    /**
     * Returns a Rational whose value is {@code (this << n)}. The shift
     * distance, {@code n}, is considered unsigned.
     * (Computes <code>this * 2<sup>n</sup></code>.)
     *
     * @param  n unsigned shift distance, in bits.
     * @return {@code this << n}
     */
    private Rational shiftLeftImpl(int n) {
        BigInteger resFloor = unsignedShiftLeft(floor, n);
        BigInteger resNum, resDen;

        if (numerator.signum == 1) { // non-zero fractional part
            final int shiftDen = (int) Math.min(n & BigInteger.LONG_MASK, denominator.getLowestSetBit());
            resNum = unsignedShiftLeft(numerator, n - shiftDen);
            resDen = denominator.shiftRight(shiftDen); // shiftDen is non-negative

            if (resNum.compareTo(resDen) >= 0) {
                BigInteger[] divRem = resNum.divideAndRemainder(resDen);
                resFloor = resFloor.add(divRem[0]);

                BigInteger[] frac = simplify(divRem[1], resDen);
                resNum = frac[0];
                resDen = frac[1];
            }
        } else { // no fractional part
            resNum = BigInteger.ZERO;
            resDen = BigInteger.ONE;
        }

        return new Rational(signum, resFloor, resNum, resDen);
    }

    private static BigInteger unsignedShiftLeft(BigInteger val, int n) {
        return n >= 0 ? val.shiftLeft(n) : val.shiftRight(n); // avoid right shift if (-n) overflows
    }

    /**
     * Returns a Rational whose value is {@code (this >> n)}.
     * The shift distance, {@code n}, may be negative, in which case
     * this method performs a left shift.
     * (Computes <code>this * 2<sup>-n</sup></code>.)
     *
     * @param  n shift distance, in bits.
     * @return {@code this >> n}
     * @see #shiftLeft
     */
    public Rational shiftRight(int n) {
        if (signum == 0 || n == 0)
            return this;
        // Possible int overflow in (-n) is not a trouble,
        // because shiftLeftImpl considers its argument unsigned
        return n > 0 ? shiftRightImpl(n) : shiftLeftImpl(-n);
    }

    /**
     * Returns a Rational whose value is {@code (this >> n)}. The shift
     * distance, {@code n}, is considered unsigned.
     * (Computes <code>this * 2<sup>-n</sup></code>.)
     *
     * @param  n unsigned shift distance, in bits.
     * @return {@code this >> n}
     */
    private Rational shiftRightImpl(final int n) {
        BigInteger resFloor = unsignedShiftRight(floor, n);
        final int nBits = (int) Math.min(n & BigInteger.LONG_MASK, floor.bitLength());
        final BigInteger rem = floor.getMagBits(0, nBits, floor.bitLength());
        BigInteger resNum, resDen;

        if (numerator.signum == 1) { // non-zero fractional part
            final int shiftNum = (int) Math.min(n & BigInteger.LONG_MASK, numerator.getLowestSetBit());
            resNum = numerator.shiftRight(shiftNum); // shiftNum is non-negative
            final int addedZeros = n - shiftNum;
            resDen = unsignedShiftLeft(denominator, addedZeros);

            if (rem.signum == 1) {
                final int denZeros = denominator.getLowestSetBit();
                final int remZeros = rem.getLowestSetBit();
                // trailingZeros(resDen) == denZeros + addedZeros
                // rem == floor - ((floor >> n) << n)

                // gcd(2^(n - remZeros), resDen)
                // == 1 << min(n - remZeros, trailingZeros(resDen))
                // == 1 << min(n - remZeros, denZeros + addedZeros)

                // this / 2^n
                // == (floor >> n) + resNum / resDen + rem / 2^n
                // == (floor >> n) + resNum / resDen + (rem >> remZeros) / 2^(n - remZeros)
                // == (floor >> n) + (resNum * (lcd(2^(n - remZeros), resDen) / resDen) + (rem >> remZeros) * (lcd(2^(n - remZeros), resDen) / 2^(n - remZeros))) / lcd(2^(n - remZeros), resDen)
                // == (floor >> n) + (resNum * (2^(n - remZeros) / gcd(2^(n - remZeros), resDen)) + (rem >> remZeros) * (resDen / gcd(2^(n - remZeros), resDen))) / (2^(n - remZeros) * resDen / gcd(2^(n - remZeros), resDen))
                if (shiftNum - denZeros < remZeros) { // n - remZeros < denZeros + addedZeros
                    // this / 2^n
                    // == (floor >> n) + (resNum * (2^(n - remZeros) / (1 << (n - remZeros))) + (rem >> remZeros) * (resDen / (1 << (n - remZeros)))) / ((2^(n - remZeros) * resDen) / (1 << (n - remZeros)))
                    // == (floor >> n) + (resNum + (rem >> remZeros) * (resDen >> (n - remZeros))) / resDen
                    resNum = resNum.add(rem.shiftRight(remZeros).multiply(unsignedShiftRight(resDen, n - remZeros)));
                } else { // n - remZeros >= denZeros + addedZeros
                    // this / 2^n
                    // == (floor >> n) + (resNum * (2^(n - remZeros) / (1 << (denZeros + addedZeros))) + (rem >> remZeros) * (resDen / (1 << (denZeros + addedZeros)))) / (2^(n - remZeros) * resDen / (1 << (denZeros + addedZeros)))
                    // == (floor >> n) + ( (resNum << (n - remZeros - (denZeros + addedZeros))) + (rem >> remZeros) * (resDen >> (denZeros + addedZeros)) ) / (resDen << (n - remZeros - (denZeros + addedZeros)))
                    // == (floor >> n) + ( (resNum << (shiftNum - remZeros - denZeros)) + (rem >> remZeros) * (denominator >> denZeros) ) / (resDen << (shiftNum - remZeros - denZeros))
                    final int shift = shiftNum - remZeros - denZeros;
                    resNum = resNum.shiftLeft(shift).add(rem.shiftRight(remZeros).multiply(denominator.shiftRight(denZeros)));
                    resDen = resDen.shiftLeft(shift); // shift is non-negative
                }

                BigInteger[] frac = simplify(resNum, resDen);
                resNum = frac[0];
                resDen = frac[1];
            }
        } else if (rem.signum == 1) { // no fractional part, non-zero remainder
            final int shift = (int) Math.min(n & BigInteger.LONG_MASK, rem.getLowestSetBit());
            resNum = rem.shiftRight(shift);
            resDen = unsignedShiftLeft(BigInteger.ONE, n - shift);
        } else {  // no fractional part, no remainder
            resNum = BigInteger.ZERO;
            resDen = BigInteger.ONE;
        }

        return new Rational(signum, resFloor, resNum, resDen);
    }

    private static BigInteger unsignedShiftRight(BigInteger val, int n) {
        return n >= 0 ? val.shiftRight(n) : val.shiftLeft(n); // avoid left shift if (-n) overflows
    }

    // Scaling/Rounding Operations

    /**
     * Returns a Rational whose value is equal to
     * ({@code this} * 10<sup>n</sup>).
     *
     * @param n the exponent power of ten to scale by
     * @return a Rational whose value is equal to
     * ({@code this} * 10<sup>n</sup>)
     */
    public Rational scaleByPowerOfTen(int n) {
        if (signum == 0 || n == 0)
            return this;
        // Possible int overflow in (-n) is not a trouble,
        // because rightScaleByPowerOfTen considers its argument unsigned
        return n > 0 ? leftScaleByPowerOfTen(n) : rightScaleByPowerOfTen(-n);
    }

    /**
     * Returns a Rational whose value is equal to ({@code this} * 10<sup>n</sup>).
     * {@code n} is considered unsigned and non-zero.
     *
     * @param n the positive exponent power of ten to scale by
     * @return a Rational whose value is equal to ({@code this} * 10<sup>n</sup>)
     */
    private Rational leftScaleByPowerOfTen(final int n) {
        BigInteger resFloor, resNum, resDen;

        if (numerator.signum == 1) { // non-zero fractional part
            // multiply fraction by 2^n
            final int shiftDen = (int) Math.min(n & BigInteger.LONG_MASK, denominator.getLowestSetBit());
            resNum = numerator.shiftLeft(n - shiftDen);
            resDen = denominator.shiftRight(shiftDen);
            // multiply fraction by 5^n
            final AtomicLong fiveExp = new AtomicLong(n & BigInteger.LONG_MASK);
            resDen = removePowersOfFive(resDen, fiveExp);
            final BigInteger fivePow = bigFiveToThe(fiveExp.intValue());
            resNum = resNum.multiply(fivePow);

            // floor * 10^n == floor * 2^n * 5^n == (floor << n) * (5^fiveExp * 5^(n - fiveExp))
            resFloor = unsignedShiftLeft(floor, n).multiply(fivePow).multiply(bigFiveToThe(n - fiveExp.intValue()));

            if (resNum.compareTo(resDen) >= 0) {
                BigInteger[] divRem = resNum.divideAndRemainder(resDen);
                resFloor = resFloor.add(divRem[0]);

                BigInteger[] frac = simplify(divRem[1], resDen);
                resNum = frac[0];
                resDen = frac[1];
            }
        } else { // no fractional part
            resFloor = unsignedShiftLeft(floor, n).multiply(bigFiveToThe(n));
            resNum = BigInteger.ZERO;
            resDen = BigInteger.ONE;
        }

        return new Rational(signum, resFloor, resNum, resDen);
    }

    /**
     * Returns a Rational whose value is equal to ({@code this} * 10<sup>-n</sup>).
     * {@code n} is considered unsigned and non-zero.
     *
     * @param n a non-zero unsigned integer whose negative is the exponent power of ten to
     *          scale by
     * @return a Rational whose value is equal to ({@code this} * 10<sup>-n</sup>)
     */
    private Rational rightScaleByPowerOfTen(final int n) {
        final Rational shifted = shiftRightImpl(n); // this / 2^n
        final BigInteger FIVE_TO_N = bigFiveToThe(n);
        final BigInteger[] divRem = shifted.floor.divideAndRemainder(FIVE_TO_N);
        final BigInteger rem = divRem[1];

        final BigInteger resFloor = divRem[0];
        BigInteger resNum, resDen;
        if (shifted.numerator.signum == 1) {  // non-zero fraction of shifted
            if (rem.signum == 1) { // non-zero remainder
                // divide fraction of shifted by 5^n and add rem/5^n
                resNum = shifted.numerator.add(rem.multiply(shifted.denominator));
                resDen = shifted.denominator.multiply(FIVE_TO_N);
                BigInteger[] frac = simplify(resNum, resDen);
                resNum = frac[0];
                resDen = frac[1];
            } else { // no remainder
                // divide fraction of shifted by 5^n
                AtomicLong fiveExp = new AtomicLong(n & BigInteger.LONG_MASK);
                resNum = removePowersOfFive(shifted.numerator, fiveExp);
                resDen = shifted.denominator.multiply(bigFiveToThe(fiveExp.intValue()));
            }
        } else if (rem.signum == 1) { // no fraction of shifted, non-zero remainder
            // set result fraction to rem/5^n
            AtomicLong fiveExp = new AtomicLong(n & BigInteger.LONG_MASK);
            resNum = removePowersOfFive(rem, fiveExp);
            resDen = bigFiveToThe(fiveExp.intValue());
        } else { // no fraction of shifted, no remainder
            resNum = BigInteger.ZERO;
            resDen = BigInteger.ONE;
        }

        return new Rational(signum, resFloor, resNum, resDen);
    }

    /**
     * {@code FIVE_SQUARES.get(n) == 5^(1L << n)}
     */
    private static final ArrayList<BigInteger> FIVE_SQUARES = new ArrayList<>(Integer.SIZE);

    static {
        FIVE_SQUARES.add(BigInteger.valueOf(5));
    }

    /**
     * Returns {@code 5^n}. {@code n} is considered unsigned.
     */
    private static BigInteger bigFiveToThe(int n) {
        BigInteger acc = BigInteger.ONE;
        final int nBits = BigInteger.bitLengthForInt(n);

        final int end = Math.min(nBits, FIVE_SQUARES.size());
        for (int i = 0; i < end; i++) {
            if ((n & 1) == 1)
                acc = acc.multiply(FIVE_SQUARES.get(i));

            n >>>= 1;
        }

        BigInteger pow = FIVE_SQUARES.get(FIVE_SQUARES.size() - 1);
        while (FIVE_SQUARES.size() < nBits) {
            pow = pow.simpleSquare();
            FIVE_SQUARES.add(pow);

            if ((n & 1) == 1)
                acc = acc.multiply(pow);

            n >>>= 1;
        }

        return acc;
    }

    /**
     * If before calling this method {@code remaining.get() == r}, the method
     * returns the least integer {@code n > 0} such that
     * {@code (val == n * 5^(r - remaining.get()) && 0 <= remaining.get() <= r)}
     * will hold after calling this method. Assumes {@code val > 0 && r >= 0}.
     */
    private static BigInteger removePowersOfFive(BigInteger val, AtomicLong remaining) {
        BigInteger divisor = FIVE_SQUARES.get(0);
        int i = 0;

        // divide val and square the divisor as much as possible
        boolean stop = false;
        do {
            BigInteger[] divRem = val.divideAndRemainder(divisor);

            if (divRem[1].signum != 0) {
                stop = true;
            } else { // no remainder
                val = divRem[0];
                remaining.addAndGet(-(1L << i));

                if (remaining.get() < 1L << i || divisor.compareTo(val) > 0) {
                    stop = true;
                } else if (i + 1 < Integer.SIZE && remaining.get() >= 1L << (i + 1)) {
                    final BigInteger square;
                    final int last = FIVE_SQUARES.size() - 1;

                    // compute divisor^2
                    if (i < last) {
                        square = FIVE_SQUARES.get(i + 1);
                    } else {
                        square = FIVE_SQUARES.get(last).simpleSquare();
                        FIVE_SQUARES.add(square);
                    }

                    if (square.compareTo(val) <= 0) {
                        // square the divisor
                        divisor = square;
                        i++;
                    }
                }
            }
        } while (!stop);
        i--; // by conditions for stop, val can no longer be divided by the divisor

        while (i >= 0 && remaining.get() > 0) {
            // find, if exists, the greatest non-negative n <= i
            // s.t. (1 << n <= remaining.get() && 5^(1 << n) <= val) using binary search
            final int highestBit = Math.min(i, Long.SIZE - 1 - Long.numberOfLeadingZeros(remaining.get()));
            if (FIVE_SQUARES.get(highestBit).compareTo(val) <= 0) {
                // easy case, no need to search for n
                i = highestBit;
            } else {
                final int pos = Collections.binarySearch(FIVE_SQUARES.subList(0, highestBit), val);
                i = pos >= 0 ? pos : -(pos + 1) - 1;
            }
            // now i == n

            // divide val as much as possible
            if (i >= 0) {
                divisor = FIVE_SQUARES.get(i);
                do {
                    BigInteger[] divRem = val.divideAndRemainder(divisor);

                    if (divRem[1].signum == 0) {
                        val = divRem[0];
                        remaining.addAndGet(-(1L << i));
                    }

                    i--; // by definition of n, val can no longer be divided by 5^(1 << i)
                    if (i >= 0)
                        divisor = FIVE_SQUARES.get(i);
                } while (i >= 0 && remaining.get() >= 1L << i && divisor.compareTo(val) <= 0);
            }
        }

        return val;
    }

    /**
     * Returns a {@code Rational} rounded according to the {@code MathContext}
     * settings. If the precision setting is 0 then no rounding takes place.
     *
     * <p>
     * The effect of this method is identical to call
     * {@code new #Rational(toBigDecimal(mc))} method.
     *
     * @param mc the context to use.
     * @return a {@code Rational} rounded according to the {@code MathContext}
     *         settings.
     */
    public Rational round(MathContext mc) {
        return valueOf(toBigDecimal(mc));
    }

    // Format Converters

    /**
     * Converts this {@code Rational} to a {@code BigInteger}. This conversion is
     * analogous to the <i>narrowing primitive conversion</i> from {@code double} to
     * {@code long} as defined in <cite>The Java Language Specification</cite>: any
     * fractional part of this {@code Rational} will be discarded. Note that this
     * conversion can lose information about the precision of the {@code Rational}
     * value.
     * <p>
     * To have an exception thrown if the conversion is inexact (in other words if a
     * nonzero fractional part is discarded), use the {@link #toBigIntegerExact()}
     * method.
     *
     * @return this {@code Rational} converted to a {@code BigInteger}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    public BigInteger toBigInteger() {
        // force to an integer, quietly
        return signum == -1 ? floor.negate() : floor;
    }

    /**
     * Converts this {@code Rational} to a {@code BigInteger}, checking for lost
     * information. An exception is thrown if this {@code Rational} has a nonzero
     * fractional part.
     *
     * @return this {@code Rational} converted to a {@code BigInteger}.
     * @throws ArithmeticException if {@code this} has a nonzero fractional part
     */
    public BigInteger toBigIntegerExact() {
        // round to an integer, with Exception if decimal part non-0
        if (numerator.signum == 1)
            throw new ArithmeticException("Rounding necessary");

        return toBigInteger();
    }

    /**
     * Converts this {@code Rational} to a {@code BigDecimal}, with rounding
     * according to the context settings. Note that this conversion can lose
     * information about the {@code Rational} value.
     * <p>
     * To always have an exception thrown if the conversion is inexact (in other
     * words if a nonzero fractional part is discarded), use the
     * {@link #toBigDecimalExact()} method.
     *
     * @param mc the context to use.
     * @return this {@code Rational} converted to a {@code BigDecimal}.
     * @throws ArithmeticException if the result is inexact but the rounding mode is
     *                             {@code UNNECESSARY} or {@code mc.precision == 0}
     *                             and this {@code Rational} has a non-terminating
     *                             decimal expansion
     * @implNote The semantics of this method exactly mimic that of the
     *           {@link BigDecimal#divide(BigDecimal, MathContext)} method, but
     *           avoiding the division of the whole numerator by the denominator to
     *           compute the absolute value: {@code (absFloor() * denominator() +
     *           numerator()) / denominator()}.
     */
    public BigDecimal toBigDecimal(MathContext mc) {
        final BigDecimal intPart = new BigDecimal(floor);
        final BigDecimal num = new BigDecimal(numerator);
        final BigDecimal den = new BigDecimal(denominator);

        final BigDecimal absVal;
        MathContext workMC;
        final int prec = mc.getPrecision();
        if (prec == 0) { // arbitrary precision
            absVal = intPart.add(num.divide(den));
            workMC = null; // no rounding
        } else if (floor.signum == 0) { // bounded precision, no integer part
            // one more digit for rounding
            BigDecimal frac = num.divide(den, new MathContext(prec + 1, RoundingMode.DOWN));

            if (frac.multiply(den).compareTo(num) == 0) { // no remainder
                workMC = mc; // user-supplied MathContext is sufficient
            } else { // non-zero discarded fraction
                RoundingMode rm = mc.getRoundingMode();
                workMC = new MathContext(prec, workRoundingMode(rm));
                // ensure rounding up if last significand digit is 0
                if (roundUp(rm, signum))
                    frac = frac.add(frac.ulp());
            }
            absVal = frac;
        } else { // bounded precision, non-zero integer part
            final int scale = prec - intPart.precision();

            if (scale >= 0) { // result includes the whole integer part
                // one more digit for rounding
                BigDecimal frac = num.divide(den, scale + 1, RoundingMode.DOWN);

                if (frac.multiply(den).compareTo(num) == 0) { // no remainder
                    frac = frac.stripTrailingZeros();
                    workMC = mc; // user-supplied MathContext is sufficient
                } else { // non-zero discarded fraction
                    RoundingMode rm = mc.getRoundingMode();
                    workMC = new MathContext(prec, workRoundingMode(rm));
                    // ensure rounding up if last significand digit is 0
                    if (roundUp(rm, signum))
                        frac = frac.add(frac.ulp());
                }
                absVal = intPart.add(frac);
            } else { // scale < 0, truncate the integer part
                // one more digit for rounding
                BigDecimal trunc = intPart.setScale(scale + 1, RoundingMode.DOWN);

                if (numerator.signum == 0 && trunc.compareTo(intPart) == 0) { // no remainder
                    workMC = mc; // user-supplied MathContext is sufficient
                } else { // non-zero discarded part
                    RoundingMode rm = mc.getRoundingMode();
                    workMC = new MathContext(prec, workRoundingMode(rm));
                    // ensure rounding up if last significand digit is 0
                    if (roundUp(rm, signum))
                        trunc = trunc.add(trunc.ulp());
                }
                absVal = trunc;
            }
        }

        BigDecimal res = signum == -1 ? absVal.negate() : absVal;
        return workMC == null ? res : res.round(workMC);
    }

    private static RoundingMode workRoundingMode(RoundingMode rm) {
        // ensure rounding up if last significand digit is 5
        return switch (rm) {
        case UNNECESSARY -> throw new ArithmeticException("Rounding necessary");
        case HALF_DOWN, HALF_EVEN -> RoundingMode.HALF_UP;
        default -> rm;
        };
    }

    /**
     * Converts this {@code Rational} to a {@code BigDecimal}, checking for lost
     * information. An exception is thrown if this {@code Rational} has a
     * non-terminating decimal expansion.
     *
     * @return this {@code Rational} converted to a {@code BigDecimal}.
     * @throws ArithmeticException if this {@code Rational} has a non-terminating
     *                             decimal expansion.
     */
    public BigDecimal toBigDecimalExact() {
        return toBigDecimal(MathContext.UNLIMITED);
    }

    /**
     * Converts this {@code Rational} to a {@code double}. This conversion is
     * similar to the <i>narrowing primitive conversion</i> from {@code double} to
     * {@code float} as defined in <cite>The Java Language Specification</cite>: if
     * this {@code Rational} has too great a magnitude represent as a
     * {@code double}, it will be converted to {@link Double#NEGATIVE_INFINITY} or
     * {@link Double#POSITIVE_INFINITY} as appropriate. Note that even when the
     * return value is finite, this conversion can lose information about the
     * {@code Rational} value.
     *
     * @return this {@code Rational} converted to a {@code double}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public double doubleValue() {
        final double fl = floor.doubleValue();

        // integer or non-representable value
        if (numerator.signum == 0 || Double.isInfinite(fl))
            return signum == -1 ? -fl : fl;
        // At this point, this Rational is non-integer

        final int flBits = floor.bitLength();
        final int prec = Double.PRECISION;
        if (flBits > prec) { // integer part does not fit into a double
            // check if the 0.5 bit is set to round up, since this Rational is non-integer
            double res = floor.testBit(flBits - prec - 1) ? Math.nextUp(fl) : fl;
            return signum == -1 ? -res : res;
        }

        long significand;
        final long biasedExp;
        int nBits; // computed bits of significand
        MutableBigInteger rem = new MutableBigInteger(numerator);
        final MutableBigInteger den = new MutableBigInteger(denominator.mag);

        // compute initial value of significand
        if (floor.signum == 1) { // non-zero integer part
            // unpack biased exponent of integer part
            biasedExp = (Double.doubleToLongBits(fl) & DoubleConsts.EXP_BIT_MASK) >> 52;
            significand = floor.longValue();
            nBits = flBits;
        } else { // no integer part
            // normalize
            int scale = (int) Math.max(1, den.bitLength() - rem.bitLength());
            rem.leftShift(scale);
            // now 0.5 <= rem / den < 2
            if (rem.compare(den) < 0) { // rem / den < 1
                scale++;
                rem.leftShift(1);
            }
            // now 1 <= rem / den < 2
            rem.subtract(den); // subtract one to (rem / den)
            // now rem / den < 1

            significand = 1L; // set the most significant bit
            // compute the correct biased exponent
            if (scale < DoubleConsts.EXP_BIAS) { // normal number
                nBits = 1; // count the implicit bit
                biasedExp = -scale + DoubleConsts.EXP_BIAS; // biasedExp > 0
            } else { // subnormal number
                nBits = scale + Double.MIN_EXPONENT + 1; // scale - 1022 + 1, count the implicit bit
                biasedExp = 0L;

                if (nBits > prec) { // abs(this) < Double.MIN_VALUE
                    // half even rounding
                    double res = nBits > prec + 1 || rem.isZero() ? 0.0 : Double.MIN_VALUE;
                    return signum == -1 ? -res : res;
                }
            }
        }
        // now rem / den < 1

        // compute remaining bits, stop if remainder is zero
        int shift;
        for (; !rem.isZero() && nBits < prec; nBits += shift) {
            shift = (int) Math.max(1, den.bitLength() - rem.bitLength());

            if (nBits + shift <= prec) { // shifted significand fits in double precision
                significand <<= shift;
                rem.leftShift(shift);
                // now 0.5 <= rem / den < 2
                final MutableBigInteger diff = new MutableBigInteger(rem);

                // subtract one to (rem / den)
                if (diff.subtract(den) != -1) { // rem / den >= 1
                    significand |= 1L;
                    rem = diff;
                }
                // now rem / den < 1
            } else { // end of iterating
                shift = prec - nBits;
                significand <<= shift; // add trailing zeros
                rem.leftShift(shift); // align remainder
                // now rem / den < 1
            }
        }

        significand &= DoubleConsts.SIGNIF_BIT_MASK; // remove the implicit bit

        if (!rem.isZero()) { // inexact result
            // compute the 0.5 bit to round
            rem.leftShift(1);
            final int rem_cmp_den = rem.compare(den);
            /*
             * We round up if either the fractional part of significand is strictly greater
             * than 0.5 (which is true if the 0.5 bit is set and the remainder is greater
             * than the denominator), or if the fractional part of significand is >= 0.5 and
             * significand is odd (which is true if both the 0.5 bit and the 1 bit are set).
             * This is equivalent to the desired HALF_EVEN rounding.
             */
            if (rem_cmp_den >= 0) { // the 0.5 bit is set
                if (rem_cmp_den > 0 || (significand & 1L) == 1) // half even rounding
                    significand++;
            }
        }
        /*
         * If significand == 2^53, we'd need to set all of the significand bits to zero
         * and add 1 to the exponent. This is exactly the behavior we get from just
         * adding significand to the shifted exponent directly. If the exponent is
         * Double.MAX_EXPONENT, we round up (correctly) to Double.POSITIVE_INFINITY.
         */
        long bits = (biasedExp << 52) + significand;
        if (signum == -1)
            bits |= DoubleConsts.SIGN_BIT_MASK;

        return Double.longBitsToDouble(bits);
    }

    /**
     * Converts this {@code Rational} to a {@code float}. This conversion is similar
     * to the <i>narrowing primitive conversion</i> from {@code double} to
     * {@code float} as defined in <cite>The Java Language Specification</cite>: if
     * this {@code Rational} has too great a magnitude to represent as a
     * {@code float}, it will be converted to {@link Float#NEGATIVE_INFINITY} or
     * {@link Float#POSITIVE_INFINITY} as appropriate. Note that even when the
     * return value is finite, this conversion can lose information about the
     * {@code Rational} value.
     *
     * @return this {@code Rational} converted to a {@code float}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public float floatValue() {
        final float fl = floor.floatValue();

        // integer or non-representable value
        if (numerator.signum() == 0 || Float.isInfinite(fl))
            return signum == -1 ? -fl : fl;
        // At this point, this Rational is non-integer

        final int flBits = floor.bitLength();
        final int prec = Float.PRECISION;
        if (flBits > prec) { // integer part does not fit into a float
            // check if the 0.5 bit is set to round up, since this Rational is non-integer
            float res = floor.testBit(flBits - prec - 1) ? Math.nextUp(fl) : fl;
            return signum == -1 ? -res : res;
        }

        int significand;
        final int biasedExp;
        int nBits; // computed bits of significand
        MutableBigInteger rem = new MutableBigInteger(numerator);
        final MutableBigInteger den = new MutableBigInteger(denominator.mag);

        // compute initial value of significand
        if (floor.signum == 1) { // non-zero integer part
            // unpack biased exponent of integer part
            biasedExp = (Float.floatToIntBits(fl) & FloatConsts.EXP_BIT_MASK) >> 23;
            significand = floor.intValue();
            nBits = flBits;
        } else { // no integer part
            // normalize
            int scale = (int) Math.max(1, den.bitLength() - rem.bitLength());
            rem.leftShift(scale);
            // now 0.5 <= rem / den < 2
            if (rem.compare(den) < 0) { // rem / den < 1
                scale++;
                rem.leftShift(1);
            }
            // now 1 <= rem / den < 2
            rem.subtract(den); // subtract one to (rem / den)
            // now rem / den < 1

            significand = 1; // set the most significant bit
            // compute the correct biased exponent
            if (scale < FloatConsts.EXP_BIAS) { // normal number
                nBits = 1; // count the implicit bit
                biasedExp = -scale + FloatConsts.EXP_BIAS; // biasedExp > 0
            } else { // subnormal number
                nBits = scale + Float.MIN_EXPONENT + 1; // scale - 126 + 1, count the implicit bit
                biasedExp = 0;

                if (nBits > prec) { // abs(this) < Float.MIN_VALUE
                    // half even rounding
                    float res = nBits > prec + 1 || rem.isZero() ? 0f : Float.MIN_VALUE;
                    return signum == -1 ? -res : res;
                }
            }
        }
        // now rem / den < 1

        // compute remaining bits, stop if remainder is zero
        int shift;
        for (; !rem.isZero() && nBits < prec; nBits += shift) {
            shift = (int) Math.max(1, den.bitLength() - rem.bitLength());

            if (nBits + shift <= prec) { // shifted significand fits in float precision
                significand <<= shift;
                rem.leftShift(shift);
                // now 0.5 <= rem / den < 2
                final MutableBigInteger diff = new MutableBigInteger(rem);

                // subtract one to (rem / den)
                if (diff.subtract(den) != -1) { // rem / den >= 1
                    significand |= 1;
                    rem = diff;
                }
                // now rem / den < 1
            } else { // end of iterating
                shift = prec - nBits;
                significand <<= shift; // add trailing zeros
                rem.leftShift(shift); // align remainder
                // now rem / den < 1
            }
        }

        significand &= FloatConsts.SIGNIF_BIT_MASK; // remove the implicit bit

        if (!rem.isZero()) { // inexact result
            // compute the 0.5 bit to round
            rem.leftShift(1);
            final int rem_cmp_den = rem.compare(den);
            /*
             * We round up if either the fractional part of significand is strictly greater
             * than 0.5 (which is true if the 0.5 bit is set and the remainder is greater
             * than the denominator), or if the fractional part of significand is >= 0.5 and
             * significand is odd (which is true if both the 0.5 bit and the 1 bit are set).
             * This is equivalent to the desired HALF_EVEN rounding.
             */
            if (rem_cmp_den >= 0) { // the 0.5 bit is set
                if (rem_cmp_den > 0 || (significand & 1) == 1) // half even rounding
                    significand++;
            }
        }
        /*
         * If significand == 2^24, we'd need to set all of the significand bits to zero
         * and add 1 to the exponent. This is exactly the behavior we get from just
         * adding significand to the shifted exponent directly. If the exponent is
         * Float.MAX_EXPONENT, we round up (correctly) to Float.POSITIVE_INFINITY.
         */
        int bits = (biasedExp << 23) + significand;
        if (signum == -1)
            bits |= FloatConsts.SIGN_BIT_MASK;

        return Float.intBitsToFloat(bits);
    }

    /**
     * Converts this {@code Rational} to an {@code int}. This conversion is
     * analogous to the <i>narrowing primitive conversion</i> from {@code double} to
     * {@code short} as defined in <cite>The Java Language Specification</cite>: any
     * fractional part of this {@code Rational} will be discarded, and if the
     * resulting "{@code BigInteger}" is too big to fit in an {@code int}, only the
     * low-order 32 bits are returned. Note that this conversion can lose
     * information about the overall magnitude and precision of this
     * {@code Rational} value as well as return a result with the opposite sign.
     *
     * @return this {@code Rational} converted to an {@code int}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public int intValue() {
        return toBigInteger().intValue();
    }

    /**
     * Converts this {@code Rational} to an {@code int}, checking for lost
     * information. If this {@code Rational} has a nonzero fractional part or is out
     * of the possible range for an {@code int} result then an
     * {@code ArithmeticException} is thrown.
     *
     * @return this {@code Rational} converted to an {@code int}.
     * @throws ArithmeticException if {@code this} has a nonzero fractional part, or
     *                             will not fit in an {@code int}.
     */
    public int intValueExact() {
        return toBigIntegerExact().intValueExact();
    }

    /**
     * Converts this {@code Rational} to a {@code short}, checking for lost
     * information. If this {@code Rational} has a nonzero fractional part or is out
     * of the possible range for a {@code short} result then an
     * {@code ArithmeticException} is thrown.
     *
     * @return this {@code Rational} converted to a {@code short}.
     * @throws ArithmeticException if {@code this} has a nonzero fractional part, or
     *                             will not fit in a {@code short}.
     */
    public short shortValueExact() {
        return toBigIntegerExact().shortValueExact();
    }

    /**
     * Converts this {@code Rational} to a {@code byte}, checking for lost
     * information. If this {@code Rational} has a nonzero fractional part or is out
     * of the possible range for a {@code byte} result then an
     * {@code ArithmeticException} is thrown.
     *
     * @return this {@code Rational} converted to a {@code byte}.
     * @throws ArithmeticException if {@code this} has a nonzero fractional part, or
     *                             will not fit in a {@code byte}.
     */
    public byte byteValueExact() {
        return toBigIntegerExact().byteValueExact();
    }

    /**
     * Converts this {@code Rational} to a {@code long}. This conversion is
     * analogous to the <i>narrowing primitive conversion</i> from {@code double} to
     * {@code short} as defined in <cite>The Java Language Specification</cite>: any
     * fractional part of this {@code Rational} will be discarded, and if the
     * resulting "{@code BigInteger}" is too big to fit in a {@code long}, only the
     * low-order 64 bits are returned. Note that this conversion can lose
     * information about the overall magnitude and precision of this
     * {@code Rational} value as well as return a result with the opposite sign.
     *
     * @return this {@code Rational} converted to a {@code long}.
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public long longValue() {
        return toBigInteger().longValue();
    }

    /**
     * Converts this {@code Rational} to a {@code long}, checking for lost
     * information. If this {@code Rational} has a nonzero fractional part or is out
     * of the possible range for a {@code long} result then an
     * {@code ArithmeticException} is thrown.
     *
     * @return this {@code Rational} converted to a {@code long}.
     * @throws ArithmeticException if {@code this} has a nonzero fractional part, or
     *                             will not fit in a {@code long}.
     */
    public long longValueExact() {
        return toBigIntegerExact().longValueExact();
    }

    /**
     * Returns the String representation of this {@code Rational}, compound as follows:
     * <ul>
     * <li> If {@code this >= 0}:
     * <p>
     * <i>this.absFloor()</i> + <i>this.numerator()</i> / <i>this.denominator()</i>;
     * <li> If {@code this < 0}:
     * <p>
     * -<i>this.absFloor()</i> - <i>this.numerator()</i> / <i>this.denominator()</i>.
     * </ul>
     *
     * <p>
     * The digit-to-character mapping provided by {@code Character.forDigit}. (This
     * representation allows for String concatenation with Java's + operator.)
     *
     * @return decimal String representation of this Rational.
     * @see Character#forDigit
     * @see #Rational(java.lang.String)
     */
    @Override
    public String toString() {
        if (stringCache != null)
            return stringCache;
        
        final int radix = 10;
        int capacity = floor.numChars(radix);
        capacity += numerator.numChars(radix);
        capacity += denominator.numChars(radix);
        capacity += 2 + (signum == -1 ? 1 : 0);
        StringBuilder b = new StringBuilder(capacity);

        if (signum == -1)
            b.append('-');

        b.append(floor).append(signum == -1 ? '-' : '+').append(numerator).append('/').append(denominator);
        return stringCache = b.toString();
    }

    // Hash Function

    /**
     * Returns the hash code for this Rational.
     *
     * @return hash code for this Rational.
     */
    @Override
    public int hashCode() {
        return signum == 0 ? 0 : signum * Objects.hash(floor, numerator, denominator);
    }

    // Comparison Operations

    /**
     * Compares this Rational with the specified Object for equality.
     *
     * @param obj Object to which this Rational is to be compared.
     * @return {@code true} if and only if the specified Object is a Rational whose
     *         value is numerically equal to this Rational.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (!(obj instanceof Rational r))
            return false;

        if (signum != r.signum)
            return false;

        if (signum == 0) // values are both zero
            return true;

        return floor.equals(r.floor) && numerator.equals(r.numerator) && denominator.equals(r.denominator);
    }

    /**
     * Compares this Rational with the specified Rational. This method is provided
     * in preference to individual methods for each of the six boolean comparison
     * operators ({@literal <}, ==, {@literal >}, {@literal >=}, !=, {@literal <=}).
     * The suggested idiom for performing these comparisons is: {@code
     * (x.compareTo(y)} &lt;<i>op</i>&gt; {@code 0)}, where &lt;<i>op</i>&gt; is one
     * of the six comparison operators.
     *
     * @param val Rational to which this Rational is to be compared.
     * @return -1, 0 or 1 as this Rational is numerically less than, equal to, or
     *         greater than {@code r}.
     */
    @Override
    public int compareTo(Rational val) {
        if (signum != val.signum)
            return signum > val.signum ? 1 : -1;

        if (signum == 0) // values are both zero
            return 0;

        // compare absolute values
        int absComp = floor.compareTo(val.floor);

        if (absComp == 0) { // values have the same floor
            if (denominator.equals(val.denominator)) {
                absComp = numerator.compareTo(val.numerator);
            } else if (numerator.equals(val.numerator)) {
                absComp = val.denominator.compareTo(denominator); // a/b < a/c <=> c < b
            } else {
                // compare using least common denominator
                BigInteger[] lcdNums = lcdNumerators(val, denominator.gcd(val.denominator));
                absComp = lcdNums[0].compareTo(lcdNums[1]);
            }
        }

        return signum * absComp; // adjust comparison with signum
    }

    /**
     * Returns the minimum of this {@code Rational} and {@code val}.
     *
     * @param val value with which the minimum is to be computed.
     * @return the {@code Rational} whose value is the lesser of this
     *         {@code Rational} and {@code val}. If they are equal, as defined by
     *         the {@link #compareTo(Rational) compareTo} method, {@code this} is
     *         returned.
     * @see #compareTo(Rational)
     */
    public Rational min(Rational val) {
        return (compareTo(val) <= 0 ? this : val);
    }

    /**
     * Returns the maximum of this {@code Rational} and {@code val}.
     *
     * @param val value with which the maximum is to be computed.
     * @return the {@code Rational} whose value is the greater of this
     *         {@code Rational} and {@code val}. If they are equal, as defined by
     *         the {@link #compareTo(Rational) compareTo} method, {@code this} is
     *         returned.
     * @see #compareTo(Rational)
     */
    public Rational max(Rational val) {
        return (compareTo(val) >= 0 ? this : val);
    }
}
