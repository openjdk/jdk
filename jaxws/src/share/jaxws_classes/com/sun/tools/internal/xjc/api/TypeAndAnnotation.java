/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.api;

import com.sun.codemodel.internal.JAnnotatable;
import com.sun.codemodel.internal.JType;

/**
 * Java type and associated JAXB annotations.
 *
 * @author Kohsuke Kawaguchi
 */
public interface TypeAndAnnotation {
    /**
     * Returns the Java type.
     *
     * <p>
     * {@link JType} is a representation of a Java type in a codeModel.
     * If you just need the fully-qualified class name, call {@link JType#fullName()}.
     *
     * @return
     *      never be null.
     */
    JType getTypeClass();

    /**
     * Annotates the given program element by additional JAXB annotations that need to be there
     * at the point of reference.
     */
    void annotate( JAnnotatable programElement );

    /**
     * Two {@link TypeAndAnnotation} are equal if they
     * has the same type and annotations.
     */
    boolean equals(Object o);
}
