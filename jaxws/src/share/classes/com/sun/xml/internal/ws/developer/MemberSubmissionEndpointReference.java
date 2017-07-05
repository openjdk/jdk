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

package com.sun.xml.internal.ws.developer;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.addressing.v200408.MemberSubmissionAddressingConstants;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import org.w3c.dom.Element;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceException;
import java.util.List;
import java.util.Map;

/**
 * Data model for Member Submission WS-Addressing specification. This is modeled after the
 * member submission schema at:
 *
 *  http://schemas.xmlsoap.org/ws/2004/08/addressing/
 *
 * @author Kathy Walsh
 * @author Vivek Pandey
 */

@XmlRootElement(name = "EndpointReference", namespace = MemberSubmissionEndpointReference.MSNS)
@XmlType(name = "EndpointReferenceType", namespace = MemberSubmissionEndpointReference.MSNS)
public class MemberSubmissionEndpointReference extends EndpointReference implements MemberSubmissionAddressingConstants {

    private final static JAXBContext msjc = MemberSubmissionEndpointReference.getMSJaxbContext();

    public MemberSubmissionEndpointReference() {
    }

    /**
     * construct an EPR from infoset representation
     *
     * @param source A source object containing valid XmlInfoset
     *               instance consistent with the Member Submission WS-Addressing
     * @throws javax.xml.ws.WebServiceException
     *                              if the source does not contain a valid W3C WS-Addressing
     *                              EndpointReference.
     * @throws WebServiceException if the <code>null</code> <code>source</code> value is given
     */
    public MemberSubmissionEndpointReference(@NotNull Source source) {

        if (source == null)
            throw new WebServiceException("Source parameter can not be null on constructor");

        try {
            Unmarshaller unmarshaller = MemberSubmissionEndpointReference.msjc.createUnmarshaller();
            MemberSubmissionEndpointReference epr = unmarshaller.unmarshal(source,MemberSubmissionEndpointReference.class).getValue();

            this.addr = epr.addr;
            this.referenceProperties = epr.referenceProperties;
            this.referenceParameters = epr.referenceParameters;
            this.portTypeName = epr.portTypeName;
            this.serviceName = epr.serviceName;
            this.attributes = epr.attributes;
            this.elements = epr.elements;
        } catch (JAXBException e) {
            throw new WebServiceException("Error unmarshalling MemberSubmissionEndpointReference ", e);
        } catch (ClassCastException e) {
            throw new WebServiceException("Source did not contain MemberSubmissionEndpointReference", e);
        }
    }

    public void writeTo(Result result) {
        try {
            Marshaller marshaller = MemberSubmissionEndpointReference.msjc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
            marshaller.marshal(this, result);
        } catch (JAXBException e) {
            throw new WebServiceException("Error marshalling W3CEndpointReference. ", e);
        }
    }

    /**
     * Constructs a Source containing the wsdl from the MemberSubmissionEndpointReference
     *
     * @return Source A source object containing the wsdl in the MemeberSubmissionEndpointReference, if present.
     */
    public Source toWSDLSource() {
        Element wsdlElement = null;

        for (Element elem : elements) {
            if (elem.getNamespaceURI().equals(WSDLConstants.NS_WSDL) &&
                    elem.getLocalName().equals(WSDLConstants.QNAME_DEFINITIONS.getLocalPart())) {
                wsdlElement = elem;
            }
        }

        return new DOMSource(wsdlElement);
    }


    private static JAXBContext getMSJaxbContext() {
        try {
            return JAXBContext.newInstance(MemberSubmissionEndpointReference.class);
        } catch (JAXBException e) {
            throw new WebServiceException("Error creating JAXBContext for MemberSubmissionEndpointReference. ", e);
        }
    }

    @XmlElement(name = "Address", namespace = MemberSubmissionEndpointReference.MSNS)
    public Address addr;

    @XmlElement(name = "ReferenceProperties", namespace = MemberSubmissionEndpointReference.MSNS)
    public Elements referenceProperties;

    @XmlElement(name = "ReferenceParameters", namespace = MemberSubmissionEndpointReference.MSNS)
    public Elements referenceParameters;

    @XmlElement(name = "PortType", namespace = MemberSubmissionEndpointReference.MSNS)
    public AttributedQName portTypeName;

    @XmlElement(name = "ServiceName", namespace = MemberSubmissionEndpointReference.MSNS)
    public ServiceNameType serviceName;

    @XmlAnyAttribute
    public Map<QName,String> attributes;

    @XmlAnyElement
    public List<Element> elements;

    public static class Address {
        public Address() {
        }

        @XmlValue
        public String uri;
        @XmlAnyAttribute
        public Map<QName, String> attributes;
    }

    public static class Elements {
        public Elements() {}

        @XmlAnyElement
        public List<Element> elements;
    }


    public static class AttributedQName {
        public AttributedQName() {
        }

        @XmlValue
        public QName name;
        @XmlAnyAttribute
        public Map<QName, String> attributes;
    }

    public static class ServiceNameType extends AttributedQName{
        public ServiceNameType() {
        }

        @XmlAttribute(name="PortName")
        public String portName;
    }

    protected static final String MSNS = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
}
