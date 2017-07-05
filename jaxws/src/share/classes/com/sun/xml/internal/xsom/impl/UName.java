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
package com.sun.xml.internal.xsom.impl;

import java.util.Comparator;

/**
 * UName.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class UName {
    /**
     * @param _nsUri
     *      Use "" to indicate the no namespace.
     */
    public UName( String _nsUri, String _localName, String _qname ) {
        if(_nsUri==null || _localName==null || _qname==null) {
            throw new NullPointerException(_nsUri+" "+_localName+" "+_qname);
        }
        this.nsUri = _nsUri.intern();
        this.localName = _localName.intern();
        this.qname = _qname.intern();
    }

    public UName( String nsUri, String localName ) {
        this(nsUri,localName,localName);
    }

    private final String nsUri;
    private final String localName;
    private final String qname;

    public String getName() { return localName; }
    public String getNamespaceURI() { return nsUri; }
    public String getQualifiedName() { return qname; }

    /**
     * Compares {@link UName}s by their names.
     */
    public static final Comparator comparator = new Comparator() {
        public int compare(Object o1, Object o2) {
            UName lhs = (UName)o1;
            UName rhs = (UName)o2;
            int r = lhs.nsUri.compareTo(rhs.nsUri);
            if(r!=0)    return r;
            return lhs.localName.compareTo(rhs.localName);
        }
    };
}
