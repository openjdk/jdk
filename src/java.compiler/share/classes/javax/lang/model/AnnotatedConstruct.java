/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * As defined by <cite>The Java Language Specification</cite>
 * section {@jls 9.7.4}, an annotation on an element is a
 * <dfn>{@index "declaration annotation"}</dfn> and an annotation on a type is a
 * <dfn>{@index "type annotation"}</dfn>.
 *
 * The terms <em>directly present</em>, <em>present</em>,
 * <em>indirectly present</em>, and <em>associated</em> are used
 * throughout this interface to describe precisely which annotations,
 * either declaration annotations or type annotations, are returned by
 * the methods in this interface.
 *
 * <p>In the definitions below, an annotation <i>A</i> has an
 * annotation interface <i>AI</i>. If <i>AI</i> is a repeatable annotation
 * interface, the type of the container annotation is <i>AIC</i>.
 *
 * <p>Annotation <i>A</i> is <dfn>{@index "directly present"}</dfn> on a construct
 * <i>C</i> if either:
 *
 * <ul>
 *
 * <li><i>A</i> is {@linkplain
 * javax.lang.model.util.Elements#getOrigin(AnnotatedConstruct,
 * AnnotationMirror) explicitly or implicitly}
 * declared as applying to
 * the source code representation of <i>C</i>.
 *
 * <p>Typically, if exactly one annotation of type <i>AI</i> appears in
 * the source code of representation of <i>C</i>, then <i>A</i> is
 * explicitly declared as applying to <i>C</i>.
 *
 * An annotation of type <i>AI</i> on a {@linkplain
 * RecordComponentElement record component} can be implicitly propagated
 * down to affiliated mandated members. Type annotations modifying the
 * type of a record component can be also propagated to mandated
 * members. Propagation of the annotations to mandated members is
 * governed by rules given in the <cite>The Java Language
 * Specification</cite> (JLS {@jls 8.10.1}).
 *
 * If there are multiple annotations of type <i>AI</i> present on
 * <i>C</i>, then if <i>AI</i> is a repeatable annotation interface, an
 * annotation of type <i>AIC</i> is {@linkplain javax.lang.model.util.Elements#getOrigin(AnnotatedConstruct, AnnotationMirror) implicitly declared} on <i>C</i>.
 * <li> A representation of <i>A</i> appears in the executable output
 * for <i>C</i>, such as the {@code RuntimeVisibleAnnotations} (JVMS {@jvms 4.7.16}) or
 * {@code RuntimeVisibleParameterAnnotations} (JVMS {@jvms 4.7.17}) attributes of a class
 * file.
 *
 * </ul>
 *
 * <p>An annotation <i>A</i> is <dfn>{@index "present"}</dfn> on a
 * construct <i>C</i> if either:
 * <ul>
 *
 * <li><i>A</i> is directly present on <i>C</i>.
 *
 * <li>No annotation of type <i>AI</i> is directly present on
 * <i>C</i>, and <i>C</i> is a class and <i>AI</i> is inheritable
 * and <i>A</i> is present on the superclass of <i>C</i>.
 *
 * </ul>
 *
 * An annotation <i>A</i> is <dfn>{@index "indirectly present"}</dfn> on a construct
 * <i>C</i> if both:
 *
 * <ul>
 *
 * <li><i>AI</i> is a repeatable annotation interface with a containing
 * annotation interface <i>AIC</i>.
 *
 * <li>An annotation of type <i>AIC</i> is directly present on
 * <i>C</i> and <i>A</i> is an annotation included in the result of
 * calling the {@code value} method of the directly present annotation
 * of type <i>AIC</i>.
 *
 * </ul>
 *
 * An annotation <i>A</i> is <dfn>{@index "associated"}</dfn> with a construct
 * <i>C</i> if either:
 *
 * <ul>
 *
 * <li> <i>A</i> is directly or indirectly present on <i>C</i>.
 *
 * <li> No annotation of type <i>AI</i> is directly or indirectly
 * present on <i>C</i>, and <i>C</i> is a class, and <i>AI</i> is
 * inheritable, and <i>A</i> is associated with the superclass of
 * <i>C</i>.
 *
 * </ul>
 *
 * @since 1.8
 * @jls 9.6 Annotation Interfaces
 * @jls 9.6.4.3 {@code @Inherited}
 * @jls 9.7.4 Where Annotations May Appear
 * @jls 9.7.5 Multiple Annotations of the Same Interface
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
     * {@return this construct's annotation of the specified type if
     * such an annotation is <em>present</em>, else {@code null}}
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
     * representations of annotation interfaces currently loaded into the
     * VM &mdash; rather than on the representations defined by and used
     * throughout these interfaces.  Consequently, calling methods on
     * the returned annotation object can throw many of the exceptions
     * that can be thrown when calling methods on an annotation object
     * returned by core reflection.  This method is intended for
     * callers that are written to operate on a known, fixed set of
     * annotation interfaces.
     * </blockquote>
     *
     * @param <A>  the annotation interface
     * @param annotationType  the {@code Class} object corresponding to
     *          the annotation interface
     *
     * @see #getAnnotationMirrors()
     * @see java.lang.reflect.AnnotatedElement#getAnnotation
     * @see EnumConstantNotPresentException
     * @see AnnotationTypeMismatchException
     * @see IncompleteAnnotationException
     * @see MirroredTypeException
     * @see MirroredTypesException
     * @jls 9.6.1 Annotation Interface Elements
     */
    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    /**
     * Returns annotations of the specified type that are <em>associated</em>
     * with this construct.
     *
     * If there are no annotations of the specified type associated with this
     * construct, the return value is an array of length 0.
     *
     * The order of annotations which are directly or indirectly
     * present on a construct <i>C</i> is computed as if indirectly present
     * annotations on <i>C</i> are directly present on <i>C</i> in place of their
     * container annotation, in the order in which they appear in the
     * value element of the container annotation.
     *
     * The difference between this method and {@link #getAnnotation(Class)}
     * is that this method detects if its argument is a <em>repeatable
     * annotation interface</em>, and if so, attempts to find one or more
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
     * representations of annotation interfaces currently loaded into the
     * VM &mdash; rather than on the representations defined by and used
     * throughout these interfaces.  Consequently, calling methods on
     * the returned annotation object can throw many of the exceptions
     * that can be thrown when calling methods on an annotation object
     * returned by core reflection.  This method is intended for
     * callers that are written to operate on a known, fixed set of
     * annotation interfaces.
     * </blockquote>
     *
     * @param <A>  the annotation interface
     * @param annotationType  the {@code Class} object corresponding to
     *          the annotation interface
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
     * @jls 9.6 Annotation Interfaces
     * @jls 9.6.1 Annotation Interface Elements
     */
    <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType);
}
