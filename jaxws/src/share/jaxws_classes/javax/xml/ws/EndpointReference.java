/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.spi.Provider;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.io.StringWriter;

/**
 * This class represents an WS-Addressing EndpointReference
 * which is a remote reference to a web service endpoint.
 * See <a href="http://www.w3.org/TR/2006/REC-ws-addr-core-20060509/">
 * Web Services Addressing 1.0 - Core</a>
 * for more information on WS-Addressing EndpointReferences.
 * <p>
 * This class is immutable as the typical web service developer
 * need not be concerned with its contents.  The web service
 * developer should use this class strictly as a mechanism to
 * reference a remote web service endpoint. See the {@link Service} APIs
 * that clients can use to that utilize an <code>EndpointReference</code>.
 * See the {@link javax.xml.ws.Endpoint}, and
 * {@link javax.xml.ws.BindingProvider} APIs on how
 * <code>EndpointReferences</code> can be created for published
 * endpoints.
 * <p>
 * Concrete implementations of this class will represent
 * an <code>EndpointReference</code> for a particular version of Addressing.
 * For example the {@link W3CEndpointReference} is for use
 * with W3C Web Services Addressing 1.0 - Core Recommendation.
 * If JAX-WS implementors need to support different versions
 * of addressing, they should write their own
 * <code>EndpointReference</code> subclass for that version.
 * This will allow a JAX-WS implementation to create
 * a vendor specific <code>EndpointReferences</code> that the
 * vendor can use to flag a different version of
 * addressing.
 * <p>
 * Web service developers that wish to pass or return
 * <code>EndpointReference</code> in Java methods in an
 * SEI should use
 * concrete instances of an <code>EndpointReference</code> such
 * as the <code>W3CEndpointReference</code>.  This way the
 * schema mapped from the SEI will be more descriptive of the
 * type of endpoint reference being passed.
 * <p>
 * JAX-WS implementors are expected to extract the XML infoset
 * from an <CODE>EndpointReferece</CODE> using the
 * <code>{@link EndpointReference#writeTo}</code>
 * method.
 * <p>
 * JAXB will bind this class to xs:anyType. If a better binding
 * is desired, web services developers should use a concrete
 * subclass such as {@link W3CEndpointReference}.
 *
 * @see W3CEndpointReference
 * @see Service
 * @since JAX-WS 2.1
 */
@XmlTransient // to treat this class like Object as far as databinding is concerned (proposed JAXB 2.1 feature)
public abstract class EndpointReference {
    //
    //Default constructor to be only called by derived types.
    //
    protected EndpointReference(){}

    /**
     * Factory method to read an EndpointReference from the infoset contained in
     * <code>eprInfoset</code>. This method delegates to the vendor specific
     * implementation of the {@link javax.xml.ws.spi.Provider#readEndpointReference} method.
     *
     * @param eprInfoset The <code>EndpointReference</code> infoset to be unmarshalled
     *
     * @return the EndpointReference unmarshalled from <code>eprInfoset</code>
     *    never <code>null</code>
     * @throws WebServiceException
     *    if an error occurs while creating the
     *    <code>EndpointReference</code> from the <CODE>eprInfoset</CODE>
     * @throws java.lang.IllegalArgumentException
     *     if the <code>null</code> <code>eprInfoset</code> value is given.
     */
    public static EndpointReference readFrom(Source eprInfoset) {
        return Provider.provider().readEndpointReference(eprInfoset);
    }

    /**
     * write this <code>EndpointReference</code> to the specified infoset format
     *
     * @param result for writing infoset
     * @throws WebServiceException
     *   if there is an error writing the
     *   <code>EndpointReference</code> to the specified <code>result</code>.
     *
     * @throws java.lang.IllegalArgumentException
     *      If the <code>null</code> <code>result</code> value is given.
     */
    public abstract void writeTo(Result result);


    /**
     * The <code>getPort</code> method returns a proxy. If there
     * are any reference parameters in the
     * <code>EndpointReference</code> instance, then those reference
     * parameters MUST appear as SOAP headers, indicating them to be
     * reference parameters, on all messages sent to the endpoint.
     * The parameter  <code>serviceEndpointInterface</code> specifies
     * the service endpoint interface that is supported by the
     * returned proxy.
     * The <code>EndpointReference</code> instance specifies the
     * endpoint that will be invoked by the returned proxy.
     * In the implementation of this method, the JAX-WS
     * runtime system takes the responsibility of selecting a protocol
     * binding (and a port) and configuring the proxy accordingly from
     * the WSDL Metadata from this <code>EndpointReference</code> or from
     * annotations on the <code>serviceEndpointInterface</code>.  For this method
     * to successfully return a proxy, WSDL metadata MUST be available and the
     * <code>EndpointReference</code> instance MUST contain an implementation understood
     * <code>serviceName</code> metadata.
     * <p>
     * Because this port is not created from a <code>Service</code> object, handlers
     * will not automatically be configured, and the <code>HandlerResolver</code>
     * and <code>Executor</code> cannot be get or set for this port. The
     * <code>BindingProvider().getBinding().setHandlerChain()</code>
     * method can be used to manually configure handlers for this port.
     *
     *
     * @param serviceEndpointInterface Service endpoint interface
     * @param features  An array of <code>WebServiceFeatures</code> to configure on the
     *                proxy.  Supported features not in the <code>features
     *                </code> parameter will have their default values.
     * @return Object Proxy instance that supports the
     *                  specified service endpoint interface
     * @throws WebServiceException
     *                  <UL>
     *                  <LI>If there is an error during creation
     *                      of the proxy
     *                  <LI>If there is any missing WSDL metadata
     *                      as required by this method
     *                  <LI>If this
     *                      <code>endpointReference</code>
     *                      is invalid
     *                  <LI>If an illegal
     *                      <code>serviceEndpointInterface</code>
     *                      is specified
     *                  <LI>If a feature is enabled that is not compatible with
     *                      this port or is unsupported.
     *                   </UL>
     *
     * @see java.lang.reflect.Proxy
     * @see WebServiceFeature
     **/
    public <T> T getPort(Class<T> serviceEndpointInterface,
                         WebServiceFeature... features) {
        return Provider.provider().getPort(this, serviceEndpointInterface,
                                           features);
    }

    /**
     * Displays EPR infoset for debugging convenience.
     */
    public String toString() {
        StringWriter w = new StringWriter();
        writeTo(new StreamResult(w));
        return w.toString();
    }
}
