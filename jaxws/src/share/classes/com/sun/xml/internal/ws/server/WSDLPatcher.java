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

package com.sun.xml.internal.ws.server;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.StartDocument;
import javax.xml.stream.events.XMLEvent;
public class WSDLPatcher {

    private static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";
    private static final QName QNAME_SCHEMA = new QName(NS_XSD, "schema");
    private static final QName SCHEMA_INCLUDE_QNAME = new QName(NS_XSD, "include");
    private static final QName SCHEMA_IMPORT_QNAME = new QName(NS_XSD, "import");
    private static final QName ATTR_NAME_QNAME = new QName("", "name");
    private static final QName ATTR_TARGETNS_QNAME =
            new QName("", "targetNamespace");
    private static final QName WSDL_LOCATION_QNAME = new QName("", "location");
    private static final QName SCHEMA_LOCATION_QNAME =
            new QName("", "schemaLocation");

    private static final XMLEventFactory eventFactory =
            XMLEventFactory.newInstance();
    private static final XMLOutputFactory outputFactory =
            XMLOutputFactory.newInstance();
    private static final XMLInputFactory inputFactory =
            XMLInputFactory.newInstance();

    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".wsdl.patcher");

    private DocInfo docInfo;
    private String baseAddress;
    private RuntimeEndpointInfo targetEndpoint;
    private List<RuntimeEndpointInfo> endpoints;

    /*
     * inPath - /WEB-INF/wsdl/xxx.wsdl
     * baseAddress - http://host:port/context/
     */
    public WSDLPatcher(DocInfo docInfo, String baseAddress,
            RuntimeEndpointInfo targetEndpoint,
            List<RuntimeEndpointInfo> endpoints) {
        this.docInfo = docInfo;
        this.baseAddress = baseAddress;
        this.targetEndpoint = targetEndpoint;
        this.endpoints = endpoints;
    }

    /*
     * import, include, soap:address locations are patched
     * caller needs to take care of closing of the streams
     */
    public void patchDoc(InputStream in, OutputStream out) {
        XMLEventReader reader = null;
        XMLEventWriter writer = null;
        try {
            reader = inputFactory.createXMLEventReader(in);
            StartElement start = null;
            QName serviceName = null;
            QName portName = null;
            String targetNamespace = null;
            while(reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (event.isStartDocument()) {
                    StartDocument sd = (StartDocument)event;
                    String encoding = sd.encodingSet()
                        ? sd.getCharacterEncodingScheme()
                        : null;
                    writer = (encoding != null)
                        ? outputFactory.createXMLEventWriter(out, encoding)
                        : outputFactory.createXMLEventWriter(out);
               } else if (event.isStartElement()) {
                    start = event.asStartElement();
                    QName name = start.getName();
                    if (name.equals(SCHEMA_INCLUDE_QNAME)) {
                        event = handleSchemaInclude(start);
                    } else if (name.equals(SCHEMA_IMPORT_QNAME)) {
                        event = handleSchemaImport(start);
                    } else if (name.equals(WSDLConstants.QNAME_IMPORT)) {
                        event = handleWSDLImport(start);
                    } else if (name.equals(WSDLConstants.NS_SOAP_BINDING_ADDRESS) ||
                            name.equals(WSDLConstants.NS_SOAP12_BINDING_ADDRESS)) {
                        event = handleSoapAddress(serviceName, portName, start);
                    } else if (name.equals(WSDLConstants.QNAME_DEFINITIONS)) {
                        Attribute attr = start.getAttributeByName(ATTR_TARGETNS_QNAME);
                        if (attr != null) {
                            targetNamespace = attr.getValue();
                        }
                    } else if (name.equals(WSDLConstants.QNAME_SERVICE)) {
                        Attribute attr = start.getAttributeByName(ATTR_NAME_QNAME);
                        if (attr != null) {
                            serviceName = new QName(targetNamespace, attr.getValue());
                        }
                    } else if (name.equals(WSDLConstants.QNAME_PORT)) {
                        Attribute attr = start.getAttributeByName(ATTR_NAME_QNAME);
                        if (attr != null) {
                            portName = new QName(targetNamespace, attr.getValue());
                        }
                    }
                } else if (event.isEndElement()) {
                    start = null;
                }
                writer.add(event);
            }
        } catch (XMLStreamException e) {
            throw new ServerRtException("runtime.wsdl.patcher",e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch(XMLStreamException e) {
                    e.printStackTrace();
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch(XMLStreamException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     * return patchedlocation  null if we don't how to patch this location
     */
    private String getPatchedImportLocation(String relPath) {
        try {
            URL relUrl = new URL(docInfo.getUrl(), relPath);
            String query = targetEndpoint.getQueryString(relUrl);
            if (query == null) {
                return null;
            }
            String abs = baseAddress+targetEndpoint.getUrlPatternWithoutStar()+"?"+query;
            return abs;
        } catch(MalformedURLException mue) {
            return null;
        }
    }

    private XMLEvent patchImport(StartElement startElement, QName location)
    throws XMLStreamException {
        Attribute locationAttr = startElement.getAttributeByName(location);
        if (locationAttr == null) {
            return startElement;
        }

        List<Attribute> newAttrs = new ArrayList<Attribute>();
        Iterator i = startElement.getAttributes();
        while(i.hasNext()) {
            Attribute attr = (Attribute)i.next();
            String file = attr.getValue();
            if (attr.getName().equals(location)) {
                String relPath = attr.getValue();
                //if (isPatchable(relPath)) {
                    String absPath = getPatchedImportLocation(relPath);
                    if (absPath == null) {
                        //logger.warning("Couldn't fix the relative location:"+relPath);
                        return startElement;        // Not patching
                    }
                    logger.fine("Fixing the relative location:"+relPath
                            +" with absolute location:"+absPath);
                    Attribute newAttr = eventFactory.createAttribute(
                        location, absPath);
                    newAttrs.add(newAttr);
                    continue;
                //}
            }
            newAttrs.add(attr);
        }
        XMLEvent event = eventFactory.createStartElement(
                startElement.getName().getPrefix(),
                startElement.getName().getNamespaceURI(),
                startElement.getName().getLocalPart(),
                newAttrs.iterator(),
                startElement.getNamespaces(),
                startElement.getNamespaceContext());
        return event;
    }

    /*
     * <schema:import> element is patched with correct uri and
     * returns a new element
     */
    private XMLEvent handleSchemaImport(StartElement startElement)
    throws XMLStreamException {
        return patchImport(startElement, SCHEMA_LOCATION_QNAME);
    }

    /*
     * <schema:include> element is patched with correct uri and
     * returns a new element
     */
    private XMLEvent handleSchemaInclude(StartElement startElement)
    throws XMLStreamException {
        return patchImport(startElement, SCHEMA_LOCATION_QNAME);
    }

    /*
     * <wsdl:import> element is patched with correct uri and
     * returns a new element
     */
    private XMLEvent handleWSDLImport(StartElement startElement)
    throws XMLStreamException {
        return patchImport(startElement, WSDL_LOCATION_QNAME);
    }

    /*
     * <soap:address> element is patched with correct endpoint address and
     * returns a new element
     */
    private XMLEvent handleSoapAddress(QName service, QName port,
            StartElement startElement) throws XMLStreamException {

        List<Attribute> newAttrs = new ArrayList<Attribute>();
        Iterator i = startElement.getAttributes();
        while(i.hasNext()) {
            Attribute attr = (Attribute)i.next();
            String file = attr.getValue();
            if (attr.getName().equals(WSDL_LOCATION_QNAME)) {
                String value = getAddressLocation(service, port);
                if (value == null) {
                    return startElement;        // Not patching
                }
                logger.fine("Fixing service:"+service+ " port:"+port
                        + " address with "+value);
                Attribute newAttr = eventFactory.createAttribute(
                        WSDL_LOCATION_QNAME, value);
                newAttrs.add(newAttr);
                continue;
            }
            newAttrs.add(attr);
        }
        XMLEvent event = eventFactory.createStartElement(
                startElement.getName().getPrefix(),
                startElement.getName().getNamespaceURI(),
                startElement.getName().getLocalPart(),
                newAttrs.iterator(),
                startElement.getNamespaces(),
                startElement.getNamespaceContext());
        return event;
    }

    /*
     * For the given service, port names it matches the correct endpoint and
     * reutrns its endpoint address
     */
    private String getAddressLocation(QName docServiceName, QName docPortName) {
        for(RuntimeEndpointInfo endpointInfo : endpoints) {
            QName serviceName = endpointInfo.getServiceName();
            QName portName = endpointInfo.getPortName();
            if (serviceName != null && portName != null
                    && docServiceName != null && docPortName != null
                    && serviceName.equals(docServiceName)
                    && docPortName.equals(portName)) {
                return baseAddress+endpointInfo.getUrlPatternWithoutStar();
            }
        }
        return null;
    }

}
