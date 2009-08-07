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

package com.sun.xml.internal.ws.client.sei;

import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.client.RequestContext;
import com.sun.xml.internal.ws.client.ResponseContextReceiver;
import com.sun.xml.internal.ws.encoding.soap.DeserializationException;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;
import com.sun.xml.internal.ws.message.jaxb.JAXBMessage;
import com.sun.xml.internal.ws.model.CheckedExceptionImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.model.ParameterImpl;
import com.sun.xml.internal.ws.model.WrapperParameter;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.Holder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link MethodHandler} that handles synchronous method invocations.
 *
 * <p>
 * This class mainly performs the following two tasks:
 * <ol>
 *  <li>Accepts Object[] that represents arguments for a Java method,
 *      and creates {@link JAXBMessage} that represents a request message.
 *  <li>Takes a {@link Message] that represents a response,
 *      and extracts the return value (and updates {@link Holder}s.)
 * </ol>
 *
 * <h2>Creating {@link JAXBMessage}</h2>
 * <p>
 * At the construction time, we prepare {@link BodyBuilder} and {@link MessageFiller}s
 * that know how to move arguments into a {@link Message}.
 * Some arguments go to the payload, some go to headers, still others go to attachments.
 *
 * @author Kohsuke Kawaguchi
 */
final class SyncMethodHandler extends SEIMethodHandler {
    private final ResponseBuilder responseBuilder;

    SyncMethodHandler(SEIStub owner, JavaMethodImpl method) {
        super(owner, method);
        responseBuilder = buildResponseBuilder(method, ValueSetterFactory.SYNC);
    }

    Object invoke(Object proxy, Object[] args) throws Throwable {
        return invoke(proxy,args,owner.requestContext,owner);
    }

    /**
     * Invokes synchronously, but with the given {@link RequestContext}
     * and {@link ResponseContextReceiver}.
     *
     * @param rc
     *      This {@link RequestContext} is used for invoking this method.
     *      We take this as a separate parameter because of the async invocation
     *      handling, which requires a separate copy.
     */
    Object invoke(Object proxy, Object[] args, RequestContext rc, ResponseContextReceiver receiver) throws Throwable {
        Packet req = new Packet(createRequestMessage(args));

        req.soapAction = soapAction;
        req.expectReply = !isOneWay;
        req.getMessage().assertOneWay(isOneWay);

        // process the message
        Packet reply = owner.doProcess(req,rc,receiver);

        Message msg = reply.getMessage();
        if(msg ==null)
            // no reply. must have been one-way
            return null;

        try {
            if(msg.isFault()) {
                SOAPFaultBuilder faultBuilder = SOAPFaultBuilder.create(msg);
                throw faultBuilder.createException(checkedExceptions);
            } else {
                return responseBuilder.readResponse(msg,args);
            }
        } catch (JAXBException e) {
            throw new DeserializationException("failed.to.read.response",e);
        } catch (XMLStreamException e) {
            throw new DeserializationException("failed.to.read.response",e);
        }
    }

    ValueGetterFactory getValueGetterFactory() {
        return ValueGetterFactory.SYNC;
    }

}
