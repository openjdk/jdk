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

/*
 * LogicalHandlerProcessor.java
 *
 * Created on February 8, 2006, 5:40 PM
 *
 */

package com.sun.xml.internal.ws.handler;

import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Messages;
import java.util.List;
import javax.xml.ws.ProtocolException;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.http.HTTPException;

/**
 * This is used only for XML/HTTP binding
 * @author WS Development Team
 */
final class XMLHandlerProcessor<C extends MessageUpdatableContext> extends HandlerProcessor<C> {

    /**
     * Creates a new instance of LogicalHandlerProcessor
     */
    public XMLHandlerProcessor(HandlerTube owner, WSBinding binding, List<? extends Handler> chain) {
        super(owner, binding, chain);
    }

    /*
     * TODO: This is valid only for XML/HTTP binding
     * Empty the XML message
     */
    final void insertFaultMessage(C context,
            ProtocolException exception) {
        if(exception instanceof HTTPException) {
            context.put(MessageContext.HTTP_RESPONSE_CODE,((HTTPException)exception).getStatusCode());
        }
        if (context != null) {
            // non-soap case
            context.setPacketMessage(Messages.createEmpty(binding.getSOAPVersion()));
        }
    }
}
