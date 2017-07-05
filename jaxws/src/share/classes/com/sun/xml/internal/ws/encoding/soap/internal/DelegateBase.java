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
package com.sun.xml.internal.ws.encoding.soap.internal;

import com.sun.xml.internal.ws.pept.Delegate;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.pept.encoding.Decoder;
import com.sun.xml.internal.ws.pept.encoding.Encoder;
import com.sun.xml.internal.ws.pept.ept.ContactInfo;
import com.sun.xml.internal.ws.pept.ept.ContactInfoList;
import com.sun.xml.internal.ws.pept.ept.ContactInfoListIterator;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.client.ContextMap;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.ContactInfoBase;
import com.sun.xml.internal.ws.client.ContactInfoListImpl;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.client.WSServiceDelegate;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.Service;
import java.util.Iterator;

/**
 * @author WS Development Team
 */
public class DelegateBase implements Delegate {
    protected ContactInfoList contactInfoList;
    protected WSServiceDelegate service;

    public DelegateBase() {
    }

    public DelegateBase(ContactInfoList contactInfoList) {
        this.contactInfoList = contactInfoList;
    }

    public DelegateBase(ContactInfoList cil, WSServiceDelegate service) {
       this(cil);
       this.service = service;
    }

    public MessageStruct getMessageStruct() {
        return new MessageInfoBase();
    }

    public void send(MessageStruct messageStruct) {
        MessageInfo messageInfo = (MessageInfo) messageStruct;

        // ContactInfoListIterator iterator = contactInfoList.iterator();
        if (!contactInfoList.iterator().hasNext())
            throw new RuntimeException("can't pickup message encoder/decoder, no ContactInfo!");

        ContextMap properties = (ContextMap)
                messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        BindingProvider stub = (BindingProvider)properties.get(BindingProviderProperties.JAXWS_CLIENT_HANDLE_PROPERTY);

        BindingImpl bi = (BindingImpl)stub.getBinding();
        String bindingId = bi.getBindingId();
        ContactInfo contactInfo = getContactInfo(contactInfoList, bindingId);

        messageInfo.setEPTFactory(contactInfo);
        MessageDispatcher messageDispatcher = contactInfo.getMessageDispatcher(messageInfo);
        messageDispatcher.send(messageInfo);
    }

    private ContactInfo getContactInfo(ContactInfoList cil, String bindingId){
        ContactInfoListIterator iter = cil.iterator();
        while(iter.hasNext()){
            ContactInfoBase cib = (ContactInfoBase)iter.next();
            if(cib.getBindingId().equals(bindingId))
                return cib;
        }
        //return the first one
        return cil.iterator().next();
    }
}
