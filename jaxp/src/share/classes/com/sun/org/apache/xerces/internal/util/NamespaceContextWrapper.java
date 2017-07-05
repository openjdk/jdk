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

package com.sun.org.apache.xerces.internal.util;

import java.util.Enumeration;
import java.util.Vector;
import javax.xml.namespace.NamespaceContext;

/**
 * Writing a wrapper to re-use most of the namespace functionality
 * already provided by NamespaceSupport, which implements NamespaceContext
 * from XNI. It would be good if we can change the XNI NamespaceContext
 * interface to implement the JAXP NamespaceContext interface.
 *
 * Note that NamespaceSupport assumes the use of symbols. Since this class
 * can be exposed to the application, we must intern all Strings before
 * calling NamespaceSupport methods.
 *
 * @author  Neeraj Bajaj, Sun Microsystems, inc.
 * @author Santiago.PericasGeertsen@sun.com
 *
 */
public class NamespaceContextWrapper implements NamespaceContext {

    private com.sun.org.apache.xerces.internal.xni.NamespaceContext fNamespaceContext;

    public NamespaceContextWrapper(NamespaceSupport namespaceContext) {
        fNamespaceContext = namespaceContext ;
    }

    public String getNamespaceURI(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix can't be null");
        }
        return fNamespaceContext.getURI(prefix.intern());
    }

    public String getPrefix(String namespaceURI) {
        if (namespaceURI == null || namespaceURI.trim().length() == 0) {
            throw new IllegalArgumentException("URI can't be null or empty String");
        }
        return fNamespaceContext.getPrefix(namespaceURI.intern());
    }

    /**
     * TODO: Namespace doesn't give information giving multiple prefixes for
     * the same namespaceURI.
     */
    public java.util.Iterator getPrefixes(String namespaceURI) {
        if (namespaceURI == null || namespaceURI.trim().length() == 0) {
            throw new IllegalArgumentException("URI can't be null or empty String");
        }
        else {
            Vector vector =
                ((NamespaceSupport) fNamespaceContext).getPrefixes(namespaceURI.intern());
            return vector.iterator();
        }
    }

    /**
     * This method supports all functions in the NamespaceContext utility class
     */
    public com.sun.org.apache.xerces.internal.xni.NamespaceContext getNamespaceContext() {
        return fNamespaceContext;
    }

}
