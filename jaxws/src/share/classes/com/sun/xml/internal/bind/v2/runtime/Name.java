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

import javax.xml.namespace.QName;

/**
 * The internal representation of an XML name.
 *
 * <p>
 * This class keeps indicies for URI and local name for enabling faster processing.
 *
 * <p>
 * {@link Name}s are ordered lexicographically (nsUri first, local name next.)
 * This is the same order required by canonical XML.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Name implements Comparable<Name> {
    /**
     * Namespace URI. interned.
     */
    public final String nsUri;

    /**
     * Local name. interned.
     */
    public final String localName;

    /**
     * Index -1 is reserved for representing the empty namespace URI of attributes.
     */
    public final short nsUriIndex;
    public final short localNameIndex;

    /**
     * Index of the Name for an EII or AII
     */
    public final short qNameIndex;

    /**
     * Specifies if the Name is associated with an EII or AII
     */
    public final boolean isAttribute;

    Name(int qNameIndex, int nsUriIndex, String nsUri, int localIndex, String localName, boolean isAttribute) {
        this.qNameIndex = (short)qNameIndex;
        this.nsUri = nsUri;
        this.localName = localName;
        this.nsUriIndex = (short)nsUriIndex;
        this.localNameIndex = (short)localIndex;
        this.isAttribute = isAttribute;
    }

    public String toString() {
        return '{'+nsUri+'}'+localName;
    }

    /**
     * Creates a {@link QName} from this.
     */
    public QName toQName() {
        return new QName(nsUri,localName);
    }

    public boolean equals( String nsUri, String localName ) {
        return localName.equals(this.localName) && nsUri.equals(this.nsUri);
    }

    public int compareTo(Name that) {
        int r = this.nsUri.compareTo(that.nsUri);
        if(r!=0)    return r;
        return this.localName.compareTo(that.localName);
    }
}
