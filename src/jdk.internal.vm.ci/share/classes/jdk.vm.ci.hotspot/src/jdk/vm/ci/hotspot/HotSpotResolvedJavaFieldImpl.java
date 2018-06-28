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

import static jdk.vm.ci.hotspot.HotSpotModifiers.jvmFieldModifiers;
import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import jdk.internal.vm.annotation.Stable;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Represents a field in a HotSpot type.
 */
class HotSpotResolvedJavaFieldImpl implements HotSpotResolvedJavaField {

    private final HotSpotResolvedObjectTypeImpl holder;
    private JavaType type;
    private final int offset;
    private final short index;

    /**
     * This value contains all flags as stored in the VM including internal ones.
     */
    private final int modifiers;

    HotSpotResolvedJavaFieldImpl(HotSpotResolvedObjectTypeImpl holder, JavaType type, long offset, int modifiers, int index) {
        this.holder = holder;
        this.type = type;
        this.index = (short) index;
        assert this.index == index;
        assert offset != -1;
        assert offset == (int) offset : "offset larger than int";
        this.offset = (int) offset;
        this.modifiers = modifiers;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HotSpotResolvedJavaField) {
            HotSpotResolvedJavaFieldImpl that = (HotSpotResolvedJavaFieldImpl) obj;
            if (that.offset != this.offset || that.isStatic() != this.isStatic()) {
                return false;
            } else if (this.holder.equals(that.holder)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return holder.hashCode() ^ offset;
    }

    @Override
    public int getModifiers() {
        return modifiers & jvmFieldModifiers();
    }

    @Override
    public boolean isInternal() {
        return (modifiers & config().jvmAccFieldInternal) != 0;
    }

    /**
     * Determines if a given object contains this field.
     *
     * @return true iff this is a non-static field and its declaring class is assignable from
     *         {@code object}'s class
     */
    @Override
    public boolean isInObject(JavaConstant constant) {
        if (isStatic()) {
            return false;
        }
        Object object = ((HotSpotObjectConstantImpl) constant).object();
        return getDeclaringClass().isAssignableFrom(HotSpotResolvedObjectTypeImpl.fromObjectClass(object.getClass()));
    }

    @Override
    public HotSpotResolvedObjectTypeImpl getDeclaringClass() {
        return holder;
    }

    @Override
    public String getName() {
        return holder.createFieldInfo(index).getName();
    }

    @Override
    public JavaType getType() {
        // Pull field into local variable to prevent a race causing
        // a ClassCastException below
        JavaType currentType = type;
        if (currentType instanceof UnresolvedJavaType) {
            // Don't allow unresolved types to hang around forever
            UnresolvedJavaType unresolvedType = (UnresolvedJavaType) currentType;
            ResolvedJavaType resolved = unresolvedType.resolve(holder);
            if (resolved != null) {
                type = resolved;
            }
        }
        return type;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return format("HotSpotField<%H.%n %t:") + offset + ">";
    }

    @Override
    public boolean isSynthetic() {
        return (config().jvmAccSynthetic & modifiers) != 0;
    }

    /**
     * Checks if this field has the {@link Stable} annotation.
     *
     * @return true if field has {@link Stable} annotation, false otherwise
     */
    @Override
    public boolean isStable() {
        return (config().jvmAccFieldStable & modifiers) != 0;
    }

    @Override
    public Annotation[] getAnnotations() {
        Field javaField = toJava();
        if (javaField != null) {
            return javaField.getAnnotations();
        }
        return new Annotation[0];
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        Field javaField = toJava();
        if (javaField != null) {
            return javaField.getDeclaredAnnotations();
        }
        return new Annotation[0];
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        Field javaField = toJava();
        if (javaField != null) {
            return javaField.getAnnotation(annotationClass);
        }
        return null;
    }

    private Field toJava() {
        if (isInternal()) {
            return null;
        }
        try {
            return holder.mirror().getDeclaredField(getName());
        } catch (NoSuchFieldException | NoClassDefFoundError e) {
            return null;
        }
    }
}
