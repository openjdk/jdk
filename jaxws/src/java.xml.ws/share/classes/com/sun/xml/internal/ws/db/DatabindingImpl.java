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

package com.sun.xml.internal.ws.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.WebServiceFeature;

import com.oracle.webservices.internal.api.databinding.JavaCallInfo;
import com.oracle.webservices.internal.api.message.MessageContext;
import com.sun.xml.internal.ws.api.databinding.EndpointCallBridge;
import com.sun.xml.internal.ws.api.databinding.WSDLGenInfo;
import com.sun.xml.internal.ws.api.databinding.Databinding;
import com.sun.xml.internal.ws.api.databinding.DatabindingConfig;
import com.sun.xml.internal.ws.api.databinding.ClientCallBridge;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.MessageContextFactory;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.MEP;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.WSDLOperationMapping;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.sei.StubAsyncHandler;
import com.sun.xml.internal.ws.client.sei.StubHandler;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.model.RuntimeModeler;
import com.sun.xml.internal.ws.server.sei.TieHandler;
import com.sun.xml.internal.ws.wsdl.ActionBasedOperationSignature;
import com.sun.xml.internal.ws.wsdl.DispatchException;
import com.sun.xml.internal.ws.wsdl.OperationDispatcher;

/**
 * WsRuntimeImpl is the databinding processor built on SEIModel
 *
 * @author shih-chang.chen@oracle.com
 */
public final class DatabindingImpl implements Databinding {

    AbstractSEIModelImpl seiModel;
    Map<Method, StubHandler> stubHandlers;
//    QNameMap<TieHandler> wsdlOpMap = new QNameMap<TieHandler>();
    Map<JavaMethodImpl, TieHandler> wsdlOpMap = new HashMap<JavaMethodImpl, TieHandler>();
    Map<Method, TieHandler> tieHandlers = new HashMap<Method, TieHandler>();
    OperationDispatcher operationDispatcher;
    OperationDispatcher operationDispatcherNoWsdl;
    boolean clientConfig = false;
    Codec codec;
    MessageContextFactory packetFactory = null;

    public DatabindingImpl(DatabindingProviderImpl p, DatabindingConfig config) {
        RuntimeModeler modeler = new RuntimeModeler(config);
        modeler.setClassLoader(config.getClassLoader());
        seiModel = modeler.buildRuntimeModel();
        WSDLPort wsdlport = config.getWsdlPort();
        Object facProp = config.properties().get("com.sun.xml.internal.ws.api.message.MessageContextFactory");
        packetFactory = (facProp != null && facProp instanceof MessageContextFactory)? (MessageContextFactory)facProp :
                        new MessageContextFactory(seiModel.getWSBinding().getFeatures());
        clientConfig = isClientConfig(config);
        if (clientConfig) {
            initStubHandlers();
        }
        seiModel.setDatabinding(this);
        if (wsdlport != null) {
            freeze(wsdlport);
        }
        if (operationDispatcher == null) {
            operationDispatcherNoWsdl = new OperationDispatcher(null, seiModel.getWSBinding(), seiModel);
        }
//    if(!clientConfig) {
        for (JavaMethodImpl jm : seiModel.getJavaMethods()) {
            if (!jm.isAsync()) {
                TieHandler th = new TieHandler(jm, seiModel.getWSBinding(), packetFactory);
                wsdlOpMap.put(jm, th);
                tieHandlers.put(th.getMethod(), th);
            }
        }
//    }
    }

    //TODO isClientConfig
    private boolean isClientConfig(DatabindingConfig config) {
        if (config.getContractClass() == null) {
            return false;
        }
        if (!config.getContractClass().isInterface()) {
            return false;
        }
        return (config.getEndpointClass() == null || config.getEndpointClass().isInterface());
    }
    //TODO fix freeze

    public void freeze(WSDLPort port) {
        if (clientConfig) {
            return;
        }
        synchronized(this) {
            if (operationDispatcher == null) {
                operationDispatcher = (port == null) ? null : new OperationDispatcher(port, seiModel.getWSBinding(), seiModel);
            }
        }
    }

    public SEIModel getModel() {
        return seiModel;
    }
//Refactored from SEIStub

    private void initStubHandlers() {
        stubHandlers = new HashMap<Method, StubHandler>();
        Map<ActionBasedOperationSignature, JavaMethodImpl> syncs = new HashMap<ActionBasedOperationSignature, JavaMethodImpl>();
        // fill in methodHandlers.
        // first fill in sychronized versions
        for (JavaMethodImpl m : seiModel.getJavaMethods()) {
            if (!m.getMEP().isAsync) {
                StubHandler handler = new StubHandler(m, packetFactory);
                syncs.put(m.getOperationSignature(), m);
                stubHandlers.put(m.getMethod(), handler);
            }
        }
        for (JavaMethodImpl jm : seiModel.getJavaMethods()) {
            JavaMethodImpl sync = syncs.get(jm.getOperationSignature());
            if (jm.getMEP() == MEP.ASYNC_CALLBACK || jm.getMEP() == MEP.ASYNC_POLL) {
                Method m = jm.getMethod();
                StubAsyncHandler handler = new StubAsyncHandler(jm, sync, packetFactory);
                stubHandlers.put(m, handler);
            }
        }
    }

    JavaMethodImpl resolveJavaMethod(Packet req) throws DispatchException {
        WSDLOperationMapping m = req.getWSDLOperationMapping();
        if (m == null) {
            synchronized (this) {
                m = (operationDispatcher != null)
                        ? operationDispatcher.getWSDLOperationMapping(req)
                        : operationDispatcherNoWsdl.getWSDLOperationMapping(req);
            }
        }
        return (JavaMethodImpl) m.getJavaMethod();
    }

    public JavaCallInfo deserializeRequest(Packet req) {
        com.sun.xml.internal.ws.api.databinding.JavaCallInfo call = new com.sun.xml.internal.ws.api.databinding.JavaCallInfo();
        try {
            JavaMethodImpl wsdlOp = resolveJavaMethod(req);
            TieHandler tie = wsdlOpMap.get(wsdlOp);
            call.setMethod(tie.getMethod());
            Object[] args = tie.readRequest(req.getMessage());
            call.setParameters(args);
        } catch (DispatchException e) {
            call.setException(e);
        }
        return call;
    }

    public JavaCallInfo deserializeResponse(Packet res, JavaCallInfo call) {
        StubHandler stubHandler = stubHandlers.get(call.getMethod());
        try {
            return stubHandler.readResponse(res, call);
        } catch (Throwable e) {
            call.setException(e);
            return call;
        }
    }

    public WebServiceFeature[] getFeatures() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Packet serializeRequest(JavaCallInfo call) {
        StubHandler stubHandler = stubHandlers.get(call.getMethod());
        Packet p = stubHandler.createRequestPacket(call);
        p.setState(Packet.State.ClientRequest);
        return p;
    }

    @Override
    public Packet serializeResponse(JavaCallInfo call) {
        Method method = call.getMethod();
        Message message = null;
        if (method != null) {
            TieHandler th = tieHandlers.get(method);
            if (th != null) {
                return th.serializeResponse(call);
            }
        }
        if (call.getException() instanceof DispatchException) {
            message = ((DispatchException) call.getException()).fault;
        }
        Packet p = (Packet) packetFactory.createContext(message);
        p.setState(Packet.State.ServerResponse);
        return p;
    }

    @Override
    public ClientCallBridge getClientBridge(Method method) {
        return stubHandlers.get(method);
    }

    @Override
    public void generateWSDL(WSDLGenInfo info) {
        com.sun.xml.internal.ws.wsdl.writer.WSDLGenerator wsdlGen = new com.sun.xml.internal.ws.wsdl.writer.WSDLGenerator(
                seiModel,
                info.getWsdlResolver(),
                seiModel.getWSBinding(),
                info.getContainer(), seiModel.getEndpointClass(),
                info.isInlineSchemas(),
                info.isSecureXmlProcessingDisabled(),
                info.getExtensions());
        wsdlGen.doGeneration();
    }

    @Override
    public EndpointCallBridge getEndpointBridge(Packet req) throws DispatchException {
        JavaMethodImpl wsdlOp = resolveJavaMethod(req);
        return wsdlOpMap.get(wsdlOp);
    }

    Codec getCodec() {
        if (codec == null) {
            codec = ((BindingImpl) seiModel.getWSBinding()).createCodec();
        }
        return codec;
    }

    @Override
    public ContentType encode(Packet packet, OutputStream out) throws IOException {
        return getCodec().encode(packet, out);
    }

    @Override
    public void decode(InputStream in, String ct, Packet p) throws IOException {
        getCodec().decode(in, ct, p);
    }

    @Override
    public com.oracle.webservices.internal.api.databinding.JavaCallInfo createJavaCallInfo(Method method, Object[] args) {
        return new com.sun.xml.internal.ws.api.databinding.JavaCallInfo(method, args);
    }

    @Override
    public com.oracle.webservices.internal.api.databinding.JavaCallInfo deserializeResponse(
            MessageContext message, com.oracle.webservices.internal.api.databinding.JavaCallInfo call) {
        return deserializeResponse((Packet) message, (JavaCallInfo) call);
    }

    @Override
    public com.oracle.webservices.internal.api.databinding.JavaCallInfo deserializeRequest(MessageContext message) {
        return deserializeRequest((Packet) message);
    }

    @Override
    public MessageContextFactory getMessageContextFactory() {
        return packetFactory;
    }
}
