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

package javax.xml.ws.spi;

import java.net.URL;
import java.util.List;
import javax.xml.ws.Endpoint;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.namespace.QName;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

import org.w3c.dom.Element;

/**
 * Service provider for <code>ServiceDelegate</code> and
 * <code>Endpoint</code> objects.
 * <p>
 *
 * @since JAX-WS 2.0
 */
public abstract class Provider {

    /**
     * A constant representing the property used to lookup the
     * name of a <code>Provider</code> implementation
     * class.
     */
    static public final String JAXWSPROVIDER_PROPERTY
            = "javax.xml.ws.spi.Provider";

    /**
     * A constant representing the name of the default
     * <code>Provider</code> implementation class.
     **/
    static private final String DEFAULT_JAXWSPROVIDER
            = "com.sun.xml.internal.ws.spi.ProviderImpl";


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
     * <p>
     * <ul>
     * <li>
     *   If a resource with the name of
     *   <code>META-INF/services/javax.xml.ws.spi.Provider</code>
     *   exists, then its first line, if present, is used as the UTF-8 encoded
     *   name of the implementation class.
     * </li>
     * <li>
     *   If the $java.home/lib/jaxws.properties file exists and it is readable by
     *   the <code>java.util.Properties.load(InputStream)</code> method and it contains
     *   an entry whose key is <code>javax.xml.ws.spi.Provider</code>, then the value of
     *   that entry is used as the name of the implementation class.
     * </li>
     * <li>
     *   If a system property with the name <code>javax.xml.ws.spi.Provider</code>
     *   is defined, then its value is used as the name of the implementation class.
     * </li>
     * <li>
     *   Finally, a default implementation class name is used.
     * </li>
     * </ul>
     *
     */
    public static Provider provider() {
        try {
            Object provider =
                    FactoryFinder.find(JAXWSPROVIDER_PROPERTY,
                    DEFAULT_JAXWSPROVIDER);
            if (!(provider instanceof Provider)) {
                Class pClass = Provider.class;
                String classnameAsResource = pClass.getName().replace('.', '/') + ".class";
                ClassLoader loader = pClass.getClassLoader();
                if(loader == null) {
                    loader = ClassLoader.getSystemClassLoader();
                }
                URL targetTypeURL  = loader.getResource(classnameAsResource);
                throw new LinkageError("ClassCastException: attempting to cast" +
                       provider.getClass().getClassLoader().getResource(classnameAsResource) +
                       "to" + targetTypeURL.toString() );
            }
            return (Provider) provider;
        } catch (WebServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WebServiceException("Unable to createEndpointReference Provider", ex);
        }
    }

    /**
     * Creates a service delegate object.
     * <p>
     * @param wsdlDocumentLocation A URL pointing to the WSDL document
     *        for the service, or <code>null</code> if there isn't one.
     * @param serviceName The qualified name of the service.
     * @param serviceClass The service class, which MUST be either
     *        <code>javax.xml.ws.Service</code> or a subclass thereof.
     * @return The newly created service delegate.
     */
    public abstract ServiceDelegate createServiceDelegate(
            java.net.URL wsdlDocumentLocation,
            QName serviceName, Class serviceClass);


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
     * <code>eprInfoset</code>.
     *
     * @return the <code>EndpointReference</code> unmarshalled from
     * <code>eprInfoset</code>.  This method never returns <code>null</code>.
     *
     * @throws WebServiceException If there is an error creating the
     * <code>EndpointReference</code> from the specified <code>eprInfoset</code>.
     *
     * @throws NullPointerException If the <code>null</code>
     * <code>eprInfoset</code> value is given.
     *
     * @since JAX-WS 2.1
     **/
    public abstract EndpointReference readEndpointReference(javax.xml.transform.Source eprInfoset);


    /**
     * The getPort method returns a proxy.  If there
     * are any reference parameters in the
     * <code>endpointReference</code>, then those reference
     * parameters MUST appear as SOAP headers, indicating them to be
     * reference parameters, on all messages sent to the endpoint.
     * The parameter  <code>serviceEndpointInterface</code> specifies
     * the service endpoint interface that is supported by the
     * returned proxy.
     * The parameter <code>endpointReference</code> specifies the
     * endpoint that will be invoked by the returned proxy.
     * In the implementation of this method, the JAX-WS
     * runtime system takes the responsibility of selecting a protocol
     * binding (and a port) and configuring the proxy accordingly from
     * the WSDL metadata of the
     * <code>serviceEndpointInterface</code> and the <code>EndpointReference</code>.
     * For this method
     * to successfully return a proxy, WSDL metadata MUST be available and the
     * <code>endpointReference</code> MUST contain an implementation understood
     * <code>serviceName</code> metadata.
     *
     *
     * @param endpointReference the EndpointReference that will
     * be invoked by the returned proxy.
     * @param serviceEndpointInterface Service endpoint interface
     * @param features  A list of WebServiceFeatures to configure on the
     *                proxy.  Supported features not in the <code>features
     *                </code> parameter will have their default values.
     * @return Object Proxy instance that supports the
     *                  specified service endpoint interface
     * @throws WebServiceException
     *                  <UL>
     *                  <LI>If there is an error during creation
     *                      of the proxy
     *                  <LI>If there is any missing WSDL metadata
     *                      as required by this method}
     *                  <LI>If this
     *                      <code>endpointReference</code>
     *                      is illegal
     *                  <LI>If an illegal
     *                      <code>serviceEndpointInterface</code>
     *                      is specified
     *                  <LI>If a feature is enabled that is not compatible with
     *                      this port or is unsupported.
     *                   </UL>
     *
     * @see WebServiceFeature
     *
     * @since JAX-WS 2.1
     **/
    public abstract <T> T getPort(EndpointReference endpointReference,
            Class<T> serviceEndpointInterface,
            WebServiceFeature... features);

    /**
     * Factory method to create a <code>W3CEndpointReference</code>.
     *
     * <p>
     * This method can be used to create a <code>W3CEndpointReference</code>
     * for any endpoint by specifying the <code>address</code> property along
     * with any other desired properties.  This method
     * can also be used to create a <code>W3CEndpointReference</code> for
     * an endpoint that is published by the same Java EE application.
     * To do so the <code>address</code> property can be provided or this
     * method can automatically determine the <code>address</code> of
     * an endpoint that is published by the same Java EE application and is
     * identified by the <code>serviceName</code> and
     * <code>portName</code> propeties.  If the <code>address</code> is
     * <code>null</code> and the <code>serviceName</code> and
     * <code>portName</code> do not identify an endpoint published by the
     * same Java EE application, a
     * <code>javax.lang.IllegalStateException</code> MUST be thrown.
     *
     * @param address Specifies the address of the target endpoint
     * @param serviceName Qualified name of the service in the WSDL.
     * @param portName Qualified name of the endpoint in the WSDL.
     * @param metadata A list of elements that should be added to the
     * <code>W3CEndpointReference</code> instances <code>wsa:metadata</code>
     * element.
     * @param wsdlDocumentLocation URL for the WSDL document location for
     * the service.
     * @param referenceParameters Reference parameters to be associated
     * with the returned <code>EndpointReference</code> instance.
     *
     * @return the <code>W3CEndpointReference<code> created from
     *          <code>serviceName</code>, <code>portName</code>,
     *          <code>metadata</code>, <code>wsdlDocumentLocation</code>
     *          and <code>referenceParameters</code>. This method
     *          never returns <code>null</code>.
     *
     * @throws java.lang.IllegalStateException
     *     <ul>
     *        <li>If the <code>address</code>, <code>serviceName</code> and
     *            <code>portName</code> are all <code>null</code>.
     *        <li>If the <code>serviceName</code> service is <code>null</code> and the
     *            <code>portName> is NOT <code>null</code>.
     *        <li>If the <code>address</code> property is <code>null</code> and
     *            the <code>serviceName</code> and <code>portName</code> do not
     *            specify a valid endpoint published by the same Java EE
     *            application.
     *        <li>If the <code>serviceName</code>is NOT <code>null</code>
     *             and is not present in the specified WSDL.
     *        <li>If the <code>portName</code> port is not <code>null<code> and it
     *             is not present in <code>serviceName</code> service in the WSDL.
     *        <li>If the <code>wsdlDocumentLocation</code> is NOT <code>null</code>
     *            and does not represent a valid WSDL.
     *     </ul>
     * @throws WebServiceException If an error occurs while creating the
     *                             <code>W3CEndpointReference</code>.
     *
     * @since JAX-WS 2.1
     */
    public abstract W3CEndpointReference createW3CEndpointReference(String address, QName serviceName, QName portName,
            List<Element> metadata, String wsdlDocumentLocation, List<Element> referenceParameters);
}
