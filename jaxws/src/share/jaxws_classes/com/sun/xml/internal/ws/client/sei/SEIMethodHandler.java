/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.client.sei;

import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.model.CheckedExceptionImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.model.ParameterImpl;
import com.sun.xml.internal.ws.model.WrapperParameter;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link com.sun.xml.internal.ws.client.sei.MethodHandler} that handles synchronous method invocations.
 *
 * <p>
 * This class mainly performs the following two tasks:
 * <ol>
 *  <li>Accepts Object[] that represents arguments for a Java method,
 *      and creates {@link com.sun.xml.internal.ws.message.jaxb.JAXBMessage} that represents a request message.
 *  <li>Takes a {@link com.sun.xml.internal.ws.api.message.Message] that represents a response,
 *      and extracts the return value (and updates {@link javax.xml.ws.Holder }s.)
 * </ol>
 *
 * <h2>Creating {@link com.sun.xml.internal.ws.message.jaxb.JAXBMessage }</h2>
 * <p>
 * At the construction time, we prepare {@link com.sun.xml.internal.ws.client.sei.BodyBuilder} and {@link com.sun.xml.internal.ws.client.sei.MessageFiller}s
 * that know how to move arguments into a {@link com.sun.xml.internal.ws.api.message.Message }.
 * Some arguments go to the payload, some go to headers, still others go to attachments.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
abstract class SEIMethodHandler extends MethodHandler {

    // these objects together create a message from method parameters
    private BodyBuilder bodyBuilder;
    private MessageFiller[] inFillers;

    protected String soapAction;

    protected boolean isOneWay;

    protected JavaMethodImpl javaMethod;

    protected Map<QName, CheckedExceptionImpl> checkedExceptions;

    SEIMethodHandler(SEIStub owner) {
        super(owner, null);
    }

    SEIMethodHandler(SEIStub owner, JavaMethodImpl method) {
        super(owner, null);

        //keep all the CheckedException model for the detail qname
        this.checkedExceptions = new HashMap<QName, CheckedExceptionImpl>();
        for(CheckedExceptionImpl ce : method.getCheckedExceptions()){
            checkedExceptions.put(ce.getBond().getTypeInfo().tagName, ce);
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

            BodyBuilder tmpBodyBuilder = null;
            List<MessageFiller> fillers = new ArrayList<MessageFiller>();

            for (ParameterImpl param : rp) {
                ValueGetter getter = getValueGetterFactory().get(param);

                switch(param.getInBinding().kind) {
                case BODY:
                    if(param.isWrapperStyle()) {
                        if(param.getParent().getBinding().isRpcLit())
                            tmpBodyBuilder = new BodyBuilder.RpcLit((WrapperParameter)param, owner.soapVersion, getValueGetterFactory());
                        else
                            tmpBodyBuilder = new BodyBuilder.DocLit((WrapperParameter)param, owner.soapVersion, getValueGetterFactory());
                    } else {
                        tmpBodyBuilder = new BodyBuilder.Bare(param, owner.soapVersion, getter);
                    }
                    break;
                case HEADER:
                    fillers.add(new MessageFiller.Header(
                        param.getIndex(),
                        param.getXMLBridge(),
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

            if(tmpBodyBuilder==null) {
                // no parameter binds to body. we create an empty message
                switch(owner.soapVersion) {
                case SOAP_11:
                    tmpBodyBuilder = BodyBuilder.EMPTY_SOAP11;
                    break;
                case SOAP_12:
                    tmpBodyBuilder = BodyBuilder.EMPTY_SOAP12;
                    break;
                default:
                    throw new AssertionError();
                }
            }

            this.bodyBuilder = tmpBodyBuilder;
            this.inFillers = fillers.toArray(new MessageFiller[fillers.size()]);
        }

        this.isOneWay = method.getMEP().isOneWay();
    }

    ResponseBuilder buildResponseBuilder(JavaMethodImpl method, ValueSetterFactory setterFactory) {
        // prepare objects for processing response
        List<ParameterImpl> rp = method.getResponseParameters();
        List<ResponseBuilder> builders = new ArrayList<ResponseBuilder>();

        for( ParameterImpl param : rp ) {
            ValueSetter setter;
            switch(param.getOutBinding().kind) {
            case BODY:
                if(param.isWrapperStyle()) {
                    if(param.getParent().getBinding().isRpcLit())
                        builders.add(new ResponseBuilder.RpcLit((WrapperParameter)param, setterFactory));
                    else
                        builders.add(new ResponseBuilder.DocLit((WrapperParameter)param, setterFactory));
                } else {
                    setter = setterFactory.get(param);
                    builders.add(new ResponseBuilder.Body(param.getXMLBridge(),setter));
                }
                break;
            case HEADER:
                setter = setterFactory.get(param);
                builders.add(new ResponseBuilder.Header(owner.soapVersion, param, setter));
                break;
            case ATTACHMENT:
                setter = setterFactory.get(param);
                builders.add(ResponseBuilder.AttachmentBuilder.createAttachmentBuilder(param, setter));
                break;
            case UNBOUND:
                setter = setterFactory.get(param);
                builders.add(new ResponseBuilder.NullSetter(setter,
                    ResponseBuilder.getVMUninitializedValue(param.getTypeInfo().type)));
                break;
            default:
                throw new AssertionError();
            }
        }
        ResponseBuilder rb;
        switch(builders.size()) {
        case 0:
            rb = ResponseBuilder.NONE;
            break;
        case 1:
            rb = builders.get(0);
            break;
        default:
            rb = new ResponseBuilder.Composite(builders);
        }
        return rb;
    }


    /**
     * Creates a request {@link com.sun.xml.internal.ws.message.jaxb.JAXBMessage} from method arguments.
     * @param args proxy invocation arguments
     * @return Message for the arguments
     */
    Message createRequestMessage(Object[] args) {
        Message msg = bodyBuilder.createMessage(args);

        for (MessageFiller filler : inFillers)
            filler.fillIn(args,msg);

        return msg;
    }

    abstract ValueGetterFactory getValueGetterFactory();

}
