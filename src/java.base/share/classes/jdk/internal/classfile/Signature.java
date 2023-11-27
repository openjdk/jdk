/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile;

import java.lang.constant.ClassDesc;
import jdk.internal.classfile.impl.SignaturesImpl;

import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.Optional;
import jdk.internal.classfile.impl.Util;

/**
 * Models generic Java type signatures, as defined in {@jvms 4.7.9.1}.
 */
public sealed interface Signature {

    /** {@return the raw signature string} */
    String signatureString();

    /**
     * Parses generic Java type signature from raw string
     * @param javaTypeSignature raw Java type signature string
     * @return Java type signature
     */
    public static Signature parseFrom(String javaTypeSignature) {
        return new SignaturesImpl().parseSignature(requireNonNull(javaTypeSignature));
    }

    /**
     * @param classDesc the symbolic description of the Java type
     * @return Java type signature
     */
    public static Signature of(ClassDesc classDesc) {
        requireNonNull(classDesc);
        if (classDesc.isArray())
            return ArrayTypeSig.of(of(classDesc.componentType()));
        if (classDesc.isPrimitive())
            return BaseTypeSig.of(classDesc);
        return ClassTypeSig.of(classDesc);
    }

    /**
     * Models the signature of a primitive type or void
     */
    public sealed interface BaseTypeSig extends Signature
            permits SignaturesImpl.BaseTypeSigImpl {

        /** {@return the single-letter descriptor for the base type} */
        char baseType();

        /**
         * {@return the signature of a primitive type or void}
         * @param classDesc a symbolic descriptor for the base type, must correspond
         *                  to a primitive type
         */
        public static BaseTypeSig of(ClassDesc classDesc) {
            requireNonNull(classDesc);
            if (!classDesc.isPrimitive())
                throw new IllegalArgumentException("primitive class type required");
            return new SignaturesImpl.BaseTypeSigImpl(classDesc.descriptorString().charAt(0));
        }

        /**
         * {@return the signature of a primitive type or void}
         * @param baseType the single-letter descriptor for the base type
         */
        public static BaseTypeSig of(char baseType) {
            if ("VIJCSBFDZ".indexOf(baseType) < 0)
                throw new IllegalArgumentException("invalid base type signature");
            return new SignaturesImpl.BaseTypeSigImpl(baseType);
        }
    }

    /**
     * Models the signature of a reference type, which may be a class, interface,
     * type variable, or array type.
     */
    public sealed interface RefTypeSig
            extends Signature
            permits ArrayTypeSig, ClassTypeSig, TypeVarSig {
    }

    /**
     * Models the signature of a possibly-parameterized class or interface type.
     */
    public sealed interface ClassTypeSig
            extends RefTypeSig, ThrowableSig
            permits SignaturesImpl.ClassTypeSigImpl {

        /** {@return the signature of the outer type, if any} */
        Optional<ClassTypeSig> outerType();

        /** {@return the class name} */
        String className();

        /** {@return the class name, as a symbolic descriptor} */
        default ClassDesc classDesc() {
            var outer = outerType();
            return outer.isEmpty() ? ClassDesc.ofInternalName(className())
                    : outer.get().classDesc().nested(className());
        }

        /** {@return the type arguments of the class} */
        List<TypeArg> typeArgs();

        /**
         * {@return a class type signature}
         * @param className the name of the class
         * @param typeArgs signatures of the type arguments
         */
        public static ClassTypeSig of(ClassDesc className, TypeArg... typeArgs) {
            return of(null, className, typeArgs);
        }

        /**
         * {@return a class type signature for an inner class}
         * @param outerType signature of the outer type
         * @param className the name of the class
         * @param typeArgs signatures of the type arguments
         */
        public static ClassTypeSig of(ClassTypeSig outerType, ClassDesc className, TypeArg... typeArgs) {
            requireNonNull(className);
            return of(outerType, Util.toInternalName(className), typeArgs);
        }

        /**
         * {@return a class type signature}
         * @param className the name of the class
         * @param typeArgs signatures of the type arguments
         */
        public static ClassTypeSig of(String className, TypeArg... typeArgs) {
            return of(null, className, typeArgs);
        }

        /**
         * {@return a class type signature for an inner class}
         * @param outerType signature of the outer type
         * @param className the name of the class
         * @param typeArgs signatures of the type arguments
         */
        public static ClassTypeSig of(ClassTypeSig outerType, String className, TypeArg... typeArgs) {
            requireNonNull(className);
            return new SignaturesImpl.ClassTypeSigImpl(Optional.ofNullable(outerType), className.replace(".", "/"), List.of(typeArgs));
        }
    }

    /**
     * Models the type argument.
     */
    public sealed interface TypeArg
            permits SignaturesImpl.TypeArgImpl {

        /**
         * Indicator for whether a wildcard has default bound, no bound,
         * an upper bound, or a lower bound
         */
        public enum WildcardIndicator {

            /**
             * default bound wildcard (empty)
             */
            DEFAULT,

            /**
             * unbounded indicator {@code *}
             */
            UNBOUNDED,

            /**
             * upper-bounded indicator {@code +}
             */
            EXTENDS,

            /**
             * lower-bounded indicator {@code -}
             */
            SUPER;
        }

        /** {@return the wildcard indicator} */
        WildcardIndicator wildcardIndicator();

        /** {@return the signature of the type bound, if any} */
        Optional<RefTypeSig> boundType();

        /**
         * {@return a bounded type arg}
         * @param boundType the bound
         */
        public static TypeArg of(RefTypeSig boundType) {
            requireNonNull(boundType);
            return of(WildcardIndicator.DEFAULT, Optional.of(boundType));
        }

        /**
         * {@return an unbounded type arg}
         */
        public static TypeArg unbounded() {
            return of(WildcardIndicator.UNBOUNDED, Optional.empty());
        }

        /**
         * {@return an upper-bounded type arg}
         * @param boundType the upper bound
         */
        public static TypeArg extendsOf(RefTypeSig boundType) {
            requireNonNull(boundType);
            return of(WildcardIndicator.EXTENDS, Optional.of(boundType));
        }

        /**
         * {@return a lower-bounded type arg}
         * @param boundType the lower bound
         */
        public static TypeArg superOf(RefTypeSig boundType) {
            requireNonNull(boundType);
            return of(WildcardIndicator.SUPER, Optional.of(boundType));
        }

        /**
         * {@return a bounded type arg}
         * @param wildcard the wild card
         * @param boundType optional bound type
         */
        public static TypeArg of(WildcardIndicator wildcard, Optional<RefTypeSig> boundType) {
            return new SignaturesImpl.TypeArgImpl(wildcard, boundType);
        }
    }

    /**
     * Models the signature of a type variable.
     */
    public sealed interface TypeVarSig
            extends RefTypeSig, ThrowableSig
            permits SignaturesImpl.TypeVarSigImpl {

        /** {@return the name of the type variable} */
        String identifier();

        /**
         * {@return a signature for a type variable}
         * @param identifier the name of the type variable
         */
        public static TypeVarSig of(String identifier) {
            return new SignaturesImpl.TypeVarSigImpl(requireNonNull(identifier));
        }
    }

    /**
     * Models the signature of an array type.
     */
    public sealed interface ArrayTypeSig
            extends RefTypeSig
            permits SignaturesImpl.ArrayTypeSigImpl {

        /** {@return the signature of the component type} */
        Signature componentSignature();

        /**
         * {@return a signature for an array type}
         * @param componentSignature the component type
         */
        public static ArrayTypeSig of(Signature componentSignature) {
            return of(1, requireNonNull(componentSignature));
        }

        /**
         * {@return a signature for an array type}
         * @param dims the dimension of the array
         * @param componentSignature the component type
         */
        public static ArrayTypeSig of(int dims, Signature componentSignature) {
            requireNonNull(componentSignature);
            if (dims < 1 || dims > 255)
                throw new IllegalArgumentException("illegal array depth value");
            if (componentSignature instanceof SignaturesImpl.ArrayTypeSigImpl arr)
                return new SignaturesImpl.ArrayTypeSigImpl(dims + arr.arrayDepth(), arr.elemType());
            return new SignaturesImpl.ArrayTypeSigImpl(dims, componentSignature);
        }
    }

    /**
     * Models a signature for a type parameter of a generic class or method.
     */
    public sealed interface TypeParam
            permits SignaturesImpl.TypeParamImpl {

        /** {@return the name of the type parameter} */
        String identifier();

        /** {@return the class bound of the type parameter} */
        Optional<RefTypeSig> classBound();

        /** {@return the interface bounds of the type parameter} */
        List<RefTypeSig> interfaceBounds();

        /**
         * {@return a signature for a type parameter}
         * @param identifier the name of the type parameter
         * @param classBound the class bound of the type parameter
         * @param interfaceBounds the interface bounds of the type parameter
         */
        public static TypeParam of(String identifier, RefTypeSig classBound, RefTypeSig... interfaceBounds) {
            return new SignaturesImpl.TypeParamImpl(
                    requireNonNull(identifier),
                    Optional.ofNullable(classBound),
                    List.of(interfaceBounds));
        }

        /**
         * {@return a signature for a type parameter}
         * @param identifier the name of the type parameter
         * @param classBound the class bound of the type parameter
         * @param interfaceBounds the interface bounds of the type parameter
         */
        public static TypeParam of(String identifier, Optional<RefTypeSig> classBound, RefTypeSig... interfaceBounds) {
            return new SignaturesImpl.TypeParamImpl(
                    requireNonNull(identifier),
                    requireNonNull(classBound),
                    List.of(interfaceBounds));
        }
    }

    /**
     * Models a signature for a throwable type.
     */
    public sealed interface ThrowableSig extends Signature {
    }
}
