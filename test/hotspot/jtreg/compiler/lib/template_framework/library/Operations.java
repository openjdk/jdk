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
        ops.add(Expression.make(INTS, "Float.floatToRawIntBits(", FLOATS, ")", WITH_NONDETERMINISTIC_RESULT));
        // Note: there are multiple NaN values with different bit representations.
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
        ops.add(Expression.make(SHORTS, "Float16.float16ToRawShortBits(", FLOAT16, ")"));
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
    private record VOP(String name, VOPType type, List<PrimitiveType> elementTypes) {}

    // TODO: consider some floating results as inexact, and handle it accordingly?
    private static final List<VOP> VECTOR_OPS = List.of(
        new VOP("ABS",                  VOPType.UNARY, PRIMITIVE_TYPES),
        //new VOP("ACOS",                 VOPType.UNARY, FLOATING_TYPES),
        new VOP("ADD",                  VOPType.INTEGRAL_ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("AND",                  VOPType.ASSOCIATIVE, INTEGRAL_TYPES),
        new VOP("AND_NOT",              VOPType.BINARY, INTEGRAL_TYPES),
        new VOP("ASHR",                 VOPType.BINARY, INTEGRAL_TYPES),
        //new VOP("ASIN",                 VOPType.UNARY, FLOATING_TYPES),
        //new VOP("ATAN",                 VOPType.UNARY, FLOATING_TYPES),
        //new VOP("ATAN2",                VOPType.BINARY, FLOATING_TYPES),
        new VOP("BIT_COUNT",            VOPType.UNARY, INTEGRAL_TYPES),
        new VOP("BITWISE_BLEND",        VOPType.TERNARY, INTEGRAL_TYPES),
        //new VOP("CBRT",                 VOPType.UNARY, FLOATING_TYPES),
        new VOP("COMPRESS_BITS",        VOPType.BINARY, INT_LONG_TYPES),
        //new VOP("COS",                  VOPType.UNARY, FLOATING_TYPES),
        //new VOP("COSH",                 VOPType.UNARY, FLOATING_TYPES),
        new VOP("DIV",                  VOPType.BINARY, FLOATING_TYPES),
        //new VOP("EXP",                  VOPType.UNARY, FLOATING_TYPES),
        new VOP("EXPAND_BITS",          VOPType.BINARY, INT_LONG_TYPES),
        //new VOP("EXPM1",                VOPType.UNARY, FLOATING_TYPES),
        new VOP("FIRST_NONZERO",        VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("FMA",                  VOPType.TERNARY, FLOATING_TYPES),
        //new VOP("HYPOT",                VOPType.BINARY, FLOATING_TYPES),
        new VOP("LEADING_ZEROS_COUNT",  VOPType.UNARY, INTEGRAL_TYPES),
        //new VOP("LOG",                  VOPType.UNARY, FLOATING_TYPES),
        //new VOP("LOG10",                VOPType.UNARY, FLOATING_TYPES),
        //new VOP("LOG1P",                VOPType.UNARY, FLOATING_TYPES),
        new VOP("LSHL",                 VOPType.BINARY, INTEGRAL_TYPES),
        new VOP("LSHR",                 VOPType.BINARY, INTEGRAL_TYPES),
        new VOP("MIN",                  VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("MAX",                  VOPType.ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("MUL",                  VOPType.INTEGRAL_ASSOCIATIVE, PRIMITIVE_TYPES),
        new VOP("NEG",                  VOPType.UNARY, PRIMITIVE_TYPES),
        new VOP("NOT",                  VOPType.UNARY, INTEGRAL_TYPES),
        new VOP("OR",                   VOPType.ASSOCIATIVE, INTEGRAL_TYPES),
        //new VOP("POW",                  VOPType.BINARY, FLOATING_TYPES),
        new VOP("REVERSE",              VOPType.UNARY, INTEGRAL_TYPES),
        new VOP("REVERSE_BYTES",        VOPType.UNARY, INTEGRAL_TYPES),
        new VOP("ROL",                  VOPType.BINARY, INTEGRAL_TYPES),
        new VOP("ROR",                  VOPType.BINARY, INTEGRAL_TYPES),
        new VOP("SADD",                 VOPType.BINARY, INTEGRAL_TYPES),
        //new VOP("SIN",                  VOPType.UNARY, FLOATING_TYPES),
        //new VOP("SINH",                 VOPType.UNARY, FLOATING_TYPES),
        //new VOP("SQRT",                 VOPType.UNARY, FLOATING_TYPES),
        new VOP("SSUB",                 VOPType.BINARY, INTEGRAL_TYPES),
        new VOP("SUADD",                VOPType.BINARY, INTEGRAL_TYPES),
        new VOP("SUB",                  VOPType.BINARY, PRIMITIVE_TYPES),
        new VOP("SUSUB",                VOPType.BINARY, INTEGRAL_TYPES),
        //new VOP("TAN",                  VOPType.UNARY, FLOATING_TYPES),
        //new VOP("TANH",                 VOPType.UNARY, FLOATING_TYPES),
        new VOP("TRAILING_ZEROS_COUNT", VOPType.UNARY, INTEGRAL_TYPES),
        new VOP("UMAX",                 VOPType.ASSOCIATIVE, INTEGRAL_TYPES),
        new VOP("UMIN",                 VOPType.ASSOCIATIVE, INTEGRAL_TYPES),
        new VOP("XOR",                  VOPType.ASSOCIATIVE, INTEGRAL_TYPES),
        new VOP("ZOMO",                 VOPType.UNARY, INTEGRAL_TYPES)
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

    private static List<Expression> generateVectorOperations() {
        List<Expression> ops = new ArrayList<>();

        for (var type : CodeGenerationDataNameType.VECTOR_VECTOR_TYPES) {
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

            // Note: check works on class / species, leaving them out.

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
                    // Reinterpretation FROM floating is not safe, because of different NaN encodings.
                    if (!type2.elementType.isFloating()) {
                        ops.add(Expression.make(type,
                                                    "((" + type.name() + ")",
                                                    type2,
                                                    ".convert(VectorOperators.Conversion.ofReinterpret("
                                                        + type2.elementType.name() +  ".class, "
                                                        + type.elementType.name() + ".class), 0))"));
                        ops.add(Expression.make(type,
                                                    "((" + type.name() + ")",
                                                    type2,
                                                    ".convert(VectorOperators.Conversion.ofReinterpret("
                                                        + type2.elementType.name() +  ".class, "
                                                        + type.elementType.name() + ".class),",
                                                    INTS, // part
                                                    "))", WITH_OUT_OF_BOUNDS_EXCEPTION));
                        if (type.elementType == BYTES) {
                            ops.add(Expression.make(type, "", type2, ".reinterpretAsBytes()"));
                        }
                        if (type.elementType == SHORTS) {
                            ops.add(Expression.make(type, "", type2, ".reinterpretAsShorts()"));
                        }
                        if (type.elementType == INTS) {
                            ops.add(Expression.make(type, "", type2, ".reinterpretAsInts()"));
                        }
                        if (type.elementType == LONGS) {
                            ops.add(Expression.make(type, "", type2, ".reinterpretAsLongs()"));
                        }
                        if (type.elementType == FLOATS) {
                            ops.add(Expression.make(type, "", type2, ".reinterpretAsFloats()"));
                        }
                        if (type.elementType == DOUBLES) {
                            ops.add(Expression.make(type, "", type2, ".reinterpretAsDoubles()"));
                        }
                        if (type.elementType.isFloating() && type.elementType.byteSize() == type2.elementType.byteSize()) {
                            ops.add(Expression.make(type, "", type2, ".viewAsFloatingLanes()"));
                        }
                        if (!type.elementType.isFloating() && type.elementType.byteSize() == type2.elementType.byteSize()) {
                            ops.add(Expression.make(type, "", type2, ".viewAsIntegralLanes()"));
                        }
                    }
                }
                // TODO: convertShape
                // TODO: reinterpretShape
            }

            ops.add(Expression.make(type, "", type, ".div(", type.elementType, ")", WITH_ARITHMETIC_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".div(", type.elementType, ", ", type.maskType, ")", WITH_ARITHMETIC_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".div(", type, ")", WITH_ARITHMETIC_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".div(", type.elementType, ", ", type.maskType, ")", WITH_ARITHMETIC_EXCEPTION));

            ops.add(Expression.make(type.maskType, "", type, ".eq(", type.elementType, ")"));
            ops.add(Expression.make(type.maskType, "", type, ".eq(", type, ")"));

            ops.add(Expression.make(type, "", type, ".expand(", type.maskType, ")"));

            // TODO: ensure we use all variants of fromArray and fromMemorySegment, plus intoArray and intoMemorySegment. Also: toArray and type variants.

            ops.add(Expression.make(type.elementType, "", type, ".lane(", INTS, " & " + (type.length-1) + ")"));
            ops.add(Expression.make(type.elementType, "", type, ".lane(", INTS, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));

            for (VOP vop : VECTOR_OPS) {
                if (vop.elementTypes().contains(type.elementType)) {
                    switch(vop.type()) {
                    case VOPType.UNARY:
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.maskType, ")"));
                        break;
                    case VOPType.ASSOCIATIVE:
                    case VOPType.INTEGRAL_ASSOCIATIVE:
                        if (vop.type() == VOPType.ASSOCIATIVE || !type.elementType.isFloating()) {
                            ops.add(Expression.make(type.elementType, "", type, ".reduceLanes(VectorOperators." + vop.name() + ")"));
                            ops.add(Expression.make(type.elementType, "", type, ".reduceLanes(VectorOperators." + vop.name() + ", ", type.maskType, ")"));
                        }
                        // fall-through
                    case VOPType.BINARY:
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type.maskType, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", LONGS, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", LONGS, ", ", type.maskType, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type.maskType, ")"));
                        break;
                    case VOPType.TERNARY:
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type.elementType, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type.elementType, ", ", type.maskType, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type.elementType, ", ", type, ", ", type.maskType, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type.elementType, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type.elementType, ", ", type.maskType, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type, ")"));
                        ops.add(Expression.make(type, "", type, ".lanewise(VectorOperators." + vop.name() + ", ", type, ", ", type, ", ", type.maskType, ")"));
                        break;
                    }
                }
            }

            ops.add(Expression.make(type.maskType, "", type, ".lt(", type.elementType, ")"));
            ops.add(Expression.make(type.maskType, "", type, ".lt(", type, ")"));

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

            // TODO: non-zero part
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, ", ", type, ", 0)", WITH_OUT_OF_BOUNDS_EXCEPTION));
            ops.add(Expression.make(type, "", type, ".unslice(", INTS, ", ", type, ", 0, ", type.maskType, ")", WITH_OUT_OF_BOUNDS_EXCEPTION));

            ops.add(Expression.make(type, "", type, ".withLane(", INTS, ", ", type.elementType, ")", WITH_ILLEGAL_ARGUMENT_EXCEPTION));

            if (type.elementType.isFloating()) {
                ops.add(Expression.make(type, "", type, ".fma(", type.elementType, ", ", type.elementType, ")"));
                ops.add(Expression.make(type, "", type, ".fma(", type, ", ", type, ")"));

                // TODO: precision?
                // ops.add(Expression.make(type, "", type, ".pow(", type.elementType, ")"));
                // ops.add(Expression.make(type, "", type, ".pow(", type, ")"));
                // ops.add(Expression.make(type, "", type, ".sqrt(", type, ")"));
            }

            ops.add(Expression.make(type.shuffleType, "", type, ".toShuffle()"));

            // TODO: rest of the ops from ShuffleVector.
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
