/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws.spi;

import java.util.Iterator;
import javax.xml.namespace.QName;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.ws.WebServiceFeature;
import javax.xml.bind.JAXBContext;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceException;


/**
 * Service delegates are used internally by {@code Service} objects
 * to allow pluggability of JAX-WS implementations.
 * <p>
 * Every {@code Service} object has its own delegate, created using
 * the {@link javax.xml.ws.spi.Provider#createServiceDelegate} method. A {@code Service}
 * object delegates all of its instance methods to its delegate.
 *
 * @see javax.xml.ws.Service
 * @see javax.xml.ws.spi.Provider
 *
 * @since 1.6, JAX-WS 2.0
 */
public abstract class ServiceDelegate {

    /**
     * Default constructor.
     */
    protected ServiceDelegate() {
    }

    /**
     * The {@code getPort} method returns a proxy. A service client
     * uses this proxy to invoke operations on the target
     * service endpoint. The {@code serviceEndpointInterface}
     * specifies the service endpoint interface that is supported by
     * the created dynamic proxy instance.
     *
     * @param <T> Service endpoint interface
     * @param portName  Qualified name of the service endpoint in
     *                  the WSDL service description
     * @param serviceEndpointInterface Service endpoint interface
     *                  supported by the dynamic proxy
     * @return Object Proxy instance that
     *                supports the specified service endpoint
     *                interface
     * @throws WebServiceException This exception is thrown in the
     *                  following cases:
     *                  <UL>
     *                  <LI>If there is an error in creation of
     *                      the proxy
     *                  <LI>If there is any missing WSDL metadata
     *                      as required by this method
     *                  <LI>If an illegal
     *                      {@code serviceEndpointInterface}
     *                      or {@code portName} is specified
     *                  </UL>
     * @see java.lang.reflect.Proxy
     * @see java.lang.reflect.InvocationHandler
     **/
    public abstract <T> T getPort(QName portName,
            Class<T> serviceEndpointInterface);

    /**
     * The {@code getPort} method returns a proxy. A service client
     * uses this proxy to invoke operations on the target
     * service endpoint. The {@code serviceEndpointInterface}
     * specifies the service endpoint interface that is supported by
     * the created dynamic proxy instance.
     *
     * @param <T> Service endpoint interface
     * @param portName  Qualified name of the service endpoint in
     *                  the WSDL service description
     * @param serviceEndpointInterface Service endpoint interface
     *                  supported by the dynamic proxy or instance
     * @param features  A list of WebServiceFeatures to configure on the
     *                proxy.  Supported features not in the {@code features
     *                } parameter will have their default values.
     * @return Object Proxy instance that
     *                supports the specified service endpoint
     *                interface
     * @throws WebServiceException This exception is thrown in the
     *                  following cases:
     *                  <UL>
     *                  <LI>If there is an error in creation of
     *                      the proxy
     *                  <LI>If there is any missing WSDL metadata
     *                      as required by this method
     *                  <LI>If an illegal
     *                      {@code serviceEndpointInterface}
     *                      or {@code portName} is specified
     *                  <LI>If a feature is enabled that is not compatible
     *                      with this port or is unsupported.
     *                  </UL>
     * @see java.lang.reflect.Proxy
     * @see java.lang.reflect.InvocationHandler
     * @see WebServiceFeature
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract <T> T getPort(QName portName,
            Class<T> serviceEndpointInterface, WebServiceFeature... features);

    /**
     * The {@code getPort} method returns a proxy.
     * The parameter {@code endpointReference} specifies the
     * endpoint that will be invoked by the returned proxy.  If there
     * are any reference parameters in the
     * {@code endpointReference}, then those reference
     * parameters MUST appear as SOAP headers, indicating them to be
     * reference parameters, on all messages sent to the endpoint.
     * The {@code endpointReference's} address MUST be used
     * for invocations on the endpoint.
     * The parameter {@code serviceEndpointInterface} specifies
     * the service endpoint interface that is supported by the
     * returned proxy.
     * In the implementation of this method, the JAX-WS
     * runtime system takes the responsibility of selecting a protocol
     * binding (and a port) and configuring the proxy accordingly from
     * the WSDL associated with this {@code Service} instance or
     * from the metadata from the {@code endpointReference}.
     * If this {@code Service} instance has a WSDL and
     * the {@code endpointReference} metadata
     * also has a WSDL, then the WSDL from this instance MUST be used.
     * If this {@code Service} instance does not have a WSDL and
     * the {@code endpointReference} does have a WSDL, then the
     * WSDL from the {@code endpointReference} MAY be used.
     * The returned proxy should not be reconfigured by the client.
     * If this {@code Service} instance has a known proxy
     * port that matches the information contained in
     * the WSDL,
     * then that proxy is returned, otherwise a WebServiceException
     * is thrown.
     * <p>
     * Calling this method has the same behavior as the following
     * <pre>
     * {@code port = service.getPort(portName, serviceEndpointInterface);}
     * </pre>
     * where the {@code portName} is retrieved from the
     * metadata of the {@code endpointReference} or from the
     * {@code serviceEndpointInterface} and the WSDL
     * associated with this {@code Service} instance.
     *
     * @param <T> Service endpoint interface.
     * @param endpointReference  The {@code EndpointReference}
     * for the target service endpoint that will be invoked by the
     * returned proxy.
     * @param serviceEndpointInterface Service endpoint interface.
     * @param features  A list of {@code WebServiceFeatures} to configure on the
     *                proxy.  Supported features not in the {@code features
     *                } parameter will have their default values.
     * @return Object Proxy instance that supports the
     *                  specified service endpoint interface.
     * @throws WebServiceException
     *                  <UL>
     *                  <LI>If there is an error during creation
     *                      of the proxy.
     *                  <LI>If there is any missing WSDL metadata
     *                      as required by this method.
     *                  <LI>If the {@code endpointReference} metadata does
     *                      not match the {@code serviceName} of this
     *                      {@code Service} instance.
     *                  <LI>If a {@code portName} cannot be extracted
     *                      from the WSDL or {@code endpointReference} metadata.
     *                  <LI>If an invalid
     *                      {@code endpointReference}
     *                      is specified.
     *                  <LI>If an invalid
     *                      {@code serviceEndpointInterface}
     *                      is specified.
     *                  <LI>If a feature is enabled that is not compatible
     *                      with this port or is unsupported.
     *                  </UL>
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract <T> T getPort(EndpointReference endpointReference,
           Class<T> serviceEndpointInterface, WebServiceFeature... features);


    /**
     * The {@code getPort} method returns a proxy. The parameter
     * {@code serviceEndpointInterface} specifies the service
     * endpoint interface that is supported by the returned proxy.
     * In the implementation of this method, the JAX-WS
     * runtime system takes the responsibility of selecting a protocol
     * binding (and a port) and configuring the proxy accordingly.
     * The returned proxy should not be reconfigured by the client.
     *
     * @param <T> Service endpoint interface
     * @param serviceEndpointInterface Service endpoint interface
     * @return Object instance that supports the
     *                  specified service endpoint interface
     * @throws WebServiceException
     *                  <UL>
     *                  <LI>If there is an error during creation
     *                      of the proxy
     *                  <LI>If there is any missing WSDL metadata
     *                      as required by this method
     *                  <LI>If an illegal
     *                      {@code serviceEndpointInterface}
     *                      is specified
     *                  </UL>
     **/
    public abstract <T> T getPort(Class<T> serviceEndpointInterface);


    /**
     * The {@code getPort} method returns a proxy. The parameter
     * {@code serviceEndpointInterface} specifies the service
     * endpoint interface that is supported by the returned proxy.
     * In the implementation of this method, the JAX-WS
     * runtime system takes the responsibility of selecting a protocol
     * binding (and a port) and configuring the proxy accordingly.
     * The returned proxy should not be reconfigured by the client.
     *
     * @param <T> Service endpoint interface
     * @param serviceEndpointInterface Service endpoint interface
     * @param features  An array of {@code WebServiceFeatures} to configure on the
     *                proxy.  Supported features not in the {@code features
     *                } parameter will have their default values.
     * @return Object instance that supports the
     *                  specified service endpoint interface
     * @throws WebServiceException
     *                  <UL>
     *                  <LI>If there is an error during creation
     *                      of the proxy
     *                  <LI>If there is any missing WSDL metadata
     *                      as required by this method
     *                  <LI>If an illegal
     *                      {@code serviceEndpointInterface}
     *                      is specified
     *                  <LI>If a feature is enabled that is not compatible
     *                      with this port or is unsupported.
     *                  </UL>
     *
     * @see WebServiceFeature
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract <T> T getPort(Class<T> serviceEndpointInterface,
            WebServiceFeature... features);


    /**
     * Creates a new port for the service. Ports created in this way contain
     * no WSDL port type information and can only be used for creating
     * {@code Dispatch}instances.
     *
     * @param portName  Qualified name for the target service endpoint
     * @param bindingId A URI identifier of a binding.
     * @param endpointAddress Address of the target service endpoint as a URI
     * @throws WebServiceException If any error in the creation of
     * the port
     *
     * @see javax.xml.ws.soap.SOAPBinding#SOAP11HTTP_BINDING
     * @see javax.xml.ws.soap.SOAPBinding#SOAP12HTTP_BINDING
     * @see javax.xml.ws.http.HTTPBinding#HTTP_BINDING
     **/
    public abstract void addPort(QName portName, String bindingId,
            String endpointAddress);



    /**
     * Creates a {@code Dispatch} instance for use with objects of
     * the user's choosing.
     *
     * @param <T> type used for messages or message payloads. Implementations are required to
     * support {@code javax.xml.transform.Source} and {@code javax.xml.soap.SOAPMessage}.
     * @param portName  Qualified name for the target service endpoint
     * @param type The class of object used for messages or message
     * payloads. Implementations are required to support
     * {@code javax.xml.transform.Source} and {@code javax.xml.soap.SOAPMessage}.
     * @param mode Controls whether the created dispatch instance is message
     * or payload oriented, i.e. whether the user will work with complete
     * protocol messages or message payloads. E.g. when using the SOAP
     * protocol, this parameter controls whether the user will work with
     * SOAP messages or the contents of a SOAP body. Mode MUST be {@code MESSAGE}
     * when type is {@code SOAPMessage}.
     *
     * @return Dispatch instance
     * @throws WebServiceException If any error in the creation of
     *                  the {@code Dispatch} object
     * @see javax.xml.transform.Source
     * @see javax.xml.soap.SOAPMessage
     **/
    public abstract <T> Dispatch<T> createDispatch(QName portName, Class<T> type,
            Service.Mode mode);

    /**
     * Creates a {@code Dispatch} instance for use with objects of
     * the user's choosing.
     *
     * @param <T> type used for messages or message payloads. Implementations are required to
     * support {@code javax.xml.transform.Source} and {@code javax.xml.soap.SOAPMessage}.
     * @param portName  Qualified name for the target service endpoint
     * @param type The class of object used for messages or message
     * payloads. Implementations are required to support
     * {@code javax.xml.transform.Source} and {@code javax.xml.soap.SOAPMessage}.
     * @param mode Controls whether the created dispatch instance is message
     * or payload oriented, i.e. whether the user will work with complete
     * protocol messages or message payloads. E.g. when using the SOAP
     * protocol, this parameter controls whether the user will work with
     * SOAP messages or the contents of a SOAP body. Mode MUST be {@code MESSAGE}
     * when type is {@code SOAPMessage}.
     * @param features  A list of {@code WebServiceFeatures} to configure on the
     *                proxy.  Supported features not in the {@code features
     *                } parameter will have their default values.
     *
     * @return Dispatch instance
     * @throws WebServiceException If any error in the creation of
     *                  the {@code Dispatch} object or if a
     *                  feature is enabled that is not compatible with
     *                  this port or is unsupported.
     *
     * @see javax.xml.transform.Source
     * @see javax.xml.soap.SOAPMessage
     * @see WebServiceFeature
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract <T> Dispatch<T> createDispatch(QName portName, Class<T> type,
            Service.Mode mode, WebServiceFeature... features);

    /**
     * Creates a {@code Dispatch} instance for use with objects of
     * the user's choosing. If there
     * are any reference parameters in the
     * {@code endpointReference}, then those reference
     * parameters MUST appear as SOAP headers, indicating them to be
     * reference parameters, on all messages sent to the endpoint.
     * The {@code endpointReference's} address MUST be used
     * for invocations on the endpoint.
     * In the implementation of this method, the JAX-WS
     * runtime system takes the responsibility of selecting a protocol
     * binding (and a port) and configuring the dispatch accordingly from
     * the WSDL associated with this {@code Service} instance or
     * from the metadata from the {@code endpointReference}.
     * If this {@code Service} instance has a WSDL and
     * the {@code endpointReference}
     * also has a WSDL in its metadata, then the WSDL from this instance MUST be used.
     * If this {@code Service} instance does not have a WSDL and
     * the {@code endpointReference} does have a WSDL, then the
     * WSDL from the {@code endpointReference} MAY be used.
     * An implementation MUST be able to retrieve the {@code portName} from the
     * {@code endpointReference} metadata.
     * <p>
     * This method behaves the same as calling
     * <pre>
     * {@code dispatch = service.createDispatch(portName, type, mode, features);}
     * </pre>
     * where the {@code portName} is retrieved from the
     * WSDL or {@code EndpointReference} metadata.
     *
     * @param <T> type of object used to messages or message
     * payloads. Implementations are required to support
     * {@code javax.xml.transform.Source} and {@code javax.xml.soap.SOAPMessage}.
     * @param endpointReference  The {@code EndpointReference}
     * for the target service endpoint that will be invoked by the
     * returned {@code Dispatch} object.
     * @param type The class of object used to messages or message
     * payloads. Implementations are required to support
     * {@code javax.xml.transform.Source} and {@code javax.xml.soap.SOAPMessage}.
     * @param mode Controls whether the created dispatch instance is message
     * or payload oriented, i.e. whether the user will work with complete
     * protocol messages or message payloads. E.g. when using the SOAP
     * protocol, this parameter controls whether the user will work with
     * SOAP messages or the contents of a SOAP body. Mode MUST be {@code MESSAGE}
     * when type is {@code SOAPMessage}.
     * @param features  An array of {@code WebServiceFeatures} to configure on the
     *                proxy.  Supported features not in the {@code features
     *                } parameter will have their default values.
     *
     * @return Dispatch instance
     * @throws WebServiceException
     *                  <UL>
     *                    <LI>If there is any missing WSDL metadata
     *                      as required by this method.
     *                    <li>If the {@code endpointReference} metadata does
     *                      not match the {@code serviceName} or {@code portName}
     *                      of a WSDL associated
     *                      with this {@code Service} instance.
     *                    <li>If the {@code portName} cannot be determined
     *                    from the {@code EndpointReference} metadata.
     *                    <li>If any error in the creation of
     *                     the {@code Dispatch} object.
     *                    <li>If a feature is enabled that is not
     *                    compatible with this port or is unsupported.
     *                  </UL>
     *
     * @see javax.xml.transform.Source
     * @see javax.xml.soap.SOAPMessage
     * @see WebServiceFeature
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract <T> Dispatch<T> createDispatch(EndpointReference endpointReference,
            Class<T> type, Service.Mode mode,
            WebServiceFeature... features);



    /**
     * Creates a {@code Dispatch} instance for use with JAXB
     * generated objects.
     *
     * @param portName  Qualified name for the target service endpoint
     * @param context The JAXB context used to marshall and unmarshall
     * messages or message payloads.
     * @param mode Controls whether the created dispatch instance is message
     * or payload oriented, i.e. whether the user will work with complete
     * protocol messages or message payloads. E.g. when using the SOAP
     * protocol, this parameter controls whether the user will work with
     * SOAP messages or the contents of a SOAP body.
     *
     * @return Dispatch instance
     * @throws WebServiceException If any error in the creation of
     *                  the {@code Dispatch} object
     *
     * @see javax.xml.bind.JAXBContext
     **/
    public abstract Dispatch<Object> createDispatch(QName portName,
            JAXBContext context, Service.Mode mode);


    /**
     * Creates a {@code Dispatch} instance for use with JAXB
     * generated objects.
     *
     * @param portName  Qualified name for the target service endpoint
     * @param context The JAXB context used to marshall and unmarshall
     * messages or message payloads.
     * @param mode Controls whether the created dispatch instance is message
     * or payload oriented, i.e. whether the user will work with complete
     * protocol messages or message payloads. E.g. when using the SOAP
     * protocol, this parameter controls whether the user will work with
     * SOAP messages or the contents of a SOAP body.
     * @param features  A list of {@code WebServiceFeatures} to configure on the
     *                proxy.  Supported features not in the {@code features
     *                } parameter will have their default values.
     *
     * @return Dispatch instance
     * @throws WebServiceException If any error in the creation of
     *                  the {@code Dispatch} object or if a
     *                  feature is enabled that is not compatible with
     *                  this port or is unsupported.
     *
     * @see javax.xml.bind.JAXBContext
     * @see WebServiceFeature
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract Dispatch<Object> createDispatch(QName portName,
            JAXBContext context, Service.Mode mode, WebServiceFeature... features);

    /**
     * Creates a {@code Dispatch} instance for use with JAXB
     * generated objects. If there
     * are any reference parameters in the
     * {@code endpointReference}, then those reference
     * parameters MUST appear as SOAP headers, indicating them to be
     * reference parameters, on all messages sent to the endpoint.
     * The {@code endpointReference's} address MUST be used
     * for invocations on the endpoint.
     * In the implementation of this method, the JAX-WS
     * runtime system takes the responsibility of selecting a protocol
     * binding (and a port) and configuring the dispatch accordingly from
     * the WSDL associated with this {@code Service} instance or
     * from the metadata from the {@code endpointReference}.
     * If this {@code Service} instance has a WSDL and
     * the {@code endpointReference}
     * also has a WSDL in its metadata, then the WSDL from this instance
     * MUST be used.
     * If this {@code Service} instance does not have a WSDL and
     * the {@code endpointReference} does have a WSDL, then the
     * WSDL from the {@code endpointReference} MAY be used.
     * An implementation MUST be able to retrieve the {@code portName} from the
     * {@code endpointReference} metadata.
     * <p>
     * This method behavies the same as calling
     * <pre>
     * {@code dispatch = service.createDispatch(portName, context, mode, features);}
     * </pre>
     * where the {@code portName} is retrieved from the
     * WSDL or {@code endpointReference} metadata.
     *
     * @param endpointReference  The {@code EndpointReference}
     * for the target service endpoint that will be invoked by the
     * returned {@code Dispatch} object.
     * @param context The JAXB context used to marshall and unmarshall
     * messages or message payloads.
     * @param mode Controls whether the created dispatch instance is message
     * or payload oriented, i.e. whether the user will work with complete
     * protocol messages or message payloads. E.g. when using the SOAP
     * protocol, this parameter controls whether the user will work with
     * SOAP messages or the contents of a SOAP body.
     * @param features  An array of {@code WebServiceFeatures} to configure on the
     *                proxy.  Supported features not in the {@code features
     *                } parameter will have their default values.
     *
     * @return Dispatch instance
     * @throws WebServiceException
     *                  <UL>
     *                    <li>If there is any missing WSDL metadata
     *                      as required by this method.
     *                    <li>If the {@code endpointReference} metadata does
     *                    not match the {@code serviceName} or {@code portName}
     *                    of a WSDL associated
     *                    with this {@code Service} instance.
     *                    <li>If the {@code portName} cannot be determined
     *                    from the {@code EndpointReference} metadata.
     *                    <li>If any error in the creation of
     *                    the {@code Dispatch} object.
     *                    <li>if a feature is enabled that is not
     *                    compatible with this port or is unsupported.
     *                  </UL>
     *
     * @see javax.xml.bind.JAXBContext
     * @see WebServiceFeature
     *
     * @since 1.6, JAX-WS 2.1
    **/
    public abstract Dispatch<Object> createDispatch(EndpointReference endpointReference,
            JAXBContext context, Service.Mode mode,
            WebServiceFeature... features);


    /**
     * Gets the name of this service.
     * @return Qualified name of this service
     **/
    public abstract QName getServiceName();

    /**
     * Returns an {@code Iterator} for the list of
     * {@code QName}s of service endpoints grouped by this
     * service
     *
     * @return Returns {@code java.util.Iterator} with elements
     *         of type {@code javax.xml.namespace.QName}
     * @throws WebServiceException If this Service class does not
     *         have access to the required WSDL metadata
     **/
    public abstract Iterator<javax.xml.namespace.QName> getPorts();

    /**
     * Gets the location of the WSDL document for this Service.
     *
     * @return URL for the location of the WSDL document for
     *         this service
     **/
    public abstract java.net.URL getWSDLDocumentLocation();

    /**
     * Returns the configured handler resolver.
     *
     * @return HandlerResolver The {@code HandlerResolver} being
     *         used by this {@code Service} instance, or {@code null}
     *         if there isn't one.
     **/
    public abstract HandlerResolver getHandlerResolver();

    /**
     * Sets the {@code HandlerResolver} for this {@code Service}
     * instance.
     * <p>
     * The handler resolver, if present, will be called once for each
     * proxy or dispatch instance that is created, and the handler chain
     * returned by the resolver will be set on the instance.
     *
     * @param handlerResolver The {@code HandlerResolver} to use
     *        for all subsequently created proxy/dispatch objects.
     *
     * @see javax.xml.ws.handler.HandlerResolver
     **/
    public abstract void setHandlerResolver(HandlerResolver handlerResolver);

    /**
     * Returns the executor for this {@code Service}instance.
     *
     * The executor is used for all asynchronous invocations that
     * require callbacks.
     *
     * @return The {@code java.util.concurrent.Executor} to be
     *         used to invoke a callback.
     *
     * @see java.util.concurrent.Executor
     **/
    public abstract java.util.concurrent.Executor getExecutor();

    /**
     * Sets the executor for this {@code Service} instance.
     *
     * The executor is used for all asynchronous invocations that
     * require callbacks.
     *
     * @param executor The {@code java.util.concurrent.Executor}
     *        to be used to invoke a callback.
     *
     * @throws SecurityException If the instance does not support
     *         setting an executor for security reasons (e.g. the
     *         necessary permissions are missing).
     *
     * @see java.util.concurrent.Executor
     **/
    public abstract void setExecutor(java.util.concurrent.Executor executor);

}
