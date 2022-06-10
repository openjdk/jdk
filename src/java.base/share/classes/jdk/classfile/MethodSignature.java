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
package jdk.classfile;

import java.lang.constant.MethodTypeDesc;
import java.util.List;
import jdk.classfile.impl.SignaturesImpl;
import static java.util.Objects.requireNonNull;
import static jdk.classfile.impl.SignaturesImpl.null2Empty;
import jdk.classfile.impl.Util;

/**
 * Models the generic signature of a class, as defined by JVMS 4.7.9.
 */
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
     * {@return a signature for a raw (no generic information) method descriptor}
     * @param descriptor the method descriptor
     */
    public static MethodSignature of(MethodTypeDesc descriptor) {
        requireNonNull(descriptor);
        // @@@ MethodReference Signature::of
        // @@@ Input to Util.mappedList is immutable therefore so is result
        return new SignaturesImpl.MethodSignatureImpl(List.of(),
                                                      Util.mappedList(descriptor.parameterList(), Signature::of),
                                                      Signature.of(descriptor.returnType()),
                                                      List.of());
    }

    /**
     * {@return a signature}
     * @param arguments signatures for the method arguments
     * @param result signature for the return type
     */
    public static MethodSignature of(List<Signature> arguments, Signature result) {
        return of(null, arguments, result, null);
    }

    /**
     * {@return a signature}
     * @param arguments signatures for the method arguments
     * @param result signature for the return type
     * @param exceptions sigantures for the exceptions
     */
    public static MethodSignature of(List<Signature> arguments,
                                     Signature result,
                                     List<Signature.ThrowableSig> exceptions) {
        return of(null, arguments, result, exceptions);
    }

    /**
     * {@return a signature}
     * @param typeParameters signatures for the type parameters
     * @param arguments signatures for the method arguments
     * @param result signature for the return type
     */
    public static MethodSignature of(List<Signature.TypeParam> typeParameters,
                                     List<Signature> arguments,
                                     Signature result) {
        return of(typeParameters, arguments, result, null);
    }

    /**
     * {@return a signature}
     * @param typeParameters signatures for the type parameters
     * @param arguments signatures for the method arguments
     * @param result signature for the return type
     * @param exceptions sigantures for the exceptions
     */
    public static MethodSignature of(List<Signature.TypeParam> typeParameters,
                                     List<Signature> arguments,
                                     Signature result,
                                     List<Signature.ThrowableSig> exceptions) {
        requireNonNull(result);
        return new SignaturesImpl.MethodSignatureImpl(null2Empty(typeParameters),
                                                      null2Empty(arguments), result, null2Empty(exceptions));
    }

    /**
     * Parses a raw method signature string into a {@linkplain Signature}
     * @param signature the raw signature string
     * @return the signature
     */
    public static MethodSignature parseFrom(String signature) {
        requireNonNull(signature);
        return new SignaturesImpl().parseMethodSignature(signature);
    }
}
