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

package com.sun.xml.internal.ws.client.dispatch.impl.encoding;

import com.sun.xml.internal.ws.encoding.jaxb.JAXBBeanInfo;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.SerializationException;
import com.sun.xml.internal.ws.streaming.Attributes;
import com.sun.xml.internal.ws.streaming.SourceReaderFactory;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;

import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import static javax.xml.stream.XMLStreamConstants.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.WebServiceException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * @author WS Development Team
 */
public final class DispatchSerializer {

    private static final Logger logger =
        Logger.getLogger(new StringBuffer().append(com.sun.xml.internal.ws.util.Constants.LoggingDomain).append(".client.dispatch").toString());

    private final QName bodyTagName;

    /**
     * For SOAP 1.0.
     */
    public static final DispatchSerializer SOAP_1_0 = new DispatchSerializer(SOAPConstants.QNAME_SOAP_BODY);

    /**
     * For SOAP 1.2.
     */
    public static final DispatchSerializer SOAP_1_2 = new DispatchSerializer(SOAP12Constants.QNAME_SOAP_BODY);


    private DispatchSerializer(QName soapBodyTagName) {
        bodyTagName = soapBodyTagName;
    }

    public void serialize(Object obj, XMLStreamWriter writer, JAXBContext context) {
        if (obj instanceof Source)
            serializeSource(obj, writer);
        else if (obj instanceof JAXBBeanInfo) {
            ((JAXBBeanInfo) obj).writeTo(writer);
        } else
            throw new WebServiceException("Unable to serialize object type " + obj.getClass().getName());
        //should not happen
    }


    private static String convertNull(String s) {
        return (s != null) ? s : "";
    }

    // this is very very inefficient.
    //kw-needs lots of cleanup
    //kw-modifying this in any way will cause dispatch tests sqe failures
    public Source deserializeSource(XMLStreamReader reader, DispatchUtil dispatchUtil) {

        ByteArrayBuffer baos = new ByteArrayBuffer();
        XMLStreamWriter writer = XMLStreamWriterFactory.createXMLStreamWriter(baos);
        dispatchUtil.populatePrefixes(writer);
        try {
            while (reader.hasNext()) {
                int state = reader.getEventType();
                switch (state) {
                    case START_ELEMENT:

                        String uri = reader.getNamespaceURI();
                        String rprefix = reader.getPrefix();
                        String nlocal = reader.getLocalName();
                        setWriterPrefixes(rprefix, uri, writer);

                        String prefix = null;
                        String wprefix = writer.getNamespaceContext().getPrefix(uri);
                        if ((wprefix != null && !"".equals(wprefix)) && wprefix.length() > 0){
                            prefix = wprefix;
                        } else
                        if ((rprefix != null && !"".equals(rprefix)) && (uri != null && !"null".equals(uri))){
                            prefix = setWriterPrefixes(reader, uri, writer);
                        } else {
                            prefix = convertNull(prefix);
                            uri = convertNull(uri);
                        }

                        writer.writeStartElement(prefix, nlocal, uri);
                        writer.writeNamespace(prefix, uri);

                        Attributes atts = XMLStreamReaderUtil.getAttributes(reader);
                        writer.flush();
                        writeAttributes(atts, writer, prefix, uri);
                        break;
                    case END_ELEMENT:
                        writer.writeEndElement();
                        break;
                    case CHARACTERS:
                        writer.writeCharacters(reader.getText());

                }
                state = XMLStreamReaderUtil.next(reader);
                if ((reader.getEventType() == END_ELEMENT) && (reader.getName().equals(bodyTagName)))
                    break;
            }
            writer.flush();
            writer.close();
            reader.close();
        } catch (XMLStreamException ex) {
            ex.printStackTrace();
        }

        return new StreamSource(baos.newInputStream());
    }

    private void writeAttributes(Attributes atts, XMLStreamWriter writer, String prefix, String uri) throws XMLStreamException {
        for (int i = 0; i < atts.getLength(); i++) {

            String value = atts.getValue(i);
            String localName = atts.getName(i).getLocalPart();
            String aprefix = atts.getPrefix(i);
            String auri = atts.getURI(i);

            setWriterPrefix(localName, value, aprefix, writer);
            if (atts.isNamespaceDeclaration(i)) {
                writeAttrNamespace(aprefix, auri, writer, localName, prefix, uri, value);
            } else {
                writeAttribute(atts, i, writer);
            }
        }
    }

    private void setWriterPrefix(String localName, String value, String aprefix, XMLStreamWriter writer) throws XMLStreamException {
        if (localName.equals("xsi") &&
            value.equals("http://www.w3.org/2001/XMLSchema-instance") &&
            aprefix.equals("xmlns")) {
            //kw was aa prefix
            writer.setPrefix(localName, value);
        }
    }

    private String setWriterPrefixes(XMLStreamReader reader, String nuri, XMLStreamWriter writer) {

        String prefix = reader.getNamespaceContext().getPrefix(nuri);
        if (prefix == null)
            prefix = convertNull(prefix);
        if (prefix != null && prefix.length() > 0 && nuri != null && !prefix.equals("xmlns"))        {
            try {
                writer.setPrefix(prefix, nuri);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return prefix;
    }

    private void setWriterPrefixes(String npre, String nuri, XMLStreamWriter writer) throws XMLStreamException {
        if ((npre != null && npre.length() > 0) && (nuri.length() > 0 && nuri != null))
        {
            if ((npre.equals("null") || nuri.equals("null"))) {
                writer.setPrefix(npre, nuri);
            }
        }
    }

    private void writeAttrNamespace(String aprefix, String auri, XMLStreamWriter writer, String localName, String prefix, String nuri, String value) throws XMLStreamException {
        if (aprefix == null || !aprefix.equals("") || (aprefix.equals("xmlns")))
        {
            String temp = aprefix;
            if (auri != null) {
                String wprefix = writer.getNamespaceContext().getPrefix(auri);
                if (aprefix.equals("xmlns") && !(localName.equals("xsi"))){
                    aprefix = prefix;
                    auri = nuri;
                } else {
                    if (wprefix != null && !wprefix.equals("xmlns")) {
                        aprefix = wprefix;
                    }
                }
                if (aprefix == null)
                    convertNull(aprefix);
            }

            writeNamespace(aprefix, prefix, auri, nuri, writer);
            writeXSINamspece(localName, value, temp, writer, aprefix, auri);
        }
    }

    private void writeNamespace(String aprefix, String prefix, String auri, String nuri, XMLStreamWriter writer) throws XMLStreamException {
        if (!(aprefix.equals(prefix) && auri.equals(nuri))){
            if (!aprefix.equals("xmlns")) {
                writer.writeNamespace(aprefix, auri);
            }
        }
    }

    private void writeXSINamspece(String localName, String value, String temp, XMLStreamWriter writer, String aprefix, String auri) throws XMLStreamException {
        if (localName.equals("xsi") &&
            value.equals("http://www.w3.org/2001/XMLSchema-instance") &&
            temp.equals("xmlns")) {
            writer.setPrefix(localName, value);
            writer.writeAttribute(aprefix, auri, localName, value);
        }
    }

    private void writeAttribute(Attributes atts, int i, XMLStreamWriter writer) throws XMLStreamException {
        if ((atts.getURI(i) == null) && (atts.getPrefix(i) != null))        {
            String ns = writer.getNamespaceContext().getNamespaceURI(atts.getURI(i));
            writer.writeAttribute(atts.getPrefix(i), ns, atts.getLocalName(i),
                atts.getValue(i));
        }
        writer.writeAttribute(atts.getPrefix(i), atts.getURI(i), atts.getLocalName(i), atts.getValue(i));
    }

    void serializeSource(Object source, XMLStreamWriter writer) {
        try {
            XMLStreamReader reader = SourceReaderFactory.createSourceReader((Source) source, true);

            int state;
            do {
                state = XMLStreamReaderUtil.next(reader);
                switch (state) {
                    case START_ELEMENT:
                        QName elementName = reader.getName();
                        String localPart = elementName.getLocalPart();
                        String namespaceURI = elementName.getNamespaceURI();
                        String prefix = elementName.getPrefix();

                        writer.writeStartElement(prefix, localPart, namespaceURI);

                        Attributes atts = XMLStreamReaderUtil.getAttributes(reader);
                        writer.flush();
                        for (int i = 0; i < atts.getLength(); i++) {
                            if (atts.isNamespaceDeclaration(i)) {
                                String value = atts.getValue(i);
                                String localName = atts.getName(i).getLocalPart();
                                writer.setPrefix(localName, value);
                                writer.writeNamespace(localName, value);
                            } else {
                                writer.writeAttribute(atts.getPrefix(i), atts.getURI(i),
                                    atts.getLocalName(i), atts.getValue(i));
                            }
                        }
                        break;
                    case END_ELEMENT:
                        writer.writeEndElement();
                        break;
                    case CHARACTERS:
                        writer.writeCharacters(reader.getText());
                }
            } while (state != END_DOCUMENT);
        } catch (XMLStreamException e) {
            throw new SerializationException(e);
        }
    }

//    private void displayDOM(Node node, java.io.OutputStream ostream) {
//        try {
//            System.out.println("\n====\n");
//            javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(new javax.xml.transform.dom.DOMSource(node),
//                new javax.xml.transform.stream.StreamResult(ostream));
//            System.out.println("\n====\n");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

//    private String sourceToXMLString(Source result) {
//        String xmlResult = null;
//        try {
//            TransformerFactory factory = TransformerFactory.newInstance();
//            Transformer transformer = factory.newTransformer();
//            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
//            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
//            OutputStream out = new ByteArrayOutputStream();
//            StreamResult streamResult = new StreamResult();
//            streamResult.setOutputStream(out);
//            transformer.transform(result, streamResult);
//            xmlResult = streamResult.getOutputStream().toString();
//        } catch (TransformerException e) {
//            e.printStackTrace();
//        }
//        return xmlResult;
//    }

}
