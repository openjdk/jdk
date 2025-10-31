/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.invoke;

/**
 * Methods corresponding to operators on primitive types in the Java
 * programming language or instructions operating on primitive types in
 * the Java virtual machine.
 *
 * @see java.lang.runtime.ExactConversionsSupport
 * @see java.util.Objects#isNull(Object)
 * @see java.util.Objects#nonNull(Object)
 *
 * @jls 4.2.2 Integer Operations
 * @jls 4.2.4 Floating-Point Operations
 */
public class Operators {
    private  Operators(){throw new AssertionError("No Operators instances for you");}

    /*
     * Quoting from JLS:
     *
     * 4.2.2. Integer Operations
     *
     * The Java programming language provides a number of operators that act on integral values:
     *
     * * The comparison operators, which result in a value of type boolean:
     *   * The numerical comparison operators <, <=, >, and >= (&sect;15.20.1)
     *   * The numerical equality operators == and != (&sect;15.21.1)
     * * The numerical operators, which result in a value of type int or long:
     *   * The unary plus and minus operators + and - (&sect;15.15.3, &sect;15.15.4)
     *   * The multiplicative operators *, /, and % (&sect;15.17)
     *   * The additive operators + and - (&sect;15.18)
     *   * The increment operator ++, both prefix (&sect;15.15.1) and postfix (&sect;15.14.2)
     *   * The decrement operator --, both prefix (&sect;15.15.2) and postfix (&sect;15.14.3)
     *   * The signed and unsigned shift operators <<, >>, and >>> (&sect;15.19)
     *   * The bitwise complement operator ~ (&sect;15.15.5)
     *   * The integer bitwise operators &, ^, and | (&sect;15.22.1)
     *   * The conditional operator ? : (&sect;15.25)
     *   * The cast operator (&sect;15.16), which can convert from an
     *     integral value to a value of any specified numeric type
     */

    // Comparison operators

    /**
     * {@return whether or not the first argument is less than the second argument}
     *
     * @implSpec
     * This method wraps the {@code <} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean lessThan(int a, int b) {return a < b;}

    /**
     * {@return whether or not the first argument is less than or
     * equal to the second argument}
     *
     * @implSpec
     * This method wraps the {@code <=} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean lessThanEqual(int a, int b) {return a <= b;}

    /**
     * {@return whether or not the first argument is greater than the second argument}
     *
     * @implSpec
     * This method wraps the {@code >} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean greaterThan(int a, int b) {return a > b;}

    /**
     * {@return whether or not the first argument is greater than or
     * equal to the second argument}
     *
     * @implSpec
     * This method wraps the {@code <=} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean greaterThanEqual(int a, int b) {return a >= b;}

    // icomp

    /**
     * {@return whether or not the first argument is equal to the second argument}
     *
     * @implSpec
     * This method wraps the {@code ==} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean equal(int a, int b) {return a == b;}

    /**
     * {@return whether or not the first argument is not equal to the second argument}
     *
     * @implSpec
     * This method wraps the {@code !=} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean notEqual(int a, int b) {return a != b;}

    // Numerical operators

    /**
     * {@return the unary plus of the argument}
     *
     * @implSpec
     * This method wraps the unary {@code +} operator on {@code int} augment.
     *
     * @param a the argument
     */
    public static int plus(int a) {return +a;} // Just for completeness; don't need this.

    /**
     * {@return the unary negation of the argument}
     *
     * @implSpec
     * This method wraps the unary {@code -} operator on {@code int} augment.
     *
     * @param a the argument
     */
    public static int negate(int a) {return -a;}

    // Multiplicative operators

    /**
     * {@return the product of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code *} operator on {@code int} augments.
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     */
    public static int multiply(int multiplier, int multiplicand) {return multiplier * multiplicand;}

    /**
     * {@return the quotient of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code /} operator on {@code int} augments.
     *
     * @param dividend the first operand
     * @param divisor the second operand
     */
    public static int divide(int dividend, int divisor) {return dividend / divisor;}

    /**
     * {@return the remainder of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code %} operator on {@code int} augments.
     *
     * @param dividend the first operand
     * @param divisor the second operand
     */
    public static int remainder(int dividend, int divisor) {return dividend % divisor;}


    // Additive operators

    /**
     * {@return the sum of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code +} operator on {@code int} augments.
     *
     * @param addend the first operand
     * @param augend the second operand
     */
    public static int add(int addend, int augend) {return addend + augend;}

    /**
     * {@return the difference of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code -} operator on {@code int} augments.
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     */
    public static int substract(int minuend, int subtrahend) {return minuend - subtrahend;}

    // Increment/decrement operators

    /**
     * {@return the operand incremented by 1}
     *
     * @implSpec
     * This method wraps the {@code ++} operator on an {@code int} augment.
     *
     * @param a the operand
     */
    public static int increment(int a) {return a++;}

    /**
     * {@return the operand incremented by 1}
     *
     * @implSpec
     * This method wraps the {@code --} operator on an {@code int} augment.
     *
     * @param a the operand
     */
    public static int decrement(int a) {return a--;}

    // Shift operators

    /**
     * {@return the first argument left shifted by the shift distance of the second argument}
     *
     * @implSpec
     * This method wraps the {@code >>} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param shiftDistance shift distance in bits
     */
    public static int shiftLeft(int a, int shiftDistance) {return a << shiftDistance;}

    /**
     * {@return the first argument right shifted by the shift distance of the second argument}
     *
     * @implSpec
     * This method wraps the {@code >>} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param shiftDistance shift distance in bits
     */
    public static int shiftRight(int a, int shiftDistance) {return a >> shiftDistance;}

    /**
     * {@return the first argument unsigned right shifted by the shift distance of the second argument}
     *
     * @implSpec
     * This method wraps the {@code >>>} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param shiftDistance shift distance in bits
     */
    public static int shiftRightUnsigned(int a, int shiftDistance) {return a >>> shiftDistance;}


    // Bitwise operators

    /**
     * {@return the complement of the argument}
     *
     * @implSpec
     * This method wraps the {@code ~} operator applied to the {@code int} augment.
     *
     * @param a the argument
     */
    public static int complement(int a) {return ~a;}

    /**
     * {@return the arguments AND'ed together}
     *
     * @implSpec
     * This method wraps the {@code &} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static int and(int a, int b) {return a & b;}

    /**
     * {@return the arguments OR'ed together}
     *
     * @implSpec
     * This method wraps the {@code |} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static int or(int a, int b) {return a | b;}

    /**
     * {@return the arguments XOR'ed together}
     *
     * @implSpec
     * This method wraps the {@code ^} operator on {@code int} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static int xor(int a, int b) {return a ^ b;}


    // casting -- i2b, i2c, i2d, i2f, i2l, i2s

    /**
     * {@return the result of casting the argument to {@code byte}}
     *
     * @apiNote
     * This method corresponds to the {@code i2b} JVM instruction.
     *
     * @param a the argument to convert to {@code byte}
     */
    public static byte i2b(int a)   {return (byte)  a;}

    /**
     * {@return the result of casting the argument to {@code char}}
     *
     * @apiNote
     * This method corresponds to the {@code i2c} JVM instruction.
     *
     * @param a the argument to convert to {@code char}
     */
    public static char i2c(int a)   {return (char)  a;}

    /**
     * {@return the result of casting the argument to {@code double}}
     *
     * @apiNote
     * This method corresponds to the {@code i2d} JVM instruction.
     *
     * @param a the argument to convert to {@code double}
     */
    public static double i2d(int a) {return (double)a;}

    /**
     * {@return the result of casting the argument to {@code float}}
     *
     * @apiNote
     * This method corresponds to the {@code i2f} JVM instruction.
     *
     * @param a the argument to convert to {@code float}
     */
    public static float i2f(int a)  {return (float) a;}

    /**
     * {@return the result of casting the argument to {@code long}}
     *
     * @apiNote
     * This method corresponds to the {@code i2l} JVM instruction.
     *
     * @param a the argument to convert to {@code long}
     */
    public static long i2l(int a)   {return (long)  a;}

    /**
     * {@return the result of indexing into the argument array}
     *
     * @apiNote
     * This method corresponds to the {@code iaload} JVM instruction.
     *
     * @param array the argument to convert to index into
     * @param index the index
     */
    public static int iaload(int[] array, int index) {return array[index];}

    /**
     * Store a value into the specified index of the argument array.
     *
     * @apiNote
     * This method corresponds to the {@code iastore} JVM instruction.
     *
     * @param array the argument to convert to index into
     * @param index the index
     * @param value the value to store into the aray
     */
    public static void iastore(int[] array, int index, int value) {array[index] = value; }

    // TODO add methods for long
    // casting -- l2d, l2f, l2i

    // Floating-point

    /*
     * Quoting from JLS:
     *
     * The Java programming language provides a number of operators that act on floating-point values:
     *
     * * The comparison operators, which result in a value of type boolean:
     *   * The numerical comparison operators <, <=, >, and >= (&sect;15.20.1)
     *   * The numerical equality operators == and != (&sect;15.21.1)
     * * The numerical operators, which result in a value of type float or double:
     *   * The unary plus and minus operators + and - (&sect;15.15.3, &sect;15.15.4)
     *   * The multiplicative operators *, /, and % (&sect;15.17)
     *   * The additive operators + and - (&sect;15.18.2)
     *   * The increment operator ++, both prefix (&sect;15.15.1) and postfix (&sect;15.14.2)
     *   * The decrement operator --, both prefix (&sect;15.15.2) and postfix (&sect;15.14.3)
     * * The conditional operator ? : (&sect;15.25)
     * * The cast operator (&sect;15.16), which can convert from a
     *   floating-point value to a value of any specified numeric type
     * * The string concatenation operator + (&sect;15.18.1), which, when
     *   given a String operand and a floating-point operand, will
     *   convert the floating-point operand to a String representing
     *   its value in decimal form (without information loss), and
     *   then produce a newly created String by concatenating the two
     *   strings
     */

    // Comparison operators

    /**
     * {@return whether or not the first argument is less than the second argument}
     *
     * @implSpec
     * This method wraps the {@code <} operator on {@code float} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean lessThan(float a, float b) {return a < b;}

    /**
     * {@return whether or not the first argument is less than or
     * equal to the second argument}
     *
     * @implSpec
     * This method wraps the {@code <=} operator on {@code float} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean lessThanEqual(float a, float b) {return a <= b;}

    /**
     * {@return whether or not the first argument is greater than the second argument}
     *
     * @implSpec
     * This method wraps the {@code >} operator on {@code float} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean greaterThan(float a, float b) {return a > b;}

    /**
     * {@return whether or not the first argument is greater than or
     * equal to the second argument}
     *
     * @implSpec
     * This method wraps the {@code <=} operator on {@code float} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean greaterThanEqual(float a, float b) {return a >= b;}

    /**
     * {@return whether or not the first argument is equal to the second argument}
     *
     * @implSpec
     * This method wraps the {@code ==} operator on {@code float} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean equal(float a, float b) {return a == b;}

    /**
     * {@return whether or not the first argument is not equal to the second argument}
     *
     * @implSpec
     * This method wraps the {@code !=} operator on {@code float} augments.
     *
     * @param a the first argument
     * @param b the second argument
     */
    public static boolean notEqual(float a, float b) {return a != b;}

    // Include fcompg, fcompl?

    // Numerical operators

    /**
     * {@return the unary plus of the argument}
     *
     * @implSpec
     * This method wraps the unary {@code +} operator on {@code float} augment.
     *
     * @param a the argument
     */
    public static float plus(float a) {return +a;} // Just for completeness; don't need this.

    /**
     * {@return the unary negation of the argument}
     *
     * @implSpec
     * This method wraps the unary {@code -} operator on {@code float} augment.
     *
     * @param a the argument
     */
    public static float negate(float a) {return -a;}

    // Multiplicative operators

    /**
     * {@return the product of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code *} operator on {@code float} augments.
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     */
    public static float multiply(float multiplier, float multiplicand) {return multiplier * multiplicand;}

    /**
     * {@return the quotient of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code /} operator on {@code float} augments.
     *
     * @param dividend the first operand
     * @param divisor the second operand
     */
    public static float divide(float dividend, float divisor) {return dividend / divisor;}

    /**
     * {@return the remainder of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code %} operator on {@code float} augments.
     *
     * @param dividend the first operand
     * @param divisor the second operand
     */
    public static float remainder(float dividend, float divisor) {return dividend % divisor;}


    // Additive operators

    /**
     * {@return the sum of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code +} operator on {@code float} augments.
     *
     * @param addend the first operand
     * @param augend the second operand
     */
    public static float add(float addend, float augend) {return addend + augend;}

    /**
     * {@return the difference of the two operands}
     *
     * @implSpec
     * This method wraps the binary {@code -} operator on {@code float} augments.
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     */
    public static float substract(float minuend, float subtrahend) {return minuend - subtrahend;}

    // Increment/decrement operators

    /**
     * {@return the operand incremented by 1}
     *
     * @implSpec
     * This method wraps the {@code ++} operator on an {@code float} augment.
     *
     * @param a the operand
     */
    public static float increment(float a) {return a++;}

    /**
     * {@return the operand incremented by 1}
     *
     * @implSpec
     * This method wraps the {@code --} operator on an {@code float} augment.
     *
     * @param a the operand
     */
    public static float decrement(float a) {return a--;}

    // casting -- f2d, f2i, f2l

    /**
     * {@return the result of casting the argument to {@code double}}
     *
     * @apiNote
     * This method corresponds to the {@code f2d} JVM instruction.
     *
     * @param a the argument to convert to {@code double}
     */
    public static double f2d(float a) {return (double)a;}

    /**
     * {@return the result of casting the argument to {@code int}}
     *
     * @apiNote
     * This method corresponds to the {@code f2i} JVM instruction.
     *
     * @param a the argument to convert to {@code int}
     */
    public static int f2i(float a)   {return (int)    a;}

    /**
     * {@return the result of casting the argument to {@code long}}
     *
     * @apiNote
     * This method corresponds to the {@code f2l} JVM instruction.
     *
     * @param a the argument to convert to {@code long}
     */
    public static long f2l(float a)   {return (long)  a;}

    /**
     * {@return the result of indexing into the argument array}
     *
     * @apiNote
     * This method corresponds to the {@code faload} JVM instruction.
     *
     * @param array the argument to convert to index into
     * @param index the index
     */
    public static float faload(float[] array, int index) {return array[index];}

    /**
     * Store a value into the specified index of the argument array.
     *
     * @apiNote
     * This method corresponds to the {@code fastore} JVM instruction.
     *
     * @param array the argument to convert to index into
     * @param index the index
     * @param value the value to store into the aray
     */
    public static void fastore(float[] array, int index, float value) {array[index] = value; }


    // TODO add methods for double
    // casting -- d2f, d2i, d2l

    // Instructions

    /**
     * Throw the argument; does not return normally.
     *
     * @apiNote
     * This method corresponds to the {@code athrow} JVM instruction.
     *
     * @param t the throwable to throw
     * @throws Throwable unconditionally
     */
    public static void athrow(Throwable t) throws Throwable {
        throw t;
    }

    // Instructions that don't have primitive operators

    // arraylength  -- add all the overloads? int[], ..., Object[]

    // checkcast
    
    // ifnonnnull -- see Objects::nonNull

    // ifnull -- see Objects::isNull

    // instanceof

    /**
     * No-operation.
     *
     * @apiNote
     * This method corresponds to the {@code nop} JVM instruction.
     */
    public void nop() {return;}
}
