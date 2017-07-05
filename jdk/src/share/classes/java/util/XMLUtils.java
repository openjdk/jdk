/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

/**
 * A class used to aid in Properties load and save in XML. Keeping this
 * code outside of Properties helps reduce the number of classes loaded
 * when Properties is loaded.
 *
 * @author  Michael McCloskey
 * @since   1.3
 */
class XMLUtils {

    // XML loading and saving methods for Properties

    // The required DTD URI for exported properties
    private static final String PROPS_DTD_URI =
    "http://java.sun.com/dtd/properties.dtd";

    private static final String PROPS_DTD =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
    "<!-- DTD for properties -->"                +
    "<!ELEMENT properties ( comment?, entry* ) >"+
    "<!ATTLIST properties"                       +
        " version CDATA #FIXED \"1.0\">"         +
    "<!ELEMENT comment (#PCDATA) >"              +
    "<!ELEMENT entry (#PCDATA) >"                +
    "<!ATTLIST entry "                           +
        " key CDATA #REQUIRED>";

    /**
     * Version number for the format of exported properties files.
     */
    private static final String EXTERNAL_XML_VERSION = "1.0";

    static void load(Properties props, InputStream in)
        throws IOException, InvalidPropertiesFormatException
    {
        Document doc = null;
        try {
            doc = getLoadingDoc(in);
        } catch (SAXException saxe) {
            throw new InvalidPropertiesFormatException(saxe);
        }
        Element propertiesElement = (Element)doc.getChildNodes().item(1);
        String xmlVersion = propertiesElement.getAttribute("version");
        if (xmlVersion.compareTo(EXTERNAL_XML_VERSION) > 0)
            throw new InvalidPropertiesFormatException(
                "Exported Properties file format version " + xmlVersion +
                " is not supported. This java installation can read" +
                " versions " + EXTERNAL_XML_VERSION + " or older. You" +
                " may need to install a newer version of JDK.");
        importProperties(props, propertiesElement);
    }

    static Document getLoadingDoc(InputStream in)
        throws SAXException, IOException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setIgnoringElementContentWhitespace(true);
        dbf.setValidating(true);
        dbf.setCoalescing(true);
        dbf.setIgnoringComments(true);
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setEntityResolver(new Resolver());
            db.setErrorHandler(new EH());
            InputSource is = new InputSource(in);
            return db.parse(is);
        } catch (ParserConfigurationException x) {
            throw new Error(x);
        }
    }

    static void importProperties(Properties props, Element propertiesElement) {
        NodeList entries = propertiesElement.getChildNodes();
        int numEntries = entries.getLength();
        int start = numEntries > 0 &&
            entries.item(0).getNodeName().equals("comment") ? 1 : 0;
        for (int i=start; i<numEntries; i++) {
            Element entry = (Element)entries.item(i);
            if (entry.hasAttribute("key")) {
                Node n = entry.getFirstChild();
                String val = (n == null) ? "" : n.getNodeValue();
                props.setProperty(entry.getAttribute("key"), val);
            }
        }
    }

    static void save(Properties props, OutputStream os, String comment,
                     String encoding)
        throws IOException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException pce) {
            assert(false);
        }
        Document doc = db.newDocument();
        Element properties =  (Element)
            doc.appendChild(doc.createElement("properties"));

        if (comment != null) {
            Element comments = (Element)properties.appendChild(
                doc.createElement("comment"));
            comments.appendChild(doc.createTextNode(comment));
        }

        synchronized (props) {
            for (String key : props.stringPropertyNames()) {
                Element entry = (Element)properties.appendChild(
                    doc.createElement("entry"));
                entry.setAttribute("key", key);
                entry.appendChild(doc.createTextNode(props.getProperty(key)));
            }
        }
        emitDocument(doc, os, encoding);
    }

    static void emitDocument(Document doc, OutputStream os, String encoding)
        throws IOException
    {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = null;
        try {
            t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, PROPS_DTD_URI);
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty(OutputKeys.METHOD, "xml");
            t.setOutputProperty(OutputKeys.ENCODING, encoding);
        } catch (TransformerConfigurationException tce) {
            assert(false);
        }
        DOMSource doms = new DOMSource(doc);
        StreamResult sr = new StreamResult(os);
        try {
            t.transform(doms, sr);
        } catch (TransformerException te) {
            IOException ioe = new IOException();
            ioe.initCause(te);
            throw ioe;
        }
    }

    private static class Resolver implements EntityResolver {
        public InputSource resolveEntity(String pid, String sid)
            throws SAXException
        {
            if (sid.equals(PROPS_DTD_URI)) {
                InputSource is;
                is = new InputSource(new StringReader(PROPS_DTD));
                is.setSystemId(PROPS_DTD_URI);
                return is;
            }
            throw new SAXException("Invalid system identifier: " + sid);
        }
    }

    private static class EH implements ErrorHandler {
        public void error(SAXParseException x) throws SAXException {
            throw x;
        }
        public void fatalError(SAXParseException x) throws SAXException {
            throw x;
        }
        public void warning(SAXParseException x) throws SAXException {
            throw x;
        }
    }

}
