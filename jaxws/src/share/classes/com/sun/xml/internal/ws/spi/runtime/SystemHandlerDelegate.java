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
package com.sun.xml.internal.ws.spi.runtime;


/**
 * A SystemHandlerDelegate is used to inject system level functionality into a
 * message processing runtime. The methods of this interface are invoked by
 * the client and enpoint message dispatchers of the message processing
 * runtime.
 *
 * @author WS Development Team
 */

public interface SystemHandlerDelegate {

   /**
    * Called by both client and endpoint message dispatchers to activate
    * injected request message processing.
    * When called by a client side message dispatcher, this method must be
    * called just before the message (associated with the MessageContext)
    * is sent. When called by the message dispatcher at an endpoint, this
    * method must be called before MustUnderstand processing on the
    * associated message.
    *
    * @param messageContext when called by a SOAPBinding the argument
    * must be an instanceof com.sun.xml.internal.ws.spi.runtime.SOAPMessageContext, and
    * when called by a SOAPBinding at an endpoint, the argument must
    * be an instanceof com.sun.xml.internal.ws.spi.runtime.SOAPMessageContext and the
    * Invoker (on the context) must be available for use on the server by the
    * delegate. An argument SOAPMessageContext passed to this method by an endpoint
    * dispatcher, must have values assigned for the following MessageContext
    * properties.
    * <ul>
    * <li>MessageContext.SERVLET_REQUEST
    * <li>MessageContext.SERVLET_RESPONSE
    * <li>MessageContext.SERVLET_SESSION
    * <li>MessageContext.SERVLET_CONTEXT
    * </ul>
    * @return true if processing by the delegate was such that the caller
    * should continue with its normal message processing. Returns false when
    * the delegate has established, in the MessageContext,
    * the response message to be sent. When this method returns
    * false, the calling message dispatcher must return the response message
    * without performing MustUnderstand processing and without invoking the
    * endpoint. Only delegates called by endpoint side message dispatchers
    * may return false
    *
    * @throws java.lang.Exception when the processing by the delegate failed
    * without yielding a response message; in which case, the caller shall
    * determine how to process the error.
    *
    */
    public boolean processRequest(MessageContext messageContext) throws Exception;

   /**
    * Called by both client and endpoint message dispatchers to activate
    * injected response message processing.
    * When called by the message dispatcher at the client, this method must be
    * called before MustUnderstand processing on the received message
    * (associated with the MessageContext). When called by the message
    * dispatcher at an endpoint, this method must be called after the
    * endpoint has been invoked, and just before the associated response
    * message is sent. In the special case where invocation of the endpoint
    * caused an Exception to be thrown, this method must not be called.
    *
    * @param messageContext when called by a SOAPBinding the argument
    * must be an instanceof com.sun.xml.internal.ws.spi.runtime.SOAPMessageContext.
    *
    * @throws java.lang.Exception when the processing by the delegate failed.
    * In this case, the caller must not send the response message but shall
    * otherwise determine how to process the error.
    */
    public void processResponse(MessageContext messageContext) throws Exception;

   /**
    * This method must be called by an endpoint message dispatcher after
    * MustUnderstand processing and before endpoint invocation.
    *
    * @param messageContext when called by a SOAPBinding the argument
    * must be an instanceof com.sun.xml.internal.ws.spi.runtime.SOAPMessageContext, and
    * must have values assigned for the following MessageContext
    * properties.
    * <ul>
    * <li>MessageContext.SERVLET_REQUEST
    * <li>MessageContext.SERVLET_RESPONSE
    * <li>MessageContext.SERVLET_SESSION
    * <li>MessageContext.SERVLET_CONTEXT
    * </ul>
     */
    public void preInvokeEndpointHook(MessageContext messageContext);
}
