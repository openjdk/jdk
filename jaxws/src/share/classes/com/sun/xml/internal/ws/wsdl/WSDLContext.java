/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.wsdl;

import com.sun.xml.internal.ws.wsdl.parser.*;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPBinding;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * $author: JAXWS Development Team
 */
public class WSDLContext {
    private final URL orgWsdlLocation;
    private String targetNamespace;
    private final WSDLDocument wsdlDoc;

    /**
     * Creates a {@link WSDLContext} by parsing the given wsdl file.
     */
    public WSDLContext(URL wsdlDocumentLocation, EntityResolver entityResolver) throws WebServiceException {
        //must get binding information
        assert entityResolver != null;

        if (wsdlDocumentLocation == null)
            throw new WebServiceException("No WSDL location Information present, error");

        orgWsdlLocation = wsdlDocumentLocation;
        try {
            wsdlDoc = RuntimeWSDLParser.parse(wsdlDocumentLocation, entityResolver);
        } catch (IOException e) {
            throw new WebServiceException(e);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        } catch (SAXException e) {
            throw new WebServiceException(e);
        }
    }

    public URL getWsdlLocation() {
        return orgWsdlLocation;
    }

    public String getOrigURLPath() {
        return orgWsdlLocation.getPath();
    }

    public QName getServiceQName() {
        return wsdlDoc.getFirstServiceName();
    }

    public boolean contains(QName serviceName) {
        return (wsdlDoc.getServices().containsKey(serviceName));
    }

    //just get the first one for now
    public String getEndpoint(QName serviceName) {
        if (serviceName == null)
            throw new WebServiceException("Service unknown, can not identify ports for an unknown Service.");
        Service service = wsdlDoc.getService(serviceName);
        String endpoint = null;
        if (service != null) {
            Iterator<Map.Entry<QName, Port>> iter = service.entrySet().iterator();
            if (iter.hasNext()) {
                Port port = iter.next().getValue();
                endpoint = port.getAddress();
            }
        }
        if (endpoint == null)
            throw new WebServiceException("Endpoint not found. Check WSDL file to verify endpoint was provided.");
        return endpoint;
    }

    //just get the first one for now
    public QName getPortName() {
        return wsdlDoc.getFirstPortName();
    }

    public String getBindingID(QName serviceName, QName portName) {
        return getWsdlDocument().getBindingId(serviceName, portName);
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String tns) {
        targetNamespace = tns;
    }

    public Set<QName> getPortsAsSet(QName serviceName) {
        Service service = wsdlDoc.getService(serviceName);
        if (service != null) {
            return service.keySet();
        }
        return null;
    }


    public boolean contains(QName serviceName, QName portName) {
        Service service = wsdlDoc.getService(serviceName);
        if (service != null) {

            Iterator<Map.Entry<QName, Port>> iter = service.entrySet().iterator();
            while (iter.hasNext()) {
                Port port = iter.next().getValue();
                if (port.getName().equals(portName))
                    return true;
            }
        }
        return false;
    }

    public QName getFirstServiceName() {
        return wsdlDoc.getFirstServiceName();
    }

    public Set<QName> getAllServiceNames() {
        return wsdlDoc.getServices().keySet();
    }

    public WSDLDocument getWsdlDocument() {
        return wsdlDoc;
    }

    public Binding getWsdlBinding(QName service, QName port) {
        if (wsdlDoc == null)
            return null;
        return wsdlDoc.getBinding(service, port);
    }

    public String getEndpoint(QName serviceName, QName portQName) {
        Service service = wsdlDoc.getService(serviceName);
        if (service != null) {
            Port p = service.get(portQName);
            if (p != null)
                return p.getAddress();
            else
                throw new WebServiceException("No ports found for service " + serviceName);
        } else {
            throw new WebServiceException("Service unknown, can not identify ports for an unknown Service.");
        }
    }

}
