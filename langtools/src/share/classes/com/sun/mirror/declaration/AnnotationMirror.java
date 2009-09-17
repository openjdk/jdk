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

import java.util.Map;
import com.sun.mirror.type.AnnotationType;
import com.sun.mirror.util.SourcePosition;


/**
 * Represents an annotation.  An annotation associates a value with
 * each element of an annotation type.
 *
 * <p> Annotations should not be compared using reference-equality
 * ("<tt>==</tt>").  There is no guarantee that any particular
 * annotation will always be represented by the same object.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.lang.model.element.AnnotationMirror}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface AnnotationMirror {

    /**
     * Returns the annotation type of this annotation.
     *
     * @return the annotation type of this annotation
     */
    AnnotationType getAnnotationType();

    /**
     * Returns the source position of the beginning of this annotation.
     * Returns null if the position is unknown or not applicable.
     *
     * <p>This source position is intended for use in providing diagnostics,
     * and indicates only approximately where an annotation begins.
     *
     * @return  the source position of the beginning of this annotation or
     * null if the position is unknown or not applicable
     */
    SourcePosition getPosition();

    /**
     * Returns this annotation's elements and their values.
     * This is returned in the form of a map that associates elements
     * with their corresponding values.
     * Only those elements and values explicitly present in the
     * annotation are included, not those that are implicitly assuming
     * their default values.
     * The order of the map matches the order in which the
     * elements appear in the annotation's source.
     *
     * @return this annotation's elements and their values,
     * or an empty map if there are none
     */
    Map<AnnotationTypeElementDeclaration, AnnotationValue> getElementValues();
}
