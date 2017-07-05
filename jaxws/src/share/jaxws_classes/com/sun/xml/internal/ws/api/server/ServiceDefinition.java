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

package com.sun.xml.internal.ws.api.server;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLModel;

/**
 * Root of the unparsed WSDL and other resources referenced from it.
 * This object represents the description of the service
 * that a {@link WSEndpoint} offers.
 *
 * <p>
 * A description consists of a set of {@link SDDocument}, which
 * each represents a single XML document that forms a part of the
 * descriptor (for example, WSDL might refer to separate schema documents,
 * or a WSDL might refer to another WSDL.)
 *
 * <p>
 * {@link ServiceDefinition} and its descendants are immutable
 * read-only objects. Once they are created, they always return
 * the same value.
 *
 * <h2>Expected Usage</h2>
 * <p>
 * This object is intended to be used for serving the descriptors
 * to remote clients (such as by MEX, or other protocol-specific
 * metadata query, such as HTTP GET with "?wsdl" query string.)
 *
 * <p>
 * This object is <b>NOT</b> intended to be used by other
 * internal components to parse them. For that purpose, use
 * {@link WSDLModel} instead.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ServiceDefinition extends Iterable<SDDocument> {
    /**
     * Gets the "primary" {@link SDDocument} that represents a WSDL.
     *
     * <p>
     * This WSDL eventually refers to all the other {@link SDDocument}s.
     *
     * @return
     *      always non-null.
     */
    @NotNull SDDocument getPrimary();

    /**
     * Adds a filter that is called while writing {@link SDDocument}'s infoset. This
     * filter is applied to the all the other reachable {@link SDDocument}s.
     *
     * @param filter that is called while writing the document
     */
    void addFilter(@NotNull SDDocumentFilter filter);
}
