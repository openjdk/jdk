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

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import static compiler.lib.template_framework.library.PrimitiveType.BYTES;
import static compiler.lib.template_framework.library.PrimitiveType.SHORTS;
import static compiler.lib.template_framework.library.PrimitiveType.CHARS;
import static compiler.lib.template_framework.library.PrimitiveType.INTS;
import static compiler.lib.template_framework.library.PrimitiveType.LONGS;
import static compiler.lib.template_framework.library.PrimitiveType.FLOATS;
import static compiler.lib.template_framework.library.PrimitiveType.DOUBLES;
import static compiler.lib.template_framework.library.PrimitiveType.BOOLEANS;

/**
 * TODO: desc
 */
public final class Operations {

    public static final List<Expression> PRIMITIVE_OPERATIONS = generatePrimitiveOperations();

    private static List<Expression> generatePrimitiveOperations() {
        List<Expression> ops = new ArrayList<>();

        Expression.Info withArithmeticException = new Expression.Info().withExceptions(Set.of("ArithmeticException"));
        Expression.Info withNondeterministicResult = new Expression.Info().withNondeterministicResult();

        // ------------ byte -------------
        ops.add(Expression.make(BYTES, "(byte)(", BYTES,    ")"));
        ops.add(Expression.make(BYTES, "(byte)(", SHORTS,   ")"));
        ops.add(Expression.make(BYTES, "(byte)(", CHARS,    ")"));
        ops.add(Expression.make(BYTES, "(byte)(", INTS,     ")"));
        ops.add(Expression.make(BYTES, "(byte)(", LONGS,    ")"));
        ops.add(Expression.make(BYTES, "(byte)(", FLOATS,   ")"));
        ops.add(Expression.make(BYTES, "(byte)(", DOUBLES,  ")"));
        // There is no cast from boolean.

        ops.add(Expression.make(BYTES, "(", BOOLEANS, "?", BYTES, ":", BYTES, ")"));

        // Arithmetic operations are not performned in byte, but rather promoted to int.

        // ------------ Byte -------------
        ops.add(Expression.make(INTS, "Byte.compare(", BYTES, ", ", BYTES, ")"));
        ops.add(Expression.make(INTS, "Byte.compareUnsigned(", BYTES, ", ", BYTES, ")"));
        ops.add(Expression.make(INTS, "Byte.toUnsignedInt(", BYTES, ")"));
        ops.add(Expression.make(LONGS, "Byte.toUnsignedLong(", BYTES, ")"));

        // ------------ char -------------
        ops.add(Expression.make(CHARS, "(char)(", BYTES,    ")"));
        ops.add(Expression.make(CHARS, "(char)(", SHORTS,   ")"));
        ops.add(Expression.make(CHARS, "(char)(", CHARS,    ")"));
        ops.add(Expression.make(CHARS, "(char)(", INTS,     ")"));
        ops.add(Expression.make(CHARS, "(char)(", LONGS,    ")"));
        ops.add(Expression.make(CHARS, "(char)(", FLOATS,   ")"));
        ops.add(Expression.make(CHARS, "(char)(", DOUBLES,  ")"));
        // There is no cast from boolean.

        ops.add(Expression.make(CHARS, "(", BOOLEANS, "?", CHARS, ":", CHARS, ")"));

        // Arithmetic operations are not performned in char, but rather promoted to int.

        // ------------ Character -------------
        ops.add(Expression.make(INTS, "Character.compare(", CHARS, ", ", CHARS, ")"));
        ops.add(Expression.make(CHARS, "Character.reverseBytes(", CHARS, ")"));

        // ------------ short -------------
        ops.add(Expression.make(SHORTS, "(short)(", BYTES,    ")"));
        ops.add(Expression.make(SHORTS, "(short)(", SHORTS,   ")"));
        ops.add(Expression.make(SHORTS, "(short)(", CHARS,    ")"));
        ops.add(Expression.make(SHORTS, "(short)(", INTS,     ")"));
        ops.add(Expression.make(SHORTS, "(short)(", LONGS,    ")"));
        ops.add(Expression.make(SHORTS, "(short)(", FLOATS,   ")"));
        ops.add(Expression.make(SHORTS, "(short)(", DOUBLES,  ")"));
        // There is no cast from boolean.

        ops.add(Expression.make(SHORTS, "(", BOOLEANS, "?", SHORTS, ":", SHORTS, ")"));

        // Arithmetic operations are not performned in short, but rather promoted to int.

        // ------------ Short -------------
        ops.add(Expression.make(INTS, "Short.compare(", SHORTS, ", ", SHORTS, ")"));
        ops.add(Expression.make(INTS, "Short.compareUnsigned(", SHORTS, ", ", SHORTS, ")"));
        ops.add(Expression.make(SHORTS, "Short.reverseBytes(", SHORTS, ")"));
        ops.add(Expression.make(INTS, "Short.toUnsignedInt(", SHORTS, ")"));
        ops.add(Expression.make(LONGS, "Short.toUnsignedLong(", SHORTS, ")"));

        // ------------ int -------------
        ops.add(Expression.make(INTS, "(int)(", BYTES,    ")"));
        ops.add(Expression.make(INTS, "(int)(", SHORTS,   ")"));
        ops.add(Expression.make(INTS, "(int)(", CHARS,    ")"));
        ops.add(Expression.make(INTS, "(int)(", INTS,     ")"));
        ops.add(Expression.make(INTS, "(int)(", LONGS,    ")"));
        ops.add(Expression.make(INTS, "(int)(", FLOATS,   ")"));
        ops.add(Expression.make(INTS, "(int)(", DOUBLES,  ")"));
        // There is no cast from boolean.

        ops.add(Expression.make(INTS, "(", BOOLEANS, "?", INTS, ":", INTS, ")"));

        // Arithmetic operators
        ops.add(Expression.make(INTS, "(-(", INTS, "))"));
        ops.add(Expression.make(INTS, "(~(", INTS, "))"));
        ops.add(Expression.make(INTS, "(", INTS, " + ",   INTS, ")"));
        ops.add(Expression.make(INTS, "(", INTS, " - ",   INTS, ")"));
        ops.add(Expression.make(INTS, "(", INTS, " * ",   INTS, ")"));
        ops.add(Expression.make(INTS, "(", INTS, " / ",   INTS, ")", withArithmeticException));
        ops.add(Expression.make(INTS, "(", INTS, " % ",   INTS, ")", withArithmeticException));
        ops.add(Expression.make(INTS, "(", INTS, " & ",   INTS, ")"));
        ops.add(Expression.make(INTS, "(", INTS, " | ",   INTS, ")"));
        ops.add(Expression.make(INTS, "(", INTS, " ^ ",   INTS, ")"));
        ops.add(Expression.make(INTS, "(", INTS, " << ",  INTS, ")"));
        ops.add(Expression.make(INTS, "(", INTS, " >> ",  INTS, ")"));
        ops.add(Expression.make(INTS, "(", INTS, " >>> ", INTS, ")"));

        // ------------ Integer -------------
        ops.add(Expression.make(INTS, "Integer.bitCount(", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.compare(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.compareUnsigned(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.compress(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.divideUnsigned(", INTS, ", ", INTS, ")", withArithmeticException));
        ops.add(Expression.make(INTS, "Integer.expand(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.highestOneBit(", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.lowestOneBit(", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.max(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.min(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.numberOfLeadingZeros(", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.numberOfTrailingZeros(", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.remainderUnsigned(", INTS, ", ", INTS, ")", withArithmeticException));
        ops.add(Expression.make(INTS, "Integer.reverse(", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.reverseBytes(", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.rotateLeft(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.rotateRight(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.signum(", INTS, ")"));
        ops.add(Expression.make(INTS, "Integer.sum(", INTS, ", ", INTS, ")"));
        ops.add(Expression.make(LONGS, "Integer.toUnsignedLong(", INTS, ")"));

        // ------------ long -------------
        ops.add(Expression.make(LONGS, "(long)(", BYTES,    ")"));
        ops.add(Expression.make(LONGS, "(long)(", SHORTS,   ")"));
        ops.add(Expression.make(LONGS, "(long)(", CHARS,    ")"));
        ops.add(Expression.make(LONGS, "(long)(", INTS,     ")"));
        ops.add(Expression.make(LONGS, "(long)(", LONGS,    ")"));
        ops.add(Expression.make(LONGS, "(long)(", FLOATS,   ")"));
        ops.add(Expression.make(LONGS, "(long)(", DOUBLES,  ")"));
        // There is no cast from boolean.

        ops.add(Expression.make(LONGS, "(", BOOLEANS, "?", LONGS, ":", LONGS, ")"));

        // Arithmetic operators
        ops.add(Expression.make(LONGS, "(-(", LONGS, "))"));
        ops.add(Expression.make(LONGS, "(~(", LONGS, "))"));
        ops.add(Expression.make(LONGS, "(", LONGS, " + ",   LONGS, ")"));
        ops.add(Expression.make(LONGS, "(", LONGS, " - ",   LONGS, ")"));
        ops.add(Expression.make(LONGS, "(", LONGS, " * ",   LONGS, ")"));
        ops.add(Expression.make(LONGS, "(", LONGS, " / ",   LONGS, ")", withArithmeticException));
        ops.add(Expression.make(LONGS, "(", LONGS, " % ",   LONGS, ")", withArithmeticException));
        ops.add(Expression.make(LONGS, "(", LONGS, " & ",   LONGS, ")"));
        ops.add(Expression.make(LONGS, "(", LONGS, " | ",   LONGS, ")"));
        ops.add(Expression.make(LONGS, "(", LONGS, " ^ ",   LONGS, ")"));
        ops.add(Expression.make(LONGS, "(", LONGS, " << ",  LONGS, ")"));
        ops.add(Expression.make(LONGS, "(", LONGS, " >> ",  LONGS, ")"));
        ops.add(Expression.make(LONGS, "(", LONGS, " >>> ", LONGS, ")"));

        // ------------ Long -------------
        ops.add(Expression.make(INTS, "Long.bitCount(", LONGS, ")"));
        ops.add(Expression.make(INTS, "Long.compare(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(INTS, "Long.compareUnsigned(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.compress(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.divideUnsigned(", LONGS, ", ", LONGS, ")", withArithmeticException));
        ops.add(Expression.make(LONGS, "Long.expand(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.highestOneBit(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.lowestOneBit(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.max(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.min(", LONGS, ", ", LONGS, ")"));
        ops.add(Expression.make(INTS, "Long.numberOfLeadingZeros(", LONGS, ")"));
        ops.add(Expression.make(INTS, "Long.numberOfTrailingZeros(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.remainderUnsigned(", LONGS, ", ", LONGS, ")", withArithmeticException));
        ops.add(Expression.make(LONGS, "Long.reverse(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.reverseBytes(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.rotateLeft(", LONGS, ", ", INTS, ")"));
        ops.add(Expression.make(LONGS, "Long.rotateRight(", LONGS, ", ", INTS, ")"));
        ops.add(Expression.make(INTS, "Long.signum(", LONGS, ")"));
        ops.add(Expression.make(LONGS, "Long.sum(", LONGS, ", ", LONGS, ")"));

        // ------------ float -------------
        ops.add(Expression.make(FLOATS, "(float)(", BYTES,    ")"));
        ops.add(Expression.make(FLOATS, "(float)(", SHORTS,   ")"));
        ops.add(Expression.make(FLOATS, "(float)(", CHARS,    ")"));
        ops.add(Expression.make(FLOATS, "(float)(", INTS,     ")"));
        ops.add(Expression.make(FLOATS, "(float)(", LONGS,    ")"));
        ops.add(Expression.make(FLOATS, "(float)(", FLOATS,   ")"));
        ops.add(Expression.make(FLOATS, "(float)(", DOUBLES,  ")"));
        // There is no cast from boolean.

        ops.add(Expression.make(FLOATS, "(", BOOLEANS, "?", FLOATS, ":", FLOATS, ")"));

        // Arithmetic operators
        ops.add(Expression.make(FLOATS, "(-(", FLOATS, "))"));
        ops.add(Expression.make(FLOATS, "(", FLOATS, " + ",   FLOATS, ")"));
        ops.add(Expression.make(FLOATS, "(", FLOATS, " - ",   FLOATS, ")"));
        ops.add(Expression.make(FLOATS, "(", FLOATS, " * ",   FLOATS, ")"));
        ops.add(Expression.make(FLOATS, "(", FLOATS, " / ",   FLOATS, ")"));
        ops.add(Expression.make(FLOATS, "(", FLOATS, " % ",   FLOATS, ")"));

        // ------------ Float -------------
        ops.add(Expression.make(INTS, "Float.compare(", FLOATS, ", ", FLOATS, ")"));
        ops.add(Expression.make(INTS, "Float.floatToIntBits(", FLOATS, ")"));
        ops.add(Expression.make(INTS, "Float.floatToRawIntBits(", FLOATS, ")", withNondeterministicResult));
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
        ops.add(Expression.make(DOUBLES, "(double)(", BYTES,    ")"));
        ops.add(Expression.make(DOUBLES, "(double)(", SHORTS,   ")"));
        ops.add(Expression.make(DOUBLES, "(double)(", CHARS,    ")"));
        ops.add(Expression.make(DOUBLES, "(double)(", INTS,     ")"));
        ops.add(Expression.make(DOUBLES, "(double)(", LONGS,    ")"));
        ops.add(Expression.make(DOUBLES, "(double)(", FLOATS,   ")"));
        ops.add(Expression.make(DOUBLES, "(double)(", DOUBLES,  ")"));
        // There is no cast from boolean.

        ops.add(Expression.make(DOUBLES, "(", BOOLEANS, "?", DOUBLES, ":", DOUBLES, ")"));

        // Arithmetic operators
        ops.add(Expression.make(DOUBLES, "(-(", DOUBLES, "))"));
        ops.add(Expression.make(DOUBLES, "(", DOUBLES, " + ",   DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES, "(", DOUBLES, " - ",   DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES, "(", DOUBLES, " * ",   DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES, "(", DOUBLES, " / ",   DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES, "(", DOUBLES, " % ",   DOUBLES, ")"));

        // ------------ Double -------------
        ops.add(Expression.make(INTS, "Double.compare(", DOUBLES, ", ", DOUBLES, ")"));
        ops.add(Expression.make(LONGS, "Double.doubleToLongBits(", DOUBLES, ")"));
        ops.add(Expression.make(LONGS, "Double.doubleToRawLongBits(", DOUBLES, ")", withNondeterministicResult));
	// Note: there are multiple NaN values with different bit representations.
        ops.add(Expression.make(DOUBLES, "Double.longBitsToDouble(", LONGS, ")"));
        ops.add(Expression.make(BOOLEANS, "Double.isFinite(", DOUBLES, ")"));
        ops.add(Expression.make(BOOLEANS, "Double.isInfinite(", DOUBLES, ")"));
        ops.add(Expression.make(BOOLEANS, "Double.isNaN(", DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES, "Double.max(", DOUBLES, ", ", DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES, "Double.min(", DOUBLES, ", ", DOUBLES, ")"));
        ops.add(Expression.make(DOUBLES, "Double.sum(", DOUBLES, ", ", DOUBLES, ")"));

        // TODO: Boolean.

        return ops;
    }
}
