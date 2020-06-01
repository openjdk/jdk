/*
 * Copyright (c) 2005, 2020, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model.element;

import java.util.List;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

/**
 * Represents a class or interface program element.  Provides access
 * to information about the type and its members.  Note that an enum
 * type and a record type are kinds of classes and an annotation type is a kind of
 * interface.
 *
 * <p> While a {@code TypeElement} represents a class or interface
 * <i>element</i>, a {@link DeclaredType} represents a class
 * or interface <i>type</i>, the latter being a use
 * (or <i>invocation</i>) of the former.
 * The distinction is most apparent with generic types,
 * for which a single element can define a whole
 * family of types.  For example, the element
 * {@code java.util.Set} corresponds to the parameterized types
 * {@code java.util.Set<String>} and {@code java.util.Set<Number>}
 * (and many others), and to the raw type {@code java.util.Set}.
 *
 * <p> Each method of this interface that returns a list of elements
 * will return them in the order that is natural for the underlying
 * source of program information.  For example, if the underlying
 * source of information is Java source code, then the elements will be
 * returned in source code order.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @author Peter von der Ah&eacute;
 * @see DeclaredType
 * @since 1.6
 */
public interface TypeElement extends Element, Parameterizable, QualifiedNameable {
    /**
     * Returns the type defined by this type element, returning the
     * <i>prototypical</i> type for an element representing a generic type.
     *
     * <p>A generic element defines a family of types, not just one.
     * If this is a generic element, a prototypical type is
     * returned which has the element's invocation on the
     * type variables corresponding to its own formal type parameters.
     * For example,
     * for the generic class element {@code C<N extends Number>},
     * the parameterized type {@code C<N>} is returned.
     * The {@link Types} utility interface has more general methods
     * for obtaining the full range of types defined by an element.
     *
     * @return the type defined by this type element
     *
     * @see Types#asMemberOf(DeclaredType, Element)
     * @see Types#getDeclaredType(TypeElement, TypeMirror...)
     */
    @Override
    TypeMirror asType();

    /**
     * Returns the fields, methods, constructors, record components,
     * and member types that are directly declared in this class or
     * interface.
     *
     * This includes any {@linkplain Elements.Origin#MANDATED
     * mandated} elements such as the (implicit) default constructor
     * and the implicit {@code values} and {@code valueOf} methods of
     * an enum type.
     *
     * @apiNote As a particular instance of the {@linkplain
     * javax.lang.model.element general accuracy requirements} and the
     * ordering behavior required of this interface, the list of
     * enclosed elements will be returned in the natural order for the
     * originating source of information about the type.  For example,
     * if the information about the type is originating from a source
     * file, the elements will be returned in source code order.
     * (However, in that case the the ordering of {@linkplain
     * Elements.Origin#MANDATED implicitly declared} elements, such as
     * default constructors, is not specified.)
     *
     * @return the enclosed elements in proper order, or an empty list if none
     *
     * @jls 8.8.9 Default Constructor
     * @jls 8.9.3 Enum Members
     */
    @Override
    List<? extends Element> getEnclosedElements();

    /**
     * Returns the <i>nesting kind</i> of this type element.
     *
     * @return the nesting kind of this type element
     */
    NestingKind getNestingKind();

    /**
     * Returns the fully qualified name of this type element.
     * More precisely, it returns the <i>canonical</i> name.
     * For local and anonymous classes, which do not have canonical names,
     * an empty name is returned.
     *
     * <p>The name of a generic type does not include any reference
     * to its formal type parameters.
     * For example, the fully qualified name of the interface
     * {@code java.util.Set<E>} is "{@code java.util.Set}".
     * Nested types use "{@code .}" as a separator, as in
     * "{@code java.util.Map.Entry}".
     *
     * @return the fully qualified name of this class or interface, or
     * an empty name if none
     *
     * @see Elements#getBinaryName
     * @jls 6.7 Fully Qualified Names and Canonical Names
     */
    Name getQualifiedName();

    /**
     * Returns the simple name of this type element.
     *
     * For an anonymous class, an empty name is returned.
     *
     * @return the simple name of this class or interface,
     * an empty name for an anonymous class
     *
     */
    @Override
    Name getSimpleName();

    /**
     * Returns the direct superclass of this type element.
     * If this type element represents an interface or the class
     * {@code java.lang.Object}, then a {@link NoType}
     * with kind {@link TypeKind#NONE NONE} is returned.
     *
     * @return the direct superclass, or a {@code NoType} if there is none
     */
    TypeMirror getSuperclass();

    /**
     * Returns the interface types directly implemented by this class
     * or extended by this interface.
     *
     * @return the interface types directly implemented by this class
     * or extended by this interface, or an empty list if there are none
     */
    List<? extends TypeMirror> getInterfaces();

    /**
     * Returns the formal type parameters of this type element
     * in declaration order.
     *
     * @return the formal type parameters, or an empty list
     * if there are none
     */
    List<? extends TypeParameterElement> getTypeParameters();

    /**
     * {@preview Associated with records, a preview feature of the Java language.
     *
     *           This method is associated with <i>records</i>, a preview
     *           feature of the Java language. Preview features
     *           may be removed in a future release, or upgraded to permanent
     *           features of the Java language.}
     *
     * Returns the record components of this type element in
     * declaration order.
     *
     * @implSpec The default implementations of this method returns an
     * empty and unmodifiable list.
     *
     * @return the record components, or an empty list if there are
     * none
     *
     * @since 14
     */
    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.RECORDS,
                                 essentialAPI=false)
    @SuppressWarnings("preview")
    default List<? extends RecordComponentElement> getRecordComponents() {
        return List.of();
    }

    /**
     * {@preview Associated with sealed classes, a preview feature of the Java language.
     *
     *           This method is associated with <i>sealed classes</i>, a preview
     *           feature of the Java language. Preview features
     *           may be removed in a future release, or upgraded to permanent
     *           features of the Java language.}
     * Returns the permitted classes of this type element in
     * declaration order.
     *
     * @implSpec The default implementations of this method returns an
     * empty and unmodifiable list.
     *
     * @return the permitted classes, or an empty list if there are none
     *
     * @since 15
     */
    @jdk.internal.PreviewFeature(feature=jdk.internal.PreviewFeature.Feature.SEALED_CLASSES,
                                 essentialAPI=false)
    default List<? extends TypeMirror> getPermittedSubclasses() {
        return List.of();
    }

    /**
     * Returns the package of a top-level type and returns the
     * immediately lexically enclosing element for a {@linkplain
     * NestingKind#isNested nested} type.
     *
     * @return the package of a top-level type, the immediately
     * lexically enclosing element for a nested type
     */
    @Override
    Element getEnclosingElement();
}
