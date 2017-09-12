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
 * This class is used to build {@code W3CEndpointReference}
 * instances. The intended use of this clsss is for
 * an application component, for example a factory component,
 * to create an {@code W3CEndpointReference} for a
 * web service endpoint published by the same
 * Java EE application. It can also be used to create
 * {@code W3CEndpointReferences} for an Java SE based
 * endpoint by providing the {@code address} property.
 * <p>
 * When creating a {@code W3CEndpointReference} for an
 * endpoint that is not published by the same Java EE application,
 * the {@code address} property MUST be specified.
 * <p>
 * When creating a {@code W3CEndpointReference} for an endpoint
 * published by the same Java EE application, the {@code address}
 * property MAY be {@code null} but then the {@code serviceName}
 * and {@code endpointName} MUST specify an endpoint published by
 * the same Java EE application.
 * <p>
 * When the {@code wsdlDocumentLocation} is specified it MUST refer
 * to a valid WSDL document and the {@code serviceName} and
 * {@code endpointName} (if specified) MUST match a service and port
 * in the WSDL document.
 *
 * @since 1.6, JAX-WS 2.1
 */
public final class W3CEndpointReferenceBuilder {
    /**
     * Creates a new {@code W3CEndpointReferenceBuilder} instance.
     */
    public W3CEndpointReferenceBuilder() {
        referenceParameters = new ArrayList<Element>();
        metadata = new ArrayList<Element>();
        attributes = new HashMap<QName, String>();
        elements = new ArrayList<Element>();
    }

    /**
     * Sets the {@code address} to the
     * {@code W3CEndpointReference} instance's
     * {@code wsa:Address}.
     * <p>
     * The {@code address} MUST be set to a non-{@code null}
     * value when building a {@code W3CEndpointReference} for a
     * web service endpoint that is not published by the same
     * Java EE application or when running on Java SE.
     *
     * @param address The address of the endpoint to be targeted
     *      by the returned {@code W3CEndpointReference}.
     *
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the {@code address} set to the {@code wsa:Address}.
     */
    public W3CEndpointReferenceBuilder address(String address) {
        this.address = address;
        return this;
    }

    /**
     * Sets the {@code interfaceName} as the
     * {@code wsam:InterfaceName} element in the
     * {@code wsa:Metadata} element.
     *
     * See <a href="http://www.w3.org/TR/2007/REC-ws-addr-metadata-20070904/#refmetadatfromepr">
     * 2.1 Referencing WSDL Metadata from an EPR</a> for more details.
     *
     * @param interfaceName The port type name of the endpoint to be targeted
     *      by the returned {@code W3CEndpointReference}.
     *
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the {@code interfaceName} as {@code wsam:InterfaceName}
     *   element added to the {@code wsa:Metadata} element
     * @since 1.7
     */
    public W3CEndpointReferenceBuilder interfaceName(QName interfaceName) {
        this.interfaceName = interfaceName;
        return this;
    }

    /**
     * Sets the {@code serviceName} as the
     * {@code wsam:ServiceName} element in the
     * {@code wsa:Metadata} element.
     *
     * See <a href="http://www.w3.org/TR/2007/REC-ws-addr-metadata-20070904/#refmetadatfromepr">
     * 2.1 Referencing WSDL Metadata from an EPR</a> for more details.
     *
     * @param serviceName The service name of the endpoint to be targeted
     *      by the returned {@code W3CEndpointReference}.  This property
     *      may also be used with the {@code endpointName} (portName)
     *      property to lookup the {@code address} of a web service
     *      endpoint that is published by the same Java EE application.
     *
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the {@code serviceName} as {@code wsam:ServiceName}
     *   element added to the {@code wsa:Metadata} element
     *
     */
    public W3CEndpointReferenceBuilder serviceName(QName serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * Sets the {@code endpointName} as
     * {@code wsam:ServiceName/@EndpointName} in the
     * {@code wsa:Metadata} element. This method can only be called
     * after the {@link #serviceName(QName)} method has been called.
     * <p>
     * See <a href="http://www.w3.org/TR/2007/REC-ws-addr-metadata-20070904/#refmetadatfromepr">
     * 2.1 Referencing WSDL Metadata from an EPR</a> for more details.
     *
     * @param endpointName The name of the endpoint to be targeted
     *      by the returned {@code W3CEndpointReference}. The
     *      {@code endpointName} (portName) property may also be
     *      used with the {@code serviceName} property to lookup
     *      the {@code address} of a web service
     *      endpoint published by the same Java EE application.
     *
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the {@code endpointName} as
     *   {@code wsam:ServiceName/@EndpointName} in the
     *   {@code wsa:Metadata} element.
     *
     * @throws java.lang.IllegalStateException if the {@code serviceName}
     *   has not been set
     *
     * @throws java.lang.IllegalArgumentException if the {@code endpointName}'s
     *   Namespace URI doesn't match {@code serviceName}'s Namespace URI
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
     * Sets the {@code wsdlDocumentLocation} that will be referenced
     * as {@code wsa:Metadata/@wsdli:wsdlLocation}. The namespace name
     * for the wsdli:wsdlLocation's value can be taken from the WSDL itself.
     *
     * <p>
     * See <a href="http://www.w3.org/TR/2007/REC-ws-addr-metadata-20070904/#refmetadatfromepr">
     * 2.1 Referencing WSDL Metadata from an EPR</a> for more details.
     *
     * @param wsdlDocumentLocation The location of the WSDL document to
     *      be referenced in the {@code wsa:Metadata} of the
     *     {@code W3CEndpointReference}.
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the {@code wsdlDocumentLocation} that is to be referenced.
     */
    public W3CEndpointReferenceBuilder wsdlDocumentLocation(String wsdlDocumentLocation) {
        this.wsdlDocumentLocation = wsdlDocumentLocation;
        return this;
    }

    /**
     * Adds the {@code referenceParameter} to the
     * {@code W3CEndpointReference} instance
     * {@code wsa:ReferenceParameters} element.
     *
     * @param referenceParameter The element to be added to the
     *      {@code wsa:ReferenceParameters} element.
     *
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the {@code referenceParameter} added to the
     *   {@code wsa:ReferenceParameters} element.
     *
     * @throws java.lang.IllegalArgumentException if {@code referenceParameter}
     * is {@code null}.
     */
    public W3CEndpointReferenceBuilder referenceParameter(Element referenceParameter) {
        if (referenceParameter == null)
            throw new java.lang.IllegalArgumentException("The referenceParameter cannot be null.");
        referenceParameters.add(referenceParameter);
        return this;
    }

    /**
     * Adds the {@code metadataElement} to the
     * {@code W3CEndpointReference} instance's
     * {@code wsa:Metadata} element.
     *
     * @param metadataElement The element to be added to the
     *      {@code wsa:Metadata} element.
     *
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the {@code metadataElement} added to the
     *    {@code wsa:Metadata} element.
     *
     * @throws java.lang.IllegalArgumentException if {@code metadataElement}
     * is {@code null}.
     */
    public W3CEndpointReferenceBuilder metadata(Element metadataElement) {
        if (metadataElement == null)
            throw new java.lang.IllegalArgumentException("The metadataElement cannot be null.");
        metadata.add(metadataElement);
        return this;
    }

    /**
     * Adds an extension element to the
     * {@code W3CEndpointReference} instance's
     * {@code wsa:EndpointReference} element.
     *
     * @param element The extension element to be added to the
     *   {@code W3CEndpointReference}
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the extension {@code element} added to the
     *    {@code W3CEndpointReference} instance.
     * @throws java.lang.IllegalArgumentException if {@code element}
     * is {@code null}.
     *
     * @since 1.7, JAX-WS 2.2
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
     * {@code W3CEndpointReference} instance's
     * {@code wsa:EndpointReference} element.
     *
     * @param name The name of the extension attribute to be added to the
     *   {@code W3CEndpointReference}
     * @param value extension attribute value
     * @return A {@code W3CEndpointReferenceBuilder} instance with
     *   the extension attribute added to the {@code W3CEndpointReference}
     *   instance.
     * @throws java.lang.IllegalArgumentException if {@code name}
     *   or {@code value} is {@code null}.
     *
     * @since 1.7, JAX-WS 2.2
     */
    public W3CEndpointReferenceBuilder attribute(QName name, String value) {
        if (name == null || value == null) {
            throw new IllegalArgumentException("The extension attribute name or value cannot be null.");
        }
        attributes.put(name, value);
        return this;
    }

    /**
     * Builds a {@code W3CEndpointReference} from the accumulated
     * properties set on this {@code W3CEndpointReferenceBuilder}
     * instance.
     * <p>
     * This method can be used to create a {@code W3CEndpointReference}
     * for any endpoint by specifying the {@code address} property along
     * with any other desired properties.  This method
     * can also be used to create a {@code W3CEndpointReference} for
     * an endpoint that is published by the same Java EE application.
     * This method can automatically determine the {@code address} of
     * an endpoint published by the same Java EE application that is identified by the
     * {@code serviceName} and
     * {@code endpointName} properties.  If the {@code address} is
     * {@code null} and the {@code serviceName} and
     * {@code endpointName}
     * do not identify an endpoint published by the same Java EE application, a
     * {@code java.lang.IllegalStateException} MUST be thrown.
     *
     *
     * @return {@code W3CEndpointReference} from the accumulated
     * properties set on this {@code W3CEndpointReferenceBuilder}
     * instance. This method never returns {@code null}.
     *
     * @throws IllegalStateException
     *     <ul>
     *        <li>If the {@code address}, {@code serviceName} and
     *            {@code endpointName} are all {@code null}.
     *        <li>If the {@code serviceName} service is {@code null} and the
     *            {@code endpointName} is NOT {@code null}.
     *        <li>If the {@code address} property is {@code null} and
     *            the {@code serviceName} and {@code endpointName} do not
     *            specify a valid endpoint published by the same Java EE
     *            application.
     *        <li>If the {@code serviceName} is NOT {@code null}
     *             and is not present in the specified WSDL.
     *        <li>If the {@code endpointName} port is not {@code null} and it
     *             is not present in {@code serviceName} service in the WSDL.
     *        <li>If the {@code wsdlDocumentLocation} is NOT {@code null}
     *            and does not represent a valid WSDL.
     *     </ul>
     * @throws WebServiceException If an error occurs while creating the
     *                             {@code W3CEndpointReference}.
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
