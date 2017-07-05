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

import com.sun.istack.internal.Nullable;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.namespace.QName;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedData;

/**
 * Represents an individual document that forms a {@link ServiceDefinition}.
 *
 * <pre>
 * TODO:
 *      how does those documents refer to each other?
 *
 * </pre>
 *
 * @author Jitendra Kotamraju
 */
@ManagedData
public interface SDDocument {

    /**
     * Gets the root tag name of this document.
     *
     * <p>
     * This can be used to identify a kind of document quickly
     * (such as schema, WSDL, ...)
     *
     * @return
     *      always non-null.
     */
    @ManagedAttribute
    QName getRootName();

    /**
     * Returns true if this document is WSDL.
     */
    @ManagedAttribute
    boolean isWSDL();

    /**
     * Returns true if this document is schema.
     */
    @ManagedAttribute
    boolean isSchema();

    /**
     * returns the referenced documents
     */
    @ManagedAttribute
    Set<String> getImports();

    /**
     * Gets the system ID of the document where it's taken from. Generated documents
     * use a fake URL that can be used to resolve relative URLs. So donot use this URL
     * for reading or writing.
     */
    @ManagedAttribute
    URL getURL();

    /**
     * Writes the document to the given {@link OutputStream}.
     *
     * <p>
     * Since {@link ServiceDefinition} doesn't know which endpoint address
     * {@link Adapter} is serving to, (and often it serves multiple URLs
     * simultaneously), this method takes the PortAddressResolver as a parameter,
     * so that it can produce the corret address information in the generated WSDL.
     *
     * @param portAddressResolver
     *      An endpoint address resolver that gives endpoint address for a WSDL
     *      port. Can be null.
     * @param resolver
     *      Used to resolve relative references among documents.
     * @param os
     *      The {@link OutputStream} that receives the generated document.
     *
     * @throws IOException
     *      if there was a failure reported from the {@link OutputStream}.
     */
    void writeTo(@Nullable PortAddressResolver portAddressResolver,
            DocumentAddressResolver resolver, OutputStream os) throws IOException;

    /**
     * Writes the document to the given {@link XMLStreamWriter}.
     *
     * <p>
     * The same as {@link #writeTo(PortAddressResolver,DocumentAddressResolver,OutputStream)} except
     * it writes to an {@link XMLStreamWriter}.
     *
     * <p>
     * The implementation must not call {@link XMLStreamWriter#writeStartDocument()}
     * nor {@link XMLStreamWriter#writeEndDocument()}. Those are the caller's
     * responsibility.
     *
     * @throws XMLStreamException
     *      if the {@link XMLStreamWriter} reports an error.
     */
    void writeTo(PortAddressResolver portAddressResolver,
            DocumentAddressResolver resolver, XMLStreamWriter out) throws XMLStreamException, IOException;

    /**
     * {@link SDDocument} that represents an XML Schema.
     */
    interface Schema extends SDDocument {
        /**
         * Gets the target namepsace of this schema.
         */
        @ManagedAttribute
        String getTargetNamespace();
    }

    /**
     * {@link SDDocument} that represents a WSDL.
     */
    interface WSDL extends SDDocument {
        /**
         * Gets the target namepsace of this schema.
         */
        @ManagedAttribute
        String getTargetNamespace();

        /**
         * This WSDL has a portType definition
         * that matches what {@link WSEndpoint} is serving.
         *
         * TODO: does this info needs to be exposed?
         */
        @ManagedAttribute
        boolean hasPortType();

        /**
         * This WSDL has a service definition
         * that matches the {@link WSEndpoint}.
         *
         * TODO: does this info need to be exposed?
         */
        @ManagedAttribute
        boolean hasService();

        /**
         * All &lt;service> names that were in this WSDL, or empty set if there was none.
         * Used for error diagnostics.
         */
        @ManagedAttribute
        Set<QName> getAllServices();
    }
}
