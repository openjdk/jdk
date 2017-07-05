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

package com.sun.xml.internal.xsom;

/**
 * Base interface of all "declarations".
 *
 * @author
 *  Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface XSDeclaration extends XSComponent
{
    /**
     * Target namespace to which this component belongs.
     * <code>""</code> is used to represent the default no namespace.
     */
    String getTargetNamespace();

    /**
     * Gets the (local) name of the declaration.
     *
     * @return null if this component is anonymous.
     */
    String getName();

    /**
     * @deprecated use the isGlobal method, which always returns
     * the opposite of this function. Or the isLocal method.
     */
    boolean isAnonymous();

    /**
     * Returns true if this declaration is a global declaration.
     *
     * Global declarations are those declaration that can be enumerated
     * through the schema object.
     */
    boolean isGlobal();

    /**
     * Returns true if this declaration is a local declaration.
     * Equivalent of <code>!isGlobal()</code>
     */
    boolean isLocal();
}
