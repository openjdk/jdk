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
package com.sun.xml.internal.ws.handler;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.message.saaj.SAAJMessage;

import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link SOAPMessageContext}. This class is used at runtime
 * to pass to the handlers for processing soap messages.
 *
 * @see MessageContextImpl
 *
 * @author WS Development Team
 */
class SOAPMessageContextImpl extends MessageUpdatableContext implements SOAPMessageContext {

    private Set<String> roles;
    private SOAPMessage soapMsg = null;
    private WSBinding binding;

    public SOAPMessageContextImpl(WSBinding binding, Packet packet) {
        super(packet);
        this.binding = binding;
    }

    public SOAPMessage getMessage() {
        if(soapMsg == null) {
            try {
                soapMsg = packet.getMessage().readAsSOAPMessage();
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }
        return soapMsg;
    }

    public void setMessage(SOAPMessage soapMsg) {
        try {
            this.soapMsg = soapMsg;
        } catch(Exception e) {
            throw new WebServiceException(e);
        }
    }

    void setPacketMessage(Message newMessage){
        if(newMessage != null) {
            packet.setMessage(newMessage);
            soapMsg = null;
        }
    }

    protected void updateMessage() {
        //Check if SOAPMessage has changed, if so construct new one,
        // Packet are handled through MessageContext
        if(soapMsg != null) {
            packet.setMessage(new SAAJMessage(soapMsg));
            soapMsg = null;
        }
    }

    public Object[] getHeaders(QName header, JAXBContext jaxbContext, boolean allRoles) {
        SOAPVersion soapVersion = binding.getSOAPVersion();

        List<Object> beanList = new ArrayList<Object>();
        try {
            Iterator<Header> itr = packet.getMessage().getHeaders().getHeaders(header,false);
            if(allRoles) {
                while(itr.hasNext()) {
                    beanList.add(itr.next().readAsJAXB(jaxbContext.createUnmarshaller()));
                }
            } else {
                while(itr.hasNext()) {
                    Header soapHeader = itr.next();
                    //Check if the role is one of the roles on this Binding
                    String role = soapHeader.getRole(soapVersion);
                    if(getRoles().contains(role)) {
                        beanList.add(soapHeader.readAsJAXB(jaxbContext.createUnmarshaller()));
                    }
                }
            }
            return beanList.toArray();
        } catch(Exception e) {
            throw new WebServiceException(e);
        }
    }

    public Set<String> getRoles() {
        return roles;
    }

    void setRoles(Set<String> roles) {
        this.roles = roles;
    }
}
