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

package com.sun.tools.internal.xjc.model;

/**
 * Visitor for {@link CPropertyInfo}.
 *
 * Ideally it should be defined on the runtime core model, but the runtime is on diet.
 * Hence it's defined here.
 *
 * @see CPropertyInfo#accept(CPropertyVisitor)
 *
 * @author Kohsuke Kawaguchi
 */
public interface CPropertyVisitor<V> {
    V onElement( CElementPropertyInfo p );
    V onAttribute( CAttributePropertyInfo p );
    V onValue( CValuePropertyInfo p );
    V onReference( CReferencePropertyInfo p );
}
