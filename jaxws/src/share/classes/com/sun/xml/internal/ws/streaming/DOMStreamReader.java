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

package com.sun.xml.internal.ws.streaming;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;

import org.w3c.dom.*;
import static org.w3c.dom.Node.*;
import javax.xml.namespace.QName;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;

/**
 *
 * Create an XMLStreamReader on top of a DOM level 2 tree. It a DOM level
 * 1 tree is passed, each method will attempt to return the correct value
 * by using <code>getNodeName()</code>.
 *
 * @author Santiago.PericasGeertsen@sun.com
 */
public class DOMStreamReader implements XMLStreamReader, NamespaceContext {

    /**
     * Current DOM node being traversed.
     */
    Node _current;

    /**
     * Starting node of the subtree being traversed.
     */
    Node _start;

    /**
     * Named mapping for attributes and NS decls for the current node.
     */
    NamedNodeMap _namedNodeMap;

    /**
     * List of attributes extracted from <code>_namedNodeMap</code>.
     */
    List<Attr> _currentAttributes = new ArrayList<Attr>();

    /**
     * List of namespace declarations extracted from <code>_namedNodeMap</code>
     */
    List<Attr> _currentNamespaces = new ArrayList<Attr>();

    /**
     * Flag indicating if <code>_namedNodeMap</code> is already split into
     * <code>_currentAttributes</code> and <code>_currentNamespaces</code>
     */
    boolean _needAttributesSplit;

    /**
     * State of this reader. Any of the valid states defined in StAX'
     * XMLStreamConstants class.
     */
    int _state;

    /**
     * Dummy Location instance returned in <code>getLocation</code>.
     */
    private static Location dummyLocation = new Location() {
        public int getCharacterOffset() {
            return -1;
        }
        public int getColumnNumber() {
            return -1;
        }
        public int getLineNumber() {
            return -1;
        }
        public String getPublicId() {
            return null;
        }
        public String getSystemId() {
            return null;
        }
    };

    public DOMStreamReader() {
    }

    public DOMStreamReader(Node node) {
        setCurrentNode(node);
    }

    public void setCurrentNode(Node node) {
        _start = _current = node;
        _state = START_DOCUMENT;
        // verifyDOMIntegrity(node);
        // displayDOM(node, System.out);
    }

    public void close() throws javax.xml.stream.XMLStreamException {
    }


    private void splitAttributes() {
        if (!_needAttributesSplit) return;

        // Clear attribute and namespace lists
        _currentAttributes.clear();
        _currentNamespaces.clear();

        _namedNodeMap = _current.getAttributes();
        if (_namedNodeMap != null) {
            final int n = _namedNodeMap.getLength();
            for (int i = 0; i < n; i++) {
                final Attr attr = (Attr) _namedNodeMap.item(i);
                final String attrName = attr.getNodeName();
                if (attrName.startsWith("xmlns:") || attrName.equals("xmlns")) {     // NS decl?
                    _currentNamespaces.add(attr);
                }
                else {
                    _currentAttributes.add(attr);
                }
            }
        }
        _needAttributesSplit = false;
    }

    public int getAttributeCount() {
        if (_state == START_ELEMENT) {
            splitAttributes();
            return _currentAttributes.size();
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeCount() called in illegal state");
    }

    /**
     * Return an attribute's local name. Handle the case of DOM level 1 nodes.
     */
    public String getAttributeLocalName(int index) {
        if (_state == START_ELEMENT) {
            splitAttributes();

            String localName = _currentAttributes.get(index).getLocalName();
            return (localName != null) ? localName :
                QName.valueOf(_currentAttributes.get(index).getNodeName()).getLocalPart();
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeLocalName() called in illegal state");
    }

    /**
     * Return an attribute's qname. Handle the case of DOM level 1 nodes.
     */
    public QName getAttributeName(int index) {
        if (_state == START_ELEMENT) {
            splitAttributes();

            Node attr = _currentAttributes.get(index);
            String localName = attr.getLocalName();
            if (localName != null) {
                String prefix = attr.getPrefix();
                String uri = attr.getNamespaceURI();
                return new QName(uri != null ? uri : "", localName,
                    prefix != null ? prefix : "");
            }
            else {
                return QName.valueOf(attr.getNodeName());
            }
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeName() called in illegal state");
    }

    public String getAttributeNamespace(int index) {
        if (_state == START_ELEMENT) {
            splitAttributes();
            String uri = _currentAttributes.get(index).getNamespaceURI();
            return uri != null ? uri : "";
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeNamespace() called in illegal state");
    }

    public String getAttributePrefix(int index) {
        if (_state == START_ELEMENT) {
            splitAttributes();
            String prefix = _currentAttributes.get(index).getPrefix();
            return prefix != null ? prefix : "";
        }
        throw new IllegalStateException("DOMStreamReader: getAttributePrefix() called in illegal state");
    }

    public String getAttributeType(int index) {
        if (_state == START_ELEMENT) {
            return "CDATA";
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeType() called in illegal state");
    }

    public String getAttributeValue(int index) {
        if (_state == START_ELEMENT) {
            splitAttributes();
            return _currentAttributes.get(index).getNodeValue();
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeValue() called in illegal state");
    }

    public String getAttributeValue(String namespaceURI, String localName) {
        if (_state == START_ELEMENT) {
            splitAttributes();
            if (_namedNodeMap != null) {
                Node attr = _namedNodeMap.getNamedItemNS(namespaceURI, localName);
                return attr != null ? attr.getNodeValue() : null;
            }
            return null;
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeValue() called in illegal state");
    }

    public String getCharacterEncodingScheme() {
        return null;
    }

    public String getElementText() throws javax.xml.stream.XMLStreamException {
        throw new RuntimeException("DOMStreamReader: getElementText() not implemented");
    }

    public String getEncoding() {
        return null;
    }

    public int getEventType() {
        return _state;
    }

    /**
     * Return an element's local name. Handle the case of DOM level 1 nodes.
     */
    public String getLocalName() {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            String localName = _current.getLocalName();
            return localName != null ? localName :
                QName.valueOf(_current.getNodeName()).getLocalPart();
        }
        else if (_state == ENTITY_REFERENCE) {
            return _current.getNodeName();
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeValue() called in illegal state");
    }

    public javax.xml.stream.Location getLocation() {
        return dummyLocation;
    }

    /**
     * Return an element's qname. Handle the case of DOM level 1 nodes.
     */
    public javax.xml.namespace.QName getName() {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            String localName = _current.getLocalName();
            if (localName != null) {
                String prefix = _current.getPrefix();
                String uri = _current.getNamespaceURI();
                return new QName(uri != null ? uri : "", localName,
                    prefix != null ? prefix : "");
            }
            else {
                return QName.valueOf(_current.getNodeName());
            }
        }
        throw new IllegalStateException("DOMStreamReader: getName() called in illegal state");
    }

    public NamespaceContext getNamespaceContext() {
        return this;
    }

    public int getNamespaceCount() {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            splitAttributes();
            return _currentNamespaces.size();
        }
        throw new IllegalStateException("DOMStreamReader: getNamespaceCount() called in illegal state");
    }

    public String getNamespacePrefix(int index) {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            splitAttributes();

            Attr attr = _currentNamespaces.get(index);
            String result = attr.getLocalName();
            if (result == null) {
                result = QName.valueOf(attr.getNodeName()).getLocalPart();
            }
            return result.equals("xmlns") ? null : result;
        }
        throw new IllegalStateException("DOMStreamReader: getNamespacePrefix() called in illegal state");
    }

    public String getNamespaceURI() {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            String uri = _current.getNamespaceURI();
            return uri != null ? uri : "";
        }
        return null;
    }

    public String getNamespaceURI(int index) {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            splitAttributes();
            return _currentNamespaces.get(index).getValue();
        }
        throw new IllegalStateException("DOMStreamReader: getNamespaceURI(int) called in illegal state");
    }

    /**
     * This method is not particularly fast, but shouldn't be called very
     * often. If we start to use it more, we should keep track of the
     * NS declarations using a NamespaceContext implementation instead.
     */
    public String getNamespaceURI(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("DOMStreamReader: getNamespaceURI(String) call with a null prefix");
        }
        else if (prefix.equals("xml")) {
            return "http://www.w3.org/XML/1998/namespace";
        }
        else if (prefix.equals("xmlns")) {
            return "http://www.w3.org/2000/xmlns/";
        }
        else {
            int type;

            // Find nearest element node
            Node node = _current;
            while ((type = node.getNodeType()) != DOCUMENT_NODE
                    && type != ELEMENT_NODE) {
                node = node.getParentNode();
            }

            boolean isDefault = (prefix.length() == 0);

            while (node.getNodeType() != DOCUMENT_NODE) {
                // Is ns declaration on this element?
                NamedNodeMap namedNodeMap = node.getAttributes();
                Attr attr = isDefault ? (Attr) namedNodeMap.getNamedItem("xmlns") :
                                        (Attr) namedNodeMap.getNamedItem("xmlns:" + prefix);
                if (attr != null) {
                    return attr.getValue();
                }
                node = node.getParentNode();
            }
            return null;
        }
    }

    public String getPrefix(String nsUri) {
        if (nsUri == null) {
            throw new IllegalArgumentException("DOMStreamReader: getPrefix(String) call with a null namespace URI");
        }
        else if (nsUri.equals("http://www.w3.org/XML/1998/namespace")) {
            return "xml";
        }
        else if (nsUri.equals("http://www.w3.org/2000/xmlns/")) {
            return "xmlns";
        }
        else {
            int type;

            // Find nearest element node
            Node node = _current;
            while ((type = node.getNodeType()) != DOCUMENT_NODE
                    && type != ELEMENT_NODE) {
                node = node.getParentNode();
            }

            while (node.getNodeType() != DOCUMENT_NODE) {
                // Is ns declaration on this element?
                NamedNodeMap namedNodeMap = node.getAttributes();
                for( int i=namedNodeMap.getLength()-1; i>=0; i-- ) {
                    Attr attr = (Attr)namedNodeMap.item(i);

                    String attrName = attr.getNodeName();
                    if (attrName.startsWith("xmlns:") || attrName.equals("xmlns")) {     // NS decl?
                        if(attr.getValue().equals(nsUri)) {
                            if(attrName.equals("xmlns"))
                                return "";
                            String localName = attr.getLocalName();
                            return (localName != null) ? localName :
                                QName.valueOf(attrName).getLocalPart();
                        }
                    }
                }
                node = node.getParentNode();
            }
            return null;
        }
    }

    public Iterator getPrefixes(String nsUri) {
        // This is an incorrect implementation,
        // but AFAIK it's not used in the JAX-WS runtime
        String prefix = getPrefix(nsUri);
        if(prefix==null)    return Collections.emptyList().iterator();
        else                return Collections.singletonList(prefix).iterator();
    }

    public String getPIData() {
        if (_state == PROCESSING_INSTRUCTION) {
            return ((ProcessingInstruction) _current).getData();
        }
        return null;
    }

    public String getPITarget() {
        if (_state == PROCESSING_INSTRUCTION) {
            return ((ProcessingInstruction) _current).getTarget();
        }
        return null;
    }

    public String getPrefix() {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            String prefix = _current.getPrefix();
            return prefix != null ? prefix : "";
        }
        return null;
    }

    public Object getProperty(String str) throws IllegalArgumentException {
        return null;
    }

    public String getText() {
        if (_state == CHARACTERS || _state == CDATA || _state == COMMENT ||
                _state == ENTITY_REFERENCE) {
            return _current.getNodeValue();
        }
        throw new IllegalStateException("DOMStreamReader: getTextLength() called in illegal state");
    }

    public char[] getTextCharacters() {
        return getText().toCharArray();
    }

    public int getTextCharacters(int sourceStart, char[] target, int targetStart,
                                 int targetLength) throws javax.xml.stream.XMLStreamException
    {
        char[] text = getTextCharacters();
        System.arraycopy(text, sourceStart, target, targetStart, targetLength);
        return Math.min(targetLength, text.length - sourceStart);
    }

    public int getTextLength() {
        if (_state == CHARACTERS || _state == CDATA || _state == COMMENT ||
                _state == ENTITY_REFERENCE) {
            return _current.getNodeValue().length();
        }
        throw new IllegalStateException("DOMStreamReader: getTextLength() called in illegal state");
    }

    public int getTextStart() {
        if (_state == CHARACTERS || _state == CDATA || _state == COMMENT ||
                _state == ENTITY_REFERENCE) {
            return 0;
        }
        throw new IllegalStateException("DOMStreamReader: getTextStart() called in illegal state");
    }

    public String getVersion() {
        return null;
    }

    public boolean hasName() {
        return (_state == START_ELEMENT || _state == END_ELEMENT);
    }

    public boolean hasNext() throws javax.xml.stream.XMLStreamException {
        return (_state != END_DOCUMENT);
    }

    public boolean hasText() {
        if (_state == CHARACTERS || _state == CDATA || _state == COMMENT ||
                _state == ENTITY_REFERENCE) {
            return (_current.getNodeValue().trim().length() > 0);
        }
        return false;
    }

    public boolean isAttributeSpecified(int param) {
        return false;
    }

    public boolean isCharacters() {
        return (_state == CHARACTERS);
    }

    public boolean isEndElement() {
        return (_state == END_ELEMENT);
    }

    public boolean isStandalone() {
        return true;
    }

    public boolean isStartElement() {
        return (_state == START_ELEMENT);
    }

    public boolean isWhiteSpace() {
        final int nodeType = _current.getNodeType();
        if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
            return (_current.getNodeValue().trim().length() == 0);
        }
        return false;
    }

    private static int mapNodeTypeToState(int nodetype) {
        switch (nodetype) {
            case CDATA_SECTION_NODE:
                return CDATA;
            case COMMENT_NODE:
                return COMMENT;
            case ELEMENT_NODE:
                return START_ELEMENT;
            case ENTITY_NODE:
                return ENTITY_DECLARATION;
            case ENTITY_REFERENCE_NODE:
                return ENTITY_REFERENCE;
            case NOTATION_NODE:
                return NOTATION_DECLARATION;
            case PROCESSING_INSTRUCTION_NODE:
                return PROCESSING_INSTRUCTION;
            case TEXT_NODE:
                return CHARACTERS;
            default:
                throw new RuntimeException("DOMStreamReader: Unexpected node type");
        }
    }

    public int next() throws javax.xml.stream.XMLStreamException {
        Node child;

        // Indicate that attributes still need processing
        _needAttributesSplit = true;

        switch (_state) {
            case END_DOCUMENT:
                throw new IllegalStateException("DOMStreamReader: Calling next() at END_DOCUMENT");
            case START_DOCUMENT:
                // Don't skip document element if this is a fragment
                if (_current.getNodeType() == ELEMENT_NODE) {
                    return (_state = START_ELEMENT);
                }

                child = _current.getFirstChild();
                if (child == null) {
                    return (_state = END_DOCUMENT);
                }
                else {
                    _current = child;
                    return (_state = mapNodeTypeToState(_current.getNodeType()));
                }
            case START_ELEMENT:
                /*
                 * SAAJ tree may contain multiple adjacent text nodes.  Normalization
                 * is very expensive, so we should think about changing SAAJ instead!
                 */
                _current.normalize();

                child = _current.getFirstChild();
                if (child == null) {
                    return (_state = END_ELEMENT);
                }
                else {
                    _current = child;
                    return (_state = mapNodeTypeToState(_current.getNodeType()));
                }
            case CHARACTERS:
            case COMMENT:
            case CDATA:
            case ENTITY_REFERENCE:
            case PROCESSING_INSTRUCTION:
            case END_ELEMENT:
                // If at the end of this fragment, then terminate traversal
                if (_current == _start) {
                    return (_state = END_DOCUMENT);
                }

                Node sibling = _current.getNextSibling();
                if (sibling == null) {
                    _current = _current.getParentNode();
                    // getParentNode() returns null for fragments
                    _state = (_current == null || _current.getNodeType() == DOCUMENT_NODE) ?
                             END_DOCUMENT : END_ELEMENT;
                    return _state;
                }
                else {
                    _current = sibling;
                    return (_state = mapNodeTypeToState(_current.getNodeType()));
                }
            case DTD:
            case ATTRIBUTE:
            case NAMESPACE:
            default:
                throw new RuntimeException("DOMStreamReader: Unexpected internal state");
        }
    }

    public int nextTag() throws javax.xml.stream.XMLStreamException {
        int eventType = next();
        while (eventType == CHARACTERS && isWhiteSpace()
               || eventType == CDATA && isWhiteSpace()
               || eventType == SPACE
               || eventType == PROCESSING_INSTRUCTION
               || eventType == COMMENT)
        {
            eventType = next();
        }
        if (eventType != START_ELEMENT && eventType != END_ELEMENT) {
            throw new XMLStreamException("DOMStreamReader: Expected start or end tag");
        }
        return eventType;
    }

    public void require(int type, String namespaceURI, String localName)
        throws javax.xml.stream.XMLStreamException
    {
        if (type != _state) {
            throw new XMLStreamException("DOMStreamReader: Required event type not found");
        }
        if (namespaceURI != null && !namespaceURI.equals(getNamespaceURI())) {
            throw new XMLStreamException("DOMStreamReader: Required namespaceURI not found");
        }
        if (localName != null && !localName.equals(getLocalName())) {
            throw new XMLStreamException("DOMStreamReader: Required localName not found");
        }
    }

    public boolean standaloneSet() {
        return true;
    }



    // -- Debugging ------------------------------------------------------

    private static void displayDOM(Node node, java.io.OutputStream ostream) {
        try {
            System.out.println("\n====\n");
            javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
                new javax.xml.transform.dom.DOMSource(node),
                new javax.xml.transform.stream.StreamResult(ostream));
            System.out.println("\n====\n");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void verifyDOMIntegrity(Node node) {
        switch (node.getNodeType()) {
            case ELEMENT_NODE:
            case ATTRIBUTE_NODE:

                // DOM level 1?
                if (node.getLocalName() == null) {
                    System.out.println("WARNING: DOM level 1 node found");
                    System.out.println(" -> node.getNodeName() = " + node.getNodeName());
                    System.out.println(" -> node.getNamespaceURI() = " + node.getNamespaceURI());
                    System.out.println(" -> node.getLocalName() = " + node.getLocalName());
                    System.out.println(" -> node.getPrefix() = " + node.getPrefix());
                }

                if (node.getNodeType() == ATTRIBUTE_NODE) return;

                NamedNodeMap attrs = ((Element) node).getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    verifyDOMIntegrity(attrs.item(i));
                }
            case DOCUMENT_NODE:
                NodeList children = node.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    verifyDOMIntegrity(children.item(i));
                }
        }
    }

    static public void main(String[] args) throws Exception {
        String sample = "<?xml version='1.0' encoding='UTF-8'?><env:Envelope xmlns:env='http://schemas.xmlsoap.org/soap/envelope/'><env:Body><env:Fault><faultcode>env:Server</faultcode><faultstring>Internal server error</faultstring></env:Fault></env:Body></env:Envelope>";
        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
        Document dd = db.parse(new java.io.ByteArrayInputStream(sample.getBytes("UTF-8")));

        DOMStreamReader dsr = new DOMStreamReader(dd);
        while (dsr.hasNext()) {
            System.out.println("dsr.next() = " + dsr.next());
            if (dsr.getEventType() == START_ELEMENT || dsr.getEventType() == END_ELEMENT) {
                System.out.println("dsr.getName = " + dsr.getName());
                if (dsr.getEventType() == START_ELEMENT)
                    System.out.println("dsr.getAttributeCount() = " + dsr.getAttributeCount());
            }
        }
    }
}
