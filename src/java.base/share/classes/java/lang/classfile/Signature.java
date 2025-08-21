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
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Optional;

import jdk.internal.classfile.impl.SignaturesImpl;
import jdk.internal.classfile.impl.Util;

import static java.util.Objects.requireNonNull;

/**
 * Models generic Java type signatures, as defined in JVMS {@jvms 4.7.9.1}.
 *
 * @see Type
 * @see SignatureAttribute
 * @jls 4.1 The Kinds of Types and Values
 * @jvms 4.7.9.1 Signatures
 * @sealedGraph
 * @since 24
 */
public sealed interface Signature {

    /** {@return the raw signature string} */
    String signatureString();

    /**
     * Parses a Java type signature from a raw string.
     *
     * @param javaTypeSignature raw Java type signature string
     * @return a Java type signature
     * @throws IllegalArgumentException if the string is not a valid Java type
     *         signature string
     */
    public static Signature parseFrom(String javaTypeSignature) {
        return new SignaturesImpl(javaTypeSignature).parseSignature();
    }

    /**
     * {@return a Java type signature from a field descriptor}  The returned
     * signature represents a reifiable type (JLS {@jls 4.7}).
     *
     * @param classDesc the symbolic description of the Java type
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
     * Models the signature of a primitive type (JLS {@jls 4.2}) or void.
     *
     * @jls 4.2 Primitive Types and Values
     * @jvms 4.7.9.1 Signatures
     * @since 24
     */
    public sealed interface BaseTypeSig extends Signature
            permits SignaturesImpl.BaseTypeSigImpl {

        /** {@return the single-letter descriptor for the base type} */
        char baseType();

        /**
         * {@return the signature of a primitive type or void}
         * @param classDesc a symbolic descriptor for the base type, must correspond
         *                  to a primitive type
         * @throws IllegalArgumentException if the {@code classDesc} is not
         *         primitive
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
         * @throws IllegalArgumentException if the {@code baseType} is not a
         *         valid descriptor character for a primitive type or void
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
     *
     * @jls 4.3 Reference Types and Values
     * @jvms 4.7.9.1 Signatures
     * @sealedGraph
     * @since 24
     */
    public sealed interface RefTypeSig
            extends Signature
            permits ArrayTypeSig, ClassTypeSig, TypeVarSig {
    }

    /**
     * Models the signature of a possibly-parameterized class or interface type.
     *
     * @see Type
     * @see ParameterizedType
     * @jvms 4.7.9.1 Signatures
     * @since 24
     */
    public sealed interface ClassTypeSig
            extends RefTypeSig, ThrowableSig
            permits SignaturesImpl.ClassTypeSigImpl {

        /**
         * {@return the signature of the class that this class is a member of,
         * only if this is a member class}  Note that the outer class may be
         * absent if it is not a parameterized type.
         *
         * @jls 4.5 Parameterized Types
         */
        Optional<ClassTypeSig> outerType();

        /**
         * {@return the class or interface name; includes the {@linkplain
         * ClassEntry##internalname slash-separated} package name if there is no
         * outer type}
         */
        String className();

        /**
         * {@return this class or interface, as a symbolic descriptor}
         */
        default ClassDesc classDesc() {
            var outer = outerType();
            return outer.isEmpty() ? ClassDesc.ofInternalName(className())
                    : outer.get().classDesc().nested(className());
        }

        /**
         * {@return the type arguments of this class or interface}
         * Note that the outer type may have more type arguments.
         *
         * @jls 4.5 Parameterized Types
         */
        List<TypeArg> typeArgs();

        /**
         * {@return a class or interface signature without an outer type}
         *
         * @param className the name of the class or interface
         * @param typeArgs the type arguments
         * @throws IllegalArgumentException if {@code className} does not
         *         represent a class or interface
         */
        public static ClassTypeSig of(ClassDesc className, TypeArg... typeArgs) {
            return of(null, className, typeArgs);
        }

        /**
         * {@return a class or interface signature}
         *
         * @param outerType signature of the outer type, may be {@code null}
         * @param className the name of this class or interface
         * @param typeArgs the type arguments
         * @throws IllegalArgumentException if {@code className} does not
         *         represent a class or interface
         */
        public static ClassTypeSig of(ClassTypeSig outerType, ClassDesc className, TypeArg... typeArgs) {
            requireNonNull(className);
            return of(outerType, Util.toInternalName(className), typeArgs);
        }

        /**
         * {@return a class or interface signature without an outer type}
         *
         * @param className the name of the class or interface
         * @param typeArgs the type arguments
         */
        public static ClassTypeSig of(String className, TypeArg... typeArgs) {
            return of(null, className, typeArgs);
        }

        /**
         * {@return a class type signature}
         *
         * @param outerType signature of the outer type, may be {@code null}
         * @param className the name of this class or interface
         * @param typeArgs the type arguments
         */
        public static ClassTypeSig of(ClassTypeSig outerType, String className, TypeArg... typeArgs) {
            requireNonNull(className);
            return new SignaturesImpl.ClassTypeSigImpl(Optional.ofNullable(outerType), className.replace(".", "/"), List.of(typeArgs));
        }
    }

    /**
     * Models a type argument, an argument to a type parameter.
     *
     * @see Type
     * @see WildcardType
     * @jls 4.5.1 Type Arguments of Parameterized Types
     * @jvms 4.7.9.1 Signatures
     * @sealedGraph
     * @since 24
     */
    public sealed interface TypeArg {

        /**
         * Models an unbounded wildcard type argument {@code *}, or {@code
         * ?} in Java programs.  This type argument has an implicit upper
         * bound of {@link Object}.
         *
         * @see WildcardType#getUpperBounds()
         * @jls 4.5.1 Type Arguments of Parameterized Types
         * @jvms 4.7.9.1 Signatures
         * @since 24
         */
        public sealed interface Unbounded extends TypeArg permits SignaturesImpl.UnboundedTypeArgImpl {
        }

        /**
         * Models a type argument with an explicit bound type.
         *
         * @jls 4.5.1 Type Arguments of Parameterized Types
         * @jvms 4.7.9.1 Signatures
         * @since 24
         */
        public sealed interface Bounded extends TypeArg permits SignaturesImpl.TypeArgImpl {

            /**
             * Models a type argument's wildcard indicator.
             *
             * @jls 4.5.1 Type Arguments of Parameterized Types
             * @jvms 4.7.9.1 Signatures
             * @since 24
             */
            public enum WildcardIndicator {

                /**
                 * No wildcard (empty), an exact type.  Also known as
                 * {@index invariant}.  This is the direct use of a
                 * reference type in Java programs.
                 *
                 * @see Type
                 */
                NONE,

                /**
                 * Upper-bound indicator {@code +}.  Also known as
                 * {@index covariant}.  This is the {@code ? extends}
                 * prefix in Java programs.
                 *
                 * @see WildcardType#getUpperBounds()
                 */
                EXTENDS,

                /**
                 * Lower-bound indicator {@code -}.  Also known as
                 * {@index contravariant}.  This is the {@code ? super}
                 * prefix in Java programs.
                 *
                 * @see WildcardType#getLowerBounds()
                 */
                SUPER;
            }

            /** {@return the kind of wildcard} */
            WildcardIndicator wildcardIndicator();

            /** {@return the signature of the type bound} */
            RefTypeSig boundType();
        }

        /**
         * {@return a type argument of a reference type}
         *
         * @param boundType the reference type
         * @see Bounded.WildcardIndicator#NONE
         */
        public static TypeArg.Bounded of(RefTypeSig boundType) {
            requireNonNull(boundType);
            return bounded(Bounded.WildcardIndicator.NONE, boundType);
        }

        /**
         * {@return an unbounded wildcard type argument {@code *}}
         */
        public static TypeArg.Unbounded unbounded() {
            return SignaturesImpl.UnboundedTypeArgImpl.INSTANCE;
        }

        /**
         * {@return an upper-bounded wildcard type argument}
         *
         * @param boundType the upper bound
         * @see Bounded.WildcardIndicator#EXTENDS
         */
        public static TypeArg.Bounded extendsOf(RefTypeSig boundType) {
            requireNonNull(boundType);
            return bounded(Bounded.WildcardIndicator.EXTENDS, boundType);
        }

        /**
         * {@return a lower-bounded wildcard type argument}
         *
         * @param boundType the lower bound
         * @see Bounded.WildcardIndicator#SUPER
         */
        public static TypeArg.Bounded superOf(RefTypeSig boundType) {
            requireNonNull(boundType);
            return bounded(Bounded.WildcardIndicator.SUPER, boundType);
        }

        /**
         * {@return a bounded type argument}
         *
         * @param wildcard the wildcard indicator
         * @param boundType the bound type
         */
        public static TypeArg.Bounded bounded(Bounded.WildcardIndicator wildcard, RefTypeSig boundType) {
            requireNonNull(wildcard);
            requireNonNull(boundType);
            return new SignaturesImpl.TypeArgImpl(wildcard, boundType);
        }
    }

    /**
     * Models the signature of a type variable.  A type variable is introduced
     * by a {@linkplain TypeParam type parameter} declaration.
     *
     * @see TypeVariable
     * @see TypeParam
     * @jls 4.4 Type Variables
     * @jvms 4.7.9.1 Signatures
     * @since 24
     */
    public sealed interface TypeVarSig
            extends RefTypeSig, ThrowableSig
            permits SignaturesImpl.TypeVarSigImpl {

        /** {@return the name of the type variable} */
        String identifier();

        /**
         * {@return a signature for a type variable}
         *
         * @param identifier the name of the type variable
         */
        public static TypeVarSig of(String identifier) {
            return new SignaturesImpl.TypeVarSigImpl(requireNonNull(identifier));
        }
    }

    /**
     * Models the signature of an array type.
     *
     * @see Type
     * @see GenericArrayType
     * @jls 10.1 Array Types
     * @jvms 4.7.9.1 Signatures
     * @since 24
     */
    public sealed interface ArrayTypeSig
            extends RefTypeSig
            permits SignaturesImpl.ArrayTypeSigImpl {

        /** {@return the signature of the component type} */
        Signature componentSignature();

        /**
         * {@return an array type with the given component type}
         * @param componentSignature the component type
         */
        public static ArrayTypeSig of(Signature componentSignature) {
            return of(1, requireNonNull(componentSignature));
        }

        /**
         * {@return a signature for an array type}
         * @param dims the dimension of the array
         * @param componentSignature the component type
         * @throws IllegalArgumentException if {@code dims < 1} or the
         *         resulting array type exceeds 255 dimensions
         */
        public static ArrayTypeSig of(int dims, Signature componentSignature) {
            requireNonNull(componentSignature);
            if (componentSignature instanceof SignaturesImpl.ArrayTypeSigImpl arr) {
                if (dims < 1 || dims > 255 - arr.arrayDepth())
                    throw new IllegalArgumentException("illegal array depth value");
                return new SignaturesImpl.ArrayTypeSigImpl(dims + arr.arrayDepth(), arr.elemType());
            }
            if (dims < 1 || dims > 255)
                throw new IllegalArgumentException("illegal array depth value");
            return new SignaturesImpl.ArrayTypeSigImpl(dims, componentSignature);
        }
    }

    /**
     * Models a signature for a type parameter of a generic class, interface,
     * method, or constructor, which introduces a {@linkplain TypeVarSig type
     * variable}.
     *
     * @see GenericDeclaration#getTypeParameters()
     * @see TypeVariable
     * @see TypeVarSig
     * @jls 4.4 Type Variables
     * @jvms 4.7.9.1 Signatures
     * @since 24
     */
    public sealed interface TypeParam
            permits SignaturesImpl.TypeParamImpl {

        /** {@return the name of the type parameter} */
        String identifier();

        /**
         * {@return the class bound of the type parameter}  This may be empty
         * if this type parameter only has interface bounds.
         */
        Optional<RefTypeSig> classBound();

        /**
         * {@return the interface bounds of the type parameter}  This may be
         * empty.
         */
        List<RefTypeSig> interfaceBounds();

        /**
         * {@return a signature for a type parameter}
         *
         * @param identifier the name of the type parameter
         * @param classBound the class bound of the type parameter, may be {@code null}
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
         *
         * @param identifier the name of the type parameter
         * @param classBound the optional class bound of the type parameter
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
     * Marker interface for a signature for a throwable type.
     *
     * @jls 8.4.6 Method Throws
     * @jvms 4.7.9.1 Signatures
     * @sealedGraph
     * @since 24
     */
    public sealed interface ThrowableSig extends Signature {
    }
}
