/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamWriter;

/**
 * Post-init action for {@link MarshallerImpl} that incorporate the in-scope namespace bindings
 * from a StAX writer.
 *
 * <p>
 * It's always used either with {@link XMLStreamWriter}, {@link XMLEventWriter}, or bare
 * {@link NamespaceContext},
 * but to reduce the # of classes in the runtime I wrote only one class that handles both.
 *
 * @author Kohsuke Kawaguchi
 */
final class StAXPostInitAction implements Runnable {
    private final XMLStreamWriter xsw;
    private final XMLEventWriter xew;
    private final NamespaceContext nsc;
    private final XMLSerializer serializer;

    StAXPostInitAction(XMLStreamWriter xsw,XMLSerializer serializer) {
        this.xsw = xsw;
        this.xew = null;
        this.nsc = null;
        this.serializer = serializer;
    }

    StAXPostInitAction(XMLEventWriter xew,XMLSerializer serializer) {
        this.xsw = null;
        this.xew = xew;
        this.nsc = null;
        this.serializer = serializer;
    }

    StAXPostInitAction(NamespaceContext nsc,XMLSerializer serializer) {
        this.xsw = null;
        this.xew = null;
        this.nsc = nsc;
        this.serializer = serializer;
    }

    public void run() {
        NamespaceContext ns = nsc;
        if(xsw!=null)   ns = xsw.getNamespaceContext();
        if(xew!=null)   ns = xew.getNamespaceContext();

        // StAX javadoc isn't very clear on the behavior,
        // so work defensively in anticipation of broken implementations.
        if(ns==null)
            return;

        // we can't enumerate all the in-scope namespace bindings in StAX,
        // so we only look for the known static namespace URIs.
        // this is less than ideal, but better than nothing.
        for( String nsUri : serializer.grammar.nameList.namespaceURIs ) {
            String p = ns.getPrefix(nsUri);
            if(p!=null)
                serializer.addInscopeBinding(nsUri,p);
        }
    }
}
