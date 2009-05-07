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

import com.sun.xml.internal.ws.api.server.DocumentAddressResolver;
import com.sun.xml.internal.ws.api.server.PortAddressResolver;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.api.server.SDDocumentFilter;
import com.sun.xml.internal.ws.api.server.SDDocumentSource;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.wsdl.parser.ParserUtil;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import com.sun.xml.internal.ws.util.RuntimeVersion;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link SDDocument} implmentation.
 *
 * <p>
 * This extends from {@link SDDocumentSource} so that
 * JAX-WS server runtime code can use {@link SDDocument}
 * as {@link SDDocumentSource}.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
class SDDocumentImpl extends SDDocumentSource implements SDDocument {

    private static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";
    private static final QName SCHEMA_INCLUDE_QNAME = new QName(NS_XSD, "include");
    private static final QName SCHEMA_IMPORT_QNAME = new QName(NS_XSD, "import");
    private static final String VERSION_COMMENT =
        " Published by JAX-WS RI at http://jax-ws.dev.java.net. RI's version is "+RuntimeVersion.VERSION+". ";

    /**
     * Creates {@link SDDocument} from {@link SDDocumentSource}.
     * @param src WSDL document infoset
     * @param serviceName wsdl:service name
     * @param portTypeName
     *      The information about the port of {@link WSEndpoint} to which this document is built for.
     *      These values are used to determine which document is the concrete and abstract WSDLs
     *      for this endpoint.
     *
     * @return null
     *      Always non-null.
     */
    public static SDDocumentImpl create(SDDocumentSource src, QName serviceName, QName portTypeName) {
        URL systemId = src.getSystemId();

        try {
            // RuntimeWSDLParser parser = new RuntimeWSDLParser(null);
            XMLStreamReader reader = src.read();
            try {
                XMLStreamReaderUtil.nextElementContent(reader);

                QName rootName = reader.getName();
                if(rootName.equals(WSDLConstants.QNAME_SCHEMA)) {
                    String tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);
                    Set<String> importedDocs = new HashSet<String>();
                    while (XMLStreamReaderUtil.nextContent(reader) != XMLStreamConstants.END_DOCUMENT) {
                         if (reader.getEventType() != XMLStreamConstants.START_ELEMENT)
                            continue;
                        QName name = reader.getName();
                        if (SCHEMA_INCLUDE_QNAME.equals(name) || SCHEMA_IMPORT_QNAME.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "schemaLocation");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc).toString());
                            }
                        }
                    }
                    return new SchemaImpl(rootName,systemId,src,tns,importedDocs);
                } else if (rootName.equals(WSDLConstants.QNAME_DEFINITIONS)) {
                    String tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);

                    boolean hasPortType = false;
                    boolean hasService = false;
                    Set<String> importedDocs = new HashSet<String>();

                    // if WSDL, parse more
                    while (XMLStreamReaderUtil.nextContent(reader) != XMLStreamConstants.END_DOCUMENT) {
                         if(reader.getEventType() != XMLStreamConstants.START_ELEMENT)
                            continue;

                        QName name = reader.getName();
                        if (WSDLConstants.QNAME_PORT_TYPE.equals(name)) {
                            String pn = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
                            if (portTypeName != null) {
                                if(portTypeName.getLocalPart().equals(pn)&&portTypeName.getNamespaceURI().equals(tns)) {
                                    hasPortType = true;
                                }
                            }
                        } else if (WSDLConstants.QNAME_SERVICE.equals(name)) {
                            String sn = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
                            QName sqn = new QName(tns,sn);
                            if(serviceName.equals(sqn)) {
                                hasService = true;
                            }
                        } else if (WSDLConstants.QNAME_IMPORT.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "location");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc).toString());
                            }
                        } else if (SCHEMA_INCLUDE_QNAME.equals(name) || SCHEMA_IMPORT_QNAME.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "schemaLocation");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc).toString());
                            }
                        }
                    }
                    return new WSDLImpl(
                        rootName,systemId,src,tns,hasPortType,hasService,importedDocs);
                } else {
                    return new SDDocumentImpl(rootName,systemId,src);
                }
            } finally {
                reader.close();
            }
        } catch (WebServiceException e) {
            throw new ServerRtException("runtime.parser.wsdl", systemId,e);
        } catch (IOException e) {
            throw new ServerRtException("runtime.parser.wsdl", systemId,e);
        } catch (XMLStreamException e) {
            throw new ServerRtException("runtime.parser.wsdl", systemId,e);
        }
    }


    private final QName rootName;
    private final SDDocumentSource source;

    /**
     * Set when {@link ServiceDefinitionImpl} is constructed.
     */
    /*package*/ ServiceDefinitionImpl owner;

    /**
     * The original system ID of this document.
     *
     * When this document contains relative references to other resources,
     * this field is used to find which {@link com.sun.xml.internal.ws.server.SDDocumentImpl} it refers to.
     *
     * Must not be null.
     */
    private final URL url;
    private final Set<String> imports;

    protected SDDocumentImpl(QName rootName, URL url, SDDocumentSource source) {
        this(rootName, url, source, new HashSet<String>());
    }

    protected SDDocumentImpl(QName rootName, URL url, SDDocumentSource source, Set<String> imports) {
        assert url!=null;
        this.rootName = rootName;
        this.source = source;
        this.url = url;
        this.imports = imports;
    }

    public QName getRootName() {
        return rootName;
    }

    public boolean isWSDL() {
        return false;
    }

    public boolean isSchema() {
        return false;
    }

    public URL getURL() {
        return url;
    }

    public XMLStreamReader read(XMLInputFactory xif) throws IOException, XMLStreamException {
        return source.read(xif);
    }

    public XMLStreamReader read() throws IOException, XMLStreamException {
        return source.read();
    }

    public URL getSystemId() {
        return url;
    }

    public Set<String> getImports() {
        return imports;
    }

    public void writeTo(PortAddressResolver portAddressResolver, DocumentAddressResolver resolver, OutputStream os) throws IOException {
        XMLStreamWriter w = null;
        try {
            //generate the WSDL with utf-8 encoding and XML version 1.0
            w = XMLStreamWriterFactory.create(os, "UTF-8");
            w.writeStartDocument("UTF-8", "1.0");
            writeTo(portAddressResolver,resolver,w);
            w.writeEndDocument();
        } catch (XMLStreamException e) {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        } finally {
            try {
                if (w != null)
                    w.close();
            } catch (XMLStreamException e) {
                IOException ioe = new IOException(e.getMessage());
                ioe.initCause(e);
                throw ioe;
            }
        }
    }

    public void writeTo(PortAddressResolver portAddressResolver, DocumentAddressResolver resolver, XMLStreamWriter out) throws XMLStreamException, IOException {
        for (SDDocumentFilter f : owner.filters) {
            out = f.filter(this,out);
        }

        XMLStreamReader xsr = source.read();
        try {
            out.writeComment(VERSION_COMMENT);
            new WSDLPatcher(owner.owner,this,portAddressResolver,resolver).bridge(xsr,out);
        } finally {
            xsr.close();
        }
    }


    /**
     * {@link SDDocument.Schema} implementation.
     *
     * @author Kohsuke Kawaguchi
     */
    private static final class SchemaImpl extends SDDocumentImpl implements SDDocument.Schema {
        private final String targetNamespace;

        public SchemaImpl(QName rootName, URL url, SDDocumentSource source, String targetNamespace,
                          Set<String> imports) {
            super(rootName, url, source, imports);
            this.targetNamespace = targetNamespace;
        }

        public String getTargetNamespace() {
            return targetNamespace;
        }

        public boolean isSchema() {
            return true;
        }
    }


    private static final class WSDLImpl extends SDDocumentImpl implements SDDocument.WSDL {
        private final String targetNamespace;
        private final boolean hasPortType;
        private final boolean hasService;

        public WSDLImpl(QName rootName, URL url, SDDocumentSource source, String targetNamespace, boolean hasPortType,
                        boolean hasService, Set<String> imports) {
            super(rootName, url, source, imports);
            this.targetNamespace = targetNamespace;
            this.hasPortType = hasPortType;
            this.hasService = hasService;
        }

        public String getTargetNamespace() {
            return targetNamespace;
        }

        public boolean hasPortType() {
            return hasPortType;
        }

        public boolean hasService() {
            return hasService;
        }

        public boolean isWSDL() {
            return true;
        }
    }


}
