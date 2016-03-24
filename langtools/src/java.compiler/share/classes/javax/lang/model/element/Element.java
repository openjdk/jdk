/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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


import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.IncompleteAnnotationException;
import java.util.List;
import java.util.Set;

import javax.lang.model.type.*;
import javax.lang.model.util.*;


/**
 * Represents a program element such as a package, class, or method.
 * Each element represents a static, language-level construct
 * (and not, for example, a runtime construct of the virtual machine).
 *
 * <p> Elements should be compared using the {@link #equals(Object)}
 * method.  There is no guarantee that any particular element will
 * always be represented by the same object.
 *
 * <p> To implement operations based on the class of an {@code
 * Element} object, either use a {@linkplain ElementVisitor visitor} or
 * use the result of the {@link #getKind} method.  Using {@code
 * instanceof} is <em>not</em> necessarily a reliable idiom for
 * determining the effective class of an object in this modeling
 * hierarchy since an implementation may choose to have a single object
 * implement multiple {@code Element} subinterfaces.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @author Peter von der Ah&eacute;
 * @see Elements
 * @see TypeMirror
 * @since 1.6
 */
public interface Element extends javax.lang.model.AnnotatedConstruct {
    /**
     * Returns the type defined by this element.
     *
     * <p> A generic element defines a family of types, not just one.
     * If this is a generic element, a <i>prototypical</i> type is
     * returned.  This is the element's invocation on the
     * type variables corresponding to its own formal type parameters.
     * For example,
     * for the generic class element {@code C<N extends Number>},
     * the parameterized type {@code C<N>} is returned.
     * The {@link Types} utility interface has more general methods
     * for obtaining the full range of types defined by an element.
     *
     * @see Types
     *
     * @return the type defined by this element
     */
    TypeMirror asType();

    /**
     * Returns the {@code kind} of this element.
     *
     * @return the kind of this element
     */
    ElementKind getKind();

    /**
     * Returns the modifiers of this element, excluding annotations.
     * Implicit modifiers, such as the {@code public} and {@code static}
     * modifiers of interface members, are included.
     *
     * @return the modifiers of this element, or an empty set if there are none
     */
    Set<Modifier> getModifiers();

    /**
     * Returns the simple (unqualified) name of this element.  The
     * name of a generic type does not include any reference to its
     * formal type parameters.
     *
     * For example, the simple name of the type element {@code
     * java.util.Set<E>} is {@code "Set"}.
     *
     * If this element represents an unnamed {@linkplain
     * PackageElement#getSimpleName package} or unnamed {@linkplain
     * ModuleElement#getSimpleName module}, an empty name is returned.
     *
     * If it represents a {@linkplain ExecutableElement#getSimpleName
     * constructor}, the name "{@code <init>}" is returned.  If it
     * represents a {@linkplain ExecutableElement#getSimpleName static
     * initializer}, the name "{@code <clinit>}" is returned.
     *
     * If it represents an {@linkplain TypeElement#getSimpleName
     * anonymous class} or {@linkplain ExecutableElement#getSimpleName
     * instance initializer}, an empty name is returned.
     *
     * @return the simple name of this element
     * @see PackageElement#getSimpleName
     * @see ExecutableElement#getSimpleName
     * @see TypeElement#getSimpleName
     * @see VariableElement#getSimpleName
     * @see ModuleElement#getSimpleName
     */
    Name getSimpleName();

    /**
     * Returns the innermost element
     * within which this element is, loosely speaking, enclosed.
     * <ul>
     * <li> If this element is one whose declaration is lexically enclosed
     * immediately within the declaration of another element, that other
     * element is returned.
     *
     * <li> If this is a {@linkplain TypeElement#getEnclosingElement
     * top-level type}, its package is returned.
     *
     * <li> If this is a {@linkplain
     * PackageElement#getEnclosingElement package}, its module is
     * returned.
     *
     * <li> If this is a {@linkplain
     * TypeParameterElement#getEnclosingElement type parameter},
     * {@linkplain TypeParameterElement#getGenericElement the
     * generic element} of the type parameter is returned.
     *
     * <li> If this is a {@linkplain
     * VariableElement#getEnclosingElement method or constructor
     * parameter}, {@linkplain ExecutableElement the executable
     * element} which declares the parameter is returned.
     *
     * <li> If this is a {@linkplain ModuleElement#getEnclosingElement
     * module}, {@code null} is returned.
     *
     * </ul>
     *
     * @return the enclosing element, or {@code null} if there is none
     * @see Elements#getPackageOf
     */
    Element getEnclosingElement();

    /**
     * Returns the elements that are, loosely speaking, directly
     * enclosed by this element.
     *
     * A {@linkplain TypeElement#getEnclosedElements class or
     * interface} is considered to enclose the fields, methods,
     * constructors, and member types that it directly declares.
     *
     * A {@linkplain PackageElement#getEnclosedElements package}
     * encloses the top-level classes and interfaces within it, but is
     * not considered to enclose subpackages.
     *
     * A {@linkplain ModuleElement#getEnclosedElements module}
     * encloses packages within it.
     *
     * Other kinds of elements are not currently considered to enclose
     * any elements; however, that may change as this API or the
     * programming language evolves.
     *
     * <p>Note that elements of certain kinds can be isolated using
     * methods in {@link ElementFilter}.
     *
     * @return the enclosed elements, or an empty list if none
     * @see TypeElement#getEnclosedElements
     * @see PackageElement#getEnclosedElements
     * @see ModuleElement#getEnclosedElements
     * @see Elements#getAllMembers
     * @jls 8.8.9 Default Constructor
     * @jls 8.9 Enums
     */
    List<? extends Element> getEnclosedElements();

    /**
     * Returns {@code true} if the argument represents the same
     * element as {@code this}, or {@code false} otherwise.
     *
     * <p>Note that the identity of an element involves implicit state
     * not directly accessible from the element's methods, including
     * state about the presence of unrelated types.  Element objects
     * created by different implementations of these interfaces should
     * <i>not</i> be expected to be equal even if &quot;the same&quot;
     * element is being modeled; this is analogous to the inequality
     * of {@code Class} objects for the same class file loaded through
     * different class loaders.
     *
     * @param obj  the object to be compared with this element
     * @return {@code true} if the specified object represents the same
     *          element as this
     */
    @Override
    boolean equals(Object obj);

    /**
     * Obeys the general contract of {@link Object#hashCode Object.hashCode}.
     *
     * @see #equals
     */
    @Override
    int hashCode();


    /**
     * {@inheritDoc}
     *
     * <p> To get inherited annotations as well, use {@link
     * Elements#getAllAnnotationMirrors(Element)
     * getAllAnnotationMirrors}.
     *
     * @since 1.6
     */
    @Override
    List<? extends AnnotationMirror> getAnnotationMirrors();

    /**
     * {@inheritDoc}
     * @since 1.6
     */
    @Override
    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    /**
     * Applies a visitor to this element.
     *
     * @param <R> the return type of the visitor's methods
     * @param <P> the type of the additional parameter to the visitor's methods
     * @param v   the visitor operating on this element
     * @param p   additional parameter to the visitor
     * @return a visitor-specified result
     */
    <R, P> R accept(ElementVisitor<R, P> v, P p);
}
