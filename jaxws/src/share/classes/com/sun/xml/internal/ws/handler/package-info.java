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

/**
 * <h1>JAX-WS 2.0 Handler Runtime</h1>
 * <p>This document describes the architecture of the handler code
 * in the JAX-WS 2.0 runtime.
 *
 * <p>Handlers may be specified by the deployment descriptor on the
 * server side, or by a wsdl customization or Java annotation. In the
 * case of a wsdl customization, wsimport will create the Java interface
 * with the handler chain annotation and will create a handler xml file
 * to which the annotation points. At runtime, thus, only deployment
 * descriptors and handler files pointed to by annotations are parsed.
 * The schema in all cases is the same, and  @HandlerChainAnnotation is
 * processed by {@link com.sun.xml.internal.ws.util.HandlerAnnotationProcessor}, which
 * delegates the parsing of handler configuartion file to
 * {@link com.sun.xml.internal.ws.handler.HandlerChainsModel} .
 *
 * <h3>Server side handler creation</h3>
 *
 * <p>The deployment descriptor is first parsed, and
 * {@link com.sun.xml.internal.ws.transport.http.servlet.RuntimeEndpointInfoParser#setHandlersAndRoles}
 * parses the handler chains xml in the deployment descriptor if present.
 * It then sets the handlers and roles on the Binding object that has
 * already been created. Setting the handler chain on the binding does
 * not automatically create a handler chain caller.
 *
 * <p>Later, when
 * {@link com.sun.xml.internal.ws.server.RuntimeEndpointInfo#init} is parsing the
 * annotations it checks for handlers on the binding. If there are handlers
 * already, it skips any further handler processing. In this way, the deployment
 * descriptor overrides any other handlers. If there are no handlers on
 * the binding at this point, RuntimeEndpointInfo has the
 * HandlerAnnotationProcessor parse the handler chain file and then
 * sets the handlers and roles on the binding.
 *
 * <h3>Client side handler creation</h3>
 *
 * <p>On the client side, the @HandlerChain annotation on generated Service
 * class is processed by {@link com.sun.xml.internal.ws.client.ServiceContextBuilder#build}.
 * If there is @HandlerChain on service class, the ServiceContextBuilder creates
 * HandlerResolverImpl from that handler file and sets it on the
 * {@link com.sun.xml.internal.ws.client.ServiceContext}. @HandlerChain annotations on
 * the SEIs are ignored on the client-side.
 *
 * <p>Unlike the server side, there is no binding object already
 * created when the handlers are parsed. When a binding provider is created, the
 * {@link com.sun.xml.internal.ws.client.WSServiceDelegate} will use the handler
 * registry (which may be the HandlerResolverImpl or another handler
 * resolver set on the service by the user code) to get the handlers to
 * set on the binding. It will get the roles from the service context and
 * set those on the binding if it is a soap binding. The relevant method is
 * {@link com.sun.xml.internal.ws.client.WSServiceDelegate#setBindingOnProvider}
 *
 * <h3>Calling the handlers</h3>
 *
 * <p>During a request or response, a
 * {@link com.sun.xml.internal.ws.handler.HandlerChainCaller} is
 * created by the binding or may be created by a message dispatcher on the
 * server side (this happens in the http binding right now). In the binding
 * objects, the handler caller is not created until needed, and it sets
 * the handlers and roles (if present) on the handler chain caller then.
 * See {@link com.sun.xml.internal.ws.binding.BindingImpl#getHandlerChainCaller}
 * and {@link com.sun.xml.internal.ws.binding.soap.SOAPBindingImpl#getHandlerChainCaller}
 * for more details.
 *
 * <p>The handler chain caller does the handler invocation and controls the
 * flow of the handlers. For details of the code that calls the handler
 * chain caller, see
 * {@link com.sun.xml.internal.ws.protocol.soap.client.SOAPMessageDispatcher}
 * on the client side and
 * {@link com.sun.xml.internal.ws.protocol.soap.server.SOAPMessageDispatcher} and
 * {@link com.sun.xml.internal.ws.protocol.xml.server.XMLMessageDispatcher} on the
 * server side.
 *
 * @ArchitectureDocument
 */
package com.sun.xml.internal.ws.handler;
