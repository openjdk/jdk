/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package java.lang;

import java.lang.StackWalker.Option;
import java.util.EnumSet;
import java.util.Set;

import static java.lang.StackWalker.ExtendedOption.*;

final class LiveStackFrameInfo extends StackFrameInfo implements LiveStackFrame {
    private static Object[] EMPTY_ARRAY = new Object[0];

    LiveStackFrameInfo(StackWalker walker) {
        super(walker);
    }

    // These fields are initialized by the VM if ExtendedOption.LOCALS_AND_OPERANDS is set
    private Object[] monitors = EMPTY_ARRAY;
    private Object[] locals = EMPTY_ARRAY;
    private Object[] operands = EMPTY_ARRAY;

    @Override
    public Object[] getMonitors() {
        return monitors;
    }

    @Override
    public Object[] getLocals() {
        return locals;
    }

    @Override
    public Object[] getStack() {
        return operands;
    }

    /*
     * Convert primitive value to {@code Primitive} object to represent
     * a local variable or an element on the operand stack of primitive type.
     */
    static PrimitiveValue asPrimitive(boolean value) {
        return new BooleanPrimitive(value);
    }

    static PrimitiveValue asPrimitive(int value) {
        return new IntPrimitive(value);
    }

    static PrimitiveValue asPrimitive(short value) {
        return new ShortPrimitive(value);
    }

    static PrimitiveValue asPrimitive(char value) {
        return new CharPrimitive(value);
    }

    static PrimitiveValue asPrimitive(byte value) {
        return new BytePrimitive(value);
    }

    static PrimitiveValue asPrimitive(long value) {
        return new LongPrimitive(value);
    }

    static PrimitiveValue asPrimitive(float value) {
        return new FloatPrimitive(value);
    }

    static PrimitiveValue asPrimitive(double value) {
        return new DoublePrimitive(value);
    }

    private static class IntPrimitive extends PrimitiveValue {
        final int value;
        IntPrimitive(int value) {
            this.value = value;
        }

        @Override
        public char type() {
            return 'I';
        }

        @Override
        public int intValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static class ShortPrimitive extends PrimitiveValue {
        final short value;
        ShortPrimitive(short value) {
            this.value = value;
        }

        @Override
        public char type() {
            return 'S';
        }

        @Override
        public short shortValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static class BooleanPrimitive extends PrimitiveValue {
        final boolean value;
        BooleanPrimitive(boolean value) {
            this.value = value;
        }

        @Override
        public char type() {
            return 'Z';
        }

        @Override
        public boolean booleanValue()  {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static class CharPrimitive extends PrimitiveValue {
        final char value;
        CharPrimitive(char value) {
            this.value = value;
        }

        @Override
        public char type() {
            return 'C';
        }

        @Override
        public char charValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static class BytePrimitive extends PrimitiveValue {
        final byte value;
        BytePrimitive(byte value) {
            this.value = value;
        }

        @Override
        public char type() {
            return 'B';
        }

        @Override
        public byte byteValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static class LongPrimitive extends PrimitiveValue {
        final long value;
        LongPrimitive(long value) {
            this.value = value;
        }

        @Override
        public char type() {
            return 'J';
        }

        @Override
        public long longValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static class FloatPrimitive extends PrimitiveValue {
        final float value;
        FloatPrimitive(float value) {
            this.value = value;
        }

        @Override
        public char type() {
            return 'F';
        }

        @Override
        public float floatValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static class DoublePrimitive extends PrimitiveValue {
        final double value;
        DoublePrimitive(double value) {
            this.value = value;
        }

        @Override
        public char type() {
            return 'D';
        }

        @Override
        public double doubleValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}
