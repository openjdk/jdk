/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.MethodTypeDesc;
import java.util.List;
import jdk.internal.classfile.impl.SignaturesImpl;
import static java.util.Objects.requireNonNull;
import jdk.internal.classfile.impl.Util;
import jdk.internal.javac.PreviewFeature;

/**
 * Models the generic signature of a method, as defined by {@jvms 4.7.9}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface MethodSignature
        permits SignaturesImpl.MethodSignatureImpl {

    /** {@return the type parameters of this method} */
    List<Signature.TypeParam> typeParameters();

    /** {@return the signatures of the parameters of this method} */
    List<Signature> arguments();

    /** {@return the signatures of the return value of this method} */
    Signature result();

    /** {@return the signatures of the exceptions thrown by this method} */
    List<Signature.ThrowableSig> throwableSignatures();

    /** {@return the raw signature string} */
    String signatureString();

    /**
     * {@return a method signature for a raw (no generic information) method descriptor}
     * @param methodDescriptor the method descriptor
     */
    public static MethodSignature of(MethodTypeDesc methodDescriptor) {

        requireNonNull(methodDescriptor);
        return new SignaturesImpl.MethodSignatureImpl(
                List.of(),
                List.of(),
                Signature.of(methodDescriptor.returnType()),
                Util.mappedList(methodDescriptor.parameterList(), Signature::of));
    }

    /**
     * {@return a method signature}
     * @param result signature for the return type
     * @param arguments signatures for the method arguments
     */
    public static MethodSignature of(Signature result,
                                     Signature... arguments) {

        return new SignaturesImpl.MethodSignatureImpl(List.of(),
                                                      List.of(),
                                                      requireNonNull(result),
                                                      List.of(arguments));
    }

    /**
     * {@return a method signature}
     * @param typeParameters signatures for the type parameters
     * @param exceptions signatures for the exceptions
     * @param result signature for the return type
     * @param arguments signatures for the method arguments
     */
    public static MethodSignature of(List<Signature.TypeParam> typeParameters,
                                     List<Signature.ThrowableSig> exceptions,
                                     Signature result,
                                     Signature... arguments) {

        return new SignaturesImpl.MethodSignatureImpl(
                List.copyOf(requireNonNull(typeParameters)),
                List.copyOf(requireNonNull(exceptions)),
                requireNonNull(result),
                List.of(arguments));
    }

    /**
     * Parses a raw method signature string into a {@linkplain MethodSignature}
     * @param methodSignature the raw method signature string
     * @return method signature
     */
    public static MethodSignature parseFrom(String methodSignature) {

        return new SignaturesImpl().parseMethodSignature(requireNonNull(methodSignature));
    }
}
