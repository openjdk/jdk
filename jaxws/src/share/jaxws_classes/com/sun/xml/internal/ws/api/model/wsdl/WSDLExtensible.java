/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.model.wsdl;

import java.util.List;

import javax.xml.namespace.QName;

import org.xml.sax.Locator;

/**
 * Interface that represents WSDL concepts that
 * can have extensions.
 *
 * @author Vivek Pandey
 * @author Kohsuke Kawaguchi
 */
public interface WSDLExtensible extends WSDLObject {
    /**
     * Gets all the {@link WSDLExtension}s
     * added through {@link #addExtension(WSDLExtension)}.
     *
     * @return
     *      never null.
     */
    Iterable<WSDLExtension> getExtensions();

    /**
     * Gets all the extensions that is assignable to the given type.
     *
     * <p>
     * This allows clients to find specific extensions in a type-safe
     * and convenient way.
     *
     * @param type
     *      The type of the extension to obtain. Must not be null.
     *
     * @return
     *      Can be an empty fromjava.collection but never null.
     */
    <T extends WSDLExtension> Iterable<T> getExtensions(Class<T> type);

    /**
     * Gets the extension that is assignable to the given type.
     *
     * <p>
     * This is just a convenient version that does
     *
     * <pre>
     * Iterator itr = getExtensions(type);
     * if(itr.hasNext())  return itr.next();
     * else               return null;
     * </pre>
     *
     * @return
     *      null if the extension was not found.
     */
    <T extends WSDLExtension> T getExtension(Class<T> type);

    /**
     * Adds a new {@link WSDLExtension}
     * to this object.
     *
     * @param extension
     *      must not be null.
     */
    void addExtension(WSDLExtension extension);

    /**
     * True if all required WSDL extensions on Port and Binding are understood
     * @return true if all wsdl required extensions on Port and Binding are understood
     */
    public boolean areRequiredExtensionsUnderstood();

    /**
     * Marks extension as not understood
     * @param extnEl QName of extension
     * @param locator Locator
     */
    public void addNotUnderstoodExtension(QName extnEl, Locator locator);

    /**
     * Lists extensions marked as not understood
     * @return List of not understood extensions
     */
    public List<? extends WSDLExtension> getNotUnderstoodExtensions();
}
