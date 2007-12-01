/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.encoding.soap.message;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBTypeSerializer;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.streaming.DOMStreamReader;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPUtil;

import javax.xml.namespace.QName;
import javax.xml.soap.Detail;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPBinding;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

import org.w3c.dom.Node;

/**
 * SOAP 1.2 soap soapFault info class
 */
public class SOAP12FaultInfo extends SOAPFaultInfo {
    private FaultCode code;
    private FaultReason reason;
    private String node;
    private String role;
    private Object detail;

    public SOAP12FaultInfo(FaultCode code, FaultReason reason, String node, String role, Object detail) {
        this.code = code;
        this.reason = reason;
        this.node = node;
        this.role = role;
        this.detail = detail;

        soapFault = SOAPUtil.createSOAPFault(SOAPBinding.SOAP12HTTP_BINDING);
        setFaultCode();
        setFaultReason();
        setFaultNode();
        setFaultRole();
        if((detail != null) && (detail instanceof Detail)){
            Node n = soapFault.getOwnerDocument().importNode((Detail)detail, true);
            soapFault.appendChild(n);
        }else{
            faultBean = (detail != null)?(JAXBBridgeInfo)detail:null;
        }
    }

    public SOAP12FaultInfo(SOAPFault fault) {
        this.soapFault = fault;

        //fault code
        QName fc = soapFault.getFaultCodeAsQName();
        Iterator iter = soapFault.getFaultSubcodes();
        code = new FaultCode(FaultCodeEnum.get(fc), iter);

        try {
            //fault reason
            List<FaultReasonText> texts = new ArrayList<FaultReasonText>();
            iter = soapFault.getFaultReasonLocales();
            while(iter.hasNext()){
                Locale lang = (Locale)iter.next();
                String text = soapFault.getFaultReasonText(lang);
                texts.add(new FaultReasonText(text, lang));
            }
            reason = new FaultReason(texts);

            //node
            node = soapFault.getFaultNode();

            //role
            role = soapFault.getFaultRole();

            //detail
            detail = soapFault.getDetail();
        } catch (SOAPException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public FaultCode getFaultCode() {
        return code;
    }

    public FaultReason getReasons() {
        return reason;
    }

    public String getNode() {
        return node;
    }

    public String getRole() {
        return role;
    }

    private void setFaultRole() {
        if (soapFault == null || role == null)
            return;
        try {
            soapFault.setFaultRole(role);
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    private void setFaultNode() {
        if (soapFault == null || node == null)
            return;
        try {
            soapFault.setFaultNode(node);
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    private void setFaultReason() {
        if (soapFault == null || reason == null)
            return;
        List<FaultReasonText> texts = reason.getFaultReasonTexts();
        for (FaultReasonText text : texts) {
            try {
                soapFault.addFaultReasonText(text.getValue(), text.getLanguage());
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }
    }

    /**
     * add SOAP 1.2 Fault Code/Subcodes to javax.xml.soap.SOAPFault
     */
    private void setFaultCode() {
        if (soapFault == null || code == null)
            return;
        try {
            soapFault.setFaultCode(code.getValue().value());
            FaultSubcode fsc = code.getSubcode();
            while (fsc != null) {
                soapFault.appendFaultSubcode(fsc.getValue());
                fsc = fsc.getSubcode();
            }
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }


    public void write(XMLStreamWriter writer, MessageInfo mi) {
        try {
            writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                    SOAP12Constants.QNAME_SOAP_FAULT.getLocalPart(),
                    SOAP12Constants.QNAME_SOAP_FAULT.getNamespaceURI());
            // Writing NS since this may be called without writing envelope
            writer.writeNamespace(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                    SOAP12Constants.QNAME_SOAP_FAULT.getNamespaceURI());

            code.write(writer); //<soapenv:Code> ... </soapenv:Code>
            reason.write(writer);


            //<soapenv:Node>...</soapenv:Node>
            if (node != null) {
                writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                        SOAP12Constants.QNAME_FAULT_NODE.getLocalPart(), SOAP12NamespaceConstants.ENVELOPE);
                writer.writeCharacters(node);
                writer.writeEndElement();
            }

            //<soapenv:Role>...</soapenv:Role>
            if (role != null) {
                writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                        SOAP12Constants.QNAME_FAULT_ROLE.getLocalPart(), SOAP12NamespaceConstants.ENVELOPE);
                writer.writeCharacters(role);
                writer.writeEndElement();
            }

            //<soapenv:Detail>...</soapenv:Detail>
            if (detail != null) {
                // Not RuntimeException, Not header soapFault
                if (detail instanceof Detail) {
                    // SOAPFaultException
                    SOAPEncoder.serializeReader(new DOMStreamReader((Detail) detail), writer);
                } else if (detail instanceof JAXBBridgeInfo) {
                    // Service specific exception
                    startDetailElement(writer);     // <soapenv:Detail>
                    RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(mi);
                    BridgeContext bridgeContext = rtCtxt.getBridgeContext();
                    ((JAXBBridgeInfo) detail).serialize(bridgeContext, writer);
                    writer.writeEndElement();  // </soapenv:Detail>
                }
            }
            writer.writeEndElement();                // </soapenv:Fault>
        } catch (XMLStreamException e) {
            throw new ServerRtException(e);
        }
    }

    private void startDetailElement(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                SOAP12Constants.QNAME_FAULT_DETAIL.getLocalPart(), SOAP12NamespaceConstants.ENVELOPE);
    }
}
