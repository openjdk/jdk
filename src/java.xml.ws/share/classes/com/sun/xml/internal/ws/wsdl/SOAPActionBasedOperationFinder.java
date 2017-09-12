/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.WSDLOperationMapping;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

/**
 * An {@link WSDLOperationFinder} that uses SOAPAction as the key for dispatching.
 * <p/>
 * A map of all SOAPAction on the port and the corresponding WSDL operation QName
 * is initialized in the constructor. The SOAPAction from the
 * request {@link com.sun.xml.internal.ws.api.message.Packet} is used as the key to identify the associated wsdl operation.
 *
 * @author Jitendra Kotamraju
 */
final class SOAPActionBasedOperationFinder extends WSDLOperationFinder {
    private final Map<String, WSDLOperationMapping> methodHandlers;

    public SOAPActionBasedOperationFinder(WSDLPort wsdlModel, WSBinding binding, @Nullable SEIModel seiModel) {
        super(wsdlModel,binding,seiModel);
        methodHandlers = new HashMap<String, WSDLOperationMapping>();

        // Find if any SOAPAction repeat for operations
        Map<String, Integer> unique = new HashMap<String, Integer>();
        if (seiModel != null) {
            for (JavaMethodImpl m : ((AbstractSEIModelImpl) seiModel).getJavaMethods()) {
                String soapAction = m.getSOAPAction();
                Integer count = unique.get(soapAction);
                if (count == null) {
                    unique.put(soapAction, 1);
                } else {
                    unique.put(soapAction, ++count);
                }
            }

            for (JavaMethodImpl m : ((AbstractSEIModelImpl) seiModel).getJavaMethods()) {
                String soapAction = m.getSOAPAction();
                // Set up method handlers only for unique SOAPAction values so
                // that dispatching happens consistently for a method
                if (unique.get(soapAction) == 1) {
                    methodHandlers.put('"' + soapAction + '"', wsdlOperationMapping(m));
                }
            }
        } else {
            for(WSDLBoundOperation wsdlOp: wsdlModel.getBinding().getBindingOperations()) {
                methodHandlers.put(wsdlOp.getSOAPAction(), wsdlOperationMapping(wsdlOp));
            }
        }

    }

//  public QName getWSDLOperationQName(Packet request) {
    public WSDLOperationMapping getWSDLOperationMapping(Packet request) throws DispatchException {
        return request.soapAction == null ? null : methodHandlers.get(request.soapAction);
    }
}
