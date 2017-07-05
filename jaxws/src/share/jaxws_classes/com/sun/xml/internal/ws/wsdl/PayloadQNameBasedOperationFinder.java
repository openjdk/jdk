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

package com.sun.xml.internal.ws.wsdl;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.WSDLOperationMapping;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.util.QNameMap;
import com.sun.xml.internal.ws.wsdl.DispatchException;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * An {@link WSDLOperationFinder} that uses SOAP payload first child's QName as the key for dispatching.
 * <p/>
 * A map of all payload QNames that the operations in the port allow and the corresponding QName of the wsdl operation
 * is initialized in the constructor. The payload QName is extracted from the
 * request {@link com.sun.xml.internal.ws.api.message.Packet} and used to identify the wsdl operation.
 *
 * @author Rama Pulavarthi
 * @author Arun Gupta
 * @author Jitendra Kotamraju
 */
final class PayloadQNameBasedOperationFinder extends WSDLOperationFinder {
    private static final Logger LOGGER = Logger.getLogger(PayloadQNameBasedOperationFinder.class.getName());

    public static final String EMPTY_PAYLOAD_LOCAL = "";
    public static final String EMPTY_PAYLOAD_NSURI = "";
    public static final QName EMPTY_PAYLOAD = new QName(EMPTY_PAYLOAD_NSURI, EMPTY_PAYLOAD_LOCAL);

    private final QNameMap<WSDLOperationMapping> methodHandlers = new QNameMap<WSDLOperationMapping>();
    private final QNameMap<List<String>> unique = new QNameMap<List<String>>();


    public PayloadQNameBasedOperationFinder(WSDLPort wsdlModel, WSBinding binding, @Nullable SEIModel seiModel) {
        super(wsdlModel,binding,seiModel);
        if (seiModel != null) {
            // Find if any payload QNames repeat for operations
            for (JavaMethodImpl m : ((AbstractSEIModelImpl) seiModel).getJavaMethods()) {
                if(m.getMEP().isAsync)
                    continue;
                QName name = m.getRequestPayloadName();
                if (name == null)
                    name = EMPTY_PAYLOAD;
                List<String> methods = unique.get(name);
                if (methods == null) {
                    methods = new ArrayList<String>();
                    unique.put(name, methods);
                }
                methods.add(m.getMethod().getName());
            }

            // Log warnings about non unique payload QNames
            for (QNameMap.Entry<List<String>> e : unique.entrySet()) {
                if (e.getValue().size() > 1) {
                    LOGGER.warning(ServerMessages.NON_UNIQUE_DISPATCH_QNAME(e.getValue(), e.createQName()));
                }
            }

            for (JavaMethodImpl m : ((AbstractSEIModelImpl) seiModel).getJavaMethods()) {
                QName name = m.getRequestPayloadName();
                if (name == null)
                    name = EMPTY_PAYLOAD;
                // Set up method handlers only for unique QNames. So that dispatching
                // happens consistently for a method
                if (unique.get(name).size() == 1) {
                    methodHandlers.put(name, wsdlOperationMapping(m));
                }
            }
        } else {
            for (WSDLBoundOperation wsdlOp : wsdlModel.getBinding().getBindingOperations()) {
                QName name = wsdlOp.getRequestPayloadName();
                if (name == null)
                    name = EMPTY_PAYLOAD;
                methodHandlers.put(name, wsdlOperationMapping(wsdlOp));
            }
        }
    }

    /**
     *
     * @return not null if it finds a unique handler for the request
     *         null if it cannot idenitify a unique wsdl operation from the Payload QName.
     *
     * @throws DispatchException if the payload itself is incorrect, this happens when the payload is not accepted by
     *          any operation in the port.
     */
//  public QName getWSDLOperationQName(Packet request) throws DispatchException{

    public WSDLOperationMapping getWSDLOperationMapping(Packet request) throws DispatchException {
        Message message = request.getMessage();
        String localPart = message.getPayloadLocalPart();
        String nsUri;
        if (localPart == null) {
            localPart = EMPTY_PAYLOAD_LOCAL;
            nsUri = EMPTY_PAYLOAD_NSURI;
        } else {
            nsUri = message.getPayloadNamespaceURI();
            if(nsUri == null)
                nsUri = EMPTY_PAYLOAD_NSURI;
        }
        WSDLOperationMapping op = methodHandlers.get(nsUri, localPart);

        // Check if payload itself is correct. Usually it is, so let us check last
        if (op == null && !unique.containsKey(nsUri,localPart)) {
            String dispatchKey = "{" + nsUri + "}" + localPart;
            String faultString = ServerMessages.DISPATCH_CANNOT_FIND_METHOD(dispatchKey);
            throw new DispatchException(SOAPFaultBuilder.createSOAPFaultMessage(
                 binding.getSOAPVersion(), faultString, binding.getSOAPVersion().faultCodeClient));
        }
        return op;
    }
}
