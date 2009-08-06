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

package com.sun.mirror.util;


import com.sun.mirror.declaration.*;


/**
 * Utility methods for operating on declarations.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.lang.model.util.Elements}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface Declarations {

    /**
     * Tests whether one type, method, or field declaration hides another.
     *
     * @param sub the first member
     * @param sup the second member
     * @return <tt>true</tt> if and only if the first member hides
     *          the second
     */
    boolean hides(MemberDeclaration sub, MemberDeclaration sup);

    /**
     * Tests whether one method overrides another.  When a
     * non-abstract method overrides an abstract one, the
     * former is also said to <i>implement</i> the latter.
     *
     * @param sub the first method
     * @param sup the second method
     * @return <tt>true</tt> if and only if the first method overrides
     *          the second
     */
    boolean overrides(MethodDeclaration sub, MethodDeclaration sup);
}
