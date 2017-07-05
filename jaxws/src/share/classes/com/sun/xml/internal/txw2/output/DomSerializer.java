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

package com.sun.xml.internal.txw2.output;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMResult;
import java.util.ArrayList;
import java.util.Stack;

import com.sun.xml.internal.txw2.TxwException;

/**
 * {@link XmlSerializer} for {@link javax.xml.transform.dom.DOMResult} and {@link org.w3c.dom.Node}.
 *
 * @author Ryan.Shoemaker@Sun.COM
 */
public class DomSerializer implements XmlSerializer {

    // delegate to SaxSerializer
    private final SaxSerializer serializer;

    public DomSerializer(Node node) {
        Dom2SaxAdapter adapter = new Dom2SaxAdapter(node);
        serializer = new SaxSerializer(adapter,adapter,false);
    }

    public DomSerializer(DOMResult domResult) {
        Node node = domResult.getNode();

        if (node == null) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.newDocument();
                domResult.setNode(doc);
                serializer = new SaxSerializer(new Dom2SaxAdapter(doc),null,false);
            } catch (ParserConfigurationException pce) {
                throw new TxwException(pce);
            }
        } else {
            serializer = new SaxSerializer(new Dom2SaxAdapter(node),null,false);
        }
    }

    // XmlSerializer api's - delegate to SaxSerializer
    public void startDocument() {
        serializer.startDocument();
    }

    public void beginStartTag(String uri, String localName, String prefix) {
        serializer.beginStartTag(uri, localName, prefix);
    }

    public void writeAttribute(String uri, String localName, String prefix, StringBuilder value) {
        serializer.writeAttribute(uri, localName, prefix, value);
    }

    public void writeXmlns(String prefix, String uri) {
        serializer.writeXmlns(prefix, uri);
    }

    public void endStartTag(String uri, String localName, String prefix) {
        serializer.endStartTag(uri, localName, prefix);
    }

    public void endTag() {
        serializer.endTag();
    }

    public void text(StringBuilder text) {
        serializer.text(text);
    }

    public void cdata(StringBuilder text) {
        serializer.cdata(text);
    }

    public void comment(StringBuilder comment) {
        serializer.comment(comment);
    }

    public void endDocument() {
        serializer.endDocument();
    }

    public void flush() {
        // no flushing
    }
}




/**
 * Builds a DOM tree from SAX2 events.
 *
 * @author  Vivek Pandey
 */
class Dom2SaxAdapter implements ContentHandler, LexicalHandler {

    private final Node _node;
    private final Stack _nodeStk = new Stack();
    private boolean inCDATA;

    public final Element getCurrentElement() {
        return (Element) _nodeStk.peek();
    }

    /**
     * Document object that owns the specified node.
     */
    private final Document _document;

    /**
     * @param   node
     *      Nodes will be created and added under this object.
     */
    public Dom2SaxAdapter(Node node)
    {
        _node = node;
        _nodeStk.push(_node);

        if( node instanceof Document )
            this._document = (Document)node;
        else
            this._document = node.getOwnerDocument();
    }

    /**
     * Creates a fresh empty DOM document and adds nodes under this document.
     */
    public Dom2SaxAdapter() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);

        _document = factory.newDocumentBuilder().newDocument();
        _node = _document;
        _nodeStk.push( _document );
    }

    public Node getDOM() {
        return _node;
    }

    public void startDocument() {
    }

    public void endDocument(){
    }

    public void startElement(String namespace, String localName, String qName, Attributes attrs){

        // some broken DOM implementatino (we confirmed it with SAXON)
        // return null from this method.
        Element element = _document.createElementNS(namespace, qName);

        if( element==null ) {
            // if so, report an user-friendly error message,
            // rather than dying mysteriously with NPE.
            throw new TxwException("Your DOM provider doesn't support the createElementNS method properly");
        }

        // process namespace bindings
        for( int i=0; i<unprocessedNamespaces.size(); i+=2 ) {
            String prefix = (String)unprocessedNamespaces.get(i+0);
            String uri = (String)unprocessedNamespaces.get(i+1);

            String qname;
            if( "".equals(prefix) || prefix==null )
                qname = "xmlns";
            else
                qname = "xmlns:"+prefix;

            // older version of Xerces (I confirmed that the bug is gone with Xerces 2.4.0)
            // have a problem of re-setting the same namespace attribute twice.
            // work around this bug removing it first.
            if( element.hasAttributeNS("http://www.w3.org/2000/xmlns/",qname) ) {
                // further workaround for an old Crimson bug where the removeAttribtueNS
                // method throws NPE when the element doesn't have any attribute.
                // to be on the safe side, check the existence of attributes before
                // attempting to remove it.
                // for details about this bug, see org.apache.crimson.tree.ElementNode2
                // line 540 or the following message:
                // https://jaxb.dev.java.net/servlets/ReadMsg?list=users&msgNo=2767
                element.removeAttributeNS("http://www.w3.org/2000/xmlns/",qname);
            }
            // workaround until here

            element.setAttributeNS("http://www.w3.org/2000/xmlns/",qname, uri);
        }
        unprocessedNamespaces.clear();


        int length = attrs.getLength();
        for(int i=0;i<length;i++){
            String namespaceuri = attrs.getURI(i);
            String value = attrs.getValue(i);
            String qname = attrs.getQName(i);
            element.setAttributeNS(namespaceuri, qname, value);
        }
        // append this new node onto current stack node
        getParent().appendChild(element);
        // push this node onto stack
        _nodeStk.push(element);
    }

    private final Node getParent() {
        return (Node) _nodeStk.peek();
    }

    public void endElement(String namespace, String localName, String qName){
        _nodeStk.pop();
    }


    public void characters(char[] ch, int start, int length) {
        Node text;
        if(inCDATA)
            text = _document.createCDATASection(new String(ch, start, length));
        else
            text = _document.createTextNode(new String(ch, start, length));
        getParent().appendChild(text);
    }

    public void comment(char ch[], int start, int length) throws SAXException {
        getParent().appendChild(_document.createComment(new String(ch,start,length)));
    }



    public void ignorableWhitespace(char[] ch, int start, int length) {
    }

    public void processingInstruction(String target, String data) throws org.xml.sax.SAXException{
        Node node = _document.createProcessingInstruction(target, data);
        getParent().appendChild(node);
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void skippedEntity(String name) {
    }

    private ArrayList unprocessedNamespaces = new ArrayList();

    public void startPrefixMapping(String prefix, String uri) {
        unprocessedNamespaces.add(prefix);
        unprocessedNamespaces.add(uri);
    }

    public void endPrefixMapping(String prefix) {
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
    }

    public void endDTD() throws SAXException {
    }

    public void startEntity(String name) throws SAXException {
    }

    public void endEntity(String name) throws SAXException {
    }

    public void startCDATA() throws SAXException {
        inCDATA = true;
    }

    public void endCDATA() throws SAXException {
        inCDATA = false;
    }
}
