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

import java.lang.classfile.*;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#signature() Signature} attribute (JVMS {@jvms
 * 4.7.9}), which indicates the generic signature of this structure.
 * <p>
 * This attribute appears on classes, fields, methods, and record components,
 * and does not permit {@linkplain AttributeMapper#allowMultiple multiple
 * instances} in one structure.  It has a data dependency on the {@linkplain
 * AttributeMapper.AttributeStability#CP_REFS constant pool}.
 * <p>
 * The attribute was introduced in the Java SE Platform version 5.0, major
 * version {@value ClassFile#JAVA_5_VERSION}.
 *
 * @see Signature
 * @see ClassSignature
 * @see MethodSignature
 * @jvms 4.7.9 The {@code Signature} Attribute
 * @since 24
 */
public sealed interface SignatureAttribute
        extends Attribute<SignatureAttribute>,
                ClassElement, MethodElement, FieldElement
        permits BoundAttribute.BoundSignatureAttribute, UnboundAttribute.UnboundSignatureAttribute {

    /**
     * {@return the signature string for the class, method, field, or record
     * component}  It is a class signature string if this attribute is on a
     * class, a method signature string if this attribute is on a method, or
     * a field signature string if this attribute is on a field or a record
     * component.
     *
     * @jvms 4.7.9.1 Signatures
     */
    Utf8Entry signature();

    /**
     * Parses the signature string as a class signature.
     *
     * @return the class signature
     * @throws IllegalArgumentException if the signature string is not a valid
     *         class signature string
     */
    default ClassSignature asClassSignature() {
        return ClassSignature.parseFrom(signature().stringValue());
    }

    /**
     * Parse the signature string as a method signature.
     *
     * @return the method signature
     * @throws IllegalArgumentException if the signature string is not a valid
     *         method signature string
     */
    default MethodSignature asMethodSignature() {
        return MethodSignature.parseFrom(signature().stringValue());
    }

    /**
     * Parses the signature string as a Java type signature.
     *
     * @return the type signature
     * @throws IllegalArgumentException if the signature string is not a valid
     *         Java type signature string
     * @see Field#getGenericType()
     * @see RecordComponent#getGenericType()
     */
    default Signature asTypeSignature() {
        return Signature.parseFrom(signature().stringValue());
    }

    /**
     * {@return a {@code Signature} attribute for a class}
     *
     * @param classSignature the class signature
     */
    static SignatureAttribute of(ClassSignature classSignature) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(classSignature.signatureString()));
    }

    /**
     * {@return a {@code Signature} attribute for a method}
     *
     * @param methodSignature the method signature
     */
    static SignatureAttribute of(MethodSignature methodSignature) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(methodSignature.signatureString()));
    }

    /**
     * {@return a {@code Signature} attribute for a field or a record component}
     *
     * @param signature the Java type signature
     */
    static SignatureAttribute of(Signature signature) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(signature.signatureString()));
    }

    /**
     * {@return a {@code Signature} attribute}
     *
     * @param signature the signature string
     */
    static SignatureAttribute of(Utf8Entry signature) {
        return new UnboundAttribute.UnboundSignatureAttribute(signature);
    }
}
