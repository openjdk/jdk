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

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;

import org.w3c.dom.Attr;

/**
 * This interface is used to construct XML via a sequence of API calls.
 *
 * <p>This is written to be similar to javax.xml.stream.XMLStreamWriter, but
 * has slightly different requirements. Specifically, we need to be able to create
 * an "ID" type attribute, and get the current node.
 * </p>
 */
public interface XmlWriter {

    /**
     * Utility class that brings together the class, and the method for marshaling an
     * instance of said class.
     *
     * @param <CLZ>
     */
    abstract static class ToMarshal<CLZ extends XMLStructure> { //NOPMD
        public final Class<CLZ> clazzToMatch;

        public ToMarshal(Class<CLZ> clazzToMatch) {
            this.clazzToMatch = clazzToMatch;
        }

        public abstract void marshalObject(XmlWriter xwriter, CLZ toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException;
    }

    /**
     *
     * @param prefix    What prefix to use?
     * @param localName What local name to use?
     * @param namespaceURI  What namespace URI?
     *
     * See also {@link javax.xml.stream.XMLStreamWriter#writeStartElement(String, String, String)}
     */
    void writeStartElement(String prefix, String localName, String namespaceURI);

    /**
     * See also {@link javax.xml.stream.XMLStreamWriter#writeEndElement()}
     */
    void writeEndElement();

    /**
     * Convenience method that writes both a start and end tag, with text contents as
     * provided.
     *
     * @param prefix
     * @param localName
     * @param namespaceURI
     * @param value
     */
    void writeTextElement(String prefix, String localName, String namespaceURI, String value);

    void writeNamespace(String prefix, String namespaceURI);

    void writeCharacters(String text);

    void writeComment(String text);

    Attr writeAttribute(String prefix, String namespaceURI, String localName, String value);

    void writeIdAttribute(String prefix, String namespaceURI, String localName, String value);

    /**
     * Get the local name of the current element.
     * @return the local name of the current element.
     */
    String getCurrentLocalName();

    XMLStructure getCurrentNodeAsStructure();

    /**
     * This method marshals a structure, and relies on implementation specific details for how
     * an instance of a particular class maps to the method that actually does the marshaling.
     *
     * @param toMarshal The object to be marshaled.
     * @param dsPrefix  The digital signature prefix.
     * @param context   The context for marshaling.
     * @throws MarshalException Thrown if something goes wrong during the marshaling.
     */
    void marshalStructure(XMLStructure toMarshal, String dsPrefix, XMLCryptoContext context) throws MarshalException;
}
