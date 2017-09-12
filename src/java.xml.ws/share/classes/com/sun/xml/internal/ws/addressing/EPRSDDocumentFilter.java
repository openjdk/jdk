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

package com.sun.xml.internal.ws.addressing;

import com.sun.xml.internal.ws.api.server.*;
import com.sun.xml.internal.ws.api.server.Module;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.util.xml.XMLStreamWriterFilter;
import com.sun.xml.internal.org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;
import com.sun.xml.internal.ws.server.WSEndpointImpl;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.NotNull;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.namespace.NamespaceContext;
import java.io.IOException;
import java.util.List;
import java.util.Collection;
import java.util.Collections;

/**
 * This class acts as a filter for the Extension elements in the wsa:EndpointReference in the wsdl.
 * In addition to filtering the EPR extensions from WSDL, it adds the extensions configured by the JAX-WS runtime
 * specifc to an endpoint.
 *
 * @author Rama Pulavarthi
 */
public class EPRSDDocumentFilter implements SDDocumentFilter {
    private final WSEndpointImpl<?> endpoint;
    //initialize lazily
    List<BoundEndpoint> beList;
    public EPRSDDocumentFilter(@NotNull WSEndpointImpl<?> endpoint) {
        this.endpoint = endpoint;
    }

    private @Nullable WSEndpointImpl<?> getEndpoint(String serviceName, String portName) {
        if (serviceName == null || portName == null)
            return null;
        if (endpoint.getServiceName().getLocalPart().equals(serviceName) && endpoint.getPortName().getLocalPart().equals(portName))
            return endpoint;

        if(beList == null) {
            //check if it is run in a Java EE Container and get hold of other endpoints in the application
            Module module = endpoint.getContainer().getSPI(Module.class);
            if (module != null) {
                beList = module.getBoundEndpoints();
            } else {
                beList = Collections.<BoundEndpoint>emptyList();
            }
        }

        for (BoundEndpoint be : beList) {
            WSEndpoint wse = be.getEndpoint();
            if (wse.getServiceName().getLocalPart().equals(serviceName) && wse.getPortName().getLocalPart().equals(portName)) {
                return (WSEndpointImpl) wse;
            }
        }

        return null;

    }

    public XMLStreamWriter filter(SDDocument doc, XMLStreamWriter w) throws XMLStreamException, IOException {
        if (!doc.isWSDL()) {
            return w;
        }

        return new XMLStreamWriterFilter(w) {
            private boolean eprExtnFilterON = false; //when true, all writer events are filtered out

            private boolean portHasEPR = false;
            private int eprDepth = -1; // -1 -> outside wsa:epr, 0 -> on wsa:epr start/end , > 0 inside wsa:epr

            private String serviceName = null; //non null when inside wsdl:service scope
            private boolean onService = false; //flag to get service name when on wsdl:service element start
            private int serviceDepth = -1;  // -1 -> outside wsdl:service, 0 -> on wsdl:service start/end , > 0 inside wsdl:service

            private String portName = null; //non null when inside wsdl:port scope
            private boolean onPort = false; //flag to get port name when on wsdl:port element start
            private int portDepth = -1; // -1 -> outside wsdl:port, 0 -> on wsdl:port start/end , > 0 inside wsdl:port

            private String portAddress; // when a complete epr is written, endpoint address is used as epr address
            private boolean onPortAddress = false; //flag to get endpoint address when on soap:address element start

            private void handleStartElement(String localName, String namespaceURI) throws XMLStreamException {
                resetOnElementFlags();
                if (serviceDepth >= 0) {
                    serviceDepth++;
                }
                if (portDepth >= 0) {
                    portDepth++;
                }
                if (eprDepth >= 0) {
                    eprDepth++;
                }

                if (namespaceURI.equals(WSDLConstants.QNAME_SERVICE.getNamespaceURI()) && localName.equals(WSDLConstants.QNAME_SERVICE.getLocalPart())) {
                    onService = true;
                    serviceDepth = 0;
                } else if (namespaceURI.equals(WSDLConstants.QNAME_PORT.getNamespaceURI()) && localName.equals(WSDLConstants.QNAME_PORT.getLocalPart())) {
                    if (serviceDepth >= 1) {
                        onPort = true;
                        portDepth = 0;
                    }
                } else if (namespaceURI.equals(W3CAddressingConstants.WSA_NAMESPACE_NAME) && localName.equals("EndpointReference")) {
                    if (serviceDepth >= 1 && portDepth >= 1) {
                        portHasEPR = true;
                        eprDepth = 0;
                    }
                } else if ((namespaceURI.equals(WSDLConstants.NS_SOAP_BINDING_ADDRESS.getNamespaceURI()) || namespaceURI.equals(WSDLConstants.NS_SOAP12_BINDING_ADDRESS.getNamespaceURI()))
                        &&  localName.equals("address") && portDepth ==1) {
                    onPortAddress = true;
                }
                WSEndpoint endpoint = getEndpoint(serviceName,portName);
                //filter epr for only for the port corresponding to this endpoint
                //if (service.getLocalPart().equals(serviceName) && port.getLocalPart().equals(portName)) {
                if ( endpoint != null) {
                    if ((eprDepth == 1) && !namespaceURI.equals(W3CAddressingConstants.WSA_NAMESPACE_NAME)) {
                        //epr extension element
                        eprExtnFilterON = true;

                    }

                    /*
                    if (eprExtnFilterON) {
                        writeEPRExtensions();
                    }
                    */
                }
            }

            private void resetOnElementFlags() {
                if (onService) {
                    onService = false;
                }
                if (onPort) {
                    onPort = false;
                }
                if (onPortAddress) {
                    onPortAddress = false;
                }

            }


            private void writeEPRExtensions(Collection<WSEndpointReference.EPRExtension> eprExtns) throws XMLStreamException {
               if (eprExtns != null) {
                        for (WSEndpointReference.EPRExtension e : eprExtns) {
                            XMLStreamReaderToXMLStreamWriter c = new XMLStreamReaderToXMLStreamWriter();
                            XMLStreamReader r = e.readAsXMLStreamReader();
                            c.bridge(r, writer);
                            XMLStreamReaderFactory.recycle(r);
                        }
                    }
            }

            @Override
            public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
                handleStartElement(localName, namespaceURI);
                if (!eprExtnFilterON) {
                    super.writeStartElement(prefix, localName, namespaceURI);
                }
            }

            @Override
            public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
                handleStartElement(localName, namespaceURI);
                if (!eprExtnFilterON) {
                    super.writeStartElement(namespaceURI, localName);
                }
            }

            @Override
            public void writeStartElement(String localName) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeStartElement(localName);
                }
            }

            private void handleEndElement() throws XMLStreamException {
                resetOnElementFlags();
                //End of wsdl:port, write complete EPR if not present.
                if (portDepth == 0) {

                    if (!portHasEPR && getEndpoint(serviceName,portName) != null) {

                        //write the complete EPR with address.
                        writer.writeStartElement(AddressingVersion.W3C.getPrefix(),"EndpointReference", AddressingVersion.W3C.nsUri );
                        writer.writeNamespace(AddressingVersion.W3C.getPrefix(), AddressingVersion.W3C.nsUri);
                        writer.writeStartElement(AddressingVersion.W3C.getPrefix(), AddressingVersion.W3C.eprType.address, AddressingVersion.W3C.nsUri);
                        writer.writeCharacters(portAddress);
                        writer.writeEndElement();
                        writeEPRExtensions(getEndpoint(serviceName, portName).getEndpointReferenceExtensions());
                        writer.writeEndElement();

                    }
                }
                //End of wsa:EndpointReference, write EPR extension elements
                if (eprDepth == 0) {
                    if (portHasEPR && getEndpoint(serviceName,portName) != null) {
                        writeEPRExtensions(getEndpoint(serviceName, portName).getEndpointReferenceExtensions());
                    }
                    eprExtnFilterON = false;
                }

                if(serviceDepth >= 0 )  {
                    serviceDepth--;
                }
                if(portDepth >= 0) {
                    portDepth--;
                }
                if(eprDepth >=0) {
                    eprDepth--;
                }

                if (serviceDepth == -1) {
                    serviceName = null;
                }
                if (portDepth == -1) {
                    portHasEPR = false;
                    portAddress = null;
                    portName = null;
                }
            }

            @Override
            public void writeEndElement() throws XMLStreamException {
                handleEndElement();
                if (!eprExtnFilterON) {
                    super.writeEndElement();
                }
            }

            private void handleAttribute(String localName, String value) {
                if (localName.equals("name")) {
                    if (onService) {
                        serviceName = value;
                        onService = false;
                    } else if (onPort) {
                        portName = value;
                        onPort = false;
                    }
                }
                if (localName.equals("location") && onPortAddress) {
                    portAddress = value;
                }


            }

            @Override
            public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
                handleAttribute(localName, value);
                if (!eprExtnFilterON) {
                    super.writeAttribute(prefix, namespaceURI, localName, value);
                }
            }

            @Override
            public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
                handleAttribute(localName, value);
                if (!eprExtnFilterON) {
                    super.writeAttribute(namespaceURI, localName, value);
                }
            }

            @Override
            public void writeAttribute(String localName, String value) throws XMLStreamException {
                handleAttribute(localName, value);
                if (!eprExtnFilterON) {
                    super.writeAttribute(localName, value);
                }
            }


            @Override
            public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeEmptyElement(namespaceURI, localName);
                }
            }

            @Override
            public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeNamespace(prefix, namespaceURI);
                }
            }

            @Override
            public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.setNamespaceContext(context);
                }
            }

            @Override
            public void setDefaultNamespace(String uri) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.setDefaultNamespace(uri);
                }
            }

            @Override
            public void setPrefix(String prefix, String uri) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.setPrefix(prefix, uri);
                }
            }

            @Override
            public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeProcessingInstruction(target, data);
                }
            }

            @Override
            public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeEmptyElement(prefix, localName, namespaceURI);
                }
            }

            @Override
            public void writeCData(String data) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeCData(data);
                }
            }

            @Override
            public void writeCharacters(String text) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeCharacters(text);
                }
            }

            @Override
            public void writeComment(String data) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeComment(data);
                }
            }

            @Override
            public void writeDTD(String dtd) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeDTD(dtd);
                }
            }

            @Override
            public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeDefaultNamespace(namespaceURI);
                }
            }

            @Override
            public void writeEmptyElement(String localName) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeEmptyElement(localName);
                }
            }

            @Override
            public void writeEntityRef(String name) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeEntityRef(name);
                }
            }

            @Override
            public void writeProcessingInstruction(String target) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeProcessingInstruction(target);
                }
            }


            @Override
            public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
                if (!eprExtnFilterON) {
                    super.writeCharacters(text, start, len);
                }
            }

        };

    }

}
