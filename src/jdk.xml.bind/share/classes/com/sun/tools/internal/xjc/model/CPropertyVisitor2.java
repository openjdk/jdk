/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model;

/**
 * Visitor for {@link CPropertyInfo}.
 *
 * The number 2 signals number of arguments.
 *
 * @param <R> the return type of this visitor's methods.  Use {@link
 *            Void} for visitors that do not need to return results.
 * @param <P> the type of the additional parameter to this visitor's
 *            methods.  Use {@code Void} for visitors that do not need an
 *            additional parameter.
 *
 * @see CPropertyInfo#accept(CPropertyVisitor2, Object)
 *
 * @author Marcel Valovy
 */
public interface CPropertyVisitor2<R, P> {

    /**
     * Visits a CElementPropertyInfo type.
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  a visitor-specified result
     */
    R visit(CElementPropertyInfo t, P p);

    /**
     * Visits a CAttributePropertyInfo type.
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  a visitor-specified result
     */
    R visit(CAttributePropertyInfo t, P p);

    /**
     * Visits a CValuePropertyInfo type.
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  a visitor-specified result
     */
    R visit(CValuePropertyInfo t, P p);

    /**
     * Visits a CReferencePropertyInfo type.
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  a visitor-specified result
     */
    R visit(CReferencePropertyInfo t, P p);
}
