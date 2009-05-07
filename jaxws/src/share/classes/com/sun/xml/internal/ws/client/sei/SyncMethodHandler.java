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
final class SyncMethodHandler extends MethodHandler {

    // these objects together create a message from method parameters
    private final BodyBuilder bodyBuilder;
    private final MessageFiller[] inFillers;

    private final String soapAction;

    private final boolean isOneWay;

    private final JavaMethodImpl javaMethod;

    /**
     * Used to get a value from method invocation parameter.
     *
     * valueGetters[i] is for methodArgs[i], and so on.
     */
    /*package*/ final ValueGetter[] valueGetters;

    private final ResponseBuilder responseBuilder;
    private final Map<QName, CheckedExceptionImpl> checkedExceptions;

    public SyncMethodHandler(SEIStub owner, JavaMethodImpl method) {
        super(owner);

        //keep all the CheckedException model for the detail qname
        this.checkedExceptions = new HashMap<QName, CheckedExceptionImpl>();
        for(CheckedExceptionImpl ce : method.getCheckedExceptions()){
            checkedExceptions.put(ce.getBridge().getTypeReference().tagName, ce);
        }
        //If a non-"" soapAction is specified, wsa:action the SOAPAction
        if(method.getInputAction() != null && !method.getBinding().getSOAPAction().equals("") ) {
            this.soapAction = method.getInputAction();
        } else {
            this.soapAction = method.getBinding().getSOAPAction();
        }
        this.javaMethod = method;

        {// prepare objects for creating messages
            List<ParameterImpl> rp = method.getRequestParameters();

            BodyBuilder bodyBuilder = null;
            List<MessageFiller> fillers = new ArrayList<MessageFiller>();
            valueGetters = new ValueGetter[rp.size()];

            for (ParameterImpl param : rp) {
                ValueGetter getter = ValueGetter.get(param);

                switch(param.getInBinding().kind) {
                case BODY:
                    if(param.isWrapperStyle()) {
                        if(param.getParent().getBinding().isRpcLit())
                            bodyBuilder = new BodyBuilder.RpcLit((WrapperParameter)param, owner.soapVersion);
                        else
                            bodyBuilder = new BodyBuilder.DocLit((WrapperParameter)param, owner.soapVersion);
                    } else {
                        bodyBuilder = new BodyBuilder.Bare(param, owner.soapVersion);
                    }
                    break;
                case HEADER:
                    fillers.add(new MessageFiller.Header(
                        param.getIndex(),
                        param.getBridge(),
                        getter ));
                    break;
                case ATTACHMENT:
                    fillers.add(MessageFiller.AttachmentFiller.createAttachmentFiller(param, getter));
                    break;
                case UNBOUND:
                    break;
                default:
                    throw new AssertionError(); // impossible
                }
            }

            if(bodyBuilder==null) {
                // no parameter binds to body. we create an empty message
                switch(owner.soapVersion) {
                case SOAP_11:
                    bodyBuilder = BodyBuilder.EMPTY_SOAP11;
                    break;
                case SOAP_12:
                    bodyBuilder = BodyBuilder.EMPTY_SOAP12;
                    break;
                default:
                    throw new AssertionError();
                }
            }

            this.bodyBuilder = bodyBuilder;
            this.inFillers = fillers.toArray(new MessageFiller[fillers.size()]);
        }

        {// prepare objects for processing response
            List<ParameterImpl> rp = method.getResponseParameters();
            List<ResponseBuilder> builders = new ArrayList<ResponseBuilder>();

            for( ParameterImpl param : rp ) {
                ValueSetter setter = ValueSetter.get(param);
                switch(param.getOutBinding().kind) {
                case BODY:
                    if(param.isWrapperStyle()) {
                        if(param.getParent().getBinding().isRpcLit())
                            builders.add(new ResponseBuilder.RpcLit((WrapperParameter)param));
                        else
                            builders.add(new ResponseBuilder.DocLit((WrapperParameter)param));
                    } else {
                        builders.add(new ResponseBuilder.Body(param.getBridge(),setter));
                    }
                    break;
                case HEADER:
                    builders.add(new ResponseBuilder.Header(owner.soapVersion, param, setter));
                    break;
                case ATTACHMENT:
                    builders.add(ResponseBuilder.AttachmentBuilder.createAttachmentBuilder(param, setter));
                    break;
                case UNBOUND:
                    builders.add(new ResponseBuilder.NullSetter(setter,
                        ResponseBuilder.getVMUninitializedValue(param.getTypeReference().type)));
                    break;
                default:
                    throw new AssertionError();
                }
            }

            switch(builders.size()) {
            case 0:
                responseBuilder = ResponseBuilder.NONE;
                break;
            case 1:
                responseBuilder = builders.get(0);
                break;
            default:
                responseBuilder = new ResponseBuilder.Composite(builders);
            }
        }
        this.isOneWay = method.getMEP().isOneWay();
    }

    public JavaMethodImpl getJavaMethod() {
        return javaMethod;
    }

    public Object invoke(Object proxy, Object[] args) throws Throwable {
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
    public Object invoke(Object proxy, Object[] args, RequestContext rc, ResponseContextReceiver receiver) throws Throwable {
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

    /**
     * Creates a request {@link JAXBMessage} from method arguments.
     *
     */
    private Message createRequestMessage(Object[] args) {
        Message msg = bodyBuilder.createMessage(args);

        for (MessageFiller filler : inFillers)
            filler.fillIn(args,msg);

        return msg;
    }
}
