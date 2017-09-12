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

package com.sun.xml.internal.ws.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.handler.HandlerChainsModel;
import com.sun.xml.internal.ws.util.HandlerAnnotationInfo;
import com.sun.xml.internal.ws.util.HandlerAnnotationProcessor;

import javax.jws.HandlerChain;
import javax.xml.ws.Service;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.handler.PortInfo;
import javax.xml.ws.soap.SOAPBinding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used by {@link WSServiceDelegate} to configure {@link BindingImpl}
 * with handlers. The two mechanisms encapsulated by this abstraction
 * is {@link HandlerChain} annotaion and {@link HandlerResolver}
 * interface.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class HandlerConfigurator {
    /**
     * Configures the given {@link BindingImpl} object by adding handlers to it.
     */
    abstract void configureHandlers(@NotNull WSPortInfo port, @NotNull BindingImpl binding);

    /**
     * Returns a {@link HandlerResolver}, if this object encapsulates any {@link HandlerResolver}.
     * Otherwise null.
     */
    abstract HandlerResolver getResolver();


    /**
     * Configures handlers by calling {@link HandlerResolver}.
     * <p>
     * When a null {@link HandlerResolver} is set by the user to
     * {@link Service#setHandlerResolver(HandlerResolver)}, we'll use this object
     * with null {@link #resolver}.
     */
    static final class HandlerResolverImpl extends HandlerConfigurator {
        private final @Nullable HandlerResolver resolver;

        public HandlerResolverImpl(HandlerResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        void configureHandlers(@NotNull WSPortInfo port, @NotNull BindingImpl binding) {
            if (resolver!=null) {
                binding.setHandlerChain(resolver.getHandlerChain(port));
            }
        }


        @Override
        HandlerResolver getResolver() {
            return resolver;
        }
    }

    /**
     * Configures handlers from {@link HandlerChain} annotation.
     *
     * <p>
     * This class is a simple
     * map of PortInfo objects to handler chains. It is used by a
     * {@link WSServiceDelegate} object, and can
     * be replaced by user code with a different class implementing
     * HandlerResolver. This class is only used on the client side, and
     * it includes a lot of logging to help when there are issues since
     * it deals with port names, service names, and bindings. All three
     * must match when getting a handler chain from the map.
     *
     * <p>It is created by the {@link WSServiceDelegate}
     * class , which uses {@link HandlerAnnotationProcessor} to create
     * a handler chain and then it sets the chains on this class and they
     * are put into the map. The ServiceContext uses the map to set handler
     * chains on bindings when they are created.
     */
    static final class AnnotationConfigurator extends HandlerConfigurator {
        private final HandlerChainsModel handlerModel;
        private final Map<WSPortInfo,HandlerAnnotationInfo> chainMap = new HashMap<WSPortInfo,HandlerAnnotationInfo>();
        private static final Logger logger = Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".handler");

        AnnotationConfigurator(WSServiceDelegate delegate) {
            handlerModel = HandlerAnnotationProcessor.buildHandlerChainsModel(delegate.getServiceClass());
            assert handlerModel!=null; // this class is suppeod to be called only when there's @HandlerCHain
        }


        void configureHandlers(WSPortInfo port, BindingImpl binding) {
            //Check in cache first
            HandlerAnnotationInfo chain = chainMap.get(port);

            if(chain==null) {
                logGetChain(port);
                // Put it in cache
                chain = handlerModel.getHandlersForPortInfo(port);
                chainMap.put(port,chain);
            }

            if (binding instanceof SOAPBinding) {
                ((SOAPBinding) binding).setRoles(chain.getRoles());
            }

            logSetChain(port,chain);
            binding.setHandlerChain(chain.getHandlers());
        }

        HandlerResolver getResolver() {
            return new HandlerResolver() {
                public List<Handler> getHandlerChain(PortInfo portInfo) {
                    return new ArrayList<Handler>(
                        handlerModel.getHandlersForPortInfo(portInfo).getHandlers());
                }
            };
        }
        // logged at finer level
        private void logSetChain(WSPortInfo info, HandlerAnnotationInfo chain) {
            logger.finer("Setting chain of length " + chain.getHandlers().size() +
                " for port info");
            logPortInfo(info, Level.FINER);
        }

        // logged at fine level
        private void logGetChain(WSPortInfo info) {
            logger.fine("No handler chain found for port info:");
            logPortInfo(info, Level.FINE);
            logger.fine("Existing handler chains:");
            if (chainMap.isEmpty()) {
                logger.fine("none");
            } else {
                for (WSPortInfo key : chainMap.keySet()) {
                    logger.fine(chainMap.get(key).getHandlers().size() +
                        " handlers for port info ");
                    logPortInfo(key, Level.FINE);
                }
            }
        }

        private void logPortInfo(WSPortInfo info, Level level) {
            logger.log(level, "binding: " + info.getBindingID() +
                "\nservice: " + info.getServiceName() +
                "\nport: " + info.getPortName());
        }
    }
}
