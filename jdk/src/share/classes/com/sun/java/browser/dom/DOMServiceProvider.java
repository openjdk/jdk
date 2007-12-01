/*
 * Copyright 2000 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.java.browser.dom;

public abstract class DOMServiceProvider
{
    /**
     * An empty constructor is provided. Implementations should
     * provide a public constructor so that the DOMService
     * can instantiate instances of the implementation class.
     * Application programmers should not be able to directly
     * construct implementation subclasses of this abstract subclass.
     * The only way an application should be able to obtain a
     * reference to a DOMServiceProvider implementation
     * instance is by using the appropriate methods of the
     * DOMService.
     */
    public DOMServiceProvider()
    {
    }

    /**
     * Returns true if the DOMService can determine the association
     * between the obj and the underlying DOM in the browser.
     */
    public abstract boolean canHandle(Object obj);

    /**
     * Returns the Document object of the DOM.
     */
    public abstract org.w3c.dom.Document getDocument(Object obj) throws DOMUnsupportedException;

    /**
     * Returns the DOMImplemenation object of the DOM.
     */
    public abstract org.w3c.dom.DOMImplementation getDOMImplementation();
}
