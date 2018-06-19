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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Manifest;
import javax.xml.crypto.dsig.SignatureProperties;
import javax.xml.crypto.dsig.SignatureProperty;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyName;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.PGPData;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.keyinfo.X509IssuerSerial;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Defines the individual marshallers for each of the different javax.xml.crypto structures.
 */
class Marshaller {

    private Marshaller() {
        // complete
    }

    public static List<XmlWriter.ToMarshal<? extends XMLStructure>> getMarshallers() {
        return MARSHALLERS;
    }

    /**
     * Marshals a {@link KeyName}.
     *
     * @param xwriter
     * @param keyName
     * @param dsPrefix
     */
    public static void marshalKeyName(XmlWriter xwriter, KeyName keyName, String dsPrefix) {
        xwriter.writeTextElement(dsPrefix, "KeyName", XMLSignature.XMLNS, keyName.getName());
    }

    /**
     * Marshals a {@link PGPData}
     *
     * @param xwriter
     * @param pgpData
     * @param dsPrefix
     * @param context
     * @throws MarshalException
     */
    public static void marshalPGPData(XmlWriter xwriter, PGPData pgpData, String dsPrefix, XMLCryptoContext context)
    throws MarshalException {
        xwriter.writeStartElement(dsPrefix, "PGPData", XMLSignature.XMLNS);

        // create and append PGPKeyID element
        byte[] keyId = pgpData.getKeyId();
        if (keyId != null) {
            xwriter.writeTextElement(dsPrefix, "PGPKeyID", XMLSignature.XMLNS,
                                     Base64.getMimeEncoder().encodeToString(keyId));
        }

        // create and append PGPKeyPacket element
        byte[] keyPacket = pgpData.getKeyPacket();
        if (keyPacket != null) {
            xwriter.writeTextElement(dsPrefix, "XMLSignature.XMLNS", XMLSignature.XMLNS,
                                     Base64.getMimeEncoder().encodeToString(keyPacket));
        }

        // create and append any elements
        @SuppressWarnings("unchecked")
        List<XMLStructure> externalElements = pgpData.getExternalElements();
        for (XMLStructure externalItem : externalElements) {
            xwriter.marshalStructure(externalItem, dsPrefix, context);
        }

        xwriter.writeEndElement(); // "PGPData"
    }

    /**
     * Marshals an {@link X509IssuerSerial}
     *
     * @param xwriter
     * @param issuerSerial
     * @param dsPrefix
     */
    public static void marshalX509IssuerSerial(XmlWriter xwriter, X509IssuerSerial issuerSerial, String dsPrefix) {
        xwriter.writeStartElement(dsPrefix, "X509IssuerSerial", XMLSignature.XMLNS);
        xwriter.writeTextElement(dsPrefix, "X509IssuerName", XMLSignature.XMLNS,
                issuerSerial.getIssuerName());

        xwriter.writeTextElement(dsPrefix, "X509SerialNumber", XMLSignature.XMLNS,
                issuerSerial.getSerialNumber().toString());

        xwriter.writeEndElement(); // "X509IssuerSerial"
    }

    private static XmlWriter.ToMarshal<KeyName> Marshal_KeyName = new XmlWriter.ToMarshal<KeyName>(KeyName.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, KeyName toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            Marshaller.marshalKeyName(xwriter, toMarshal, dsPrefix);
        }
    };

    private static XmlWriter.ToMarshal<KeyInfo> Marshal_KeyInfo = new XmlWriter.ToMarshal<KeyInfo>(KeyInfo.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, KeyInfo toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            DOMKeyInfo.marshal(xwriter, toMarshal, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<KeyValue> Marshal_KeyValue = new XmlWriter.ToMarshal<KeyValue>(KeyValue.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, KeyValue toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            // Since DOMKeyValue allows for deserializing unrecognized keys, and that
            // capability isn't available via the KeyValue interface, this must continue
            // to cast to DOMKeyValue.
            DOMKeyValue<?> dkv = (DOMKeyValue<?>) toMarshal;
            dkv.marshal( xwriter, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<X509IssuerSerial> Marshal_X509IssuerSerial =
            new XmlWriter.ToMarshal<X509IssuerSerial>(X509IssuerSerial.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, X509IssuerSerial toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            Marshaller.marshalX509IssuerSerial( xwriter, toMarshal, dsPrefix);
        }
    };

    private static XmlWriter.ToMarshal<X509Data> Marshal_X509Data =
            new XmlWriter.ToMarshal<X509Data>(X509Data.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, X509Data toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            DOMX509Data.marshal( xwriter, toMarshal, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<DigestMethod> Marshal_DigestMethod =
            new XmlWriter.ToMarshal<DigestMethod>(DigestMethod.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, DigestMethod toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            DOMDigestMethod.marshal( xwriter, toMarshal, dsPrefix);
        }
    };

    private static XmlWriter.ToMarshal<PGPData> Marshal_PGPData =
            new XmlWriter.ToMarshal<PGPData>(PGPData.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, PGPData toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            Marshaller.marshalPGPData( xwriter, toMarshal, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<SignatureProperty> Marshal_SignatureProperty =
            new XmlWriter.ToMarshal<SignatureProperty>(SignatureProperty.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, SignatureProperty toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            DOMSignatureProperty.marshal(xwriter, toMarshal, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<SignatureProperties> Marshal_SignatureProperties =
            new XmlWriter.ToMarshal<SignatureProperties>(SignatureProperties.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, SignatureProperties toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            DOMSignatureProperties.marshal(xwriter, toMarshal, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<DOMSignatureMethod> Marshal_DOMSignatureMethod =
            new XmlWriter.ToMarshal<DOMSignatureMethod>(DOMSignatureMethod.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, DOMSignatureMethod toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            toMarshal.marshal(xwriter, dsPrefix);
        }
    };

    private static XmlWriter.ToMarshal<DOMTransform> Marshal_DOMTransform =
            new XmlWriter.ToMarshal<DOMTransform>(DOMTransform.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, DOMTransform toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            toMarshal.marshal(xwriter, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<Manifest> Marshal_Manifest =
            new XmlWriter.ToMarshal<Manifest>(Manifest.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, Manifest toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            DOMManifest.marshal(xwriter, toMarshal, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<DOMStructure> Marshal_DOMStructure =
            new XmlWriter.ToMarshal<DOMStructure>(DOMStructure.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, DOMStructure toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            toMarshal.marshal(xwriter, dsPrefix, context);
        }
    };

    private static XmlWriter.ToMarshal<javax.xml.crypto.dom.DOMStructure> Marshal_JavaXDOMStructure =
            new XmlWriter.ToMarshal<javax.xml.crypto.dom.DOMStructure>(javax.xml.crypto.dom.DOMStructure.class) {
        @Override
        public void marshalObject(XmlWriter xwriter, javax.xml.crypto.dom.DOMStructure toMarshal, String dsPrefix,
                XMLCryptoContext context) throws MarshalException {
            marshalGenericNode(xwriter, toMarshal);
        }
    };

    private static final List<XmlWriter.ToMarshal<? extends XMLStructure>> MARSHALLERS =
        new ArrayList<XmlWriter.ToMarshal<? extends XMLStructure>>();

    static {
        MARSHALLERS.add(Marshal_KeyName);
        MARSHALLERS.add(Marshal_KeyInfo);
        MARSHALLERS.add(Marshal_KeyValue);
        MARSHALLERS.add(Marshal_X509IssuerSerial);
        MARSHALLERS.add(Marshal_X509Data);
        MARSHALLERS.add(Marshal_DigestMethod);
        MARSHALLERS.add(Marshal_PGPData);
        MARSHALLERS.add(Marshal_SignatureProperty);
        MARSHALLERS.add(Marshal_SignatureProperties);
        MARSHALLERS.add(Marshal_DOMSignatureMethod);
        MARSHALLERS.add(Marshal_DOMTransform);
        MARSHALLERS.add(Marshal_Manifest);
        MARSHALLERS.add(Marshal_DOMStructure);
        MARSHALLERS.add(Marshal_JavaXDOMStructure);
    }

    private static void marshalGenericNode(XmlWriter xwriter, javax.xml.crypto.dom.DOMStructure xmlStruct) {
        Node node = xmlStruct.getNode();

        // if it is a namespace, make a copy.
        if (DOMUtils.isNamespace(node)) {
            xwriter.writeNamespace(node.getLocalName(), node.getTextContent());
        }
        else if (Node.ATTRIBUTE_NODE == node.getNodeType() ) {
            sendAttributeToWriter(xwriter, (Attr) node);
        }
        else {
            marshalGenericNode(xwriter, node);
        }
    }

    private static void marshalGenericNode(XmlWriter xwriter, Node node) {

        short nodeType = node.getNodeType();
        if (DOMUtils.isNamespace(node)) {
            xwriter.writeNamespace(node.getLocalName(), node.getTextContent());
        }
        else if (nodeType == Node.ATTRIBUTE_NODE) {
            // if it is an attribute, make a copy.
            sendAttributeToWriter(xwriter, (Attr) node);
        }
        else {
            switch (nodeType) {
            case Node.ELEMENT_NODE:
                xwriter.writeStartElement(node.getPrefix(), node.getLocalName(), node.getNamespaceURI());

                // emit all the namespaces and attributes.
                NamedNodeMap nnm = node.getAttributes();
                for (int idx = 0 ; idx < nnm.getLength() ; idx++) {
                    Attr attr = (Attr) nnm.item(idx);
                    // is this a namespace node?
                    if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(node.getNamespaceURI())) {
                        xwriter.writeNamespace(attr.getLocalName(), attr.getValue());
                    }
                    else {
                        // nope - standard attribute.
                        sendAttributeToWriter(xwriter, attr);
                    }
                }
                // now loop through all the children.
                for (Node child = node.getFirstChild() ; child != null ; child = child.getNextSibling()) {
                    marshalGenericNode(xwriter, child);
                }
                xwriter.writeEndElement();
                break;
            case Node.COMMENT_NODE:
                xwriter.writeComment(node.getTextContent());
                break;
            case Node.TEXT_NODE:
                xwriter.writeCharacters(node.getTextContent());
                break;
            default:
                // unhandled - don't care to deal with processing instructions.
                break;
            }
        }
    }

    private static void sendAttributeToWriter(XmlWriter xwriter, Attr attr) {
        if (attr.isId()) {
            xwriter.writeIdAttribute(attr.getPrefix(), attr.getNamespaceURI(),
                    attr.getLocalName(), attr.getTextContent());
        }
        else {
            if (attr.getNamespaceURI() == null && attr.getLocalName() == null) {
                // Level 1 DOM attribute
                xwriter.writeAttribute(null, null, attr.getName(), attr.getTextContent());
            } else {
                xwriter.writeAttribute(attr.getPrefix(), attr.getNamespaceURI(), attr.getLocalName(),
                                       attr.getTextContent());
            }
        }
    }

}
