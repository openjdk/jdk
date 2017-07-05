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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implementation of {@link JavaType} for unresolved HotSpot classes.
 */
final class HotSpotUnresolvedJavaType extends HotSpotJavaType {

    private final HotSpotJVMCIRuntimeProvider runtime;

    private HotSpotUnresolvedJavaType(String name, HotSpotJVMCIRuntimeProvider runtime) {
        super(name);
        assert name.charAt(0) == '[' || name.charAt(name.length() - 1) == ';' : name;
        this.runtime = runtime;
    }

    /**
     * Creates an unresolved type for a valid {@link JavaType#getName() type name}.
     */
    static HotSpotUnresolvedJavaType create(HotSpotJVMCIRuntimeProvider runtime, String name) {
        return new HotSpotUnresolvedJavaType(name, runtime);
    }

    @Override
    public JavaType getComponentType() {
        assert getName().charAt(0) == '[' : "no array class" + getName();
        return new HotSpotUnresolvedJavaType(getName().substring(1), runtime);
    }

    @Override
    public JavaType getArrayClass() {
        return new HotSpotUnresolvedJavaType('[' + getName(), runtime);
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof HotSpotUnresolvedJavaType)) {
            return false;
        }
        HotSpotUnresolvedJavaType that = (HotSpotUnresolvedJavaType) obj;
        return this.getName().equals(that.getName());
    }

    @Override
    public String toString() {
        return "HotSpotType<" + getName() + ", unresolved>";
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return (ResolvedJavaType) runtime.lookupType(getName(), (HotSpotResolvedObjectType) accessingClass, true);
    }

    /**
     * Try to find a loaded version of this class.
     *
     * @param accessingClass
     * @return the resolved class or null.
     */
    ResolvedJavaType reresolve(HotSpotResolvedObjectType accessingClass) {
        JavaType type = runtime.lookupType(getName(), accessingClass, false);
        if (type instanceof ResolvedJavaType) {
            return (ResolvedJavaType) type;
        }
        return null;
    }
}
