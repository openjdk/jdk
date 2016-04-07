/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.VMConstant;

final class HotSpotMetaspaceConstantImpl implements HotSpotMetaspaceConstant, VMConstant, HotSpotProxified {

    static HotSpotMetaspaceConstantImpl forMetaspaceObject(MetaspaceWrapperObject metaspaceObject, boolean compressed) {
        return new HotSpotMetaspaceConstantImpl(metaspaceObject, compressed);
    }

    static MetaspaceWrapperObject getMetaspaceObject(Constant constant) {
        return ((HotSpotMetaspaceConstantImpl) constant).metaspaceObject;
    }

    private final MetaspaceWrapperObject metaspaceObject;
    private final boolean compressed;

    private HotSpotMetaspaceConstantImpl(MetaspaceWrapperObject metaspaceObject, boolean compressed) {
        this.metaspaceObject = metaspaceObject;
        this.compressed = compressed;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(metaspaceObject) ^ (compressed ? 1 : 2);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof HotSpotMetaspaceConstantImpl)) {
            return false;
        }

        HotSpotMetaspaceConstantImpl other = (HotSpotMetaspaceConstantImpl) o;
        return Objects.equals(this.metaspaceObject, other.metaspaceObject) && this.compressed == other.compressed;
    }

    @Override
    public String toValueString() {
        return String.format("meta{%s%s}", metaspaceObject, compressed ? ";compressed" : "");
    }

    @Override
    public String toString() {
        return toValueString();
    }

    public boolean isDefaultForKind() {
        return false;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public Constant compress() {
        assert !isCompressed();
        HotSpotMetaspaceConstantImpl res = HotSpotMetaspaceConstantImpl.forMetaspaceObject(metaspaceObject, true);
        assert res.isCompressed();
        return res;
    }

    public Constant uncompress() {
        assert isCompressed();
        HotSpotMetaspaceConstantImpl res = HotSpotMetaspaceConstantImpl.forMetaspaceObject(metaspaceObject, false);
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

    public HotSpotSymbol asSymbol() {
        if (metaspaceObject instanceof HotSpotSymbol) {
            return (HotSpotSymbol) metaspaceObject;
        }
        return null;
    }
}
