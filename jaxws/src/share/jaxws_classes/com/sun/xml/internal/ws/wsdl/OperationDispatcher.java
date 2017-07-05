/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.WSDLOperationMapping;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.ArrayList;
import java.text.MessageFormat;

/**
 * This class abstracts the process of identifying the wsdl operation from a SOAP Message request.
 * This is primarily for dispatching the request messages to an endpoint method.
 *
 * Different implementations of {@link WSDLOperationFinder} are used underneath to identify the wsdl operation based on
 * if AddressingFeature is enabled or not.
 *
 * @author Rama Pulavarthi
 */
public class OperationDispatcher {
    private List<WSDLOperationFinder> opFinders;
    private WSBinding binding;

    public OperationDispatcher(@NotNull WSDLPort wsdlModel, @NotNull WSBinding binding, @Nullable SEIModel seiModel) {
        this.binding = binding;
        opFinders = new ArrayList<WSDLOperationFinder>();
        if (binding.getAddressingVersion() != null) {
            opFinders.add(new ActionBasedOperationFinder(wsdlModel, binding, seiModel));
        }
        opFinders.add(new PayloadQNameBasedOperationFinder(wsdlModel, binding, seiModel));
        opFinders.add(new SOAPActionBasedOperationFinder(wsdlModel, binding, seiModel));

    }

    /**
     * @deprecated use getWSDLOperationMapping(Packet request)
     * @param request Packet
     * @return QName of the wsdl operation.
     * @throws DispatchException if a unique operartion cannot be associated with this packet.
     */
    public @NotNull QName getWSDLOperationQName(Packet request) throws DispatchException {
        WSDLOperationMapping m = getWSDLOperationMapping(request);
        return m != null ? m.getOperationName() : null;
    }

    public @NotNull WSDLOperationMapping getWSDLOperationMapping(Packet request) throws DispatchException {
        WSDLOperationMapping opName;
        for(WSDLOperationFinder finder: opFinders) {
            opName = finder.getWSDLOperationMapping(request);
            if(opName != null)
                return opName;
        }
        //No way to dispatch this request
        String err = MessageFormat.format("Request=[SOAPAction={0},Payload='{'{1}'}'{2}]",
                request.soapAction, request.getMessage().getPayloadNamespaceURI(),
                request.getMessage().getPayloadLocalPart());
        String faultString = ServerMessages.DISPATCH_CANNOT_FIND_METHOD(err);
        Message faultMsg = SOAPFaultBuilder.createSOAPFaultMessage(
                binding.getSOAPVersion(), faultString, binding.getSOAPVersion().faultCodeClient);
        throw new DispatchException(faultMsg);
    }
}
