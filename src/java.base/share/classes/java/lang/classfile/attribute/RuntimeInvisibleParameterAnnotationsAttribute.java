/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.classfile.attribute;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.classfile.Annotation;
import java.lang.classfile.Attribute;
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributeMapper.AttributeStability;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodElement;
import java.lang.reflect.AnnotatedElement;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#runtimeInvisibleParameterAnnotations()
 * RuntimeInvisibleParameterAnnotations} attribute (JVMS {@jvms 4.7.19}), which
 * stores declaration annotations on the method parameters of this method
 * that are visible to {@code class} file consumers but are not visible to
 * {@linkplain AnnotatedElement core reflection}.
 * <p>
 * This attribute only appears on methods, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in a method.  It has a
 * data dependency on the {@linkplain AttributeStability#CP_REFS constant pool}.
 * <p>
 * The attribute was introduced in the Java SE Platform version 5.0, major
 * version {@value ClassFile#JAVA_5_VERSION}.
 *
 * @see Attributes#runtimeInvisibleParameterAnnotations()
 * @see ElementType#PARAMETER
 * @see RetentionPolicy#CLASS
 * @jvms 4.7.19 The {@code RuntimeInvisibleParameterAnnotations} Attribute
 * @since 24
 */
public sealed interface RuntimeInvisibleParameterAnnotationsAttribute
        extends Attribute<RuntimeInvisibleParameterAnnotationsAttribute>, MethodElement
        permits BoundAttribute.BoundRuntimeInvisibleParameterAnnotationsAttribute,
                UnboundAttribute.UnboundRuntimeInvisibleParameterAnnotationsAttribute {

    /**
     * {@return the list of run-time invisible annotations on the method parameters}
     * The element at the i'th index corresponds to the annotations on the i'th
     * formal parameter, but note that some synthetic or implicit parameters
     * may be omitted by this list.  If a parameter has no annotations, that
     * element is left empty, but is not omitted; thus, the list will never be
     * truncated because trailing parameters are not annotated.
     *
     * @see java.lang.reflect##LanguageJvmModel Java programming language and
     *      JVM modeling in core reflection
     */
    List<List<Annotation>> parameterAnnotations();

    /**
     * {@return a {@code RuntimeInvisibleParameterAnnotations} attribute}
     * The {@code parameterAnnotations} list should not be truncated, and must
     * have a length equal to the number of formal parameters; elements for
     * unannotated parameters may be empty, but may not be omitted.  It may omit
     * some synthetic or implicit parameters.
     *
     * @param parameterAnnotations a list of run-time invisible annotations for each parameter
     */
    static RuntimeInvisibleParameterAnnotationsAttribute of(List<List<Annotation>> parameterAnnotations) {
        return new UnboundAttribute.UnboundRuntimeInvisibleParameterAnnotationsAttribute(parameterAnnotations);
    }
}
