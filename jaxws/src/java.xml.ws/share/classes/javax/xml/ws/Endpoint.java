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

package javax.xml.ws;

import java.util.List;
import java.util.Map;
import javax.xml.ws.spi.Provider;
import javax.xml.ws.spi.http.HttpContext;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import org.w3c.dom.Element;


/**
 * A Web service endpoint.
 *
 * <p>Endpoints are created using the static methods defined in this
 * class. An endpoint is always tied to one {@code Binding}
 * and one implementor, both set at endpoint creation time.
 *
 * <p>An endpoint is either in a published or an unpublished state.
 * The {@code publish} methods can be used to start publishing
 * an endpoint, at which point it starts accepting incoming requests.
 * Conversely, the {@code stop} method can be used to stop
 * accepting incoming requests and take the endpoint down.
 * Once stopped, an endpoint cannot be published again.
 *
 * <p>An {@code Executor} may be set on the endpoint in order
 * to gain better control over the threads used to dispatch incoming
 * requests. For instance, thread pooling with certain parameters
 * can be enabled by creating a {@code ThreadPoolExecutor} and
 * registering it with the endpoint.
 *
 * <p>Handler chains can be set using the contained {@code Binding}.
 *
 * <p>An endpoint may have a list of metadata documents, such as WSDL
 * and XMLSchema documents, bound to it. At publishing time, the
 * JAX-WS implementation will try to reuse as much of that metadata
 * as possible instead of generating new ones based on the annotations
 * present on the implementor.
 *
 * @since 1.6, JAX-WS 2.0
 *
 * @see javax.xml.ws.Binding
 * @see javax.xml.ws.BindingType
 * @see javax.xml.ws.soap.SOAPBinding
 * @see java.util.concurrent.Executor
 *
 **/
public abstract class Endpoint {

    /** Standard property: name of WSDL service.
     *  <p>Type: javax.xml.namespace.QName
     **/
    public static final String WSDL_SERVICE = "javax.xml.ws.wsdl.service";

    /** Standard property: name of WSDL port.
     *  <p>Type: javax.xml.namespace.QName
     **/
    public static final String WSDL_PORT = "javax.xml.ws.wsdl.port";

    /**
     * Creates an endpoint with the specified implementor object. If there is
     * a binding specified via a BindingType annotation then it MUST be used else
     * a default of SOAP 1.1 / HTTP binding MUST be used.
     * <p>
     * The newly created endpoint may be published by calling
     * one of the {@link javax.xml.ws.Endpoint#publish(String)} and
     * {@link javax.xml.ws.Endpoint#publish(Object)} methods.
     *
     *
     * @param implementor The endpoint implementor.
     *
     * @return The newly created endpoint.
     *
     **/
    public static Endpoint create(Object implementor) {
        return create(null, implementor);
    }

    /**
     * Creates an endpoint with the specified implementor object and web
     * service features. If there is a binding specified via a BindingType
     * annotation then it MUST be used else a default of SOAP 1.1 / HTTP
     * binding MUST be used.
     * <p>
     * The newly created endpoint may be published by calling
     * one of the {@link javax.xml.ws.Endpoint#publish(String)} and
     * {@link javax.xml.ws.Endpoint#publish(Object)} methods.
     *
     *
     * @param implementor The endpoint implementor.
     * @param features A list of WebServiceFeature to configure on the
     *        endpoint. Supported features not in the {@code features
     *        } parameter will have their default values.
     *
     *
     * @return The newly created endpoint.
     * @since 1.7, JAX-WS 2.2
     *
     */
    public static Endpoint create(Object implementor, WebServiceFeature ... features) {
        return create(null, implementor, features);
    }

    /**
     * Creates an endpoint with the specified binding type and
     * implementor object.
     * <p>
     * The newly created endpoint may be published by calling
     * one of the {@link javax.xml.ws.Endpoint#publish(String)} and
     * {@link javax.xml.ws.Endpoint#publish(Object)} methods.
     *
     * @param bindingId A URI specifying the binding to use. If the bindingID is
     * {@code null} and no binding is specified via a BindingType
     * annotation then a default SOAP 1.1 / HTTP binding MUST be used.
     *
     * @param implementor The endpoint implementor.
     *
     * @return The newly created endpoint.
     *
     **/
    public static Endpoint create(String bindingId, Object implementor) {
        return Provider.provider().createEndpoint(bindingId, implementor);
    }

    /**
     * Creates an endpoint with the specified binding type,
     * implementor object, and web service features.
     * <p>
     * The newly created endpoint may be published by calling
     * one of the {@link javax.xml.ws.Endpoint#publish(String)} and
     * {@link javax.xml.ws.Endpoint#publish(Object)} methods.
     *
     * @param bindingId A URI specifying the binding to use. If the bindingID is
     * {@code null} and no binding is specified via a BindingType
     * annotation then a default SOAP 1.1 / HTTP binding MUST be used.
     *
     * @param implementor The endpoint implementor.
     *
     * @param features A list of WebServiceFeature to configure on the
     *        endpoint. Supported features not in the {@code features
     *        } parameter will have their default values.
     *
     * @return The newly created endpoint.
     * @since 1.7, JAX-WS 2.2
     */
    public static Endpoint create(String bindingId, Object implementor, WebServiceFeature ... features) {
        return Provider.provider().createEndpoint(bindingId, implementor, features);
    }

    /**
     * Returns the binding for this endpoint.
     *
     * @return The binding for this endpoint
     **/
    public abstract Binding getBinding();

    /**
     * Returns the implementation object for this endpoint.
     *
     * @return The implementor for this endpoint
     **/
    public abstract Object getImplementor();

    /**
     * Publishes this endpoint at the given address.
     * The necessary server infrastructure will be created and
     * configured by the JAX-WS implementation using some default configuration.
     * In order to get more control over the server configuration, please
     * use the {@link javax.xml.ws.Endpoint#publish(Object)} method instead.
     *
     * @param address A URI specifying the address to use. The address
     *        MUST be compatible with the binding specified at the
     *        time the endpoint was created.
     *
     * @throws java.lang.IllegalArgumentException
     *          If the provided address URI is not usable
     *          in conjunction with the endpoint's binding.
     *
     * @throws java.lang.IllegalStateException
     *          If the endpoint has been published already or it has been stopped.
     *
     * @throws java.lang.SecurityException
     *          If a {@code java.lang.SecurityManger}
     *          is being used and the application doesn't have the
     *          {@code WebServicePermission("publishEndpoint")} permission.
     **/
    public abstract void publish(String address);

    /**
     * Creates and publishes an endpoint for the specified implementor
     * object at the given address.
     * <p>
     * The necessary server infrastructure will be created and
     * configured by the JAX-WS implementation using some default configuration.
     *
     * In order to get more control over the server configuration, please
     * use the {@link javax.xml.ws.Endpoint#create(String,Object)} and
     * {@link javax.xml.ws.Endpoint#publish(Object)} methods instead.
     *
     * @param address A URI specifying the address and transport/protocol
     *        to use. A http: URI MUST result in the SOAP 1.1/HTTP
     *        binding being used. Implementations may support other
     *        URI schemes.
     * @param implementor The endpoint implementor.
     *
     * @return The newly created endpoint.
     *
     * @throws java.lang.SecurityException
     *          If a {@code java.lang.SecurityManger}
     *          is being used and the application doesn't have the
     *          {@code WebServicePermission("publishEndpoint")} permission.
     *
     **/
    public static Endpoint publish(String address, Object implementor) {
        return Provider.provider().createAndPublishEndpoint(address, implementor);
    }

    /**
     * Creates and publishes an endpoint for the specified implementor
     * object at the given address. The created endpoint is configured
     * with the web service features.
     * <p>
     * The necessary server infrastructure will be created and
     * configured by the JAX-WS implementation using some default configuration.
     *
     * In order to get more control over the server configuration, please
     * use the {@link javax.xml.ws.Endpoint#create(String,Object)} and
     * {@link javax.xml.ws.Endpoint#publish(Object)} methods instead.
     *
     * @param address A URI specifying the address and transport/protocol
     *        to use. A http: URI MUST result in the SOAP 1.1/HTTP
     *        binding being used. Implementations may support other
     *        URI schemes.
     * @param implementor The endpoint implementor.
     * @param features A list of WebServiceFeature to configure on the
     *        endpoint. Supported features not in the {@code features
     *        } parameter will have their default values.
     * @return The newly created endpoint.
     *
     * @throws java.lang.SecurityException
     *          If a {@code java.lang.SecurityManger}
     *          is being used and the application doesn't have the
     *          {@code WebServicePermission("publishEndpoint")} permission.
     * @since 1.7, JAX-WS 2.2
     */
    public static Endpoint publish(String address, Object implementor, WebServiceFeature ... features) {
        return Provider.provider().createAndPublishEndpoint(address, implementor, features);
    }

    /**
     * Publishes this endpoint at the provided server context.
     * A server context encapsulates the server infrastructure
     * and addressing information for a particular transport.
     * For a call to this method to succeed, the server context
     * passed as an argument to it MUST be compatible with the
     * endpoint's binding.
     *
     * @param serverContext An object representing a server
     *           context to be used for publishing the endpoint.
     *
     * @throws java.lang.IllegalArgumentException
     *              If the provided server context is not
     *              supported by the implementation or turns
     *              out to be unusable in conjunction with the
     *              endpoint's binding.
     *
     * @throws java.lang.IllegalStateException
     *         If the endpoint has been published already or it has been stopped.
     *
     * @throws java.lang.SecurityException
     *          If a {@code java.lang.SecurityManger}
     *          is being used and the application doesn't have the
     *          {@code WebServicePermission("publishEndpoint")} permission.
     **/
    public abstract void publish(Object serverContext);

    /**
     * Publishes this endpoint at the provided server context.
     * A server context encapsulates the server infrastructure
     * and addressing information for a particular transport.
     * For a call to this method to succeed, the server context
     * passed as an argument to it MUST be compatible with the
     * endpoint's binding.
     *
     * <p>
     * This is meant for container developers to publish the
     * the endpoints portably and not intended for the end
     * developers.
     *
     *
     * @param serverContext An object representing a server
     *           context to be used for publishing the endpoint.
     *
     * @throws java.lang.IllegalArgumentException
     *              If the provided server context is not
     *              supported by the implementation or turns
     *              out to be unusable in conjunction with the
     *              endpoint's binding.
     *
     * @throws java.lang.IllegalStateException
     *         If the endpoint has been published already or it has been stopped.
     *
     * @throws java.lang.SecurityException
     *          If a {@code java.lang.SecurityManger}
     *          is being used and the application doesn't have the
     *          {@code WebServicePermission("publishEndpoint")} permission.
     * @since 1.7, JAX-WS 2.2
     */
    public void publish(HttpContext serverContext) {
        throw new UnsupportedOperationException("JAX-WS 2.2 implementation must override this default behaviour.");
    }

    /**
     * Stops publishing this endpoint.
     *
     * If the endpoint is not in a published state, this method
     * has no effect.
     *
     **/
    public abstract void stop();

    /**
     * Returns true if the endpoint is in the published state.
     *
     * @return {@code true} if the endpoint is in the published state.
     **/
    public abstract boolean isPublished();

    /**
     * Returns a list of metadata documents for the service.
     *
     * @return {@code List<javax.xml.transform.Source>} A list of metadata documents for the service
     **/
    public abstract List<javax.xml.transform.Source> getMetadata();

    /**
     * Sets the metadata for this endpoint.
     *
     * @param metadata A list of XML document sources containing
     *           metadata information for the endpoint (e.g.
     *           WSDL or XML Schema documents)
     *
     * @throws java.lang.IllegalStateException  If the endpoint
     *         has already been published.
     **/
    public abstract void setMetadata(List<javax.xml.transform.Source> metadata);

    /**
     * Returns the executor for this {@code Endpoint}instance.
     *
     * The executor is used to dispatch an incoming request to
     * the implementor object.
     *
     * @return The {@code java.util.concurrent.Executor} to be
     *         used to dispatch a request.
     *
     * @see java.util.concurrent.Executor
     **/
    public abstract java.util.concurrent.Executor getExecutor();

    /**
     * Sets the executor for this {@code Endpoint} instance.
     *
     * The executor is used to dispatch an incoming request to
     * the implementor object.
     *
     * If this {@code Endpoint} is published using the
     * {@code publish(Object)} method and the specified server
     * context defines its own threading behavior, the executor
     * may be ignored.
     *
     * @param executor The {@code java.util.concurrent.Executor}
     *        to be used to dispatch a request.
     *
     * @throws SecurityException  If the instance does not support
     *         setting an executor for security reasons (e.g. the
     *         necessary permissions are missing).
     *
     * @see java.util.concurrent.Executor
     **/
    public abstract void setExecutor(java.util.concurrent.Executor executor);

    /**
     * Returns the property bag for this {@code Endpoint} instance.
     *
     * @return Map&lt;String,Object&gt; The property bag
     *         associated with this instance.
     **/
    public abstract Map<String,Object> getProperties();

    /**
     * Sets the property bag for this {@code Endpoint} instance.
     *
     * @param properties The property bag associated with
     *        this instance.
     **/
    public abstract void setProperties(Map<String,Object> properties);

    /**
     * Returns the {@code EndpointReference} associated with
     * this {@code Endpoint} instance.
     * <p>
     * If the Binding for this {@code bindingProvider} is
     * either SOAP1.1/HTTP or SOAP1.2/HTTP, then a
     * {@code W3CEndpointReference} MUST be returned.
     *
     * @param referenceParameters Reference parameters to be associated with the
     * returned {@code EndpointReference} instance.
     * @return EndpointReference of this {@code Endpoint} instance.
     * If the returned {@code EndpointReference} is of type
     * {@code W3CEndpointReference} then it MUST contain the
     * the specified {@code referenceParameters}.

     * @throws WebServiceException If any error in the creation of
     * the {@code EndpointReference} or if the {@code Endpoint} is
     * not in the published state.
     * @throws UnsupportedOperationException If this {@code BindingProvider}
     * uses the XML/HTTP binding.
     *
     * @see W3CEndpointReference
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract EndpointReference getEndpointReference(Element... referenceParameters);

    /**
     * Returns the {@code EndpointReference} associated with
     * this {@code Endpoint} instance.
     *
     * @param <T> The type of EndpointReference.
     * @param clazz Specifies the type of EndpointReference  that MUST be returned.
     * @param referenceParameters Reference parameters to be associated with the
     * returned {@code EndpointReference} instance.
     * @return EndpointReference of type {@code clazz} of this
     * {@code Endpoint} instance.
     * If the returned {@code EndpointReference} is of type
     * {@code W3CEndpointReference} then it MUST contain the
     * the specified {@code referenceParameters}.

     * @throws WebServiceException If any error in the creation of
     * the {@code EndpointReference} or if the {@code Endpoint} is
     * not in the published state or if the {@code clazz} is not a supported
     * {@code EndpointReference} type.
     * @throws UnsupportedOperationException If this {@code BindingProvider}
     * uses the XML/HTTP binding.
     *
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract <T extends EndpointReference> T getEndpointReference(Class<T> clazz,
            Element... referenceParameters);

    /**
     * By setting a {@code EndpointContext}, JAX-WS runtime knows about
     * addresses of other endpoints in an application. If multiple endpoints
     * share different ports of a WSDL, then the multiple port addresses
     * are patched when the WSDL is accessed.
     *
     * <p>
     * This needs to be set before publishing the endpoints.
     *
     * @param ctxt that is shared for multiple endpoints
     * @throws java.lang.IllegalStateException
     *        If the endpoint has been published already or it has been stopped.
     *
     * @since 1.7, JAX-WS 2.2
     */
    public void setEndpointContext(EndpointContext ctxt) {
        throw new UnsupportedOperationException("JAX-WS 2.2 implementation must override this default behaviour.");
    }
}
