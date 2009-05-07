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

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.util.DOMUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import javax.xml.soap.Detail;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;

/**
 * This class represents SOAP1.1 Fault. This class will be used to marshall/unmarshall a soap fault using JAXB.
 * <p/>
 * <pre>
 * Example:
 * <p/>
 *     &lt;soap:Fault xmlns:soap='http://schemas.xmlsoap.org/soap/envelope/'>
 *         &lt;faultcode>soap:Client&lt;/faultcode>
 *         &lt;faultstring>Invalid message format&lt;/faultstring>
 *         &lt;faultactor>http://example.org/someactor&lt;/faultactor>
 *         &lt;detail>
 *             &lt;m:msg xmlns:m='http://example.org/faults/exceptions'>
 *                 Test message
 *             &lt;/m:msg>
 *         &lt;/detail>
 *     &lt;/soap:Fault>
 * <p/>
 * Above, m:msg, if a known fault (described in the WSDL), IOW, if m:msg is known by JAXBContext it should be unmarshalled into a
 * Java object otherwise it should be deserialized as {@link javax.xml.soap.Detail}
 * </pre>
 * <p/>
 *
 * @author Vivek Pandey
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "faultcode",
        "faultstring",
        "faultactor",
        "detail"
        })
@XmlRootElement(name = "Fault", namespace = "http://schemas.xmlsoap.org/soap/envelope/")
class SOAP11Fault extends SOAPFaultBuilder {
    @XmlElement(namespace = "")
    private QName faultcode;

    @XmlElement(namespace = "")
    private String faultstring;

    @XmlElement(namespace = "")
    private String faultactor;

    @XmlElement(namespace = "")
    private DetailType detail;

    SOAP11Fault() {
    }

    /**
     * This constructor takes soap fault detail among other things. The detail could represent {@link javax.xml.soap.Detail}
     * or a java object that can be marshalled/unmarshalled by JAXB.
     *
     * @param code
     * @param reason
     * @param actor
     * @param detailObject
     */
    SOAP11Fault(QName code, String reason, String actor, Element detailObject) {
        this.faultcode = code;
        this.faultstring = reason;
        this.faultactor = actor;
        if (detailObject != null) {
            if(detailObject.getNamespaceURI().equals("") && detailObject.getLocalName().equals("detail")){
                detail = new DetailType();
                for(Element detailEntry : DOMUtil.getChildElements(detailObject)){
                    detail.getDetails().add(detailEntry);
                }
            }else{
                detail = new DetailType(detailObject);
            }
        }
    }

    SOAP11Fault(SOAPFault fault) {
        this.faultcode = fault.getFaultCodeAsQName();
        this.faultstring = fault.getFaultString();
        this.faultactor = fault.getFaultActor();
        if (fault.getDetail() != null) {
            detail = new DetailType(fault.getDetail());
        }
    }

    QName getFaultcode() {
        return faultcode;
    }

    void setFaultcode(QName faultcode) {
        this.faultcode = faultcode;
    }

    @Override
    String getFaultString() {
        return faultstring;
    }

    void setFaultstring(String faultstring) {
        this.faultstring = faultstring;
    }

    String getFaultactor() {
        return faultactor;
    }

    void setFaultactor(String faultactor) {
        this.faultactor = faultactor;
    }

    /**
     * returns the object that represents detail.
     */
    @Override
    DetailType getDetail() {
        return detail;
    }

    void setDetail(DetailType detail) {
        this.detail = detail;
    }

    protected Throwable getProtocolException() {
        try {
            SOAPFault fault = SOAPVersion.SOAP_11.saajSoapFactory.createFault(faultstring, faultcode);
            if(detail != null && detail.getDetail(0) != null) {
                Node n = fault.getOwnerDocument().importNode(detail.getDetail(0), true);
                Detail d = fault.addDetail();
                d.appendChild(n);
            }
            fault.setFaultActor(faultactor);
            return new SOAPFaultException(fault);
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }
}
