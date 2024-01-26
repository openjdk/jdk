/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model.util;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.IncompleteAnnotationException;
import java.util.List;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

/**
 * Utility methods for operating on types.
 *
 * Most methods operate on {@linkplain PrimitiveType primitive types},
 * {@linkplain ReferenceType reference types} (including {@linkplain
 * ArrayType array types} and the {@linkplain NullType null type}),
 * {@linkplain IntersectionType intersection types}, and the
 * pseudo-type '{@link TypeKind#VOID void}'. {@linkplain
 * ExecutableType Executable types} and the pseudo-types for
 * {@linkplain TypeKind#PACKAGE packages} and {@linkplain
 * TypeKind#MODULE modules} are generally out of scope for these
 * methods. One or more out of scope arguments will typically result
 * in a method throwing an {@link IllegalArgumentException}.
 *
 * <p>Where a method returns a type mirror or a collection of type
 * mirrors, any type mirrors represent types with no type annotations,
 * unless otherwise indicated.
 *
 * <p><b>Compatibility Note:</b> Methods may be added to this interface
 * in future releases of the platform.
 *
 * @see javax.annotation.processing.ProcessingEnvironment#getTypeUtils
 * @since 1.6
 */
public interface Types {

    /**
     * Returns the element corresponding to a type.
     * The type may be one of:
     * <ul>
     * <li>a {@link DeclaredType}
     * <li>a {@link TypeVariable}
     * <li>a pseudo-type for a {@linkplain TypeKind#PACKAGE package} or
     * {@linkplain TypeKind#MODULE module}
     * </ul>
     * The method returns {@code null} if the type is not one with a
     * corresponding element.
     * Types <em>without</em> corresponding elements include:
     * <ul>
     * <li>{@linkplain TypeKind#isPrimitive() primitive types}
     * <li>{@linkplain TypeKind#EXECUTABLE executable types}
     * <li>{@linkplain TypeKind#NONE "none"} pseudo-types
     * <li>{@linkplain TypeKind#NULL null types}
     * <li>{@link TypeKind#VOID void}
     * <li>{@linkplain TypeKind#WILDCARD wildcard type argument}
     * </ul>
     *
     * @param t the type to map to an element
     * @return the element corresponding to the given type
     */
    Element asElement(TypeMirror t);

    /**
     * Tests whether two {@code TypeMirror} objects represent the same type.
     *
     * <p>Caveat: if either of the arguments to this method represents a
     * wildcard, this method will return false.  As a consequence, a wildcard
     * is not the same type as itself.  This might be surprising at first,
     * but makes sense once you consider that an example like this must be
     * rejected by the compiler:
     * <pre>
     *   {@code List<?> list = new ArrayList<Object>();}
     *   {@code list.add(list.get(0));}
     * </pre>
     *
     * <p>Since annotations are only meta-data associated with a type,
     * the set of annotations on either argument is <em>not</em> taken
     * into account when computing whether or not two {@code
     * TypeMirror} objects are the same type. In particular, two
     * {@code TypeMirror} objects can have different annotations and
     * still be considered the same.
     *
     * @param t1  the first type
     * @param t2  the second type
     * @return {@code true} if and only if the two types are the same
     */
    boolean isSameType(TypeMirror t1, TypeMirror t2);

    /**
     * Tests whether one type is a subtype of another.
     * Any type is considered to be a subtype of itself.
     *
     * @param t1  the first type
     * @param t2  the second type
     * @return {@code true} if and only if the first type is a subtype
     *          of the second
     * @throws IllegalArgumentException if given a type for an executable, package, or module
     * @jls 4.10 Subtyping
     */
    boolean isSubtype(TypeMirror t1, TypeMirror t2);

    /**
     * Tests whether one type is assignable to another.
     *
     * @param t1  the first type
     * @param t2  the second type
     * @return {@code true} if and only if the first type is assignable
     *          to the second
     * @throws IllegalArgumentException if given a type for an executable, package, or module
     * @jls 5.2 Assignment Contexts
     */
    boolean isAssignable(TypeMirror t1, TypeMirror t2);

    /**
     * Tests whether one type argument <i>contains</i> another.
     *
     * @param t1  the first type
     * @param t2  the second type
     * @return {@code true} if and only if the first type contains the second
     * @throws IllegalArgumentException if given a type for an executable, package, or module
     * @jls 4.5.1 Type Arguments of Parameterized Types
     */
    boolean contains(TypeMirror t1, TypeMirror t2);

    /**
     * Tests whether the signature of one method is a <i>subsignature</i>
     * of another.
     *
     * @param m1  the first method
     * @param m2  the second method
     * @return {@code true} if and only if the first signature is a
     *          subsignature of the second
     * @jls 8.4.2 Method Signature
     */
    boolean isSubsignature(ExecutableType m1, ExecutableType m2);

    /**
     * Returns the direct supertypes of a type. The interface types, if any,
     * will appear last in the list. For an interface type with no direct
     * super-interfaces, a type mirror representing {@code java.lang.Object}
     * is returned.
     * The type {@code java.lang.Object} has no direct supertype (JLS
     * {@jls 8.1.4}, {@jls 8.1.5}) so an empty list is returned for
     * the direct supertypes of a type mirror representing {@code
     * java.lang.Object}.
     *
     * Annotations on the direct super types are preserved.
     *
     * @param t  the type being examined
     * @return the direct supertypes, or an empty list if none
     * @throws IllegalArgumentException if given a type for an executable, package, or module
     * @jls 4.10 Subtyping
     */
    List<? extends TypeMirror> directSupertypes(TypeMirror t);

    /**
     * {@return the erasure of a type}
     *
     * @param t  the type to be erased
     * @throws IllegalArgumentException if given a type for a package or module
     * @jls 4.6 Type Erasure
     */
    TypeMirror erasure(TypeMirror t);

    /**
     * {@return the class of a boxed value of the primitive type argument}
     * That is, <i>boxing conversion</i> is applied.
     *
     * @param p  the primitive type to be converted
     * @jls 5.1.7 Boxing Conversion
     */
    TypeElement boxedClass(PrimitiveType p);

    /**
     * Returns the type (a primitive type) of unboxed values of a given type.
     * That is, <i>unboxing conversion</i> is applied.
     *
     * @param t  the type to be unboxed
     * @return the type of an unboxed value of type {@code t}
     * @throws IllegalArgumentException if the given type has no
     *          unboxing conversion
     * @jls 5.1.8 Unboxing Conversion
     */
    PrimitiveType unboxedType(TypeMirror t);

    /**
     * Applies capture conversion to a type.
     *
     * @param t  the type to be converted
     * @return the result of applying capture conversion
     * @throws IllegalArgumentException if given a type for an executable, package, or module
     * @jls 5.1.10 Capture Conversion
     */
    TypeMirror capture(TypeMirror t);

    /**
     * {@return a primitive type}
     *
     * @param kind  the kind of primitive type to return
     * @throws IllegalArgumentException if {@code kind} is not a primitive kind
     * @jls 4.2 Primitive Types and Values
     */
    PrimitiveType getPrimitiveType(TypeKind kind);

    /**
     * {@return the null type}  This is the type of {@code null}.
     * @jls 4.1 The Kinds of Types and Values
     */
    NullType getNullType();

    /**
     * Returns a pseudo-type used where no actual type is appropriate.
     * The kind of type to return may be either
     * {@link TypeKind#VOID VOID} or {@link TypeKind#NONE NONE}.
     *
     * <p>To get the pseudo-type corresponding to a package or module,
     * call {@code asType()} on the element modeling the {@linkplain
     * PackageElement package} or {@linkplain ModuleElement
     * module}. Names can be converted to elements for packages or
     * modules using {@link Elements#getPackageElement(CharSequence)}
     * or {@link Elements#getModuleElement(CharSequence)},
     * respectively.
     *
     * @param kind  the kind of type to return
     * @return a pseudo-type of kind {@code VOID} or {@code NONE}
     * @throws IllegalArgumentException if {@code kind} is not valid
     */
    NoType getNoType(TypeKind kind);

    /**
     * {@return an array type with the specified component type}
     *
     * Annotations on the component type are preserved.
     *
     * @param componentType  the component type
     * @throws IllegalArgumentException if the component type is not valid for
     *          an array, including executable, package, module, and wildcard types
     * @jls 10.1 Array Types
     */
    ArrayType getArrayType(TypeMirror componentType);

    /**
     * {@return a new wildcard type}  Either of the wildcard's
     * bounds may be specified, or neither, but not both.
     *
     * Annotations on the bounds are preserved.
     *
     * @param extendsBound  the extends (upper) bound, or {@code null} if none
     * @param superBound    the super (lower) bound, or {@code null} if none
     * @throws IllegalArgumentException if bounds are not valid
     * @jls 4.5.1 Type Arguments of Parameterized Types
     */
    WildcardType getWildcardType(TypeMirror extendsBound,
                                 TypeMirror superBound);

    /**
     * {@return the type corresponding to a type element and
     * actual type arguments}
     * Given the type element for {@code Set} and the type mirror
     * for {@code String},
     * for example, this method may be used to get the
     * parameterized type {@code Set<String>}.
     *
     * Annotations on the type arguments are preserved.
     *
     * <p> The number of type arguments must either equal the
     * number of the type element's formal type parameters, or must be
     * zero.  If zero, and if the type element is generic,
     * then the type element's raw type is returned.
     *
     * <p> If a parameterized type is being returned, its type element
     * must not be contained within a generic outer class.
     * The parameterized type {@code Outer<String>.Inner<Number>},
     * for example, may be constructed by first using this
     * method to get the type {@code Outer<String>}, and then invoking
     * {@link #getDeclaredType(DeclaredType, TypeElement, TypeMirror...)}.
     *
     * @param typeElem  the type element
     * @param typeArgs  the actual type arguments
     * @throws IllegalArgumentException if too many or too few
     *          type arguments are given, or if an inappropriate type
     *          argument or type element is provided
     */
    DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs);

    /**
     * Returns the type corresponding to a type element
     * and actual type arguments, given a
     * {@linkplain DeclaredType#getEnclosingType() containing type}
     * of which it is a member.
     * The parameterized type {@code Outer<String>.Inner<Number>},
     * for example, may be constructed by first using
     * {@link #getDeclaredType(TypeElement, TypeMirror...)}
     * to get the type {@code Outer<String>}, and then invoking
     * this method.
     *
     * Annotations on the type arguments are preserved.
     *
     * <p> If the containing type is a parameterized type,
     * the number of type arguments must equal the
     * number of {@code typeElem}'s formal type parameters.
     * If it is not parameterized or if it is {@code null}, this method is
     * equivalent to {@code getDeclaredType(typeElem, typeArgs)}.
     *
     * @param containing  the containing type, or {@code null} if none
     * @param typeElem    the type element
     * @param typeArgs    the actual type arguments
     * @return the type corresponding to the type element and
     *          actual type arguments, contained within the given type
     * @throws IllegalArgumentException if too many or too few
     *          type arguments are given, or if an inappropriate type
     *          argument, type element, or containing type is provided
     */
    DeclaredType getDeclaredType(DeclaredType containing,
                                 TypeElement typeElem, TypeMirror... typeArgs);

    /**
     * Returns the type of an element when that element is viewed as
     * a member of, or otherwise directly contained by, a given type.
     * For example,
     * when viewed as a member of the parameterized type {@code Set<String>},
     * the {@code Set.add} method is an {@code ExecutableType}
     * whose parameter is of type {@code String}.
     *
     * @param containing  the containing type
     * @param element     the element
     * @return the type of the element as viewed from the containing type
     * @throws IllegalArgumentException if the element is not a valid one
     *          for the given type
     */
    TypeMirror asMemberOf(DeclaredType containing, Element element);

    /**
     * {@return a type mirror equivalent to the argument, but with no annotations}
     * If the type mirror is a composite type, such as an array type
     * or a wildcard type, any constitute types, such as the
     * component type of an array and the type of the bounds of a
     * wildcard type, also have no annotations, recursively.
     *
     * <p>For most kinds of type mirrors, the result of
     * {@snippet lang="java" :
     *   types.isSameType(typeMirror, types.stripAnnotations(typeMirror))
     * }
     * is {@code true}. The predicate is {@code false} on wildcard
     * types for {@linkplain #isSameType(TypeMirror, TypeMirror)
     * reasons discussed elsewhere}.
     *
     * @param t the type mirror
     * @param <T> the specific type of type mirror
     * @implSpec
     * The default implementation throws {@code UnsupportedOperationException}.
     * @since 23
     */
    default <T extends TypeMirror> T stripAnnotations(T t) {
        throw new UnsupportedOperationException();
    }
}
