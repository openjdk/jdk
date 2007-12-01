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
package com.sun.xml.internal.ws.handler;

import com.sun.xml.internal.ws.client.ServiceContext;
import com.sun.xml.internal.ws.util.HandlerAnnotationInfo;
import com.sun.xml.internal.ws.util.HandlerAnnotationProcessor;

import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.handler.PortInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Implementation class of HandlerResolver. This class is a simple
 * map of PortInfo objects to handler chains. It is used by a
 * {@link com.sun.xml.internal.ws.client.ServiceContext} object, and can
 * be replaced by user code with a different class implementing
 * HandlerResolver. This class is only used on the client side, and
 * it includes a lot of logging to help when there are issues since
 * it deals with port names, service names, and bindings. All three
 * must match when getting a handler chain from the map.
 *
 * <p>It is created by the {@link com.sun.xml.internal.ws.client.ServiceContextBuilder}
 * class and set on the ServiceContext. The ServiceContextBuilder uses
 * the {@link com.sun.xml.internal.ws.util.HandlerAnnotationProcessor} to create
 * a handler chain and then it sets the chains on this class and they
 * are put into the map. The ServiceContext uses the map to set handler
 * chains on bindings when they are created.
 *
 * @see com.sun.xml.internal.ws.client.ServiceContext
 * @see com.sun.xml.internal.ws.handler.PortInfoImpl
 *
 * @author WS Development Team
 */
public class HandlerResolverImpl implements HandlerResolver {
    private HandlerChainsModel handlerModel;
    private Map<PortInfo, List<Handler>> chainMap;
    private ServiceContext serviceContext;
    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".handler");

    public HandlerResolverImpl(ServiceContext serviceContext) {
        this.serviceContext = serviceContext;
        handlerModel = HandlerAnnotationProcessor.buildHandlerChainsModel(serviceContext.getServiceClass());
        chainMap = new HashMap<PortInfo, List<Handler>>();
    }

    /**
     * API method to return the correct handler chain for a given
     * PortInfo class.
     *
     * @param info A PortInfo object.
     * @return A list of handler objects. If there is no handler chain
     * found, it will return an empty list rather than null.
     */
    public List<Handler> getHandlerChain(PortInfo info) {
        //Check in cache first
        List<Handler> chain = chainMap.get(info);

        if(chain != null)
            return chain;
        if(handlerModel != null) {
            HandlerAnnotationInfo chainInfo = handlerModel.getHandlersForPortInfo(info);
            if(chainInfo != null) {
                chain = chainInfo.getHandlers();
                serviceContext.setRoles(info.getPortName(),chainInfo.getRoles());
            }
        }
        if (chain == null) {
            if (logger.isLoggable(Level.FINE)) {
                logGetChain(info);
            }
            chain = new ArrayList<Handler>();
        }
        // Put it in cache
        chainMap.put(info,chain);
        return chain;
    }

    // logged at fine level
    private void logGetChain(PortInfo info) {
        logger.fine("No handler chain found for port info:");
        logPortInfo(info, Level.FINE);
        logger.fine("Existing handler chains:");
        if (chainMap.isEmpty()) {
            logger.fine("none");
        } else {
            for (PortInfo key : chainMap.keySet()) {
                logger.fine(chainMap.get(key).size() +
                    " handlers for port info ");
                logPortInfo(key, Level.FINE);
            }
        }
    }

    private void logPortInfo(PortInfo info, Level level) {
        logger.log(level, "binding: " + info.getBindingID() +
            "\nservice: " + info.getServiceName() +
            "\nport: " + info.getPortName());
    }
}
