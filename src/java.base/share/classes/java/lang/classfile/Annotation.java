/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.lang.classfile;

import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.AnnotationImpl;
import jdk.internal.classfile.impl.TemporaryConstantPool;

import java.lang.constant.ClassDesc;
import java.util.List;
import jdk.internal.javac.PreviewFeature;

/**
 * Models an {@code annotation} structure ({@jvms 4.7.16}) or part of a {@code
 * type_annotation} structure ({@jvms 4.7.20}).
 * <p>
 * Each {@code annotation} structure denotes an annotation that applies to a
 * construct in Java source code ({@jls 9.7.4}).
 * Similarly, each {@code type_annotation} structure denotes an annotation
 * that applies to a type in Java source code.
 * In either case, the structure indicates the interface of the annotation
 * and a set of element-value pairs.
 * <p>
 * The location in the class file of an {@code annotation} structure or a
 * {@code type_annotation} structure,
 * respectively, indicates the source code construct or type, respectively, to
 * which the annotation applies.
 * Accordingly, an {@code Annotation} may represent:
 * <ul>
 * <li>A <i>declaration annotation</i> on a class, field, method, or record
 * component declaration, when an {@code annotation} structure appears in the
 * {@link RuntimeVisibleAnnotationsAttribute} or
 * {@link RuntimeInvisibleAnnotationsAttribute} of a class, field, method, or
 * record component.
 * <li>A <i>declaration annotation</i> on a method parameter declaration, when
 * an {@code annotation} structure appears in the
 * {@link RuntimeVisibleParameterAnnotationsAttribute} or
 * {@link RuntimeInvisibleParameterAnnotationsAttribute} of a method.
 * <li>The {@linkplain AnnotationValue.OfAnnotation element value} of an
 * annotation, where the type of the element value is itself an annotation
 * interface. In this case, the {@code annotation} structure appears as the
 * {@code annotation_value} item of an {@code element_value} structure
 * ({@jvms 4.7.16.1}).
 * <li>A <i>type annotation</i>, when a {@code type_annotation} structure
 * appears in the {@link RuntimeVisibleTypeAnnotationsAttribute}
 * or {@link RuntimeInvisibleTypeAnnotationsAttribute} of a class, field,
 * method, {@link CodeAttribute}, or record component.
 * </ul>
 * <p>
 * Two {@code Annotation} objects should be compared using the {@link
 * Object#equals(Object) equals} method.
 *
 * @see AnnotationElement
 * @see AnnotationValue
 * @see TypeAnnotation
 * @see RuntimeVisibleAnnotationsAttribute
 * @see RuntimeInvisibleAnnotationsAttribute
 * @see RuntimeVisibleParameterAnnotationsAttribute
 * @see RuntimeInvisibleParameterAnnotationsAttribute
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface Annotation
        permits AnnotationImpl {

    /**
     * {@return the class of the annotation}
     */
    Utf8Entry className();

    /**
     * {@return the class of the annotation, as a symbolic descriptor}
     */
    default ClassDesc classSymbol() {
        return ClassDesc.ofDescriptor(className().stringValue());
    }

    /**
     * {@return the elements of the annotation}
     */
    List<AnnotationElement> elements();

    /**
     * {@return an annotation}
     * @param annotationClass the class of the annotation
     * @param elements the elements of the annotation
     */
    static Annotation of(Utf8Entry annotationClass,
                         List<AnnotationElement> elements) {
        return new AnnotationImpl(annotationClass, elements);
    }

    /**
     * {@return an annotation}
     * @param annotationClass the class of the annotation
     * @param elements the elements of the annotation
     */
    static Annotation of(Utf8Entry annotationClass,
                         AnnotationElement... elements) {
        return of(annotationClass, List.of(elements));
    }

    /**
     * {@return an annotation}
     * @param annotationClass the class of the annotation
     * @param elements the elements of the annotation
     */
    static Annotation of(ClassDesc annotationClass,
                         List<AnnotationElement> elements) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(annotationClass.descriptorString()), elements);
    }

    /**
     * {@return an annotation}
     * @param annotationClass the class of the annotation
     * @param elements the elements of the annotation
     */
    static Annotation of(ClassDesc annotationClass,
                         AnnotationElement... elements) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(annotationClass.descriptorString()), elements);
    }
}
