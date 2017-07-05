/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: SAX2DOM.java,v 1.8.2.1 2006/12/04 18:45:41 spericas Exp $
 */


package com.sun.org.apache.xalan.internal.xsltc.trax;

import java.util.Stack;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xalan.internal.xsltc.runtime.Constants;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.ext.Locator2;

/**
 * @author G. Todd Miller
 * @author Sunitha Reddy
 */
public class SAX2DOM implements ContentHandler, LexicalHandler, Constants {

    private Node _root = null;
    private Document _document = null;
    private Node _nextSibling = null;
    private Stack _nodeStk = new Stack();
    private Vector _namespaceDecls = null;
    private Node _lastSibling = null;
    private Locator locator = null;
    private boolean needToSetDocumentInfo = true;

    /**
     * JAXP document builder factory. Create a single instance and use
     * synchronization because the Javadoc is not explicit about
     * thread safety.
     */
    static final DocumentBuilderFactory _factory =
            DocumentBuilderFactory.newInstance();

   public SAX2DOM() throws ParserConfigurationException {
        synchronized (SAX2DOM.class) {
          _document = _factory.newDocumentBuilder().newDocument();
        }
        _root = _document;
    }

    public SAX2DOM(Node root, Node nextSibling) throws ParserConfigurationException {
        _root = root;
        if (root instanceof Document) {
          _document = (Document)root;
        }
        else if (root != null) {
          _document = root.getOwnerDocument();
        }
        else {
          synchronized (SAX2DOM.class) {
              _document = _factory.newDocumentBuilder().newDocument();
          }
          _root = _document;
        }

        _nextSibling = nextSibling;
    }

    public SAX2DOM(Node root) throws ParserConfigurationException {
        this(root, null);
    }

    public Node getDOM() {
        return _root;
    }

    public void characters(char[] ch, int start, int length) {
        // Ignore text nodes of length 0
        if (length == 0) {
            return;
        }

        final Node last = (Node)_nodeStk.peek();

        // No text nodes can be children of root (DOM006 exception)
        if (last != _document) {
            final String text = new String(ch, start, length);
            if( _lastSibling != null && _lastSibling.getNodeType() == Node.TEXT_NODE ){
                  ((Text)_lastSibling).appendData(text);
            }
            else if (last == _root && _nextSibling != null) {
                _lastSibling = last.insertBefore(_document.createTextNode(text), _nextSibling);
            }
            else {
                _lastSibling = last.appendChild(_document.createTextNode(text));
            }
        }
    }

    public void startDocument() {
        _nodeStk.push(_root);
    }

    public void endDocument() {
        _nodeStk.pop();
    }

    private void setDocumentInfo() {
        //try to set document version
        if (locator == null) return;
        try{
            _document.setXmlVersion(((Locator2)locator).getXMLVersion());
        }catch(ClassCastException e){}

    }

    public void startElement(String namespace, String localName, String qName,
        Attributes attrs)
    {

        if (needToSetDocumentInfo) {
            setDocumentInfo();
            needToSetDocumentInfo = false;
        }

        final Element tmp = (Element)_document.createElementNS(namespace, qName);

        // Add namespace declarations first
        if (_namespaceDecls != null) {
            final int nDecls = _namespaceDecls.size();
            for (int i = 0; i < nDecls; i++) {
                final String prefix = (String) _namespaceDecls.elementAt(i++);

                if (prefix == null || prefix.equals(EMPTYSTRING)) {
                    tmp.setAttributeNS(XMLNS_URI, XMLNS_PREFIX,
                        (String) _namespaceDecls.elementAt(i));
                }
                else {
                    tmp.setAttributeNS(XMLNS_URI, XMLNS_STRING + prefix,
                        (String) _namespaceDecls.elementAt(i));
                }
            }
            _namespaceDecls.clear();
        }

        // Add attributes to element
/*      final int nattrs = attrs.getLength();
        for (int i = 0; i < nattrs; i++) {
            if (attrs.getLocalName(i) == null) {
                tmp.setAttribute(attrs.getQName(i), attrs.getValue(i));
            }
            else {
                tmp.setAttributeNS(attrs.getURI(i), attrs.getQName(i),
                    attrs.getValue(i));
            }
        } */


        // Add attributes to element
        final int nattrs = attrs.getLength();
        for (int i = 0; i < nattrs; i++) {
            // checking if Namespace processing is being done
            String attQName = attrs.getQName(i);
            String attURI = attrs.getURI(i);
            if (attrs.getLocalName(i).equals("")) {
                tmp.setAttribute(attQName, attrs.getValue(i));
                if (attrs.getType(i).equals("ID")) {
                    tmp.setIdAttribute(attQName, true);
                }
            } else {
                tmp.setAttributeNS(attURI, attQName, attrs.getValue(i));
                if (attrs.getType(i).equals("ID")) {
                    tmp.setIdAttributeNS(attURI, attrs.getLocalName(i), true);
                }
            }
        }


        // Append this new node onto current stack node
        Node last = (Node)_nodeStk.peek();

        // If the SAX2DOM is created with a non-null next sibling node,
        // insert the result nodes before the next sibling under the root.
        if (last == _root && _nextSibling != null)
            last.insertBefore(tmp, _nextSibling);
        else
            last.appendChild(tmp);

        // Push this node onto stack
        _nodeStk.push(tmp);
        _lastSibling = null;
    }

    public void endElement(String namespace, String localName, String qName) {
        _nodeStk.pop();
        _lastSibling = null;
    }

    public void startPrefixMapping(String prefix, String uri) {
        if (_namespaceDecls == null) {
            _namespaceDecls = new Vector(2);
        }
        _namespaceDecls.addElement(prefix);
        _namespaceDecls.addElement(uri);
    }

    public void endPrefixMapping(String prefix) {
        // do nothing
    }

    /**
     * This class is only used internally so this method should never
     * be called.
     */
    public void ignorableWhitespace(char[] ch, int start, int length) {
    }

    /**
     * adds processing instruction node to DOM.
     */
    public void processingInstruction(String target, String data) {
        final Node last = (Node)_nodeStk.peek();
        ProcessingInstruction pi = _document.createProcessingInstruction(
                target, data);
        if (pi != null){
          if (last == _root && _nextSibling != null)
              last.insertBefore(pi, _nextSibling);
          else
              last.appendChild(pi);

          _lastSibling = pi;
        }
    }

    /**
     * This class is only used internally so this method should never
     * be called.
     */
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    /**
     * This class is only used internally so this method should never
     * be called.
     */
    public void skippedEntity(String name) {
    }


    /**
     * Lexical Handler method to create comment node in DOM tree.
     */
    public void comment(char[] ch, int start, int length) {
        final Node last = (Node)_nodeStk.peek();
        Comment comment = _document.createComment(new String(ch,start,length));
        if (comment != null){
          if (last == _root && _nextSibling != null)
              last.insertBefore(comment, _nextSibling);
          else
              last.appendChild(comment);

          _lastSibling = comment;
        }
    }

    // Lexical Handler methods- not implemented
    public void startCDATA() { }
    public void endCDATA() { }
    public void startEntity(java.lang.String name) { }
    public void endDTD() { }
    public void endEntity(String name) { }
    public void startDTD(String name, String publicId, String systemId)
        throws SAXException {}
}
