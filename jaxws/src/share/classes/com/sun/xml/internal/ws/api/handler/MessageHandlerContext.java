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
package com.sun.xml.internal.ws.api.handler;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;

import javax.xml.ws.handler.MessageContext;
import java.util.Set;

/**
 * The <code>MessageHandlerContext</code> interface extends
 * <code>MessageContext</code> to provide easy access to the contained message.
 *
 * This context provides access to RI's <code>Message</code> model for efficient access
 * to various things like accessing headers etc. It also provides access to
 * binding information as <code>WSBinding</code>.
 *
 * @author Rama Pulavarthi
 * @since JAX-WS 2.1.3
 */
public interface MessageHandlerContext extends MessageContext {
    /**
     * Gets the message from this message context
     *
     * @return The contained message; returns <code>null</code> if no
     *         message is present in this message context
     */
    public Message getMessage();


    /**
     * Sets the message in this message context
     */
    public void setMessage(Message message);

    /**
     * @see javax.xml.ws.handler.soap.SOAPMessageContext#getRoles()
     */
     public Set<String> getRoles();


    /**
     * Provides access to <code>WSBinding</code> which can be used in various ways.
     * for example: <code>WSBinding#getSOAPVersion</code> to get SOAP version of the binding.
     *              <code>WSBinding#isFeatureEnabled(AddressingFeature)</code> to check if addressing is enabled
     */
    public WSBinding getWSBinding();

    /**
     * Provides access to <code>SEIModel</code>.
     */
    public @Nullable SEIModel getSEIModel();

    /**
     * Gets the {@link WSDLPort} that represents the port.
     * @return
     *      returns the WSDLModel of the port that the client/endpoint binds to.
     *      null when the Service is not configured with WSDL information.
     */
    public @Nullable WSDLPort getPort();

}
