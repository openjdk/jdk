/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.meta;

/**
 * Implementation of {@link JavaType} for unresolved HotSpot classes.
 */
public final class UnresolvedJavaType implements JavaType {
    private final String name;

    /**
     * The reason type resolution failed. Can be null.
     */
    private final Throwable cause;

    @Override
    public String getName() {
        return name;
    }

    private UnresolvedJavaType(String name, Throwable cause) {
        this.name = name;
        this.cause = cause;
        assert name.length() == 1 && JavaKind.fromPrimitiveOrVoidTypeChar(name.charAt(0)) != null || name.charAt(0) == '[' || name.charAt(name.length() - 1) == ';' : name;
    }

    /**
     * Creates an unresolved type for a valid {@link JavaType#getName() type name}.
     */
    public static UnresolvedJavaType create(String name) {
        return new UnresolvedJavaType(name, null);
    }

    /**
     * Creates an unresolved type for a valid {@link JavaType#getName() type name}.
     */
    public static UnresolvedJavaType create(String name, Throwable cause) {
        return new UnresolvedJavaType(name, cause);
    }

    /**
     * Gets the exception, if any, representing the reason type resolution resulted in this object.
     */
    public Throwable getCause() {
        return cause;
    }

    @Override
    public JavaType getComponentType() {
        if (getName().charAt(0) == '[') {
            return new UnresolvedJavaType(getName().substring(1), null);
        }
        return null;
    }

    @Override
    public JavaType getArrayClass() {
        return new UnresolvedJavaType('[' + getName(), null);
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
        if (obj == null || !(obj instanceof UnresolvedJavaType)) {
            return false;
        }
        UnresolvedJavaType that = (UnresolvedJavaType) obj;
        return this.getName().equals(that.getName());
    }

    @Override
    public String toString() {
        return "UnresolvedJavaType<" + getName() + ">";
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return accessingClass.lookupType(this, true);
    }
}
