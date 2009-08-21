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
package com.sun.xml.internal.stream.buffer.stax;

import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.org.jvnet.staxex.Base64Data;
import com.sun.xml.internal.org.jvnet.staxex.NamespaceContextEx;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamWriterEx;

import javax.activation.DataHandler;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;

/**
 * {@link XMLStreamWriter} that fills {@link MutableXMLStreamBuffer}.
 * <p>
 * TODO: need to retain all attributes/namespaces and then store all namespaces
 * before the attributes. Currently it is necessary for the caller to ensure
 * all namespaces are written before attributes and the caller must not intermix
 * calls to the writeNamespace and writeAttribute methods.
 *
 */
public class StreamWriterBufferCreator extends StreamBufferCreator implements XMLStreamWriterEx {
    private final NamespaceContexHelper namespaceContext = new NamespaceContexHelper();

    /**
     * Nesting depth of the element.
     * This field is ultimately used to keep track of the # of trees we created in
     * the buffer.
     */
    private int depth=0;

    public StreamWriterBufferCreator() {
        setXMLStreamBuffer(new MutableXMLStreamBuffer());
    }

    public StreamWriterBufferCreator(MutableXMLStreamBuffer buffer) {
        setXMLStreamBuffer(buffer);
    }

    // XMLStreamWriter

    public Object getProperty(String str) throws IllegalArgumentException {
        return null; //return  null for all the property names instead of
                    //throwing unsupported operation exception.
    }

    public void close() throws XMLStreamException {
    }

    public void flush() throws XMLStreamException {
    }

    public NamespaceContextEx getNamespaceContext() {
        return namespaceContext;
    }

    public void setNamespaceContext(NamespaceContext namespaceContext) throws XMLStreamException {
        /*
         * It is really unclear from the JavaDoc how to implement this method.
         */
        throw new UnsupportedOperationException();
    }

    public void setDefaultNamespace(String namespaceURI) throws XMLStreamException {
        setPrefix("", namespaceURI);
    }

    public void setPrefix(String prefix, String namespaceURI) throws XMLStreamException {
        namespaceContext.declareNamespace(prefix, namespaceURI);
    }

    public String getPrefix(String namespaceURI) throws XMLStreamException {
        return namespaceContext.getPrefix(namespaceURI);
    }


    public void writeStartDocument() throws XMLStreamException {
        writeStartDocument("", "");
    }

    public void writeStartDocument(String version) throws XMLStreamException {
        writeStartDocument("", "");
    }

    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        namespaceContext.resetContexts();

        storeStructure(T_DOCUMENT);
    }

    public void writeEndDocument() throws XMLStreamException {
        storeStructure(T_END);
    }

    public void writeStartElement(String localName) throws XMLStreamException {
        namespaceContext.pushContext();
        depth++;

        final String defaultNamespaceURI = namespaceContext.getNamespaceURI("");

        if (defaultNamespaceURI == null)
            storeQualifiedName(T_ELEMENT_LN, null, null, localName);
        else
            storeQualifiedName(T_ELEMENT_LN, null, defaultNamespaceURI, localName);
    }

    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        namespaceContext.pushContext();
        depth++;

        final String prefix = namespaceContext.getPrefix(namespaceURI);
        if (prefix == null) {
            throw new XMLStreamException();
        }

        namespaceContext.pushContext();
        storeQualifiedName(T_ELEMENT_LN, prefix, namespaceURI, localName);
    }

    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        namespaceContext.pushContext();
        depth++;

        storeQualifiedName(T_ELEMENT_LN, prefix, namespaceURI, localName);
    }

    public void writeEmptyElement(String localName) throws XMLStreamException {
        writeStartElement(localName);
        writeEndElement();
    }

    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        writeStartElement(namespaceURI, localName);
        writeEndElement();
    }

    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        writeStartElement(prefix, localName, namespaceURI);
        writeEndElement();
    }

    public void writeEndElement() throws XMLStreamException {
        namespaceContext.popContext();

        storeStructure(T_END);
        if(--depth==0)
            increaseTreeCount();
    }

    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
        storeNamespaceAttribute(null, namespaceURI);
    }

    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        if ("xmlns".equals(prefix))
            prefix = null;
        storeNamespaceAttribute(prefix, namespaceURI);
    }


    public void writeAttribute(String localName, String value) throws XMLStreamException {
        storeAttribute(null, null, localName, "CDATA", value);
    }

    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        final String prefix = namespaceContext.getPrefix(namespaceURI);
        if (prefix == null) {
            // TODO
            throw new XMLStreamException();
        }

        writeAttribute(prefix, namespaceURI, localName, value);
    }

    public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
        storeAttribute(prefix, namespaceURI, localName, "CDATA", value);
    }

    public void writeCData(String data) throws XMLStreamException {
        storeStructure(T_TEXT_AS_STRING);
        storeContentString(data);
    }

    public void writeCharacters(String charData) throws XMLStreamException {
        storeStructure(T_TEXT_AS_STRING);
        storeContentString(charData);
    }

    public void writeCharacters(char[] buf, int start, int len) throws XMLStreamException {
        storeContentCharacters(T_TEXT_AS_CHAR_ARRAY, buf, start, len);
    }

    public void writeComment(String str) throws XMLStreamException {
        storeStructure(T_COMMENT_AS_STRING);
        storeContentString(str);
    }

    public void writeDTD(String str) throws XMLStreamException {
        // not support. just ignore.
    }

    public void writeEntityRef(String str) throws XMLStreamException {
        storeStructure(T_UNEXPANDED_ENTITY_REFERENCE);
        storeContentString(str);
    }

    public void writeProcessingInstruction(String target) throws XMLStreamException {
        writeProcessingInstruction(target, "");
    }

    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
        storeProcessingInstruction(target, data);
    }

    // XMLStreamWriterEx

    public void writePCDATA(CharSequence charSequence) throws XMLStreamException {
        if (charSequence instanceof Base64Data) {
            storeStructure(T_TEXT_AS_OBJECT);
            storeContentObject(((Base64Data)charSequence).clone());
        } else {
            writeCharacters(charSequence.toString());
        }
    }

    public void writeBinary(byte[] bytes, int offset, int length, String endpointURL) throws XMLStreamException {
        Base64Data d = new Base64Data();
        byte b[] = new byte[length];
        System.arraycopy(bytes, offset, b, 0, length);
        d.set(b, length, null, true);
        storeStructure(T_TEXT_AS_OBJECT);
        storeContentObject(d);
    }

    public void writeBinary(DataHandler dataHandler) throws XMLStreamException {
        Base64Data d = new Base64Data();
        d.set(dataHandler);
        storeStructure(T_TEXT_AS_OBJECT);
        storeContentObject(d);
    }

    public OutputStream writeBinary(String endpointURL) throws XMLStreamException {
        // TODO
        throw new UnsupportedOperationException();
    }
}
