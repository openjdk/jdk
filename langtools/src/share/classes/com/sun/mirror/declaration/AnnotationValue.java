/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.mirror.declaration;

import com.sun.mirror.util.SourcePosition;

/**
 * Represents a value of an annotation type element.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.lang.model.element.AnnotationValue}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface AnnotationValue {

    /**
     * Returns the value.
     * The result has one of the following types:
     * <ul><li> a wrapper class (such as {@link Integer}) for a primitive type
     *     <li> {@code String}
     *     <li> {@code TypeMirror}
     *     <li> {@code EnumConstantDeclaration}
     *     <li> {@code AnnotationMirror}
     *     <li> {@code Collection<AnnotationValue>}
     *          (representing the elements, in order, if the value is an array)
     * </ul>
     *
     * @return the value
     */
    Object getValue();

    /**
     * Returns the source position of the beginning of this annotation value.
     * Returns null if the position is unknown or not applicable.
     *
     * <p>This source position is intended for use in providing diagnostics,
     * and indicates only approximately where an annotation value begins.
     *
     * @return  the source position of the beginning of this annotation value or
     * null if the position is unknown or not applicable
     */
    SourcePosition getPosition();

    /**
     * Returns a string representation of this value.
     * This is returned in a form suitable for representing this value
     * in the source code of an annotation.
     *
     * @return a string representation of this value
     */
    String toString();
}
