/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model;

import java.lang.annotation.*;
import java.util.List;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

/**
 * Represents a construct that can be annotated.
 *
 * A construct is either an {@linkplain
 * javax.lang.model.element.Element element} or a {@linkplain
 * javax.lang.model.type.TypeMirror type}.  Annotations on an element
 * are on a <em>declaration</em>, whereas annotations on a type are on
 * a specific <em>use</em> of a type name.
 *
 * The terms <em>directly present</em>, <em>present</em>,
 * <em>indirectly present</em>, and <em>associated </em> are used
 * throughout this interface to describe precisely which annotations
 * are returned by the methods defined herein.
 *
 * <p>In the definitions below, an annotation <i>A</i> has an
 * annotation type <i>AT</i>. If <i>AT</i> is a repeatable annotation
 * type, the type of the containing annotation is <i>ATC</i>.
 *
 * <p>Annotation <i>A</i> is <em>directly present</em> on a construct
 * <i>C</i> if either:
 *
 * <ul>
 *
 * <li><i>A</i> is explicitly or implicitly declared as applying to
 * the source code representation of <i>C</i>.
 *
 * <p>Typically, if exactly one annotation of type <i>AT</i> appears in
 * the source code of representation of <i>C</i>, then <i>A</i> is
 * explicitly declared as applying to <i>C</i>.
 *
 * If there are multiple annotations of type <i>AT</i> present on
 * <i>C</i>, then if <i>AT</i> is repeatable annotation type, an
 * annotation of type <i>ATC</i> is implicitly declared on <i>C</i>.
 *
 * <li> A representation of <i>A</i> appears in the executable output
 * for <i>C</i>, such as the {@code RuntimeVisibleAnnotations} or
 * {@code RuntimeVisibleParameterAnnotations} attributes of a class
 * file.
 *
 * </ul>
 *
 * <p>An annotation <i>A</i> is <em>present</em> on a
 * construct <i>C</i> if either:
 * <ul>
 *
 * <li><i>A</i> is directly present on <i>C</i>.
 *
 * <li>No annotation of type <i>AT</i> is directly present on
 * <i>C</i>, and <i>C</i> is a class and <i>AT</i> is inheritable
 * and <i>A</i> is present on the superclass of <i>C</i>.
 *
 * </ul>
 *
 * An annotation <i>A</i> is <em>indirectly present</em> on a construct
 * <i>C</i> if both:
 *
 * <ul>
 *
 * <li><i>AT</i> is a repeatable annotation type with a containing
 * annotation type <i>ATC</i>.
 *
 * <li>An annotation of type <i>ATC</i> is directly present on
 * <i>C</i> and <i>A</i> is an annotation included in the result of
 * calling the {@code value} method of the directly present annotation
 * of type <i>ATC</i>.
 *
 * </ul>
 *
 * An annotation <i>A</i> is <em>associated</em> with a construct
 * <i>C</i> if either:
 *
 * <ul>
 *
 * <li> <i>A</i> is directly or indirectly present on <i>C</i>.
 *
 * <li> No annotation of type <i>AT</i> is directly or indirectly
 * present on <i>C</i>, and <i>C</i> is a class, and <i>AT</i> is
 * inheritable, and <i>A</i> is associated with the superclass of
 * <i>C</i>.
 *
 * </ul>
 *
 * @since 1.8
 * @jls 9.6 Annotation Types
 * @jls 9.6.3.3 @Inherited
 */
public interface AnnotatedConstruct {
    /**
     * Returns the annotations that are <em>directly present</em> on
     * this construct.
     *
     * @return the annotations <em>directly present</em> on this
     * construct; an empty list if there are none
     */
    List<? extends AnnotationMirror> getAnnotationMirrors();

    /**
     * Returns this construct's annotation of the specified type if
     * such an annotation is <em>present</em>, else {@code null}.
     *
     * <p> The annotation returned by this method could contain an element
     * whose value is of type {@code Class}.
     * This value cannot be returned directly:  information necessary to
     * locate and load a class (such as the class loader to use) is
     * not available, and the class might not be loadable at all.
     * Attempting to read a {@code Class} object by invoking the relevant
     * method on the returned annotation
     * will result in a {@link MirroredTypeException},
     * from which the corresponding {@link TypeMirror} may be extracted.
     * Similarly, attempting to read a {@code Class[]}-valued element
     * will result in a {@link MirroredTypesException}.
     *
     * <blockquote>
     * <i>Note:</i> This method is unlike others in this and related
     * interfaces.  It operates on runtime reflective information &mdash;
     * representations of annotation types currently loaded into the
     * VM &mdash; rather than on the representations defined by and used
     * throughout these interfaces.  Consequently, calling methods on
     * the returned annotation object can throw many of the exceptions
     * that can be thrown when calling methods on an annotation object
     * returned by core reflection.  This method is intended for
     * callers that are written to operate on a known, fixed set of
     * annotation types.
     * </blockquote>
     *
     * @param <A>  the annotation type
     * @param annotationType  the {@code Class} object corresponding to
     *          the annotation type
     * @return this construct's annotation for the specified
     * annotation type if present, else {@code null}
     *
     * @see #getAnnotationMirrors()
     * @see java.lang.reflect.AnnotatedElement#getAnnotation
     * @see EnumConstantNotPresentException
     * @see AnnotationTypeMismatchException
     * @see IncompleteAnnotationException
     * @see MirroredTypeException
     * @see MirroredTypesException
     * @jls 9.6.1 Annotation Type Elements
     */
    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    /**
     * Returns annotations that are <em>associated</em> with this construct.
     *
     * If there are no annotations associated with this construct, the
     * return value is an array of length 0.
     *
     * The order of annotations which are directly or indirectly
     * present on a construct <i>C</i> is computed as if indirectly present
     * annotations on <i>C</i> are directly present on <i>C</i> in place of their
     * container annotation, in the order in which they appear in the
     * value element of the container annotation.
     *
     * The difference between this method and {@link #getAnnotation(Class)}
     * is that this method detects if its argument is a <em>repeatable
     * annotation type</em>, and if so, attempts to find one or more
     * annotations of that type by "looking through" a container annotation.
     *
     * <p> The annotations returned by this method could contain an element
     * whose value is of type {@code Class}.
     * This value cannot be returned directly:  information necessary to
     * locate and load a class (such as the class loader to use) is
     * not available, and the class might not be loadable at all.
     * Attempting to read a {@code Class} object by invoking the relevant
     * method on the returned annotation
     * will result in a {@link MirroredTypeException},
     * from which the corresponding {@link TypeMirror} may be extracted.
     * Similarly, attempting to read a {@code Class[]}-valued element
     * will result in a {@link MirroredTypesException}.
     *
     * <blockquote>
     * <i>Note:</i> This method is unlike others in this and related
     * interfaces.  It operates on runtime reflective information &mdash;
     * representations of annotation types currently loaded into the
     * VM &mdash; rather than on the representations defined by and used
     * throughout these interfaces.  Consequently, calling methods on
     * the returned annotation object can throw many of the exceptions
     * that can be thrown when calling methods on an annotation object
     * returned by core reflection.  This method is intended for
     * callers that are written to operate on a known, fixed set of
     * annotation types.
     * </blockquote>
     *
     * @param <A>  the annotation type
     * @param annotationType  the {@code Class} object corresponding to
     *          the annotation type
     * @return this construct's annotations for the specified annotation
     *         type if present on this construct, else an empty array
     *
     * @see #getAnnotationMirrors()
     * @see #getAnnotation(Class)
     * @see java.lang.reflect.AnnotatedElement#getAnnotationsByType(Class)
     * @see EnumConstantNotPresentException
     * @see AnnotationTypeMismatchException
     * @see IncompleteAnnotationException
     * @see MirroredTypeException
     * @see MirroredTypesException
     * @jls 9.6 Annotation Types
     * @jls 9.6.1 Annotation Type Elements
     */
    <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType);
}
