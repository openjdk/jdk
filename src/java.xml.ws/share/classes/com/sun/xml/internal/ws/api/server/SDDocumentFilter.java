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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;

/**
 * Provides a way to filter {@link SDDocument} infoset while writing it. These
 * filter objects can be added to {@link ServiceDefinition} using
 * {@link ServiceDefinition#addFilter(SDDocumentFilter)}
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public interface SDDocumentFilter {
    /**
     * Returns a wrapped XMLStreamWriter on top of passed-in XMLStreamWriter.
     * It works like any filtering API for e.g. {@link java.io.FilterOutputStream}.
     * The method returns a XMLStreamWriter that calls the same methods on original
     * XMLStreamWriter with some modified events. The end result is some infoset
     * is filtered before it reaches the original writer and the infoset writer
     * doesn't have to change any code to incorporate this filter.
     *
     * @param doc gives context for the filter. This should only be used to query
     *  read-only information. Calling doc.writeTo() may result in infinite loop.
     * @param w Original XMLStreamWriter
     * @return Filtering {@link XMLStreamWriter}
     */
    XMLStreamWriter filter(SDDocument doc, XMLStreamWriter w) throws XMLStreamException, IOException;
}
