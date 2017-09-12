/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.server;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferResult;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.api.server.SDDocumentSource;

import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WSDLGenerator uses WSDLResolver while creating WSDL artifacts. WSDLResolver
 * is used to control the file names and which artifact to be generated or not.
 *
 * @author Jitendra Kotamraju
 */
final class WSDLGenResolver implements com.oracle.webservices.internal.api.databinding.WSDLResolver {

    private final Collection<SDDocumentImpl> docs;
    private final List<SDDocumentSource> newDocs = new ArrayList<SDDocumentSource>();
    private SDDocumentSource concreteWsdlSource;

    private SDDocumentImpl abstractWsdl;
    private SDDocumentImpl concreteWsdl;

    /**
     * targetNS -> schema documents.
     */
    private final Map<String, List<SDDocumentImpl>> nsMapping = new HashMap<String,List<SDDocumentImpl>>();

    private final QName serviceName;
    private final QName portTypeName;

    public WSDLGenResolver(@NotNull Collection<SDDocumentImpl> docs,QName serviceName,QName portTypeName) {
        this.docs = docs;
        this.serviceName = serviceName;
        this.portTypeName = portTypeName;

        for (SDDocumentImpl doc : docs) {
            if(doc.isWSDL()) {
                SDDocument.WSDL wsdl = (SDDocument.WSDL) doc;
                if(wsdl.hasPortType())
                    abstractWsdl = doc;
            }
            if(doc.isSchema()) {
                SDDocument.Schema schema = (SDDocument.Schema) doc;
                List<SDDocumentImpl> sysIds = nsMapping.get(schema.getTargetNamespace());
                if (sysIds == null) {
                    sysIds = new ArrayList<SDDocumentImpl>();
                    nsMapping.put(schema.getTargetNamespace(), sysIds);
                }
                sysIds.add(doc);
            }
        }
    }

    /**
     * Generates the concrete WSDL that contains service element.
     *
     * @return Result the generated concrete WSDL
     */
    public Result getWSDL(String filename) {
        URL url = createURL(filename);
        MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();
        xsb.setSystemId(url.toExternalForm());
        concreteWsdlSource = SDDocumentSource.create(url,xsb);
        newDocs.add(concreteWsdlSource);
        XMLStreamBufferResult r = new XMLStreamBufferResult(xsb);
        r.setSystemId(filename);
        return r;
    }

    /**
     * At present, it returns file URL scheme eventhough there is no resource
     * in the filesystem.
     *
     * @return URL of the generated document
     *
     */
    private URL createURL(String filename) {
        try {
            return new URL("file:///"+filename);
        } catch (MalformedURLException e) {
            // TODO: I really don't think this is the right way to handle this error,
            // WSDLResolver needs to be documented carefully.
            throw new WebServiceException(e);
        }
    }

    /**
     * Updates filename if the suggested filename need to be changed in
     * wsdl:import. If the metadata already contains abstract wsdl(i.e. a WSDL
     * which has the porttype), then the abstract wsdl shouldn't be generated
     *
     * return null if abstract WSDL need not be generated
     *        Result the abstract WSDL
     */
    public Result getAbstractWSDL(Holder<String> filename) {
        if (abstractWsdl != null) {
            filename.value = abstractWsdl.getURL().toString();
            return null;                // Don't generate abstract WSDL
        }
        URL url = createURL(filename.value);
        MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();
        xsb.setSystemId(url.toExternalForm());
        SDDocumentSource abstractWsdlSource = SDDocumentSource.create(url,xsb);
        newDocs.add(abstractWsdlSource);
        XMLStreamBufferResult r = new XMLStreamBufferResult(xsb);
        r.setSystemId(filename.value);
        return r;
    }

    /**
     * Updates filename if the suggested filename need to be changed in
     * xsd:import. If there is already a schema document for the namespace
     * in the metadata, then it is not generated.
     *
     * return null if schema need not be generated
     *        Result the generated schema document
     */
    public Result getSchemaOutput(String namespace, Holder<String> filename) {
        List<SDDocumentImpl> schemas = nsMapping.get(namespace);
        if (schemas != null) {
            if (schemas.size() > 1) {
                throw new ServerRtException("server.rt.err",
                    "More than one schema for the target namespace "+namespace);
            }
            filename.value = schemas.get(0).getURL().toExternalForm();
            return null;            // Don't generate schema
        }

        URL url = createURL(filename.value);
        MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();
        xsb.setSystemId(url.toExternalForm());
        SDDocumentSource sd = SDDocumentSource.create(url,xsb);
        newDocs.add(sd);

        XMLStreamBufferResult r = new XMLStreamBufferResult(xsb);
        r.setSystemId(filename.value);
        return r;
    }

    /**
     * Converts SDDocumentSource to SDDocumentImpl and updates original docs. It
     * categories the generated documents into WSDL, Schema types.
     *
     * @return the primary WSDL
     *         null if it is not there in the generated documents
     *
     */
    public SDDocumentImpl updateDocs() {
        for (SDDocumentSource doc : newDocs) {
            SDDocumentImpl docImpl = SDDocumentImpl.create(doc,serviceName,portTypeName);
            if (doc == concreteWsdlSource) {
                concreteWsdl = docImpl;
            }
            docs.add(docImpl);
        }
        return concreteWsdl;
    }

}
