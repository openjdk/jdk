/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen.types;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.JSType;

/**
 * Represents the value range of a symbol.
 */
public abstract class Range {

    private static final Range GENERIC_RANGE = new Range() {
        @Override
        public Type getType() {
            return Type.OBJECT;
        }
    };

    private static final Range NUMBER_RANGE = new Range() {
        @Override
        public Type getType() {
            return Type.NUMBER;
        }
    };

    private static final Range UNKNOWN_RANGE = new Range() {
        @Override
        public Type getType() {
            return Type.UNKNOWN;
        }

        @Override
        public boolean isUnknown() {
            return true;
        }
    };

    private static class IntegerRange extends Range {
        private final long min;
        private final long max;
        private final Type type;

        private IntegerRange(final long min, final long max) {
            assert min <= max;
            this.min  = min;
            this.max  = max;
            this.type = typeFromRange(min, max);
        }

        private static Type typeFromRange(final long from, final long to) {
            if (from >= Integer.MIN_VALUE && to <= Integer.MAX_VALUE) {
                return Type.INT;
            }
            return Type.LONG;
        }

        @Override
        public Type getType() {
            return type;
        }

        public long getMin() {
            return min;
        }

        public long getMax() {
            return max;
        }

        @Override
        public boolean isIntegerConst() {
            return getMin() == getMax();
        }

        private long getBitMask() {
            if (min == max) {
                return min;
            }

            if (min < 0) {
                return ~0L;
            }

            long mask = 1;
            while (mask < max) {
                mask = (mask << 1) | 1;
            }
            return mask;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof IntegerRange) {
                final IntegerRange other = (IntegerRange)obj;
                return this.type == other.type && this.min == other.min && this.max == other.max;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(min) ^ Long.hashCode(max);
        }

        @Override
        public String toString() {
            return super.toString() + "[" + min +", " + max + "]";
        }
    }

    /**
     * Get narrowest type for this range
     * @return type
     */
    public abstract Type getType();

    /**
     * Is this range unknown
     * @return true if unknown
     */
    public boolean isUnknown() {
        return false;
    }

    /**
     * Check if an integer is enough to span this range
     * @return true if integer is enough
     */
    public boolean isIntegerType() {
        return this instanceof IntegerRange;
    }

    /**
     * Check if an integer is enough to span this range
     * @return true if integer is enough
     */
    public boolean isIntegerConst() {
        return false;
    }

    /**
     * Create an unknown range - this is most likely a singleton object
     * and it represents "we have no known range information"
     * @return the range
     */
    public static Range createUnknownRange() {
        return UNKNOWN_RANGE;
    }

    /**
     * Create a constant range: [value, value]
     * @param value value
     * @return the range
     */
    public static Range createRange(final int value) {
        return createIntegerRange(value, value);
    }

    /**
     * Create a constant range: [value, value]
     * @param value value
     * @return the range
     */
    public static Range createRange(final long value) {
        return createIntegerRange(value, value);
    }

    /**
     * Create a constant range: [value, value]
     * @param value value
     * @return the range
     */
    public static Range createRange(final double value) {
        if (isRepresentableAsLong(value)) {
            return createIntegerRange((long) value, (long) value);
        }
        return createNumberRange();
    }

    /**
     * Create a constant range: [value, value]
     * @param value value
     * @return the range
     */
    public static Range createRange(final Object value) {
        if (value instanceof Integer) {
            return createRange((int)value);
        } else if (value instanceof Long) {
            return createRange((long)value);
        } else if (value instanceof Double) {
            return createRange((double)value);
        }

        return createGenericRange();
    }

    /**
     * Create a generic range - object symbol that carries no range
     * information
     * @return the range
     */
    public static Range createGenericRange() {
        return GENERIC_RANGE;
    }

    /**
     * Create a number range - number symbol that carries no range
     * information
     * @return the range
     */
    public static Range createNumberRange() {
        return NUMBER_RANGE;
    }

    /**
     * Create an integer range [min, max]
     * @param min minimum value, inclusive
     * @param max maximum value, inclusive
     * @return the range
     */
    public static IntegerRange createIntegerRange(final long min, final long max) {
        return new IntegerRange(min, max);
    }

    /**
     * Create an integer range of maximum type width for the given type
     * @param type the type
     * @return the range
     */
    public static IntegerRange createIntegerRange(final Type type) {
        assert type.isNumeric() && !type.isNumber();
        final long min;
        final long max;
        if (type.isInteger()) {
            min = Integer.MIN_VALUE;
            max = Integer.MAX_VALUE;
        } else if (type.isLong()) {
            min = Long.MIN_VALUE;
            max = Long.MAX_VALUE;
        } else {
            throw new AssertionError(); //type incompatible with integer range
        }
        return new IntegerRange(min, max);
    }

    /**
     * Create an range of maximum type width for the given type
     * @param type the type
     * @return the range
     */
    public static Range createTypeRange(final Type type) {
        if (type.isNumber()) {
            return createNumberRange();
        } else if (type.isNumeric()) {
            return createIntegerRange(type);
        } else {
            return createGenericRange();
        }
    }

    // check that add doesn't overflow
    private static boolean checkAdd(final long a, final long b) {
        final long result = a + b;
        return ((a ^ result) & (b ^ result)) >= 0;
    }

    // check that sub doesn't overflow
    private static boolean checkSub(final long a, final long b) {
        final long result = a - b;
        return ((a ^ result) & (b ^ result)) >= 0;
    }

    private static boolean checkMul(final long a, final long b) {
        // TODO correct overflow check
        return a >= Integer.MIN_VALUE && a <= Integer.MAX_VALUE && b >= Integer.MIN_VALUE && b <= Integer.MAX_VALUE;
    }

    /**
     * The range functionality class responsible for merging ranges and drawing
     * range conclusions from operations executed
     */
    public static class Functionality {
        /** logger */
        protected final DebugLogger log;

        /**
         * Constructor
         * @param log logger
         */
        public Functionality(final DebugLogger log) {
            this.log = log;
        }

        /**
         * Join two ranges
         * @param a first range
         * @param b second range
         * @return the joined range
         */
        public Range join(final Range a, final Range b) {
            if (a.equals(b)) {
                return a;
            }

            Type joinedType = a.getType();
            if (a.getType() != b.getType()) {
                if (a.isUnknown()) {
                    return b;
                }
                if (b.isUnknown()) {
                    return a;
                }

                joinedType = Type.widest(a.getType(), b.getType());
            }

            if (joinedType.isInteger() || joinedType.isLong()) {
                return createIntegerRange(
                        Math.min(((IntegerRange) a).getMin(), ((IntegerRange) b).getMin()),
                        Math.max(((IntegerRange) a).getMax(), ((IntegerRange) b).getMax()));
            }

            return createTypeRange(joinedType);
        }

        /**
         * Add operation
         * @param a range of first symbol to be added
         * @param b range of second symbol to be added
         * @return resulting range representing the value range after add
         */
        public Range add(final Range a, final Range b) {
            if (a.isIntegerType() && b.isIntegerType()) {
                final IntegerRange lhs = (IntegerRange)a;
                final IntegerRange rhs = (IntegerRange)b;
                if (checkAdd(lhs.getMin(), rhs.getMin()) && checkAdd(lhs.getMax(), rhs.getMax())) {
                    return createIntegerRange(lhs.getMin() + rhs.getMin(), lhs.getMax() + rhs.getMax());
                }
            }

            if (a.getType().isNumeric() && b.getType().isNumeric()) {
                return createNumberRange();
            }

            return createGenericRange();
        }

        /**
         * Sub operation
         * @param a range of first symbol to be subtracted
         * @param b range of second symbol to be subtracted
         * @return resulting range representing the value range after subtraction
         */
        public Range sub(final Range a, final Range b) {
            if (a.isIntegerType() && b.isIntegerType()) {
                final IntegerRange lhs = (IntegerRange)a;
                final IntegerRange rhs = (IntegerRange)b;
                if (checkSub(lhs.getMin(), rhs.getMax()) && checkSub(lhs.getMax(), rhs.getMin())) {
                    return createIntegerRange(lhs.getMin() - rhs.getMax(), lhs.getMax() - rhs.getMin());
                }
            }

            if (a.getType().isNumeric() && b.getType().isNumeric()) {
                return createNumberRange();
            }

            return createGenericRange();
        }

        /**
         * Mul operation
         * @param a range of first symbol to be multiplied
         * @param b range of second symbol to be multiplied
         * @return resulting range representing the value range after multiplication
         */
        public Range mul(final Range a, final Range b) {
            if (a.isIntegerType() && b.isIntegerType()) {
                final IntegerRange lhs = (IntegerRange)a;
                final IntegerRange rhs = (IntegerRange)b;

                //ensure that nothing ever overflows or underflows
                if (checkMul(lhs.getMin(), rhs.getMin()) &&
                    checkMul(lhs.getMax(), rhs.getMax()) &&
                    checkMul(lhs.getMin(), rhs.getMax()) &&
                    checkMul(lhs.getMax(), rhs.getMin())) {

                    final List<Long> results =
                        Arrays.asList(
                            lhs.getMin() * rhs.getMin(),
                            lhs.getMin() * rhs.getMax(),
                            lhs.getMax() * rhs.getMin(),
                            lhs.getMax() * rhs.getMax());
                    return createIntegerRange(Collections.min(results), Collections.max(results));
                }
            }

            if (a.getType().isNumeric() && b.getType().isNumeric()) {
                return createNumberRange();
            }

            return createGenericRange();
        }

        /**
         * Neg operation
         * @param a range of value symbol to be negated
         * @return resulting range representing the value range after neg
         */
        public Range neg(final Range a) {
            if (a.isIntegerType()) {
                final IntegerRange rhs = (IntegerRange)a;
                if (rhs.getMin() != Long.MIN_VALUE && rhs.getMax() != Long.MIN_VALUE) {
                    return createIntegerRange(-rhs.getMax(), -rhs.getMin());
                }
            }

            if (a.getType().isNumeric()) {
                return createNumberRange();
            }

            return createGenericRange();
        }

        /**
         * Bitwise and operation
         * @param a range of first symbol to be and:ed
         * @param b range of second symbol to be and:ed
         * @return resulting range representing the value range after and
         */
        public Range and(final Range a, final Range b) {
            if (a.isIntegerType() && b.isIntegerType()) {
                final int resultMask = (int) (((IntegerRange)a).getBitMask() & ((IntegerRange)b).getBitMask());
                if (resultMask >= 0) {
                    return createIntegerRange(0, resultMask);
                }
            } else if (a.isUnknown() && b.isIntegerType()) {
                final long operandMask = ((IntegerRange)b).getBitMask();
                if (operandMask >= 0) {
                    return createIntegerRange(0, operandMask);
                }
            } else if (a.isIntegerType() && b.isUnknown()) {
                final long operandMask = ((IntegerRange)a).getBitMask();
                if (operandMask >= 0) {
                    return createIntegerRange(0, operandMask);
                }
            }

            return createTypeRange(Type.INT);
        }

        /**
         * Bitwise or operation
         * @param a range of first symbol to be or:ed
         * @param b range of second symbol to be or:ed
         * @return resulting range representing the value range after or
         */
        public Range or(final Range a, final Range b) {
            if (a.isIntegerType() && b.isIntegerType()) {
                final int resultMask = (int)(((IntegerRange)a).getBitMask() | ((IntegerRange)b).getBitMask());
                if (resultMask >= 0) {
                    return createIntegerRange(0, resultMask);
                }
            }

            return createTypeRange(Type.INT);
        }

        /**
         * Bitwise xor operation
         * @param a range of first symbol to be xor:ed
         * @param b range of second symbol to be xor:ed
         * @return resulting range representing the value range after and
         */
        public Range xor(final Range a, final Range b) {
            if (a.isIntegerConst() && b.isIntegerConst()) {
                return createRange(((IntegerRange)a).getMin() ^ ((IntegerRange)b).getMin());
            }

            if (a.isIntegerType() && b.isIntegerType()) {
                final int resultMask = (int)(((IntegerRange)a).getBitMask() | ((IntegerRange)b).getBitMask());
                if (resultMask >= 0) {
                    return createIntegerRange(0, createIntegerRange(0, resultMask).getBitMask());
                }
            }
            return createTypeRange(Type.INT);
        }

        /**
         * Bitwise shl operation
         * @param a range of first symbol to be shl:ed
         * @param b range of second symbol to be shl:ed
         * @return resulting range representing the value range after shl
         */
        public Range shl(final Range a, final Range b) {
            if (b.isIntegerType() && b.isIntegerConst()) {
                final IntegerRange left  = (IntegerRange)(a.isIntegerType() ? a : createTypeRange(Type.INT));
                final int          shift = (int)((IntegerRange) b).getMin() & 0x1f;
                final int          min   = (int)left.getMin() << shift;
                final int          max   = (int)left.getMax() << shift;
                if (min >> shift == left.getMin() && max >> shift == left.getMax()) {
                    return createIntegerRange(min, max);
                }
            }

            return createTypeRange(Type.INT);
        }

        /**
         * Bitwise shr operation
         * @param a range of first symbol to be shr:ed
         * @param b range of second symbol to be shr:ed
         * @return resulting range representing the value range after shr
         */
        public Range shr(final Range a, final Range b) {
            if (b.isIntegerType() && b.isIntegerConst()) {
                final long         shift = ((IntegerRange) b).getMin() & 0x1f;
                final IntegerRange left  = (IntegerRange)(a.isIntegerType() ? a : createTypeRange(Type.INT));
                if (left.getMin() >= 0) {
                    long min = left.getMin() >>> shift;
                    long max = left.getMax() >>> shift;
                    return createIntegerRange(min, max);
                } else if (shift >= 1) {
                    return createIntegerRange(0, JSType.MAX_UINT >>> shift);
                }
            }

            return createTypeRange(Type.INT);
        }

        /**
         * Bitwise sar operation
         * @param a range of first symbol to be sar:ed
         * @param b range of second symbol to be sar:ed
         * @return resulting range representing the value range after sar
         */
        public Range sar(final Range a, final Range b) {
            if (b.isIntegerType() && b.isIntegerConst()) {
                final IntegerRange left  = (IntegerRange)(a.isIntegerType() ? a : createTypeRange(Type.INT));
                final long         shift = ((IntegerRange) b).getMin() & 0x1f;
                final long         min   = left.getMin() >> shift;
                final long         max   = left.getMax() >> shift;
                return createIntegerRange(min, max);
            }

            return createTypeRange(Type.INT);
        }

        /**
         * Modulo operation
         * @param a range of first symbol to the mod operation
         * @param b range of second symbol to be mod operation
         * @return resulting range representing the value range after mod
         */
        public Range mod(final Range a, final Range b) {
            if (a.isIntegerType() && b.isIntegerType()) {
                final IntegerRange rhs = (IntegerRange) b;
                if (rhs.getMin() > 0 || rhs.getMax() < 0) { // divisor range must not include 0
                    final long absmax = Math.max(Math.abs(rhs.getMin()), Math.abs(rhs.getMax())) - 1;
                    return createIntegerRange(rhs.getMin() > 0 ? 0 : -absmax, rhs.getMax() < 0 ? 0 : +absmax);
                }
            }
            return createTypeRange(Type.NUMBER);
        }

        /**
         * Division operation
         * @param a range of first symbol to the division
         * @param b range of second symbol to be division
         * @return resulting range representing the value range after division
         */
        public Range div(final Range a, final Range b) {
            // TODO
            return createTypeRange(Type.NUMBER);
        }
    }

    /**
     * Simple trace functionality that will log range creation
     */
    public static class TraceFunctionality extends Functionality {
        TraceFunctionality(final DebugLogger log) {
            super(log);
        }

        private Range trace(final Range result, final String operation, final Range... operands) {
            log.fine("range::" + operation + Arrays.toString(operands) + " => " + result);
            return result;
        }

        @Override
        public Range join(final Range a, final Range b) {
            final Range result = super.join(a, b);
            if (!a.equals(b)) {
                trace(result, "join", a, b);
            }
            return result;
        }

        @Override
        public Range add(final Range a, final Range b) {
            return trace(super.add(a, b), "add", a, b);
        }

        @Override
        public Range sub(final Range a, final Range b) {
            return trace(super.sub(a, b), "sub", a, b);
        }

        @Override
        public Range mul(final Range a, final Range b) {
            return trace(super.mul(a, b), "mul", a, b);
        }

        @Override
        public Range neg(final Range a) {
            return trace(super.neg(a), "neg", a);
        }

        @Override
        public Range and(final Range a, final Range b) {
            return trace(super.and(a, b), "and", a, b);
        }

        @Override
        public Range or(final Range a, final Range b) {
            return trace(super.or(a, b), "or", a, b);
        }

        @Override
        public Range xor(final Range a, final Range b) {
            return trace(super.xor(a, b), "xor", a, b);
        }

        @Override
        public Range shl(final Range a, final Range b) {
            return trace(super.shl(a, b), "shl", a, b);
        }

        @Override
        public Range shr(final Range a, final Range b) {
            return trace(super.shr(a, b), "shr", a, b);
        }

        @Override
        public Range sar(final Range a, final Range b) {
            return trace(super.sar(a, b), "sar", a, b);
        }

        @Override
        public Range mod(final Range a, final Range b) {
            return trace(super.mod(a, b), "mod", a, b);
        }

        @Override
        public Range div(final Range a, final Range b) {
            return trace(super.div(a, b), "div", a, b);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(getType());
    }

    @SuppressWarnings("unused")
    private static boolean isRepresentableAsInt(final double number) {
        return (int)number == number && !isNegativeZero(number);
    }

    private static boolean isRepresentableAsLong(final double number) {
        return (long)number == number && !isNegativeZero(number);
    }

    private static boolean isNegativeZero(final double number) {
        return Double.doubleToLongBits(number) == Double.doubleToLongBits(-0.0);
    }
}
