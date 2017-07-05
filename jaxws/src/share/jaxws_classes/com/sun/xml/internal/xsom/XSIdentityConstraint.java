/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

/**
 * Identity constraint.
 *
 * @author Kohsuke Kawaguchi
 */
public interface XSIdentityConstraint extends XSComponent {

    /**
     * Gets the {@link XSElementDecl} that owns this identity constraint.
     *
     * @return
     *      never null.
     */
    XSElementDecl getParent();

    /**
     * Name of the identity constraint.
     *
     * A name uniquely identifies this {@link XSIdentityConstraint} within
     * the namespace.
     *
     * @return
     *      never null.
     */
    String getName();

    /**
     * Target namespace of the identity constraint.
     *
     * Just short for <code>getParent().getTargetNamespace()</code>.
     */
    String getTargetNamespace();

    /**
     * Returns the type of the identity constraint.
     *
     * @return
     *      either {@link #KEY},{@link #KEYREF}, or {@link #UNIQUE}.
     */
    short getCategory();

    final short KEY = 0;
    final short KEYREF = 1;
    final short UNIQUE = 2;

    /**
     * Returns the selector XPath expression as string.
     *
     * @return
     *      never null.
     */
    XSXPath getSelector();

    /**
     * Returns the list of field XPaths.
     *
     * @return
     *      a non-empty read-only list of {@link String}s,
     *      each representing the XPath.
     */
    List<XSXPath> getFields();

    /**
     * If this is {@link #KEYREF}, returns the key {@link XSIdentityConstraint}
     * being referenced.
     *
     * @return
     *      always non-null (when {@link #getCategory()}=={@link #KEYREF}).
     * @throws IllegalStateException
     *      if {@link #getCategory()}!={@link #KEYREF}
     */
    XSIdentityConstraint getReferencedKey();
}
