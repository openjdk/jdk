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

package com.sun.xml.internal.ws.fault;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.bind.annotation.XmlAnyElement;
import java.util.ArrayList;
import java.util.List;

/**
 * &lt;env:Detail>
 *     &lt;m:MaxTime>P5M</m:MaxTime>
 * &lt;/env:Detail>
 */
class DetailType {
    /**
     * The detail entry could be 0 or more elements. Perhaps some elements may be
     * known to JAXB while others can be handled using DOMHandler.
     *
     * Even though the jaxbContext is aware of the detail jaxbBean but we get the list of
     * {@link org.w3c.dom.Node}s.
     *
     * this is because since we unmarshall using {@link com.sun.xml.internal.bind.api.Bridge} all we're
     * going to get during unmarshalling is {@link org.w3c.dom.Node} and not the jaxb bean instances.
     *
     * TODO: For now detailEntry would be List of Node isntead of Object and it needs to be changed to
     * {@link Object} once we have better solution that working thru {@link com.sun.xml.internal.bind.api.Bridge}
     */
    @XmlAnyElement
    private final List<Element> detailEntry = new ArrayList<Element>();

    @NotNull
    List<Element> getDetails() {
        return detailEntry;
    }

    /**
     * Gets the n-th detail object, or null if no such item exists.
     */
    @Nullable
    Node getDetail(int n) {
        if(n < detailEntry.size())
            return detailEntry.get(n);
        else
            return null;
    }

    DetailType(Element detailObject) {
        if(detailObject != null)
            detailEntry.add(detailObject);
    }

    DetailType() {
    }
}
