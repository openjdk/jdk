/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.hotspot.HotSpotResolvedObjectTypeImpl.fromObjectClass;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;

import jdk.vm.ci.inittimer.SuppressFBWarnings;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Represents a constant non-{@code null} object reference, within the compiler and across the
 * compiler/runtime interface.
 */
final class HotSpotObjectConstantImpl implements HotSpotObjectConstant, HotSpotProxified {

    static JavaConstant forObject(Object object) {
        return forObject(object, false);
    }

    static JavaConstant forObject(Object object, boolean compressed) {
        if (object == null) {
            return compressed ? HotSpotCompressedNullConstant.COMPRESSED_NULL : JavaConstant.NULL_POINTER;
        } else {
            return new HotSpotObjectConstantImpl(object, compressed);
        }
    }

    static JavaConstant forStableArray(Object object, int stableDimension, boolean isDefaultStable) {
        if (object == null) {
            return JavaConstant.NULL_POINTER;
        } else {
            assert object.getClass().isArray();
            return new HotSpotObjectConstantImpl(object, false, stableDimension, isDefaultStable);
        }
    }

    public static JavaConstant forBoxedValue(JavaKind kind, Object value) {
        if (kind == JavaKind.Object) {
            return HotSpotObjectConstantImpl.forObject(value);
        } else {
            return JavaConstant.forBoxedPrimitive(value);
        }
    }

    static Object asBoxedValue(Constant constant) {
        if (JavaConstant.isNull(constant)) {
            return null;
        } else if (constant instanceof HotSpotObjectConstantImpl) {
            return ((HotSpotObjectConstantImpl) constant).object;
        } else {
            return ((JavaConstant) constant).asBoxedPrimitive();
        }
    }

    private final Object object;
    private final boolean compressed;
    private final byte stableDimension;
    private final boolean isDefaultStable;

    private HotSpotObjectConstantImpl(Object object, boolean compressed, int stableDimension, boolean isDefaultStable) {
        this.object = object;
        this.compressed = compressed;
        this.stableDimension = (byte) stableDimension;
        this.isDefaultStable = isDefaultStable;
        assert object != null;
        assert stableDimension == 0 || (object != null && object.getClass().isArray());
        assert stableDimension >= 0 && stableDimension <= 255;
        assert !isDefaultStable || stableDimension > 0;
    }

    private HotSpotObjectConstantImpl(Object object, boolean compressed) {
        this(object, compressed, 0, false);
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    /**
     * Package-private accessor for the object represented by this constant.
     */
    Object object() {
        return object;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public JavaConstant compress() {
        assert !compressed;
        return new HotSpotObjectConstantImpl(object, true, stableDimension, isDefaultStable);
    }

    public JavaConstant uncompress() {
        assert compressed;
        return new HotSpotObjectConstantImpl(object, false, stableDimension, isDefaultStable);
    }

    public HotSpotResolvedObjectType getType() {
        return fromObjectClass(object.getClass());
    }

    public JavaConstant getClassLoader() {
        if (object instanceof Class) {
            /*
             * This is an intrinsic for getClassLoader0, which occurs after any security checks. We
             * can't call that directly so just call getClassLoader.
             */
            return HotSpotObjectConstantImpl.forObject(((Class<?>) object).getClassLoader());
        }
        return null;
    }

    public int getIdentityHashCode() {
        return System.identityHashCode(object);
    }

    public JavaConstant getComponentType() {
        if (object instanceof Class) {
            return HotSpotObjectConstantImpl.forObject(((Class<?>) object).getComponentType());
        }
        return null;
    }

    public JavaConstant getSuperclass() {
        if (object instanceof Class) {
            return HotSpotObjectConstantImpl.forObject(((Class<?>) object).getSuperclass());
        }
        return null;
    }

    public JavaConstant getCallSiteTarget(Assumptions assumptions) {
        if (object instanceof CallSite) {
            CallSite callSite = (CallSite) object;
            MethodHandle target = callSite.getTarget();
            if (!(callSite instanceof ConstantCallSite)) {
                if (assumptions == null) {
                    return null;
                }
                assumptions.record(new Assumptions.CallSiteTargetValue(callSite, target));
            }
            return HotSpotObjectConstantImpl.forObject(target);
        }
        return null;
    }

    @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "reference equality is what we want")
    public boolean isInternedString() {
        if (object instanceof String) {
            String s = (String) object;
            return s.intern() == s;
        }
        return false;
    }

    public <T> T asObject(Class<T> type) {
        if (type.isInstance(object)) {
            return type.cast(object);
        }
        return null;
    }

    public Object asObject(ResolvedJavaType type) {
        if (type.isInstance(this)) {
            return object;
        }
        return null;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public Object asBoxedPrimitive() {
        throw new IllegalArgumentException();
    }

    @Override
    public int asInt() {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean asBoolean() {
        throw new IllegalArgumentException();
    }

    @Override
    public long asLong() {
        throw new IllegalArgumentException();
    }

    @Override
    public float asFloat() {
        throw new IllegalArgumentException();
    }

    @Override
    public double asDouble() {
        throw new IllegalArgumentException();
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(object);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof HotSpotObjectConstantImpl) {
            HotSpotObjectConstantImpl other = (HotSpotObjectConstantImpl) o;
            return object == other.object && compressed == other.compressed && stableDimension == other.stableDimension && isDefaultStable == other.isDefaultStable;
        }
        return false;
    }

    @Override
    public String toValueString() {
        if (object instanceof String) {
            return "\"" + (String) object + "\"";
        } else {
            return JavaKind.Object.format(object);
        }
    }

    @Override
    public String toString() {
        return (compressed ? "NarrowOop" : getJavaKind().getJavaName()) + "[" + JavaKind.Object.format(object) + "]";
    }

    /**
     * Number of stable dimensions if this constant is a stable array.
     */
    public int getStableDimension() {
        return stableDimension & 0xff;
    }

    /**
     * Returns {@code true} if this is a stable array constant and its elements should be considered
     * as stable regardless of whether they are default values.
     */
    public boolean isDefaultStable() {
        return isDefaultStable;
    }
}
