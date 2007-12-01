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

import java.util.Iterator;
import javax.xml.namespace.QName;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.handler.HandlerResolver;
import javax.xml.bind.JAXBContext;

/**
 * Service delegates are used internally by <code>Service</code> objects
 * to allow pluggability of JAX-WS implementations.
 * <p>
 * Every <code>Service</code> object has its own delegate, created using
 * the javax.xml.ws.Provider#createServiceDelegate method. A <code>Service</code>
 * object delegates all of its instance methods to its delegate.
 *
 * @see javax.xml.ws.Service
 * @see javax.xml.ws.spi.Provider
 *
 * @since JAX-WS 2.0
 */
public abstract class ServiceDelegate {

    protected ServiceDelegate() {
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


  public abstract <T> T getPort(QName portName,
                                 Class<T> serviceEndpointInterface);


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
  public abstract <T> T getPort(Class<T> serviceEndpointInterface);

  /** Creates a new port for the service. Ports created in this way contain
   *  no WSDL port type information and can only be used for creating
   *  <code>Dispatch</code>instances.
   *
   *  @param portName  Qualified name for the target service endpoint
   *  @param bindingId A URI identifier of a binding.
   *  @param endpointAddress Address of the target service endpoint as a URI
   *  @throws WebServiceException If any error in the creation of
   *  the port
   *
   *  @see javax.xml.ws.soap.SOAPBinding#SOAP11HTTP_BINDING
   *  @see javax.xml.ws.soap.SOAPBinding#SOAP12HTTP_BINDING
   *  @see javax.xml.ws.http.HTTPBinding#HTTP_BINDING
   **/
  public abstract void addPort(QName portName, String bindingId,
      String endpointAddress);

  /** Creates a <code>Dispatch</code> instance for use with objects of
   *  the users choosing.
   *
   *  @param portName  Qualified name for the target service endpoint
   *  @param type The class of object used to messages or message
   *  payloads. Implementations are required to support
   *  javax.xml.transform.Source and javax.xml.soap.SOAPMessage.
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
  public abstract <T> Dispatch<T> createDispatch(QName portName, Class<T> type, Service.Mode mode);

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
  public abstract Dispatch<Object> createDispatch(QName portName,
              JAXBContext context, Service.Mode mode);


  /** Gets the name of this service.
   *  @return Qualified name of this service
  **/
  public abstract QName getServiceName();

  /** Returns an <code>Iterator</code> for the list of
   *  <code>QName</code>s of service endpoints grouped by this
   *  service
   *
   *  @return Returns <code>java.util.Iterator</code> with elements
   *          of type <code>javax.xml.namespace.QName</code>
   *  @throws WebServiceException If this Service class does not
   *          have access to the required WSDL metadata
  **/
  public abstract Iterator<javax.xml.namespace.QName> getPorts();

  /** Gets the location of the WSDL document for this Service.
   *
   *  @return URL for the location of the WSDL document for
   *          this service
  **/
  public abstract java.net.URL getWSDLDocumentLocation();

  /**
   * Returns the configured handler resolver.
   *
   *  @return HandlerResolver The <code>HandlerResolver</code> being
   *          used by this <code>Service</code> instance, or <code>null</code>
   *          if there isn't one.
  **/
  public abstract HandlerResolver getHandlerResolver();

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
  public abstract void setHandlerResolver(HandlerResolver handlerResolver);

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
   public abstract java.util.concurrent.Executor getExecutor();

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
   public abstract void setExecutor(java.util.concurrent.Executor executor);

}
