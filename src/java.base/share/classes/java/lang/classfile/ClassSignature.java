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

import java.util.List;
import jdk.internal.classfile.impl.SignaturesImpl;
import static java.util.Objects.requireNonNull;
import jdk.internal.javac.PreviewFeature;

/**
 * Models the generic signature of a class file, as defined by {@jvms 4.7.9}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ClassSignature
        permits SignaturesImpl.ClassSignatureImpl {

    /** {@return the type parameters of this class} */
    List<Signature.TypeParam> typeParameters();

    /** {@return the instantiation of the superclass in this signature} */
    Signature.ClassTypeSig superclassSignature();

    /** {@return the instantiation of the interfaces in this signature} */
    List<Signature.ClassTypeSig> superinterfaceSignatures();

    /** {@return the raw signature string} */
    String signatureString();

    /**
     * {@return a class signature}
     * @param superclassSignature the superclass
     * @param superinterfaceSignatures the interfaces
     * @since 23
     */
    public static ClassSignature of(Signature.ClassTypeSig superclassSignature,
                                    Signature.ClassTypeSig... superinterfaceSignatures) {
        return of(List.of(), superclassSignature, superinterfaceSignatures);
    }

    /**
     * {@return a class signature}
     * @param typeParameters the type parameters
     * @param superclassSignature the superclass
     * @param superinterfaceSignatures the interfaces
     * @since 23
     */
    public static ClassSignature of(List<Signature.TypeParam> typeParameters,
                                    Signature.ClassTypeSig superclassSignature,
                                    Signature.ClassTypeSig... superinterfaceSignatures) {
        return new SignaturesImpl.ClassSignatureImpl(
                requireNonNull(typeParameters),
                requireNonNull(superclassSignature),
                List.of(superinterfaceSignatures));
    }

    /**
     * Parses a raw class signature string into a {@linkplain Signature}
     * @param classSignature the raw class signature string
     * @return class signature
     */
    public static ClassSignature parseFrom(String classSignature) {
        return new SignaturesImpl(classSignature).parseClassSignature();
    }
}
