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

package com.sun.xml.internal.ws.server;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.api.server.SDDocumentFilter;
import com.sun.xml.internal.ws.api.server.ServiceDefinition;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * {@link ServiceDefinition} implementation.
 *
 * <p>
 * You construct a {@link ServiceDefinitionImpl} by first constructing
 * a list of {@link SDDocumentImpl}s.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ServiceDefinitionImpl implements ServiceDefinition {
    private final List<SDDocumentImpl> docs;

    private final Map<String,SDDocumentImpl> bySystemId;
    private final @NotNull SDDocumentImpl primaryWsdl;

    /**
     * Set when {@link WSEndpointImpl} is created.
     */
    /*package*/ WSEndpointImpl<?> owner;

    /*package*/ final List<SDDocumentFilter> filters = new ArrayList<SDDocumentFilter>();

    /**
     * @param docs
     *      List of {@link SDDocumentImpl}s to form the description.
     *      There must be at least one entry.
     *      The first document is considered {@link #getPrimary() primary}.
     */
    public ServiceDefinitionImpl(List<SDDocumentImpl> docs, @NotNull SDDocumentImpl primaryWsdl) {
        assert docs.contains(primaryWsdl);
        this.docs = docs;
        this.primaryWsdl = primaryWsdl;

        this.bySystemId = new HashMap<String, SDDocumentImpl>(docs.size());
        for (SDDocumentImpl doc : docs) {
            bySystemId.put(doc.getURL().toExternalForm(),doc);

            assert doc.owner==null;
            doc.owner = this;
        }
    }

    /**
     * The owner is set when {@link WSEndpointImpl} is created.
     */
    /*package*/ void setOwner(WSEndpointImpl<?> owner) {
        assert owner!=null && this.owner==null;
        this.owner = owner;
    }

    public @NotNull SDDocument getPrimary() {
        return primaryWsdl;
    }

    public void addFilter(SDDocumentFilter filter) {
        filters.add(filter);
    }

    public Iterator<SDDocument> iterator() {
        return (Iterator)docs.iterator();
    }

    /**
     * @see #getBySystemId(String)
     */
    public SDDocument getBySystemId(URL systemId) {
        return getBySystemId(systemId.toString());
    }

    /**
     * Gets the {@link SDDocumentImpl} whose {@link SDDocumentImpl#getURL()}
     * returns the specified value.
     *
     * @return
     *      null if none is found.
     */
    public SDDocumentImpl getBySystemId(String systemId) {
        return bySystemId.get(systemId);
    }
}
