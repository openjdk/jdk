/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.bind.v2.model.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.xml.bind.annotation.XmlTransient;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.v2.model.core.ErrorHandler;

/**
 * Reads annotations for the given property.
 *
 * <p>
 * This is the lowest abstraction that encapsulates the difference
 * between reading inline annotations and external binding files.
 *
 * <p>
 * Because the former operates on a {@link Field} and {@link Method}
 * while the latter operates on a "property", the methods defined
 * on this interface takes both, and the callee gets to choose which
 * to use.
 *
 * <p>
 * Most of the get method takes {@link Locatable}, which points to
 * the place/context in which the annotation is read. The returned
 * annotation also implements {@link Locatable} (so that it can
 * point to the place where the annotation is placed), and its
 * {@link Locatable#getUpstream()} will return the given
 * {@link Locatable}.
 *
 *
 * <p>
 * Errors found during reading annotations are reported through the error handler.
 * A valid {@link ErrorHandler} must be registered before the {@link AnnotationReader}
 * is used.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public interface AnnotationReader<T,C,F,M> {

    /**
     * Sets the error handler that receives errors found
     * during reading annotations.
     *
     * @param errorHandler
     *      must not be null.
     */
    void setErrorHandler(ErrorHandler errorHandler);

    /**
     * Reads an annotation on a property that consists of a field.
     */
    <A extends Annotation> A getFieldAnnotation(Class<A> annotation,
                                                F field, Locatable srcpos);

    /**
     * Checks if the given field has an annotation.
     */
    boolean hasFieldAnnotation(Class<? extends Annotation> annotationType, F field);

    /**
     * Checks if a class has the annotation.
     */
    boolean hasClassAnnotation(C clazz, Class<? extends Annotation> annotationType);

    /**
     * Gets all the annotations on a field.
     */
    Annotation[] getAllFieldAnnotations(F field, Locatable srcPos);

    /**
     * Reads an annotation on a property that consists of a getter and a setter.
     *
     */
    <A extends Annotation> A getMethodAnnotation(Class<A> annotation,
                                                 M getter, M setter, Locatable srcpos);

    /**
     * Checks if the given method has an annotation.
     */
    boolean hasMethodAnnotation(Class<? extends Annotation> annotation, String propertyName, M getter, M setter, Locatable srcPos);

    /**
     * Gets all the annotations on a method.
     *
     * @param srcPos
     *      the location from which this annotation is read.
     */
    Annotation[] getAllMethodAnnotations(M method, Locatable srcPos);

    // TODO: we do need this to read certain annotations,
    // but that shows inconsistency wrt the spec. consult the spec team about the abstraction.
    <A extends Annotation> A getMethodAnnotation(Class<A> annotation, M method, Locatable srcpos );

    boolean hasMethodAnnotation(Class<? extends Annotation> annotation, M method );

    /**
     * Reads an annotation on a parameter of the method.
     *
     * @return null
     *      if the annotation was not found.
     */
    @Nullable
    <A extends Annotation> A getMethodParameterAnnotation(
            Class<A> annotation, M method, int paramIndex, Locatable srcPos );

    /**
     * Reads an annotation on a class.
     */
    @Nullable
    <A extends Annotation> A getClassAnnotation(Class<A> annotation, C clazz, Locatable srcpos) ;

    /**
     * Reads an annotation on the package that the given class belongs to.
     */
    @Nullable
    <A extends Annotation> A getPackageAnnotation(Class<A> annotation, C clazz, Locatable srcpos);

    /**
     * Reads a value of an annotation that returns a Class object.
     *
     * <p>
     * Depending on the underlying reflection library, you can't always
     * obtain the {@link Class} object directly (see the APT MirrorTypeException
     * for example), so use this method to avoid that.
     *
     * @param name
     *      The name of the annotation parameter to be read.
     */
    T getClassValue( Annotation a, String name );

    /**
     * Similar to {@link #getClassValue(Annotation, String)} method but
     * obtains an array parameter.
     */
    T[] getClassArrayValue( Annotation a, String name );
}
