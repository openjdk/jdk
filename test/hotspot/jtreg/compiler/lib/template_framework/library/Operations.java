/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework.library;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static compiler.lib.template_framework.library.PrimitiveType.BYTES;
import static compiler.lib.template_framework.library.PrimitiveType.SHORTS;
import static compiler.lib.template_framework.library.PrimitiveType.CHARS;
import static compiler.lib.template_framework.library.PrimitiveType.INTS;
import static compiler.lib.template_framework.library.PrimitiveType.LONGS;
import static compiler.lib.template_framework.library.PrimitiveType.FLOATS;
import static compiler.lib.template_framework.library.PrimitiveType.DOUBLES;
import static compiler.lib.template_framework.library.PrimitiveType.BOOLEANS;
import static compiler.lib.template_framework.library.Float16Type.FLOAT16;

/**
 * This class provides various lists of {@link Expression}s, that represent Java operators or library
 * methods. For example, we represent arithmetic operations on primitive types.
 */
public final class Operations {

    // private constructor to avoid instantiation.
    private Operations() {}

    private static Expression.Info WITH_ARITHMETIC_EXCEPTION = new Expression.Info().withExceptions(Set.of("ArithmeticException"));
    private static Expression.Info WITH_NONDETERMINISTIC_RESULT = new Expression.Info().withNondeterministicResult();


    /**
     * Provides a lits of operations on {@link PrimitiveType}s, such as arithmetic, logical,
     * and cast operations.
     */
    public static final List<Expression> PRIMITIVE_OPERATIONS = generatePrimitiveOperations();

    public static final List<Expression> FLOAT16_OPERATIONS = generateFloat16Operations();

    public static final List<Expression> SCALAR_NUMERIC_OPERATIONS = concat(
            PRIMITIVE_OPERATIONS,
            FLOAT16_OPERATIONS
    );

    @SafeVarargs
    private static List<Expression> concat(List<Expression>... lists) {
        return Arrays.stream(lists)
                     .flatMap(List::stream)
                     .collect(Collectors.toList());
    }

    private static void addComparisonOperations(List<Expression> ops, String operatorName, CodeGenerationDataNameType type) {
        for (String mask : List.of("==", "!=", "<", ">", "<=", ">=")) {
            ops.add(Expression.make(BOOLEANS, "(" + operatorName + "(", type, ", ", type, ")" + mask + "0)"));
        }
    }

    private static List<Expression> generatePrimitiveOperations() {
        List<Expression> ops = new ArrayList<>();

        // Cast between all primitive types. Except for Boolean, we cannot cast from and to.
        CodeGenerationDataNameType.INTEGRAL_AND_FLOATING_TYPES.stream().forEach(src -> {
            CodeGenerationDataNameType.INTEGRAL_AND_FLOATING_TYPES.stream().forEach(dst -> {
                ops.add(Expression.make(dst, "(" + dst.name() + ")(", src,   ")"));
            });
        });

        // Ternary operator.
        CodeGenerationDataNameType.INTEGRAL_AND_FLOATING_TYPES.stream().forEach(type -> {
            ops.add(Expression.make(type, "(", BOOLEANS, "?", type, ":", type, ")"));
        });

        List.of(INTS, LONGS).stream().forEach(type -> {
            // Arithmetic operators
            ops.add(Expression.make(type, "(-(", type, "))"));
            ops.add(Expression.make(type, "(", type, " + ", type, ")"));
            ops.add(Expression.make(type, "(", type, " - ", type, ")"));
            ops.add(Expression.make(type, "(", type, " * ", type, ")"));
            ops.add(Expression.make(type, "(", type, " / ", type, ")", WITH_ARITHMETIC_EXCEPTION));
            ops.add(Expression.make(type, "(", type, " % ", type, ")", WITH_ARITHMETIC_EXCEPTION));

            // Bitwise Operators (non short-circuit)
            ops.add(Expression.make(type, "(~(", type, "))"));
            ops.add(Expression.make(type, "(", type, " & ",   type, ")"));
            ops.add(Expression.make(type, "(", type, " | ",   type, ")"));
            ops.add(Expression.make(type, "(", type, " ^ ",   type, ")"));
            ops.add(Expression.make(type, "(", type, " << ",  type, ")"));
            ops.add(Expression.make(type, "(", type, " >> ",  type, ")"));
            ops.add(Expression.make(type, "(", type, " >>> ", type, ")"));

            // Relational / Comparison Operators
            ops.add(Expression.make(BOOLEANS, "(", type, " == ", type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " != ", type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " > ",  type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " < ",  type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " >= ", type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " <= ", type, ")"));
            addComparisonOperations(ops, type.boxedTypeName() + ".compare", type);
            addComparisonOperations(ops, type.boxedTypeName() + ".compareUnsigned", type); // ugt, uge, ule, ult
        });

        CodeGenerationDataNameType.FLOATING_TYPES.stream().forEach(type -> {
            // Arithmetic operators
            ops.add(Expression.make(type, "(-(", type, "))"));
            ops.add(Expression.make(type, "(", type, " + ", type, ")"));
            ops.add(Expression.make(type, "(", type, " - ", type, ")"));
            ops.add(Expression.make(type, "(", type, " * ", type, ")"));
            ops.add(Expression.make(type, "(", type, " / ", type, ")"));
            ops.add(Expression.make(type, "(", type, " % ", type, ")"));

            // Relational / Comparison Operators
            ops.add(Expression.make(BOOLEANS, "(", type, " == ", type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " != ", type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " > ",  type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " < ",  type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " >= ", type, ")"));
            ops.add(Expression.make(BOOLEANS, "(", type, " <= ", type, ")"));
            addComparisonOperations(ops, type.boxedTypeName() + ".compare", type);
        });

        // ------------ byte -------------
        // Cast and ternary operator handled above.
        // Arithmetic operations are not performed in byte, but rather promoted to int.

        // ------------ Byte -------------
        ops.add(Expression.make(INTS,  "Byte.compare(",         BYTES, ", ", BYTES, ")"));
        ops.add(Expression.make(INTS,  "Byte.compareUnsigned(", BYTES, ", ", BYTES, ")"));
        ops.add(Expression.make(INTS,  "Byte.toUnsignedInt(",   BYTES, ")"));
        ops.add(Expression.make(LONGS, "Byte.toUnsignedLong(",  BYTES, ")"));

        // ------------ char -------------
        // Cast and ternary operator handled above.
        // Arithmetic operations are not performned in char, but rather promoted to int.

        // ------------ Character -------------
        ops.add(Expression.make(INTS,  "Character.compare(",      CHARS, ", ", CHARS, ")"));
        ops.add(Expression.make(CHARS, "Character.reverseBytes(", CHARS, ")"));

        // ------------ short -------------
        // Cast and ternary operator handled above.
        // Arithmetic operations are not performned in short, but rather promoted to int.

        // ------------ Short -------------
        ops.add(Expression.make(INTS,   "Short.compare(",         SHORTS, ", ", SHORTS, ")"));
        ops.add(Expression.make(INTS,   "Short.compareUnsigned(", SHORTS, ", ", SHORTS, ")"));
        ops.add(Expression.make(SHORTS, "Short.reverseBytes(",    SHORTS, ")"));
        ops.add(Expression.make(INTS,   "Short.toUnsignedInt(",   SHORTS, ")"));
        ops.add(Expression.make(LONGS,  "Short.toUnsignedLong(",  SHORTS, ")"));

        // ------------ int -------------
        // Cast and ternary operator handled above.
        // Arithmetic, Bitwise, Relational / Comparison handled above.

        // ------------ Integer -------------
        ops.add(Expression.make(INTS,  "Integer.bitCount(", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.compare(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.compareUnsigned(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.compress(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.divideUnsigned(", INTS, ", ", INTS, ")", WITH_ARITHMETIC_EXCEPTION));
        ops.add(Expression.make(INTS,  "Integer.expand(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.highestOneBit(", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.lowestOneBit(", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.max(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.min(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.numberOfLeadingZeros(", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.numberOfTrailingZeros(", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.remainderUnsigned(", INTS, ", ", INTS, ")", WITH_ARITHMETIC_EXCEPTION));
        ops.add(Expression.make(INTS,  "Integer.reverse(", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.reverseBytes(", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.rotateLeft(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.rotateRight(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.signum(", INTS, ")"));
        ops.add(Expression.make(INTS,  "Integer.sum(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(LONGS, "Integer.toUnsignedLong(", INTS, ")"));

        // ------------ long -------------
        // Cast and ternary operator handled above.
        // Arithmetic, Bitwise, Relational / Comparison handled above.

        // ------------ Long -------------
        ops.add(Expression.make(INTS,  "Long.bitCount(", LONGS, ")"));
        ops.add(Expression.make(INTS,  "Long.compare(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(INTS,  "Long.compareUnsigned(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.compress(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.divideUnsigned(", LONGS, ", ", LONGS, ")", WITH_ARITHMETIC_EXCEPTION));
        ops.add(Expression.make(LONGS, "Long.expand(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.highestOneBit(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.lowestOneBit(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.max(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.min(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(INTS,  "Long.numberOfLeadingZeros(", LONGS, ")"));
        ops.add(Expression.make(INTS,  "Long.numberOfTrailingZeros(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.remainderUnsigned(", LONGS, ", ", LONGS, ")", WITH_ARITHMETIC_EXCEPTION));
        ops.add(Expression.make(LONGS, "Long.reverse(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.reverseBytes(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.rotateLeft(", LONGS, ", ", INTS, ")"));
        ops.add(Expression.make(LONGS, "Long.rotateRight(", LONGS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS,  "Long.signum(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.sum(", LONGS, ", ", LONGS, ")"));

        // ------------ float -------------
        // Cast and ternary operator handled above.
        // Arithmetic and Relational / Comparison handled above.

        // ------------ Float -------------
        ops.add(Expression.make(INTS, "Float.compare(", FLOATS, ", ", FLOATS, ")"));
        ops.add(Expression.make(INTS, "Float.floatToIntBits(", FLOATS, ")"));
        // Note: there are multiple NaN values with different bit representations.
        ops.add(Expression.make(INTS, "Float.floatToRawIntBits(", FLOATS, ")", WITH_NONDETERMINISTIC_RESULT));
        ops.add(Expression.make(FLOATS, "Float.float16ToFloat(", SHORTS, ")"));
        ops.add(Expression.make(FLOATS, "Float.intBitsToFloat(", INTS, ")"));
        ops.add(Expression.make(BOOLEANS, "Float.isFinite(", FLOATS, ")"));
        ops.add(Expression.make(BOOLEANS, "Float.isInfinite(", FLOATS, ")"));
        ops.add(Expression.make(BOOLEANS, "Float.isNaN(", FLOATS, ")"));
        ops.add(Expression.make(FLOATS, "Float.max(", FLOATS, ", ", FLOATS, ")"));
        ops.add(Expression.make(FLOATS, "Float.min(", FLOATS, ", ", FLOATS, ")"));
        ops.add(Expression.make(FLOATS, "Float.sum(", FLOATS, ", ", FLOATS, ")"));

        // ------------ double -------------
        // Cast and ternary operator handled above.
        // Arithmetic and Relational / Comparison handled above.

        // ------------ Double -------------
        ops.add(Expression.make(INTS,     "Double.compare(", DOUBLES, ", ", DOUBLES, ")"));
        ops.add(Expression.make(LONGS,    "Double.doubleToLongBits(", DOUBLES, ")"));
        // Note: there are multiple NaN values with different bit representations.
        ops.add(Expression.make(LONGS,    "Double.doubleToRawLongBits(", DOUBLES, ")", WITH_NONDETERMINISTIC_RESULT));
        ops.add(Expression.make(DOUBLES,  "Double.longBitsToDouble(", LONGS, ")"));
        ops.add(Expression.make(BOOLEANS, "Double.isFinite(", DOUBLES, ")"));
        ops.add(Expression.make(BOOLEANS, "Double.isInfinite(", DOUBLES, ")"));
        ops.add(Expression.make(BOOLEANS, "Double.isNaN(", DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES,  "Double.max(", DOUBLES, ", ", DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES,  "Double.min(", DOUBLES, ", ", DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES,  "Double.sum(", DOUBLES, ", ", DOUBLES, ")"));

        // ------------ boolean -------------
        // Cast and ternary operator handled above.
        // There are no boolean arithmetic operators

        // Logical operators
        ops.add(Expression.make(BOOLEANS, "(!(", BOOLEANS, "))"));
        ops.add(Expression.make(BOOLEANS, "(", BOOLEANS, " || ", BOOLEANS, ")"));
        ops.add(Expression.make(BOOLEANS, "(", BOOLEANS, " && ", BOOLEANS, ")"));
        ops.add(Expression.make(BOOLEANS, "(", BOOLEANS, " ^ ",  BOOLEANS, ")"));

        // ------------ Boolean -------------
        ops.add(Expression.make(INTS,     "Boolean.compare(",    BOOLEANS, ", ", BOOLEANS, ")"));
        ops.add(Expression.make(BOOLEANS, "Boolean.logicalAnd(", BOOLEANS, ", ", BOOLEANS, ")"));
        ops.add(Expression.make(BOOLEANS, "Boolean.logicalOr(",  BOOLEANS, ", ", BOOLEANS, ")"));
        ops.add(Expression.make(BOOLEANS, "Boolean.logicalXor(", BOOLEANS, ", ", BOOLEANS, ")"));

        // TODO: Math and other classes.

        // Make sure the list is not modifiable.
        return List.copyOf(ops);
    }

    private static List<Expression> generateFloat16Operations() {
        List<Expression> ops = new ArrayList<>();

        // Casts.
        CodeGenerationDataNameType.INTEGRAL_AND_FLOATING_TYPES.stream().forEach(type -> {
            if (type == CHARS) { return; }
            ops.add(Expression.make(FLOAT16, "Float16.valueOf(", type, ")"));
            ops.add(Expression.make(type, "", FLOAT16, "." + type.name() + "Value()"));
        });

        ops.add(Expression.make(FLOAT16, "Float16.abs(", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.add(", FLOAT16, ",", FLOAT16, ")"));
        ops.add(Expression.make(INTS, "Float16.compare(", FLOAT16, ",", FLOAT16, ")"));
        addComparisonOperations(ops, "Float16.compare", FLOAT16);
        ops.add(Expression.make(INTS, "(", FLOAT16, ").compareTo(",  FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.copySign(", FLOAT16, ",", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.divide(", FLOAT16, ",", FLOAT16, ")"));
        ops.add(Expression.make(BOOLEANS, "", FLOAT16, ".equals(", FLOAT16, ")"));
        // Note: there are multiple NaN values with different bit representations.
        ops.add(Expression.make(SHORTS, "Float16.float16ToRawShortBits(", FLOAT16, ")", WITH_NONDETERMINISTIC_RESULT));
        ops.add(Expression.make(SHORTS, "Float16.float16ToShortBits(", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.fma(", FLOAT16, ",", FLOAT16, ", ", FLOAT16, ")"));
        ops.add(Expression.make(INTS, "Float16.getExponent(", FLOAT16, ")"));
        ops.add(Expression.make(BOOLEANS, "Float16.isFinite(", FLOAT16, ")"));
        ops.add(Expression.make(BOOLEANS, "Float16.isInfinite(", FLOAT16, ")"));
        ops.add(Expression.make(BOOLEANS, "Float16.isNaN(", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.max(", FLOAT16, ",", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.min(", FLOAT16, ",", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.multiply(", FLOAT16, ",", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.negate(", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.nextDown(", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.nextUp(", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.scalb(", FLOAT16, ", ", INTS, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.shortBitsToFloat16(", SHORTS, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.signum(", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.sqrt(", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.subtract(", FLOAT16, ",", FLOAT16, ")"));
        ops.add(Expression.make(FLOAT16, "Float16.ulp(", FLOAT16, ")"));

        // Make sure the list is not modifiable.
        return List.copyOf(ops);
    }
}
