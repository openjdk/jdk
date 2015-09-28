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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.client.dispatch.DataSourceDispatch;
import com.sun.xml.internal.ws.client.dispatch.DispatchImpl;
import com.sun.xml.internal.ws.client.dispatch.JAXBDispatch;
import com.sun.xml.internal.ws.client.dispatch.MessageDispatch;
import com.sun.xml.internal.ws.client.dispatch.PacketDispatch;
import com.sun.xml.internal.ws.client.sei.SEIStub;
import com.sun.xml.internal.ws.developer.WSBindingProvider;
import com.sun.xml.internal.ws.model.SOAPSEIModel;

import javax.activation.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.Service.Mode;
import javax.xml.ws.WebServiceException;
import java.lang.reflect.Proxy;

/**
 * Factory methods of various stubs.
 *
 * <p>
 * This class provides various methods to create "stub"s,
 * which are the component that turns a method invocation
 * into a {@link Message} and back into a return value.
 *
 * <p>
 * This class is meant to serve as the API from JAX-WS to
 * Tango, so that they don't have hard-code dependency on
 * our implementation classes.
 *
 * <a name="param"></a>
 * <h2>Common Parameters and Their Meanings</h2>
 *
 * <h3>Pipe next</h3>
 * <p>
 * Stubs turn a method invocation into a {@link Pipe#process(com.sun.xml.internal.ws.api.message.Packet)} invocation,
 * and this pipe passed in as the {@code next} parameter will receive a {@link Message}
 * from newly created stub. All the methods taking Tube <<next>> parameter are deprecated. JAX-WS Runtime takes care of
 * creating the tubeline when the {@code next} parameter is not passed. This gives flexibility for the JAX-WS Runtime
 * to pass extra information during the tube line creation via {@link ClientTubeAssemblerContext}.
 *
 * <h3>WSPortInfo portInfo</h3>
 * <p> Gives information about the port for which the "stub" being created. Such information includes Port QName,
 * target endpoint address, and bindingId etc.
 *
 * <h3>BindingImpl binding</h3>
 * <p>
 * Stubs implement {@link BindingProvider}, and its {@link BindingProvider#getBinding()}
 * will return this {@code binding} object. Stubs often also use this information
 * to decide which SOAP version a {@link Message} should be created in.
 *
 * <h3>{@link WSService} service</h3>
 * <p>
 * This object represents a {@link Service} that owns the newly created stub.
 * For example, asynchronous method invocation will use {@link Service#getExecutor()}.
 *
 * <h3>{@link WSEndpointReference} epr</h3>
 * <p>
 * If you want the created {@link Dispatch} to talk to the given EPR, specify the parameter.
 * Otherwise leave it {@code null}. Note that the addressing needs to be enabled separately
 * for this to take effect.
 *
 * @author Kohsuke Kawaguchi
 * @author Kathy Walsh
 */
public abstract class Stubs {
    private Stubs() {}   // no instanciation please

    /**
     * Creates a new {@link Dispatch} stub for {@link SOAPMessage}.
     *
     * This is short-cut of calling
     * <pre>
     * createDispatch(port,owner,binding,SOAPMessage.class,mode,next);
     * </pre>
     */
    @Deprecated
    public static Dispatch<SOAPMessage> createSAAJDispatch(QName portName, WSService owner, WSBinding binding, Service.Mode mode, Tube next, @Nullable WSEndpointReference epr) {
        DispatchImpl.checkValidSOAPMessageDispatch(binding, mode);
        return new com.sun.xml.internal.ws.client.dispatch.SOAPMessageDispatch(portName, mode, (WSServiceDelegate)owner, next, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new {@link Dispatch} stub for {@link SOAPMessage}.
     *
     * This is short-cut of calling
     * <pre>
     * createDispatch(port,owner,binding,SOAPMessage.class,mode,next);
     * </pre>
     */
    public static Dispatch<SOAPMessage> createSAAJDispatch(WSPortInfo portInfo, WSBinding binding, Service.Mode mode, @Nullable WSEndpointReference epr) {
        DispatchImpl.checkValidSOAPMessageDispatch(binding, mode);
        return new com.sun.xml.internal.ws.client.dispatch.SOAPMessageDispatch(portInfo, mode, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new {@link Dispatch} stub for {@link DataSource}.
     *
     * This is short-cut of calling
     * <pre>
     * createDispatch(port,owner,binding,DataSource.class,mode,next);
     * </pre>
     */
    @Deprecated
    public static Dispatch<DataSource> createDataSourceDispatch(QName portName, WSService owner, WSBinding binding, Service.Mode mode, Tube next, @Nullable WSEndpointReference epr) {
        DispatchImpl.checkValidDataSourceDispatch(binding, mode);
        return new DataSourceDispatch(portName, mode, (WSServiceDelegate)owner, next, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new {@link Dispatch} stub for {@link DataSource}.
     *
     * This is short-cut of calling
     * <pre>
     * createDispatch(port,owner,binding,DataSource.class,mode,next);
     * </pre>
     */
    public static Dispatch<DataSource> createDataSourceDispatch(WSPortInfo portInfo, WSBinding binding, Service.Mode mode,@Nullable WSEndpointReference epr) {
        DispatchImpl.checkValidDataSourceDispatch(binding, mode);
        return new DataSourceDispatch(portInfo, mode, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new {@link Dispatch} stub for {@link Source}.
     *
     * This is short-cut of calling
     * <pre>
     * createDispatch(port,owner,binding,Source.class,mode,next);
     * </pre>
     */
    @Deprecated
    public static Dispatch<Source> createSourceDispatch(QName portName, WSService owner, WSBinding binding, Service.Mode mode, Tube next, @Nullable WSEndpointReference epr) {
        return DispatchImpl.createSourceDispatch(portName, mode, (WSServiceDelegate)owner, next, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new {@link Dispatch} stub for {@link Source}.
     *
     * This is short-cut of calling
     * <pre>
     * createDispatch(port,owner,binding,Source.class,mode,next);
     * </pre>
     */
    public static Dispatch<Source> createSourceDispatch(WSPortInfo portInfo, WSBinding binding, Service.Mode mode, @Nullable WSEndpointReference epr) {
        return DispatchImpl.createSourceDispatch(portInfo, mode, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new {@link Dispatch} stub that connects to the given pipe.
     *
     * @param portName
     *      see {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param owner
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param clazz
     *      Type of the {@link Dispatch} to be created.
     *      See {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param mode
     *      The mode of the dispatch.
     *      See {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param next
     *      see <a href="#param">common parameters</a>
     * @param epr
     *      see <a href="#param">common parameters</a>
     * TODO: are these parameters making sense?
     */
    @SuppressWarnings("unchecked")
        public static <T> Dispatch<T> createDispatch(QName portName,
                                                 WSService owner,
                                                 WSBinding binding,
                                                 Class<T> clazz, Service.Mode mode, Tube next,
                                                 @Nullable WSEndpointReference epr) {
        if (clazz == SOAPMessage.class) {
            return (Dispatch<T>) createSAAJDispatch(portName, owner, binding, mode, next, epr);
        } else if (clazz == Source.class) {
            return (Dispatch<T>) createSourceDispatch(portName, owner, binding, mode, next, epr);
        } else if (clazz == DataSource.class) {
            return (Dispatch<T>) createDataSourceDispatch(portName, owner, binding, mode, next, epr);
        } else if (clazz == Message.class) {
            if(mode==Mode.MESSAGE)
                return (Dispatch<T>) createMessageDispatch(portName, owner, binding, next, epr);
            else
                throw new WebServiceException(mode+" not supported with Dispatch<Message>");
        } else if (clazz == Packet.class) {
            return (Dispatch<T>) createPacketDispatch(portName, owner, binding, next, epr);
        } else
            throw new WebServiceException("Unknown class type " + clazz.getName());
    }

    /**
     * Creates a new {@link Dispatch} stub that connects to the given pipe.
     *
     * @param portInfo
     *      see <a href="#param">common parameters</a>
     * @param owner
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param clazz
     *      Type of the {@link Dispatch} to be created.
     *      See {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param mode
     *      The mode of the dispatch.
     *      See {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param epr
     *      see <a href="#param">common parameters</a>
     * TODO: are these parameters making sense?
     */
    public static <T> Dispatch<T> createDispatch(WSPortInfo portInfo,
                                                 WSService owner,
                                                 WSBinding binding,
                                                 Class<T> clazz, Service.Mode mode,
                                                 @Nullable WSEndpointReference epr) {
        if (clazz == SOAPMessage.class) {
            return (Dispatch<T>) createSAAJDispatch(portInfo, binding, mode, epr);
        } else if (clazz == Source.class) {
            return (Dispatch<T>) createSourceDispatch(portInfo, binding, mode, epr);
        } else if (clazz == DataSource.class) {
            return (Dispatch<T>) createDataSourceDispatch(portInfo, binding, mode, epr);
        } else if (clazz == Message.class) {
            if(mode==Mode.MESSAGE)
                return (Dispatch<T>) createMessageDispatch(portInfo, binding, epr);
            else
                throw new WebServiceException(mode+" not supported with Dispatch<Message>");
        } else if (clazz == Packet.class) {
            if(mode==Mode.MESSAGE)
                return (Dispatch<T>) createPacketDispatch(portInfo, binding, epr);
            else
                throw new WebServiceException(mode+" not supported with Dispatch<Packet>");
        } else
            throw new WebServiceException("Unknown class type " + clazz.getName());
    }

    /**
     * Creates a new JAXB-based {@link Dispatch} stub that connects to the given pipe.
     *
     * @param portName
     *      see {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param owner
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param jaxbContext
     *      {@link JAXBContext} used to convert between objects and XML.
     * @param mode
     *      The mode of the dispatch.
     *      See {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param next
     *      see <a href="#param">common parameters</a>
     * @param epr
     *      see <a href="#param">common parameters</a>
     */
    @Deprecated
    public static Dispatch<Object> createJAXBDispatch(
                                           QName portName, WSService owner, WSBinding binding,
                                           JAXBContext jaxbContext, Service.Mode mode, Tube next,
                                           @Nullable WSEndpointReference epr) {
        return new JAXBDispatch(portName, jaxbContext, mode, (WSServiceDelegate)owner, next, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new JAXB-based {@link Dispatch} stub that connects to the given pipe.
     *
     * @param portInfo    see <a href="#param">common parameters</a>
     * @param binding     see <a href="#param">common parameters</a>
     * @param jaxbContext {@link JAXBContext} used to convert between objects and XML.
     * @param mode        The mode of the dispatch.
     *                    See {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param epr         see <a href="#param">common parameters</a>
     */
    public static Dispatch<Object> createJAXBDispatch(
            WSPortInfo portInfo, WSBinding binding,
            JAXBContext jaxbContext, Service.Mode mode,
            @Nullable WSEndpointReference epr) {
        return new JAXBDispatch(portInfo, jaxbContext, mode, (BindingImpl) binding, epr);
    }


    /**
     * Creates a new {@link Message}-based {@link Dispatch} stub that connects to the given pipe.
     * The returned dispatch is always {@link Mode#MESSAGE}.
     *
     * @param portName
     *      see {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param owner
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param next
     *      see <a href="#param">common parameters</a>
     * @param epr
     *      see <a href="#param">common parameters</a>
     */
    @Deprecated
    public static Dispatch<Message> createMessageDispatch(
                                           QName portName, WSService owner, WSBinding binding,
                                           Tube next, @Nullable WSEndpointReference epr) {
        return new MessageDispatch(portName, (WSServiceDelegate)owner, next, (BindingImpl)binding, epr);
    }


    /**
     * Creates a new {@link Message}-based {@link Dispatch} stub that connects to the given pipe.
     * The returned dispatch is always {@link Mode#MESSAGE}.
     *
     * @param portInfo
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param epr
     *      see <a href="#param">common parameters</a>
     */
    public static Dispatch<Message> createMessageDispatch(
                                           WSPortInfo portInfo, WSBinding binding,
                                           @Nullable WSEndpointReference epr) {
        return new MessageDispatch(portInfo, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new {@link Packet}-based {@link Dispatch} stub that connects to the given pipe.
     *
     * @param portName
     *      see {@link Service#createDispatch(QName, Class, Service.Mode)}.
     * @param owner
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param next
     *      see <a href="#param">common parameters</a>
     * @param epr
     *      see <a href="#param">common parameters</a>
     */
    public static Dispatch<Packet> createPacketDispatch(
                                           QName portName, WSService owner, WSBinding binding,
                                           Tube next, @Nullable WSEndpointReference epr) {
        return new PacketDispatch(portName, (WSServiceDelegate)owner, next, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new {@link Message}-based {@link Dispatch} stub that connects to the given pipe.
     * The returned dispatch is always {@link Mode#MESSAGE}.
     *
     * @param portInfo
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param epr
     *      see <a href="#param">common parameters</a>
     */
    public static Dispatch<Packet> createPacketDispatch(
                                           WSPortInfo portInfo, WSBinding binding,
                                           @Nullable WSEndpointReference epr) {
        return new PacketDispatch(portInfo, (BindingImpl)binding, epr);
    }

    /**
     * Creates a new strongly-typed proxy object that implements a given port interface.
     *
     * @param service
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param model
     *      This model shall represent a port interface.
     *      TODO: can model be constructed from portInterface and binding?
     *      Find out and update.
     * @param portInterface
     *      The port interface that has operations as Java methods.
     * @param next
     *      see <a href="#param">common parameters</a>
     * @param epr
     *      see <a href="#param">common parameters</a>
     */
    public <T> T createPortProxy( WSService service, WSBinding binding, SEIModel model,
                                  Class<T> portInterface, Tube next, @Nullable WSEndpointReference epr ) {

        SEIStub ps = new SEIStub((WSServiceDelegate)service,(BindingImpl)binding, (SOAPSEIModel)model, next, epr);
        return portInterface.cast(
            Proxy.newProxyInstance( portInterface.getClassLoader(),
                new Class[]{portInterface, WSBindingProvider.class}, ps ));
    }

     /**
     * Creates a new strongly-typed proxy object that implements a given port interface.
     *
     * @param portInfo
     *      see <a href="#param">common parameters</a>
     * @param binding
     *      see <a href="#param">common parameters</a>
     * @param model
     *      This model shall represent a port interface.
     *      TODO: can model be constructed from portInterface and binding?
     *      Find out and update.
     * @param portInterface
     *      The port interface that has operations as Java methods.
     * @param epr
     *      see <a href="#param">common parameters</a>
     */
    public <T> T createPortProxy( WSPortInfo portInfo, WSBinding binding, SEIModel model,
                                  Class<T> portInterface, @Nullable WSEndpointReference epr ) {

        SEIStub ps = new SEIStub(portInfo, (BindingImpl)binding, (SOAPSEIModel)model, epr);
        return portInterface.cast(
            Proxy.newProxyInstance( portInterface.getClassLoader(),
                new Class[]{portInterface, WSBindingProvider.class}, ps ));
    }
}
