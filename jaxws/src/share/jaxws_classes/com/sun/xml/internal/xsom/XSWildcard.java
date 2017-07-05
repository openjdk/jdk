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

import java.util.Iterator;
import java.util.Collection;

import com.sun.xml.internal.xsom.visitor.XSWildcardFunction;
import com.sun.xml.internal.xsom.visitor.XSWildcardVisitor;

/**
 * Wildcard schema component (used for both attribute wildcard
 * and element wildcard.)
 *
 * XSWildcard interface can always be downcasted to either
 * Any, Other, or Union.
 */
public interface XSWildcard extends XSComponent, XSTerm
{
    static final int LAX = 1;
    static final int STRTICT = 2;
    static final int SKIP = 3;
    /**
     * Gets the processing mode.
     *
     * @return
     *      Either LAX, STRICT, or SKIP.
     */
    int getMode();

    /**
     * Returns true if the specified namespace URI is valid
     * wrt this wildcard.
     *
     * @param namespaceURI
     *      Use the empty string to test the default no-namespace.
     */
    boolean acceptsNamespace(String namespaceURI);

    /** Visitor support. */
    void visit(XSWildcardVisitor visitor);
    <T> T apply(XSWildcardFunction<T> function);

    /**
     * <code>##any</code> wildcard.
     */
    interface Any extends XSWildcard {
    }
    /**
     * <code>##other</code> wildcard.
     */
    interface Other extends XSWildcard {
        /**
         * Gets the namespace URI excluded from this wildcard.
         */
        String getOtherNamespace();
    }
    /**
     * Wildcard of a set of namespace URIs.
     */
    interface Union extends XSWildcard {
        /**
         * Short for <code>getNamespaces().iterator()</code>
         */
        Iterator<String> iterateNamespaces();

        /**
         * Read-only list of namespace URIs.
         */
        Collection<String> getNamespaces();
    }
}
