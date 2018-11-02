/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaConstant;

/**
 * Represents a constant that was retrieved from a constant pool. Used to keep track of the constant
 * pool slot for the constant.
 */
public final class HotSpotConstantPoolObject extends HotSpotObjectConstantImpl {

    static JavaConstant forObject(HotSpotResolvedObjectType type, int cpi, Object object) {
        return new HotSpotConstantPoolObject(type, cpi, object);
    }

    public static JavaConstant forObject(HotSpotResolvedObjectType type, int cpi, JavaConstant object) {
        return forObject(type, cpi, ((HotSpotObjectConstantImpl) object).object());
    }

    private final HotSpotResolvedObjectType type;
    private final int cpi;

    public HotSpotResolvedObjectType getCpType() {
        return type;
    }

    public int getCpi() {
        return cpi;
    }

    HotSpotConstantPoolObject(HotSpotResolvedObjectType type, int cpi, Object object) {
        super(object, false);
        this.type = type;
        this.cpi = cpi;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HotSpotConstantPoolObject) {
            if (super.equals(o)) {
                HotSpotConstantPoolObject other = (HotSpotConstantPoolObject) o;
                return type.equals(other.type) && cpi == other.cpi;
            }
        }
        return false;
    }

    @Override
    public String toValueString() {
        return getCpType().getName() + getCpi();
    }

    @Override
    public String toString() {
        return super.toString() + "@" + toValueString();
    }

}
