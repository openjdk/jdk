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

import java.lang.classfile.Attribute;
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributeMapper.AttributeStability;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.util.Arrays;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;

/**
 * Models the {@link Attributes#permittedSubclasses() PermittedSubclasses}
 * attribute (JVMS {@jvms 4.7.31}), which indicates this class or interface
 * is {@linkplain java.compiler/javax.lang.model.element.Modifier#SEALED sealed},
 * and which classes or interfaces may extend or implement this class or
 * interface.
 * <p>
 * This attribute only appears on classes, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in a class.  It has a
 * data dependency on the {@linkplain AttributeStability#CP_REFS constant pool}.
 * <p>
 * The attribute was introduced in the Java SE Platform version 17, major
 * version {@value ClassFile#JAVA_17_VERSION}.
 *
 * @see Attributes#permittedSubclasses()
 * @see Class#isSealed()
 * @see Class#getPermittedSubclasses()
 * @jls 8.1.1.2 {@code sealed}, {@code non-sealed}, and {@code final} Classes
 * @jls 9.1.1.4 {@code sealed} and {@code non-sealed} Interfaces
 * @jvms 4.7.31 The {@code PermittedSubclasses} Attribute
 * @since 24
 */
@SuppressWarnings("doclint:reference")
public sealed interface PermittedSubclassesAttribute
        extends Attribute<PermittedSubclassesAttribute>, ClassElement
        permits BoundAttribute.BoundPermittedSubclassesAttribute, UnboundAttribute.UnboundPermittedSubclassesAttribute {

    /**
     * {@return the list of permitted subclasses or subinterfaces}
     *
     * @see Class#getPermittedSubclasses()
     */
    List<ClassEntry> permittedSubclasses();

    /**
     * {@return a {@code PermittedSubclasses} attribute}
     *
     * @param permittedSubclasses the permitted subclasses or subinterfaces
     */
    static PermittedSubclassesAttribute of(List<ClassEntry> permittedSubclasses) {
        return new UnboundAttribute.UnboundPermittedSubclassesAttribute(permittedSubclasses);
    }

    /**
     * {@return a {@code PermittedSubclasses} attribute}
     *
     * @param permittedSubclasses the permitted subclasses or subinterfaces
     */
    static PermittedSubclassesAttribute of(ClassEntry... permittedSubclasses) {
        return of(List.of(permittedSubclasses));
    }

    /**
     * {@return a {@code PermittedSubclasses} attribute}
     *
     * @param permittedSubclasses the permitted subclasses or subinterfaces
     * @throws IllegalArgumentException if any of {@code permittedSubclasses} is primitive
     */
    static PermittedSubclassesAttribute ofSymbols(List<ClassDesc> permittedSubclasses) {
        return of(Util.entryList(permittedSubclasses));
    }

    /**
     * {@return a {@code PermittedSubclasses} attribute}
     *
     * @param permittedSubclasses the permitted subclasses or subinterfaces
     * @throws IllegalArgumentException if any of {@code permittedSubclasses} is primitive
     */
    static PermittedSubclassesAttribute ofSymbols(ClassDesc... permittedSubclasses) {
        // List version does defensive copy
        return ofSymbols(Arrays.asList(permittedSubclasses));
    }
}
