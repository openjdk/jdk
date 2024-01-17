/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.test.lib.security;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.crypto.*;
import javax.xml.crypto.dom.DOMStructure;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.RSAKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.*;

// A collection of test utility methods for parsing, validating and
// generating XML Signatures.
public class XMLUtils {

    private static final XMLSignatureFactory FAC =
            XMLSignatureFactory.getInstance("DOM");

    //////////// CONVERT ////////////

    // Converts a Document object to string
    public static String doc2string(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        // Indentation would invalidate the signature
        // transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    // Parses a string into a Document object
    public static Document string2doc(String input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().
                parse(new InputSource(new StringReader(input)));
    }

    // Clones a document
    public static Document clone(Document d) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document copiedDocument = db.newDocument();
        Node copiedRoot = copiedDocument.importNode(d.getDocumentElement(), true);
        copiedDocument.appendChild(copiedRoot);
        return copiedDocument;
    }

    //////////// QUERY AND EDIT ////////////

    // An XPath object that recognizes ds and pss names
    public static final XPath XPATH;

    static {
        XPATH = XPathFactory.newInstance().newXPath();
        XPATH.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return switch (prefix) {
                    case "ds" -> "http://www.w3.org/2000/09/xmldsig#";
                    case "pss" -> "http://www.w3.org/2007/05/xmldsig-more#";
                    default -> throw new IllegalArgumentException();
                };
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        });
    }

    // Returns an Element inside a Document
    public static Element sub(Document d, String path) throws Exception {
        return (Element) XMLUtils.XPATH.evaluate(path, d, XPathConstants.NODE);
    }

    // Returns a new document with an attribute modified
    public static Document withAttribute(Document d, String path, String attr, String value) throws Exception {
        d = clone(d);
        sub(d, path).setAttribute(attr, value);
        return d;
    }

    // Returns a new document with a text modified
    public static Document withText(Document d, String path, String value) throws Exception {
        d = clone(d);
        sub(d, path).setTextContent(value);
        return d;
    }

    // Returns a new document without a child element
    public static Document withoutNode(Document d, String... paths) throws Exception {
        d = clone(d);
        for (String path : paths) {
            Element e = sub(d, path);
            e.getParentNode().removeChild(e);
        }
        return d;
    }

    //////////// SIGN ////////////

    // Creates a signer from a private key and a certificate
    public static Signer signer(PrivateKey privateKey, X509Certificate cert)
            throws Exception {
        return signer(privateKey).cert(cert);
    }

    // Creates a signer from a private key and a public key
    public static Signer signer(PrivateKey privateKey, PublicKey publicKey)
            throws Exception {
        return signer(privateKey).publicKey(publicKey);
    }

    // Creates a signer from a private key entry in a keystore. The KeyInfo
    // inside the signature will only contain a KeyName to alias
    public static Signer signer(KeyStore ks, String alias, char[] password)
            throws Exception {
        return signer((PrivateKey) ks.getKey(alias, password))
                .keyName(alias);
    }

    // Creates a signer from a private key. There will be no KeyInfo inside
    // the signature and must be validated with a given public key.
    public static Signer signer(PrivateKey privateKey)
            throws Exception {
        return new Signer(privateKey);
    }

    public static class Signer {

        final PrivateKey privateKey;  // signer key, never null

        X509Certificate cert;   // certificate, optional
        PublicKey publicKey;    // public key, optional
        String keyName;         // alias, optional

        String sm = null;       // default determined by privateKey
        SignatureMethodParameterSpec smSpec = null;
        String dm = DigestMethod.SHA256;
        String cm = CanonicalizationMethod.EXCLUSIVE;
        String tr = Transform.ENVELOPED;

        public Signer(PrivateKey privateKey) {
            this.privateKey = Objects.requireNonNull(privateKey);
        }

        // Change KeyInfo source

        public Signer cert(X509Certificate cert) {
            this.cert = cert;
            return this;
        }

        public Signer publicKey(PublicKey key) {
            this.publicKey = key;
            return this;
        }

        public Signer keyName(String n) {
            keyName = n;
            return this;
        }

        // Change various methods

        public Signer tr(String transform) {
            tr = Objects.requireNonNull(transform);
            return this;
        }

        public Signer dm(String method) {
            dm = Objects.requireNonNull(method);
            return this;
        }

        public Signer cm(String method) {
            cm = Objects.requireNonNull(method);
            return this;
        }

        public Signer sm(String method, SignatureMethodParameterSpec spec) {
            sm = method;
            smSpec = spec;
            return this;
        }

        public Signer sm(String method) throws Exception {
            return sm(method, null);
        }

        // Signs different sources

        // Signs an XML file in detached mode
        public Document sign(URI uri) throws Exception {
            Document newDocument = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            FAC.newXMLSignature(buildSignedInfo(uri.toString()), buildKeyInfo()).sign(
                    new DOMSignContext(privateKey, newDocument));
            return newDocument;
        }

        // Signs an XML file in a relative reference in detached mode
        public Document sign(URI base, URI ref) throws Exception {
            Document newDocument = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            DOMSignContext ctxt = new DOMSignContext(privateKey, newDocument);
            ctxt.setBaseURI(base.toString());
            FAC.newXMLSignature(buildSignedInfo(ref.toString()), buildKeyInfo()).sign(ctxt);
            return newDocument;
        }

        // Signs a document in enveloped mode
        public Document sign(Document document) throws Exception {
            DOMResult result = new DOMResult();
            TransformerFactory.newInstance().newTransformer()
                    .transform(new DOMSource(document), result);
            Document newDocument = (Document) result.getNode();
            FAC.newXMLSignature(buildSignedInfo(""), buildKeyInfo()).sign(
                    new DOMSignContext(privateKey, newDocument.getDocumentElement()));
            return newDocument;
        }

        // Signs a document in enveloping mode
        public Document signEnveloping(Document document, String id, String ref) throws Exception {
            Document newDocument = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            FAC.newXMLSignature(
                    buildSignedInfo(FAC.newReference(ref, FAC.newDigestMethod(dm, null))),
                    buildKeyInfo(),
                    List.of(FAC.newXMLObject(List.of(new DOMStructure(document.getDocumentElement())),
                            id, null, null)),
                    null,
                    null)
                    .sign(new DOMSignContext(privateKey, newDocument));
            return newDocument;
        }

        // Signs a raw byte array
        public Document sign(byte[] data) throws Exception {
            Document newDocument = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            FAC.newXMLSignature(
                    buildSignedInfo(FAC.newReference("#object", FAC.newDigestMethod(dm, null), List.of
                            (FAC.newTransform(Transform.BASE64,
                                    (TransformParameterSpec) null)), null, null)),
                    buildKeyInfo(),
                    List.of(FAC.newXMLObject(List.of(new DOMStructure(
                            newDocument.createTextNode(Base64.getEncoder().encodeToString(data)))),
                            "object", null, null)),
                    null,
                    null)
                    .sign(new DOMSignContext(privateKey, newDocument));
            return newDocument;
        }

        // Signs a plain string
        public Document sign(String str) throws Exception {
            Document newDocument = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            FAC.newXMLSignature(
                    buildSignedInfo(
                            FAC.newReference("#object", FAC.newDigestMethod(dm, null))),
                    buildKeyInfo(),
                    List.of(FAC.newXMLObject(
                            List.of(new DOMStructure(newDocument.createTextNode(str))),
                            "object", null, null)),
                    null,
                    null)
                    .sign(new DOMSignContext(privateKey, newDocument));
            return newDocument;
        }

        // Builds a SignedInfo for a string reference
        private SignedInfo buildSignedInfo(String ref) throws Exception {
            return buildSignedInfo(FAC.newReference(
                            ref,
                            FAC.newDigestMethod(dm, null),
                            List.of(FAC.newTransform(tr, switch (tr) {
                                case Transform.XPATH ->
                                    new XPathFilterParameterSpec("//.");
                                case Transform.XPATH2 -> new XPathFilter2ParameterSpec(
                                            Collections.singletonList(new XPathType("//.",
                                                    XPathType.Filter.INTERSECT)));
                                default -> null;
                            })),
                            null, null));
        }

        // Builds a SignedInfo for a Reference
        private SignedInfo buildSignedInfo(Reference ref) throws Exception {
            SignatureMethod signatureMethod;
            if (sm == null) {
                String alg = privateKey.getAlgorithm().toUpperCase(Locale.ROOT);
                if (alg.equals("RSASSA-PSS")) {
                    PSSParameterSpec pspec
                            = (PSSParameterSpec) ((RSAKey) privateKey).getParams();
                    if (pspec != null) {
                        signatureMethod = FAC.newSignatureMethod(
                                SignatureMethod.RSA_PSS, new RSAPSSParameterSpec(pspec));
                    } else {
                        signatureMethod = FAC.newSignatureMethod(SignatureMethod.RSA_PSS, null);
                    }
                } else {
                    signatureMethod = FAC.newSignatureMethod(switch (alg) {
                        case "RSA" -> SignatureMethod.RSA_SHA256;
                        case "DSA" -> SignatureMethod.DSA_SHA256;
                        case "EC" -> SignatureMethod.ECDSA_SHA256;
                        case "ED25519" -> "http://www.w3.org/2021/04/xmldsig-more#eddsa-ed25519";
                        case "ED448" -> "http://www.w3.org/2021/04/xmldsig-more#eddsa-ed448";
                        case "EDDSA" -> {
                            if (privateKey instanceof EdECPrivateKey edsk) {
                                yield edsk.getParams().getName()
                                        .equals(NamedParameterSpec.ED25519.getName())
                                        ? "http://www.w3.org/2021/04/xmldsig-more#eddsa-ed25519"
                                        : "http://www.w3.org/2021/04/xmldsig-more#eddsa-ed448";
                            } else {
                                throw new InvalidKeyException();
                            }
                        }
                        default -> throw new InvalidKeyException();
                    }, null);
                }
            } else {
                signatureMethod = FAC.newSignatureMethod(sm, smSpec);
            }
            return FAC.newSignedInfo(
                    FAC.newCanonicalizationMethod(cm, (C14NMethodParameterSpec) null),
                    signatureMethod,
                    List.of(ref));
        }

        // Builds a KeyInfo from different sources
        private KeyInfo buildKeyInfo() throws Exception {
            KeyInfoFactory keyInfoFactory = FAC.getKeyInfoFactory();
            if (cert != null) {
                return keyInfoFactory.newKeyInfo(List.of(
                        keyInfoFactory.newX509Data(List.of(cert))));
            } else if (publicKey != null) {
                return keyInfoFactory.newKeyInfo(List.of(
                        keyInfoFactory.newKeyValue(publicKey)));
            } else if (keyName != null) {
                return keyInfoFactory.newKeyInfo(List.of(
                        keyInfoFactory.newKeyName(keyName)));
            } else {
                return null;
            }
        }
    }

    //////////// VALIDATE ////////////

    // Create a Validator, ks will be used if there is a KeyName
    public static Validator validator(KeyStore ks)
            throws Exception {
        return new Validator(ks);
    }

    // Create a Validator, key will either be inside KeyInfo or
    // a key will be provided when validate() is called
    public static Validator validator()
            throws Exception {
        return new Validator(null);
    }

    public static class Validator {

        private Boolean secureValidation = null;
        private String baseURI = null;
        private final KeyStore ks;

        public Validator(KeyStore ks) {
            this.ks = ks;
        }

        public Validator secureValidation(boolean v) {
            this.secureValidation = v;
            return this;
        }

        public Validator baseURI(String base) {
            this.baseURI = base;
            return this;
        }

        public boolean validate(Document document) throws Exception {
            return validate(document, null);
        }

        // If key is not null, any key from the signature will be ignored
        public boolean validate(Document document, PublicKey key)
                throws Exception {
            NodeList nodeList = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (nodeList.getLength() == 1) {
                Node signatureNode = nodeList.item(0);
                if (signatureNode != null) {
                    KeySelector ks = key == null ? new MyKeySelector(this.ks) : new KeySelector() {
                        @Override
                        public KeySelectorResult select(KeyInfo ki, Purpose p,
                                AlgorithmMethod m, XMLCryptoContext c) {
                            return () -> key;
                        }
                    };
                    DOMValidateContext valContext
                            = new DOMValidateContext(ks, signatureNode);
                    if (baseURI != null) {
                        valContext.setBaseURI(baseURI);
                    }
                    if (secureValidation != null) {
                        valContext.setProperty("org.jcp.xml.dsig.secureValidation",
                                secureValidation);
                        valContext.setProperty("org.apache.jcp.xml.dsig.secureValidation",
                                secureValidation);
                    }
                    return XMLSignatureFactory.getInstance("DOM")
                            .unmarshalXMLSignature(valContext).validate(valContext);
                }
            }
            return false;
        }

        // Find public key from KeyInfo, ks will be used if it's KeyName
        private static class MyKeySelector extends KeySelector {
            private final KeyStore ks;

            public MyKeySelector(KeyStore ks) {
                this.ks = ks;
            }

            public KeySelectorResult select(KeyInfo keyInfo,
                                            KeySelector.Purpose purpose,
                                            AlgorithmMethod method,
                                            XMLCryptoContext context)
                    throws KeySelectorException {
                if (keyInfo == null) {
                    throw new IllegalArgumentException("Null KeyInfo object!");
                }

                for (XMLStructure xmlStructure : keyInfo.getContent()) {
                    PublicKey pk;
                    if (xmlStructure instanceof KeyValue kv) {
                        try {
                            pk = kv.getPublicKey();
                        } catch (KeyException ke) {
                            throw new KeySelectorException(ke);
                        }
                        return () -> pk;
                    } else if (xmlStructure instanceof X509Data x509) {
                        for (Object data : x509.getContent()) {
                            if (data instanceof X509Certificate) {
                                pk = ((X509Certificate) data).getPublicKey();
                                return () -> pk;
                            }
                        }
                    } else if (xmlStructure instanceof KeyName kn) {
                        try {
                            pk = ks.getCertificate(kn.getName()).getPublicKey();
                        } catch (KeyStoreException e) {
                            throw new KeySelectorException(e);
                        }
                        return () -> pk;
                    }
                }
                throw new KeySelectorException("No KeyValue element found!");
            }
        }
    }

    //////////// MISC ////////////

    /**
     * Adds a new rule to "jdk.xml.dsig.secureValidationPolicy"
     */
    public static void addPolicy(String rule) {
        String value = Security.getProperty("jdk.xml.dsig.secureValidationPolicy");
        value = rule + "," + value;
        Security.setProperty("jdk.xml.dsig.secureValidationPolicy", value);
    }

    private XMLUtils() {
        assert false : "No one instantiates me";
    }
}
