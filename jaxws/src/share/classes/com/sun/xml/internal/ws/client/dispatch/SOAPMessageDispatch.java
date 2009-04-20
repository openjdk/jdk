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

package com.sun.xml.internal.ws.client.dispatch;

import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.message.saaj.SAAJMessage;
import com.sun.xml.internal.ws.resources.DispatchMessages;

import javax.xml.namespace.QName;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The <code>SOAPMessageDispatch</code> class provides support
 * for the dynamic invocation of a service endpoint operation using
 * the <code>SOAPMessage</code> class. The <code>javax.xml.ws.Service</code>
 * interface acts as a factory for the creation of <code>SOAPMessageDispatch</code>
 * instances.
 *
 * @author WS Development Team
 * @version 1.0
 */
public class SOAPMessageDispatch extends com.sun.xml.internal.ws.client.dispatch.DispatchImpl<SOAPMessage> {
    public SOAPMessageDispatch(QName port, Service.Mode mode, WSServiceDelegate owner, Tube pipe, BindingImpl binding, WSEndpointReference epr) {
        super(port, mode, owner, pipe, binding, epr);
    }

    Packet createPacket(SOAPMessage arg) {

        if (arg == null && !isXMLHttp(binding))
           throw new WebServiceException(DispatchMessages.INVALID_NULLARG_SOAP_MSGMODE(mode.name(), Service.Mode.PAYLOAD.toString()));

        MimeHeaders mhs = arg.getMimeHeaders();
        // TODO: these two lines seem dangerous. It should be left up to the transport and codec
        // to decide how they are sent. remove after 2.1 FCS - KK.
        mhs.addHeader("Content-Type", "text/xml");
        mhs.addHeader("Content-Transfer-Encoding", "binary");
        Map<String, List<String>> ch = new HashMap<String, List<String>>();
        for (Iterator iter = arg.getMimeHeaders().getAllHeaders(); iter.hasNext();)
        {
            MimeHeader mh = (MimeHeader) iter.next();
            List<String> h = new ArrayList<String>();
            h.add(mh.getValue());
            ch.put(mh.getName(), h);
        }

        Packet packet = new Packet(new SAAJMessage(arg));
        packet.invocationProperties.put(MessageContext.HTTP_REQUEST_HEADERS,ch);
        return packet;
    }

    SOAPMessage toReturnValue(Packet response) {
        try {

            //not sure if this is the correct way to deal with this.
            if ( response ==null || response.getMessage() == null )
                     throw new WebServiceException(DispatchMessages.INVALID_RESPONSE());
            else
                return response.getMessage().readAsSOAPMessage();
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }
}
