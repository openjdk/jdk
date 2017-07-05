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

package com.sun.xml.internal.ws.api.addressing;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferResult;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferSource;
import com.sun.xml.internal.stream.buffer.sax.SAXBufferProcessor;
import com.sun.xml.internal.stream.buffer.stax.StreamReaderBufferProcessor;
import com.sun.xml.internal.stream.buffer.stax.StreamWriterBufferCreator;
import com.sun.xml.internal.ws.addressing.EndpointReferenceUtil;
import com.sun.xml.internal.ws.addressing.W3CAddressingMetadataConstants;
import com.sun.xml.internal.ws.addressing.WSEPRExtension;
import com.sun.xml.internal.ws.addressing.model.InvalidAddressingHeaderException;
import com.sun.xml.internal.ws.addressing.v200408.MemberSubmissionAddressingConstants;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.MessageHeaders;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLExtension;
import com.sun.xml.internal.ws.resources.AddressingMessages;
import com.sun.xml.internal.ws.resources.ClientMessages;
import com.sun.xml.internal.ws.spi.ProviderImpl;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.DOMUtil;
import com.sun.xml.internal.ws.util.xml.XMLStreamWriterFilter;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.util.xml.XMLStreamReaderToXMLStreamWriter;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import org.w3c.dom.Element;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Dispatch;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * Internal representation of the EPR.
 *
 * <p>
 * Instances of this class are immutable and thread-safe.
 *
 * @author Kohsuke Kawaguchi
 * @author Rama Pulavarthi
 *
 * @see AddressingVersion#anonymousEpr
 */
public final class WSEndpointReference  implements WSDLExtension {
    private final XMLStreamBuffer infoset;
    /**
     * Version of the addressing spec.
     */
    private final AddressingVersion version;

    /**
     * Marked Reference parameters inside this EPR.
     *
     * Parsed when the object is created. can be empty but never null.
     * @see #parse()
     */
    private @NotNull Header[] referenceParameters;
    private @NotNull String address;

    private @NotNull QName rootElement;
    /**
     * Creates from the spec version of {@link EndpointReference}.
     *
     * <p>
     * This method performs the data conversion, so it's slow.
     * Do not use this method in a performance critical path.
     */
    public WSEndpointReference(EndpointReference epr, AddressingVersion version) {
        try {
            MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();
            epr.writeTo(new XMLStreamBufferResult(xsb));
            this.infoset = xsb;
            this.version = version;
            this.rootElement = new QName("EndpointReference", version.nsUri);
            parse();
        } catch (XMLStreamException e) {
            throw new WebServiceException(ClientMessages.FAILED_TO_PARSE_EPR(epr),e);
        }
    }

    /**
     * Creates from the spec version of {@link EndpointReference}.
     *
     * <p>
     * This method performs the data conversion, so it's slow.
     * Do not use this method in a performance critical path.
     */
    public WSEndpointReference(EndpointReference epr) {
        this(epr,AddressingVersion.fromSpecClass(epr.getClass()));
    }

    /**
     * Creates a {@link WSEndpointReference} that wraps a given infoset.
     */
    public WSEndpointReference(XMLStreamBuffer infoset, AddressingVersion version) {
        try {
            this.infoset = infoset;
            this.version = version;
            this.rootElement = new QName("EndpointReference", version.nsUri);
            parse();
        } catch (XMLStreamException e) {
            // this can never happen because XMLStreamBuffer never has underlying I/O error.
            throw new AssertionError(e);
        }
    }

    /**
     * Creates a {@link WSEndpointReference} by parsing an infoset.
     */
    public WSEndpointReference(InputStream infoset, AddressingVersion version) throws XMLStreamException {
        this(XMLStreamReaderFactory.create(null,infoset,false),version);
    }

    /**
     * Creates a {@link WSEndpointReference} from the given infoset.
     * The {@link XMLStreamReader} must point to either a document or an element.
     */
    public WSEndpointReference(XMLStreamReader in, AddressingVersion version) throws XMLStreamException {
        this(XMLStreamBuffer.createNewBufferFromXMLStreamReader(in), version);
    }

    /**
     * @see #WSEndpointReference(String, AddressingVersion)
     */
    public WSEndpointReference(URL address, AddressingVersion version) {
        this(address.toExternalForm(), version);
    }

    /**
     * @see #WSEndpointReference(String, AddressingVersion)
     */
    public WSEndpointReference(URI address, AddressingVersion version) {
        this(address.toString(), version);
    }

    /**
     * Creates a {@link WSEndpointReference} that only has an address.
     */
    public WSEndpointReference(String address, AddressingVersion version) {
        this.infoset = createBufferFromAddress(address,version);
        this.version = version;
        this.address = address;
        this.rootElement = new QName("EndpointReference", version.nsUri);
        this.referenceParameters = EMPTY_ARRAY;
    }

    private static XMLStreamBuffer createBufferFromAddress(String address, AddressingVersion version) {
        try {
            MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();
            StreamWriterBufferCreator w = new StreamWriterBufferCreator(xsb);
            w.writeStartDocument();
            w.writeStartElement(version.getPrefix(),
                "EndpointReference", version.nsUri);
            w.writeNamespace(version.getPrefix(), version.nsUri);
            w.writeStartElement(version.getPrefix(),version.eprType.address, version.nsUri);
            w.writeCharacters(address);
            w.writeEndElement();
            w.writeEndElement();
            w.writeEndDocument();
            w.close();
            return xsb;
        } catch (XMLStreamException e) {
            // can never happen because we are writing to XSB
            throw new AssertionError(e);
        }
    }

    /**
     * Creates an EPR from individual components.
     *
     * <p>
     * This version takes various information about metadata, and creates an EPR that has
     * the necessary embedded WSDL.
     */
    public WSEndpointReference(@NotNull AddressingVersion version,
                               @NotNull String address,
                               @Nullable QName service,
                               @Nullable QName port,
                               @Nullable QName portType,
                               @Nullable List<Element> metadata,
                               @Nullable String wsdlAddress,
                               @Nullable List<Element> referenceParameters) {
       this(version, address, service, port, portType, metadata, wsdlAddress, null, referenceParameters, null, null);
    }

    /**
     * Creates an EPR from individual components.
     *
     * <p>
     * This version takes various information about metadata, and creates an EPR that has
     * the necessary embedded WSDL.
     */
    public WSEndpointReference(@NotNull AddressingVersion version,
                               @NotNull String address,
                               @Nullable QName service,
                               @Nullable QName port,
                               @Nullable QName portType,
                               @Nullable List<Element> metadata,
                               @Nullable String wsdlAddress,
                               @Nullable List<Element> referenceParameters,
                               @Nullable Collection<EPRExtension> extns,@Nullable Map<QName, String> attributes) {
       this(createBufferFromData(version, address, referenceParameters, service, port, portType, metadata, wsdlAddress, null, extns, attributes),
            version );
    }

    /**
     * Creates an EPR from individual components.
     *
     * <p>
     * This version takes various information about metadata, and creates an EPR that has
     * the necessary embedded WSDL.
     * @since JAX-WS 2.2
     */
    public WSEndpointReference(@NotNull AddressingVersion version,
                               @NotNull String address,
                               @Nullable QName service,
                               @Nullable QName port,
                               @Nullable QName portType,
                               @Nullable List<Element> metadata,
                               @Nullable String wsdlAddress,
                               @Nullable String wsdlTargetNamepsace,
                               @Nullable List<Element> referenceParameters,
                               @Nullable List<Element> elements, @Nullable Map<QName, String> attributes) {
       this(
            createBufferFromData(version, address, referenceParameters, service, port, portType, metadata, wsdlAddress,wsdlTargetNamepsace, elements, attributes),
            version );
    }

    private static XMLStreamBuffer createBufferFromData(AddressingVersion version, String address, List<Element> referenceParameters, QName service, QName port, QName portType,
                                                            List<Element> metadata, String wsdlAddress, String wsdlTargetNamespace, @Nullable List<Element> elements, @Nullable Map<QName, String> attributes) {

        StreamWriterBufferCreator writer = new StreamWriterBufferCreator();

        try {
            writer.writeStartDocument();
            writer.writeStartElement(version.getPrefix(),"EndpointReference", version.nsUri);
            writer.writeNamespace(version.getPrefix(),version.nsUri);

                writePartialEPRInfoset(writer, version, address, referenceParameters, service, port, portType,
                        metadata,wsdlAddress, wsdlTargetNamespace, attributes);

                //write extensibility elements in the EPR element
                if (elements != null) {
                    for (Element e : elements) {
                        DOMUtil.serializeNode(e, writer);
                    }
                }

                writer.writeEndElement();
                writer.writeEndDocument();
                writer.flush();

                return writer.getXMLStreamBuffer();
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
        }

        private static XMLStreamBuffer createBufferFromData(AddressingVersion version, String address, List<Element> referenceParameters, QName service, QName port, QName portType,
                                                            List<Element> metadata, String wsdlAddress, String wsdlTargetNamespace, @Nullable Collection<EPRExtension> extns, @Nullable Map<QName, String> attributes) {

            StreamWriterBufferCreator writer = new StreamWriterBufferCreator();

            try {
                writer.writeStartDocument();
                writer.writeStartElement(version.getPrefix(),"EndpointReference", version.nsUri);
                writer.writeNamespace(version.getPrefix(),version.nsUri);

                writePartialEPRInfoset(writer, version, address, referenceParameters, service, port, portType,
                        metadata,wsdlAddress, wsdlTargetNamespace, attributes);

                //write extensibility elements in the EPR element
                if (extns != null) {
                    for (EPRExtension e : extns) {
                        XMLStreamReaderToXMLStreamWriter c = new XMLStreamReaderToXMLStreamWriter();
                        XMLStreamReader r = e.readAsXMLStreamReader();
                        c.bridge(r, writer);
                        XMLStreamReaderFactory.recycle(r);
                    }
                }

                writer.writeEndElement();
                writer.writeEndDocument();
                writer.flush();

                return writer.getXMLStreamBuffer();
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
        }

        private static void writePartialEPRInfoset(StreamWriterBufferCreator writer, AddressingVersion version, String address, List<Element> referenceParameters, QName service, QName port, QName portType,
                                                   List<Element> metadata, String wsdlAddress, String wsdlTargetNamespace, @Nullable Map<QName, String> attributes) throws XMLStreamException {
            //add extensibile attributes on the EPR element
            if (attributes != null) {
                for (Map.Entry<QName, String> entry : attributes.entrySet()) {
                    QName qname = entry.getKey();
                    writer.writeAttribute(qname.getPrefix(), qname.getNamespaceURI(), qname.getLocalPart(), entry.getValue());
                }
            }

            writer.writeStartElement(version.getPrefix(), version.eprType.address, version.nsUri);
            writer.writeCharacters(address);
            writer.writeEndElement();
            //When the size of ReferenceParametes is zero, the ReferenceParametes element will not be written.
            if(referenceParameters != null && referenceParameters.size() > 0) {
                writer.writeStartElement(version.getPrefix(), version.eprType.referenceParameters, version.nsUri);
                for (Element e : referenceParameters) {
                    DOMUtil.serializeNode(e, writer);
                }
                writer.writeEndElement();
            }

            switch (version) {
                case W3C:
                    writeW3CMetaData(writer, service, port, portType, metadata, wsdlAddress, wsdlTargetNamespace);
                    break;

                case MEMBER:
                    writeMSMetaData(writer, service, port, portType, metadata);
                    if (wsdlAddress != null) {
                        //Inline the wsdl as extensibility element
                        //Write mex:Metadata wrapper
                        writer.writeStartElement(MemberSubmissionAddressingConstants.MEX_METADATA.getPrefix(),
                                MemberSubmissionAddressingConstants.MEX_METADATA.getLocalPart(),
                                MemberSubmissionAddressingConstants.MEX_METADATA.getNamespaceURI());
                        writer.writeStartElement(MemberSubmissionAddressingConstants.MEX_METADATA_SECTION.getPrefix(),
                                MemberSubmissionAddressingConstants.MEX_METADATA_SECTION.getLocalPart(),
                                MemberSubmissionAddressingConstants.MEX_METADATA_SECTION.getNamespaceURI());
                        writer.writeAttribute(MemberSubmissionAddressingConstants.MEX_METADATA_DIALECT_ATTRIBUTE,
                                MemberSubmissionAddressingConstants.MEX_METADATA_DIALECT_VALUE);

                        writeWsdl(writer, service, wsdlAddress);

                        writer.writeEndElement();
                        writer.writeEndElement();
                    }

                    break;
            }
        }

    private static boolean isEmty(QName qname) {
        return qname == null || qname.toString().trim().length()== 0;
    }

    private static void writeW3CMetaData(StreamWriterBufferCreator writer,
                                         QName service,
                                         QName port,
                                         QName portType, List<Element> metadata,
                                         String wsdlAddress, String wsdlTargetNamespace) throws XMLStreamException {


        //.NET treate empty metaData element as bad request.
        if (isEmty(service) && isEmty(port) && isEmty(portType) && metadata == null/* && wsdlAddress == null*/) {
            return;
        }

        writer.writeStartElement(AddressingVersion.W3C.getPrefix(),
                AddressingVersion.W3C.eprType.wsdlMetadata.getLocalPart(), AddressingVersion.W3C.nsUri);
        writer.writeNamespace(AddressingVersion.W3C.getWsdlPrefix(),
                AddressingVersion.W3C.wsdlNsUri);
        //write wsdliLication as defined in WS-Addressing 1.0 Metadata spec
        if(wsdlAddress != null) {
            writeWsdliLocation(writer, service, wsdlAddress, wsdlTargetNamespace);
        }

        //Write Interface info
        if (portType != null) {
            writer.writeStartElement(W3CAddressingMetadataConstants.WSAM_PREFIX_NAME,
                    AddressingVersion.W3C.eprType.portTypeName,
                    W3CAddressingMetadataConstants.WSAM_NAMESPACE_NAME);
            writer.writeNamespace(W3CAddressingMetadataConstants.WSAM_PREFIX_NAME,
                W3CAddressingMetadataConstants.WSAM_NAMESPACE_NAME);
            String portTypePrefix = portType.getPrefix();
            if (portTypePrefix == null || portTypePrefix.equals("")) {
                //TODO check prefix again
                portTypePrefix = "wsns";
            }
            writer.writeNamespace(portTypePrefix, portType.getNamespaceURI());
            writer.writeCharacters(portTypePrefix + ":" + portType.getLocalPart());
            writer.writeEndElement();
        }
        if (service != null) {
            //Write service and Port info
            if (!(service.getNamespaceURI().equals("") || service.getLocalPart().equals(""))) {
                writer.writeStartElement(W3CAddressingMetadataConstants.WSAM_PREFIX_NAME,
                        AddressingVersion.W3C.eprType.serviceName,
                        W3CAddressingMetadataConstants.WSAM_NAMESPACE_NAME);
                writer.writeNamespace(W3CAddressingMetadataConstants.WSAM_PREFIX_NAME,
                    W3CAddressingMetadataConstants.WSAM_NAMESPACE_NAME);
                String servicePrefix = service.getPrefix();
                if (servicePrefix == null || servicePrefix.equals("")) {
                    //TODO check prefix again
                    servicePrefix = "wsns";
                }
                writer.writeNamespace(servicePrefix, service.getNamespaceURI());
                if (port != null) {
                    writer.writeAttribute(AddressingVersion.W3C.eprType.portName, port.getLocalPart());
                }
                writer.writeCharacters(servicePrefix + ":" + service.getLocalPart());
                writer.writeEndElement();
            }
        }
        /*
        //Inline the wsdl
        if (wsdlAddress != null) {
            writeWsdl(writer, service, wsdlAddress);
        }
        */
        //Add the extra metadata Elements
        if (metadata != null) {
            for (Element e : metadata) {
                DOMUtil.serializeNode(e, writer);
            }
        }
        writer.writeEndElement();

    }

    /**
     * @param writer the writer should be at the start of element.
     * @param service Namespace URI of servcie is used as targetNamespace of wsdl if wsdlTargetNamespace is not null
     * @param wsdlAddress  wsdl location
     * @param wsdlTargetNamespace  targetnamespace of wsdl to be put in wsdliLocation
     *
     */
    private static void writeWsdliLocation(StreamWriterBufferCreator writer, QName service,String wsdlAddress,String wsdlTargetNamespace) throws XMLStreamException {
        String wsdliLocation = "";
        if(wsdlTargetNamespace != null) {
           wsdliLocation = wsdlTargetNamespace + " ";
        } else if (service != null) {
            wsdliLocation = service.getNamespaceURI() + " ";
        } else {
            throw new WebServiceException("WSDL target Namespace cannot be resolved");
        }
        wsdliLocation += wsdlAddress;
        writer.writeNamespace(W3CAddressingMetadataConstants.WSAM_WSDLI_ATTRIBUTE_PREFIX,
            W3CAddressingMetadataConstants.WSAM_WSDLI_ATTRIBUTE_NAMESPACE);
        writer.writeAttribute(W3CAddressingMetadataConstants.WSAM_WSDLI_ATTRIBUTE_PREFIX,
                W3CAddressingMetadataConstants.WSAM_WSDLI_ATTRIBUTE_NAMESPACE,
                W3CAddressingMetadataConstants.WSAM_WSDLI_ATTRIBUTE_LOCALNAME,
                wsdliLocation);

    }
    private static void writeMSMetaData(StreamWriterBufferCreator writer,
                                        QName service,
                                        QName port,
                                        QName portType, List<Element> metadata) throws XMLStreamException {
        // TODO: write ReferenceProperties
        //TODO: write ReferenceParameters
        if (portType != null) {
            //Write Interface info
            writer.writeStartElement(AddressingVersion.MEMBER.getPrefix(),
                    AddressingVersion.MEMBER.eprType.portTypeName,
                    AddressingVersion.MEMBER.nsUri);


            String portTypePrefix = portType.getPrefix();
            if (portTypePrefix == null || portTypePrefix.equals("")) {
                //TODO check prefix again
                portTypePrefix = "wsns";
            }
            writer.writeNamespace(portTypePrefix, portType.getNamespaceURI());
            writer.writeCharacters(portTypePrefix + ":" + portType.getLocalPart());
            writer.writeEndElement();
        }
        //Write service and Port info
        if (service != null) {
            if (!(service.getNamespaceURI().equals("") || service.getLocalPart().equals(""))) {
                writer.writeStartElement(AddressingVersion.MEMBER.getPrefix(),
                        AddressingVersion.MEMBER.eprType.serviceName,
                        AddressingVersion.MEMBER.nsUri);
                String servicePrefix = service.getPrefix();
                if (servicePrefix == null || servicePrefix.equals("")) {
                    //TODO check prefix again
                    servicePrefix = "wsns";
                }
                writer.writeNamespace(servicePrefix, service.getNamespaceURI());
                if (port != null) {
                    writer.writeAttribute(AddressingVersion.MEMBER.eprType.portName,
                            port.getLocalPart());
                }
                writer.writeCharacters(servicePrefix + ":" + service.getLocalPart());
                writer.writeEndElement();
            }
        }
    }

    private static void writeWsdl(StreamWriterBufferCreator writer, QName service, String wsdlAddress) throws XMLStreamException {
       // Inline-wsdl
       writer.writeStartElement(WSDLConstants.PREFIX_NS_WSDL,
               WSDLConstants.QNAME_DEFINITIONS.getLocalPart(),
               WSDLConstants.NS_WSDL);
       writer.writeNamespace(WSDLConstants.PREFIX_NS_WSDL, WSDLConstants.NS_WSDL);
       writer.writeStartElement(WSDLConstants.PREFIX_NS_WSDL,
               WSDLConstants.QNAME_IMPORT.getLocalPart(),
               WSDLConstants.NS_WSDL);
       writer.writeAttribute("namespace", service.getNamespaceURI());
       writer.writeAttribute("location", wsdlAddress);
       writer.writeEndElement();
       writer.writeEndElement();
   }



    /**
     * Converts from {@link EndpointReference}.
     *
     * This handles null {@link EndpointReference} correctly.
     * Call {@link #WSEndpointReference(EndpointReference)} directly
     * if you know it's not null.
     */
    public static @Nullable
    WSEndpointReference create(@Nullable EndpointReference epr) {
        if (epr != null) {
            return new WSEndpointReference(epr);
        } else {
            return null;
        }
    }

    /**
     * @see #createWithAddress(String)
     */
    public @NotNull WSEndpointReference createWithAddress(@NotNull URI newAddress) {
        return createWithAddress(newAddress.toString());
    }

    /**
     * @see #createWithAddress(String)
     */
    public @NotNull WSEndpointReference createWithAddress(@NotNull URL newAddress) {
        return createWithAddress(newAddress.toString());
    }

    /**
     * Creates a new {@link WSEndpointReference} by replacing the address of this EPR
     * to the new one.
     *
     * <p>
     * The following example shows how you can use this to force an HTTPS EPR,
     * when the endpoint can serve both HTTP and HTTPS requests.
     * <pre>
     * if(epr.getAddress().startsWith("http:"))
     *   epr = epr.createWithAddress("https:"+epr.getAddress().substring(5));
     * </pre>
     *
     * @param newAddress
     *      This is a complete URL to be written inside &lt;Adress> element of the EPR,
     *      such as "http://foo.bar/abc/def"
     */
    public @NotNull WSEndpointReference createWithAddress(@NotNull final String newAddress) {
        MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();
        XMLFilterImpl filter = new XMLFilterImpl() {
            private boolean inAddress = false;
            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                if (localName.equals("Address") && uri.equals(version.nsUri)) {
                    inAddress = true;
                }
                super.startElement(uri,localName,qName,atts);
            }

            @Override
            public void characters(char ch[], int start, int length) throws SAXException {
                if (!inAddress) {
                    super.characters(ch, start, length);
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                if (inAddress) {
                    super.characters(newAddress.toCharArray(),0,newAddress.length());
                }
                inAddress = false;
                super.endElement(uri, localName, qName);
            }
        };
        filter.setContentHandler(xsb.createFromSAXBufferCreator());
        try {
            infoset.writeTo(filter,false);
        } catch (SAXException e) {
            throw new AssertionError(e); // impossible since we are writing from XSB to XSB.
        }

        return new WSEndpointReference(xsb,version);
    }

    /**
     * Convert the EPR to the spec version. The actual type of
     * {@link EndpointReference} to be returned depends on which version
     * of the addressing spec this EPR conforms to.
     *
     * @throws WebServiceException
     *      if the conversion fails, which can happen if the EPR contains
     *      invalid infoset (wrong namespace URI, etc.)
     */
    public @NotNull EndpointReference toSpec() {
        return ProviderImpl.INSTANCE.readEndpointReference(asSource("EndpointReference"));
    }

    /**
     * Converts the EPR to the specified spec version.
     *
     * If the {@link #getVersion() the addressing version in use} and
     * the given class is different, then this may involve version conversion.
     */
    public @NotNull <T extends EndpointReference> T toSpec(Class<T> clazz) {
        return EndpointReferenceUtil.transform(clazz,toSpec());
    }

    /**
     * Creates a proxy that can be used to talk to this EPR.
     *
     * <p>
     * All the normal WS-Addressing processing happens automatically,
     * such as setting the endpoint address to {@link #getAddress() the address},
     * and sending the reference parameters associated with this EPR as
     * headers, etc.
     */
    public @NotNull <T> T getPort(@NotNull Service jaxwsService,
                     @NotNull Class<T> serviceEndpointInterface,
                     WebServiceFeature... features)     {
        // TODO: implement it in a better way
        return jaxwsService.getPort(toSpec(),serviceEndpointInterface,features);
    }

    /**
     * Creates a {@link Dispatch} that can be used to talk to this EPR.
     *
     * <p>
     * All the normal WS-Addressing processing happens automatically,
     * such as setting the endpoint address to {@link #getAddress() the address},
     * and sending the reference parameters associated with this EPR as
     * headers, etc.
     */
    public @NotNull <T> Dispatch<T> createDispatch(
        @NotNull Service jaxwsService,
        @NotNull Class<T> type,
        @NotNull Service.Mode mode,
        WebServiceFeature... features) {

        // TODO: implement it in a better way
        return jaxwsService.createDispatch(toSpec(),type,mode,features);
    }

    /**
     * Creates a {@link Dispatch} that can be used to talk to this EPR.
     *
     * <p>
     * All the normal WS-Addressing processing happens automatically,
     * such as setting the endpoint address to {@link #getAddress() the address},
     * and sending the reference parameters associated with this EPR as
     * headers, etc.
     */
    public @NotNull Dispatch<Object> createDispatch(
        @NotNull Service jaxwsService,
        @NotNull JAXBContext context,
        @NotNull Service.Mode mode,
        WebServiceFeature... features) {

        // TODO: implement it in a better way
        return jaxwsService.createDispatch(toSpec(),context,mode,features);
    }

    /**
     * Gets the addressing version of this EPR.
     */
    public @NotNull AddressingVersion getVersion() {
        return version;
    }

    /**
     * The value of the &lt;wsa:address> header.
     */
    public @NotNull String getAddress() {
        return address;
    }

    /**
     * Returns true if this has anonymous URI as the {@link #getAddress() address}.
     */
    public boolean isAnonymous() {
        return address.equals(version.anonymousUri);
    }

    /**
     * Returns true if this has {@link AddressingVersion#noneUri none URI}
     * as the {@link #getAddress() address}.
     */
    public boolean isNone() {
        return address.equals(version.noneUri);
    }

    /**
     * Parses inside EPR and mark all reference parameters.
     */
    private void parse() throws XMLStreamException {
        // TODO: validate the EPR structure.
        // check for non-existent Address, that sort of things.

        StreamReaderBufferProcessor xsr = infoset.readAsXMLStreamReader();

        // parser should be either at the start element or the start document
        if (xsr.getEventType()==XMLStreamReader.START_DOCUMENT) {
            xsr.nextTag();
        }
        assert xsr.getEventType()==XMLStreamReader.START_ELEMENT;

        String rootLocalName = xsr.getLocalName();
        if(!xsr.getNamespaceURI().equals(version.nsUri)) {
            throw new WebServiceException(AddressingMessages.WRONG_ADDRESSING_VERSION(
                version.nsUri, xsr.getNamespaceURI()));
        }

        this.rootElement = new QName(xsr.getNamespaceURI(), rootLocalName);

        // since often EPR doesn't have a reference parameter, create array lazily
        List<Header> marks=null;

        while(xsr.nextTag()==XMLStreamReader.START_ELEMENT) {
            String localName = xsr.getLocalName();
            if(version.isReferenceParameter(localName)) {
                XMLStreamBuffer mark;
                while((mark = xsr.nextTagAndMark())!=null) {
                    if (marks==null) {
                        marks = new ArrayList<Header>();
                    }

                    // TODO: need a different header for member submission version
                    marks.add(version.createReferenceParameterHeader(
                        mark, xsr.getNamespaceURI(), xsr.getLocalName()));
                    XMLStreamReaderUtil.skipElement(xsr);
                }
            } else
            if(localName.equals("Address")) {
                if (address!=null) {
                    throw new InvalidAddressingHeaderException(new QName(version.nsUri,rootLocalName),AddressingVersion.fault_duplicateAddressInEpr);
                }
                address = xsr.getElementText().trim();
            } else {
                XMLStreamReaderUtil.skipElement(xsr);
            }
        }

        // hit to </EndpointReference> by now

        if (marks==null) {
            this.referenceParameters = EMPTY_ARRAY;
        } else {
            this.referenceParameters = marks.toArray(new Header[marks.size()]);
        }

        if (address==null) {
            throw new InvalidAddressingHeaderException(new QName(version.nsUri,rootLocalName),version.fault_missingAddressInEpr);
        }
    }


    /**
     * Reads this EPR as {@link XMLStreamReader}.
     *
     * @param localName
     *      EPR uses a different root tag name depending on the context.
     *      The returned {@link XMLStreamReader} will use the given local name
     *      for the root element name.
     */
    public XMLStreamReader read(final @NotNull String localName) throws XMLStreamException {
        return new StreamReaderBufferProcessor(infoset) {
            @Override
            protected void processElement(String prefix, String uri, String _localName, boolean inScope) {
                if (_depth == 0) {
                    _localName = localName;
                }
                super.processElement(prefix, uri, _localName, isInscope(infoset,_depth));
            }
        };
    }

    private boolean isInscope(XMLStreamBuffer buffer, int depth) {
        return buffer.getInscopeNamespaces().size() > 0 && depth ==0;
    }

    /**
     * Returns a {@link Source} that represents this EPR.
     *
     * @param localName
     *      EPR uses a different root tag name depending on the context.
     *      The returned {@link Source} will use the given local name
     *      for the root element name.
     */
    public Source asSource(@NotNull String localName) {
        return new SAXSource(new SAXBufferProcessorImpl(localName),new InputSource());
    }

    /**
     * Writes this EPR to the given {@link ContentHandler}.
     *
     * @param localName
     *      EPR uses a different root tag name depending on the context.
     *      The returned {@link Source} will use the given local name
     *      for the root element name.
     * @param fragment
     *      If true, generate a fragment SAX events without start/endDocument callbacks.
     *      If false, generate a full XML document event.
     */
    public void writeTo(@NotNull String localName, ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        SAXBufferProcessorImpl p = new SAXBufferProcessorImpl(localName);
        p.setContentHandler(contentHandler);
        p.setErrorHandler(errorHandler);
        p.process(infoset,fragment);
    }

    /**
     * Writes this EPR into the given writer.
     *
     * @param localName
     *      EPR uses a different root tag name depending on the context.
     *      The returned {@link Source} will use the given local name
     */
    public void writeTo(final @NotNull String localName, @NotNull XMLStreamWriter w) throws XMLStreamException {
        infoset.writeToXMLStreamWriter(new XMLStreamWriterFilter(w) {
            private boolean root=true;

            @Override
            public void writeStartDocument() throws XMLStreamException {
            }

            @Override
            public void writeStartDocument(String encoding, String version) throws XMLStreamException {
            }

            @Override
            public void writeStartDocument(String version) throws XMLStreamException {
            }

            @Override
            public void writeEndDocument() throws XMLStreamException {
            }

            private String override(String ln) {
                if(root) {
                    root = false;
                    return localName;
                }
                return ln;
            }

            @Override
            public void writeStartElement(String localName) throws XMLStreamException {
                super.writeStartElement(override(localName));
            }

            @Override
            public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
                super.writeStartElement(namespaceURI, override(localName));
            }

            @Override
            public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
                super.writeStartElement(prefix, override(localName), namespaceURI);
            }
        },true/*write as fragment*/);
    }

    /**
     * Returns a {@link Header} that wraps this {@link WSEndpointReference}.
     *
     * <p>
     * The returned header is immutable too, and can be reused with
     * many {@link Message}s.
     *
     * @param rootTagName
     *      The header tag name to be used, such as &lt;ReplyTo> or &lt;FaultTo>.
     *      (It's bit ugly that this method takes {@link QName} and not just local name,
     *      unlike other methods. If it's making the caller's life miserable, then
     *      we can talk.)
     */
    public Header createHeader(QName rootTagName) {
        return new EPRHeader(rootTagName,this);
    }

    /**
     * Copies all the reference parameters in this EPR as headers
     * to the given {@link HeaderList}.
     * @deprecated - use addReferenceParametersToList(MessageHeaders)
     */
    @SuppressWarnings("ManualArrayToCollectionCopy")
    public void addReferenceParametersToList(HeaderList outbound) {
        // implemented through iteration because of unsupportedoperation exception thrown from addAll method on headerlist
        // do not change
        for (Header header : referenceParameters) {
            outbound.add(header);
        }
    }

    /**
     * Copies all the reference parameters in this EPR as headers
     * to the given {@link MessageHeaders}.
     */
    public void addReferenceParametersToList(MessageHeaders outbound) {
        for (Header header : referenceParameters) {
            outbound.add(header);
        }
    }
    /**
     * Copies all the reference parameters from the given {@link HeaderList}
     * to this EPR
     */
    public void addReferenceParameters(HeaderList headers) {
        if (headers != null) {
                Header[] hs = new Header[referenceParameters.length + headers.size()];
                System.arraycopy(referenceParameters, 0, hs, 0, referenceParameters.length);
                int i = referenceParameters.length;
                for (Header h : headers) {
                        hs[i++] = h;
                }
                referenceParameters = hs;
        }
    }

    /**
     * Dumps the EPR infoset in a human-readable string.
     */
    @Override
    public String toString() {
        try {
            // debug convenience
            StringWriter sw = new StringWriter();
            XmlUtil.newTransformer().transform(asSource("EndpointReference"),new StreamResult(sw));
            return sw.toString();
        } catch (TransformerException e) {
            return e.toString();
        }
    }

    /**
     * Gets the QName of the EndpointReference element.
     * @return
     */
    @Override
    public QName getName() {
        return rootElement;
    }

    /**
     * Filtering {@link SAXBufferProcessor} that replaces the root tag name.
     */
    class SAXBufferProcessorImpl extends SAXBufferProcessor {
        private final String rootLocalName;
        private boolean root=true;

        public SAXBufferProcessorImpl(String rootLocalName) {
            super(infoset,false);
            this.rootLocalName = rootLocalName;
        }

        @Override
        protected void processElement(String uri, String localName, String qName, boolean inscope) throws SAXException {
            if(root) {
                root = false;

                if(qName.equals(localName)) {
                    qName = localName = rootLocalName;
                } else {
                    localName = rootLocalName;
                    int idx = qName.indexOf(':');
                    qName = qName.substring(0,idx+1)+rootLocalName;
                }
            }
            super.processElement(uri, localName, qName, inscope);
        }
    }

    private static final OutboundReferenceParameterHeader[] EMPTY_ARRAY = new OutboundReferenceParameterHeader[0];

    private Map<QName, EPRExtension> rootEprExtensions;

    /**
     * Represents an extensibility element inside an EndpointReference
     */
    public static abstract class EPRExtension {
        public abstract XMLStreamReader readAsXMLStreamReader() throws XMLStreamException;

        public abstract QName getQName();
    }

    /**
     * Returns the first extensibility element inside EPR root element with input QName.
     */
    public @Nullable
    EPRExtension getEPRExtension(final QName extnQName) throws XMLStreamException {
        if (rootEprExtensions == null) {
            parseEPRExtensions();
        }
        return rootEprExtensions.get(extnQName);
    }

    public @NotNull Collection<EPRExtension> getEPRExtensions() throws XMLStreamException {
        if (rootEprExtensions == null) {
            parseEPRExtensions();
        }
        return rootEprExtensions.values();
    }

    private void parseEPRExtensions() throws XMLStreamException {

        rootEprExtensions = new HashMap<QName, EPRExtension>();


        StreamReaderBufferProcessor xsr = infoset.readAsXMLStreamReader();

        // parser should be either at the start element or the start document
        if (xsr.getEventType() == XMLStreamReader.START_DOCUMENT) {
            xsr.nextTag();
        }
        assert xsr.getEventType() == XMLStreamReader.START_ELEMENT;

        if (!xsr.getNamespaceURI().equals(version.nsUri)) {
            throw new WebServiceException(AddressingMessages.WRONG_ADDRESSING_VERSION(
                    version.nsUri, xsr.getNamespaceURI()));
        }

        // since often EPR doesn't have extensions, create array lazily
        XMLStreamBuffer mark;
        String localName;
        String ns;
        while ((mark = xsr.nextTagAndMark()) != null) {
            localName = xsr.getLocalName();
            ns = xsr.getNamespaceURI();
            if (version.nsUri.equals(ns)) {
                //EPR extensions do not use the same namespace of the Addressing Version.
                //Not an extension -  SKIP
                XMLStreamReaderUtil.skipElement(xsr);
            } else {
                QName qn = new QName(ns, localName);
                rootEprExtensions.put(qn, new WSEPRExtension(mark,qn));
                XMLStreamReaderUtil.skipElement(xsr);
            }
        }
        // hit to </EndpointReference> by now
    }

    /**
     * Parses the metadata inside this EPR and obtains it in a easy-to-process form.
     *
     * <p>
     * See {@link Metadata} class for what's avaliable as "metadata".
     */
    public @NotNull Metadata getMetaData() {
        return new Metadata();
    }

    /**
     * Parses the Metadata in an EPR and provides convenience methods to access
     * the metadata.
     *
     */
   public class Metadata {
        private @Nullable QName serviceName;
        private @Nullable QName portName;
        private @Nullable QName portTypeName; //interfaceName
        private @Nullable Source wsdlSource;
        private @Nullable String wsdliLocation;

        public @Nullable QName getServiceName(){
            return serviceName;
        }
        public @Nullable QName getPortName(){
            return portName;
        }
        public @Nullable QName getPortTypeName(){
            return portTypeName;
        }
        public @Nullable Source getWsdlSource(){
            return wsdlSource;
        }
        public @Nullable String getWsdliLocation(){
            return wsdliLocation;
        }

        private Metadata() {
            try {
                parseMetaData();
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
        }

       /**
         * Parses the Metadata section of the EPR.
         */
       private void parseMetaData() throws XMLStreamException {
           StreamReaderBufferProcessor xsr = infoset.readAsXMLStreamReader();

            // parser should be either at the start element or the start document
            if (xsr.getEventType() == XMLStreamReader.START_DOCUMENT) {
               xsr.nextTag();
           }
            assert xsr.getEventType() == XMLStreamReader.START_ELEMENT;
            String rootElement = xsr.getLocalName();
            if (!xsr.getNamespaceURI().equals(version.nsUri)) {
               throw new WebServiceException(AddressingMessages.WRONG_ADDRESSING_VERSION(
                       version.nsUri, xsr.getNamespaceURI()));
           }
            String localName;
            String ns;
            if (version == AddressingVersion.W3C) {
                do {
                    //If the current element is metadata enclosure, look inside
                    if (xsr.getLocalName().equals(version.eprType.wsdlMetadata.getLocalPart())) {
                        String wsdlLoc = xsr.getAttributeValue("http://www.w3.org/ns/wsdl-instance","wsdlLocation");
                        if (wsdlLoc != null) {
                            wsdliLocation = wsdlLoc.trim();
                        }
                        XMLStreamBuffer mark;
                        while ((mark = xsr.nextTagAndMark()) != null) {
                            localName = xsr.getLocalName();
                            ns = xsr.getNamespaceURI();
                            if (localName.equals(version.eprType.serviceName)) {
                                String portStr = xsr.getAttributeValue(null, version.eprType.portName);
                                if (serviceName != null) {
                                    throw new RuntimeException("More than one "+ version.eprType.serviceName +" element in EPR Metadata");
                                }
                                serviceName = getElementTextAsQName(xsr);
                                if (serviceName != null && portStr != null) {
                                    portName = new QName(serviceName.getNamespaceURI(), portStr);
                                }
                            } else if (localName.equals(version.eprType.portTypeName)) {
                                if (portTypeName != null) {
                                    throw new RuntimeException("More than one "+ version.eprType.portTypeName +" element in EPR Metadata");
                                }
                                portTypeName = getElementTextAsQName(xsr);
                            } else if (ns.equals(WSDLConstants.NS_WSDL)
                                    && localName.equals(WSDLConstants.QNAME_DEFINITIONS.getLocalPart())) {
                                wsdlSource = new XMLStreamBufferSource(mark);
                            } else {
                                XMLStreamReaderUtil.skipElement(xsr);
                            }
                        }
                    } else {
                        //Skip is it is not root element
                        if (!xsr.getLocalName().equals(rootElement)) {
                            XMLStreamReaderUtil.skipElement(xsr);
                        }
                    }
                } while (XMLStreamReaderUtil.nextElementContent(xsr) == XMLStreamReader.START_ELEMENT);

                if(wsdliLocation != null) {
                    String wsdlLocation = wsdliLocation.trim();
                    wsdlLocation = wsdlLocation.substring(wsdliLocation.lastIndexOf(" "));
                    wsdlSource = new StreamSource(wsdlLocation);
                }
            } else if (version == AddressingVersion.MEMBER) {
                do {
                    localName = xsr.getLocalName();
                    ns = xsr.getNamespaceURI();
                    //If the current element is metadata enclosure, look inside
                    if (localName.equals(version.eprType.wsdlMetadata.getLocalPart()) &&
                            ns.equals(version.eprType.wsdlMetadata.getNamespaceURI())) {
                        while (xsr.nextTag() == XMLStreamReader.START_ELEMENT) {
                            XMLStreamBuffer mark;
                            while ((mark = xsr.nextTagAndMark()) != null) {
                                localName = xsr.getLocalName();
                                ns = xsr.getNamespaceURI();
                                if (ns.equals(WSDLConstants.NS_WSDL)
                                        && localName.equals(WSDLConstants.QNAME_DEFINITIONS.getLocalPart())) {
                                    wsdlSource = new XMLStreamBufferSource(mark);
                                } else {
                                    XMLStreamReaderUtil.skipElement(xsr);
                                }
                            }
                        }
                    } else if (localName.equals(version.eprType.serviceName)) {
                        String portStr = xsr.getAttributeValue(null, version.eprType.portName);
                        serviceName = getElementTextAsQName(xsr);
                        if (serviceName != null && portStr != null) {
                            portName = new QName(serviceName.getNamespaceURI(), portStr);
                        }
                    } else if (localName.equals(version.eprType.portTypeName)) {
                        portTypeName = getElementTextAsQName(xsr);
                    } else {
                        //Skip is it is not root element
                        if (!xsr.getLocalName().equals(rootElement)) {
                            XMLStreamReaderUtil.skipElement(xsr);
                        }
                    }
                } while (XMLStreamReaderUtil.nextElementContent(xsr) == XMLStreamReader.START_ELEMENT);
            }
        }

        private QName getElementTextAsQName(StreamReaderBufferProcessor xsr) throws XMLStreamException {
            String text = xsr.getElementText().trim();
            String prefix = XmlUtil.getPrefix(text);
            String name = XmlUtil.getLocalPart(text);
            if (name != null) {
                if (prefix != null) {
                    String ns = xsr.getNamespaceURI(prefix);
                    if (ns != null) {
                        return new QName(ns, name, prefix);
                    }
                } else {
                    return new QName(null, name);
                }
            }
            return null;
        }
    }
}
