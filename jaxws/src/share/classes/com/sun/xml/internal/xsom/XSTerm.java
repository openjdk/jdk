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


package com.sun.xml.internal.xsom;

import com.sun.xml.internal.xsom.visitor.XSTermFunction;
import com.sun.xml.internal.xsom.visitor.XSTermVisitor;
import com.sun.xml.internal.xsom.visitor.XSTermFunctionWithParam;

/**
 * A component that can be referenced from {@link XSParticle}
 *
 * This interface provides a set of type check functions (<code>isXXX</code>),
 * which are essentially:
 *
 * <pre>
 * boolean isXXX() {
 *     return this instanceof XXX;
 * }
 * <pre>
 *
 * and a set of cast functions (<code>asXXX</code>), which are
 * essentially:
 *
 * <pre>
 * XXX asXXX() {
 *     if(isXXX())  return (XXX)this;
 *     else          return null;
 * }
 * </pre>
 */
public interface XSTerm extends XSComponent
{
    void visit( XSTermVisitor visitor );
    <T> T apply( XSTermFunction<T> function );
    <T,P> T apply( XSTermFunctionWithParam<T,P> function, P param );

    // cast functions
    boolean isWildcard();
    boolean isModelGroupDecl();
    boolean isModelGroup();
    boolean isElementDecl();

    XSWildcard asWildcard();
    XSModelGroupDecl asModelGroupDecl();
    XSModelGroup asModelGroup();
    XSElementDecl asElementDecl();
}
