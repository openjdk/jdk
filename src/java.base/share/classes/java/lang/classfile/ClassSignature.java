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
package java.lang.classfile;

import java.lang.classfile.attribute.SignatureAttribute;
import java.util.List;

import jdk.internal.classfile.impl.SignaturesImpl;

import static java.util.Objects.requireNonNull;

/**
 * Models the generic signature of a class or interface, as defined by JVMS
 * {@jvms 4.7.9.1}.
 *
 * @see Class
 * @see SignatureAttribute
 * @jls 8.1 Class Declarations
 * @jls 9.1 Interface Declarations
 * @jvms 4.7.9.1 Signatures
 * @since 24
 */
public sealed interface ClassSignature
        permits SignaturesImpl.ClassSignatureImpl {

    /**
     * {@return the type parameters of this class or interface, may be empty}
     *
     * @see Class#getTypeParameters()
     * @jls 8.1.2 Generic Classes and Type Parameters
     * @jls 9.1.2 Generic Interfaces and Type Parameters
     */
    List<Signature.TypeParam> typeParameters();

    /**
     * {@return the instantiation of the superclass in this signature}
     * Interfaces return a signature representing the {@link Object} class.
     *
     * @see Class#getGenericSuperclass()
     * @jls 8.1.4 Superclasses and Subclasses
     */
    Signature.ClassTypeSig superclassSignature();

    /**
     * {@return the instantiation of the interfaces in this signature, may be
     * empty}
     *
     * @see Class#getGenericInterfaces()
     * @jls 8.1.5 Superinterfaces
     * @jls 9.1.3 Superinterfaces and Subinterfaces
     */
    List<Signature.ClassTypeSig> superinterfaceSignatures();

    /** {@return the raw signature string} */
    String signatureString();

    /**
     * {@return a class signature with no type parameter declaration}
     *
     * @param superclassSignature the superclass
     * @param superinterfaceSignatures the interfaces
     */
    public static ClassSignature of(Signature.ClassTypeSig superclassSignature,
                                    Signature.ClassTypeSig... superinterfaceSignatures) {
        return of(List.of(), superclassSignature, superinterfaceSignatures);
    }

    /**
     * {@return a class signature}
     * @param typeParameters the type parameters, may be empty
     * @param superclassSignature the superclass
     * @param superinterfaceSignatures the interfaces
     */
    public static ClassSignature of(List<Signature.TypeParam> typeParameters,
                                    Signature.ClassTypeSig superclassSignature,
                                    Signature.ClassTypeSig... superinterfaceSignatures) {
        return new SignaturesImpl.ClassSignatureImpl(
                List.copyOf(requireNonNull(typeParameters)),
                requireNonNull(superclassSignature),
                List.of(superinterfaceSignatures));
    }

    /**
     * Parses a raw class signature string into a {@linkplain Signature}.
     *
     * @param classSignature the raw class signature string
     * @return class signature
     * @throws IllegalArgumentException if the string is not a valid class signature string
     */
    public static ClassSignature parseFrom(String classSignature) {
        return new SignaturesImpl(classSignature).parseClassSignature();
    }
}
