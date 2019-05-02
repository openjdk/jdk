/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * HotSpot implementation of {@link MemoryAccessProvider}.
 */
class HotSpotMemoryAccessProviderImpl implements HotSpotMemoryAccessProvider {

    protected final HotSpotJVMCIRuntime runtime;

    HotSpotMemoryAccessProviderImpl(HotSpotJVMCIRuntime runtime) {
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
    private static HotSpotObjectConstantImpl asObject(Constant base, JavaKind kind, long displacement) {
        if (base instanceof HotSpotObjectConstantImpl) {
            HotSpotObjectConstantImpl constant = (HotSpotObjectConstantImpl) base;
            HotSpotResolvedObjectType type = constant.getType();
            runtime().reflection.checkRead(constant, kind, displacement, type);
            return constant;
        }
        return null;
    }

    private boolean isValidObjectFieldDisplacement(Constant base, long displacement) {
        if (base instanceof HotSpotMetaspaceConstant) {
            MetaspaceObject metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                if (displacement == runtime.getConfig().javaMirrorOffset) {
                    // Klass::_java_mirror is valid for all Klass* values
                    return true;
                }
            } else {
                throw new IllegalArgumentException(String.valueOf(metaspaceObject));
            }
        }
        return false;
    }

    private static long asRawPointer(Constant base) {
        if (base instanceof HotSpotMetaspaceConstantImpl) {
            MetaspaceObject meta = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            return meta.getMetaspacePointer();
        } else if (base instanceof PrimitiveConstant) {
            PrimitiveConstant prim = (PrimitiveConstant) base;
            if (prim.getJavaKind().isNumericInteger()) {
                return prim.asLong();
            }
        }
        throw new IllegalArgumentException(String.valueOf(base));
    }

    private static long readRawValue(Constant baseConstant, long displacement, JavaKind kind, int bits) {
        HotSpotObjectConstantImpl base = asObject(baseConstant, kind, displacement);
        if (base != null) {
            switch (bits) {
                case Byte.SIZE:
                    return runtime().reflection.getByte(base, displacement);
                case Short.SIZE:
                    return runtime().reflection.getShort(base, displacement);
                case Integer.SIZE:
                    return runtime().reflection.getInt(base, displacement);
                case Long.SIZE:
                    return runtime().reflection.getLong(base, displacement);
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

    private boolean verifyReadRawObject(JavaConstant expected, Constant base, long displacement) {
        if (base instanceof HotSpotMetaspaceConstant) {
            MetaspaceObject metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                if (displacement == runtime.getConfig().javaMirrorOffset) {
                    HotSpotResolvedObjectTypeImpl type = (HotSpotResolvedObjectTypeImpl) metaspaceObject;
                    assert expected.equals(type.getJavaMirror());
                }
            }
        }
        return true;
    }

    private JavaConstant readRawObject(Constant baseConstant, long initialDisplacement, boolean compressed) {
        long displacement = initialDisplacement;
        JavaConstant ret;
        HotSpotObjectConstantImpl base = asObject(baseConstant, JavaKind.Object, displacement);
        if (base == null) {
            assert !compressed;
            displacement += asRawPointer(baseConstant);
            ret = runtime.getCompilerToVM().readUncompressedOop(displacement);
            assert verifyReadRawObject(ret, baseConstant, initialDisplacement);
        } else {
            assert runtime.getConfig().useCompressedOops == compressed;
            ret = runtime.getCompilerToVM().getObject(base, displacement);
        }
        return ret == null ? JavaConstant.NULL_POINTER : ret;
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
            return readRawObject(base, displacement, runtime.getConfig().useCompressedOops);
        }
        if (!isValidObjectFieldDisplacement(base, displacement)) {
            return null;
        }
        if (base instanceof HotSpotMetaspaceConstant &&
            displacement == runtime.getConfig().javaMirrorOffset) {
            MetaspaceObject metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            return ((HotSpotResolvedObjectTypeImpl) metaspaceObject).getJavaMirror();
        }
        return readRawObject(base, displacement, false);
    }

    @Override
    public JavaConstant readNarrowOopConstant(Constant base, long displacement) {
        JavaConstant res = readRawObject(base, displacement, true);
        return JavaConstant.NULL_POINTER.equals(res) ? HotSpotCompressedNullConstant.COMPRESSED_NULL : ((HotSpotObjectConstant) res).compress();
    }

    private HotSpotResolvedObjectTypeImpl readKlass(Constant base, long displacement, boolean compressed) {
        assert (base instanceof HotSpotMetaspaceConstantImpl) || (base instanceof HotSpotObjectConstantImpl) : base.getClass();
        if (base instanceof HotSpotMetaspaceConstantImpl) {
            return runtime.getCompilerToVM().getResolvedJavaType((HotSpotResolvedObjectTypeImpl) ((HotSpotMetaspaceConstantImpl) base).asResolvedJavaType(), displacement, compressed);
        } else {
            return runtime.getCompilerToVM().getResolvedJavaType(((HotSpotObjectConstantImpl) base), displacement, compressed);
        }
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
        HotSpotResolvedJavaMethodImpl method = runtime.getCompilerToVM().getResolvedJavaMethod((HotSpotObjectConstantImpl) base, displacement);
        return HotSpotMetaspaceConstantImpl.forMetaspaceObject(method, false);
    }
}
