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

package com.sun.xml.internal.ws.streaming;

import com.sun.istack.internal.FinalArrayList;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.XMLStreamException2;
import com.sun.xml.internal.ws.util.xml.DummyLocation;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import static org.w3c.dom.Node.*;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.Collections;
import java.util.Iterator;

/**
 * Create an {@link XMLStreamReader} on top of a DOM tree.
 *
 * <p>
 * Since various libraries as well as users often create "incorrect" DOM node,
 * this class spends a lot of efforts making sure that broken DOM trees are
 * nevertheless interpreted correctly.
 *
 * <p>
 * For example, if a DOM level
 * 1 tree is passed, each method will attempt to return the correct value
 * by using {@link Node#getNodeName()}.
 *
 * <p>
 * Similarly, if DOM is missing explicit namespace declarations,
 * this class attempts to emulate necessary declarations.
 *
 *
 * @author Santiago.PericasGeertsen@sun.com
 * @author Kohsuke Kawaguchi
 */
public final class DOMStreamReader implements XMLStreamReader, NamespaceContext {

    /**
     * Current DOM node being traversed.
     */
    private Node _current;

    /**
     * Starting node of the subtree being traversed.
     */
    private Node _start;

    /**
     * Named mapping for attributes and NS decls for the current node.
     */
    private NamedNodeMap _namedNodeMap;

    /**
     * If the reader points at {@link #CHARACTERS the text node},
     * its whole value.
     *
     * <p>
     * This is simply a cache of {@link Text#getWholeText()} of {@link #_current},
     * but when a large binary data sent as base64 text, this could get very much
     * non-trivial.
     */
    private String wholeText;

    /**
     * List of attributes extracted from <code>_namedNodeMap</code>.
     */
    private final FinalArrayList<Attr> _currentAttributes = new FinalArrayList<Attr>();

    /**
     * {@link Scope} buffer.
     */
    private Scope[] scopes = new Scope[8];

    /**
     * Depth of the current element. The first element gets depth==0.
     * Also used as the index to {@link #scopes}.
     */
    private int depth = 0;

    /**
     * State of this reader. Any of the valid states defined in StAX'
     * XMLStreamConstants class.
     */
    int _state;

    /**
     * Namespace declarations on one element.
     *
     * Instances are reused.
     */
    private static final class Scope {
        /**
         * Scope for the parent element.
         */
        final Scope parent;

        /**
         * List of namespace declarations extracted from <code>_namedNodeMap</code>
         */
        final FinalArrayList<Attr> currentNamespaces = new FinalArrayList<Attr>();

        /**
         * Additional namespace declarations obtained as a result of "fixing" DOM tree,
         * which were not part of the original DOM tree.
         *
         * One entry occupies two spaces (prefix followed by URI.)
         */
        final FinalArrayList<String> additionalNamespaces = new FinalArrayList<String>();

        Scope(Scope parent) {
            this.parent = parent;
        }

        void reset() {
            currentNamespaces.clear();
            additionalNamespaces.clear();
        }

        int getNamespaceCount() {
            return currentNamespaces.size()+additionalNamespaces.size()/2;
        }

        String getNamespacePrefix(int index) {
            int sz = currentNamespaces.size();
            if(index< sz) {
                Attr attr = currentNamespaces.get(index);
                String result = attr.getLocalName();
                if (result == null) {
                    result = QName.valueOf(attr.getNodeName()).getLocalPart();
                }
                return result.equals("xmlns") ? null : result;
            } else {
                return additionalNamespaces.get((index-sz)*2);
            }
        }

        String getNamespaceURI(int index) {
            int sz = currentNamespaces.size();
            if(index< sz) {
                return currentNamespaces.get(index).getValue();
            } else {
                return additionalNamespaces.get((index-sz)*2+1);
            }
        }

        /**
         * Returns the prefix bound to the given URI, or null.
         * This method recurses to the parent.
         */
        String getPrefix(String nsUri) {
            for( Scope sp=this; sp!=null; sp=sp.parent ) {
                for( int i=sp.currentNamespaces.size()-1; i>=0; i--) {
                    String result = getPrefixForAttr(sp.currentNamespaces.get(i),nsUri);
                    if(result!=null)
                        return result;
                }
                for( int i=sp.additionalNamespaces.size()-2; i>=0; i-=2 )
                    if(sp.additionalNamespaces.get(i+1).equals(nsUri))
                        return sp.additionalNamespaces.get(i);
            }
            return null;
        }

        /**
         * Returns the namespace URI bound by the given prefix.
         *
         * @param prefix
         *      Prefix to look up.
         */
        String getNamespaceURI(@NotNull String prefix) {
            String nsDeclName = prefix.length()==0 ? "xmlns" : "xmlns:"+prefix;

            for( Scope sp=this; sp!=null; sp=sp.parent ) {
                for( int i=sp.currentNamespaces.size()-1; i>=0; i--) {
                    Attr a = sp.currentNamespaces.get(i);
                    if(a.getNodeName().equals(nsDeclName))
                        return a.getValue();
                }
                for( int i=sp.additionalNamespaces.size()-2; i>=0; i-=2 )
                    if(sp.additionalNamespaces.get(i).equals(prefix))
                        return sp.additionalNamespaces.get(i+1);
            }
            return null;
        }
    }


    public DOMStreamReader() {
    }

    public DOMStreamReader(Node node) {
        setCurrentNode(node);
    }

    public void setCurrentNode(Node node) {
        scopes[0] = new Scope(null);
        depth=0;

        _start = _current = node;
        _state = START_DOCUMENT;
        // verifyDOMIntegrity(node);
        // displayDOM(node, System.out);
    }

    public void close() throws XMLStreamException {
    }

    /**
     * Called when the current node is {@link Element} to look at attribute list
     * (which contains both ns decl and attributes in DOM) and split them
     * to attributes-proper and namespace decls.
     */
    private void splitAttributes() {
        // Clear attribute and namespace lists
        _currentAttributes.clear();

        Scope scope = allocateScope();

        _namedNodeMap = _current.getAttributes();
        if (_namedNodeMap != null) {
            final int n = _namedNodeMap.getLength();
            for (int i = 0; i < n; i++) {
                final Attr attr = (Attr) _namedNodeMap.item(i);
                final String attrName = attr.getNodeName();
                if (attrName.startsWith("xmlns:") || attrName.equals("xmlns")) {     // NS decl?
                    scope.currentNamespaces.add(attr);
                }
                else {
                    _currentAttributes.add(attr);
                }
            }
        }

        // verify that all the namespaces used in element and attributes are indeed available
        ensureNs(_current);
        for( int i=_currentAttributes.size()-1; i>=0; i-- ) {
            Attr a = _currentAttributes.get(i);
            if(fixNull(a.getNamespaceURI()).length()>0)
                ensureNs(a);    // no need to declare "" for attributes in the default namespace
        }
    }

    /**
     * Sub-routine of {@link #splitAttributes()}.
     *
     * <p>
     * Makes sure that the namespace URI/prefix used in the given node is available,
     * and if not, declare it on the current scope to "fix" it.
     *
     * It's often common to create DOM trees without putting namespace declarations,
     * and this makes sure that such DOM tree will be properly marshalled.
     */
    private void ensureNs(Node n) {
        String prefix = fixNull(n.getPrefix());
        String uri = fixNull(n.getNamespaceURI());

        Scope scope = scopes[depth];

        String currentUri = scope.getNamespaceURI(prefix);

        if(prefix.length()==0) {
            currentUri = fixNull(currentUri);
            if(currentUri.equals(uri))
                return; // declared correctly
        } else {
            if(currentUri!=null && currentUri.equals(uri))
                return; // declared correctly
        }

        if(prefix.equals("xml") || prefix.equals("xmlns"))
            return; // implicitly declared namespaces

        // needs to be declared
        scope.additionalNamespaces.add(prefix);
        scope.additionalNamespaces.add(uri);
    }

    /**
     * Allocate new {@link Scope} for {@link #splitAttributes()}.
     */
    private Scope allocateScope() {
        if(scopes.length==++depth) {
            Scope[] newBuf = new Scope[scopes.length*2];
            System.arraycopy(scopes,0,newBuf,0,scopes.length);
            scopes = newBuf;
        }
        Scope scope = scopes[depth];
        if(scope==null) {
            scope = scopes[depth] = new Scope(scopes[depth-1]);
        } else {
            scope.reset();
        }
        return scope;
    }

    public int getAttributeCount() {
        if (_state == START_ELEMENT)
            return _currentAttributes.size();
        throw new IllegalStateException("DOMStreamReader: getAttributeCount() called in illegal state");
    }

    /**
     * Return an attribute's local name. Handle the case of DOM level 1 nodes.
     */
    public String getAttributeLocalName(int index) {
        if (_state == START_ELEMENT) {
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
            Node attr = _currentAttributes.get(index);
            String localName = attr.getLocalName();
            if (localName != null) {
                String prefix = attr.getPrefix();
                String uri = attr.getNamespaceURI();
                return new QName(fixNull(uri), localName, fixNull(prefix));
            }
            else {
                return QName.valueOf(attr.getNodeName());
            }
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeName() called in illegal state");
    }

    public String getAttributeNamespace(int index) {
        if (_state == START_ELEMENT) {
            String uri = _currentAttributes.get(index).getNamespaceURI();
            return fixNull(uri);
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeNamespace() called in illegal state");
    }

    public String getAttributePrefix(int index) {
        if (_state == START_ELEMENT) {
            String prefix = _currentAttributes.get(index).getPrefix();
            return fixNull(prefix);
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
            return _currentAttributes.get(index).getNodeValue();
        }
        throw new IllegalStateException("DOMStreamReader: getAttributeValue() called in illegal state");
    }

    public String getAttributeValue(String namespaceURI, String localName) {
        if (_state == START_ELEMENT) {
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

    public Location getLocation() {
        return DummyLocation.INSTANCE;
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
                return new QName(fixNull(uri), localName, fixNull(prefix));
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

    /**
     * Verifies the current state to see if we can return the scope, and do so
     * if appropriate.
     *
     * Used to implement a bunch of StAX API methods that have the same usage restriction.
     */
    private Scope getCheckedScope() {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            return scopes[depth];
        }
        throw new IllegalStateException("DOMStreamReader: neither on START_ELEMENT nor END_ELEMENT");
    }

    public int getNamespaceCount() {
        return getCheckedScope().getNamespaceCount();
    }

    public String getNamespacePrefix(int index) {
        return getCheckedScope().getNamespacePrefix(index);
    }

    public String getNamespaceURI(int index) {
        return getCheckedScope().getNamespaceURI(index);
    }

    public String getNamespaceURI() {
        if (_state == START_ELEMENT || _state == END_ELEMENT) {
            String uri = _current.getNamespaceURI();
            return fixNull(uri);
        }
        return null;
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

        // check scopes
        String nsUri = scopes[depth].getNamespaceURI(prefix);
        if(nsUri!=null)    return nsUri;

        // then ancestors above start node
        Node node = findRootElement();
        String nsDeclName = prefix.length()==0 ? "xmlns" : "xmlns:"+prefix;
        while (node.getNodeType() != DOCUMENT_NODE) {
            // Is ns declaration on this element?
            NamedNodeMap namedNodeMap = node.getAttributes();
            Attr attr = (Attr) namedNodeMap.getNamedItem(nsDeclName);
            if (attr != null)
                return attr.getValue();
            node = node.getParentNode();
        }
        return null;
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

        // check scopes
        String prefix = scopes[depth].getPrefix(nsUri);
        if(prefix!=null)    return prefix;

        // then ancestors above start node
        Node node = findRootElement();

        while (node.getNodeType() != DOCUMENT_NODE) {
            // Is ns declaration on this element?
            NamedNodeMap namedNodeMap = node.getAttributes();
            for( int i=namedNodeMap.getLength()-1; i>=0; i-- ) {
                Attr attr = (Attr)namedNodeMap.item(i);
                prefix = getPrefixForAttr(attr,nsUri);
                if(prefix!=null)
                    return prefix;
            }
            node = node.getParentNode();
        }
        return null;
    }

    /**
     * Finds the root element node of the traversal.
     */
    private Node findRootElement() {
        int type;

        Node node = _start;
        while ((type = node.getNodeType()) != DOCUMENT_NODE
                && type != ELEMENT_NODE) {
            node = node.getParentNode();
        }
        return node;
    }

    /**
     * If the given attribute is a namespace declaration for the given namespace URI,
     * return its prefix. Otherwise null.
     */
    private static String getPrefixForAttr(Attr attr, String nsUri) {
        String attrName = attr.getNodeName();
        if (!attrName.startsWith("xmlns:") && !attrName.equals("xmlns"))
            return null;    // not nsdecl

        if(attr.getValue().equals(nsUri)) {
            if(attrName.equals("xmlns"))
                return "";
            String localName = attr.getLocalName();
            return (localName != null) ? localName :
                QName.valueOf(attrName).getLocalPart();
        }

        return null;
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
            return fixNull(prefix);
        }
        return null;
    }

    public Object getProperty(String str) throws IllegalArgumentException {
        return null;
    }

    public String getText() {
        if (_state == CHARACTERS)
            return wholeText;
        if(_state == CDATA || _state == COMMENT || _state == ENTITY_REFERENCE)
            return _current.getNodeValue();
        throw new IllegalStateException("DOMStreamReader: getTextLength() called in illegal state");
    }

    public char[] getTextCharacters() {
        return getText().toCharArray();
    }

    public int getTextCharacters(int sourceStart, char[] target, int targetStart,
                                 int targetLength) throws XMLStreamException {
        String text = getText();
        int copiedSize = Math.min(targetLength, text.length() - sourceStart);
        text.getChars(sourceStart, sourceStart + copiedSize, target, targetStart);

        return copiedSize;
    }

    public int getTextLength() {
        return getText().length();
    }

    public int getTextStart() {
        if (_state == CHARACTERS || _state == CDATA || _state == COMMENT || _state == ENTITY_REFERENCE) {
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
        if (_state == CHARACTERS || _state == CDATA || _state == COMMENT || _state == ENTITY_REFERENCE) {
            return getText().trim().length() > 0;
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
        if (_state == CHARACTERS || _state == CDATA)
            return getText().trim().length()==0;
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

    public int next() throws XMLStreamException {
        while(true) {
            int r = _next();
            switch (r) {
            case CHARACTERS:
                // if we are currently at text node, make sure that this is a meaningful text node.
                Node prev = _current.getPreviousSibling();
                if(prev!=null && prev.getNodeType()==Node.TEXT_NODE)
                    continue;   // nope. this is just a continuation of previous text that should be invisible

                Text t = (Text)_current;
                wholeText = t.getWholeText();
                if(wholeText.length()==0)
                    continue;   // nope. this is empty text.
                return CHARACTERS;
            case START_ELEMENT:
                splitAttributes();
                return START_ELEMENT;
            default:
                return r;
            }
        }
    }

    private int _next() throws XMLStreamException {
        Node child;

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
                child = _current.getFirstChild();
                if (child == null) {
                    return (_state = END_ELEMENT);
                }
                else {
                    _current = child;
                    return (_state = mapNodeTypeToState(_current.getNodeType()));
                }
            case END_ELEMENT:
                depth--;
                // fall through next
            case CHARACTERS:
            case COMMENT:
            case CDATA:
            case ENTITY_REFERENCE:
            case PROCESSING_INSTRUCTION:
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
            throw new XMLStreamException2("DOMStreamReader: Expected start or end tag");
        }
        return eventType;
    }

    public void require(int type, String namespaceURI, String localName)
        throws javax.xml.stream.XMLStreamException
    {
        if (type != _state) {
            throw new XMLStreamException2("DOMStreamReader: Required event type not found");
        }
        if (namespaceURI != null && !namespaceURI.equals(getNamespaceURI())) {
            throw new XMLStreamException2("DOMStreamReader: Required namespaceURI not found");
        }
        if (localName != null && !localName.equals(getLocalName())) {
            throw new XMLStreamException2("DOMStreamReader: Required localName not found");
        }
    }

    public boolean standaloneSet() {
        return true;
    }



    // -- Debugging ------------------------------------------------------

    private static void displayDOM(Node node, java.io.OutputStream ostream) {
        try {
            System.out.println("\n====\n");
            XmlUtil.newTransformer().transform(
                new DOMSource(node), new StreamResult(ostream));
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

                NamedNodeMap attrs = node.getAttributes();
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


    private static String fixNull(String s) {
        if(s==null) return "";
        else        return s;
    }
}
