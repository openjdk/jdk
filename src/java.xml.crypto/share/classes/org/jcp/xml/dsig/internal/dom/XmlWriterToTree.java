/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jcp.xml.dsig.internal.dom;

import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dom.DOMStructure;

import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * Manifestation of XmlWriter interface designed to write to a tree.
 */
public class XmlWriterToTree implements XmlWriter {

    private Document factory;

    private Element createdElement;

    private Node nextSibling;

    private Node currentNode;

    private List<XmlWriter.ToMarshal<? extends XMLStructure>> m_marshallers;

    public XmlWriterToTree(List<XmlWriter.ToMarshal<? extends XMLStructure>> marshallers, Node parent) {
        m_marshallers = marshallers;
        factory = parent instanceof Document ? (Document)parent : parent.getOwnerDocument();
        currentNode = parent;
    }

    /**
     * Reset to a new parent so that the writer can be re-used.
     * @param newParent
     */
    public void resetToNewParent(Node newParent) {
        currentNode = newParent;
        createdElement = null;
    }

    /**
     * Get the root element created with this writer.
     * @return the root element created with this writer.
     */
    public Element getCreatedElement() {
        return createdElement;
    }

    /**
     * In cases where the serialization is supposed to precede a specific
     * element, we add an extra parameter to capture that. Only affects the
     * first element insertion (obviously?).
     *
     * @param marshallers
     * @param parent
     * @param nextSibling The first element created will be created *before* this element.
     */
    public XmlWriterToTree(List<XmlWriter.ToMarshal<? extends XMLStructure>> marshallers, Node parent, Node nextSibling) {
        this(marshallers, parent);
        this.nextSibling = nextSibling;
    }

    @Override
    public void writeStartElement(String prefix, String localName, String namespaceURI) {
        if ("".equals(namespaceURI)) {
            // Map global namespace from StAX to DOM
            namespaceURI = null;
        }

        Element newElem = factory.createElementNS(namespaceURI, DOMUtils.getQNameString(prefix, localName));
        if (nextSibling != null) {
            newElem = (Element)nextSibling.getParentNode().insertBefore(newElem, nextSibling);
        }
        else {
            newElem = (Element)currentNode.appendChild(newElem);
        }
        nextSibling = null;
        currentNode = newElem;

        if (createdElement == null) {
            createdElement = newElem;
        }
    }

    @Override
    public void writeEndElement() {
        currentNode = currentNode.getParentNode();
    }


    @Override
    public void writeTextElement(String prefix, String localName, String namespaceURI, String value) {
        writeStartElement(prefix, localName, namespaceURI);
        writeCharacters(value);
        writeEndElement();
    }

    @Override
    public void writeNamespace(String prefix, String namespaceURI) {
        if ("".equals(prefix) || prefix == null) {
            writeAttribute(null, XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", namespaceURI);
        }
        else {
            writeAttribute("xmlns", XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix, namespaceURI);
        }
    }

    @Override
    public void writeCharacters(String text) {
        Text textNode = factory.createTextNode(text);
        currentNode.appendChild(textNode);
    }


    @Override
    public void writeComment(String text) {
        Comment commentNode = factory.createComment(text);
        currentNode.appendChild(commentNode);
    }

    @Override
    public Attr writeAttribute(String prefix, String namespaceURI, String localName, String value) {

        Attr result = null;
        if (value != null) {
            if ("".equals(namespaceURI)) {
                // Map global namespace from StAX to DOM
                namespaceURI = null;
            }

            result = factory.createAttributeNS(namespaceURI, DOMUtils.getQNameString(prefix, localName));
            result.setTextContent(value);
            if (! (currentNode instanceof Element)) {
                throw new IllegalStateException(
                        "Attempting to add an attribute to something other than an element node. Node is "
                                + currentNode.toString());
            }
            ( (Element)currentNode).setAttributeNodeNS(result);
        }
        return result;
    }

    @Override
    public void writeIdAttribute(String prefix, String namespaceURI, String localName, String value) {
        if (value == null) {
            return;
        }
        Attr newAttr = writeAttribute(prefix, namespaceURI, localName, value);
        ( (Element)currentNode).setIdAttributeNode(newAttr, true);
    }


    @Override
    public String getCurrentLocalName() {
        return currentNode.getLocalName();
    }

    @Override
    public XMLStructure getCurrentNodeAsStructure() {
        return new DOMStructure(currentNode);
    }

    @Override
    public void marshalStructure(XMLStructure toMarshal, String dsPrefix, XMLCryptoContext context) throws MarshalException {

        // look for the first isInstance match, and marshal to that.
        for (int idx = 0 ; idx < m_marshallers.size() ; idx++) {
            @SuppressWarnings("unchecked")
            XmlWriter.ToMarshal<XMLStructure> marshaller = (ToMarshal<XMLStructure>) m_marshallers.get(idx);
            if (marshaller.clazzToMatch.isInstance(toMarshal)) {
                marshaller.marshalObject(this, toMarshal, dsPrefix, context);
                return;
            }
        }
        throw new IllegalArgumentException("Unable to marshal unexpected object of class " + toMarshal.getClass().toString());
    }


}
