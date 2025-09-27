/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

import java.lang.annotation.Annotation;

import static jdk.internal.misc.Unsafe.ADDRESS_SIZE;
import static jdk.vm.ci.hotspot.CompilerToVM.compilerToVM;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;
import static jdk.vm.ci.hotspot.UnsafeAccess.UNSAFE;

/**
 * Represents a field in a HotSpot type.
 */
class HotSpotResolvedJavaFieldImpl implements HotSpotResolvedJavaField {

    private final HotSpotResolvedObjectTypeImpl holder;
    private JavaType type;

    /**
     * Offset (in bytes) of field from start of its storage container (i.e. {@code instanceOop} or
     * {@code Klass*}).
     */
    private final int offset;

    /**
     * Value of {@code fieldDescriptor::index()}.
     */
    private final int index;

    /**
     * This value contains all flags from the class file
     */
    private final int classfileFlags;

    /**
     * This value contains VM internal flags
     */
    private final int internalFlags;

    HotSpotResolvedJavaFieldImpl(HotSpotResolvedObjectTypeImpl holder, JavaType type, int offset, int classfileFlags, int internalFlags, int index) {
        this.holder = holder;
        this.type = type;
        this.offset = offset;
        this.classfileFlags = classfileFlags;
        this.internalFlags = internalFlags;
        this.index = index;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof HotSpotResolvedJavaFieldImpl that) {
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
        return classfileFlags & HotSpotModifiers.jvmFieldModifiers();
    }

    @Override
    public boolean isInternal() {
        return (internalFlags & (1 << config().jvmFieldFlagInternalShift)) != 0;
    }

    /**
     * Determines if a given object contains this field.
     *
     * @return true iff this is a non-static field and its declaring class is assignable from
     *         {@code object}'s class
     */
    @Override
    public boolean isInObject(JavaConstant object) {
        if (isStatic()) {
            return false;
        }
        HotSpotObjectConstant constant = (HotSpotObjectConstant) object;
        return getDeclaringClass().isAssignableFrom(constant.getType());
    }

    @Override
    public HotSpotResolvedObjectTypeImpl getDeclaringClass() {
        return holder;
    }

    @Override
    public String getName() {
        return holder.getFieldInfo(index).getName(holder);
    }

    @Override
    public JavaType getType() {
        // Pull field into local variable to prevent a race causing
        // a ClassCastException below
        JavaType currentType = type;
        if (currentType instanceof UnresolvedJavaType unresolvedType) {
            // Don't allow unresolved types to hang around forever
            JavaType resolved = HotSpotJVMCIRuntime.runtime().lookupType(unresolvedType.getName(), holder, false);
            if (resolved instanceof ResolvedJavaType) {
                type = resolved;
            }
        }
        return type;

    }

    /**
     * Gets the offset (in bytes) of field from start of its storage container (i.e.
     * {@code instanceOop} or {@code Klass*}).
     */
    @Override
    public int getOffset() {
        return offset;
    }

    /**
     * Gets the value of this field's index (i.e. {@code fieldDescriptor::index()} in the encoded
     * fields of the declaring class.
     */
    int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        return format("HotSpotResolvedJavaFieldImpl<%H.%n %t:") + offset + ">";
    }

    @Override
    public boolean isSynthetic() {
        return (config().jvmAccSynthetic & classfileFlags) != 0;
    }

    /**
     * Checks if this field has the {@code Stable} annotation.
     *
     * @return true if field has {@code Stable} annotation, false otherwise
     */
    @Override
    public boolean isStable() {
        return (1 << (config().jvmFieldFlagStableShift) & internalFlags) != 0;
    }

    /**
     * Returns whether this field has type annotations ({@code typeAnnotations == true}) or
     * declared annotations ({@code typeAnnotations == false}).
     */
    private boolean hasAnnotations(boolean typeAnnotations) {
        if (!isInternal()) {
            HotSpotVMConfig config = config();
            final long metaspaceAnnotations = UNSAFE.getAddress(holder.getKlassPointer() + config.instanceKlassAnnotationsOffset);
            if (metaspaceAnnotations != 0) {
                int annotationsOffset = typeAnnotations? config.annotationsFieldTypeAnnotationsOffset: config.annotationsFieldAnnotationsOffset;
                long fieldsAnnotations = UNSAFE.getAddress(metaspaceAnnotations + annotationsOffset);
                if (fieldsAnnotations != 0) {
                    int indexOffset = ADDRESS_SIZE * index;
                    long fieldAnnotations = UNSAFE.getAddress(fieldsAnnotations + config.annotationArrayArrayBaseOffset + indexOffset);
                    return fieldAnnotations != 0;
                }
            }
        }
        return false;
    }

    @Override
    public Annotation[] getAnnotations() {
        if (!hasAnnotations(false)) {
            return new Annotation[0];
        }
        return runtime().reflection.getFieldAnnotations(this);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        if (!hasAnnotations(false)) {
            return new Annotation[0];
        }
        return runtime().reflection.getFieldDeclaredAnnotations(this);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (!hasAnnotations(false)) {
            return null;
        }
        return runtime().reflection.getFieldAnnotation(this, annotationClass);
    }

    @Override
    public JavaConstant getConstantValue() {
        return holder.getFieldInfo(index).getConstantValue(holder);
    }

    @Override
    public AnnotationsInfo getDeclaredAnnotationInfo() {
        if (!hasAnnotations(false)) {
            return null;
        }
        byte[] bytes = compilerToVM().getRawAnnotationBytes('f', holder, holder.getKlassPointer(), index, CompilerToVM.DECLARED_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getDeclaringClass().getConstantPool(), getDeclaringClass());
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        if (!hasAnnotations(true)) {
            return null;
        }
        byte[] bytes = compilerToVM().getRawAnnotationBytes('f', holder, holder.getKlassPointer(), index, CompilerToVM.TYPE_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getDeclaringClass().getConstantPool(), getDeclaringClass());
    }
}
