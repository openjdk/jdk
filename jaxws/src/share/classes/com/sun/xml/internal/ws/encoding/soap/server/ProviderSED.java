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
package com.sun.xml.internal.ws.encoding.soap.server;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.encoding.internal.InternalEncoder;
import com.sun.xml.internal.ws.encoding.soap.internal.BodyBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.model.soap.SOAPRuntimeModel;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import javax.xml.transform.Source;
import javax.xml.ws.soap.SOAPBinding;

public class ProviderSED implements InternalEncoder {

    public void toMessageInfo(Object internalMessage, MessageInfo messageInfo) {
        throw new UnsupportedOperationException();
    }

    /*
     * Sets Source in InternalMessage's BodyBlock. If there is an exception
     * in MessageInfo, it is set as fault in BodyBlock
     *
     */
    public InternalMessage toInternalMessage(MessageInfo messageInfo) {
        InternalMessage internalMessage = new InternalMessage();
        switch(messageInfo.getResponseType()) {
            case MessageStruct.NORMAL_RESPONSE :
                Object obj = messageInfo.getResponse();
                if (obj instanceof Source) {
                    BodyBlock bodyBlock = new BodyBlock((Source)obj);
                    internalMessage.setBody(bodyBlock);
                } else {
                    throw new UnsupportedOperationException();
                }
                break;

            case MessageStruct.CHECKED_EXCEPTION_RESPONSE :
                // invoke() doesn't throw any checked exception
                // Fallthrough

            case MessageStruct.UNCHECKED_EXCEPTION_RESPONSE :
                RuntimeContext rtContext = MessageInfoUtil.getRuntimeContext(messageInfo);
                BindingImpl bindingImpl =
                    (BindingImpl)rtContext.getRuntimeEndpointInfo().getBinding();
                String bindingId = bindingImpl.getBindingId();
                if (bindingId.equals(SOAPBinding.SOAP11HTTP_BINDING)) {
                    SOAPRuntimeModel.createFaultInBody(messageInfo.getResponse(),
                            null, null, internalMessage);
                } else if (bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING)) {
                    SOAPRuntimeModel.createSOAP12FaultInBody(messageInfo.getResponse(),
                            null, null, null, internalMessage);
                }
                break;
        }
        return internalMessage;
    }

}
