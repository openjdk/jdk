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

    private enum VOPType {
        UNARY,
        BINARY,
        ASSOCIATIVE, // Binary and associative - safe for reductions of any type
        INTEGRAL_ASSOCIATIVE, // Binary - but only safe for integral reductions
        TERNARY
    }
    private record VOP(String name, VOPType type, List<PrimitiveType> elementTypes) {}

    // TODO: consider some floating results as inexact, and handle it accordingly?
    private static final List<VOP> VECTOR_API_OPS = List.of(
        new VOP("ABS",                  VOPType.UNARY, Type.PRIMITIVE_TYPES),
        //new VOP("ACOS",                 VOPType.UNARY, Type.FLOATING_TYPES),
        new VOP("ADD",                  VOPType.INTEGRAL_ASSOCIATIVE, Type.PRIMITIVE_TYPES),
        new VOP("AND",                  VOPType.ASSOCIATIVE, Type.INTEGRAL_TYPES),
        new VOP("AND_NOT",              VOPType.BINARY, Type.INTEGRAL_TYPES),
        new VOP("ASHR",                 VOPType.BINARY, Type.INTEGRAL_TYPES),
        //new VOP("ASIN",                 VOPType.UNARY, Type.FLOATING_TYPES),
        //new VOP("ATAN",                 VOPType.UNARY, Type.FLOATING_TYPES),
        //new VOP("ATAN2",                VOPType.BINARY, Type.FLOATING_TYPES),
        new VOP("BIT_COUNT",            VOPType.UNARY, Type.INTEGRAL_TYPES),
        new VOP("BITWISE_BLEND",        VOPType.TERNARY, Type.INTEGRAL_TYPES),
        //new VOP("CBRT",                 VOPType.UNARY, Type.FLOATING_TYPES),
        new VOP("COMPRESS_BITS",        VOPType.BINARY, Type.INT_LONG_TYPES),
        //new VOP("COS",                  VOPType.UNARY, Type.FLOATING_TYPES),
        //new VOP("COSH",                 VOPType.UNARY, Type.FLOATING_TYPES),
        new VOP("DIV",                  VOPType.BINARY, Type.FLOATING_TYPES),
        //new VOP("EXP",                  VOPType.UNARY, Type.FLOATING_TYPES),
        new VOP("EXPAND_BITS",          VOPType.BINARY, Type.INT_LONG_TYPES),
        //new VOP("EXPM1",                VOPType.UNARY, Type.FLOATING_TYPES),
        new VOP("FIRST_NONZERO",        VOPType.ASSOCIATIVE, Type.PRIMITIVE_TYPES),
        new VOP("FMA",                  VOPType.TERNARY, Type.FLOATING_TYPES),
        //new VOP("HYPOT",                VOPType.BINARY, Type.FLOATING_TYPES),
        new VOP("LEADING_ZEROS_COUNT",  VOPType.UNARY, Type.INTEGRAL_TYPES),
        //new VOP("LOG",                  VOPType.UNARY, Type.FLOATING_TYPES),
        //new VOP("LOG10",                VOPType.UNARY, Type.FLOATING_TYPES),
        //new VOP("LOG1P",                VOPType.UNARY, Type.FLOATING_TYPES),
        new VOP("LSHL",                 VOPType.BINARY, Type.INTEGRAL_TYPES),
        new VOP("LSHR",                 VOPType.BINARY, Type.INTEGRAL_TYPES),
        new VOP("MIN",                  VOPType.ASSOCIATIVE, Type.PRIMITIVE_TYPES),
        new VOP("MAX",                  VOPType.ASSOCIATIVE, Type.PRIMITIVE_TYPES),
        new VOP("MUL",                  VOPType.INTEGRAL_ASSOCIATIVE, Type.PRIMITIVE_TYPES),
        new VOP("NEG",                  VOPType.UNARY, Type.PRIMITIVE_TYPES),
        new VOP("NOT",                  VOPType.UNARY, Type.INTEGRAL_TYPES),
        new VOP("OR",                   VOPType.ASSOCIATIVE, Type.INTEGRAL_TYPES),
        //new VOP("POW",                  VOPType.BINARY, Type.FLOATING_TYPES),
        new VOP("REVERSE",              VOPType.UNARY, Type.INTEGRAL_TYPES),
        new VOP("REVERSE_BYTES",        VOPType.UNARY, Type.INTEGRAL_TYPES),

        // TODO: add back in after fix of JDK-8351627
        // new VOP("ROL",                  VOPType.BINARY, Type.INTEGRAL_TYPES),
        // new VOP("ROR",                  VOPType.BINARY, Type.INTEGRAL_TYPES),

        new VOP("SADD",                 VOPType.BINARY, Type.INTEGRAL_TYPES),
        //new VOP("SIN",                  VOPType.UNARY, Type.FLOATING_TYPES),
        //new VOP("SINH",                 VOPType.UNARY, Type.FLOATING_TYPES),
        //new VOP("SQRT",                 VOPType.UNARY, Type.FLOATING_TYPES),
        new VOP("SSUB",                 VOPType.BINARY, Type.INTEGRAL_TYPES),
        new VOP("SUADD",                VOPType.BINARY, Type.INTEGRAL_TYPES),
        new VOP("SUB",                  VOPType.BINARY, Type.PRIMITIVE_TYPES),
        new VOP("SUSUB",                VOPType.BINARY, Type.INTEGRAL_TYPES),
        //new VOP("TAN",                  VOPType.UNARY, Type.FLOATING_TYPES),
        //new VOP("TANH",                 VOPType.UNARY, Type.FLOATING_TYPES),
        new VOP("TRAILING_ZEROS_COUNT", VOPType.UNARY, Type.INTEGRAL_TYPES),
        new VOP("UMAX",                 VOPType.ASSOCIATIVE, Type.INTEGRAL_TYPES),
        new VOP("UMIN",                 VOPType.ASSOCIATIVE, Type.INTEGRAL_TYPES),
        new VOP("XOR",                  VOPType.ASSOCIATIVE, Type.INTEGRAL_TYPES),
        new VOP("ZOMO",                 VOPType.UNARY, Type.INTEGRAL_TYPES)
    );

    private static final List<Operation> generateVectorAPIOperations() {
        List<Operation> ops = new ArrayList<Operation>();

        for (var type : Type.VECTOR_API_VECTOR_TYPES) {
            ops.add(new Operation.Unary(type, "", type, ".abs()", null));
            ops.add(new Operation.Binary(type, "", type, ".add(", type.elementType, ")", null));
            // TODO: add(int e, VectorMask<Integer> m)
            ops.add(new Operation.Binary(type, "", type, ".add(", type, ")", null));
            // TODO: add(Vector<Integer> v, VectorMask<Integer> m)

            // If VLENGTH*scale overflows, then a IllegalArgumentException is thrown.
            ops.add(new Operation.Unary(type, "", type, ".addIndex(1)", null));
            ops.add(new Operation.Binary(type, "", type, ".addIndex(", Type.ints(), ")", List.of("IllegalArgumentException")));

            if (!type.elementType.isFloating()) {
                ops.add(new Operation.Binary(type, "", type, ".and(", type.elementType, ")", null));
                ops.add(new Operation.Binary(type, "", type, ".and(", type, ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".bitwiseBlend(", type.elementType, ", ", type.elementType, ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".bitwiseBlend(", type.elementType, ", ", type,             ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".bitwiseBlend(", type,             ", ", type.elementType, ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".bitwiseBlend(", type,             ", ", type,             ")", null));
                ops.add(new Operation.Unary(type, "", type, ".not()", null));
                ops.add(new Operation.Binary(type, "", type, ".or(", type.elementType, ")", null));
                ops.add(new Operation.Binary(type, "", type, ".or(", type, ")", null));
            }

            // TODO: blend(int e, VectorMask<Integer> m)
            // TODO: blend(long e, VectorMask<Integer> m)
            // TODO: blend(Vector<Integer> v, VectorMask<Integer> m)

            ops.add(new Operation.Unary(type, type.vectorType + ".broadcast(" + type.species + ", ", type.elementType, ")", null));
            ops.add(new Operation.Unary(type, type.vectorType + ".broadcast(" + type.species + ", ", Type.longs(), ")", List.of("IllegalArgumentException")));

            // TODO: non zero parts
            for (var type2 : Type.VECTOR_API_VECTOR_TYPES) {
                ops.add(new Operation.Unary(type, "((" + type.vectorType + ")", type2 , ".castShape(" + type.species + ", 0))", null));
            }

            // Note: check works on class / species, leaving them out.

            // TODO: compare with VectorMask type
            // TODO: compress with VectorMask type

            // TODO: non zero parts
            for (var type2 : Type.VECTOR_API_VECTOR_TYPES) {
                // "convert" keeps the same shape, i.e. length of the vector in bits.
                if (type.sizeInBits() == type2.sizeInBits()) {
                    ops.add(new Operation.Unary(type,
                                                "((" + type.vectorType + ")",
                                                type2 ,
                                                ".convert(VectorOperators.Conversion.ofCast("
                                                    + type2.elementType.name() +  ".class, "
                                                    + type.elementType.name() + ".class), 0))",
                                                null));
                    // Reinterpretation FROM floating is not safe, because of different NaN encodings.
                    if (!type2.elementType.isFloating()) {
                        ops.add(new Operation.Unary(type,
                                                    "((" + type.vectorType + ")",
                                                    type2 ,
                                                    ".convert(VectorOperators.Conversion.ofReinterpret("
                                                        + type2.elementType.name() +  ".class, "
                                                        + type.elementType.name() + ".class), 0))",
                                                    null));
                        if (type.elementType == Type.bytes()) {
                            ops.add(new Operation.Unary(type, "", type2, ".reinterpretAsBytes()", null));
                        }
                        if (type.elementType == Type.shorts()) {
                            ops.add(new Operation.Unary(type, "", type2, ".reinterpretAsShorts()", null));
                        }
                        if (type.elementType == Type.ints()) {
                            ops.add(new Operation.Unary(type, "", type2, ".reinterpretAsInts()", null));
                        }
                        if (type.elementType == Type.longs()) {
                            ops.add(new Operation.Unary(type, "", type2, ".reinterpretAsLongs()", null));
                        }
                        if (type.elementType == Type.floats()) {
                            ops.add(new Operation.Unary(type, "", type2, ".reinterpretAsFloats()", null));
                        }
                        if (type.elementType == Type.doubles()) {
                            ops.add(new Operation.Unary(type, "", type2, ".reinterpretAsDoubles()", null));
                        }
                        if (type.elementType.isFloating() && type.elementType.sizeInBits() == type2.elementType.sizeInBits()) {
                            ops.add(new Operation.Unary(type, "", type2, ".viewAsFloatingLanes()", null));
                        }
                        if (!type.elementType.isFloating() && type.elementType.sizeInBits() == type2.elementType.sizeInBits()) {
                            ops.add(new Operation.Unary(type, "", type2, ".viewAsIntegralLanes()", null));
                        }
                    }
                }
                // TODO: convertShape
                // TODO: reinterpretShape
            }

            ops.add(new Operation.Binary(type, "", type, ".div(", type.elementType, ")", List.of("ArithmeticException")));
            // TODO: div(int e, VectorMask<Integer> m)
            ops.add(new Operation.Binary(type, "", type, ".div(", type, ")", List.of("ArithmeticException")));
            // TODO: div(Vector<Integer> v, VectorMask<Integer> m)

            // TODO: eq(int e)   -> VectorMask
            // TODO: eq(Vector<Integer> v) -> VectorMask

            // TODO: expand(VectorMask<Integer> m)

            // TODO: ensure we use all variants of fromArray and fromMemorySegment, plus intoArray and intoMemorySegment. Also: toArray and type variants.

            // TODO: lane case that is allowed to throw java.lang.IllegalArgumentException for out of bonds.
            ops.add(new Operation.Binary(type.elementType, "", type, ".lane(", Type.ints(), " & " + (type.length-1) + ")", null));

            for (VOP vop : VECTOR_API_OPS) {
                if (vop.elementTypes().contains(type.elementType)) {
                    switch(vop.type()) {
                    case VOPType.UNARY:
                        ops.add(new Operation.Unary(type, "", type, ".lanewise(VectorOperators." + vop.name() + ")", null));
                        // TODO: lanewise(VectorOperators.Unary op, VectorMask<Integer> m)
                        break;
                    case VOPType.ASSOCIATIVE:
                    case VOPType.INTEGRAL_ASSOCIATIVE:
                        if (vop.type() == VOPType.ASSOCIATIVE || !type.elementType.isFloating()) {
                            ops.add(new Operation.Unary(type.elementType, "", type, ".reduceLanes(VectorOperators." + vop.name() + ")", null));
                            // TODO: reduceLanes(VectorOperators.Associative op, VectorMask<Integer> m)
                        }
                        // fall-through
                    case VOPType.BINARY:
                        ops.add(new Operation.Binary(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ")", null));
                        // TODO: lanewise(VectorOperators.Binary op, int e, VectorMask<Integer> m)
                        // TODO: lanewise(VectorOperators.Binary op, long e)
                        // TODO: lanewise(VectorOperators.Binary op, long e, VectorMask<Integer> m)
                        ops.add(new Operation.Binary(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ")", null));
                        // TODO: lanewise(VectorOperators.Binary op, Vector<Integer> v, VectorMask<Integer> m)
                        break;
                    case VOPType.TERNARY:
                        ops.add(new Operation.Ternary(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type.elementType, ")", null));
                        // TODO: lanewise(VectorOperators.Ternary op, int e1, int e2, VectorMask<Integer> m)
                        ops.add(new Operation.Ternary(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type, ")", null));
                        // TODO: lanewise(VectorOperators.Ternary op, int e1, Vector<Integer> v2, VectorMask<Integer> m)
                        ops.add(new Operation.Ternary(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type.elementType, ")", null));
                        // TODO: lanewise(VectorOperators.Ternary op, Vector<Integer> v1, int e2, VectorMask<Integer> m)
                        ops.add(new Operation.Ternary(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type, ")", null));
                        // TODO: lanewise(VectorOperators.Ternary op, Vector<Integer> v1, Vector<Integer> v2, VectorMask<Integer> m)
                        break;
                    }
                }
            }

            // TODO: lt, maskAll -> VectorMask

            ops.add(new Operation.Binary(type, "", type, ".max(", type.elementType, ")", null));
            ops.add(new Operation.Binary(type, "", type, ".max(", type, ")", null));
            ops.add(new Operation.Binary(type, "", type, ".min(", type.elementType, ")", null));
            ops.add(new Operation.Binary(type, "", type, ".min(", type, ")", null));

            ops.add(new Operation.Binary(type, "", type, ".mul(", type.elementType, ")", null));
            // TODO: mul(int e, VectorMask<Integer> m)
            ops.add(new Operation.Binary(type, "", type, ".mul(", type, ")", null));
            // TODO: mul(Vector<Integer> v, VectorMask<Integer> m)

            ops.add(new Operation.Unary(type, "", type, ".neg()", null));

            // TODO: rearrange(VectorShuffle<Integer> shuffle)
            // TODO: rearrange(VectorShuffle<Integer> s, Vector<Integer> v)
            // TODO: rearrange(VectorShuffle<Integer> s, VectorMask<Integer> m)

            ops.add(new Operation.Binary(type, "", type, ".selectFrom(", type, ")", null));
            ops.add(new Operation.Ternary(type, "", type, ".selectFrom(", type, ", ", type, ")", null));
            // TODO: selectFrom(Vector<Integer> s, VectorMask<Integer> m)

            ops.add(new Operation.Binary(type, "", type, ".slice(", Type.ints(), ")", List.of("IndexOutOfBoundsException")));
            ops.add(new Operation.Ternary(type, "", type, ".slice(", Type.ints(), ", ", type, ")", List.of("IndexOutOfBoundsException")));
            // TODO: slice(int origin, Vector<Integer> w, VectorMask<Integer> m)

            ops.add(new Operation.Binary(type, "", type, ".sub(", type.elementType, ")", null));
            // TODO: sub(int e, VectorMask<Integer> m)
            ops.add(new Operation.Binary(type, "", type, ".sub(", type, ")", null));
            // TODO: sub(Vector<Integer> v, VectorMask<Integer> m)

            // TODO: test(VectorOperators.Test op)
            // TODO: test(VectorOperators.Test op, VectorMask<Integer> m)

            ops.add(new Operation.Binary(type, "", type, ".unslice(", Type.ints(), ")", List.of("IndexOutOfBoundsException")));
            // TODO: unslice(int origin, Vector<Integer> w, int part)
            // TODO: unslice(int origin, Vector<Integer> w, int part, VectorMask<Integer> m)

            ops.add(new Operation.Ternary(type, "", type, ".withLane(", Type.ints(), ", ", type.elementType, ")", List.of("IllegalArgumentException")));

            if (type.elementType.isFloating()) {
                ops.add(new Operation.Ternary(type, "", type, ".fma(", type.elementType, ", ", type.elementType, ")", null));
                ops.add(new Operation.Ternary(type, "", type, ".fma(", type, ", ", type, ")", null));

                // TODO: precision?
                // ops.add(new Operation.Binary(type, "", type, ".pow(", type.elementType, ")", null));
                // ops.add(new Operation.Binary(type, "", type, ".pow(", type, ")", null));
                // ops.add(new Operation.Unary(type, "", type, ".sqrt(", type, ")", null));
            }

            // TODO: toShuffle - VectorShuffle
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
