/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import jdk.vm.ci.hotspot.HotSpotVMConfig.*;
import jdk.vm.ci.meta.*;

public final class HotSpotMetaspaceConstantImpl extends PrimitiveConstant implements HotSpotMetaspaceConstant, VMConstant, HotSpotProxified {

    static HotSpotMetaspaceConstantImpl forMetaspaceObject(JavaKind kind, long primitive, Object metaspaceObject, boolean compressed) {
        return new HotSpotMetaspaceConstantImpl(kind, primitive, metaspaceObject, compressed);
    }

    static Object getMetaspaceObject(Constant constant) {
        return ((HotSpotMetaspaceConstantImpl) constant).metaspaceObject;
    }

    private final Object metaspaceObject;
    private final boolean compressed;

    private HotSpotMetaspaceConstantImpl(JavaKind kind, long primitive, Object metaspaceObject, boolean compressed) {
        super(kind, primitive);
        this.metaspaceObject = metaspaceObject;
        this.compressed = compressed;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ System.identityHashCode(metaspaceObject);
    }

    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof HotSpotMetaspaceConstantImpl && super.equals(o) && Objects.equals(metaspaceObject, ((HotSpotMetaspaceConstantImpl) o).metaspaceObject));
    }

    @Override
    public String toString() {
        return super.toString() + "{" + metaspaceObject + (compressed ? ";compressed}" : "}");
    }

    public boolean isCompressed() {
        return compressed;
    }

    public JavaConstant compress(CompressEncoding encoding) {
        assert !isCompressed();
        HotSpotMetaspaceConstantImpl res = HotSpotMetaspaceConstantImpl.forMetaspaceObject(JavaKind.Int, encoding.compress(asLong()), metaspaceObject, true);
        assert res.isCompressed();
        return res;
    }

    public JavaConstant uncompress(CompressEncoding encoding) {
        assert isCompressed();
        HotSpotMetaspaceConstantImpl res = HotSpotMetaspaceConstantImpl.forMetaspaceObject(JavaKind.Long, encoding.uncompress(asInt()), metaspaceObject, false);
        assert !res.isCompressed();
        return res;
    }

    public HotSpotResolvedObjectType asResolvedJavaType() {
        if (metaspaceObject instanceof HotSpotResolvedObjectType) {
            return (HotSpotResolvedObjectType) metaspaceObject;
        }
        return null;
    }

    public HotSpotResolvedJavaMethod asResolvedJavaMethod() {
        if (metaspaceObject instanceof HotSpotResolvedJavaMethod) {
            return (HotSpotResolvedJavaMethod) metaspaceObject;
        }
        return null;
    }

    public long rawValue() {
        return asLong();
    }
}
