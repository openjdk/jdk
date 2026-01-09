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
import static compiler.lib.template_framework.library.CodeGenerationDataNameType.PRIMITIVE_TYPES;
import static compiler.lib.template_framework.library.CodeGenerationDataNameType.INTEGRAL_TYPES;
import static compiler.lib.template_framework.library.CodeGenerationDataNameType.FLOATING_TYPES;
import static compiler.lib.template_framework.library.CodeGenerationDataNameType.INT_LONG_TYPES;

/**
 * This class provides various lists of {@link Expression}s, that represent Java operators or library
 * methods. For example, we represent arithmetic operations on primitive types.
 */
public final class Operations {

    // private constructor to avoid instantiation.
    private Operations() {}

    private static Expression.Info WITH_ARITHMETIC_EXCEPTION = new Expression.Info().withExceptions(Set.of("ArithmeticException"));
    private static Expression.Info WITH_NONDETERMINISTIC_RESULT = new Expression.Info().withNondeterministicResult();
    private static Expression.Info WITH_ILLEGAL_ARGUMENT_EXCEPTION = new Expression.Info().withExceptions(Set.of("IllegalArgumentException"));
    private static Expression.Info WITH_OUT_OF_BOUNDS_EXCEPTION = new Expression.Info().withExceptions(Set.of("IndexOutOfBoundsException"));

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

    private enum VOPType {
        UNARY,
        BINARY,
        ASSOCIATIVE, // Binary and associative - safe for reductions of any type
        INTEGRAL_ASSOCIATIVE, // Binary - but only safe for integral reductions
        TERNARY
    }
    private record VOP(String name, VOPType type, List<PrimitiveType> elementTypes, boolean isDeterministic) {
        VOP(String name, VOPType type, List<PrimitiveType> elementTypes) {
            this(name, type, elementTypes, true);
        }
    }

    // TODO: consider enforcing precision instead of just blanket non-teterminism
    private static final List<VOP> VECTOR_OPS = List.of(
        new VOP("ABS",                  VOPType.UNARY,                PRIMITIVE_TYPES),
        new VOP("ACOS",                 VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("ADD",                  VOPType.INTEGRAL_ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("AND",                  VOPType.ASSOCIATIVE,          INTEGRAL_TYPES),
        new VOP("AND_NOT",              VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("ASHR",                 VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("ASIN",                 VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("ATAN",                 VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("ATAN2",                VOPType.BINARY,               FLOATING_TYPES,     false), // 2 ulp
        new VOP("BIT_COUNT",            VOPType.UNARY,                INTEGRAL_TYPES),
        new VOP("BITWISE_BLEND",        VOPType.TERNARY,              INTEGRAL_TYPES),
        new VOP("CBRT",                 VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("COMPRESS_BITS",        VOPType.BINARY,               INT_LONG_TYPES),
        new VOP("COS",                  VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("COSH",                 VOPType.UNARY,                FLOATING_TYPES,     false), // 2.5 ulp
        new VOP("DIV",                  VOPType.BINARY,               FLOATING_TYPES),
        new VOP("EXP",                  VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("EXPAND_BITS",          VOPType.BINARY,               INT_LONG_TYPES),
        new VOP("EXPM1",                VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("FIRST_NONZERO",        VOPType.ASSOCIATIVE,          PRIMITIVE_TYPES),
        new VOP("FMA",                  VOPType.TERNARY,              FLOATING_TYPES),
        new VOP("HYPOT",                VOPType.BINARY,               FLOATING_TYPES,     false), // 1.5 ulp
        new VOP("LEADING_ZEROS_COUNT",  VOPType.UNARY,                INTEGRAL_TYPES),
        new VOP("LOG",                  VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("LOG10",                VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("LOG1P",                VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("LSHL",                 VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("LSHR",                 VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("MIN",                  VOPType.ASSOCIATIVE,          PRIMITIVE_TYPES),
        new VOP("MAX",                  VOPType.ASSOCIATIVE,          PRIMITIVE_TYPES),
        new VOP("MUL",                  VOPType.INTEGRAL_ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("NEG",                  VOPType.UNARY,                PRIMITIVE_TYPES),
        new VOP("NOT",                  VOPType.UNARY,                INTEGRAL_TYPES),
        new VOP("OR",                   VOPType.ASSOCIATIVE,          INTEGRAL_TYPES),
        new VOP("POW",                  VOPType.BINARY,               FLOATING_TYPES,     false), // 1 ulp
        new VOP("REVERSE",              VOPType.UNARY,                INTEGRAL_TYPES),
        new VOP("REVERSE_BYTES",        VOPType.UNARY,                INTEGRAL_TYPES),
        new VOP("ROL",                  VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("ROR",                  VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("SADD",                 VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("SIN",                  VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp
        new VOP("SINH",                 VOPType.UNARY,                FLOATING_TYPES,     false), // 2.5 ulp
        new VOP("SQRT",                 VOPType.UNARY,                FLOATING_TYPES,     false), // 1 ulp (closest double value)
        new VOP("SSUB",                 VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("SUADD",                VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("SUB",                  VOPType.BINARY,               PRIMITIVE_TYPES),
        new VOP("SUSUB",                VOPType.BINARY,               INTEGRAL_TYPES),
        new VOP("TAN",                  VOPType.UNARY,                FLOATING_TYPES,     false), // 1.25 ulp
        new VOP("TANH",                 VOPType.UNARY,                FLOATING_TYPES,     false), // 2.5 ulp
        new VOP("TRAILING_ZEROS_COUNT", VOPType.UNARY,                INTEGRAL_TYPES),
        new VOP("UMAX",                 VOPType.ASSOCIATIVE,          INTEGRAL_TYPES),
        new VOP("UMIN",                 VOPType.ASSOCIATIVE,          INTEGRAL_TYPES),
        new VOP("XOR",                  VOPType.ASSOCIATIVE,          INTEGRAL_TYPES),
        new VOP("ZOMO",                 VOPType.UNARY,                INTEGRAL_TYPES)
    );

    private static final List<VOP> VECTOR_CMP = List.of(
        new VOP("EQ",                   VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("GE",                   VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("GT",                   VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("LE",                   VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("LT",                   VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("NE",                   VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("UGE",                  VOPType.ASSOCIATIVE, INTEGRAL_TYPES),
        new VOP("UGT",                  VOPType.ASSOCIATIVE, INTEGRAL_TYPES),
        new VOP("ULE",                  VOPType.ASSOCIATIVE, INTEGRAL_TYPES),
        new VOP("ULT",                  VOPType.ASSOCIATIVE, INTEGRAL_TYPES)
    );

    private static final List<VOP> VECTOR_TEST = List.of(
        new VOP("IS_DEFAULT",           VOPType.UNARY, PRIMITIVE_TYPES),
        new VOP("IS_NEGATIVE",          VOPType.UNARY, PRIMITIVE_TYPES),
        new VOP("IS_FINITE",            VOPType.UNARY, FLOATING_TYPES),
        new VOP("IS_NAN",               VOPType.UNARY, FLOATING_TYPES),
        new VOP("IS_INFINITE",          VOPType.UNARY, FLOATING_TYPES)
    );

    // TODO: Conversion VectorOperators -> convertShape

    private static List<Expression> generateVectorOperations() {
        List<Expression> ops = new ArrayList<>();

        for (var type : CodeGenerationDataNameType.VECTOR_VECTOR_TYPES) {
            // ----------------- IntVector, FloatVector, ... --------------------
            ops.add(Expression.make(type, "", type, ".abs()"));
            ops.add(Expression.make(type, "", type, ".add(", type.elementType, ")"));
            ops.add(Expression.make(type, "", type, ".add(", type.elementType, ", ", type.maskType, ")"));
            ops.add(Expression.make(type, "", type, ".add(", type, ")"));
            ops.add(Expression.make(type, "", type, ".add(", type, ", ", type.maskType, ")"));

            // If VLENGTH*scale overflows, then a IllegalArgumentException is thrown.
            ops.add(Expression.make(type, "", type, ".addIndex(1)"));
            ops.add(Expression.make(type, "", type, ".addIndex(", INTS, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));

            if (!type.elementType.isFloating()) {
                ops.add(Expression.make(type, "", type, ".and(", type.elementType, ")"));
                ops.add(Expression.make(type, "", type, ".and(", type, ")"));
                ops.add(Expression.make(type, "", type, ".bitwiseBlend(", type.elementType, ", ", type.elementType, ")"));
                ops.add(Expression.make(type, "", type, ".bitwiseBlend(", type.elementType, ", ", type,             ")"));
                ops.add(Expression.make(type, "", type, ".bitwiseBlend(", type,             ", ", type.elementType, ")"));
                ops.add(Expression.make(type, "", type, ".bitwiseBlend(", type,             ", ", type,             ")"));
                ops.add(Expression.make(type, "", type, ".not()"));
                ops.add(Expression.make(type, "", type, ".or(", type.elementType, ")"));
                ops.add(Expression.make(type, "", type, ".or(", type, ")"));
            }

            ops.add(Expression.make(type, "", type, ".blend(", type.elementType, ", ", type.maskType, ")"));
            ops.add(Expression.make(type, "", type, ".blend(", LONGS, ", ", type.maskType, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".blend(", type, ", ", type.maskType, ")"));

            ops.add(Expression.make(type, type.name() + ".broadcast(" + type.speciesName + ", ", type.elementType, ")"));
            ops.add(Expression.make(type, type.name() + ".broadcast(" + type.speciesName + ", ", LONGS, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));

            for (var type2 : CodeGenerationDataNameType.VECTOR_VECTOR_TYPES) {
                ops.add(Expression.make(type, "((" + type.name() + ")", type2 , ".castShape(" + type.speciesName + ", 0))"));
                ops.add(Expression.make(type, "((" + type.name() + ")", type2 , ".castShape(" + type.speciesName + ", ", INTS, "))", WITH_OUT_OF_BOUNDS_EXCEPTION));
            }

            // skip check

            for (VOP cmp : VECTOR_CMP) {
                if (cmp.elementTypes().contains(type.elementType)) {
                    ops.add(Expression.make(type.maskType, "", type, ".compare(VectorOperators." + cmp.name() + ", ", type.elementType, ")"));
                    ops.add(Expression.make(type.maskType, "", type, ".compare(VectorOperators." + cmp.name() + ", ", type.elementType, ", ", type.maskType, ")"));
                    ops.add(Expression.make(type.maskType, "", type, ".compare(VectorOperators." + cmp.name() + ", ", LONGS, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));
                    ops.add(Expression.make(type.maskType, "", type, ".compare(VectorOperators." + cmp.name() + ", ", LONGS, ", ", type.maskType, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));
                    ops.add(Expression.make(type.maskType, "", type, ".compare(VectorOperators." + cmp.name() + ", ", type, ")"));
                }
            }

            ops.add(Expression.make(type, "", type, ".compress(", type.maskType, ")"));

            for (var type2 : CodeGenerationDataNameType.VECTOR_VECTOR_TYPES) {
                // "convert" keeps the same shape, i.e. length of the vector in bits.
                if (type.byteSize() == type2.byteSize()) {
                    ops.add(Expression.make(type,
                                                "((" + type.name() + ")",
                                                type2,
                                                ".convert(VectorOperators.Conversion.ofCast("
                                                    + type2.elementType.name() +  ".class, "
                                                    + type.elementType.name() + ".class), 0))"));
                    ops.add(Expression.make(type,
                                                "((" + type.name() + ")",
                                                type2,
                                                ".convert(VectorOperators.Conversion.ofCast("
                                                    + type2.elementType.name() +  ".class, "
                                                    + type.elementType.name() + ".class),",
                                                INTS, // part
                                                "))", WITH_OUT_OF_BOUNDS_EXCEPTION));
                }

                // Reinterpretation FROM floating is not safe, because of different NaN encodings, i.e.
                // we will not get deterministic results.
                var reinterpretInfo = type2.elementType.isFloating() ? WITH_NONDETERMINISTIC_RESULT : new Expression.Info();

                // The following "reinterpret" operations require same input and output shape.
                if (type.byteSize() == type2.byteSize()) {
                    ops.add(Expression.make(type,
                                            "((" + type.name() + ")",
                                            type2,
                                            ".convert(VectorOperators.Conversion.ofReinterpret("
                                                + type2.elementType.name() +  ".class, "
                                                + type.elementType.name() + ".class), 0))", reinterpretInfo));
                    ops.add(Expression.make(type,
                                            "((" + type.name() + ")",
                                            type2,
                                            ".convert(VectorOperators.Conversion.ofReinterpret("
                                                + type2.elementType.name() +  ".class, "
                                                + type.elementType.name() + ".class),",
                                            INTS, // part
                                            "))", reinterpretInfo.combineWith(WITH_OUT_OF_BOUNDS_EXCEPTION)));
                    if (type.elementType == BYTES) {
                        ops.add(Expression.make(type, "", type2, ".reinterpretAsBytes()", reinterpretInfo));
                    }
                    if (type.elementType == SHORTS) {
                        ops.add(Expression.make(type, "", type2, ".reinterpretAsShorts()", reinterpretInfo));
                    }
                    if (type.elementType == INTS) {
                        ops.add(Expression.make(type, "", type2, ".reinterpretAsInts()", reinterpretInfo));
                    }
                    if (type.elementType == LONGS) {
                        ops.add(Expression.make(type, "", type2, ".reinterpretAsLongs()", reinterpretInfo));
                    }
                    if (type.elementType == FLOATS) {
                        ops.add(Expression.make(type, "", type2, ".reinterpretAsFloats()", reinterpretInfo));
                    }
                    if (type.elementType == DOUBLES) {
                        ops.add(Expression.make(type, "", type2, ".reinterpretAsDoubles()", reinterpretInfo));
                    }
                    if (type.elementType.isFloating() && type.elementType.byteSize() == type2.elementType.byteSize()) {
                        ops.add(Expression.make(type, "", type2, ".viewAsFloatingLanes()", reinterpretInfo));
                    }
                    if (!type.elementType.isFloating() && type.elementType.byteSize() == type2.elementType.byteSize()) {
                        ops.add(Expression.make(type, "", type2, ".viewAsIntegralLanes()", reinterpretInfo));
                    }
                }

                // reinterpretShape
                if (type2.byteSize() >= type.byteSize()) {
                    // Output overflows, is truncated (Expansion): part >= 0
                    int partMask = type2.byteSize() / type.byteSize() - 1;
                    ops.add(Expression.make(type,
                                            "((" + type.name() + ")",
                                            type2,
                                            ".reinterpretShape(" + type.speciesName + ", ",
                                            INTS, " & " + partMask + "))", reinterpretInfo));
                } else {
                    // Logical output too small to fill output vector (Contraction): part <= 0
                    int partMask = type.byteSize() / type2.byteSize() - 1;
                    ops.add(Expression.make(type,
                                            "((" + type.name() + ")",
                                            type2,
                                            ".reinterpretShape(" + type.speciesName + ", "
                                            + "-(", INTS, " & " + partMask + ")))", reinterpretInfo));
                }

                // convertShape - Cast/Reinterpret
                ops.add(Expression.make(type,
                                        "((" + type.name() + ")",
                                        type2,
                                        ".convertShape(VectorOperators.Conversion.ofCast("
                                            + type2.elementType.name() +  ".class, "
                                            + type.elementType.name() + ".class), "
                                        + type.speciesName + ", ",
                                        INTS, // part
                                        "))", WITH_OUT_OF_BOUNDS_EXCEPTION));
                ops.add(Expression.make(type,
                                        "((" + type.name() + ")",
                                        type2,
                                        ".convertShape(VectorOperators.Conversion.ofReinterpret("
                                            + type2.elementType.name() +  ".class, "
                                            + type.elementType.name() + ".class), "
                                        + type.speciesName + ", ",
                                        INTS, // part
                                        "))", reinterpretInfo.combineWith(WITH_OUT_OF_BOUNDS_EXCEPTION)));
                // Compute size of logical output, before it is "fit" into the output vector.
                // Each element is cast/reinterpret individually, and so the logical output
                // has the lane count of the input vector, and the element size that of the output element size.
                // Note: reinterpret of float -> long means we expand each element from 4->8 bytes, and so
                // we take the lower 4 bytes from the float and add 4 bytes of zero padding.
                int conversionLogicalByteSize = type2.length * type.elementType.byteSize();
                if (conversionLogicalByteSize >= type.byteSize()) {
                    // Output overflows, is truncated (Expansion): part >= 0
                    int partMask = conversionLogicalByteSize / type.byteSize() - 1;
                    ops.add(Expression.make(type,
                                            "((" + type.name() + ")",
                                            type2,
                                            ".convertShape(VectorOperators.Conversion.ofCast("
                                                + type2.elementType.name() +  ".class, "
                                                + type.elementType.name() + ".class), "
                                            + type.speciesName + ", ",
                                            INTS, " & " + partMask + "))"));
                    ops.add(Expression.make(type,
                                            "((" + type.name() + ")",
                                            type2,
                                            ".convertShape(VectorOperators.Conversion.ofReinterpret("
                                                + type2.elementType.name() +  ".class, "
                                                + type.elementType.name() + ".class), "
                                            + type.speciesName + ", ",
                                            INTS, " & " + partMask + "))", reinterpretInfo));
                } else {
                    // Logical output too small to fill output vector (Contraction): part <= 0
                    int partMask = type.byteSize() / conversionLogicalByteSize - 1;
                    ops.add(Expression.make(type,
                                            "((" + type.name() + ")",
                                            type2,
                                            ".convertShape(VectorOperators.Conversion.ofCast("
                                                + type2.elementType.name() +  ".class, "
                                                + type.elementType.name() + ".class), "
                                            + type.speciesName + ", "
                                            + "-(", INTS, " & " + partMask + ")))"));
                    ops.add(Expression.make(type,
                                            "((" + type.name() + ")",
                                            type2,
                                            ".convertShape(VectorOperators.Conversion.ofReinterpret("
                                                + type2.elementType.name() +  ".class, "
                                                + type.elementType.name() + ".class), "
                                            + type.speciesName + ", "
                                            + "-(", INTS, " & " + partMask + ")))", reinterpretInfo));
                }
                // TODO: convertShape - using VectorOperators.I2S,REINTERPRET_I2F,ZERO_EXTEND_B2I etc.
            }

            ops.add(Expression.make(type, "", type, ".div(", type.elementType, ")", WITH_ARITHMETIC_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".div(", type.elementType, ", ", type.maskType, ")", WITH_ARITHMETIC_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".div(", type, ")", WITH_ARITHMETIC_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".div(", type.elementType, ", ", type.maskType, ")", WITH_ARITHMETIC_EXCEPTION));

            ops.add(Expression.make(type.maskType, "", type, ".eq(", type.elementType, ")"));
            ops.add(Expression.make(type.maskType, "", type, ".eq(", type, ")"));
            // skip equals
            ops.add(Expression.make(type, "", type, ".expand(", type.maskType, ")"));
            // skip fromArray
            // skip fromMemorySegment
            // skip hashCode
            // skip intoArray
            // skip intoMemorySegment
            // TODO: memory accesses. It is not clear yet if these are to be modeled as Expressions, or rather statements.
            ops.add(Expression.make(type.elementType, "", type, ".lane(", INTS, " & " + (type.length-1) + ")"));
            ops.add(Expression.make(type.elementType, "", type, ".lane(", INTS, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));

            for (VOP vop : VECTOR_OPS) {
                var vopInfo = vop.isDeterministic ? new Expression.Info() : WITH_NONDETERMINISTIC_RESULT;
                if (vop.elementTypes().contains(type.elementType)) {
                    switch(vop.type()) {
                    case VOPType.UNARY:
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.maskType, ")", vopInfo));
                        break;
                    case VOPType.ASSOCIATIVE:
                    case VOPType.INTEGRAL_ASSOCIATIVE:
                        if (vop.type() == VOPType.ASSOCIATIVE || !type.elementType.isFloating()) {
                            ops.add(Expression.make(type.elementType, "", type, ".reduceLanes(VectorOperators." + vop.name() + ")", vopInfo));
                            ops.add(Expression.make(type.elementType, "", type, ".reduceLanes(VectorOperators." + vop.name() + ", ", type.maskType, ")", vopInfo));
                        }
                        // fall-through
                    case VOPType.BINARY:
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type.maskType, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", LONGS, ")", vopInfo.combineWith(WITH_ILLEGAL_ARGUMENT_EXCEPTION)));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", LONGS, ", ", type.maskType, ")", vopInfo.combineWith(WITH_ILLEGAL_ARGUMENT_EXCEPTION)));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type.maskType, ")", vopInfo));
                        break;
                    case VOPType.TERNARY:
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type.elementType, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type.elementType, ", ", type.maskType, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type, ", ", type.maskType, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type.elementType, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type.elementType, ", ", type.maskType, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type, ")", vopInfo));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type, ", ", type.maskType, ")", vopInfo));
                        break;
                    }
                }
            }

            ops.add(Expression.make(type.maskType, "", type, ".lt(", type.elementType, ")"));
            ops.add(Expression.make(type.maskType, "", type, ".lt(", type, ")"));

            ops.add(Expression.make(type.maskType, "", type, ".maskAll(", BOOLEANS, ")"));

            ops.add(Expression.make(type, "", type, ".max(", type.elementType, ")"));
            ops.add(Expression.make(type, "", type, ".max(", type, ")"));
            ops.add(Expression.make(type, "", type, ".min(", type.elementType, ")"));
            ops.add(Expression.make(type, "", type, ".min(", type, ")"));

            ops.add(Expression.make(type, "", type, ".mul(", type.elementType, ")"));
            ops.add(Expression.make(type, "", type, ".mul(", type.elementType, ", ", type.maskType, ")"));
            ops.add(Expression.make(type, "", type, ".mul(", type, ")"));
            ops.add(Expression.make(type, "", type, ".mul(", type, ", ", type.maskType, ")"));

            ops.add(Expression.make(type, "", type, ".neg()"));

            ops.add(Expression.make(type, "", type, ".rearrange(", type.shuffleType, ")"));
            ops.add(Expression.make(type, "", type, ".rearrange(", type.shuffleType, ", ", type, ")"));
            ops.add(Expression.make(type, "", type, ".rearrange(", type.shuffleType, ", ", type.maskType, ")"));

            ops.add(Expression.make(type, "", type, ".selectFrom(", type, ")"));
            ops.add(Expression.make(type, "", type, ".selectFrom(", type, ", ", type, ")"));
            ops.add(Expression.make(type, "", type, ".selectFrom(", type, ", ", type.maskType, ")"));

            ops.add(Expression.make(type, "", type, ".slice(", INTS, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".slice(", INTS, ", ", type, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".slice(", INTS, ", ", type, ", ", type.maskType, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));

            ops.add(Expression.make(type, "", type, ".sub(", type.elementType, ")"));
            ops.add(Expression.make(type, "", type, ".sub(", type.elementType, ", ", type.maskType, ")"));
            ops.add(Expression.make(type, "", type, ".sub(", type, ")"));
            ops.add(Expression.make(type, "", type, ".sub(", type, ", ", type.maskType, ")"));


            for (VOP test : VECTOR_TEST) {
                if (test.elementTypes().contains(type.elementType)) {
                    ops.add(Expression.make(type.maskType, "", type, ".test(VectorOperators." + test.name() + ")"));
                    ops.add(Expression.make(type.maskType, "", type, ".test(VectorOperators." + test.name() + ", ", type.maskType, ")"));
                }
            }

            // skip toArray and friends

            ops.add(Expression.make(type, "", type, ".unslice(", INTS, " & " + (type.length-1) + ")"));
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, " & " + (type.length-1) + ", ", type, ", ", INTS, " & 2)"));
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, ", ", type, ", 0)", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, ", ", type, ", ", INTS, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, ", ", type, ", 0, ", type.maskType, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, ", ", type, ", ", INTS, ", ", type.maskType, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));

            ops.add(Expression.make(type, "", type, ".withLane(", INTS, ", ", type.elementType, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));

            if (type.elementType.isFloating()) {
                ops.add(Expression.make(type, "", type, ".fma(", type.elementType, ", ", type.elementType, ")"));
                ops.add(Expression.make(type, "", type, ".fma(", type, ", ", type, ")"));

                // TODO: enforce precision instead of just making it non-deterministic?
                ops.add(Expression.make(type, "", type, ".pow(", type.elementType, ")", WITH_NONDETERMINISTIC_RESULT));
                ops.add(Expression.make(type, "", type, ".pow(", type, ")", WITH_NONDETERMINISTIC_RESULT));
                ops.add(Expression.make(type, "", type, ".sqrt()"));
            }

            ops.add(Expression.make(type.shuffleType, "", type, ".toShuffle()"));
            // skip zero - can get it from type.con() anyway.

            // ----------------- MaskVector --------------------
            // skip fromValues, too many inputs
            // skip fromArray
            ops.add(Expression.make(type.maskType, "VectorMask.fromLong(" + type.speciesName + ", ", LONGS, ")"));
            for (var type2 : CodeGenerationDataNameType.VECTOR_VECTOR_TYPES) {
                var mask = type.shuffleType;
                var mask2 = type2.shuffleType;
                if (type.length == type2.length) {
                    ops.add(Expression.make(mask, "((" + mask.name() + ")", mask2 , ".cast(" + type.speciesName + "))"));
                }
            }
            ops.add(Expression.make(LONGS, "", type.maskType , ".toLong()"));
            // skip toArray
            // skip intoArray
            ops.add(Expression.make(BOOLEANS, "", type.maskType , ".anyTrue()"));
            ops.add(Expression.make(BOOLEANS, "", type.maskType , ".allTrue()"));
            ops.add(Expression.make(INTS, "", type.maskType , ".trueCount()"));
            ops.add(Expression.make(INTS, "", type.maskType , ".firstTrue()"));
            ops.add(Expression.make(INTS, "", type.maskType , ".lastTrue()"));
            ops.add(Expression.make(type.maskType, "", type.maskType , ".and(", type.maskType, ")"));
            ops.add(Expression.make(type.maskType, "", type.maskType , ".or(", type.maskType, ")"));
            ops.add(Expression.make(type.maskType, "", type.maskType , ".xor(", type.maskType, ")"));
            ops.add(Expression.make(type.maskType, "", type.maskType , ".andNot(", type.maskType, ")"));
            ops.add(Expression.make(type.maskType, "", type.maskType , ".eq(", type.maskType, ")"));
            ops.add(Expression.make(type.maskType, "", type.maskType , ".not()"));
            ops.add(Expression.make(type.maskType, "", type.maskType , ".indexInRange(", INTS, ", ", INTS,")"));
            ops.add(Expression.make(type.maskType, "", type.maskType , ".indexInRange(", LONGS, ", ", LONGS,")"));
            ops.add(Expression.make(type, "((" + type.name() + ")", type.maskType , ".toVector())"));
            ops.add(Expression.make(BOOLEANS, "", type.maskType , ".laneIsSet(", INTS, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(BOOLEANS, "", type.maskType , ".laneIsSet(", INTS, " & " + (type.length-1) + ")"));
            // skip check
            // skip toString
            // skip equals
            // skip hashCode
            ops.add(Expression.make(type.maskType, "", type.maskType , ".compress()"));

            // ----------------- ShuffleVector --------------------
            for (var type2 : CodeGenerationDataNameType.VECTOR_VECTOR_TYPES) {
                var shuffle = type.shuffleType;
                var shuffle2 = type2.shuffleType;
                if (type.length == type2.length) {
                    ops.add(Expression.make(shuffle, "((" + shuffle.name() + ")", shuffle2 , ".cast(" + type.speciesName + "))"));
                }
            }
            ops.add(Expression.make(INTS, "", type.shuffleType , ".checkIndex(", INTS, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(INTS, "", type.shuffleType , ".wrapIndex(", INTS, ")"));
            ops.add(Expression.make(type.shuffleType, "", type.shuffleType , ".checkIndexes()", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type.shuffleType, "", type.shuffleType , ".wrapIndexes()"));
            ops.add(Expression.make(type.maskType, "", type.shuffleType , ".laneIsValid()"));
            // skip fromValues, too many inputs
            // skip fromArray
            // skip fromMemorySegment
            // skip fromOp
            // skip iota
            // skip makeZip
            // skip makeUnzip
            // skip toArray
            // skip intoArray
            // skip intoMemorySegment
            ops.add(Expression.make(type, "((" + type.name() + ")", type.shuffleType , ".toVector())"));
            ops.add(Expression.make(INTS, "", type.shuffleType , ".laneSource(", INTS,")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));
            ops.add(Expression.make(INTS, "", type.shuffleType , ".laneSource(", INTS," & " + (type.length-1) + ")"));
            ops.add(Expression.make(type.shuffleType, "", type.shuffleType, ".rearrange(", type.shuffleType, ")"));
            // skip toString
            // skip equals
            // skip hashCode
        }

        // Make sure the list is not modifiable.
        return List.copyOf(ops);
    }

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

    public static final List<Expression> VECTOR_OPERATIONS = generateVectorOperations();

    public static final List<Expression> ALL_OPERATIONS = concat(
        SCALAR_NUMERIC_OPERATIONS,
        VECTOR_OPERATIONS
    );
}
