/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayIndexScale;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

import java.lang.reflect.Array;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * HotSpot implementation of {@link MemoryAccessProvider}.
 */
class HotSpotMemoryAccessProviderImpl implements HotSpotMemoryAccessProvider {

    protected final HotSpotJVMCIRuntimeProvider runtime;

    HotSpotMemoryAccessProviderImpl(HotSpotJVMCIRuntimeProvider runtime) {
        this.runtime = runtime;
    }

    /**
     * Gets the object boxed by {@code base} that is about to have a value of kind {@code kind} read
     * from it at the offset {@code displacement}.
     *
     * @param base constant value containing the base address for a pending read
     * @return {@code null} if {@code base} does not box an object otherwise the object boxed in
     *         {@code base}
     */
    private Object asObject(Constant base, JavaKind kind, long displacement) {
        if (base instanceof HotSpotObjectConstantImpl) {
            HotSpotObjectConstantImpl constant = (HotSpotObjectConstantImpl) base;
            HotSpotResolvedObjectType type = constant.getType();
            Object object = constant.object();
            checkRead(kind, displacement, type, object);
            return object;
        }
        return null;
    }

    /**
     * Offset of injected {@code java.lang.Class::oop_size} field. No need to make {@code volatile}
     * as initialization is idempotent.
     */
    private long oopSizeOffset;

    private static int computeOopSizeOffset(HotSpotJVMCIRuntimeProvider runtime) {
        MetaAccessProvider metaAccess = runtime.getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaType staticType = metaAccess.lookupJavaType(Class.class);
        for (ResolvedJavaField f : staticType.getInstanceFields(false)) {
            if (f.getName().equals("oop_size")) {
                int offset = ((HotSpotResolvedJavaField) f).offset();
                assert offset != 0 : "not expecting offset of java.lang.Class::oop_size to be 0";
                return offset;
            }
        }
        throw new JVMCIError("Could not find injected java.lang.Class::oop_size field");
    }

    private boolean checkRead(JavaKind kind, long displacement, HotSpotResolvedObjectType type, Object object) {
        if (type.isArray()) {
            ResolvedJavaType componentType = type.getComponentType();
            JavaKind componentKind = componentType.getJavaKind();
            final int headerSize = getArrayBaseOffset(componentKind);
            int sizeOfElement = getArrayIndexScale(componentKind);
            int length = Array.getLength(object);
            long arrayEnd = headerSize + (sizeOfElement * length);
            boolean aligned = ((displacement - headerSize) % sizeOfElement) == 0;
            if (displacement < 0 || displacement > (arrayEnd - sizeOfElement) || (kind == JavaKind.Object && !aligned)) {
                int index = (int) ((displacement - headerSize) / sizeOfElement);
                throw new IllegalArgumentException("Unsafe array access: reading element of kind " + kind +
                                " at offset " + displacement + " (index ~ " + index + ") in " +
                                type.toJavaName() + " object of length " + length);
            }
        } else if (kind != JavaKind.Object) {
            long size;
            if (object instanceof Class) {
                if (oopSizeOffset == 0) {
                    oopSizeOffset = computeOopSizeOffset(runtime);
                }
                int wordSize = runtime.getHostJVMCIBackend().getCodeCache().getTarget().wordSize;
                size = UNSAFE.getInt(object, oopSizeOffset) * wordSize;
            } else {
                size = Math.abs(type.instanceSize());
            }
            int bytesToRead = kind.getByteCount();
            if (displacement + bytesToRead > size || displacement < 0) {
                throw new IllegalArgumentException("Unsafe access: reading " + bytesToRead + " bytes at offset " + displacement + " in " +
                                type.toJavaName() + " object of size " + size);
            }
        } else {
            ResolvedJavaField field = type.findInstanceFieldWithOffset(displacement, JavaKind.Object);
            if (field == null && object instanceof Class) {
                // Read of a static field
                MetaAccessProvider metaAccess = runtime.getHostJVMCIBackend().getMetaAccess();
                HotSpotResolvedObjectTypeImpl staticFieldsHolder = (HotSpotResolvedObjectTypeImpl) metaAccess.lookupJavaType((Class<?>) object);
                field = staticFieldsHolder.findStaticFieldWithOffset(displacement, JavaKind.Object);
            }
            if (field == null) {
                throw new IllegalArgumentException("Unsafe object access: field not found for read of kind Object" +
                                " at offset " + displacement + " in " + type.toJavaName() + " object");
            }
            if (field.getJavaKind() != JavaKind.Object) {
                throw new IllegalArgumentException("Unsafe object access: field " + field.format("%H.%n:%T") + " not of expected kind Object" +
                                " at offset " + displacement + " in " + type.toJavaName() + " object");
            }
        }
        return true;
    }

    private static long asRawPointer(Constant base) {
        if (base instanceof HotSpotMetaspaceConstantImpl) {
            MetaspaceWrapperObject meta = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            return meta.getMetaspacePointer();
        } else if (base instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) base;
            if (prim.getJavaKind().isNumericInteger()) {
                return prim.asLong();
            }
        }
        throw new IllegalArgumentException(String.valueOf(base));
    }

    private long readRawValue(Constant baseConstant, long displacement, JavaKind kind, int bits) {
        Object base = asObject(baseConstant, kind, displacement);
        if (base != null) {
            switch (bits) {
                case Byte.SIZE:
                    return UNSAFE.getByte(base, displacement);
                case Short.SIZE:
                    return UNSAFE.getShort(base, displacement);
                case Integer.SIZE:
                    return UNSAFE.getInt(base, displacement);
                case Long.SIZE:
                    return UNSAFE.getLong(base, displacement);
                default:
                    throw new IllegalArgumentException(String.valueOf(bits));
            }
        } else {
            long pointer = asRawPointer(baseConstant);
            switch (bits) {
                case Byte.SIZE:
                    return UNSAFE.getByte(pointer + displacement);
                case Short.SIZE:
                    return UNSAFE.getShort(pointer + displacement);
                case Integer.SIZE:
                    return UNSAFE.getInt(pointer + displacement);
                case Long.SIZE:
                    return UNSAFE.getLong(pointer + displacement);
                default:
                    throw new IllegalArgumentException(String.valueOf(bits));
            }
        }
    }

    private boolean verifyReadRawObject(Object expected, Constant base, long displacement) {
        if (base instanceof HotSpotMetaspaceConstant) {
            MetaspaceWrapperObject metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                if (displacement == runtime.getConfig().classMirrorHandleOffset) {
                    assert expected == ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror();
                }
            }
        }
        return true;
    }

    private Object readRawObject(Constant baseConstant, long initialDisplacement, boolean compressed) {
        long displacement = initialDisplacement;
        Object ret;
        Object base = asObject(baseConstant, JavaKind.Object, displacement);
        if (base == null) {
            assert !compressed;
            displacement += asRawPointer(baseConstant);
            ret = UNSAFE.getUncompressedObject(displacement);
            assert verifyReadRawObject(ret, baseConstant, initialDisplacement);
        } else {
            assert runtime.getConfig().useCompressedOops == compressed;
            ret = UNSAFE.getObject(base, displacement);
        }
        return ret;
    }

    JavaConstant readFieldValue(HotSpotResolvedJavaField field, Object obj) {
        assert obj != null;
        assert !field.isStatic() || obj instanceof Class;
        long displacement = field.offset();
        assert checkRead(field.getJavaKind(), displacement, (HotSpotResolvedObjectType) runtime.getHostJVMCIBackend().getMetaAccess().lookupJavaType(obj.getClass()), obj);
        if (field.getJavaKind() == JavaKind.Object) {
            Object o = UNSAFE.getObject(obj, displacement);
            return HotSpotObjectConstantImpl.forObject(o);
        } else {
            JavaKind kind = field.getJavaKind();
            switch (kind) {
                case Boolean:
                    return JavaConstant.forBoolean(UNSAFE.getBoolean(obj, displacement));
                case Byte:
                    return JavaConstant.forByte(UNSAFE.getByte(obj, displacement));
                case Char:
                    return JavaConstant.forChar(UNSAFE.getChar(obj, displacement));
                case Short:
                    return JavaConstant.forShort(UNSAFE.getShort(obj, displacement));
                case Int:
                    return JavaConstant.forInt(UNSAFE.getInt(obj, displacement));
                case Long:
                    return JavaConstant.forLong(UNSAFE.getLong(obj, displacement));
                case Float:
                    return JavaConstant.forFloat(UNSAFE.getFloat(obj, displacement));
                case Double:
                    return JavaConstant.forDouble(UNSAFE.getDouble(obj, displacement));
                default:
                    throw new IllegalArgumentException("Unsupported kind: " + kind);
            }
        }
    }

    @Override
    public JavaConstant readPrimitiveConstant(JavaKind kind, Constant baseConstant, long initialDisplacement, int bits) {
        try {
            long rawValue = readRawValue(baseConstant, initialDisplacement, kind, bits);
            switch (kind) {
                case Boolean:
                    return JavaConstant.forBoolean(rawValue != 0);
                case Byte:
                    return JavaConstant.forByte((byte) rawValue);
                case Char:
                    return JavaConstant.forChar((char) rawValue);
                case Short:
                    return JavaConstant.forShort((short) rawValue);
                case Int:
                    return JavaConstant.forInt((int) rawValue);
                case Long:
                    return JavaConstant.forLong(rawValue);
                case Float:
                    return JavaConstant.forFloat(Float.intBitsToFloat((int) rawValue));
                case Double:
                    return JavaConstant.forDouble(Double.longBitsToDouble(rawValue));
                default:
                    throw new IllegalArgumentException("Unsupported kind: " + kind);
            }
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public JavaConstant readObjectConstant(Constant base, long displacement) {
        if (base instanceof HotSpotObjectConstantImpl) {
            Object o = readRawObject(base, displacement, runtime.getConfig().useCompressedOops);
            return HotSpotObjectConstantImpl.forObject(o);
        }
        if (base instanceof HotSpotMetaspaceConstant) {
            MetaspaceWrapperObject metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                 if (displacement == runtime.getConfig().classMirrorHandleOffset) {
                    // Klass::_java_mirror is valid for all Klass* values
                    return HotSpotObjectConstantImpl.forObject(((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror());
                 }
             } else {
                 throw new IllegalArgumentException(String.valueOf(metaspaceObject));
             }
        }
        return null;
    }

    @Override
    public JavaConstant readNarrowOopConstant(Constant base, long displacement) {
        return HotSpotObjectConstantImpl.forObject(readRawObject(base, displacement, true), true);
    }

    private HotSpotResolvedObjectTypeImpl readKlass(Constant base, long displacement, boolean compressed) {
        assert (base instanceof HotSpotMetaspaceConstantImpl) || (base instanceof HotSpotObjectConstantImpl) : base.getClass();
        Object baseObject = (base instanceof HotSpotMetaspaceConstantImpl) ? ((HotSpotMetaspaceConstantImpl) base).asResolvedJavaType() : ((HotSpotObjectConstantImpl) base).object();
        return runtime.getCompilerToVM().getResolvedJavaType(baseObject, displacement, compressed);
    }

    @Override
    public Constant readKlassPointerConstant(Constant base, long displacement) {
        HotSpotResolvedObjectTypeImpl klass = readKlass(base, displacement, false);
        if (klass == null) {
            return JavaConstant.NULL_POINTER;
        }
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(klass, false);
    }

    @Override
    public Constant readNarrowKlassPointerConstant(Constant base, long displacement) {
        HotSpotResolvedObjectTypeImpl klass = readKlass(base, displacement, true);
        if (klass == null) {
            return HotSpotCompressedNullConstant.COMPRESSED_NULL;
        }
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(klass, true);
    }

    @Override
    public Constant readMethodPointerConstant(Constant base, long displacement) {
        assert (base instanceof HotSpotObjectConstantImpl);
        Object baseObject = ((HotSpotObjectConstantImpl) base).object();
        HotSpotResolvedJavaMethodImpl method = runtime.getCompilerToVM().getResolvedJavaMethod(baseObject, displacement);
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(method, false);
    }
}
