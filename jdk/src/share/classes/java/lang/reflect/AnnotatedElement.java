/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.annotation.Annotation;

/**
 * Represents an annotated element of the program currently running in this
 * VM.  This interface allows annotations to be read reflectively.  All
 * annotations returned by methods in this interface are immutable and
 * serializable.  It is permissible for the caller to modify the
 * arrays returned by accessors for array-valued enum members; it will
 * have no affect on the arrays returned to other callers.
 *
 * <p>An annotation A is <em>directly present</em> on an element E if the
 * RuntimeVisibleAnnotations or RuntimeVisibleParameterAnnotations attribute
 * associated with E either:
 * <ul>
 * <li>contains A; or
 * <li>for invocations of get[Declared]Annotations(Class<T>),
 * contains A or exactly one annotation C whose type is the containing
 * annotation type of A's type (JLS 9.6) and whose value element contains A
 * </ul>
 *
 * <p>An annotation A is <em>present</em> on an element E if either:
 * <ul>
 * <li>A is <em>directly present</em> on E; or
 * <li>There are no annotations of A's type which are <em>directly present</em>
 * on E, and E is a class, and A's type is inheritable (JLS 9.6.3.3), and A is
 * present on the superclass of E
 * </ul>
 *
 * <p>If an annotation returned by a method in this interface contains
 * (directly or indirectly) a {@link Class}-valued member referring to
 * a class that is not accessible in this VM, attempting to read the class
 * by calling the relevant Class-returning method on the returned annotation
 * will result in a {@link TypeNotPresentException}.
 *
 * <p>Similarly, attempting to read an enum-valued member will result in
 * a {@link EnumConstantNotPresentException} if the enum constant in the
 * annotation is no longer present in the enum type.
 *
 * <p>Attempting to read annotations of a repeatable annotation type T
 * that are contained in an annotation whose type is not, in fact, the
 * containing annotation type of T will result in an
 * InvalidContainerAnnotationError.
 *
 * <p>Finally, attempting to read a member whose definition has evolved
 * incompatibly will result in a {@link
 * java.lang.annotation.AnnotationTypeMismatchException} or an
 * {@link java.lang.annotation.IncompleteAnnotationException}.
 *
 * @see java.lang.EnumConstantNotPresentException
 * @see java.lang.TypeNotPresentException
 * @see java.lang.annotation.AnnotationFormatError
 * @see java.lang.annotation.AnnotationTypeMismatchException
 * @see java.lang.annotation.IncompleteAnnotationException
 * @see java.lang.annotation.InvalidContainerAnnotationError
 * @since 1.5
 * @author Josh Bloch
 */
public interface AnnotatedElement {
    /**
     * Returns true if an annotation for the specified type
     * is present on this element, else false.  This method
     * is designed primarily for convenient access to marker annotations.
     *
     * <p>The truth value returned by this method is equivalent to:
     * {@code getAnnotation(annotationClass) != null}
     *
     * <p>The body of the default method is specified to be the code
     * above.
     *
     * @param annotationClass the Class object corresponding to the
     *        annotation type
     * @return true if an annotation for the specified annotation
     *     type is present on this element, else false
     * @throws NullPointerException if the given annotation class is null
     * @since 1.5
     */
    default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

   /**
     * Returns this element's annotation for the specified type if
     * such an annotation is present, else null.
     *
     * @param annotationClass the Class object corresponding to the
     *        annotation type
     * @return this element's annotation for the specified annotation type if
     *     present on this element, else null
     * @throws NullPointerException if the given annotation class is null
     * @since 1.5
     */
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    /**
     * Returns an array of all this element's annotations for the
     * specified type if one or more of such annotation is present,
     * else an array of length zero.
     *
     * The caller of this method is free to modify the returned array;
     * it will have no effect on the arrays returned to other callers.
     *
     * @param annotationClass the Class object corresponding to the
     *        annotation type
     * @return all this element's annotations for the specified annotation type if
     *     present on this element, else an array of length zero
     * @throws NullPointerException if the given annotation class is null
     * @since 1.8
     */
    <T extends Annotation> T[] getAnnotations(Class<T> annotationClass);

    /**
     * Returns annotations that are <em>present</em> on this element.
     *
     * If there are no annotations <em>present</em> on this element, the return
     * value is an array of length 0.
     *
     * The caller of this method is free to modify the returned array; it will
     * have no effect on the arrays returned to other callers.
     *
     * @return annotations present on this element
     * @since 1.5
     */
    Annotation[] getAnnotations();

    /**
     * Returns this element's annotation for the specified type if
     * such an annotation is present, else null.
     *
     * This method ignores inherited annotations. (Returns null if no
     * annotations are directly present on this element.)
     *
     * @param annotationClass the Class object corresponding to the
     *        annotation type
     * @return this element's annotation for the specified annotation type if
     *     present on this element, else null
     * @throws NullPointerException if the given annotation class is null
     * @since 1.8
     */
    <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass);

   /**
     * Returns an array of all this element's annotations for the
     * specified type if one or more of such annotation is directly
     * present, else an array of length zero.
     *
     * This method ignores inherited annotations. (Returns
     * an array of length zero if no annotations are directly present
     * on this element.)  The caller of this method is free to modify
     * the returned array; it will have no effect on the arrays
     * returned to other callers.
     *
     * @param annotationClass the Class object corresponding to the
     *        annotation type
     * @return all this element's annotations for the specified annotation type if
     *     present on this element, else an array of length zero
     * @throws NullPointerException if the given annotation class is null
     * @since 1.8
     */
    <T extends Annotation> T[] getDeclaredAnnotations(Class<T> annotationClass);

    /**
     * Returns annotations that are <em>directly present</em> on this element.
     * This method ignores inherited annotations.
     *
     * If there are no annotations <em>directly present</em> on this element,
     * the return value is an array of length 0.
     *
     * The caller of this method is free to modify the returned array; it will
     * have no effect on the arrays returned to other callers.
     *
     * @return annotations directly present on this element
     * @since 1.5
     */
    Annotation[] getDeclaredAnnotations();
}
