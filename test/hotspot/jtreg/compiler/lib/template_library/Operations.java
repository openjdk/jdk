/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_library;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;

// TODO: find more operations, like in Math.java or ExactMath.java
final class Operations {

    private static final List<Operation> BYTE_OPERATIONS = List.of(
        // Note: the standard integer arithmetic operations are only defined for int/long.
        //       They can be used for smaller types only via automatic promotion to int,
        //       and then a cast back to byte, e.g:
        //           byte a = (byte)(b + c)
        //
        //       Instead of adding these operations explicitly, we just add the conversion
        //       from int to byte, and let the IntType generate all the integer arithmetic
        //       operations.
        new Operation.Unary(Type.bytes(), "((byte)", Type.chars(), ")", null),
        new Operation.Unary(Type.bytes(), "((byte)", Type.shorts(), ")", null),
        new Operation.Unary(Type.bytes(), "((byte)", Type.ints(), ")", null),
        new Operation.Unary(Type.bytes(), "((byte)", Type.longs(), ")", null),
        new Operation.Unary(Type.bytes(), "((byte)", Type.floats(), ")", null),
        new Operation.Unary(Type.bytes(), "((byte)", Type.doubles(), ")", null),
        // Note: There is no cast from boolean

        new Operation.Ternary(Type.bytes(), "(", Type.booleans(), " ? ", Type.bytes(), " : ", Type.bytes(), ")", null)
    );

    private static final List<Operation> CHAR_OPERATIONS = List.of(
        // Note: the standard integer arithmetic operations are only defined for int/long.
        //       They can be used for smaller types only via automatic promotion to int,
        //       and then a cast back to char, e.g:
        //           char a = (char)(b + c)
        //
        //       Instead of adding these operations explicitly, we just add the conversion
        //       from int to char, and let the IntType generate all the integer arithmetic
        //       operations.
        new Operation.Unary(Type.chars(), "((char)", Type.bytes(), ")", null),
        new Operation.Unary(Type.chars(), "((char)", Type.shorts(), ")", null),
        new Operation.Unary(Type.chars(), "((char)", Type.ints(), ")", null),
        new Operation.Unary(Type.chars(), "((char)", Type.longs(), ")", null),
        new Operation.Unary(Type.chars(), "((char)", Type.floats(), ")", null),
        new Operation.Unary(Type.chars(), "((char)", Type.doubles(), ")", null),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.chars(), "Character.reverseBytes(", Type.chars(), ")", null),

        new Operation.Ternary(Type.chars(), "(", Type.booleans(), " ? ", Type.chars(), " : ", Type.chars(), ")", null)
    );

    private static final List<Operation> SHORT_OPERATIONS = List.of(
        // Note: the standard integer arithmetic operations are only defined for int/long.
        //       They can be used for smaller types only via automatic promotion to int,
        //       and then a cast back to short, e.g:
        //           short a = (short)(b + c)
        //
        //       Instead of adding these operations explicitly, we just add the conversion
        //       from int to short, and let the IntType generate all the integer arithmetic
        //       operations.
        new Operation.Unary(Type.shorts(), "((short)", Type.bytes(), ")", null),
        new Operation.Unary(Type.shorts(), "((short)", Type.chars(), ")", null),
        new Operation.Unary(Type.shorts(), "((short)", Type.ints(), ")", null),
        new Operation.Unary(Type.shorts(), "((short)", Type.longs(), ")", null),
        new Operation.Unary(Type.shorts(), "((short)", Type.floats(), ")", null),
        new Operation.Unary(Type.shorts(), "((short)", Type.doubles(), ")", null),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.shorts(), "Short.reverseBytes(", Type.shorts(), ")", null),

        // Note: Float.floatToFloat16 can lead to issues, because NaN values are not always
        //       represented by the same short bits.

        new Operation.Ternary(Type.shorts(), "(", Type.booleans(), " ? ", Type.shorts(), " : ", Type.shorts(), ")", null)
    );

    private static final List<Operation> INT_OPERATIONS = List.of(
        new Operation.Unary(Type.ints(), "((int)", Type.bytes(), ")", null),
        new Operation.Unary(Type.ints(), "((int)", Type.chars(), ")", null),
        new Operation.Unary(Type.ints(), "((int)", Type.shorts(), ")", null),
        new Operation.Unary(Type.ints(), "((int)", Type.longs(), ")", null),
        new Operation.Unary(Type.ints(), "((int)", Type.floats(), ")", null),
        new Operation.Unary(Type.ints(), "((int)", Type.doubles(), ")", null),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.ints(), "(-(", Type.ints(), "))", null),
        new Operation.Unary(Type.ints(), "(~", Type.ints(), ")", null),

        new Operation.Binary(Type.ints(), "(", Type.ints(), " + ",   Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " - ",   Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " * ",   Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " / ",   Type.ints(), ")", List.of("ArithmeticException")),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " % ",   Type.ints(), ")", List.of("ArithmeticException")),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " & ",   Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " | ",   Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " ^ ",   Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " << ",  Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " >> ",  Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " >>> ", Type.ints(), ")", null),

        new Operation.Binary(Type.ints(), "Byte.compare(", Type.bytes(), ", ", Type.bytes(), ")", null),
        new Operation.Binary(Type.ints(), "Byte.compareUnsigned(", Type.bytes(), ", ", Type.bytes(), ")", null),
        new Operation.Unary(Type.ints(), "Byte.toUnsignedInt(", Type.bytes(), ")", null),

        new Operation.Binary(Type.ints(), "Character.compare(", Type.chars(), ", ", Type.chars(), ")", null),

        new Operation.Binary(Type.ints(), "Short.compare(", Type.shorts(), ", ", Type.shorts(), ")", null),
        new Operation.Binary(Type.ints(), "Short.compareUnsigned(", Type.shorts(), ", ", Type.shorts(), ")", null),
        new Operation.Unary(Type.ints(), "Short.toUnsignedInt(", Type.shorts(), ")", null),

        new Operation.Unary(Type.ints(), "Integer.bitCount(", Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "Integer.compare(", Type.ints(), ", ", Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "Integer.compareUnsigned(", Type.ints(), ", ", Type.ints(), ")", null),
        //new Operation.Binary(Type.ints(), "Integer.compress(", Type.ints(), ", ", Type.ints(), ")", null),
        // TODO: add back after JDK-8350896
        new Operation.Binary(Type.ints(), "Integer.divideUnsigned(", Type.ints(), ", ", Type.ints(), ")", List.of("ArithmeticException")),
        new Operation.Binary(Type.ints(), "Integer.expand(", Type.ints(), ", ", Type.ints(), ")", null),
        new Operation.Unary(Type.ints(), "Integer.highestOneBit(", Type.ints(), ")", null),
        new Operation.Unary(Type.ints(), "Integer.lowestOneBit(", Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "Integer.min(", Type.ints(), ", ", Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "Integer.max(", Type.ints(), ", ", Type.ints(), ")", null),
        new Operation.Unary(Type.ints(), "Integer.numberOfLeadingZeros(", Type.ints(), ")", null),
        new Operation.Unary(Type.ints(), "Integer.numberOfTrailingZeros(", Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "Integer.remainderUnsigned(", Type.ints(), ", ", Type.ints(), ")", List.of("ArithmeticException")),
        new Operation.Unary(Type.ints(), "Integer.reverse(", Type.ints(), ")", null),
        new Operation.Unary(Type.ints(), "Integer.reverseBytes(", Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "Integer.rotateLeft(", Type.ints(), ", ", Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "Integer.rotateRight(", Type.ints(), ", ", Type.ints(), ")", null),
        new Operation.Unary(Type.ints(), "Integer.signum(", Type.ints(), ")", null),
        new Operation.Binary(Type.ints(), "Integer.sum(", Type.ints(), ", ", Type.ints(), ")", null),

        new Operation.Unary(Type.ints(), "Long.bitCount(", Type.longs(), ")", null),
        new Operation.Binary(Type.ints(), "Long.compare(", Type.longs(), ", ", Type.longs(), ")", null),
        new Operation.Binary(Type.ints(), "Long.compareUnsigned(", Type.longs(), ", ", Type.longs(), ")", null),
        new Operation.Unary(Type.ints(), "Long.numberOfLeadingZeros(", Type.longs(), ")", null),
        new Operation.Unary(Type.ints(), "Long.numberOfTrailingZeros(", Type.longs(), ")", null),
        new Operation.Unary(Type.ints(), "Long.signum(", Type.longs(), ")", null),

        new Operation.Binary(Type.ints(), "Float.compare(", Type.floats(), ", ", Type.floats(), ")", null),
        new Operation.Unary(Type.ints(), "Float.floatToIntBits(", Type.floats(), ")", null),
        // Note: Float.floatToRawIntBits can lead to issues, because the NaN values are not always
        //       represented by the same int bits.

        new Operation.Binary(Type.ints(), "Double.compare(", Type.doubles(), ", ", Type.doubles(), ")", null),

        new Operation.Binary(Type.ints(), "Boolean.compare(", Type.booleans(), ", ", Type.booleans(), ")", null),

        new Operation.Ternary(Type.ints(), "(", Type.booleans(), " ? ", Type.ints(), " : ", Type.ints(), ")", null)
    );

    private static final List<Operation> LONG_OPERATIONS = List.of(
        new Operation.Unary(Type.longs(), "((long)", Type.bytes(), ")", null),
        new Operation.Unary(Type.longs(), "((long)", Type.chars(), ")", null),
        new Operation.Unary(Type.longs(), "((long)", Type.shorts(), ")", null),
        new Operation.Unary(Type.longs(), "((long)", Type.ints(), ")", null),
        new Operation.Unary(Type.longs(), "((long)", Type.floats(), ")", null),
        new Operation.Unary(Type.longs(), "((long)", Type.doubles(), ")", null),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.longs(), "(-(", Type.longs(), "))", null),
        new Operation.Unary(Type.longs(), "(~", Type.longs(), ")", null),

        new Operation.Binary(Type.longs(), "(", Type.longs(), " + ",   Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " - ",   Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " * ",   Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " / ",   Type.longs(), ")", List.of("ArithmeticException")),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " % ",   Type.longs(), ")", List.of("ArithmeticException")),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " & ",   Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " | ",   Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " ^ ",   Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " << ",  Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " >> ",  Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " >>> ", Type.longs(), ")", null),

        new Operation.Unary(Type.longs(), "Byte.toUnsignedLong(", Type.bytes(), ")", null),

        new Operation.Unary(Type.longs(), "Short.toUnsignedLong(", Type.shorts(), ")", null),

        new Operation.Unary(Type.longs(), "Integer.toUnsignedLong(", Type.ints(), ")", null),

        // new Operation.Binary(Type.longs(), "Long.compress(", Type.longs(), ", ", Type.longs(), ")", null),
        // TODO: add back after JDK-8350896
        new Operation.Binary(Type.longs(), "Long.divideUnsigned(", Type.longs(), ", ", Type.longs(), ")", List.of("ArithmeticException")),
        new Operation.Binary(Type.longs(), "Long.expand(", Type.longs(), ", ", Type.longs(), ")", null),
        new Operation.Unary(Type.longs(), "Long.highestOneBit(", Type.longs(), ")", null),
        new Operation.Unary(Type.longs(), "Long.lowestOneBit(", Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "Long.min(", Type.longs(), ", ", Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "Long.max(", Type.longs(), ", ", Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "Long.remainderUnsigned(", Type.longs(), ", ", Type.longs(), ")", List.of("ArithmeticException")),
        new Operation.Unary(Type.longs(), "Long.reverse(", Type.longs(), ")", null),
        new Operation.Unary(Type.longs(), "Long.reverseBytes(", Type.longs(), ")", null),
        new Operation.Binary(Type.longs(), "Long.rotateLeft(", Type.longs(), ", ", Type.ints(), ")", null),
        new Operation.Binary(Type.longs(), "Long.rotateRight(", Type.longs(), ", ", Type.ints(), ")", null),
        new Operation.Binary(Type.longs(), "Long.sum(", Type.longs(), ", ", Type.longs(), ")", null),

        new Operation.Unary(Type.longs(), "Double.doubleToLongBits(", Type.doubles(), ")", null),
        // Note: Double.doubleToRawLongBits can lead to issues, because the NaN values are not always
        //       represented by the same long bits.

        new Operation.Ternary(Type.longs(), "(", Type.booleans(), " ? ", Type.longs(), " : ", Type.longs(), ")", null)
    );

    private static final List<Operation> FLOAT_OPERATIONS = List.of(
        new Operation.Unary(Type.floats(), "((float)", Type.bytes(), ")", null),
        new Operation.Unary(Type.floats(), "((float)", Type.chars(), ")", null),
        new Operation.Unary(Type.floats(), "((float)", Type.shorts(), ")", null),
        new Operation.Unary(Type.floats(), "((float)", Type.ints(), ")", null),
        new Operation.Unary(Type.floats(), "((float)", Type.longs(), ")", null),
        new Operation.Unary(Type.floats(), "((float)", Type.doubles(), ")", null),

        new Operation.Unary(Type.floats(), "(-(", Type.floats(), "))", null),

        new Operation.Binary(Type.floats(), "(", Type.floats(), " + ",   Type.floats(), ")", null),
        new Operation.Binary(Type.floats(), "(", Type.floats(), " - ",   Type.floats(), ")", null),
        new Operation.Binary(Type.floats(), "(", Type.floats(), " * ",   Type.floats(), ")", null),
        new Operation.Binary(Type.floats(), "(", Type.floats(), " / ",   Type.floats(), ")", null),
        new Operation.Binary(Type.floats(), "(", Type.floats(), " % ",   Type.floats(), ")", null),

        //new Operation.Unary(Type.floats(), "Float.float16ToFloat(", Type.shorts(), ")", null),
        // TODO: add back after JDK-8350835
        new Operation.Unary(Type.floats(), "Float.intBitsToFloat(", Type.ints(), ")", null),
        new Operation.Binary(Type.floats(), "Float.max(", Type.floats(), ", ", Type.floats(), ")", null),
        new Operation.Binary(Type.floats(), "Float.min(", Type.floats(), ", ", Type.floats(), ")", null),
        new Operation.Binary(Type.floats(), "Float.sum(", Type.floats(), ", ", Type.floats(), ")", null),

        new Operation.Ternary(Type.floats(), "(", Type.booleans(), " ? ", Type.floats(), " : ", Type.floats(), ")", null)
    );

    private static final List<Operation> DOUBLE_OPERATIONS = List.of(
        new Operation.Unary(Type.doubles(), "((double)", Type.bytes(), ")", null),
        new Operation.Unary(Type.doubles(), "((double)", Type.chars(), ")", null),
        new Operation.Unary(Type.doubles(), "((double)", Type.shorts(), ")", null),
        new Operation.Unary(Type.doubles(), "((double)", Type.ints(), ")", null),
        new Operation.Unary(Type.doubles(), "((double)", Type.longs(), ")", null),
        new Operation.Unary(Type.doubles(), "((double)", Type.floats(), ")", null),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.doubles(), "(-(", Type.doubles(), "))", null),

        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " + ",   Type.doubles(), ")", null),
        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " - ",   Type.doubles(), ")", null),
        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " * ",   Type.doubles(), ")", null),
        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " / ",   Type.doubles(), ")", null),
        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " % ",   Type.doubles(), ")", null),

        new Operation.Unary(Type.doubles(), "Double.longBitsToDouble(", Type.ints(), ")", null),
        new Operation.Binary(Type.doubles(), "Double.max(", Type.doubles(), ", ", Type.doubles(), ")", null),
        new Operation.Binary(Type.doubles(), "Double.min(", Type.doubles(), ", ", Type.doubles(), ")", null),
        new Operation.Binary(Type.doubles(), "Double.sum(", Type.doubles(), ", ", Type.doubles(), ")", null),

        new Operation.Ternary(Type.doubles(), "(", Type.booleans(), " ? ", Type.doubles(), " : ", Type.doubles(), ")", null)
    );

    private static final List<Operation> BOOLEAN_OPERATIONS = List.of(
        // Note: there is no casting / conversion from an to boolean directly.

        new Operation.Unary(Type.booleans(), "(!(", Type.booleans(), "))", null),

        new Operation.Binary(Type.booleans(), "(", Type.booleans(), " || ",   Type.booleans(), ")", null),
        new Operation.Binary(Type.booleans(), "(", Type.booleans(), " && ",   Type.booleans(), ")", null),
        new Operation.Binary(Type.booleans(), "(", Type.booleans(), " ^ ",    Type.booleans(), ")", null),

        new Operation.Binary(Type.booleans(), "Boolean.logicalAnd(", Type.booleans(), ", ",   Type.booleans(), ")", null),
        new Operation.Binary(Type.booleans(), "Boolean.logicalOr(", Type.booleans(), ", ",   Type.booleans(), ")", null),
        new Operation.Binary(Type.booleans(), "Boolean.logicalXor(", Type.booleans(), ", ",   Type.booleans(), ")", null),

        // Note: For now, we are omitting all the Character.is<...> methods. We can add them in the future.

        new Operation.Unary(Type.booleans(), "Float.isFinite(", Type.floats(), ")", null),
        new Operation.Unary(Type.booleans(), "Float.isInfinite(", Type.floats(), ")", null),
        new Operation.Unary(Type.booleans(), "Float.isNaN(", Type.floats(), ")", null),

        new Operation.Unary(Type.booleans(), "Double.isFinite(", Type.doubles(), ")", null),
        new Operation.Unary(Type.booleans(), "Double.isInfinite(", Type.doubles(), ")", null),
        new Operation.Unary(Type.booleans(), "Double.isNaN(", Type.doubles(), ")", null),

        new Operation.Ternary(Type.booleans(), "(", Type.booleans(), " ? ", Type.booleans(), " : ", Type.booleans(), ")", null)
    );

    public static final List<Operation> PRIMITIVE_OPERATIONS = Stream.of(
        BYTE_OPERATIONS,
        CHAR_OPERATIONS,
        SHORT_OPERATIONS,
        INT_OPERATIONS,
        LONG_OPERATIONS,
        FLOAT_OPERATIONS,
        DOUBLE_OPERATIONS,
        BOOLEAN_OPERATIONS
    ).flatMap((List<Operation> l) -> l.stream()).toList();


    private record VOP(String name, int args, List<PrimitiveType> elementTypes) {}

    // TODO: consider some floating results as inexact, and handle it accordingly?
    private static final List<VOP> VECTOR_API_OPS = List.of(
        new VOP("ABS",                  1, Type.PRIMITIVE_TYPES),
        //new VOP("ACOS",                 1, Type.FLOATING_TYPES),
        new VOP("ADD",                  2, Type.PRIMITIVE_TYPES),
        new VOP("AND",                  2, Type.INTEGRAL_TYPES),
        new VOP("AND_NOT",              2, Type.INTEGRAL_TYPES),
        new VOP("ASHR",                 2, Type.INTEGRAL_TYPES),
        //new VOP("ASIN",                 1, Type.FLOATING_TYPES),
        //new VOP("ATAN",                 1, Type.FLOATING_TYPES),
        //new VOP("ATAN2",                2, Type.FLOATING_TYPES),
        new VOP("BIT_COUNT",            1, Type.INTEGRAL_TYPES),
        new VOP("BITWISE_BLEND",        3, Type.INTEGRAL_TYPES),
        //new VOP("CBRT",                 1, Type.FLOATING_TYPES),
        new VOP("COMPRESS_BITS",        2, Type.INT_LONG_TYPES),
        //new VOP("COS",                  1, Type.FLOATING_TYPES),
        //new VOP("COSH",                 1, Type.FLOATING_TYPES),
        new VOP("DIV",                  2, Type.FLOATING_TYPES),
        //new VOP("EXP",                  1, Type.FLOATING_TYPES),
        new VOP("EXPAND_BITS",          2, Type.INT_LONG_TYPES),
        //new VOP("EXPM1",                1, Type.FLOATING_TYPES),
        new VOP("FIRST_NONZERO",        1, Type.PRIMITIVE_TYPES),
        new VOP("FMA",                  3, Type.FLOATING_TYPES),
        //new VOP("HYPOT",                2, Type.FLOATING_TYPES),
        new VOP("LEADING_ZEROS_COUNT",  1, Type.PRIMITIVE_TYPES),
        //new VOP("LOG",                  1, Type.FLOATING_TYPES),
        //new VOP("LOG10",                1, Type.FLOATING_TYPES),
        //new VOP("LOG1P",                1, Type.FLOATING_TYPES),
        new VOP("LSHL",                 2, Type.INTEGRAL_TYPES),
        new VOP("LSHR",                 2, Type.INTEGRAL_TYPES),
        new VOP("MIN",                  2, Type.PRIMITIVE_TYPES),
        new VOP("MAX",                  2, Type.PRIMITIVE_TYPES),
        new VOP("MUL",                  2, Type.PRIMITIVE_TYPES),
        new VOP("NEG",                  1, Type.PRIMITIVE_TYPES),
        new VOP("NOT",                  1, Type.INTEGRAL_TYPES),
        new VOP("OR",                   2, Type.INTEGRAL_TYPES),
        //new VOP("POW",                  2, Type.FLOATING_TYPES),
        new VOP("REVERSE",              1, Type.PRIMITIVE_TYPES),
        new VOP("REVERSE_BYTES",        1, Type.PRIMITIVE_TYPES),
        new VOP("ROL",                  2, Type.INTEGRAL_TYPES),
        new VOP("ROR",                  2, Type.INTEGRAL_TYPES),
        new VOP("SADD",                 2, Type.INTEGRAL_TYPES),
        //new VOP("SIN",                  1, Type.FLOATING_TYPES),
        //new VOP("SINH",                 1, Type.FLOATING_TYPES),
        //new VOP("SQRT",                 1, Type.FLOATING_TYPES),
        new VOP("SSUB",                 2, Type.INTEGRAL_TYPES),
        new VOP("SUADD",                2, Type.INTEGRAL_TYPES),
        new VOP("SUB",                  2, Type.PRIMITIVE_TYPES),
        new VOP("SUSUB",                2, Type.INTEGRAL_TYPES),
        //new VOP("TAN",                  1, Type.FLOATING_TYPES),
        //new VOP("TANH",                 1, Type.FLOATING_TYPES),
        new VOP("TRAILING_ZEROS_COUNT", 1, Type.PRIMITIVE_TYPES),
        new VOP("UMAX",                 2, Type.INTEGRAL_TYPES),
        new VOP("UMIN",                 2, Type.INTEGRAL_TYPES),
        new VOP("XOR",                  2, Type.INTEGRAL_TYPES),
        new VOP("ZOMO",                 1, Type.INTEGRAL_TYPES)
    );

    private static final List<Operation> generateVectorAPIOperations() {
        List<Operation> ops = new ArrayList<Operation>();

        for (var type : Type.VECTOR_API_TYPES) {
            ops.add(new Operation.Unary(type, "", type, ".abs()", null));
            ops.add(new Operation.Binary(type, "", type, ".add(", type.elementType, ")", null));
            // TODO: add(int e, VectorMask<Integer> m)
            ops.add(new Operation.Binary(type, "", type, ".add(", type, ")", null));
            // TODO: add(Vector<Integer> v, VectorMask<Integer> m)
            // FIXME: addIndex bounds
            //ops.add(new Operation.Binary(type, "", type, ".addIndex(", Type.ints(), ")", null));

            if (!type.elementType.isFloating()) {
                ops.add(new Operation.Binary(type, "", type, ".and(", type.elementType, ")", null));
                ops.add(new Operation.Binary(type, "", type, ".and(", type, ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".bitwiseBlend(", type.elementType, ", ", type.elementType, ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".bitwiseBlend(", type.elementType, ", ", type,             ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".bitwiseBlend(", type,             ", ", type.elementType, ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".bitwiseBlend(", type,             ", ", type,             ")", null));
            }

            // TODO: blend(int e, VectorMask<Integer> m)
            // TODO: blend(long e, VectorMask<Integer> m)
            // TODO: blend(Vector<Integer> v, VectorMask<Integer> m)

            ops.add(new Operation.Unary(type, type.vectorType + ".broadcast(" + type.species + ", ", type.elementType, ")", null));
            ops.add(new Operation.Unary(type, type.vectorType + ".broadcast(" + type.species + ", ", Type.longs(), ")", List.of("IllegalArgumentException")));

            // TODO: non zero parts
            for (var type2 : Type.VECTOR_API_TYPES) {
                ops.add(new Operation.Unary(type, "((" + type.vectorType + ")", type2 , ".castShape(" + type.species + ", 0))", null));
            }

            // Note: check works on class / species, leaving them out.

            // TODO: compare with VectorMask type
            // TODO: compress with VectorMask type

            // TODO: non zero parts
            for (var type2 : Type.VECTOR_API_TYPES) {
                // FIXME: fix shape compatibility
                // ops.add(new Operation.Unary(type,
                //                             "((" + type.vectorType + ")",
                //                             type2 ,
                //                             ".convert(VectorOperators.Conversion.ofCast("
                //                                 + type2.elementType.name() +  ".class, "
                //                                 + type.elementType.name() + ".class), 0))",
                //                             null));
                // ops.add(new Operation.Unary(type,
                //                             "((" + type.vectorType + ")",
                //                             type2 ,
                //                             ".convert(VectorOperators.Conversion.ofReinterpret("
                //                                 + type2.elementType.name() +  ".class, "
                //                                 + type.elementType.name() + ".class), 0))",
                //                             null));

                // TODO: convertShape
            }

            ops.add(new Operation.Binary(type, "", type, ".div(", type.elementType, ")", List.of("ArithmeticException")));
            // TODO: div(int e, VectorMask<Integer> m)
            ops.add(new Operation.Binary(type, "", type, ".div(", type, ")", List.of("ArithmeticException")));
            // TODO: div(Vector<Integer> v, VectorMask<Integer> m)

            // TODO: eq(int e)   -> VectorMask
            // TODO: eq(Vector<Integer> v) -> VectorMask

            // TODO: expand(VectorMask<Integer> m)

            // TODO: ensure we use all variants of fromArray and fromMemorySegment, plus intoArray and intoMemorySegment.

            // TODO: lane case that is allowed to throw java.lang.IllegalArgumentException for out of bonds.
            ops.add(new Operation.Binary(type.elementType, "", type, ".lane(", Type.ints(), " & " + (type.length-1) + ")", null));

            for (VOP vop : VECTOR_API_OPS) {
                if (vop.args() == 2 && vop.elementTypes().contains(type.elementType)) {
                    ops.add(new Operation.Binary(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ")", null));
                    // TODO: lanewise(VectorOperators.Binary op, int e, VectorMask<Integer> m)
                }
            }
        }

        // Ensure the list is immutable.
        return List.copyOf(ops);
    }

    public static final List<Operation> VECTOR_API_OPERATIONS = generateVectorAPIOperations();

    public static final List<Operation> ALL_BUILTIN_OPERATIONS = Library.concat(
        PRIMITIVE_OPERATIONS,
        VECTOR_API_OPERATIONS
    );
}
