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

package javax.xml.ws;

import java.util.List;
import java.util.Map;
import javax.xml.ws.spi.Provider;

/**
 * A Web service endpoint.
 *
 * <p>Endpoints are created using the static methods defined in this
 * class. An endpoint is always tied to one <code>Binding</code>
 * and one implementor, both set at endpoint creation time.
 *
 * <p>An endpoint is either in a published or an unpublished state.
 * The <code>publish</code> methods can be used to start publishing
 * an endpoint, at which point it starts accepting incoming requests.
 * Conversely, the <code>stop</code> method can be used to stop
 * accepting incoming requests and take the endpoint down.
 * Once stopped, an endpoint cannot be published again.
 *
 * <p>An <code>Executor</code> may be set on the endpoint in order
 * to gain better control over the threads used to dispatch incoming
 * requests. For instance, thread pooling with certain parameters
 * can be enabled by creating a <code>ThreadPoolExecutor</code> and
 * registering it with the endpoint.
 *
 * <p>Handler chains can be set using the contained <code>Binding</code>.
 *
 * <p>An endpoint may have a list of metadata documents, such as WSDL
 * and XMLSchema documents, bound to it. At publishing time, the
 * JAX-WS implementation will try to reuse as much of that metadata
 * as possible instead of generating new one based on the annotations
 * present on the implementor.
 *
 * @since JAX-WS 2.0
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
   * one of the javax.xml.ws.Endpoint#publish(String) and
   * javax.xml.ws.Endpoint#publish(Object) methods.
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
   * Creates an endpoint with the specified binding type and
   * implementor object.
   * <p>
   * The newly created endpoint may be published by calling
   * one of the javax.xml.ws.Endpoint#publish(String) and
   * javax.xml.ws.Endpoint#publish(Object) methods.
   *
   * @param bindingId A URI specifying the binding to use. If the bindingID is
   * <code>null</code> and no binding is specified via a BindingType
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
   * use the javax.xml.ws.Endpoint#publish(Object) method instead.
   *
   * @param address A URI specifying the address to use. The address
   *        must be compatible with the binding specified at the
   *        time the endpoint was created.
   *
   * @throws java.lang.IllegalArgumentException
   *              If the provided address URI is not usable
   *              in conjunction with the endpoint's binding.
   *
   * @throws java.lang.IllegalStateException
   *         If the endpoint has been published already or it has been stopped.
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
   * use the javax.xml.ws.Endpoint#create(String,Object) and
   * javax.xml.ws.Endpoint#publish(Object) method instead.
   *
   * @param address A URI specifying the address and transport/protocol
   *        to use. A http: URI must result in the SOAP 1.1/HTTP
   *        binding being used. Implementations may support other
   *        URI schemes.
   * @param implementor The endpoint implementor.
   *
   * @return The newly created endpoint.
   *
   **/
  public static Endpoint publish (String address, Object implementor) {
    return Provider.provider().createAndPublishEndpoint(address, implementor);
  }

  /**
   * Publishes this endpoint at the provided server context.
   * A server context encapsulates the server infrastructure
   * and addressing information for a particular transport.
   * For a call to this method to succeed, the server context
   * passed as an argument to it must be compatible with the
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
  **/
  public abstract void publish(Object serverContext);

  /**
   * Stops publishing this endpoint.
   *
   * If the endpoint is not in a published state, this method
   * has not effect.
   *
  **/
  public abstract void stop();

  /**
   * Returns true if the endpoint is in the published state.
   *
   * @return <code>true</code> if the endpoint is in the published state.
  **/
  public abstract boolean isPublished();

  /**
   * Returns a list of metadata documents for the service.
   *
   * @return <code>List&lt;javax.xml.transform.Source&gt;</code> A list of metadata documents for the service
  **/
  public abstract List<javax.xml.transform.Source> getMetadata();

  /**
   * Sets the metadata for this endpoint.
   *
   * @param metadata A list of XML document sources containing
   *           metadata information for the endpoint (e.g.
   *           WSDL or XML Schema documents)
   *
   * @throws java.lang.IllegalStateException If the endpoint
   *         has already been published.
  **/
  public abstract void setMetadata(List<javax.xml.transform.Source> metadata);

  /**
   * Returns the executor for this <code>Endpoint</code>instance.
   *
   * The executor is used to dispatch an incoming request to
   * the implementor object.
   *
   * @return The <code>java.util.concurrent.Executor</code> to be
   *         used to dispatch a request.
   *
   * @see java.util.concurrent.Executor
   **/
   public abstract java.util.concurrent.Executor getExecutor();

  /**
   * Sets the executor for this <code>Endpoint</code> instance.
   *
   * The executor is used to dispatch an incoming request to
   * the implementor object.
   *
   * If this <code>Endpoint</code> is published using the
   * <code>publish(Object)</code> method and the specified server
   * context defines its own threading behavior, the executor
   * may be ignored.
   *
   * @param executor The <code>java.util.concurrent.Executor</code>
   *        to be used to dispatch a request.
   *
   * @throws SecurityException If the instance does not support
   *         setting an executor for security reasons (e.g. the
   *         necessary permissions are missing).
   *
   * @see java.util.concurrent.Executor
   **/
   public abstract void setExecutor(java.util.concurrent.Executor executor);


    /**
     * Returns the property bag for this <code>Endpoint</code> instance.
     *
     * @return Map&lt;String,Object&gt; The property bag
     *         associated with this instance.
     **/
     public abstract Map<String,Object> getProperties();

    /**
     * Sets the property bag for this <code>Endpoint</code> instance.
     *
     * @param properties The property bag associated with
     *        this instance.
     **/
     public abstract void setProperties(Map<String,Object> properties);
}
