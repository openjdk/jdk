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
import java.util.stream.Stream;

import compiler.lib.template_library.types.ByteType;
import compiler.lib.template_library.types.CharType;
import compiler.lib.template_library.types.ShortType;
import compiler.lib.template_library.types.IntType;
import compiler.lib.template_library.types.LongType;
import compiler.lib.template_library.types.FloatType;
import compiler.lib.template_library.types.DoubleType;
import compiler.lib.template_library.types.BooleanType;

public class Operations {

    private static final List<Operation> BYTE_OPERATIONS = List.of(
        // Note: the standard integer arithmetic operations are only defined for int/long.
        //       They can be used for smaller types only via automatic promotion to int,
        //       and then a cast back to byte, e.g:
        //           byte a = (byte)(b + c)
        //
        //       Instead of adding these operations explicitly, we just add the conversion
        //       from int to byte, and let the IntType generate all the integer arithmetic
        //       operations.
        new Operation.Unary(ByteType.INSTANCE, "((byte)", CharType.INSTANCE, ")"),
        new Operation.Unary(ByteType.INSTANCE, "((byte)", ShortType.INSTANCE, ")"),
        new Operation.Unary(ByteType.INSTANCE, "((byte)", IntType.INSTANCE, ")"),
        new Operation.Unary(ByteType.INSTANCE, "((byte)", LongType.INSTANCE, ")"),
        new Operation.Unary(ByteType.INSTANCE, "((byte)", FloatType.INSTANCE, ")"),
        new Operation.Unary(ByteType.INSTANCE, "((byte)", DoubleType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Ternary(ByteType.INSTANCE, "(", BooleanType.INSTANCE, " ? ", ByteType.INSTANCE, " : ", ByteType.INSTANCE, ")")
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
        new Operation.Unary(CharType.INSTANCE, "((char)", ByteType.INSTANCE, ")"),
        new Operation.Unary(CharType.INSTANCE, "((char)", ShortType.INSTANCE, ")"),
        new Operation.Unary(CharType.INSTANCE, "((char)", IntType.INSTANCE, ")"),
        new Operation.Unary(CharType.INSTANCE, "((char)", LongType.INSTANCE, ")"),
        new Operation.Unary(CharType.INSTANCE, "((char)", FloatType.INSTANCE, ")"),
        new Operation.Unary(CharType.INSTANCE, "((char)", DoubleType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(CharType.INSTANCE, "Character.reverseBytes(", CharType.INSTANCE, ")"),

        new Operation.Ternary(CharType.INSTANCE, "(", BooleanType.INSTANCE, " ? ", CharType.INSTANCE, " : ", CharType.INSTANCE, ")")
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
        new Operation.Unary(ShortType.INSTANCE, "((short)", ByteType.INSTANCE, ")"),
        new Operation.Unary(ShortType.INSTANCE, "((short)", CharType.INSTANCE, ")"),
        new Operation.Unary(ShortType.INSTANCE, "((short)", IntType.INSTANCE, ")"),
        new Operation.Unary(ShortType.INSTANCE, "((short)", LongType.INSTANCE, ")"),
        new Operation.Unary(ShortType.INSTANCE, "((short)", FloatType.INSTANCE, ")"),
        new Operation.Unary(ShortType.INSTANCE, "((short)", DoubleType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(ShortType.INSTANCE, "Short.reverseBytes(", ShortType.INSTANCE, ")"),

        // Note: Float.floatToFloat16 can lead to issues, because NaN values are not always
        //       represented by the same short bits.

        new Operation.Ternary(ShortType.INSTANCE, "(", BooleanType.INSTANCE, " ? ", ShortType.INSTANCE, " : ", ShortType.INSTANCE, ")")
    );

    private static final List<Operation> INT_OPERATIONS = List.of(
        new Operation.Unary(IntType.INSTANCE, "((int)", ByteType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "((int)", CharType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "((int)", ShortType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "((int)", LongType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "((int)", FloatType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "((int)", DoubleType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(IntType.INSTANCE, "(-(", IntType.INSTANCE, "))"),
        new Operation.Unary(IntType.INSTANCE, "(~", IntType.INSTANCE, ")"),

        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " + ",   IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " - ",   IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " * ",   IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " / ",   IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " % ",   IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " & ",   IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " | ",   IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " ^ ",   IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " << ",  IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " >> ",  IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "(", IntType.INSTANCE, " >>> ", IntType.INSTANCE, ")"),

        new Operation.Binary(IntType.INSTANCE, "Byte.compare(", ByteType.INSTANCE, ", ", ByteType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Byte.compareUnsigned(", ByteType.INSTANCE, ", ", ByteType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Byte.toUnsignedInt(", ByteType.INSTANCE, ")"),

        new Operation.Binary(IntType.INSTANCE, "Character.compare(", CharType.INSTANCE, ", ", CharType.INSTANCE, ")"),

        new Operation.Binary(IntType.INSTANCE, "Short.compare(", ShortType.INSTANCE, ", ", ShortType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Short.compareUnsigned(", ShortType.INSTANCE, ", ", ShortType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Short.toUnsignedInt(", ShortType.INSTANCE, ")"),

        new Operation.Unary(IntType.INSTANCE, "Integer.bitCount(", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.compare(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.compareUnsigned(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.compress(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.divideUnsigned(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.expand(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Integer.highestOneBit(", IntType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Integer.lowestOneBit(", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.min(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.max(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Integer.numberOfLeadingZeros(", IntType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Integer.numberOfTrailingZeros(", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.remainderUnsigned(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Integer.reverse(", IntType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Integer.reverseBytes(", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.rotateLeft(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.rotateRight(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Integer.signum(", IntType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Integer.sum(", IntType.INSTANCE, ", ", IntType.INSTANCE, ")"),

        new Operation.Unary(IntType.INSTANCE, "Long.bitCount(", LongType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Long.compare(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),
        new Operation.Binary(IntType.INSTANCE, "Long.compareUnsigned(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Long.numberOfLeadingZeros(", LongType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Long.numberOfTrailingZeros(", LongType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Long.signum(", LongType.INSTANCE, ")"),

        new Operation.Binary(IntType.INSTANCE, "Float.compare(", FloatType.INSTANCE, ", ", FloatType.INSTANCE, ")"),
        new Operation.Unary(IntType.INSTANCE, "Float.floatToIntBits(", FloatType.INSTANCE, ")"),
        // Note: Float.floatToRawIntBits can lead to issues, because the NaN values are not always
        //       represented by the same int bits.

        new Operation.Binary(IntType.INSTANCE, "Double.compare(", DoubleType.INSTANCE, ", ", DoubleType.INSTANCE, ")"),

        new Operation.Binary(IntType.INSTANCE, "Boolean.compare(", BooleanType.INSTANCE, ", ", BooleanType.INSTANCE, ")"),

        new Operation.Ternary(IntType.INSTANCE, "(", BooleanType.INSTANCE, " ? ", IntType.INSTANCE, " : ", IntType.INSTANCE, ")")
    );

    private static final List<Operation> LONG_OPERATIONS = List.of(
        new Operation.Unary(LongType.INSTANCE, "((long)", ByteType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "((long)", CharType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "((long)", ShortType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "((long)", IntType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "((long)", FloatType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "((long)", DoubleType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(LongType.INSTANCE, "(-(", LongType.INSTANCE, "))"),
        new Operation.Unary(LongType.INSTANCE, "(~", LongType.INSTANCE, ")"),

        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " + ",   LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " - ",   LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " * ",   LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " / ",   LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " % ",   LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " & ",   LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " | ",   LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " ^ ",   LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " << ",  LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " >> ",  LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "(", LongType.INSTANCE, " >>> ", LongType.INSTANCE, ")"),

        new Operation.Unary(LongType.INSTANCE, "Byte.toUnsignedLong(", ByteType.INSTANCE, ")"),

        new Operation.Unary(LongType.INSTANCE, "Short.toUnsignedLong(", ShortType.INSTANCE, ")"),

        new Operation.Unary(LongType.INSTANCE, "Integer.toUnsignedLong(", IntType.INSTANCE, ")"),

        new Operation.Binary(LongType.INSTANCE, "Long.compress(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "Long.divideUnsigned(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "Long.expand(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "Long.highestOneBit(", LongType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "Long.lowestOneBit(", LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "Long.min(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "Long.max(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "Long.remainderUnsigned(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "Long.reverse(", LongType.INSTANCE, ")"),
        new Operation.Unary(LongType.INSTANCE, "Long.reverseBytes(", LongType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "Long.rotateLeft(", LongType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "Long.rotateRight(", LongType.INSTANCE, ", ", IntType.INSTANCE, ")"),
        new Operation.Binary(LongType.INSTANCE, "Long.sum(", LongType.INSTANCE, ", ", LongType.INSTANCE, ")"),

        new Operation.Unary(LongType.INSTANCE, "Double.doubleToLongBits(", DoubleType.INSTANCE, ")"),
        // Note: Double.doubleToRawLongBits can lead to issues, because the NaN values are not always
        //       represented by the same long bits.

        new Operation.Ternary(LongType.INSTANCE, "(", BooleanType.INSTANCE, " ? ", LongType.INSTANCE, " : ", LongType.INSTANCE, ")")
    );

    private static final List<Operation> FLOAT_OPERATIONS = List.of(
        new Operation.Unary(FloatType.INSTANCE, "((float)", ByteType.INSTANCE, ")"),
        new Operation.Unary(FloatType.INSTANCE, "((float)", CharType.INSTANCE, ")"),
        new Operation.Unary(FloatType.INSTANCE, "((float)", ShortType.INSTANCE, ")"),
        new Operation.Unary(FloatType.INSTANCE, "((float)", IntType.INSTANCE, ")"),
        new Operation.Unary(FloatType.INSTANCE, "((float)", LongType.INSTANCE, ")"),
        new Operation.Unary(FloatType.INSTANCE, "((float)", DoubleType.INSTANCE, ")"),

        new Operation.Unary(FloatType.INSTANCE, "(-(", FloatType.INSTANCE, "))"),

        new Operation.Binary(FloatType.INSTANCE, "(", FloatType.INSTANCE, " + ",   FloatType.INSTANCE, ")"),
        new Operation.Binary(FloatType.INSTANCE, "(", FloatType.INSTANCE, " - ",   FloatType.INSTANCE, ")"),
        new Operation.Binary(FloatType.INSTANCE, "(", FloatType.INSTANCE, " * ",   FloatType.INSTANCE, ")"),
        new Operation.Binary(FloatType.INSTANCE, "(", FloatType.INSTANCE, " / ",   FloatType.INSTANCE, ")"),
        new Operation.Binary(FloatType.INSTANCE, "(", FloatType.INSTANCE, " % ",   FloatType.INSTANCE, ")"),

        new Operation.Unary(FloatType.INSTANCE, "Float.float16ToFloat(", ShortType.INSTANCE, ")"),
        new Operation.Unary(FloatType.INSTANCE, "Float.intBitsToFloat(", IntType.INSTANCE, ")"),
        new Operation.Binary(FloatType.INSTANCE, "Float.max(", FloatType.INSTANCE, ", ", FloatType.INSTANCE, ")"),
        new Operation.Binary(FloatType.INSTANCE, "Float.min(", FloatType.INSTANCE, ", ", FloatType.INSTANCE, ")"),
        new Operation.Binary(FloatType.INSTANCE, "Float.sum(", FloatType.INSTANCE, ", ", FloatType.INSTANCE, ")"),

        new Operation.Ternary(FloatType.INSTANCE, "(", BooleanType.INSTANCE, " ? ", FloatType.INSTANCE, " : ", FloatType.INSTANCE, ")")
    );

    private static final List<Operation> DOUBLE_OPERATIONS = List.of(
        new Operation.Unary(DoubleType.INSTANCE, "((double)", ByteType.INSTANCE, ")"),
        new Operation.Unary(DoubleType.INSTANCE, "((double)", CharType.INSTANCE, ")"),
        new Operation.Unary(DoubleType.INSTANCE, "((double)", ShortType.INSTANCE, ")"),
        new Operation.Unary(DoubleType.INSTANCE, "((double)", IntType.INSTANCE, ")"),
        new Operation.Unary(DoubleType.INSTANCE, "((double)", LongType.INSTANCE, ")"),
        new Operation.Unary(DoubleType.INSTANCE, "((double)", FloatType.INSTANCE, ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(DoubleType.INSTANCE, "(-(", DoubleType.INSTANCE, "))"),

        new Operation.Binary(DoubleType.INSTANCE, "(", DoubleType.INSTANCE, " + ",   DoubleType.INSTANCE, ")"),
        new Operation.Binary(DoubleType.INSTANCE, "(", DoubleType.INSTANCE, " - ",   DoubleType.INSTANCE, ")"),
        new Operation.Binary(DoubleType.INSTANCE, "(", DoubleType.INSTANCE, " * ",   DoubleType.INSTANCE, ")"),
        new Operation.Binary(DoubleType.INSTANCE, "(", DoubleType.INSTANCE, " / ",   DoubleType.INSTANCE, ")"),
        new Operation.Binary(DoubleType.INSTANCE, "(", DoubleType.INSTANCE, " % ",   DoubleType.INSTANCE, ")"),

        new Operation.Unary(DoubleType.INSTANCE, "Double.longBitsToDouble(", IntType.INSTANCE, ")"),
        new Operation.Binary(DoubleType.INSTANCE, "Double.max(", DoubleType.INSTANCE, ", ", DoubleType.INSTANCE, ")"),
        new Operation.Binary(DoubleType.INSTANCE, "Double.min(", DoubleType.INSTANCE, ", ", DoubleType.INSTANCE, ")"),
        new Operation.Binary(DoubleType.INSTANCE, "Double.sum(", DoubleType.INSTANCE, ", ", DoubleType.INSTANCE, ")"),

        new Operation.Ternary(DoubleType.INSTANCE, "(", BooleanType.INSTANCE, " ? ", DoubleType.INSTANCE, " : ", DoubleType.INSTANCE, ")")
    );

    private static final List<Operation> BOOLEAN_OPERATIONS = List.of(
        // Note: there is no casting / conversion from an to boolean directly.

        new Operation.Unary(BooleanType.INSTANCE, "(!(", BooleanType.INSTANCE, "))"),

        new Operation.Binary(BooleanType.INSTANCE, "(", BooleanType.INSTANCE, " || ",   BooleanType.INSTANCE, ")"),
        new Operation.Binary(BooleanType.INSTANCE, "(", BooleanType.INSTANCE, " && ",   BooleanType.INSTANCE, ")"),
        new Operation.Binary(BooleanType.INSTANCE, "(", BooleanType.INSTANCE, " ^ ",    BooleanType.INSTANCE, ")"),

        new Operation.Binary(BooleanType.INSTANCE, "Boolean.logicalAnd(", BooleanType.INSTANCE, ", ",   BooleanType.INSTANCE, ")"),
        new Operation.Binary(BooleanType.INSTANCE, "Boolean.logicalOr(", BooleanType.INSTANCE, ", ",   BooleanType.INSTANCE, ")"),
        new Operation.Binary(BooleanType.INSTANCE, "Boolean.logicalXor(", BooleanType.INSTANCE, ", ",   BooleanType.INSTANCE, ")"),

        // Note: For now, we are omitting all the Character.is<...> methods. We can add them in the future.

        new Operation.Unary(BooleanType.INSTANCE, "Float.isFinite(", FloatType.INSTANCE, ")"),
        new Operation.Unary(BooleanType.INSTANCE, "Float.isInfinite(", FloatType.INSTANCE, ")"),
        new Operation.Unary(BooleanType.INSTANCE, "Float.isNaN(", FloatType.INSTANCE, ")"),

        new Operation.Unary(BooleanType.INSTANCE, "Double.isFinite(", DoubleType.INSTANCE, ")"),
        new Operation.Unary(BooleanType.INSTANCE, "Double.isInfinite(", DoubleType.INSTANCE, ")"),
        new Operation.Unary(BooleanType.INSTANCE, "Double.isNaN(", DoubleType.INSTANCE, ")"),

        new Operation.Ternary(BooleanType.INSTANCE, "(", BooleanType.INSTANCE, " ? ", BooleanType.INSTANCE, " : ", BooleanType.INSTANCE, ")")
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
}
