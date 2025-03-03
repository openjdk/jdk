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
        new Operation.Unary(Type.bytes(), "((byte)", Type.chars(), ")"),
        new Operation.Unary(Type.bytes(), "((byte)", Type.shorts(), ")"),
        new Operation.Unary(Type.bytes(), "((byte)", Type.ints(), ")"),
        new Operation.Unary(Type.bytes(), "((byte)", Type.longs(), ")"),
        new Operation.Unary(Type.bytes(), "((byte)", Type.floats(), ")"),
        new Operation.Unary(Type.bytes(), "((byte)", Type.doubles(), ")"),
        // Note: There is no cast from boolean

        new Operation.Ternary(Type.bytes(), "(", Type.booleans(), " ? ", Type.bytes(), " : ", Type.bytes(), ")")
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
        new Operation.Unary(Type.chars(), "((char)", Type.bytes(), ")"),
        new Operation.Unary(Type.chars(), "((char)", Type.shorts(), ")"),
        new Operation.Unary(Type.chars(), "((char)", Type.ints(), ")"),
        new Operation.Unary(Type.chars(), "((char)", Type.longs(), ")"),
        new Operation.Unary(Type.chars(), "((char)", Type.floats(), ")"),
        new Operation.Unary(Type.chars(), "((char)", Type.doubles(), ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.chars(), "Character.reverseBytes(", Type.chars(), ")"),

        new Operation.Ternary(Type.chars(), "(", Type.booleans(), " ? ", Type.chars(), " : ", Type.chars(), ")")
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
        new Operation.Unary(Type.shorts(), "((short)", Type.bytes(), ")"),
        new Operation.Unary(Type.shorts(), "((short)", Type.chars(), ")"),
        new Operation.Unary(Type.shorts(), "((short)", Type.ints(), ")"),
        new Operation.Unary(Type.shorts(), "((short)", Type.longs(), ")"),
        new Operation.Unary(Type.shorts(), "((short)", Type.floats(), ")"),
        new Operation.Unary(Type.shorts(), "((short)", Type.doubles(), ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.shorts(), "Short.reverseBytes(", Type.shorts(), ")"),

        // Note: Float.floatToFloat16 can lead to issues, because NaN values are not always
        //       represented by the same short bits.

        new Operation.Ternary(Type.shorts(), "(", Type.booleans(), " ? ", Type.shorts(), " : ", Type.shorts(), ")")
    );

    private static final List<Operation> INT_OPERATIONS = List.of(
        new Operation.Unary(Type.ints(), "((int)", Type.bytes(), ")"),
        new Operation.Unary(Type.ints(), "((int)", Type.chars(), ")"),
        new Operation.Unary(Type.ints(), "((int)", Type.shorts(), ")"),
        new Operation.Unary(Type.ints(), "((int)", Type.longs(), ")"),
        new Operation.Unary(Type.ints(), "((int)", Type.floats(), ")"),
        new Operation.Unary(Type.ints(), "((int)", Type.doubles(), ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.ints(), "(-(", Type.ints(), "))"),
        new Operation.Unary(Type.ints(), "(~", Type.ints(), ")"),

        new Operation.Binary(Type.ints(), "(", Type.ints(), " + ",   Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " - ",   Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " * ",   Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " / ",   Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " % ",   Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " & ",   Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " | ",   Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " ^ ",   Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " << ",  Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " >> ",  Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "(", Type.ints(), " >>> ", Type.ints(), ")"),

        new Operation.Binary(Type.ints(), "Byte.compare(", Type.bytes(), ", ", Type.bytes(), ")"),
        new Operation.Binary(Type.ints(), "Byte.compareUnsigned(", Type.bytes(), ", ", Type.bytes(), ")"),
        new Operation.Unary(Type.ints(), "Byte.toUnsignedInt(", Type.bytes(), ")"),

        new Operation.Binary(Type.ints(), "Character.compare(", Type.chars(), ", ", Type.chars(), ")"),

        new Operation.Binary(Type.ints(), "Short.compare(", Type.shorts(), ", ", Type.shorts(), ")"),
        new Operation.Binary(Type.ints(), "Short.compareUnsigned(", Type.shorts(), ", ", Type.shorts(), ")"),
        new Operation.Unary(Type.ints(), "Short.toUnsignedInt(", Type.shorts(), ")"),

        new Operation.Unary(Type.ints(), "Integer.bitCount(", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.compare(", Type.ints(), ", ", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.compareUnsigned(", Type.ints(), ", ", Type.ints(), ")"),
        //new Operation.Binary(Type.ints(), "Integer.compress(", Type.ints(), ", ", Type.ints(), ")"),
        // TODO: add back after JDK-8350896
        new Operation.Binary(Type.ints(), "Integer.divideUnsigned(", Type.ints(), ", ", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.expand(", Type.ints(), ", ", Type.ints(), ")"),
        new Operation.Unary(Type.ints(), "Integer.highestOneBit(", Type.ints(), ")"),
        new Operation.Unary(Type.ints(), "Integer.lowestOneBit(", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.min(", Type.ints(), ", ", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.max(", Type.ints(), ", ", Type.ints(), ")"),
        new Operation.Unary(Type.ints(), "Integer.numberOfLeadingZeros(", Type.ints(), ")"),
        new Operation.Unary(Type.ints(), "Integer.numberOfTrailingZeros(", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.remainderUnsigned(", Type.ints(), ", ", Type.ints(), ")"),
        new Operation.Unary(Type.ints(), "Integer.reverse(", Type.ints(), ")"),
        new Operation.Unary(Type.ints(), "Integer.reverseBytes(", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.rotateLeft(", Type.ints(), ", ", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.rotateRight(", Type.ints(), ", ", Type.ints(), ")"),
        new Operation.Unary(Type.ints(), "Integer.signum(", Type.ints(), ")"),
        new Operation.Binary(Type.ints(), "Integer.sum(", Type.ints(), ", ", Type.ints(), ")"),

        new Operation.Unary(Type.ints(), "Long.bitCount(", Type.longs(), ")"),
        new Operation.Binary(Type.ints(), "Long.compare(", Type.longs(), ", ", Type.longs(), ")"),
        new Operation.Binary(Type.ints(), "Long.compareUnsigned(", Type.longs(), ", ", Type.longs(), ")"),
        new Operation.Unary(Type.ints(), "Long.numberOfLeadingZeros(", Type.longs(), ")"),
        new Operation.Unary(Type.ints(), "Long.numberOfTrailingZeros(", Type.longs(), ")"),
        new Operation.Unary(Type.ints(), "Long.signum(", Type.longs(), ")"),

        new Operation.Binary(Type.ints(), "Float.compare(", Type.floats(), ", ", Type.floats(), ")"),
        new Operation.Unary(Type.ints(), "Float.floatToIntBits(", Type.floats(), ")"),
        // Note: Float.floatToRawIntBits can lead to issues, because the NaN values are not always
        //       represented by the same int bits.

        new Operation.Binary(Type.ints(), "Double.compare(", Type.doubles(), ", ", Type.doubles(), ")"),

        new Operation.Binary(Type.ints(), "Boolean.compare(", Type.booleans(), ", ", Type.booleans(), ")"),

        new Operation.Ternary(Type.ints(), "(", Type.booleans(), " ? ", Type.ints(), " : ", Type.ints(), ")")
    );

    private static final List<Operation> LONG_OPERATIONS = List.of(
        new Operation.Unary(Type.longs(), "((long)", Type.bytes(), ")"),
        new Operation.Unary(Type.longs(), "((long)", Type.chars(), ")"),
        new Operation.Unary(Type.longs(), "((long)", Type.shorts(), ")"),
        new Operation.Unary(Type.longs(), "((long)", Type.ints(), ")"),
        new Operation.Unary(Type.longs(), "((long)", Type.floats(), ")"),
        new Operation.Unary(Type.longs(), "((long)", Type.doubles(), ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.longs(), "(-(", Type.longs(), "))"),
        new Operation.Unary(Type.longs(), "(~", Type.longs(), ")"),

        new Operation.Binary(Type.longs(), "(", Type.longs(), " + ",   Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " - ",   Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " * ",   Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " / ",   Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " % ",   Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " & ",   Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " | ",   Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " ^ ",   Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " << ",  Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " >> ",  Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "(", Type.longs(), " >>> ", Type.longs(), ")"),

        new Operation.Unary(Type.longs(), "Byte.toUnsignedLong(", Type.bytes(), ")"),

        new Operation.Unary(Type.longs(), "Short.toUnsignedLong(", Type.shorts(), ")"),

        new Operation.Unary(Type.longs(), "Integer.toUnsignedLong(", Type.ints(), ")"),

        // new Operation.Binary(Type.longs(), "Long.compress(", Type.longs(), ", ", Type.longs(), ")"),
        // TODO: add back after JDK-8350896
        new Operation.Binary(Type.longs(), "Long.divideUnsigned(", Type.longs(), ", ", Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "Long.expand(", Type.longs(), ", ", Type.longs(), ")"),
        new Operation.Unary(Type.longs(), "Long.highestOneBit(", Type.longs(), ")"),
        new Operation.Unary(Type.longs(), "Long.lowestOneBit(", Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "Long.min(", Type.longs(), ", ", Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "Long.max(", Type.longs(), ", ", Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "Long.remainderUnsigned(", Type.longs(), ", ", Type.longs(), ")"),
        new Operation.Unary(Type.longs(), "Long.reverse(", Type.longs(), ")"),
        new Operation.Unary(Type.longs(), "Long.reverseBytes(", Type.longs(), ")"),
        new Operation.Binary(Type.longs(), "Long.rotateLeft(", Type.longs(), ", ", Type.ints(), ")"),
        new Operation.Binary(Type.longs(), "Long.rotateRight(", Type.longs(), ", ", Type.ints(), ")"),
        new Operation.Binary(Type.longs(), "Long.sum(", Type.longs(), ", ", Type.longs(), ")"),

        new Operation.Unary(Type.longs(), "Double.doubleToLongBits(", Type.doubles(), ")"),
        // Note: Double.doubleToRawLongBits can lead to issues, because the NaN values are not always
        //       represented by the same long bits.

        new Operation.Ternary(Type.longs(), "(", Type.booleans(), " ? ", Type.longs(), " : ", Type.longs(), ")")
    );

    private static final List<Operation> FLOAT_OPERATIONS = List.of(
        new Operation.Unary(Type.floats(), "((float)", Type.bytes(), ")"),
        new Operation.Unary(Type.floats(), "((float)", Type.chars(), ")"),
        new Operation.Unary(Type.floats(), "((float)", Type.shorts(), ")"),
        new Operation.Unary(Type.floats(), "((float)", Type.ints(), ")"),
        new Operation.Unary(Type.floats(), "((float)", Type.longs(), ")"),
        new Operation.Unary(Type.floats(), "((float)", Type.doubles(), ")"),

        new Operation.Unary(Type.floats(), "(-(", Type.floats(), "))"),

        new Operation.Binary(Type.floats(), "(", Type.floats(), " + ",   Type.floats(), ")"),
        new Operation.Binary(Type.floats(), "(", Type.floats(), " - ",   Type.floats(), ")"),
        new Operation.Binary(Type.floats(), "(", Type.floats(), " * ",   Type.floats(), ")"),
        new Operation.Binary(Type.floats(), "(", Type.floats(), " / ",   Type.floats(), ")"),
        new Operation.Binary(Type.floats(), "(", Type.floats(), " % ",   Type.floats(), ")"),

        //new Operation.Unary(Type.floats(), "Float.float16ToFloat(", Type.shorts(), ")"),
        // TODO: add back after JDK-8350835
        new Operation.Unary(Type.floats(), "Float.intBitsToFloat(", Type.ints(), ")"),
        new Operation.Binary(Type.floats(), "Float.max(", Type.floats(), ", ", Type.floats(), ")"),
        new Operation.Binary(Type.floats(), "Float.min(", Type.floats(), ", ", Type.floats(), ")"),
        new Operation.Binary(Type.floats(), "Float.sum(", Type.floats(), ", ", Type.floats(), ")"),

        new Operation.Ternary(Type.floats(), "(", Type.booleans(), " ? ", Type.floats(), " : ", Type.floats(), ")")
    );

    private static final List<Operation> DOUBLE_OPERATIONS = List.of(
        new Operation.Unary(Type.doubles(), "((double)", Type.bytes(), ")"),
        new Operation.Unary(Type.doubles(), "((double)", Type.chars(), ")"),
        new Operation.Unary(Type.doubles(), "((double)", Type.shorts(), ")"),
        new Operation.Unary(Type.doubles(), "((double)", Type.ints(), ")"),
        new Operation.Unary(Type.doubles(), "((double)", Type.longs(), ")"),
        new Operation.Unary(Type.doubles(), "((double)", Type.floats(), ")"),
        // Note: There is no cast from boolean

        new Operation.Unary(Type.doubles(), "(-(", Type.doubles(), "))"),

        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " + ",   Type.doubles(), ")"),
        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " - ",   Type.doubles(), ")"),
        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " * ",   Type.doubles(), ")"),
        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " / ",   Type.doubles(), ")"),
        new Operation.Binary(Type.doubles(), "(", Type.doubles(), " % ",   Type.doubles(), ")"),

        new Operation.Unary(Type.doubles(), "Double.longBitsToDouble(", Type.ints(), ")"),
        new Operation.Binary(Type.doubles(), "Double.max(", Type.doubles(), ", ", Type.doubles(), ")"),
        new Operation.Binary(Type.doubles(), "Double.min(", Type.doubles(), ", ", Type.doubles(), ")"),
        new Operation.Binary(Type.doubles(), "Double.sum(", Type.doubles(), ", ", Type.doubles(), ")"),

        new Operation.Ternary(Type.doubles(), "(", Type.booleans(), " ? ", Type.doubles(), " : ", Type.doubles(), ")")
    );

    private static final List<Operation> BOOLEAN_OPERATIONS = List.of(
        // Note: there is no casting / conversion from an to boolean directly.

        new Operation.Unary(Type.booleans(), "(!(", Type.booleans(), "))"),

        new Operation.Binary(Type.booleans(), "(", Type.booleans(), " || ",   Type.booleans(), ")"),
        new Operation.Binary(Type.booleans(), "(", Type.booleans(), " && ",   Type.booleans(), ")"),
        new Operation.Binary(Type.booleans(), "(", Type.booleans(), " ^ ",    Type.booleans(), ")"),

        new Operation.Binary(Type.booleans(), "Boolean.logicalAnd(", Type.booleans(), ", ",   Type.booleans(), ")"),
        new Operation.Binary(Type.booleans(), "Boolean.logicalOr(", Type.booleans(), ", ",   Type.booleans(), ")"),
        new Operation.Binary(Type.booleans(), "Boolean.logicalXor(", Type.booleans(), ", ",   Type.booleans(), ")"),

        // Note: For now, we are omitting all the Character.is<...> methods. We can add them in the future.

        new Operation.Unary(Type.booleans(), "Float.isFinite(", Type.floats(), ")"),
        new Operation.Unary(Type.booleans(), "Float.isInfinite(", Type.floats(), ")"),
        new Operation.Unary(Type.booleans(), "Float.isNaN(", Type.floats(), ")"),

        new Operation.Unary(Type.booleans(), "Double.isFinite(", Type.doubles(), ")"),
        new Operation.Unary(Type.booleans(), "Double.isInfinite(", Type.doubles(), ")"),
        new Operation.Unary(Type.booleans(), "Double.isNaN(", Type.doubles(), ")"),

        new Operation.Ternary(Type.booleans(), "(", Type.booleans(), " ? ", Type.booleans(), " : ", Type.booleans(), ")")
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

    private static final List<Operation> generateVectorAPIOperations() {
        List<Operation> ops = new ArrayList<Operation>();

        for (var type : Type.VECTOR_API_TYPES) {
            ops.add(new Operation.Unary(type, type.vectorType + ".broadcast(", type.elementType, ")"));
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
