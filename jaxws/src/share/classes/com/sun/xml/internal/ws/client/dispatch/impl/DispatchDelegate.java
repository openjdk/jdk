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
package com.sun.xml.internal.ws.client.dispatch.impl;

import com.sun.xml.internal.ws.pept.ept.ContactInfo;
import com.sun.xml.internal.ws.pept.ept.ContactInfoList;
import com.sun.xml.internal.ws.pept.ept.ContactInfoListIterator;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.encoding.soap.internal.DelegateBase;
import com.sun.xml.internal.ws.client.*;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.xml.internal.ws.binding.BindingImpl;

/**
 * @author WS Development Team
 */
public class DispatchDelegate extends DelegateBase {

    private static final Logger logger =
        Logger.getLogger(new StringBuffer().append(com.sun.xml.internal.ws.util.Constants.LoggingDomain).append(".client.dispatch").toString());

    public DispatchDelegate() {
    }

    public DispatchDelegate(ContactInfoList contactInfoList) {
        this.contactInfoList = contactInfoList;
    }

    public void send(MessageStruct messageStruct) {
        MessageInfo messageInfo = (MessageInfo) messageStruct;

        ContextMap properties = (ContextMap)
                messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        BindingProvider dispatch = (BindingProvider)properties.get(BindingProviderProperties.JAXWS_CLIENT_HANDLE_PROPERTY);

        if (!contactInfoList.iterator().hasNext())
            throw new WebServiceException("can't pickup message encoder/decoder, no ContactInfo!");


        BindingImpl bi = (BindingImpl)dispatch.getBinding();
        String bindingId = bi.getBindingId();
        ContactInfo contactInfo = getContactInfo(contactInfoList, bindingId);
        messageInfo.setEPTFactory(contactInfo);
        messageInfo.setConnection(contactInfo.getConnection(messageInfo));

        MessageDispatcher messageDispatcher =
            contactInfo.getMessageDispatcher(messageInfo);
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
    /*private ContactInfo getContactInfo(ContactInfoListIterator iterator, MessageStruct messageStruct) {
        if (!iterator.hasNext())
            throw new RuntimeException("no next");

        ContactInfo contactInfo = iterator.next();
        //Todo: use Map
        //if fast encoding go to next
         if (isFastEncoding((RequestContext)
                 messageStruct.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY))) {
             if (iterator.hasNext())
                 contactInfo = iterator.next();
             else {
                 if (logger.isLoggable(Level.INFO))     //needs localicalization
                     logger.info("Defaulting to XML Encoding. ");
                 setDefaultEncoding((RequestContext)
                         messageStruct.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY));
             }
         } else
             setDefaultEncoding((RequestContext)
                     messageStruct.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY));

        return contactInfo;
    }
 */

     private void setDefaultEncoding(RequestContext requestContext) {
         requestContext.put(BindingProviderProperties.ACCEPT_ENCODING_PROPERTY,
                 BindingProviderProperties.XML_ENCODING_VALUE);
     }

}
