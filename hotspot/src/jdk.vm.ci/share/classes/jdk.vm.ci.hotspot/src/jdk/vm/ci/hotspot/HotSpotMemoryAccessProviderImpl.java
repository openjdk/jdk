/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotVMConfig.CompressEncoding;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * HotSpot implementation of {@link MemoryAccessProvider}.
 */
class HotSpotMemoryAccessProviderImpl implements HotSpotMemoryAccessProvider, HotSpotProxified {

    protected final HotSpotJVMCIRuntimeProvider runtime;

    HotSpotMemoryAccessProviderImpl(HotSpotJVMCIRuntimeProvider runtime) {
        this.runtime = runtime;
    }

    private static Object asObject(Constant base) {
        if (base instanceof HotSpotObjectConstantImpl) {
            return ((HotSpotObjectConstantImpl) base).object();
        } else {
            return null;
        }
    }

    private boolean isValidObjectFieldDisplacement(Constant base, long displacement) {
        if (base instanceof HotSpotMetaspaceConstant) {
            MetaspaceWrapperObject metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                if (displacement == runtime.getConfig().classMirrorOffset) {
                    // Klass::_java_mirror is valid for all Klass* values
                    return true;
                }
            } else {
                throw new JVMCIError("%s", metaspaceObject);
            }
        }
        return false;
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
        throw new JVMCIError("%s", base);
    }

    private static long readRawValue(Constant baseConstant, long displacement, int bits) {
        Object base = asObject(baseConstant);
        if (base != null) {
            switch (bits) {
                case 8:
                    return UNSAFE.getByte(base, displacement);
                case 16:
                    return UNSAFE.getShort(base, displacement);
                case 32:
                    return UNSAFE.getInt(base, displacement);
                case 64:
                    return UNSAFE.getLong(base, displacement);
                default:
                    throw new JVMCIError("%d", bits);
            }
        } else {
            long pointer = asRawPointer(baseConstant);
            switch (bits) {
                case 8:
                    return UNSAFE.getByte(pointer + displacement);
                case 16:
                    return UNSAFE.getShort(pointer + displacement);
                case 32:
                    return UNSAFE.getInt(pointer + displacement);
                case 64:
                    return UNSAFE.getLong(pointer + displacement);
                default:
                    throw new JVMCIError("%d", bits);
            }
        }
    }

    private boolean verifyReadRawObject(Object expected, Constant base, long displacement, boolean compressed) {
        if (compressed == runtime.getConfig().useCompressedOops) {
            Object obj = asObject(base);
            if (obj != null) {
                assert expected == UNSAFE.getObject(obj, displacement) : "readUnsafeOop doesn't agree with unsafe.getObject";
            }
        }
        if (base instanceof HotSpotMetaspaceConstant) {
            MetaspaceWrapperObject metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(base);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                if (displacement == runtime.getConfig().classMirrorOffset) {
                    assert expected == ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror();
                }
            }
        }
        return true;
    }

    private Object readRawObject(Constant baseConstant, long initialDisplacement, boolean compressed) {
        long displacement = initialDisplacement;

        Object ret;
        Object base = asObject(baseConstant);
        if (base == null) {
            assert !compressed;
            displacement += asRawPointer(baseConstant);
            ret = runtime.getCompilerToVM().readUncompressedOop(displacement);
        } else {
            assert runtime.getConfig().useCompressedOops == compressed;
            ret = UNSAFE.getObject(base, displacement);
        }
        assert verifyReadRawObject(ret, baseConstant, initialDisplacement, compressed);
        return ret;
    }

    @Override
    public JavaConstant readUnsafeConstant(JavaKind kind, JavaConstant baseConstant, long displacement) {
        if (kind == JavaKind.Object) {
            Object o = readRawObject(baseConstant, displacement, runtime.getConfig().useCompressedOops);
            return HotSpotObjectConstantImpl.forObject(o);
        } else {
            return readPrimitiveConstant(kind, baseConstant, displacement, kind.getByteCount() * 8);
        }
    }

    @Override
    public JavaConstant readPrimitiveConstant(JavaKind kind, Constant baseConstant, long initialDisplacement, int bits) {
        try {
            long rawValue = readRawValue(baseConstant, initialDisplacement, bits);
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
                    throw new JVMCIError("Unsupported kind: %s", kind);
            }
        } catch (NullPointerException e) {
            return null;
        }
    }

    @Override
    public JavaConstant readObjectConstant(Constant base, long displacement) {
        if (!isValidObjectFieldDisplacement(base, displacement)) {
            return null;
        }
        return HotSpotObjectConstantImpl.forObject(readRawObject(base, displacement, false));
    }

    @Override
    public JavaConstant readNarrowOopConstant(Constant base, long displacement, CompressEncoding encoding) {
        assert encoding.equals(runtime.getConfig().getOopEncoding()) : "unexpected oop encoding: " + encoding + " != " + runtime.getConfig().getOopEncoding();
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
    public Constant readNarrowKlassPointerConstant(Constant base, long displacement, CompressEncoding encoding) {
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
