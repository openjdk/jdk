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

package com.sun.xml.internal.ws.message.source;

import com.sun.xml.internal.ws.message.RootElementSniffer;
import com.sun.xml.internal.ws.streaming.SourceReaderFactory;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.WebServiceException;

/**
 *
 * @author Vivek Pandey
 */
final class SourceUtils {

    int srcType;

    private final int domSource = 1;
    private final int streamSource = 2;
    private final int saxSource=4;

    public SourceUtils(Source src) {
        if(src instanceof StreamSource){
            srcType = streamSource;
        }else if(src instanceof DOMSource){
            srcType = domSource;
        }else if(src instanceof SAXSource){
            srcType = saxSource;
        }
    }

    public boolean isDOMSource(){
        return (srcType&domSource) == domSource;
    }

    public boolean isStreamSource(){
        return (srcType&streamSource) == streamSource;
    }

    public boolean isSaxSource(){
        return (srcType&saxSource) == saxSource;
    }

    /**
     * This would peek into the Source (DOMSource and SAXSource) for the localName and NamespaceURI
     * of the top-level element.
     * @param src
     * @return QName of the payload
     */
    public QName sniff(Source src) {
        return sniff(src, new RootElementSniffer());
    }

    public QName sniff(Source src, RootElementSniffer sniffer){
        String localName = null;
        String namespaceUri = null;

        if(isDOMSource()){
            DOMSource domSource = (DOMSource)src;
            Node n = domSource.getNode();
            if(n.getNodeType()== Node.DOCUMENT_NODE) {
                n = ((Document)n).getDocumentElement();
            }
            localName = n.getLocalName();
            namespaceUri = n.getNamespaceURI();
        }else if(isSaxSource()){
            SAXSource saxSrc = (SAXSource)src;
            SAXResult saxResult = new SAXResult(sniffer);
            try {
                Transformer tr = XmlUtil.newTransformer();
                tr.transform(saxSrc, saxResult);
            } catch (TransformerConfigurationException e) {
                throw new WebServiceException(e);
            } catch (TransformerException e) {
                // if it's due to aborting the processing after the first element,
                // we can safely ignore this exception.
                //
                // if it's due to error in the object, the same error will be reported
                // when the readHeader() method is used, so we don't have to report
                // an error right now.
                localName = sniffer.getLocalName();
                namespaceUri = sniffer.getNsUri();
            }
        }
        return new QName(namespaceUri, localName);
    }

    public static void serializeSource(Source src, XMLStreamWriter writer) throws XMLStreamException {
        XMLStreamReader reader = SourceReaderFactory.createSourceReader(src, true);
        int state;
        do {
            state = reader.next();
            switch (state) {
                case XMLStreamConstants.START_ELEMENT:
                    /*
                     * TODO: Is this necessary, shouldn't zephyr return "" instead of
                     * null for getNamespaceURI() and getPrefix()?
                     */
                    String uri = reader.getNamespaceURI();
                    String prefix = reader.getPrefix();
                    String localName = reader.getLocalName();

                    if (prefix == null) {
                        if (uri == null) {
                            writer.writeStartElement(localName);
                        } else {
                            writer.writeStartElement(uri, localName);
                        }
                    } else {
                        assert uri != null;

                        if(prefix.length() > 0){
                            /**
                             * Before we write the
                             */
                            String writerURI = null;
                            if (writer.getNamespaceContext() != null)
                                writerURI = writer.getNamespaceContext().getNamespaceURI(prefix);
                            String writerPrefix = writer.getPrefix(uri);
                            if(declarePrefix(prefix, uri, writerPrefix, writerURI)){
                                writer.writeStartElement(prefix, localName, uri);
                                writer.setPrefix(prefix, uri != null ? uri : "");
                                writer.writeNamespace(prefix, uri);
                            }else{
                                writer.writeStartElement(prefix, localName, uri);
                            }
                        }else{
                            writer.writeStartElement(prefix, localName, uri);
                        }
                    }

                    int n = reader.getNamespaceCount();
                    // Write namespace declarations
                    for (int i = 0; i < n; i++) {
                        String nsPrefix = reader.getNamespacePrefix(i);
                        if (nsPrefix == null) nsPrefix = "";
                        // StAX returns null for default ns
                        String writerURI = null;
                        if (writer.getNamespaceContext() != null)
                            writerURI = writer.getNamespaceContext().getNamespaceURI(nsPrefix);

                        // Zephyr: Why is this returning null?
                        // Compare nsPrefix with prefix because of [1] (above)
                        String readerURI = reader.getNamespaceURI(i);

                        /**
                         * write the namespace in 3 conditions
                         *  - when the namespace URI is not bound to the prefix in writer(writerURI == 0)
                         *  - when the readerPrefix and writerPrefix are ""
                         *  - when readerPrefix and writerPrefix are not equal and the URI bound to them
                         *    are different
                         */
                        if (writerURI == null || ((nsPrefix.length() == 0) || (prefix.length() == 0)) ||
                                (!nsPrefix.equals(prefix) && !writerURI.equals(readerURI))) {
                            writer.setPrefix(nsPrefix, readerURI != null ? readerURI : "");
                            writer.writeNamespace(nsPrefix, readerURI != null ? readerURI : "");
                        }
                    }

                    // Write attributes
                    n = reader.getAttributeCount();
                    for (int i = 0; i < n; i++) {
                        String attrPrefix = reader.getAttributePrefix(i);
                        String attrURI = reader.getAttributeNamespace(i);

                        writer.writeAttribute(attrPrefix != null ? attrPrefix : "",
                            attrURI != null ? attrURI : "",
                            reader.getAttributeLocalName(i),
                            reader.getAttributeValue(i));
                        // if the attribute prefix is undeclared in current writer scope then declare it
                        setUndeclaredPrefix(attrPrefix, attrURI, writer);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    writer.writeEndElement();
                    break;
                case XMLStreamConstants.CHARACTERS:
                    writer.writeCharacters(reader.getText());
            }
        } while (state != XMLStreamConstants.END_DOCUMENT);
        reader.close();
    }

    /**
     * sets undeclared prefixes on the writer
     * @param prefix
     * @param writer
     * @throws XMLStreamException
     */
    private static void setUndeclaredPrefix(String prefix, String readerURI, XMLStreamWriter writer) throws XMLStreamException {
        String writerURI = null;
        if (writer.getNamespaceContext() != null)
            writerURI = writer.getNamespaceContext().getNamespaceURI(prefix);

        if (writerURI == null) {
            writer.setPrefix(prefix, readerURI != null ? readerURI : "");
            writer.writeNamespace(prefix, readerURI != null ? readerURI : "");
        }
    }

    /**
     * check if we need to declare
     * @param rPrefix
     * @param rUri
     * @param wPrefix
     * @param wUri
     */
    private static boolean declarePrefix(String rPrefix, String rUri, String wPrefix, String wUri){
        if (wUri == null ||((wPrefix != null) && !rPrefix.equals(wPrefix))||
                (rUri != null && !wUri.equals(rUri)))
            return true;
        return false;
    }
}
