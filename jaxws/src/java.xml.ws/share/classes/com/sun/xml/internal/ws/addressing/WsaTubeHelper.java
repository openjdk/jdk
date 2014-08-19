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

package com.sun.xml.internal.ws.addressing;

import com.sun.xml.internal.ws.addressing.model.InvalidAddressingHeaderException;
import com.sun.xml.internal.ws.addressing.model.MissingAddressingHeaderException;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.message.AddressingUtils;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLFault;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOutput;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.WSDLOperationMapping;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.model.CheckedExceptionImpl;
import com.sun.istack.internal.Nullable;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import javax.xml.soap.Detail;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.WebServiceException;

/**
 * @author Rama Pulavarthi
 * @author Arun Gupta
 */
public abstract class WsaTubeHelper {

    public WsaTubeHelper(WSBinding binding, SEIModel seiModel, WSDLPort wsdlPort) {
        this.binding = binding;
        this.wsdlPort = wsdlPort;
        this.seiModel = seiModel;
        this.soapVer = binding.getSOAPVersion();
        this.addVer = binding.getAddressingVersion();

    }

    public String getFaultAction(Packet requestPacket, Packet responsePacket) {
        String action = null;
        if(seiModel != null) {
            action = getFaultActionFromSEIModel(requestPacket,responsePacket);
        }
        if (action != null) {
            return action;
        } else {
            action = addVer.getDefaultFaultAction();
        }
        if (wsdlPort != null) {
            WSDLOperationMapping wsdlOp = requestPacket.getWSDLOperationMapping();
            if (wsdlOp != null) {
                WSDLBoundOperation wbo = wsdlOp.getWSDLBoundOperation();
                return getFaultAction(wbo, responsePacket);
            }
        }
        return action;
    }

    String getFaultActionFromSEIModel(Packet requestPacket, Packet responsePacket) {
        String action = null;
        if (seiModel == null || wsdlPort == null) {
            return action;
        }

        try {
            SOAPMessage sm = responsePacket.getMessage().copy().readAsSOAPMessage();
            if (sm == null) {
                return action;
            }

            if (sm.getSOAPBody() == null) {
                return action;
            }

            if (sm.getSOAPBody().getFault() == null) {
                return action;
            }

            Detail detail = sm.getSOAPBody().getFault().getDetail();
            if (detail == null) {
                return action;
            }

            String ns = detail.getFirstChild().getNamespaceURI();
            String name = detail.getFirstChild().getLocalName();

            WSDLOperationMapping wsdlOp = requestPacket.getWSDLOperationMapping();
            JavaMethodImpl jm = (wsdlOp != null) ? (JavaMethodImpl)wsdlOp.getJavaMethod() : null;
            if (jm != null) {
              for (CheckedExceptionImpl ce : jm.getCheckedExceptions()) {
                  if (ce.getDetailType().tagName.getLocalPart().equals(name) &&
                          ce.getDetailType().tagName.getNamespaceURI().equals(ns)) {
                      return ce.getFaultAction();
                  }
              }
            }
            return action;
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    String getFaultAction(@Nullable WSDLBoundOperation wbo, Packet responsePacket) {
        String action = AddressingUtils.getAction(responsePacket.getMessage().getHeaders(), addVer, soapVer);
        if (action != null) {
            return action;
        }

        action = addVer.getDefaultFaultAction();
        if (wbo == null) {
            return action;
        }

        try {
            SOAPMessage sm = responsePacket.getMessage().copy().readAsSOAPMessage();
            if (sm == null) {
                return action;
            }

            if (sm.getSOAPBody() == null) {
                return action;
            }

            if (sm.getSOAPBody().getFault() == null) {
                return action;
            }

            Detail detail = sm.getSOAPBody().getFault().getDetail();
            if (detail == null) {
                return action;
            }

            String ns = detail.getFirstChild().getNamespaceURI();
            String name = detail.getFirstChild().getLocalName();

            WSDLOperation o = wbo.getOperation();

            WSDLFault fault = o.getFault(new QName(ns, name));
            if (fault == null) {
                return action;
            }

            action = fault.getAction();

            return action;
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    public String getInputAction(Packet packet) {
        String action = null;

        if (wsdlPort != null) {
            WSDLOperationMapping wsdlOp = packet.getWSDLOperationMapping();
            if (wsdlOp != null) {
                WSDLBoundOperation wbo = wsdlOp.getWSDLBoundOperation();
                WSDLOperation op = wbo.getOperation();
                action = op.getInput().getAction();
            }
        }

        return action;
    }

    /**
     * This method gives the Input addressing Action for a message.
     * It gives the Action set in the wsdl operation for the corresponding payload.
     * If it is not explicitly set, it gives the soapAction
     * @param packet
     * @return input Action
     */
    public String getEffectiveInputAction(Packet packet) {
        //non-default SOAPAction beomes wsa:action
        if(packet.soapAction != null && !packet.soapAction.equals("")) {
            return packet.soapAction;
        }
        String action;

        if (wsdlPort != null) {
            WSDLOperationMapping wsdlOp = packet.getWSDLOperationMapping();
            if (wsdlOp != null) {
                WSDLBoundOperation wbo = wsdlOp.getWSDLBoundOperation();
                WSDLOperation op = wbo.getOperation();
                action = op.getInput().getAction();
            } else {
                action = packet.soapAction;
            }
        } else {
            action = packet.soapAction;
        }
        return action;
    }

    public boolean isInputActionDefault(Packet packet) {
        if (wsdlPort == null) {
            return false;
        }
        WSDLOperationMapping wsdlOp = packet.getWSDLOperationMapping();
        if(wsdlOp == null) {
            return false;
        }
        WSDLBoundOperation wbo = wsdlOp.getWSDLBoundOperation();
        WSDLOperation op = wbo.getOperation();
        return op.getInput().isDefaultAction();

    }

    public String getSOAPAction(Packet packet) {
        String action = "";

        if (packet == null || packet.getMessage() == null) {
            return action;
        }

        if (wsdlPort == null) {
            return action;
        }

        WSDLOperationMapping wsdlOp = packet.getWSDLOperationMapping();
        if (wsdlOp == null) {
            return action;
        }

        WSDLBoundOperation op = wsdlOp.getWSDLBoundOperation();
        action = op.getSOAPAction();
        return action;
    }

    public String getOutputAction(Packet packet) {
        //String action = AddressingVersion.UNSET_OUTPUT_ACTION;
        String action = null;
        WSDLOperationMapping wsdlOp = packet.getWSDLOperationMapping();
        if (wsdlOp != null) {
            JavaMethod javaMethod = wsdlOp.getJavaMethod();
            if (javaMethod != null) {
                JavaMethodImpl jm = (JavaMethodImpl) javaMethod;
                if (jm != null && jm.getOutputAction() != null && !jm.getOutputAction().equals("")) {
                    return jm.getOutputAction();
                }
            }
            WSDLBoundOperation wbo = wsdlOp.getWSDLBoundOperation();
            if (wbo != null) return getOutputAction(wbo);
        }
        return action;
    }

    String getOutputAction(@Nullable WSDLBoundOperation wbo) {
        String action = AddressingVersion.UNSET_OUTPUT_ACTION;
        if (wbo != null) {
            WSDLOutput op = wbo.getOperation().getOutput();
            if (op != null) {
                action = op.getAction();
            }
        }
        return action;
    }

    public SOAPFault createInvalidAddressingHeaderFault(InvalidAddressingHeaderException e, AddressingVersion av) {
        QName name = e.getProblemHeader();
        QName subsubcode = e.getSubsubcode();
        QName subcode = av.invalidMapTag;
        String faultstring = String.format(av.getInvalidMapText(), name, subsubcode);

        try {
            SOAPFactory factory;
            SOAPFault fault;
            if (soapVer == SOAPVersion.SOAP_12) {
                factory = SOAPVersion.SOAP_12.getSOAPFactory();
                fault = factory.createFault();
                fault.setFaultCode(SOAPConstants.SOAP_SENDER_FAULT);
                fault.appendFaultSubcode(subcode);
                fault.appendFaultSubcode(subsubcode);
                getInvalidMapDetail(name, fault.addDetail());
            } else {
                factory = SOAPVersion.SOAP_11.getSOAPFactory();
                fault = factory.createFault();
                fault.setFaultCode(subsubcode);
            }

            fault.setFaultString(faultstring);

            return fault;
        } catch (SOAPException se) {
            throw new WebServiceException(se);
        }
    }

    public SOAPFault newMapRequiredFault(MissingAddressingHeaderException e) {
        QName subcode = addVer.mapRequiredTag;
        QName subsubcode = addVer.mapRequiredTag;
        String faultstring = addVer.getMapRequiredText();

        try {
            SOAPFactory factory;
            SOAPFault fault;
            if (soapVer == SOAPVersion.SOAP_12) {
                factory = SOAPVersion.SOAP_12.getSOAPFactory();
                fault = factory.createFault();
                fault.setFaultCode(SOAPConstants.SOAP_SENDER_FAULT);
                fault.appendFaultSubcode(subcode);
                fault.appendFaultSubcode(subsubcode);
                getMapRequiredDetail(e.getMissingHeaderQName(), fault.addDetail());
            } else {
                factory = SOAPVersion.SOAP_11.getSOAPFactory();
                fault = factory.createFault();
                fault.setFaultCode(subsubcode);
            }

            fault.setFaultString(faultstring);

            return fault;
        } catch (SOAPException se) {
            throw new WebServiceException(se);
        }
    }

    public abstract void getProblemActionDetail(String action, Element element);
    public abstract void getInvalidMapDetail(QName name, Element element);
    public abstract void getMapRequiredDetail(QName name, Element element);

    protected SEIModel seiModel;
    protected WSDLPort wsdlPort;
    protected WSBinding binding;
    protected final SOAPVersion soapVer;
    protected final AddressingVersion addVer;
}
