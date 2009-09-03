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
package com.sun.xml.internal.ws.spi;


import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLService;
import com.sun.xml.internal.ws.api.server.BoundEndpoint;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.ContainerResolver;
import com.sun.xml.internal.ws.api.server.Module;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.developer.MemberSubmissionEndpointReference;
import com.sun.xml.internal.ws.model.wsdl.WSDLModelImpl;
import com.sun.xml.internal.ws.resources.ProviderApiMessages;
import com.sun.xml.internal.ws.transport.http.server.EndpointImpl;
import com.sun.xml.internal.ws.util.ServiceFinder;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.wsdl.parser.RuntimeWSDLParser;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Endpoint;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.spi.Provider;
import javax.xml.ws.spi.ServiceDelegate;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;

/**
 * The entry point to the JAX-WS RI from the JAX-WS API.
 *
 * @author WS Development Team
 */
public class ProviderImpl extends Provider {

    private final static JAXBContext eprjc = getEPRJaxbContext();

    /**
     * Convenient singleton instance.
     */
    public static final ProviderImpl INSTANCE = new ProviderImpl();

    @Override
    public Endpoint createEndpoint(String bindingId, Object implementor) {
        return new EndpointImpl(
            (bindingId != null) ? BindingID.parse(bindingId) : BindingID.parse(implementor.getClass()),
            implementor);
    }

    @Override
    public ServiceDelegate createServiceDelegate( URL wsdlDocumentLocation, QName serviceName, Class serviceClass) {
         return new WSServiceDelegate(wsdlDocumentLocation, serviceName, serviceClass);
    }

    @Override
    public Endpoint createAndPublishEndpoint(String address,
                                             Object implementor) {
        Endpoint endpoint = new EndpointImpl(
            BindingID.parse(implementor.getClass()),
            implementor);
        endpoint.publish(address);
        return endpoint;
    }

    public EndpointReference readEndpointReference(final Source eprInfoset) {
        // EPR constructors are private, so we need privilege escalation.
        // this unmarshalling can only access instances of a fixed, known set of classes,
        // so doing that shouldn't introduce security vulnerability.
        return AccessController.doPrivileged(new PrivilegedAction<EndpointReference>() {
            public EndpointReference run() {
                try {
                    Unmarshaller unmarshaller = eprjc.createUnmarshaller();
                    return (EndpointReference) unmarshaller.unmarshal(eprInfoset);
                } catch (JAXBException e) {
                    throw new WebServiceException("Error creating Marshaller or marshalling.", e);
                }
            }
        });
    }

    public <T> T getPort(EndpointReference endpointReference, Class<T> clazz, WebServiceFeature... webServiceFeatures) {
        /*
        final @NotNull MemberSubmissionEndpointReference msepr =
                EndpointReferenceUtil.transform(MemberSubmissionEndpointReference.class, endpointReference);
                WSService service = new WSServiceDelegate(msepr.toWSDLSource(), msepr.serviceName.name, Service.class);
                */
        if(endpointReference == null)
            throw new WebServiceException(ProviderApiMessages.NULL_EPR());
        WSEndpointReference wsepr =  new WSEndpointReference(endpointReference);
        WSEndpointReference.Metadata metadata = wsepr.getMetaData();
        WSService service;
        if(metadata.getWsdlSource() != null)
            service = new WSServiceDelegate(metadata.getWsdlSource(), metadata.getServiceName(), Service.class);
        else
            throw new WebServiceException("WSDL metadata is missing in EPR");
        return service.getPort(wsepr, clazz, webServiceFeatures);
    }

    public W3CEndpointReference createW3CEndpointReference(String address, QName serviceName, QName portName, List<Element> metadata, String wsdlDocumentLocation, List<Element> referenceParameters) {
        Container container = ContainerResolver.getInstance().getContainer();
        if (address == null) {
            if (serviceName == null || portName == null) {
                throw new IllegalStateException(ProviderApiMessages.NULL_ADDRESS_SERVICE_ENDPOINT());
            } else {
                //check if it is run in a Java EE Container and if so, get address using serviceName and portName
                Module module = container.getSPI(Module.class);
                if (module != null) {
                    List<BoundEndpoint> beList = module.getBoundEndpoints();
                    for (BoundEndpoint be : beList) {
                        WSEndpoint wse = be.getEndpoint();
                        if (wse.getServiceName().equals(serviceName) && wse.getPortName().equals(portName)) {
                            try {
                                address = be.getAddress().toString();
                            } catch (WebServiceException e) {
                                // May be the container does n't support this
                                //just ignore the exception
                            }
                            break;
                        }
                    }
                }
                //address is still null? may be its not run in a JavaEE Container
                if (address == null)
                    throw new IllegalStateException(ProviderApiMessages.NULL_ADDRESS());
            }
        }
        if((serviceName==null) && (portName != null)) {
            throw new IllegalStateException(ProviderApiMessages.NULL_SERVICE());
        }
        //Validate Service and Port in WSDL
        if (wsdlDocumentLocation != null) {
            try {
                EntityResolver er = XmlUtil.createDefaultCatalogResolver();

                URL wsdlLoc = new URL(wsdlDocumentLocation);
                WSDLModelImpl wsdlDoc = RuntimeWSDLParser.parse(wsdlLoc, new StreamSource(wsdlLoc.toExternalForm()), er,
                        false, container, ServiceFinder.find(WSDLParserExtension.class).toArray());
                if (serviceName != null) {
                    WSDLService wsdlService = wsdlDoc.getService(serviceName);
                    if (wsdlService == null)
                        throw new IllegalStateException(ProviderApiMessages.NOTFOUND_SERVICE_IN_WSDL(
                                serviceName,wsdlDocumentLocation));
                    if (portName != null) {
                        WSDLPort wsdlPort = wsdlService.get(portName);
                        if (wsdlPort == null)
                            throw new IllegalStateException(ProviderApiMessages.NOTFOUND_PORT_IN_WSDL(
                                    portName,serviceName,wsdlDocumentLocation));
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(ProviderApiMessages.ERROR_WSDL(wsdlDocumentLocation),e);
            }
        }
        // Supress writing ServiceName and EndpointName in W3C EPR,
        // Until the ns for those metadata elements is resolved.
        return new WSEndpointReference(
            AddressingVersion.fromSpecClass(W3CEndpointReference.class),
            address, null /*serviceName*/, null /*portName*/, null, metadata, null /*wsdlDocumentLocation*/, referenceParameters).toSpec(W3CEndpointReference.class);
    }

    private static JAXBContext getEPRJaxbContext() {
        // EPRs have package and private fields, so we need privilege escalation.
        // this access only fixed, known set of classes, so doing that
        // shouldn't introduce security vulnerability.
        return AccessController.doPrivileged(new PrivilegedAction<JAXBContext>() {
            public JAXBContext run() {
                try {
                    return JAXBContext.newInstance(MemberSubmissionEndpointReference.class, W3CEndpointReference.class);
                } catch (JAXBException e) {
                    throw new WebServiceException("Error creating JAXBContext for W3CEndpointReference. ", e);
                }
            }
        });
    }
}
