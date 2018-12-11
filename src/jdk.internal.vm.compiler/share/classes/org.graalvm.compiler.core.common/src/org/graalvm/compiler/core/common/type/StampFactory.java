/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.CodeUtil.signExtend;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class StampFactory {

    // JaCoCo Exclude

    private static final Stamp[] stampCache = new Stamp[JavaKind.values().length];
    private static final Stamp[] emptyStampCache = new Stamp[JavaKind.values().length];
    private static final Stamp objectStamp = new ObjectStamp(null, false, false, false);
    private static final Stamp objectNonNullStamp = new ObjectStamp(null, false, true, false);
    private static final Stamp objectAlwaysNullStamp = new ObjectStamp(null, false, false, true);
    private static final Stamp positiveInt = forInteger(JavaKind.Int, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
    private static final Stamp booleanTrue = forInteger(JavaKind.Boolean, -1, -1, 1, 1);
    private static final Stamp booleanFalse = forInteger(JavaKind.Boolean, 0, 0, 0, 0);
    private static final Stamp rawPointer = new RawPointerStamp();

    private static void setCache(JavaKind kind, Stamp stamp) {
        stampCache[kind.ordinal()] = stamp;
    }

    private static void setIntCache(JavaKind kind) {
        int bits = kind.getStackKind().getBitCount();
        long mask;
        if (kind.isUnsigned()) {
            mask = CodeUtil.mask(kind.getBitCount());
        } else {
            mask = CodeUtil.mask(bits);
        }
        setCache(kind, IntegerStamp.create(bits, kind.getMinValue(), kind.getMaxValue(), 0, mask));
    }

    private static void setFloatCache(JavaKind kind) {
        setCache(kind, new FloatStamp(kind.getBitCount()));
    }

    static {
        setIntCache(JavaKind.Boolean);
        setIntCache(JavaKind.Byte);
        setIntCache(JavaKind.Short);
        setIntCache(JavaKind.Char);
        setIntCache(JavaKind.Int);
        setIntCache(JavaKind.Long);

        setFloatCache(JavaKind.Float);
        setFloatCache(JavaKind.Double);

        setCache(JavaKind.Object, objectStamp);
        setCache(JavaKind.Void, VoidStamp.getInstance());
        setCache(JavaKind.Illegal, IllegalStamp.getInstance());

        for (JavaKind k : JavaKind.values()) {
            if (stampCache[k.ordinal()] != null) {
                emptyStampCache[k.ordinal()] = stampCache[k.ordinal()].empty();
            }
        }
    }

    public static Stamp tautology() {
        return booleanTrue;
    }

    public static Stamp contradiction() {
        return booleanFalse;
    }

    /**
     * Return a stamp for a Java kind, as it would be represented on the bytecode stack.
     */
    public static Stamp forKind(JavaKind kind) {
        assert stampCache[kind.ordinal()] != null : "unexpected forKind(" + kind + ")";
        return stampCache[kind.ordinal()];
    }

    /**
     * Return the stamp for the {@code void} type. This will return a singleton instance than can be
     * compared using {@code ==}.
     */
    public static Stamp forVoid() {
        return VoidStamp.getInstance();
    }

    public static Stamp intValue() {
        return forKind(JavaKind.Int);
    }

    public static Stamp positiveInt() {
        return positiveInt;
    }

    public static Stamp empty(JavaKind kind) {
        return emptyStampCache[kind.ordinal()];
    }

    public static IntegerStamp forInteger(JavaKind kind, long lowerBound, long upperBound, long downMask, long upMask) {
        return IntegerStamp.create(kind.getBitCount(), lowerBound, upperBound, downMask, upMask);
    }

    public static IntegerStamp forInteger(JavaKind kind, long lowerBound, long upperBound) {
        return forInteger(kind.getBitCount(), lowerBound, upperBound);
    }

    /**
     * Create a new stamp use {@code newLowerBound} and {@code newUpperBound} computing the
     * appropriate {@link IntegerStamp#upMask} and {@link IntegerStamp#downMask} and incorporating
     * any mask information from {@code maskStamp}.
     *
     * @param bits
     * @param newLowerBound
     * @param newUpperBound
     * @param maskStamp
     * @return a new stamp with the appropriate bounds and masks
     */
    public static IntegerStamp forIntegerWithMask(int bits, long newLowerBound, long newUpperBound, IntegerStamp maskStamp) {
        IntegerStamp limit = StampFactory.forInteger(bits, newLowerBound, newUpperBound);
        return IntegerStamp.create(bits, newLowerBound, newUpperBound, limit.downMask() | maskStamp.downMask(), limit.upMask() & maskStamp.upMask());
    }

    public static IntegerStamp forIntegerWithMask(int bits, long newLowerBound, long newUpperBound, long newDownMask, long newUpMask) {
        IntegerStamp limit = StampFactory.forInteger(bits, newLowerBound, newUpperBound);
        return IntegerStamp.create(bits, newLowerBound, newUpperBound, limit.downMask() | newDownMask, limit.upMask() & newUpMask);
    }

    public static IntegerStamp forInteger(int bits) {
        return IntegerStamp.create(bits, CodeUtil.minValue(bits), CodeUtil.maxValue(bits), 0, CodeUtil.mask(bits));
    }

    public static IntegerStamp forUnsignedInteger(int bits) {
        return forUnsignedInteger(bits, 0, NumUtil.maxValueUnsigned(bits), 0, CodeUtil.mask(bits));
    }

    public static IntegerStamp forUnsignedInteger(int bits, long unsignedLowerBound, long unsignedUpperBound) {
        return forUnsignedInteger(bits, unsignedLowerBound, unsignedUpperBound, 0, CodeUtil.mask(bits));
    }

    public static IntegerStamp forUnsignedInteger(int bits, long unsignedLowerBound, long unsignedUpperBound, long downMask, long upMask) {
        long lowerBound = signExtend(unsignedLowerBound, bits);
        long upperBound = signExtend(unsignedUpperBound, bits);
        if (!NumUtil.sameSign(lowerBound, upperBound)) {
            lowerBound = CodeUtil.minValue(bits);
            upperBound = CodeUtil.maxValue(bits);
        }
        long mask = CodeUtil.mask(bits);
        return IntegerStamp.create(bits, lowerBound, upperBound, downMask & mask, upMask & mask);
    }

    public static IntegerStamp forInteger(int bits, long lowerBound, long upperBound) {
        return IntegerStamp.create(bits, lowerBound, upperBound, 0, CodeUtil.mask(bits));
    }

    public static FloatStamp forFloat(JavaKind kind, double lowerBound, double upperBound, boolean nonNaN) {
        assert kind.isNumericFloat();
        return new FloatStamp(kind.getBitCount(), lowerBound, upperBound, nonNaN);
    }

    public static Stamp forConstant(JavaConstant value) {
        JavaKind kind = value.getJavaKind();
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
                long mask = value.asLong() & CodeUtil.mask(kind.getBitCount());
                return forInteger(kind.getStackKind(), value.asLong(), value.asLong(), mask, mask);
            case Float:
                return forFloat(kind, value.asFloat(), value.asFloat(), !Float.isNaN(value.asFloat()));
            case Double:
                return forFloat(kind, value.asDouble(), value.asDouble(), !Double.isNaN(value.asDouble()));
            case Illegal:
                return forKind(JavaKind.Illegal);
            case Object:
                if (value.isNull()) {
                    return alwaysNull();
                } else {
                    return objectNonNull();
                }
            default:
                throw new GraalError("unexpected kind: %s", kind);
        }
    }

    public static Stamp forConstant(JavaConstant value, MetaAccessProvider metaAccess) {
        if (value.getJavaKind() == JavaKind.Object) {
            ResolvedJavaType type = value.isNull() ? null : metaAccess.lookupJavaType(value);
            return new ObjectStamp(type, value.isNonNull(), value.isNonNull(), value.isNull());
        } else {
            return forConstant(value);
        }
    }

    public static Stamp object() {
        return objectStamp;
    }

    public static Stamp objectNonNull() {
        return objectNonNullStamp;
    }

    public static Stamp alwaysNull() {
        return objectAlwaysNullStamp;
    }

    public static ObjectStamp object(TypeReference type) {
        return object(type, false);
    }

    public static ObjectStamp objectNonNull(TypeReference type) {
        return object(type, true);
    }

    public static ObjectStamp object(TypeReference type, boolean nonNull) {
        if (type == null) {
            return new ObjectStamp(null, false, nonNull, false);
        } else {
            return new ObjectStamp(type.getType(), type.isExact(), nonNull, false);
        }
    }

    public static Stamp[] createParameterStamps(Assumptions assumptions, ResolvedJavaMethod method) {
        return createParameterStamps(assumptions, method, false);
    }

    public static Stamp[] createParameterStamps(Assumptions assumptions, ResolvedJavaMethod method, boolean trustInterfaceTypes) {
        Signature signature = method.getSignature();
        Stamp[] result = new Stamp[signature.getParameterCount(method.hasReceiver())];

        int index = 0;
        ResolvedJavaType accessingClass = method.getDeclaringClass();
        if (method.hasReceiver()) {
            if (trustInterfaceTypes) {
                result[index++] = StampFactory.objectNonNull(TypeReference.createTrusted(assumptions, accessingClass));
            } else {
                result[index++] = StampFactory.objectNonNull(TypeReference.create(assumptions, accessingClass));
            }
        }

        for (int i = 0; i < signature.getParameterCount(false); i++) {
            JavaType type = signature.getParameterType(i, accessingClass);
            JavaKind kind = type.getJavaKind();

            Stamp stamp;
            if (kind == JavaKind.Object && type instanceof ResolvedJavaType) {
                if (trustInterfaceTypes) {
                    stamp = StampFactory.object(TypeReference.createTrusted(assumptions, (ResolvedJavaType) type));
                } else {
                    stamp = StampFactory.object(TypeReference.create(assumptions, (ResolvedJavaType) type));
                }
            } else {
                stamp = StampFactory.forKind(kind);
            }
            result[index++] = stamp;
        }

        return result;
    }

    public static Stamp pointer() {
        return rawPointer;
    }

    public static StampPair forDeclaredType(Assumptions assumptions, JavaType returnType, boolean nonNull) {
        if (returnType.getJavaKind() == JavaKind.Object && returnType instanceof ResolvedJavaType) {
            ResolvedJavaType resolvedJavaType = (ResolvedJavaType) returnType;
            TypeReference reference = TypeReference.create(assumptions, resolvedJavaType);
            ResolvedJavaType elementalType = resolvedJavaType.getElementalType();
            if (elementalType.isInterface()) {
                assert reference == null || !reference.getType().equals(resolvedJavaType);
                TypeReference uncheckedType;
                ResolvedJavaType elementalImplementor = elementalType.getSingleImplementor();
                if (elementalImplementor != null && !elementalType.equals(elementalImplementor)) {
                    ResolvedJavaType implementor = elementalImplementor;
                    ResolvedJavaType t = resolvedJavaType;
                    while (t.isArray()) {
                        implementor = implementor.getArrayClass();
                        t = t.getComponentType();
                    }
                    uncheckedType = TypeReference.createTrusted(assumptions, implementor);
                } else {
                    uncheckedType = TypeReference.createTrusted(assumptions, resolvedJavaType);
                }
                return StampPair.create(StampFactory.object(reference, nonNull), StampFactory.object(uncheckedType, nonNull));
            }
            return StampPair.createSingle(StampFactory.object(reference, nonNull));
        } else {
            return StampPair.createSingle(StampFactory.forKind(returnType.getJavaKind()));
        }
    }

}
