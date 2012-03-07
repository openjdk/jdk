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

package javax.xml.ws.wsaddressing;


import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.spi.Provider;


/**
 * This class is used to build <code>W3CEndpointReference</code>
 * instances. The intended use of this clsss is for
 * an application component, for example a factory component,
 * to create an <code>W3CEndpointReference</code> for a
 * web service endpoint published by the same
 * Java EE application. It can also be used to create
 * <code>W3CEndpointReferences</code> for an Java SE based
 * endpoint by providing the <code>address</code> property.
 * <p>
 * When creating a <code>W3CEndpointReference</code> for an
 * endpoint that is not published by the same Java EE application,
 * the <code>address</code> property MUST be specified.
 * <p>
 * When creating a <code>W3CEndpointReference</code> for an endpoint
 * published by the same Java EE application, the <code>address</code>
 * property MAY be <code>null</code> but then the <code>serviceName</code>
 * and <code>endpointName</code> MUST specify an endpoint published by
 * the same Java EE application.
 * <p>
 * When the <code>wsdlDocumentLocation</code> is specified it MUST refer
 * to a valid WSDL document and the <code>serviceName</code> and
 * <code>endpointName</code> (if specified) MUST match a service and port
 * in the WSDL document.
 *
 * @since JAX-WS 2.1
 */
public final class W3CEndpointReferenceBuilder {
    /**
     * Creates a new <code>W3CEndpointReferenceBuilder</code> instance.
     */
    public W3CEndpointReferenceBuilder() {
        referenceParameters = new ArrayList<Element>();
        metadata = new ArrayList<Element>();
        attributes = new HashMap<QName, String>();
        elements = new ArrayList<Element>();
    }

    /**
     * Sets the <code>address</code> to the
     * <code>W3CEndpointReference</code> instance's
     * <code>wsa:Address</code>.
     * <p>
     * The <code>address</code> MUST be set to a non-<code>null</code>
     * value when building a <code>W3CEndpointReference</code> for a
     * web service endpoint that is not published by the same
     * Java EE application or when running on Java SE.
     *
     * @param address The address of the endpoint to be targeted
     *      by the returned <code>W3CEndpointReference</code>.
     *
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the <code>address</code> set to the <code>wsa:Address</code>.
     */
    public W3CEndpointReferenceBuilder address(String address) {
        this.address = address;
        return this;
    }

    /**
     * Sets the <code>interfaceName</code> as the
     * <code>wsam:InterfaceName</code> element in the
     * <code>wsa:Metadata</code> element.
     *
     * See <a href="http://www.w3.org/TR/2007/REC-ws-addr-metadata-20070904/#refmetadatfromepr">
     * 2.1 Referencing WSDL Metadata from an EPR</a> for more details.
     *
     * @param interfaceName The port type name of the endpoint to be targeted
     *      by the returned <code>W3CEndpointReference</code>.
     *
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the <code>interfaceName</code> as <code>wsam:InterfaceName</code>
     *   element added to the <code>wsa:Metadata</code> element
     */
    public W3CEndpointReferenceBuilder interfaceName(QName interfaceName) {
        this.interfaceName = interfaceName;
        return this;
    }

    /**
     * Sets the <code>serviceName</code> as the
     * <code>wsam:ServiceName</code> element in the
     * <code>wsa:Metadata</code> element.
     *
     * See <a href="http://www.w3.org/TR/2007/REC-ws-addr-metadata-20070904/#refmetadatfromepr">
     * 2.1 Referencing WSDL Metadata from an EPR</a> for more details.
     *
     * @param serviceName The service name of the endpoint to be targeted
     *      by the returned <code>W3CEndpointReference</code>.  This property
     *      may also be used with the <code>endpointName</code> (portName)
     *      property to lookup the <code>address</code> of a web service
     *      endpoint that is published by the same Java EE application.
     *
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the <code>serviceName</code> as <code>wsam:ServiceName</code>
     *   element added to the <code>wsa:Metadata</code> element
     *
     */
    public W3CEndpointReferenceBuilder serviceName(QName serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * Sets the <code>endpointName</code> as
     * <code>wsam:ServiceName/@EndpointName</code> in the
     * <code>wsa:Metadata</code> element. This method can only be called
     * after the {@link #serviceName} method has been called.
     * <p>
     * See <a href="http://www.w3.org/TR/2007/REC-ws-addr-metadata-20070904/#refmetadatfromepr">
     * 2.1 Referencing WSDL Metadata from an EPR</a> for more details.
     *
     * @param endpointName The name of the endpoint to be targeted
     *      by the returned <code>W3CEndpointReference</code>. The
     *      <code>endpointName</code> (portName) property may also be
     *      used with the <code>serviceName</code> property to lookup
     *      the <code>address</code> of a web service
     *      endpoint published by the same Java EE application.
     *
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the <code>endpointName</code> as
     * <code>wsam:ServiceName/@EndpointName</code> in the
     * <code>wsa:Metadata</code> element.
     *
     * @throws IllegalStateException, if the <code>serviceName</code>
     * has not been set.
     * @throws IllegalArgumentException, if the <code>endpointName</code>'s
     * Namespace URI doesn't match <code>serviceName</code>'s Namespace URI
     *
     */
    public W3CEndpointReferenceBuilder endpointName(QName endpointName) {
        if (serviceName == null) {
            throw new IllegalStateException("The W3CEndpointReferenceBuilder's serviceName must be set before setting the endpointName: "+endpointName);
        }

        this.endpointName = endpointName;
        return this;
    }

    /**
     * Sets the <code>wsdlDocumentLocation</code> that will be referenced
     * as <code>wsa:Metadata/@wsdli:wsdlLocation</code>. The namespace name
     * for the wsdli:wsdlLocation's value can be taken from the WSDL itself.
     *
     * <p>
     * See <a href="http://www.w3.org/TR/2007/REC-ws-addr-metadata-20070904/#refmetadatfromepr">
     * 2.1 Referencing WSDL Metadata from an EPR</a> for more details.
     *
     * @param wsdlDocumentLocation The location of the WSDL document to
     *      be referenced in the <code>wsa:Metadata</code> of the
     *     <code>W3CEndpointReference</code>.
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the <code>wsdlDocumentLocation</code> that is to be referenced.
     */
    public W3CEndpointReferenceBuilder wsdlDocumentLocation(String wsdlDocumentLocation) {
        this.wsdlDocumentLocation = wsdlDocumentLocation;
        return this;
    }

    /**
     * Adds the <code>referenceParameter</code> to the
     * <code>W3CEndpointReference</code> instance
     * <code>wsa:ReferenceParameters</code> element.
     *
     * @param referenceParameter The element to be added to the
     *      <code>wsa:ReferenceParameters</code> element.
     *
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the <code>referenceParameter</code> added to the
     *   <code>wsa:ReferenceParameters</code> element.
     *
     * @throws java.lang.IllegalArgumentException if <code>referenceParameter</code>
     * is <code>null</code>.
     */
    public W3CEndpointReferenceBuilder referenceParameter(Element referenceParameter) {
        if (referenceParameter == null)
            throw new java.lang.IllegalArgumentException("The referenceParameter cannot be null.");
        referenceParameters.add(referenceParameter);
        return this;
    }

    /**
     * Adds the <code>metadataElement</code> to the
     * <code>W3CEndpointReference</code> instance's
     * <code>wsa:Metadata</code> element.
     *
     * @param metadataElement The element to be added to the
     *      <code>wsa:Metadata</code> element.
     *
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the <code>metadataElement</code> added to the
     *    <code>wsa:Metadata</code> element.
     *
     * @throws java.lang.IllegalArgumentException if <code>metadataElement</code>
     * is <code>null</code>.
     */
    public W3CEndpointReferenceBuilder metadata(Element metadataElement) {
        if (metadataElement == null)
            throw new java.lang.IllegalArgumentException("The metadataElement cannot be null.");
        metadata.add(metadataElement);
        return this;
    }

    /**
     * Adds an extension element to the
     * <code>W3CEndpointReference</code> instance's
     * <code>wsa:EndpointReference</code> element.
     *
     * @param element The extension element to be added to the
     *   <code>W3CEndpointReference</code>
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the extension <code>element</code> added to the
     *    <code>W3CEndpointReference</code> instance.
     * @throws java.lang.IllegalArgumentException if <code>element</code>
     * is <code>null</code>.
     *
     * @since JAX-WS 2.2
     */
    public W3CEndpointReferenceBuilder element(Element element) {
        if (element == null) {
            throw new IllegalArgumentException("The extension element cannot be null.");
        }
        elements.add(element);
        return this;
    }

    /**
     * Adds an extension attribute to the
     * <code>W3CEndpointReference</code> instance's
     * <code>wsa:EndpointReference</code> element.
     *
     * @param name The name of the extension attribute to be added to the
     *   <code>W3CEndpointReference</code>
     * @param value extension attribute value
     * @return A <code>W3CEndpointReferenceBuilder</code> instance with
     *   the extension attribute added to the <code>W3CEndpointReference</code>
     *   instance.
     * @throws java.lang.IllegalArgumentException if <code>name</code>
     *   or <code>value</code> is <code>null</code>.
     *
     * @since JAX-WS 2.2
     */
    public W3CEndpointReferenceBuilder attribute(QName name, String value) {
        if (name == null || value == null) {
            throw new IllegalArgumentException("The extension attribute name or value cannot be null.");
        }
        attributes.put(name, value);
        return this;
    }

    /**
     * Builds a <code>W3CEndpointReference</code> from the accumulated
     * properties set on this <code>W3CEndpointReferenceBuilder</code>
     * instance.
     * <p>
     * This method can be used to create a <code>W3CEndpointReference</code>
     * for any endpoint by specifying the <code>address</code> property along
     * with any other desired properties.  This method
     * can also be used to create a <code>W3CEndpointReference</code> for
     * an endpoint that is published by the same Java EE application.
     * This method can automatically determine the <code>address</code> of
     * an endpoint published by the same Java EE application that is identified by the
     * <code>serviceName</code> and
     * <code>endpointName</code> properties.  If the <code>address</code> is
     * <code>null</code> and the <code>serviceName</code> and
     * <code>endpointName</code>
     * do not identify an endpoint published by the same Java EE application, a
     * <code>java.lang.IllegalStateException</code> MUST be thrown.
     *
     *
     * @return <code>W3CEndpointReference</code> from the accumulated
     * properties set on this <code>W3CEndpointReferenceBuilder</code>
     * instance. This method never returns <code>null</code>.
     *
     * @throws IllegalStateException
     *     <ul>
     *        <li>If the <code>address</code>, <code>serviceName</code> and
     *            <code>endpointName</code> are all <code>null</code>.
     *        <li>If the <code>serviceName</code> service is <code>null</code> and the
     *            <code>endpointName</code> is NOT <code>null</code>.
     *        <li>If the <code>address</code> property is <code>null</code> and
     *            the <code>serviceName</code> and <code>endpointName</code> do not
     *            specify a valid endpoint published by the same Java EE
     *            application.
     *        <li>If the <code>serviceName</code> is NOT <code>null</code>
     *             and is not present in the specified WSDL.
     *        <li>If the <code>endpointName</code> port is not <code>null</code> and it
     *             is not present in <code>serviceName</code> service in the WSDL.
     *        <li>If the <code>wsdlDocumentLocation</code> is NOT <code>null</code>
     *            and does not represent a valid WSDL.
     *     </ul>
     * @throws WebServiceException If an error occurs while creating the
     *                             <code>W3CEndpointReference</code>.
     *
     */
    public W3CEndpointReference build() {
        if (elements.isEmpty() && attributes.isEmpty() && interfaceName == null) {
            // 2.1 API
            return Provider.provider().createW3CEndpointReference(address,
                serviceName, endpointName, metadata, wsdlDocumentLocation,
                referenceParameters);
        }
        return Provider.provider().createW3CEndpointReference(address,
                interfaceName, serviceName, endpointName, metadata, wsdlDocumentLocation,
                referenceParameters, elements, attributes);
    }

    private String address;
    private List<Element> referenceParameters;
    private List<Element> metadata;
    private QName interfaceName;
    private QName serviceName;
    private QName endpointName;
    private String wsdlDocumentLocation;
    private Map<QName,String> attributes;
    private List<Element> elements;
}
