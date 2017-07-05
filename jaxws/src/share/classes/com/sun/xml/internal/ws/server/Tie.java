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

package com.sun.xml.internal.ws.server;

import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.MessageContext.Scope;
import com.sun.xml.internal.ws.pept.Delegate;
import com.sun.xml.internal.ws.pept.ept.EPTFactory;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.protocol.MessageDispatcher;
import com.sun.xml.internal.ws.encoding.soap.internal.DelegateBase;
import com.sun.xml.internal.ws.model.RuntimeModel;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.developer.JAXWSProperties;

/**
 * Entry point for all server requests.
 *
 * @author WS Development Team
 */
public class Tie implements com.sun.xml.internal.ws.spi.runtime.Tie {

    /**
     * Common entry point for server runtime. <br>
     * Creates a MessageInfo for every Request/Response.<br>
     * Creates a RuntimeContext for every Request/Response and sets that as a metadata in
     * MessageInfo. Doesn't create any other metadata on MessageInfo. If anything is needed,
     * that can be created on RuntimeContext<br>
     * EPTFactoryFactoryBase is used to select a correct EPTFactory<br>
     * Calls MessageDispatcher.receive(MessageInfo). <br>
     * MessageDispatcher orchestrates all the flow: reading from WSConnection,
     * decodes message to parameters, invoking implementor, encodes parameters to message,
     * and writing to WSConnection
     *
     * @param connection encapsulates multiple transports
     * @param endpoint has all the information about target endpoint
     * @throws Exception throws Exception if any error occurs
     */
    public void handle(WSConnection connection,
        com.sun.xml.internal.ws.spi.runtime.RuntimeEndpointInfo endpoint)
    throws Exception {

        // Create MessageInfo. MessageInfo holds all the info for this request
        Delegate delegate = new DelegateBase();
        MessageInfo messageInfo = (MessageInfo)delegate.getMessageStruct();

        // Create runtime context, runtime model for dynamic runtime
        RuntimeEndpointInfo endpointInfo = (RuntimeEndpointInfo)endpoint;
        RuntimeModel runtimeModel = endpointInfo.getRuntimeModel();
        RuntimeContext runtimeContext = new RuntimeContext(runtimeModel);
        runtimeContext.setRuntimeEndpointInfo(endpointInfo);

        // Update MessageContext
        MessageContext msgCtxt =
            endpointInfo.getWebServiceContext().getMessageContext();
        updateMessageContext(endpointInfo, msgCtxt);

        // Set runtime context on MessageInfo
        MessageInfoUtil.setRuntimeContext(messageInfo, runtimeContext);
        messageInfo.setConnection(connection);

        // Select EPTFactory based on binding, and transport
        EPTFactory eptFactory = EPTFactoryFactoryBase.getEPTFactory(messageInfo);
        messageInfo.setEPTFactory(eptFactory);

        // MessageDispatcher archestrates the flow
        MessageDispatcher messageDispatcher =
            messageInfo.getEPTFactory().getMessageDispatcher(messageInfo);
        messageDispatcher.receive(messageInfo);
    }

    /**
     * Updates MessageContext object with Service, and Port QNames
     */
    private void updateMessageContext( RuntimeEndpointInfo endpoint,
        MessageContext ctxt) {

        ctxt.put(MessageContext.WSDL_SERVICE, endpoint.getServiceName());
        ctxt.setScope(MessageContext.WSDL_SERVICE, Scope.APPLICATION);
        ctxt.put(MessageContext.WSDL_PORT, endpoint.getPortName());
        ctxt.setScope(MessageContext.WSDL_PORT, Scope.APPLICATION);
        ctxt.put(JAXWSProperties.MTOM_THRESHOLOD_VALUE, endpoint.getMtomThreshold());
        ctxt.setScope(JAXWSProperties.MTOM_THRESHOLOD_VALUE, Scope.APPLICATION);
    }

}
