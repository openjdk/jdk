
package java.math;

import java.util.Arrays;
import java.util.Objects;

public class Rational extends Number implements Comparable<Rational> {
    /**
     * The signum of this Rational: -1 for negative, 0 for zero, or 1 for positive.
     * Note that the Rational zero <em>must</em> have a signum of 0. This is
     * necessary to ensures that there is exactly one representation for each
     * Rational value.
     */
    private final int signum;

    /**
     * The absolute integer part of this Rational.
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
        // to the formulae in JLS, Section 20.10.22.
        long valBits = Double.doubleToLongBits(val);
        int sign = ((valBits >> 63) == 0 ? 1 : -1);
        int exponent = (int) ((valBits >> 52) & 0x7ffL);
        long significand = (exponent == 0 ? (valBits & ((1L << 52) - 1)) << 1
                : (valBits & ((1L << 52) - 1)) | (1L << 52));
        exponent -= 1075;
        // At this point, val == sign * significand * 2**exponent.

        if (significand == 0) {
            signum = 0;
            floor = BigInteger.ZERO;
            numerator = BigInteger.ZERO;
            denominator = BigInteger.ONE;
        } else {
            signum = sign;

            if (exponent < 0) {
                // Simplify even significands
                int zeros = Long.numberOfTrailingZeros(significand);
                significand >>= zeros;
                exponent += zeros;
                // now the significand and the denominator are relative primes
                // the significand is odd and the denominator is a power of two
            }

            // check the exponent again, may be changed
            if (exponent < 0) {
                int quot = significand >>> -exponent;
                floor = BigInteger.valueOf(quot);
                numerator = BigInteger.valueOf(significand - (quot << -exponent));
                denominator = BigInteger.ONE.shiftLeft(-exponent);
            } else {
                floor = BigInteger.valueOf(significand).shiftLeft(exponent);
                numerator = BigInteger.ZERO;
                denominator = BigInteger.ONE;
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
        this(val.signum, val.abs());
    }

    /**
     * Translates a {@code BigInteger} into a {@code Rational}, with the specified
     * signum. Assumes that the {@code BigInteger} is non-negative.
     *
     * @param val {@code BigInteger} value to be converted to {@code Rational}.
     */
    private Rational(int sign, BigInteger val) {
        signum = sign;
        floor = val;
        numerator = BigInteger.ZERO;
        denominator = BigInteger.ONE;
    }

    /**
     * Translates a {@code BigDecimal} into a {@code Rational}.
     *
     * @param val {@code BigDecimal} value to be converted to {@code Rational}.
     */
    public Rational(BigDecimal val) {
        signum = val.signum();

        if (signum == 0) {
            floor = BigInteger.ZERO;
            numerator = BigInteger.ZERO;
            denominator = BigInteger.ONE;
        } else {
            final int scale = val.scale();
            final BigInteger intVal = val.unscaledValue().abs();

            if (scale > 0) {
                Rational res = valueOf(signum, intVal, BigDecimal.bigTenToThe(scale));
                floor = res.floor;
                numerator = res.numerator;
                denominator = res.denominator;
            } else { // scale <= 0
                floor = BigDecimal.bigMultiplyPowerTen(intVal, -scale);
                numerator = BigInteger.ZERO;
                denominator = BigInteger.ONE;
            }
        }
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
    public Rational(char[] in, int offset, int len) {
        this(new BigDecimal(in, offset, len));
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
     * Translates the string representation of a decimal number into a
     * {@code Rational}. The string representation consists of an optional sign,
     * {@code '+'} (<code> '&#92;u002B'</code>) or {@code '-'}
     * (<code>'&#92;u002D'</code>), followed by a sequence of zero or more decimal
     * digits ("the integer"), optionally followed by a fraction, optionally
     * followed by an exponent.
     *
     * <p>
     * The fraction consists of a decimal point followed by zero or more decimal
     * digits. The string must contain at least one digit in either the integer or
     * the fraction. The number formed by the sign, the integer and the fraction is
     * referred to as the <i>significand</i>.
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
     * <dd>any character for which {@link Character#isDigit} returns {@code true},
     * including 0, 1, 2 ...
     * </dl>
     * </blockquote>
     *
     * <p>
     * The scale of the decimal number represented by the returned {@code Rational}
     * will be the number of digits in the fraction, or zero if the string contains
     * no decimal point, subject to adjustment for any exponent; if the string
     * contains an exponent, the exponent is subtracted from the scale. The value of
     * the resulting scale must lie between {@code Integer.MIN_VALUE} and
     * {@code Integer.MAX_VALUE}, inclusive.
     *
     * <p>
     * The character-to-digit mapping is provided by
     * {@link java.lang.Character#digit} set to convert to radix 10. The String may
     * not contain any extraneous characters (whitespace, for example).
     *
     * <p>
     * <b>Examples:</b><br>
     * The value of the returned {@code Rational} is equal to <i>significand</i>
     * &times; 10<sup>&nbsp;<i>exponent</i></sup>. For each string on the left, the
     * resulting representation [{@code denominator}/{@code numerator}] is shown on
     * the right.
     * 
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
     * @apiNote For values other than {@code float} and {@code double} NaN and
     *          &plusmn;Infinity, this constructor is compatible with the values
     *          returned by {@link Float#toString} and {@link Double#toString}. This
     *          is generally the preferred way to convert a {@code float} or
     *          {@code double} into a Rational, as it doesn't suffer from the
     *          unpredictability of the {@link #Rational(double)} constructor.
     *
     * @param val String representation of a decimal number.
     *
     * @throws NumberFormatException if {@code val} is not a valid representation of
     *                               a decimal number.
     */
    public Rational(String val) {
        this(val.toCharArray(), 0, val.length());
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
     * Returns a Rational whose value is represented by the fraction with the
     * specified numerator and denominator.
     * 
     * @param num the numerator of the fraction to represent
     * @param den the denominator of the fraction to represent
     * @return a Rational whose value is represented by the fraction with the
     *         specified numerator and denominator
     * @throws ArithmeticException if the specified denominator is zero
     */
    public static Rational valueOf(BigDecimal num, BigDecimal den) {
        if (den.signum() == 0)
            throw new ArithmeticException("Denominator is zero");

        if (num.signum() == 0)
            return ZERO;

        return new Rational(num).divide(new Rational(den));
    }

    /**
     * Returns a Rational whose value is represented by the fraction with the
     * specified numerator and denominator.
     * 
     * @param num the numerator of the fraction to represent
     * @param den the denominator of the fraction to represent
     * @return a Rational whose value is represented by the fraction with the
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
     * Returns a Rational whose value is represented by the fraction with the
     * specified numerator and denominator.
     * 
     * @param num the numerator of the fraction to represent
     * @param den the denominator of the fraction to represent
     * @return a Rational whose value is represented by the fraction with the
     *         specified numerator and denominator
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
     * Constructs a new Rational with the specified values. Assumes that the passed
     * values are always valid.
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
            if (resFloor.signum == 1) // abs(this) > abs(augend)
                resSign = signum;
            else { // abs(this) < abs(augend)
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
        // lcm(a, b) == a * b / gcd(a, b) => n/a == n * (b / gcd(a, b)) / lcm(a, b)
        // trying to pospone the overflow as as late as possible
        return new BigInteger[] { val.denominator.divide(gcd).multiply(numerator),
                denominator.divide(gcd).multiply(val.numerator) };
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

        BigInteger plainNum = floor.multiply(denominator).add(numerator);
        BigInteger[] quotAndRem = denominator.divideAndRemainder(plainNum);
        return valueOf(signum, quotAndRem[0], quotAndRem[1], plainNum);
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

        // (a + n / m)**2 == a**2 + 2 * a * (n / m) + (n / m)**2
        Rational res = ZERO;

        if (floor.signum == 1) {
            res = res.add(new Rational(1, floor.square())); // res += a**2

            if (numerator.signum == 1) {
                BigInteger gcd = floor.gcd(denominator);
                BigInteger numAug = floor.divide(gcd).multiply(numerator);
                BigInteger denAug = denominator.divide(gcd);

                // double the product
                if (denAug.testBit(0))
                    numAug = numAug.shiftLeft(1); // denominator is odd
                else
                    denAug = denAug.shiftRight(1); // denominator is even

                Rational aug = valueOf(1, numAug, denAug);
                res = res.add(aug); // res += 2 * a * (n / m)
            }
        }

        if (numerator.signum == 1) {
            Rational aug = new Rational(1, BigInteger.ZERO, numerator.square(), denominator.square());
            res = res.add(aug); // res += (n / m)**2
        }

        return res;
    }

    /**
     * Returns a {@code Rational} whose value is <code>(this<sup>n</sup>)</code>.
     *
     * {@code ZERO.pow(0)} returns {@link #ONE}.
     *
     * @param n power to raise this {@code Rational} to.
     * @return <code>this<sup>n</sup></code>
     */
    public Rational pow(int n) {
        if (n == 0)
            return ONE; // x**0 == 1

        int mag = Math.abs(n); // magnitude of n
        // ready to carry out power calculation...
        Rational acc = ONE; // accumulator
        boolean seenbit = false; // set once we've seen a 1-bit
        final int nBits = Integer.SIZE - 1; // 31

        for (int i = 1; i <= nBits; i++) { // for each bit [top bit ignored]
            mag <<= 1; // shift left 1 bit
            if (mag < 0) { // top bit is set
                seenbit = true; // OK, we're off
                acc = acc.multiply(this); // acc=acc*x
            }

            if (seenbit && i < nBits) // don't square at the last bit
                acc = acc.square(); // acc=acc*acc [square]
            // if (!seenbit) no point in squaring ONE
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
     * one half an ulp of the exact decimal value.
     *
     * @param mc the context to use.
     * @return the square root of {@code this}.
     * @throws ArithmeticException if {@code this} is less than zero.
     * @throws ArithmeticException if an exact result is requested
     *                             ({@code mc.getPrecision()==0}) and there is no
     *                             finite decimal expansion of the exact result
     * @throws ArithmeticException if
     *                             {@code (mc.getRoundingMode()==RoundingMode.UNNECESSARY})
     *                             and the exact result cannot fit in
     *                             {@code mc.getPrecision()} digits.
     * @see BigDecimal#sqrt()
     * @see BigInteger#sqrt()
     */
    public Rational sqrt(MathContext mc) {
        if (signum == -1)
            throw new ArithmeticException("Attempted square root of negative Rational");

        if (signum == 0)
            return ZERO;
        /*
         * The following code draws on the algorithm presented in
         * "Properly Rounded Variable Precision Square Root," Hull and Abrham, ACM
         * Transactions on Mathematical Software, Vol 11, No. 3, September 1985, Pages
         * 229-237.
         *
         * The Rational computational model differs from the one presented in the paper
         * in several ways: first Rational numbers aren't normalized, second many more
         * rounding modes are supported, including UNNECESSARY, and exact results can be
         * requested.
         *
         * The main steps of the algorithm below are as follows, first argument reduce
         * the value to the numerical range [1, 10) using the following relations:
         *
         * x = y * 10 ^ exp sqrt(x) = sqrt(y) * 10^(exp / 2) if exp is even sqrt(x) =
         * sqrt(y/10) * 10 ^((exp+1)/2) if exp is odd
         *
         * Then use Newton's iteration on the reduced value to compute the numerical
         * digits of the desired result.
         *
         * Finally, scale back to the desired exponent range and perform any adjustment
         * to get the preferred scale in the representation.
         */

        // To allow binary floating-point hardware to be used to get
        // approximately a 15 digit approximation to the square
        // root, it is helpful to instead normalize this so that
        // the significand portion is to right of the decimal
        // point.

        final int scaleAdjust;
        Rational working = this;
        if (floor.signum == 1) { // non-zero integer part
            int scale = -BigDecimal.bigDigitLength(floor);
            // scaleAdjust must be even
            scaleAdjust = scale & 1 == 0 ? scale : scale + 1;

            if (scaleAdjust != 0) {
                Rational mul = new Rational(1, BigInteger.ZERO, BigInteger.ONE, BigDecimal.bigTenToThe(-scaleAdjust));
                working = working.multiply(mul);
            }
        } else {
            BigDecimal num = new BigDecimal(numerator), den = new BigDecimal(denominator);
            int scale = den.precision() - num.precision();
            BigDecimal scaledNum = num.scaleByPowerOfTen(scale);

            // scaleAdjust must be even
            if (scale & 1 == 0)
                scaleAdjust = scale;
            else if (scaledNum.compareTo(den) >= 0) // fractional part >= 1
                scaleAdjust = scale - 1;
            else // fractional part < 1
                scaleAdjust = scale + 1;

            if (scaleAdjust != 0) {
                Rational mul = new Rational(1, BigDecimal.bigTenToThe(scaleAdjust));
                working = working.multiply(mul);
            }
        }

        assert // Verify 0.1 <= working < 10
        ONE_TENTH.compareTo(working) <= 0 && working.compareTo(TEN) < 0;

        // Use good ole' Math.sqrt to get the initial guess for
        // the Newton iteration, good to at least 15 decimal
        // digits. This approach does incur the cost of a
        //
        // Rational -> double -> Rational
        //
        // conversion cycle, but it avoids the need for several
        // Newton iterations in Rational arithmetic to get the
        // working answer to 15 digits of precision. If many fewer
        // than 15 digits were needed, it might be faster to do
        // the loop entirely in Rational arithmetic.
        //
        // (A double value might have as many as 17 decimal
        // digits of precision; it depends on the relative density
        // of binary and decimal numbers at different regions of
        // the number line.)
        //
        // (It would be possible to check for certain special
        // cases to avoid doing any Newton iterations. For
        // example, if the Rational -> double conversion was
        // known to be exact and the rounding mode had a
        // low-enough precision, the post-Newton rounding logic
        // could be applied directly.)

        Rational approx = new Rational(Math.sqrt(working.doubleValue()));
        int guessPrecision = 15;
        int originalPrecision = mc.getPrecision();
        int targetPrecision;

        // If an exact value is requested, it must only need about
        // half of the input digits to represent since multiplying
        // an N digit number by itself yield a 2N-1 digit or 2N
        // digit result.
        if (originalPrecision == 0) {
            BigDecimal stripped;
            try {
                stripped = toBigDecimalExact().stripTrailingZeros();
            } catch (ArithmeticException e) {
                // this Rational has a non-terminating decimal expansion
                throw new ArithmeticException("Computed square root not exact.");
            }

            targetPrecision = stripped.precision() / 2 + 1;
        } else {
            /*
             * To avoid the need for post-Newton fix-up logic, in the case of half-way
             * rounding modes, double the target precision so that the "2p + 2" property can
             * be relied on to accomplish the final rounding.
             */
            switch (mc.getRoundingMode()) {
            case HALF_UP:
            case HALF_DOWN:
            case HALF_EVEN:
                targetPrecision = 2 * originalPrecision;
                if (targetPrecision < 0) // Overflow
                    targetPrecision = Integer.MAX_VALUE - 2;
                break;

            default:
                targetPrecision = originalPrecision;
                break;
            }
        }

        do {
            // approx = 0.5 * (approx + fraction / approx)
            approx = working.divide(approx).add(approx).multiply(ONE_HALF);
            guessPrecision <<= 1;
        } while (guessPrecision < targetPrecision + 2);

        Rational result = approx;

        if (scaleAdjust != 0)
            if (floor.signum == 1) { // non-zero integer part
                Rational mul = new Rational(1, BigDecimal.bigTenToThe(-scaleAdjust / 2));
                result = result.multiply(mul);
            } else {
                Rational mul = new Rational(1, BigInteger.ZERO, BigInteger.ONE,
                        BigDecimal.bigTenToThe(scaleAdjust / 2));
                result = result.multiply(mul);
            }

        BigDecimal decimalRes;
        RoundingMode targetRm = mc.getRoundingMode();
        if (targetRm == RoundingMode.UNNECESSARY || originalPrecision == 0) {
            RoundingMode tmpRm = (targetRm == RoundingMode.UNNECESSARY) ? RoundingMode.DOWN : targetRm;
            MathContext mcTmp = new MathContext(targetPrecision, tmpRm);
            decimalRes = result.toBigDecimal(mcTmp); // round with mcTmp
            result = new Rational(decimalRes);

            // If result*result != this numerically, the square root isn't exact
            if (result.square().compareTo(this) != 0)
                throw new ArithmeticException("Computed square root not exact.");
        } else {
            decimalRes = result.toBigDecimal(mc); // round with mc
            result = new Rational(decimalRes);

            switch (targetRm) {
            case DOWN:
            case FLOOR:
                // Check if too big
                if (result.square().compareTo(this) > 0) {
                    BigDecimal ulp = decimalRes.ulp();
                    // Adjust increment down in case of 1.0 = 10^0
                    // since the next smaller number is only 1/10
                    // as far way as the next larger at exponent
                    // boundaries. Test approx and *not* result to
                    // avoid having to detect an arbitrary power
                    // of ten.
                    if (approx.compareTo(ONE) == 0)
                        ulp = ulp.multiply(BigDecimal.ONE_TENTH);

                    result = result.subtract(new Rational(ulp));
                    decimalRes = decimalRes.subtract(ulp);
                }
                break;

            case UP:
            case CEILING:
                // Check if too small
                if (result.square().compareTo(this) < 0) {
                    BigDecimal ulp = decimalRes.ulp();
                    result = result.add(new Rational(ulp));
                    decimalRes = decimalRes.add(ulp);
                }

                break;
            // No additional work, rely on "2p + 2" property
            // for correct rounding. Alternatively, could
            // instead run the Newton iteration to around p
            // digits and then do tests and fix-ups on the
            // rounded value. One possible set of tests and
            // fix-ups is given in the Hull and Abrham paper;
            // however, additional half-way cases can occur
            // for Rational given the more varied
            // combinations of input and output precisions
            // supported.
            }

        }

        // Test numerical properties
        assert BigDecimal.squareRootResultAssertions(decimalRes, mc);
        return result;
    }

    // Scaling/Rounding Operations

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
        return new Rational(toBigDecimal(mc));
    }

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

    // Format Converters

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
     * To have an exception thrown if the conversion is inexact (in other words if a
     * nonzero fractional part is discarded), use the {@link #toBigDecimalExact()}
     * method.
     *
     * @param mc the context to use.
     * @return this {@code Rational} converted to a {@code BigDecimal}.
     */
    public BigDecimal toBigDecimal(MathContext mc) {
        BigDecimal decimalPart = new BigDecimal(numerator).divide(new BigDecimal(denominator), mc);
        BigDecimal absVal = new BigDecimal(floor).add(decimalPart);
        return signum == -1 ? absVal.negate() : absVal;
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
    publBigDecimalmal toBigDecimalExact() {
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
        return toBigDecimal(MathContext.DECIMAL64).doubleValue();
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
        return toBigDecimal(MathContext.DECIMAL32).floatValue();
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
     * Returns the decimal String representation of this Rational:
     * <i>numerator</i>/<i>denominator</i>.
     * 
     * The digit-to-character mapping provided by {@code Character.forDigit} is
     * used, and a minus sign is prepended if appropriate. (This representation is
     * compatible with the {@link #Rational(String) (String)} constructor, and
     * allows for String concatenation with Java's + operator.)
     *
     * @return decimal String representation of this Rational.
     * @see Character#forDigit
     * @see #Rational(java.lang.String)
     */
    @Override
    public String toString() {
        final int radix = 10;
        int capacity = floor.numChars(radix);
        capacity += numerator.numChars(radix);
        capacity += denominator.numChars(radix);
        capacity += 2 + (signum == -1 ? 1 : 0);
        StringBuilder b = new StringBuilder(capacity);

        if (signum == -1)
            b.append('-');

        b.append(floor).append(signum == -1 ? '-' : '+').append(numerator).append('/').append(denominator);
        return b.toString();
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
        final int absComp = floor.compareTo(val.floor);

        if (absComp == 0) { // values have the same floor
            if (denominator.equals(val.denominator))
                absComp = numerator.compareTo(val.numerator);
            else if (numerator.equals(val.numerator))
                absComp = val.denominator.compareTo(denominator); // a/b < a/c <=> c < b
            else {
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