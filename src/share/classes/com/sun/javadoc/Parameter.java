/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * Parameter information.
 * This includes a parameter type and parameter name.
 *
 * @author Robert Field
 */
public interface Parameter {

    /**
     * Get the type of this parameter.
     */
    Type type();

    /**
     * Get local name of this parameter.
     * For example if parameter is the short 'index', returns "index".
     */
    String name();

    /**
     * Get type name of this parameter.
     * For example if parameter is the short 'index', returns "short".
     * <p>
     * This method returns a complete string
     * representation of the type, including the dimensions of arrays and
     * the type arguments of parameterized types.  Names are qualified.
     */
    String typeName();

    /**
     * Returns a string representation of the parameter.
     * <p>
     * For example if parameter is the short 'index', returns "short index".
     *
     * @return type and parameter name of this parameter.
     */
    String toString();

    /**
     * Get the annotations of this parameter.
     * Return an empty array if there are none.
     *
     * @return the annotations of this parameter.
     * @since 1.5
     */
    AnnotationDesc[] annotations();
}
