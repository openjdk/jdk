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

package com.sun.xml.internal.ws.api.model.wsdl;

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
}
