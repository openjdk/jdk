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

package com.sun.xml.internal.ws.wsdl.writer;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.server.SDDocument;

/**
 * Resolves relative references among the metadata(WSDL, schema)
 * documents.
 *
 * <p>
 * This interface is implemented by the caller of
 * {@link SDDocument#writeTo} method so that the {@link SDDocument} can
 * correctly produce references to other documents.
 *
 * <h2>Usage Example 1</h2>
 * <p>
 * Say: http://localhost/hello?wsdl has reference to
 * <p>
 *   &lt;xsd:import namespace="urn:test:types" schemaLocation="http://localhost/hello?xsd=1"/>
 *
 * <p>
 * Using this class, it is possible to write A.wsdl to a local filesystem with
 * a local file schema import.
 * <p>
 *   &lt;xsd:import namespace="urn:test:types" schemaLocation="hello.xsd"/>
 *
 * @author Jitendra Kotamraju
 */
public interface DocumentLocationResolver {
    /**
     * Produces a relative reference from one document to another.
     *
     * @param namespaceURI
     *      The namespace urI for the referenced document.
     *      for e.g. wsdl:import/@namespace, xsd:import/@namespace
     * @param systemId
     *      The location value for the referenced document.
     *      for e.g. wsdl:import/@location, xsd:import/@schemaLocation
     * @return
     *      The reference to be put inside {@code current} to refer to
     *      {@code referenced}. This can be a relative URL as well as
     *      an absolute. If null is returned, then the document
     *      will produce a "implicit reference" (for example, &lt;xs:import>
     *      without the @schemaLocation attribute, etc).
     */
    @Nullable String getLocationFor(String namespaceURI, String systemId);
}
