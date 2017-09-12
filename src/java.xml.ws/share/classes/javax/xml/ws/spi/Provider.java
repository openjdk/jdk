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

import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.ws.*;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

import org.w3c.dom.Element;

/**
 * Service provider for {@code ServiceDelegate} and
 * {@code Endpoint} objects.
 *
 * @since 1.6, JAX-WS 2.0
 */
public abstract class Provider {

    /**
     * A constant representing the name of the default
     * {@code Provider} implementation class.
     **/
    // Using two strings so that package renaming doesn't change it
    private static final String DEFAULT_JAXWSPROVIDER =
            "com.sun"+".xml.internal.ws.spi.ProviderImpl";

    /**
     * Creates a new instance of Provider
     */
    protected Provider() {
    }

    /**
     *
     * Creates a new provider object.
     * <p>
     * The algorithm used to locate the provider subclass to use consists
     * of the following steps:
     * <ul>
     *  <li> Use the service-provider loading facilities, defined by the {@link java.util.ServiceLoader} class,
     *  to attempt to locate and load an implementation of {@link javax.xml.ws.spi.Provider} service using
     *  the {@linkplain java.util.ServiceLoader#load(java.lang.Class) default loading mechanism}.
     *  <li>Use the configuration file "jaxws.properties". The file is in standard
     *  {@link java.util.Properties} format and typically located in the
     *  {@code conf} directory of the Java installation. It contains the fully qualified
     *  name of the implementation class with the key {@code javax.xml.ws.spi.Provider}.
     *  <li> If a system property with the name {@code javax.xml.ws.spi.Provider}
     *  is defined, then its value is used as the name of the implementation class.
     *  <li> Finally, a platform default implementation is used.
     * </ul>
     *
     * @return provider object
     */
    public static Provider provider() {
        try {
            return FactoryFinder.find(Provider.class, DEFAULT_JAXWSPROVIDER);
        } catch (WebServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WebServiceException("Unable to createEndpointReference Provider", ex);
        }
    }

    /**
     * Creates a service delegate object.
     *
     * @param wsdlDocumentLocation A URL pointing to the WSDL document
     *        for the service, or {@code null} if there isn't one.
     * @param serviceName The qualified name of the service.
     * @param serviceClass The service class, which MUST be either
     *        {@code javax.xml.ws.Service} or a subclass thereof.
     * @return The newly created service delegate.
     */
    public abstract ServiceDelegate createServiceDelegate(
            java.net.URL wsdlDocumentLocation,
            QName serviceName, Class<? extends Service> serviceClass);

    /**
     * Creates a service delegate object.
     *
     * @param wsdlDocumentLocation A URL pointing to the WSDL document
     *        for the service, or {@code null} if there isn't one.
     * @param serviceName The qualified name of the service.
     * @param serviceClass The service class, which MUST be either
     *        {@code javax.xml.ws.Service} or a subclass thereof.
     * @param features Web Service features that must be configured on
     *        the service. If the provider doesn't understand a feature,
     *        it must throw a WebServiceException.
     * @return The newly created service delegate.
     *
     * @since 1.7, JAX-WS 2.2
     */
    public ServiceDelegate createServiceDelegate(
            java.net.URL wsdlDocumentLocation,
            QName serviceName, Class<? extends Service> serviceClass, WebServiceFeature ... features) {
        throw new UnsupportedOperationException("JAX-WS 2.2 implementation must override this default behaviour.");
    }

    /**
     *
     * Creates an endpoint object with the provided binding and implementation
     * object.
     *
     * @param bindingId A URI specifying the desired binding (e.g. SOAP/HTTP)
     * @param implementor A service implementation object to which
     *        incoming requests will be dispatched. The corresponding
     *        class MUST be annotated with all the necessary Web service
     *        annotations.
     * @return The newly created endpoint.
     */
    public abstract Endpoint createEndpoint(String bindingId,
            Object implementor);

    /**
     * Creates and publishes an endpoint object with the specified
     * address and implementation object.
     *
     * @param address A URI specifying the address and transport/protocol
     *        to use. A http: URI MUST result in the SOAP 1.1/HTTP
     *        binding being used. Implementations may support other
     *        URI schemes.
     * @param implementor A service implementation object to which
     *        incoming requests will be dispatched. The corresponding
     *        class MUST be annotated with all the necessary Web service
     *        annotations.
     * @return The newly created endpoint.
     */
    public abstract Endpoint createAndPublishEndpoint(String address,
            Object implementor);

    /**
     * read an EndpointReference from the infoset contained in
     * {@code eprInfoset}.
     *
     * @param eprInfoset infoset for EndpointReference
     *
     * @return the {@code EndpointReference} unmarshalled from
     * {@code eprInfoset}.  This method never returns {@code null}.
     *
     * @throws WebServiceException If there is an error creating the
     * {@code EndpointReference} from the specified {@code eprInfoset}.
     *
     * @throws NullPointerException If the {@code null}
     * {@code eprInfoset} value is given.
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract EndpointReference readEndpointReference(javax.xml.transform.Source eprInfoset);


    /**
     * The getPort method returns a proxy.  If there
     * are any reference parameters in the
     * {@code endpointReference}, then those reference
     * parameters MUST appear as SOAP headers, indicating them to be
     * reference parameters, on all messages sent to the endpoint.
     * The parameter  {@code serviceEndpointInterface} specifies
     * the service endpoint interface that is supported by the
     * returned proxy.
     * The parameter {@code endpointReference} specifies the
     * endpoint that will be invoked by the returned proxy.
     * In the implementation of this method, the JAX-WS
     * runtime system takes the responsibility of selecting a protocol
     * binding (and a port) and configuring the proxy accordingly from
     * the WSDL metadata of the
     * {@code serviceEndpointInterface} and the {@code EndpointReference}.
     * For this method
     * to successfully return a proxy, WSDL metadata MUST be available and the
     * {@code endpointReference} MUST contain an implementation understood
     * {@code serviceName} metadata.
     *
     *
     * @param <T> Service endpoint interface
     * @param endpointReference the EndpointReference that will
     * be invoked by the returned proxy.
     * @param serviceEndpointInterface Service endpoint interface
     * @param features  A list of WebServiceFeatures to configure on the
     *                proxy.  Supported features not in the {@code features
     *                } parameter will have their default values.
     * @return Object Proxy instance that supports the
     *                  specified service endpoint interface
     * @throws WebServiceException
     *                  <UL>
     *                  <LI>If there is an error during creation
     *                      of the proxy
     *                  <LI>If there is any missing WSDL metadata
     *                      as required by this method}
     *                  <LI>If this
     *                      {@code endpointReference}
     *                      is illegal
     *                  <LI>If an illegal
     *                      {@code serviceEndpointInterface}
     *                      is specified
     *                  <LI>If a feature is enabled that is not compatible with
     *                      this port or is unsupported.
     *                   </UL>
     *
     * @see WebServiceFeature
     *
     * @since 1.6, JAX-WS 2.1
     **/
    public abstract <T> T getPort(EndpointReference endpointReference,
            Class<T> serviceEndpointInterface,
            WebServiceFeature... features);

    /**
     * Factory method to create a {@code W3CEndpointReference}.
     *
     * <p>
     * This method can be used to create a {@code W3CEndpointReference}
     * for any endpoint by specifying the {@code address} property along
     * with any other desired properties.  This method
     * can also be used to create a {@code W3CEndpointReference} for
     * an endpoint that is published by the same Java EE application.
     * To do so the {@code address} property can be provided or this
     * method can automatically determine the {@code address} of
     * an endpoint that is published by the same Java EE application and is
     * identified by the {@code serviceName} and
     * {@code portName} properties.  If the {@code address} is
     * {@code null} and the {@code serviceName} and
     * {@code portName} do not identify an endpoint published by the
     * same Java EE application, a
     * {@code javax.lang.IllegalStateException} MUST be thrown.
     *
     * @param address Specifies the address of the target endpoint
     * @param serviceName Qualified name of the service in the WSDL.
     * @param portName Qualified name of the endpoint in the WSDL.
     * @param metadata A list of elements that should be added to the
     * {@code W3CEndpointReference} instances {@code wsa:metadata}
     * element.
     * @param wsdlDocumentLocation URL for the WSDL document location for
     * the service.
     * @param referenceParameters Reference parameters to be associated
     * with the returned {@code EndpointReference} instance.
     *
     * @return the {@code W3CEndpointReference} created from
     *          {@code serviceName}, {@code portName},
     *          {@code metadata}, {@code wsdlDocumentLocation}
     *          and {@code referenceParameters}. This method
     *          never returns {@code null}.
     *
     * @throws java.lang.IllegalStateException
     *     <ul>
     *        <li>If the {@code address}, {@code serviceName} and
     *            {@code portName} are all {@code null}.
     *        <li>If the {@code serviceName} service is {@code null} and the
     *            {@code portName} is NOT {@code null}.
     *        <li>If the {@code address} property is {@code null} and
     *            the {@code serviceName} and {@code portName} do not
     *            specify a valid endpoint published by the same Java EE
     *            application.
     *        <li>If the {@code serviceName}is NOT {@code null}
     *             and is not present in the specified WSDL.
     *        <li>If the {@code portName} port is not {@code null} and it
     *             is not present in {@code serviceName} service in the WSDL.
     *        <li>If the {@code wsdlDocumentLocation} is NOT {@code null}
     *            and does not represent a valid WSDL.
     *     </ul>
     * @throws WebServiceException If an error occurs while creating the
     *                             {@code W3CEndpointReference}.
     *
     * @since 1.6, JAX-WS 2.1
     */
    public abstract W3CEndpointReference createW3CEndpointReference(String address, QName serviceName, QName portName,
            List<Element> metadata, String wsdlDocumentLocation, List<Element> referenceParameters);


    /**
     * Factory method to create a {@code W3CEndpointReference}.
     * Using this method, a {@code W3CEndpointReference} instance
     * can be created with extension elements, and attributes.
     * {@code Provider} implementations must override the default
     * implementation.
     *
     * <p>
     * This method can be used to create a {@code W3CEndpointReference}
     * for any endpoint by specifying the {@code address} property along
     * with any other desired properties.  This method
     * can also be used to create a {@code W3CEndpointReference} for
     * an endpoint that is published by the same Java EE application.
     * To do so the {@code address} property can be provided or this
     * method can automatically determine the {@code address} of
     * an endpoint that is published by the same Java EE application and is
     * identified by the {@code serviceName} and
     * {@code portName} propeties.  If the {@code address} is
     * {@code null} and the {@code serviceName} and
     * {@code portName} do not identify an endpoint published by the
     * same Java EE application, a
     * {@code javax.lang.IllegalStateException} MUST be thrown.
     *
     * @param address Specifies the address of the target endpoint
     * @param interfaceName the {@code wsam:InterfaceName} element in the
     * {@code wsa:Metadata} element.
     * @param serviceName Qualified name of the service in the WSDL.
     * @param portName Qualified name of the endpoint in the WSDL.
     * @param metadata A list of elements that should be added to the
     * {@code W3CEndpointReference} instances {@code wsa:metadata}
     * element.
     * @param wsdlDocumentLocation URL for the WSDL document location for
     * the service.
     * @param referenceParameters Reference parameters to be associated
     * with the returned {@code EndpointReference} instance.
     * @param elements extension elements to be associated
     * with the returned {@code EndpointReference} instance.
     * @param attributes extension attributes to be associated
     * with the returned {@code EndpointReference} instance.
     *
     * @return the {@code W3CEndpointReference} created from
     *          {@code serviceName}, {@code portName},
     *          {@code metadata}, {@code wsdlDocumentLocation}
     *          and {@code referenceParameters}. This method
     *          never returns {@code null}.
     *
     * @throws java.lang.IllegalStateException
     *     <ul>
     *        <li>If the {@code address}, {@code serviceName} and
     *            {@code portName} are all {@code null}.
     *        <li>If the {@code serviceName} service is {@code null} and the
     *            {@code portName} is NOT {@code null}.
     *        <li>If the {@code address} property is {@code null} and
     *            the {@code serviceName} and {@code portName} do not
     *            specify a valid endpoint published by the same Java EE
     *            application.
     *        <li>If the {@code serviceName}is NOT {@code null}
     *             and is not present in the specified WSDL.
     *        <li>If the {@code portName} port is not {@code null} and it
     *             is not present in {@code serviceName} service in the WSDL.
     *        <li>If the {@code wsdlDocumentLocation} is NOT {@code null}
     *            and does not represent a valid WSDL.
     *        <li>If the {@code wsdlDocumentLocation} is NOT {@code null} but
     *            wsdli:wsdlLocation's namespace name cannot be got from the available
     *            metadata.
     *     </ul>
     * @throws WebServiceException If an error occurs while creating the
     *                             {@code W3CEndpointReference}.
     * @since 1.7, JAX-WS 2.2
     */
    public W3CEndpointReference createW3CEndpointReference(String address,
            QName interfaceName, QName serviceName, QName portName,
            List<Element> metadata, String wsdlDocumentLocation, List<Element> referenceParameters,
            List<Element> elements, Map<QName, String> attributes) {
        throw new UnsupportedOperationException("JAX-WS 2.2 implementation must override this default behaviour.");
    }

    /**
     * Creates and publishes an endpoint object with the specified
     * address, implementation object and web service features.
     * {@code Provider} implementations must override the
     * default implementation.
     *
     * @param address A URI specifying the address and transport/protocol
     *        to use. A http: URI MUST result in the SOAP 1.1/HTTP
     *        binding being used. Implementations may support other
     *        URI schemes.
     * @param implementor A service implementation object to which
     *        incoming requests will be dispatched. The corresponding
     *        class MUST be annotated with all the necessary Web service
     *        annotations.
     * @param features A list of WebServiceFeatures to configure on the
     *        endpoint.  Supported features not in the {@code features}
     *        parameter will have their default values.
     * @return The newly created endpoint.
     * @since 1.7, JAX-WS 2.2
     */
    public Endpoint createAndPublishEndpoint(String address,
            Object implementor, WebServiceFeature ... features) {
        throw new UnsupportedOperationException("JAX-WS 2.2 implementation must override this default behaviour.");
    }

    /**
     * Creates an endpoint object with the provided binding, implementation
     * object and web service features. {@code Provider} implementations
     * must override the default implementation.
     *
     * @param bindingId A URI specifying the desired binding (e.g. SOAP/HTTP)
     * @param implementor A service implementation object to which
     *        incoming requests will be dispatched. The corresponding
     *        class MUST be annotated with all the necessary Web service
     *        annotations.
     * @param features A list of WebServiceFeatures to configure on the
     *        endpoint.  Supported features not in the {@code features}
     *        parameter will have their default values.
     * @return The newly created endpoint.
     * @since 1.7, JAX-WS 2.2
     */
    public Endpoint createEndpoint(String bindingId, Object implementor,
            WebServiceFeature ... features) {
        throw new UnsupportedOperationException("JAX-WS 2.2 implementation must override this default behaviour.");
    }

    /**
     * Creates an endpoint object with the provided binding, implementation
     * class, invoker and web service features. Containers typically use
     * this to create Endpoint objects. {@code Provider}
     * implementations must override the default implementation.
     *
     * @param bindingId A URI specifying the desired binding (e.g. SOAP/HTTP).
     *        Can be null.
     * @param implementorClass A service implementation class that
     *        MUST be annotated with all the necessary Web service
     *        annotations.
     * @param invoker that does the actual invocation on the service instance.
     * @param features A list of WebServiceFeatures to configure on the
     *        endpoint.  Supported features not in the {@code features
     *        } parameter will have their default values.
     * @return The newly created endpoint.
     * @since 1.7, JAX-WS 2.2
     */
    public Endpoint createEndpoint(String bindingId, Class<?> implementorClass,
            Invoker invoker, WebServiceFeature ... features) {
        throw new UnsupportedOperationException("JAX-WS 2.2 implementation must override this default behaviour.");
    }

}
