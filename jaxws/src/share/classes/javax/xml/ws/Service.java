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

import javax.xml.namespace.QName;
import java.util.Iterator;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.bind.JAXBContext;
import javax.xml.ws.spi.ServiceDelegate;
import javax.xml.ws.spi.Provider;

/**
 *  <code>Service</code> objects provide the client view of a Web service.
 *  <p><code>Service</code> acts as a factory of the following:
 *  <ul>
 *  <li>Proxies for a target service endpoint.
 *  <li>Instances of <code>javax.xml.ws.Dispatch</code> for
 *      dynamic message-oriented invocation of a remote
 *      operation.
 *  </li>
 *
 * <p>The ports available on a service can be enumerated using the
 * <code>getPorts</code> method. Alternatively, you can pass a
 * service endpoint interface to the unary <code>getPort</code> method
 * and let the runtime select a compatible port.
 *
 * <p>Handler chains for all the objects created by a <code>Service</code>
 * can be set by means of a <code>HandlerResolver</code>.
 *
 * <p>An <code>Executor</code> may be set on the service in order
 * to gain better control over the threads used to dispatch asynchronous
 * callbacks. For instance, thread pooling with certain parameters
 * can be enabled by creating a <code>ThreadPoolExecutor</code> and
 * registering it with the service.
 *
 *  @since JAX-WS 2.0
 *
 *  @see javax.xml.ws.spi.Provider
 *  @see javax.xml.ws.handler.HandlerResolver
 *  @see java.util.concurrent.Executor
**/
public class Service {

  private ServiceDelegate delegate;
  /**
   * The orientation of a dynamic client or service. MESSAGE provides
   * access to entire protocol message, PAYLOAD to protocol message
   * payload only.
  **/
  public enum Mode { MESSAGE, PAYLOAD };

  protected Service(java.net.URL wsdlDocumentLocation, QName serviceName)
  {
      delegate = Provider.provider().createServiceDelegate(wsdlDocumentLocation,
                                                           serviceName,
                                                           this.getClass());
  }


  /** The getPort method returns a stub. A service client
   *  uses this stub to invoke operations on the target
   *  service endpoint. The <code>serviceEndpointInterface</code>
   *  specifies the service endpoint interface that is supported by
   *  the created dynamic proxy or stub instance.
   *
   *  @param portName  Qualified name of the service endpoint in
   *                   the WSDL service description
   *  @param serviceEndpointInterface Service endpoint interface
   *                   supported by the dynamic proxy or stub
   *                   instance
   *  @return Object Proxy instance that
   *                 supports the specified service endpoint
   *                 interface
   *  @throws WebServiceException This exception is thrown in the
   *                   following cases:
   *                   <UL>
   *                   <LI>If there is an error in creation of
   *                       the proxy
   *                   <LI>If there is any missing WSDL metadata
   *                       as required by this method
   *                   <LI>Optionally, if an illegal
   *                       <code>serviceEndpointInterface</code>
   *                       or <code>portName</code> is specified
   *                   </UL>
   *  @see java.lang.reflect.Proxy
   *  @see java.lang.reflect.InvocationHandler
  **/
  public <T> T getPort(QName portName,
                                 Class<T> serviceEndpointInterface)
  {
      return delegate.getPort(portName, serviceEndpointInterface);
  }

  /** The getPort method returns a stub. The parameter
   *  <code>serviceEndpointInterface</code> specifies the service
   *  endpoint interface that is supported by the returned proxy.
   *  In the implementation of this method, the JAX-WS
   *  runtime system takes the responsibility of selecting a protocol
   *  binding (and a port) and configuring the proxy accordingly.
   *  The returned proxy should not be reconfigured by the client.
   *
   *  @param serviceEndpointInterface Service endpoint interface
   *  @return Object instance that supports the
   *                   specified service endpoint interface
   *  @throws WebServiceException
   *                   <UL>
   *                   <LI>If there is an error during creation
   *                       of the proxy
   *                   <LI>If there is any missing WSDL metadata
   *                       as required by this method
   *                   <LI>Optionally, if an illegal
   *                       <code>serviceEndpointInterface</code>
   *                       is specified
   *                   </UL>
  **/
  public <T> T getPort(Class<T> serviceEndpointInterface) {
      return delegate.getPort(serviceEndpointInterface);
  }

  /** Creates a new port for the service. Ports created in this way contain
   *  no WSDL port type information and can only be used for creating
   *  <code>Dispatch</code>instances.
   *
   *  @param portName  Qualified name for the target service endpoint
   *  @param bindingId A String identifier of a binding.
   *  @param endpointAddress Address of the target service endpoint as a URI
   *  @throws WebServiceException If any error in the creation of
   *  the port
   *
   *  @see javax.xml.ws.soap.SOAPBinding#SOAP11HTTP_BINDING
   *  @see javax.xml.ws.soap.SOAPBinding#SOAP12HTTP_BINDING
   *  @see javax.xml.ws.http.HTTPBinding#HTTP_BINDING
   **/
  public void addPort(QName portName, String bindingId, String endpointAddress)
  {
      delegate.addPort(portName, bindingId, endpointAddress);
  }

  /** Creates a <code>Dispatch</code> instance for use with objects of
   *  the users choosing.
   *
   *  @param portName  Qualified name for the target service endpoint
   *  @param type The class of object used to messages or message
   *  payloads. Implementations are required to support
   *  <code>javax.xml.transform.Source</code>, <code>javax.xml.soap.SOAPMessage</code>
   *  and <code>javax.activation.DataSource</code>, depending on
   *  the binding in use.
   *  @param mode Controls whether the created dispatch instance is message
   *  or payload oriented, i.e. whether the user will work with complete
   *  protocol messages or message payloads. E.g. when using the SOAP
   *  protocol, this parameter controls whether the user will work with
   *  SOAP messages or the contents of a SOAP body. Mode must be MESSAGE
   *  when type is SOAPMessage.
   *
   *  @return Dispatch instance
   *  @throws WebServiceException If any error in the creation of
   *                   the <code>Dispatch</code> object
   *  @see javax.xml.transform.Source
   *  @see javax.xml.soap.SOAPMessage
   **/
  public <T> Dispatch<T> createDispatch(QName portName, Class<T> type, Mode mode)
  {
      return delegate.createDispatch(portName, type, mode);
  }

  /** Creates a <code>Dispatch</code> instance for use with JAXB
   *  generated objects.
   *
   *  @param portName  Qualified name for the target service endpoint
   *  @param context The JAXB context used to marshall and unmarshall
   *  messages or message payloads.
   *  @param mode Controls whether the created dispatch instance is message
   *  or payload oriented, i.e. whether the user will work with complete
   *  protocol messages or message payloads. E.g. when using the SOAP
   *  protocol, this parameter controls whether the user will work with
   *  SOAP messages or the contents of a SOAP body.
   *
   *  @return Dispatch instance
   *  @throws ServiceException If any error in the creation of
   *                   the <code>Dispatch</code> object
   *
   *  @see javax.xml.bind.JAXBContext
   **/
  public Dispatch<Object> createDispatch(QName portName, JAXBContext context,
      Mode mode)
  {
      return delegate.createDispatch(portName, context,  mode);
  }



  /** Gets the name of this service.
   *  @return Qualified name of this service
  **/
  public QName getServiceName() {
      return delegate.getServiceName();
  }

  /** Returns an <code>Iterator</code> for the list of
   *  <code>QName</code>s of service endpoints grouped by this
   *  service
   *
   *  @return Returns <code>java.util.Iterator</code> with elements
   *          of type <code>javax.xml.namespace.QName</code>
   *  @throws WebServiceException If this Service class does not
   *          have access to the required WSDL metadata
  **/
  public Iterator<javax.xml.namespace.QName> getPorts() {
      return delegate.getPorts();
  }

  /** Gets the location of the WSDL document for this Service.
   *
   *  @return URL for the location of the WSDL document for
   *          this service
  **/
  public java.net.URL getWSDLDocumentLocation() {
      return delegate.getWSDLDocumentLocation();
  }

  /**
   * Returns the configured handler resolver.
   *
   *  @return HandlerResolver The <code>HandlerResolver</code> being
   *          used by this <code>Service</code> instance, or <code>null</code>
   *          if there isn't one.
  **/
    public HandlerResolver getHandlerResolver() {
        return delegate.getHandlerResolver();
    }

  /**
   *  Sets the <code>HandlerResolver</code> for this <code>Service</code>
   *  instance.
   *  <p>
   *  The handler resolver, if present, will be called once for each
   *  proxy or dispatch instance that is created, and the handler chain
   *  returned by the resolver will be set on the instance.
   *
   *  @param handlerResolver The <code>HandlerResolver</code> to use
   *         for all subsequently created proxy/dispatch objects.
   *
   *  @see javax.xml.ws.handler.HandlerResolver
  **/
   public void setHandlerResolver(HandlerResolver handlerResolver) {
       delegate.setHandlerResolver(handlerResolver);
   }

  /**
   * Returns the executor for this <code>Service</code>instance.
   *
   * The executor is used for all asynchronous invocations that
   * require callbacks.
   *
   * @return The <code>java.util.concurrent.Executor</code> to be
   *         used to invoke a callback.
   *
   * @see java.util.concurrent.Executor
   **/
   public java.util.concurrent.Executor getExecutor() {
       return delegate.getExecutor();
   }

  /**
   * Sets the executor for this <code>Service</code> instance.
   *
   * The executor is used for all asynchronous invocations that
   * require callbacks.
   *
   * @param executor The <code>java.util.concurrent.Executor</code>
   *        to be used to invoke a callback.
   *
   * @throws SecurityException If the instance does not support
   *         setting an executor for security reasons (e.g. the
   *         necessary permissions are missing).
   *
   * @see java.util.concurrent.Executor
   **/
   public void setExecutor(java.util.concurrent.Executor executor) {
       delegate.setExecutor(executor);
   }

  /**
   *  Create a <code>Service</code> instance.
   *
   *  The specified WSDL document location and service qualified name must
   *  uniquely identify a <code>wsdl:service</code> element.
   *
   *  @param wsdlDocumentLocation URL for the WSDL document location
   *                              for the service
   *  @param serviceName QName for the service
   *  @throws WebServiceException If any error in creation of the
   *                     specified service
  **/
  public static Service create(
                            java.net.URL wsdlDocumentLocation,
                            QName serviceName)
  {
      return new Service(wsdlDocumentLocation, serviceName);
  }

  /**
   *  Create a <code>Service</code> instance.
   *
   *  @param serviceName QName for the service
   *  @throws WebServiceException If any error in creation of the
   *                     specified service
   */
  public static Service create(QName serviceName) {
      return new Service(null, serviceName);
  }
}
