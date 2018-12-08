/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.type;

import static org.graalvm.compiler.core.common.calc.FloatConvert.D2F;
import static org.graalvm.compiler.core.common.calc.FloatConvert.D2I;
import static org.graalvm.compiler.core.common.calc.FloatConvert.D2L;
import static org.graalvm.compiler.core.common.calc.FloatConvert.F2D;
import static org.graalvm.compiler.core.common.calc.FloatConvert.F2I;
import static org.graalvm.compiler.core.common.calc.FloatConvert.F2L;

import java.nio.ByteBuffer;
import java.util.function.DoubleBinaryOperator;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.FloatConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SerializableConstant;

public class FloatStamp extends PrimitiveStamp {

    private final double lowerBound;
    private final double upperBound;
    private final boolean nonNaN;

    protected FloatStamp(int bits) {
        this(bits, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false);
    }

    public FloatStamp(int bits, double lowerBound, double upperBound, boolean nonNaN) {
        super(bits, OPS);
        assert bits == 64 || (bits == 32 && (Double.isNaN(lowerBound) || (float) lowerBound == lowerBound) && (Double.isNaN(upperBound) || (float) upperBound == upperBound));
        assert Double.isNaN(lowerBound) == Double.isNaN(upperBound);
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.nonNaN = nonNaN;
    }

    @Override
    public Stamp unrestricted() {
        return new FloatStamp(getBits());
    }

    @Override
    public Stamp empty() {
        return new FloatStamp(getBits(), Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, true);
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        JavaConstant jc = (JavaConstant) c;
        assert jc.getJavaKind().isNumericFloat() && jc.getJavaKind().getBitCount() == getBits();
        return StampFactory.forConstant(jc);
    }

    @Override
    public SerializableConstant deserialize(ByteBuffer buffer) {
        switch (getBits()) {
            case 32:
                return JavaConstant.forFloat(buffer.getFloat());
            case 64:
                return JavaConstant.forDouble(buffer.getDouble());
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public boolean hasValues() {
        return lowerBound <= upperBound || !nonNaN;
    }

    @Override
    public JavaKind getStackKind() {
        if (getBits() > 32) {
            return JavaKind.Double;
        } else {
            return JavaKind.Float;
        }
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        return tool.getFloatingKind(getBits());
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        switch (getBits()) {
            case 32:
                return metaAccess.lookupJavaType(Float.TYPE);
            case 64:
                return metaAccess.lookupJavaType(Double.TYPE);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * The (inclusive) lower bound on the value described by this stamp.
     */
    public double lowerBound() {
        return lowerBound;
    }

    /**
     * The (inclusive) upper bound on the value described by this stamp.
     */
    public double upperBound() {
        return upperBound;
    }

    /**
     * Returns true if NaN is non included in the value described by this stamp.
     */
    public boolean isNonNaN() {
        return nonNaN;
    }

    /**
     * Returns true if this stamp represents the NaN value.
     */
    public boolean isNaN() {
        return Double.isNaN(lowerBound);
    }

    @Override
    public boolean isUnrestricted() {
        return lowerBound == Double.NEGATIVE_INFINITY && upperBound == Double.POSITIVE_INFINITY && !nonNaN;
    }

    public boolean contains(double value) {
        if (Double.isNaN(value)) {
            return !nonNaN;
        } else {
            /*
             * Don't use Double.compare for checking the bounds as -0.0 isn't correctly tracked, so
             * the presence of 0.0 means -0.0 might also exist in the range.
             */
            return value >= lowerBound && value <= upperBound;
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append('f');
        str.append(getBits());
        if (hasValues()) {
            str.append(nonNaN ? "!" : "");
            if (lowerBound == upperBound) {
                str.append(" [").append(lowerBound).append(']');
            } else if (lowerBound != Double.NEGATIVE_INFINITY || upperBound != Double.POSITIVE_INFINITY) {
                str.append(" [").append(lowerBound).append(" - ").append(upperBound).append(']');
            }
        } else {
            str.append("<empty>");
        }
        return str.toString();
    }

    private static double meetBounds(double a, double b, DoubleBinaryOperator op) {
        if (Double.isNaN(a)) {
            return b;
        } else if (Double.isNaN(b)) {
            return a;
        } else {
            return op.applyAsDouble(a, b);
        }
    }

    @Override
    public Stamp meet(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        if (isEmpty()) {
            return this;
        }
        if (otherStamp.isEmpty()) {
            return otherStamp;
        }
        FloatStamp other = (FloatStamp) otherStamp;
        assert getBits() == other.getBits();
        double meetUpperBound = meetBounds(upperBound, other.upperBound, Math::max);
        double meetLowerBound = meetBounds(lowerBound, other.lowerBound, Math::min);
        boolean meetNonNaN = nonNaN && other.nonNaN;
        if (Double.compare(meetLowerBound, lowerBound) == 0 && Double.compare(meetUpperBound, upperBound) == 0 && meetNonNaN == nonNaN) {
            return this;
        } else if (Double.compare(meetLowerBound, other.lowerBound) == 0 && Double.compare(meetUpperBound, other.upperBound) == 0 && meetNonNaN == other.nonNaN) {
            return other;
        } else {
            return new FloatStamp(getBits(), meetLowerBound, meetUpperBound, meetNonNaN);
        }
    }

    @Override
    public Stamp join(Stamp otherStamp) {
        if (otherStamp == this) {
            return this;
        }
        FloatStamp other = (FloatStamp) otherStamp;
        assert getBits() == other.getBits();
        double joinUpperBound = Math.min(upperBound, other.upperBound);
        double joinLowerBound = Math.max(lowerBound, other.lowerBound);
        boolean joinNonNaN = nonNaN || other.nonNaN;
        if (Double.compare(joinLowerBound, lowerBound) == 0 && Double.compare(joinUpperBound, upperBound) == 0 && joinNonNaN == nonNaN) {
            return this;
        } else if (Double.compare(joinLowerBound, other.lowerBound) == 0 && Double.compare(joinUpperBound, other.upperBound) == 0 && joinNonNaN == other.nonNaN) {
            return other;
        } else {
            return new FloatStamp(getBits(), joinLowerBound, joinUpperBound, joinNonNaN);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        result = prime * result + super.hashCode();
        temp = Double.doubleToLongBits(lowerBound);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (nonNaN ? 1231 : 1237);
        temp = Double.doubleToLongBits(upperBound);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (this == stamp) {
            return true;
        }
        if (stamp instanceof FloatStamp) {
            FloatStamp other = (FloatStamp) stamp;
            return getBits() == other.getBits();
        }
        return false;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        if (constant instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) constant;
            return prim.getJavaKind().isNumericFloat();
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass() || !super.equals(obj)) {
            return false;
        }
        FloatStamp other = (FloatStamp) obj;
        if (Double.doubleToLongBits(lowerBound) != Double.doubleToLongBits(other.lowerBound)) {
            return false;
        }
        if (Double.doubleToLongBits(upperBound) != Double.doubleToLongBits(other.upperBound)) {
            return false;
        }
        if (nonNaN != other.nonNaN) {
            return false;
        }
        return super.equals(other);
    }

    @Override
    public JavaConstant asConstant() {
        if (isConstant()) {
            switch (getBits()) {
                case 32:
                    return JavaConstant.forFloat((float) lowerBound);
                case 64:
                    return JavaConstant.forDouble(lowerBound);
            }
        }
        return null;
    }

    private boolean isConstant() {
        /*
         * There are many forms of NaNs and any operations on them can silently convert them into
         * the canonical NaN.
         */
        return (Double.compare(lowerBound, upperBound) == 0 && nonNaN);
    }

    private static FloatStamp stampForConstant(Constant constant) {
        FloatStamp result;
        PrimitiveConstant value = (PrimitiveConstant) constant;
        switch (value.getJavaKind()) {
            case Float:
                if (Float.isNaN(value.asFloat())) {
                    result = new FloatStamp(32, Double.NaN, Double.NaN, false);
                } else {
                    result = new FloatStamp(32, value.asFloat(), value.asFloat(), !Float.isNaN(value.asFloat()));
                }
                break;
            case Double:
                if (Double.isNaN(value.asDouble())) {
                    result = new FloatStamp(64, Double.NaN, Double.NaN, false);
                } else {
                    result = new FloatStamp(64, value.asDouble(), value.asDouble(), !Double.isNaN(value.asDouble()));
                }
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        if (result.isConstant()) {
            return result;
        }
        return null;
    }

    private static Stamp maybeFoldConstant(UnaryOp<?> op, FloatStamp stamp) {
        if (stamp.isConstant()) {
            JavaConstant constant = stamp.asConstant();
            Constant folded = op.foldConstant(constant);
            if (folded != null) {
                return FloatStamp.stampForConstant(folded);
            }
        }
        return null;
    }

    private static Stamp maybeFoldConstant(BinaryOp<?> op, FloatStamp stamp1, FloatStamp stamp2) {
        if (stamp1.isConstant() && stamp2.isConstant()) {
            JavaConstant constant1 = stamp1.asConstant();
            JavaConstant constant2 = stamp2.asConstant();
            Constant folded = op.foldConstant(constant1, constant2);
            if (folded != null) {
                FloatStamp stamp = stampForConstant(folded);
                if (stamp != null && stamp.isConstant()) {
                    assert stamp.asConstant().equals(folded);
                    return stamp;
                }
            }
        }
        return null;
    }

    public static final ArithmeticOpTable OPS = new ArithmeticOpTable(

                    new UnaryOp.Neg() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return JavaConstant.forFloat(-value.asFloat());
                                case Double:
                                    return JavaConstant.forDouble(-value.asDouble());
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            FloatStamp stamp = (FloatStamp) s;
                            Stamp folded = maybeFoldConstant(this, stamp);
                            if (folded != null) {
                                return folded;
                            }
                            return new FloatStamp(stamp.getBits(), -stamp.upperBound(), -stamp.lowerBound(), stamp.isNonNaN());
                        }

                    },

                    new BinaryOp.Add(false, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            switch (a.getJavaKind()) {
                                case Float:
                                    return JavaConstant.forFloat(a.asFloat() + b.asFloat());
                                case Double:
                                    return JavaConstant.forDouble(a.asDouble() + b.asDouble());
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            switch (n.getJavaKind()) {
                                case Float:
                                    return Float.compare(n.asFloat(), -0.0f) == 0;
                                case Double:
                                    return Double.compare(n.asDouble(), -0.0) == 0;
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }
                    },

                    new BinaryOp.Sub(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            switch (a.getJavaKind()) {
                                case Float:
                                    return JavaConstant.forFloat(a.asFloat() - b.asFloat());
                                case Double:
                                    return JavaConstant.forDouble(a.asDouble() - b.asDouble());
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            switch (n.getJavaKind()) {
                                case Float:
                                    return Float.compare(n.asFloat(), 0.0f) == 0;
                                case Double:
                                    return Double.compare(n.asDouble(), 0.0) == 0;
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }
                    },

                    new BinaryOp.Mul(false, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            switch (a.getJavaKind()) {
                                case Float:
                                    return JavaConstant.forFloat(a.asFloat() * b.asFloat());
                                case Double:
                                    return JavaConstant.forDouble(a.asDouble() * b.asDouble());
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            switch (n.getJavaKind()) {
                                case Float:
                                    return Float.compare(n.asFloat(), 1.0f) == 0;
                                case Double:
                                    return Double.compare(n.asDouble(), 1.0) == 0;
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }
                    },

                    null,

                    null,

                    new BinaryOp.Div(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            switch (a.getJavaKind()) {
                                case Float:
                                    float floatDivisor = b.asFloat();
                                    return (floatDivisor == 0) ? null : JavaConstant.forFloat(a.asFloat() / floatDivisor);
                                case Double:
                                    double doubleDivisor = b.asDouble();
                                    return (doubleDivisor == 0) ? null : JavaConstant.forDouble(a.asDouble() / doubleDivisor);
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant value) {
                            PrimitiveConstant n = (PrimitiveConstant) value;
                            switch (n.getJavaKind()) {
                                case Float:
                                    return Float.compare(n.asFloat(), 1.0f) == 0;
                                case Double:
                                    return Double.compare(n.asDouble(), 1.0) == 0;
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }
                    },

                    new BinaryOp.Rem(false, false) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            switch (a.getJavaKind()) {
                                case Float:
                                    return JavaConstant.forFloat(a.asFloat() % b.asFloat());
                                case Double:
                                    return JavaConstant.forDouble(a.asDouble() % b.asDouble());
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }
                    },

                    new UnaryOp.Not() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            switch (value.getJavaKind()) {
                                case Float:
                                    int f = Float.floatToRawIntBits(value.asFloat());
                                    return JavaConstant.forFloat(Float.intBitsToFloat(~f));
                                case Double:
                                    long d = Double.doubleToRawLongBits(value.asDouble());
                                    return JavaConstant.forDouble(Double.longBitsToDouble(~d));
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            FloatStamp stamp = (FloatStamp) s;
                            JavaConstant constant = stamp.asConstant();
                            if (constant != null) {
                                Constant folded = foldConstant(constant);
                                if (folded != null) {
                                    FloatStamp result = stampForConstant(folded);
                                    if (result != null && result.isConstant()) {
                                        return result;
                                    }
                                }
                            }
                            return s.unrestricted();
                        }
                    },

                    new BinaryOp.And(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            switch (a.getJavaKind()) {
                                case Float:
                                    int fa = Float.floatToRawIntBits(a.asFloat());
                                    int fb = Float.floatToRawIntBits(b.asFloat());
                                    return JavaConstant.forFloat(Float.intBitsToFloat(fa & fb));
                                case Double:
                                    long da = Double.doubleToRawLongBits(a.asDouble());
                                    long db = Double.doubleToRawLongBits(b.asDouble());
                                    return JavaConstant.forDouble(Double.longBitsToDouble(da & db));
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            PrimitiveConstant value = (PrimitiveConstant) n;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return Float.floatToRawIntBits(value.asFloat()) == 0xFFFFFFFF;
                                case Double:
                                    return Double.doubleToRawLongBits(value.asDouble()) == 0xFFFFFFFFFFFFFFFFL;
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }
                    },

                    new BinaryOp.Or(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            switch (a.getJavaKind()) {
                                case Float:
                                    int fa = Float.floatToRawIntBits(a.asFloat());
                                    int fb = Float.floatToRawIntBits(b.asFloat());
                                    float floatOr = Float.intBitsToFloat(fa | fb);
                                    assert (fa | fb) == Float.floatToRawIntBits((floatOr));
                                    return JavaConstant.forFloat(floatOr);
                                case Double:
                                    long da = Double.doubleToRawLongBits(a.asDouble());
                                    long db = Double.doubleToRawLongBits(b.asDouble());
                                    return JavaConstant.forDouble(Double.longBitsToDouble(da | db));
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            PrimitiveConstant value = (PrimitiveConstant) n;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return Float.floatToRawIntBits(value.asFloat()) == 0;
                                case Double:
                                    return Double.doubleToRawLongBits(value.asDouble()) == 0L;
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }
                    },

                    new BinaryOp.Xor(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            PrimitiveConstant a = (PrimitiveConstant) const1;
                            PrimitiveConstant b = (PrimitiveConstant) const2;
                            assert a.getJavaKind() == b.getJavaKind();
                            switch (a.getJavaKind()) {
                                case Float:
                                    int fa = Float.floatToRawIntBits(a.asFloat());
                                    int fb = Float.floatToRawIntBits(b.asFloat());
                                    return JavaConstant.forFloat(Float.intBitsToFloat(fa ^ fb));
                                case Double:
                                    long da = Double.doubleToRawLongBits(a.asDouble());
                                    long db = Double.doubleToRawLongBits(b.asDouble());
                                    return JavaConstant.forDouble(Double.longBitsToDouble(da ^ db));
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s1, Stamp s2) {
                            if (s1.isEmpty()) {
                                return s1;
                            }
                            if (s2.isEmpty()) {
                                return s2;
                            }
                            FloatStamp stamp1 = (FloatStamp) s1;
                            FloatStamp stamp2 = (FloatStamp) s2;
                            Stamp folded = maybeFoldConstant(this, stamp1, stamp2);
                            if (folded != null) {
                                return folded;
                            }
                            return stamp1.unrestricted();
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            PrimitiveConstant value = (PrimitiveConstant) n;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return Float.floatToRawIntBits(value.asFloat()) == 0;
                                case Double:
                                    return Double.doubleToRawLongBits(value.asDouble()) == 0L;
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }
                    },

                    null, null, null,

                    new UnaryOp.Abs() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return JavaConstant.forFloat(Math.abs(value.asFloat()));
                                case Double:
                                    return JavaConstant.forDouble(Math.abs(value.asDouble()));
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            FloatStamp stamp = (FloatStamp) s;
                            Stamp folded = maybeFoldConstant(this, stamp);
                            if (folded != null) {
                                return folded;
                            }
                            if (stamp.isNaN()) {
                                return stamp;
                            }
                            return new FloatStamp(stamp.getBits(), 0, Math.max(-stamp.lowerBound(), stamp.upperBound()), stamp.isNonNaN());
                        }
                    },

                    new UnaryOp.Sqrt() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            switch (value.getJavaKind()) {
                                case Float:
                                    return JavaConstant.forFloat((float) Math.sqrt(value.asFloat()));
                                case Double:
                                    return JavaConstant.forDouble(Math.sqrt(value.asDouble()));
                                default:
                                    throw GraalError.shouldNotReachHere();
                            }
                        }

                        @Override
                        public Stamp foldStamp(Stamp s) {
                            if (s.isEmpty()) {
                                return s;
                            }
                            FloatStamp stamp = (FloatStamp) s;
                            Stamp folded = maybeFoldConstant(this, stamp);
                            if (folded != null) {
                                return folded;
                            }
                            return s.unrestricted();
                        }
                    },

                    null, null, null,

                    new FloatConvertOp(F2I) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forInt((int) value.asFloat());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Int);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 32;
                            boolean mustHaveZero = !floatStamp.isNonNaN();
                            int lowerBound = (int) floatStamp.lowerBound();
                            int upperBound = (int) floatStamp.upperBound();
                            if (mustHaveZero) {
                                if (lowerBound > 0) {
                                    lowerBound = 0;
                                } else if (upperBound < 0) {
                                    upperBound = 0;
                                }
                            }
                            return StampFactory.forInteger(JavaKind.Int, lowerBound, upperBound);
                        }
                    },

                    new FloatConvertOp(F2L) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forLong((long) value.asFloat());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Long);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 32;
                            boolean mustHaveZero = !floatStamp.isNonNaN();
                            long lowerBound = (long) floatStamp.lowerBound();
                            long upperBound = (long) floatStamp.upperBound();
                            if (mustHaveZero) {
                                if (lowerBound > 0) {
                                    lowerBound = 0;
                                } else if (upperBound < 0) {
                                    upperBound = 0;
                                }
                            }
                            return StampFactory.forInteger(JavaKind.Long, lowerBound, upperBound);
                        }
                    },

                    new FloatConvertOp(D2I) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forInt((int) value.asDouble());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Int);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 64;
                            boolean mustHaveZero = !floatStamp.isNonNaN();
                            int lowerBound = (int) floatStamp.lowerBound();
                            int upperBound = (int) floatStamp.upperBound();
                            if (mustHaveZero) {
                                if (lowerBound > 0) {
                                    lowerBound = 0;
                                } else if (upperBound < 0) {
                                    upperBound = 0;
                                }
                            }
                            return StampFactory.forInteger(JavaKind.Int, lowerBound, upperBound);
                        }
                    },

                    new FloatConvertOp(D2L) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forLong((long) value.asDouble());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Long);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 64;
                            boolean mustHaveZero = !floatStamp.isNonNaN();
                            long lowerBound = (long) floatStamp.lowerBound();
                            long upperBound = (long) floatStamp.upperBound();
                            if (mustHaveZero) {
                                if (lowerBound > 0) {
                                    lowerBound = 0;
                                } else if (upperBound < 0) {
                                    upperBound = 0;
                                }
                            }
                            return StampFactory.forInteger(JavaKind.Long, lowerBound, upperBound);
                        }
                    },

                    new FloatConvertOp(F2D) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forDouble(value.asFloat());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Double);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 32;
                            return StampFactory.forFloat(JavaKind.Double, floatStamp.lowerBound(), floatStamp.upperBound(), floatStamp.isNonNaN());
                        }
                    },

                    new FloatConvertOp(D2F) {

                        @Override
                        public Constant foldConstant(Constant c) {
                            PrimitiveConstant value = (PrimitiveConstant) c;
                            return JavaConstant.forFloat((float) value.asDouble());
                        }

                        @Override
                        public Stamp foldStamp(Stamp stamp) {
                            if (stamp.isEmpty()) {
                                return StampFactory.empty(JavaKind.Float);
                            }
                            FloatStamp floatStamp = (FloatStamp) stamp;
                            assert floatStamp.getBits() == 64;
                            return StampFactory.forFloat(JavaKind.Float, (float) floatStamp.lowerBound(), (float) floatStamp.upperBound(), floatStamp.isNonNaN());
                        }
                    });
}
