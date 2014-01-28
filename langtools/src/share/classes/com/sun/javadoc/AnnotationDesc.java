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

package com.sun.javadoc;


/**
 * Represents an annotation.
 * An annotation associates a value with each element of an annotation type.
 *
 * @author Scott Seligman
 * @since 1.5
 */
public interface AnnotationDesc {

    /**
     * Returns the annotation type of this annotation.
     *
     * @return the annotation type of this annotation.
     */
    AnnotationTypeDoc annotationType();

    /**
     * Returns this annotation's elements and their values.
     * Only those explicitly present in the annotation are
     * included, not those assuming their default values.
     * Returns an empty array if there are none.
     *
     * @return this annotation's elements and their values.
     */
    ElementValuePair[] elementValues();

    /**
     * Check for the synthesized bit on the annotation.
     *
     * @return true if the annotation is synthesized.
     */
    boolean isSynthesized();

    /**
     * Represents an association between an annotation type element
     * and one of its values.
     *
     * @author Scott Seligman
     * @since 1.5
     */
    public interface ElementValuePair {

        /**
         * Returns the annotation type element.
         *
         * @return the annotation type element.
         */
        AnnotationTypeElementDoc element();

        /**
         * Returns the value associated with the annotation type element.
         *
         * @return the value associated with the annotation type element.
         */
        AnnotationValue value();
    }
}
