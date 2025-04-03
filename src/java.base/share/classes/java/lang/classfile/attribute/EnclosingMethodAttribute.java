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
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.Optional;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;

/**
 * Models the {@link Attributes#enclosingMethod() EnclosingMethod} attribute
 * (JVMS {@jvms 4.7.7}), which indicates that this class is a local or
 * anonymous class, and indicates the enclosing method or constructor of this
 * class if this class is enclosed in exactly one method or constructor.
 * <p>
 * This attribute only appears on classes, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in a class.  It has a
 * data dependency on the {@linkplain AttributeStability#CP_REFS constant pool}.
 * <p>
 * The attribute was introduced in the Java SE Platform version 5.0, major
 * version {@value ClassFile#JAVA_5_VERSION}.
 *
 * @see Attributes#enclosingMethod()
 * @jvms 4.7.7 The {@code EnclosingMethod} Attribute
 * @since 24
 */
public sealed interface EnclosingMethodAttribute
        extends Attribute<EnclosingMethodAttribute>, ClassElement
        permits BoundAttribute.BoundEnclosingMethodAttribute,
                UnboundAttribute.UnboundEnclosingMethodAttribute {

    /**
     * {@return the class that encloses the declaration of the current
     * class}  If the {@link #enclosingMethod()} is present, this is the
     * declaring class of that enclosing method or constructor.
     *
     * @see Class#getEnclosingClass()
     */
    ClassEntry enclosingClass();

    /**
     * {@return the name and type of the enclosing method, if the class is
     * immediately enclosed by exactly one method or constructor}  This may
     * be empty if the anonymous or local class appears in a field initializer
     * (JLS {@jls 8.3.2}), an instance initializer (JLS {@jls 8.6}), or a static
     * initializer (JLS {@jls 8.7}).  As a result, this never describes a class
     * initialization method {@value ConstantDescs#CLASS_INIT_NAME}.
     *
     * @see Class#getEnclosingMethod()
     * @see Class#getEnclosingConstructor()
     */
    Optional<NameAndTypeEntry> enclosingMethod();

    /**
     * {@return the name of the enclosing method, if the class is immediately
     * enclosed by exactly one method or constructor}
     *
     * @see #enclosingMethod()
     */
    default Optional<Utf8Entry> enclosingMethodName() {
        return enclosingMethod().map(NameAndTypeEntry::name);
    }

    /**
     * {@return the name of the enclosing method, if the class is immediately
     * enclosed by exactly one method or constructor}
     *
     * @see #enclosingMethod()
     */
    default Optional<Utf8Entry> enclosingMethodType() {
        return enclosingMethod().map(NameAndTypeEntry::type);
    }

    /**
     * {@return the name of the enclosing method, if the class is immediately
     * enclosed by exactly one method or constructor}
     *
     * @see #enclosingMethod()
     */
    default Optional<MethodTypeDesc> enclosingMethodTypeSymbol() {
        return enclosingMethodType().map(Util::methodTypeSymbol);
    }

    /**
     * {@return an {@code EnclosingMethod} attribute}
     * @param className the class name
     * @param method the name and type of the enclosing method or {@code Optional.empty()} if
     *               the class is not immediately enclosed by exactly one method or constructor
     */
    static EnclosingMethodAttribute of(ClassEntry className,
                                       Optional<NameAndTypeEntry> method) {
        return new UnboundAttribute.UnboundEnclosingMethodAttribute(className, method.orElse(null));
    }

    /**
     * {@return an {@code EnclosingMethod} attribute}
     * @param className the class name
     * @param methodName the name of the enclosing method or {@code Optional.empty()} if
     *                   the class is not immediately enclosed by exactly one method or constructor
     * @param methodType the type of the enclosing method or {@code Optional.empty()} if
     *                   the class is not immediately enclosed by exactly one method or constructor
     * @throws IllegalArgumentException if {@code className} represents a primitive type
     */
    static EnclosingMethodAttribute of(ClassDesc className,
                                       Optional<String> methodName,
                                       Optional<MethodTypeDesc> methodType) {
        return new UnboundAttribute.UnboundEnclosingMethodAttribute(
                        TemporaryConstantPool.INSTANCE.classEntry(className),
                        methodName.isPresent() && methodType.isPresent()
                                ? TemporaryConstantPool.INSTANCE.nameAndTypeEntry(methodName.get(), methodType.get())
                                : null);
    }
}
