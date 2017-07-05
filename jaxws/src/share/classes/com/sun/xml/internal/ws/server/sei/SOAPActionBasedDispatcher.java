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
package com.sun.xml.internal.ws.server.sei;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

/**
 * An {@link EndpointMethodDispatcher} that uses SOAPAction as the key for dispatching.
 * <p/>
 * A map of all SOAPAction on the port and the corresponding {@link EndpointMethodHandler}
 * is initialized in the constructor. The SOAPAction from the
 * request {@link Packet} is used as the key to return the correct handler.
 *
 * @author Jitendra Kotamraju
 */
final class SOAPActionBasedDispatcher implements EndpointMethodDispatcher {
    private final Map<String, EndpointMethodHandler> methodHandlers;

    public SOAPActionBasedDispatcher(AbstractSEIModelImpl model, WSBinding binding, SEIInvokerTube invokerTube) {
        // Find if any SOAPAction repeat for operations
        Map<String, Integer> unique = new HashMap<String, Integer>();
        for(JavaMethodImpl m : model.getJavaMethods()) {
            String soapAction = m.getOperation().getSOAPAction();
            Integer count = unique.get(soapAction);
            if (count == null) {
                unique.put(soapAction, 1);
            } else {
                unique.put(soapAction, ++count);
            }
        }
        methodHandlers = new HashMap<String, EndpointMethodHandler>();
        for( JavaMethodImpl m : model.getJavaMethods() ) {
            String soapAction = m.getOperation().getSOAPAction();
            // Set up method handlers only for unique SOAPAction values so
            // that dispatching happens consistently for a method
            if (unique.get(soapAction) == 1) {
                methodHandlers.put('"'+soapAction+'"', new EndpointMethodHandler(invokerTube,m,binding));
            }
        }
    }

    public @Nullable EndpointMethodHandler getEndpointMethodHandler(Packet request) {
        return request.soapAction == null ? null : methodHandlers.get(request.soapAction);
    }

}
