/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.util.Objects;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * HotSpot implementation of {@link ConstantReflectionProvider}.
 */
public class HotSpotConstantReflectionProvider implements ConstantReflectionProvider {

    protected final HotSpotJVMCIRuntime runtime;
    protected final HotSpotMethodHandleAccessProvider methodHandleAccess;
    protected final HotSpotMemoryAccessProviderImpl memoryAccess;

    public HotSpotConstantReflectionProvider(HotSpotJVMCIRuntime runtime) {
        this.runtime = runtime;
        this.methodHandleAccess = new HotSpotMethodHandleAccessProvider(this);
        this.memoryAccess = new HotSpotMemoryAccessProviderImpl(runtime);
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return methodHandleAccess;
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return memoryAccess;
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        if (x == y) {
            return true;
        } else if (x instanceof HotSpotObjectConstantImpl) {
            return y instanceof HotSpotObjectConstantImpl && ((HotSpotObjectConstantImpl) x).object() == ((HotSpotObjectConstantImpl) y).object();
        } else {
            return Objects.equals(x, y);
        }
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (array == null || array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }

        Object arrayObject = ((HotSpotObjectConstantImpl) array).object();
        if (!arrayObject.getClass().isArray()) {
            return null;
        }
        return Array.getLength(arrayObject);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array == null || array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        Object a = ((HotSpotObjectConstantImpl) array).object();

        if (!a.getClass().isArray() || index < 0 || index >= Array.getLength(a)) {
            return null;
        }

        if (a instanceof Object[]) {
            Object element = ((Object[]) a)[index];
            return HotSpotObjectConstantImpl.forObject(element);
        } else {
            return JavaConstant.forBoxedPrimitive(Array.get(a, index));
        }
    }

    /**
     * Check if the constant is a boxed value that is guaranteed to be cached by the platform.
     * Otherwise the generated code might be the only reference to the boxed value and since object
     * references from nmethods are weak this can cause GC problems.
     *
     * @param source
     * @return true if the box is cached
     */
    private static boolean isBoxCached(JavaConstant source) {
        switch (source.getJavaKind()) {
            case Boolean:
                return true;
            case Char:
                return source.asInt() <= 127;
            case Byte:
            case Short:
            case Int:
                return source.asInt() >= -128 && source.asInt() <= 127;
            case Long:
                return source.asLong() >= -128 && source.asLong() <= 127;
            case Float:
            case Double:
                return false;
            default:
                throw new IllegalArgumentException("unexpected kind " + source.getJavaKind());
        }
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        if (source == null || !source.getJavaKind().isPrimitive() || !isBoxCached(source)) {
            return null;
        }
        return HotSpotObjectConstantImpl.forObject(source.asBoxedPrimitive());
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (source == null || !source.getJavaKind().isObject()) {
            return null;
        }
        if (source.isNull()) {
            return null;
        }
        return JavaConstant.forBoxedPrimitive(((HotSpotObjectConstantImpl) source).object());
    }

    @Override
    public JavaConstant forString(String value) {
        return HotSpotObjectConstantImpl.forObject(value);
    }

    public JavaConstant forObject(Object value) {
        return HotSpotObjectConstantImpl.forObject(value);
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant instanceof HotSpotObjectConstant) {
            Object obj = ((HotSpotObjectConstantImpl) constant).object();
            if (obj instanceof Class) {
                return runtime.getHostJVMCIBackend().getMetaAccess().lookupJavaType((Class<?>) obj);
            }
        }
        if (constant instanceof HotSpotMetaspaceConstant) {
            MetaspaceWrapperObject obj = HotSpotMetaspaceConstantImpl.getMetaspaceObject(constant);
            if (obj instanceof HotSpotResolvedObjectTypeImpl) {
                return (ResolvedJavaType) obj;
            }
        }
        return null;
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        HotSpotResolvedJavaField hotspotField = (HotSpotResolvedJavaField) field;
        if (hotspotField.isStatic()) {
            HotSpotResolvedJavaType holder = (HotSpotResolvedJavaType) hotspotField.getDeclaringClass();
            if (holder.isInitialized()) {
                return memoryAccess.readFieldValue(hotspotField, holder.mirror());
            }
        } else {
            if (receiver.isNonNull()) {
                Object object = ((HotSpotObjectConstantImpl) receiver).object();
                if (hotspotField.isInObject(receiver)) {
                    return memoryAccess.readFieldValue(hotspotField, object);
                }
            }
        }
        return null;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return HotSpotObjectConstantImpl.forObject(((HotSpotResolvedJavaType) type).mirror());
    }

    @Override
    public Constant asObjectHub(ResolvedJavaType type) {
        if (type instanceof HotSpotResolvedObjectType) {
            return ((HotSpotResolvedObjectType) type).klass();
        } else {
            throw JVMCIError.unimplemented();
        }
    }
}
