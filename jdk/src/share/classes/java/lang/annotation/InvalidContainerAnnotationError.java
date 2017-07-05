/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.annotation;

import java.util.Objects;

/**
 * Thrown to indicate that an annotation type whose declaration is
 * (meta-)annotated with a {@link ContainerFor} annotation is not, in
 * fact, the <em>containing annotation type of the type named by {@link
 * ContainerFor}</em>.
 *
 * @see   java.lang.reflect.AnnotatedElement
 * @since 1.8
 * @jls   9.6 Annotation Types
 * @jls   9.7 Annotations
 */
public class InvalidContainerAnnotationError extends AnnotationFormatError {
    private static final long serialVersionUID = 5023L;

    /**
     * The instance of the erroneous container.
     */
    private transient Annotation container;

    /**
     * The type of the annotation that should be contained in the
     * container.
     */
    private transient Class<? extends Annotation> annotationType;

    /**
     * Constructs a new InvalidContainerAnnotationError with the
     * specified detail message.
     *
     * @param  message the detail message.
     */
    public InvalidContainerAnnotationError(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidContainerAnnotationError with the specified
     * detail message and cause.  Note that the detail message associated
     * with {@code cause} is <i>not</i> automatically incorporated in
     * this error's detail message.
     *
     * @param message the detail message
     * @param cause the cause, may be {@code null}
     */
    public InvalidContainerAnnotationError(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new InvalidContainerAnnotationError with the
     * specified cause and a detail message of {@code (cause == null ?
     * null : cause.toString())} (which typically contains the class
     * and detail message of {@code cause}).
     *
     * @param cause the cause, may be {@code null}
     */
    public InvalidContainerAnnotationError(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs InvalidContainerAnnotationError for the specified
     * container instance and contained annotation type.
     *
     * @param  message the detail message
     * @param  cause the cause, may be {@code null}
     * @param container the erroneous container instance, may be
     *        {@code null}
     * @param annotationType the annotation type intended to be
     *        contained, may be {@code null}
     */
    public InvalidContainerAnnotationError(String message,
                                           Throwable cause,
                                           Annotation container,
                                           Class<? extends Annotation> annotationType) {
        super(message, cause);
        this.container = container;
        this.annotationType = annotationType;
    }

    /**
     * Returns the erroneous container.
     *
     * @return the erroneous container, may return {@code null}
     */
    public Annotation getContainer() {
        return container;
    }

    /**
     * Returns the annotation type intended to be contained. Returns
     * {@code null} if the annotation type intended to be contained
     * could not be determined.
     *
     * @return the annotation type intended to be contained, or {@code
     * null} if unknown
     */
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }
}
