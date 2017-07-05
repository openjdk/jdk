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

package com.sun.xml.internal.bind.v2.runtime;

/**
 * Namespace URIs and local names sorted by their indices.
 * Number of Names used for EIIs and AIIs
 *
 * @author Kohsuke Kawaguchi
 */
public final class NameList {
    /**
     * Namespace URIs by their indices. No nulls in this array.
     * Read-only.
     */
    public final String[] namespaceURIs;

    /**
     * For each entry in {@link #namespaceURIs}, whether the namespace URI
     * can be declared as the default. If namespace URI is used in attributes,
     * we always need a prefix, so we can't.
     *
     * True if this URI has to have a prefix.
     */
    public final boolean[] nsUriCannotBeDefaulted;

    /**
     * Local names by their indices. No nulls in this array.
     * Read-only.
     */
    public final String[] localNames;

    /**
     * Number of Names for elements
     */
    public final int numberOfElementNames;

    /**
     * Number of Names for attributes
     */
    public final int numberOfAttributeNames;

    public NameList(String[] namespaceURIs, boolean[] nsUriCannotBeDefaulted, String[] localNames, int numberElementNames, int numberAttributeNames) {
        this.namespaceURIs = namespaceURIs;
        this.nsUriCannotBeDefaulted = nsUriCannotBeDefaulted;
        this.localNames = localNames;
        this.numberOfElementNames = numberElementNames;
        this.numberOfAttributeNames = numberAttributeNames;
    }
}
