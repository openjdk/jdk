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
