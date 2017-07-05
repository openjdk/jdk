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

package com.sun.xml.internal.ws.api.message;

import java.util.List;
import java.util.Iterator;
import java.util.Set;

import javax.xml.namespace.QName;

import com.sun.xml.internal.ws.api.WSBinding;

/**
 * Interface representing all the headers of a {@link Message}
 */
public interface MessageHeaders {
    public void understood(Header header);
    public void understood(QName name);
    public void understood(String nsUri, String localName);
    public Header get(String nsUri, String localName, boolean markAsUnderstood);
    public Header get(QName name, boolean markAsUnderstood);
    public Iterator<Header> getHeaders(String nsUri, String localName, final boolean markAsUnderstood);
    /**
     * Get all headers in specified namespace
     * @param nsUri
     * @param markAsUnderstood
     * @return
     */
    public Iterator<Header> getHeaders(String nsUri, final boolean markAsUnderstood);
    public Iterator<Header> getHeaders(QName headerName, final boolean markAsUnderstood);
    public Iterator<Header> getHeaders();
    public boolean hasHeaders();
    public boolean add(Header header);
    public Header remove(QName name);
    public Header remove(String nsUri, String localName);
    //DONT public Header remove(Header header);
    public void replace(Header old, Header header);

    /**
     * Replaces an existing {@link Header} or adds a new {@link Header}.
     *
     * <p>
     * Order doesn't matter in headers, so this method
     * does not make any guarantee as to where the new header
     * is inserted.
     *
     * @return
     *      always true. Don't use the return value.
     */
    public boolean addOrReplace(Header header);

    /**
     * Return a Set of QNames of headers that have been explicitly marked as understood.
     * If none have been marked, this method could return null
     */
    public Set<QName> getUnderstoodHeaders();

    /**
     * Returns a Set of QNames of headers that satisfy ALL the following conditions:
     * (a) Have mustUnderstand = true
     * (b) have NOT been explicitly marked as understood
     * (c) If roles argument is non-null, the header has isIgnorable = false
     * for the roles argument and SOAP version
     * (d) If non-null binding is passed in, are NOT understood by the binding
     * (e) If (d) is met, the header is NOT in the knownHeaders list passed in
     *
     * @param roles
     * @param knownHeaders
     * @param binding
     * @return
     */
    public Set<QName> getNotUnderstoodHeaders(Set<String> roles, Set<QName> knownHeaders, WSBinding binding);

    /**
     * True if the header has been explicitly marked understood, false otherwise
     * @param header
     * @return
     */
    public boolean isUnderstood(Header header);

    /**
     * True if the header has been explicitly marked understood, false otherwise
     * @param header
     * @return
     */
    public boolean isUnderstood(QName header);

    /**
     * True if the header has been explicitly marked understood, false otherwise
     * @param header
     * @return
     */
    public boolean isUnderstood(String nsUri, String header);

    /**
     * Returns <code>Header</code> instances in a <code>List</code>.
     * @return <code>List</code> containing <code>Header</code> instances
     */
    public List<Header> asList();
}
