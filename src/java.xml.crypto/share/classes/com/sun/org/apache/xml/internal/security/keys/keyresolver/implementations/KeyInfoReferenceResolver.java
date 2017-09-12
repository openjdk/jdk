/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import javax.crypto.SecretKey;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.c14n.CanonicalizationException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.KeyInfo;
import com.sun.org.apache.xml.internal.security.keys.content.KeyInfoReference;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.resolver.ResourceResolver;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * KeyResolverSpi implementation which resolves public keys, private keys, secret keys, and X.509 certificates from a
 * <code>dsig11:KeyInfoReference</code> element.
 *
 * @author Brent Putman (putmanb@georgetown.edu)
 */
public class KeyInfoReferenceResolver extends KeyResolverSpi {

    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(KeyInfoReferenceResolver.class.getName());

    /** {@inheritDoc}. */
    public boolean engineCanResolve(Element element, String baseURI, StorageResolver storage) {
        return XMLUtils.elementIsInSignature11Space(element, Constants._TAG_KEYINFOREFERENCE);
    }

    /** {@inheritDoc}. */
    public PublicKey engineLookupAndResolvePublicKey(Element element, String baseURI, StorageResolver storage)
        throws KeyResolverException {

        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName());
        }

        if (!engineCanResolve(element, baseURI, storage)) {
            return null;
        }

        try {
            KeyInfo referent = resolveReferentKeyInfo(element, baseURI, storage);
            if (referent != null) {
                return referent.getPublicKey();
            }
        } catch (XMLSecurityException e) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "XMLSecurityException", e);
            }
        }

        return null;
    }

    /** {@inheritDoc}. */
    public X509Certificate engineLookupResolveX509Certificate(Element element, String baseURI, StorageResolver storage)
        throws KeyResolverException {

        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName());
        }

        if (!engineCanResolve(element, baseURI, storage)) {
            return null;
        }

        try {
            KeyInfo referent = resolveReferentKeyInfo(element, baseURI, storage);
            if (referent != null) {
                return referent.getX509Certificate();
            }
        } catch (XMLSecurityException e) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "XMLSecurityException", e);
            }
        }

        return null;
    }

    /** {@inheritDoc}. */
    public SecretKey engineLookupAndResolveSecretKey(Element element, String baseURI, StorageResolver storage)
        throws KeyResolverException {

        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName());
        }

        if (!engineCanResolve(element, baseURI, storage)) {
            return null;
        }

        try {
            KeyInfo referent = resolveReferentKeyInfo(element, baseURI, storage);
            if (referent != null) {
                return referent.getSecretKey();
            }
        } catch (XMLSecurityException e) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "XMLSecurityException", e);
            }
        }

        return null;
    }

    /** {@inheritDoc}. */
    public PrivateKey engineLookupAndResolvePrivateKey(Element element, String baseURI, StorageResolver storage)
        throws KeyResolverException {

        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName());
        }

        if (!engineCanResolve(element, baseURI, storage)) {
            return null;
        }

        try {
            KeyInfo referent = resolveReferentKeyInfo(element, baseURI, storage);
            if (referent != null) {
                return referent.getPrivateKey();
            }
        } catch (XMLSecurityException e) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "XMLSecurityException", e);
            }
        }

        return null;
    }

    /**
     * Resolve the KeyInfoReference Element's URI attribute into a KeyInfo instance.
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return the KeyInfo which is referred to by this KeyInfoReference, or null if can not be resolved
     * @throws XMLSecurityException
     */
    private KeyInfo resolveReferentKeyInfo(Element element, String baseURI, StorageResolver storage) throws XMLSecurityException {
        KeyInfoReference reference = new KeyInfoReference(element, baseURI);
        Attr uriAttr = reference.getURIAttr();

        XMLSignatureInput resource = resolveInput(uriAttr, baseURI, secureValidation);

        Element referentElement = null;
        try {
            referentElement = obtainReferenceElement(resource);
        } catch (Exception e) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "XMLSecurityException", e);
            }
            return null;
        }

        if (referentElement == null) {
            log.log(java.util.logging.Level.FINE, "De-reference of KeyInfoReference URI returned null: " + uriAttr.getValue());
            return null;
        }

        validateReference(referentElement);

        KeyInfo referent = new KeyInfo(referentElement, baseURI);
        referent.addStorageResolver(storage);
        return referent;
    }

    /**
     * Validate the Element referred to by the KeyInfoReference.
     *
     * @param referentElement
     *
     * @throws XMLSecurityException
     */
    private void validateReference(Element referentElement) throws XMLSecurityException {
        if (!XMLUtils.elementIsInSignatureSpace(referentElement, Constants._TAG_KEYINFO)) {
            Object exArgs[] = { new QName(referentElement.getNamespaceURI(), referentElement.getLocalName()) };
            throw new XMLSecurityException("KeyInfoReferenceResolver.InvalidReferentElement.WrongType", exArgs);
        }

        KeyInfo referent = new KeyInfo(referentElement, "");
        if (referent.containsKeyInfoReference()) {
            if (secureValidation) {
                throw new XMLSecurityException("KeyInfoReferenceResolver.InvalidReferentElement.ReferenceWithSecure");
            } else {
                // Don't support chains of references at this time. If do support in the future, this is where the code
                // would go to validate that don't have a cycle, resulting in an infinite loop. This may be unrealistic
                // to implement, and/or very expensive given remote URI references.
                throw new XMLSecurityException("KeyInfoReferenceResolver.InvalidReferentElement.ReferenceWithoutSecure");
            }
        }

    }

    /**
     * Resolve the XML signature input represented by the specified URI.
     *
     * @param uri
     * @param baseURI
     * @param secureValidation
     * @return
     * @throws XMLSecurityException
     */
    private XMLSignatureInput resolveInput(Attr uri, String baseURI, boolean secureValidation)
        throws XMLSecurityException {
        ResourceResolver resRes = ResourceResolver.getInstance(uri, baseURI, secureValidation);
        XMLSignatureInput resource = resRes.resolve(uri, baseURI, secureValidation);
        return resource;
    }

    /**
     * Resolve the Element effectively represented by the XML signature input source.
     *
     * @param resource
     * @return
     * @throws CanonicalizationException
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     * @throws KeyResolverException
     */
    private Element obtainReferenceElement(XMLSignatureInput resource)
        throws CanonicalizationException, ParserConfigurationException,
        IOException, SAXException, KeyResolverException {

        Element e;
        if (resource.isElement()){
            e = (Element) resource.getSubNode();
        } else if (resource.isNodeSet()) {
            log.log(java.util.logging.Level.FINE, "De-reference of KeyInfoReference returned an unsupported NodeSet");
            return null;
        } else {
            // Retrieved resource is a byte stream
            byte inputBytes[] = resource.getBytes();
            e = getDocFromBytes(inputBytes);
        }
        return e;
    }

    /**
     * Parses a byte array and returns the parsed Element.
     *
     * @param bytes
     * @return the Document Element after parsing bytes
     * @throws KeyResolverException if something goes wrong
     */
    private Element getDocFromBytes(byte[] bytes) throws KeyResolverException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(bytes));
            return doc.getDocumentElement();
        } catch (SAXException ex) {
            throw new KeyResolverException("empty", ex);
        } catch (IOException ex) {
            throw new KeyResolverException("empty", ex);
        } catch (ParserConfigurationException ex) {
            throw new KeyResolverException("empty", ex);
        }
    }

}
