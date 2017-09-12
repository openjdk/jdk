/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations;

import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import javax.crypto.SecretKey;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;

/**
 * Resolves a SecretKey within a KeyStore based on the KeyName.
 * The KeyName is the key entry alias within the KeyStore.
 */
public class SecretKeyResolver extends KeyResolverSpi
{
    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(SecretKeyResolver.class.getName());

    private KeyStore keyStore;
    private char[] password;

    /**
     * Constructor.
     */
    public SecretKeyResolver(KeyStore keyStore, char[] password) {
        this.keyStore = keyStore;
        this.password = password;
    }

    /**
     * This method returns whether the KeyResolverSpi is able to perform the requested action.
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return whether the KeyResolverSpi is able to perform the requested action.
     */
    public boolean engineCanResolve(Element element, String baseURI, StorageResolver storage) {
        return XMLUtils.elementIsInSignatureSpace(element, Constants._TAG_KEYNAME);
    }

    /**
     * Method engineLookupAndResolvePublicKey
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return null if no {@link PublicKey} could be obtained
     * @throws KeyResolverException
     */
    public PublicKey engineLookupAndResolvePublicKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        return null;
    }

    /**
     * Method engineResolveX509Certificate
     * @inheritDoc
     * @param element
     * @param baseURI
     * @param storage
     * @throws KeyResolverException
     */
    public X509Certificate engineLookupResolveX509Certificate(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        return null;
    }

    /**
     * Method engineResolveSecretKey
     *
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved SecretKey key or null if no {@link SecretKey} could be obtained
     *
     * @throws KeyResolverException
     */
    public SecretKey engineResolveSecretKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName() + "?");
        }

        if (XMLUtils.elementIsInSignatureSpace(element, Constants._TAG_KEYNAME)) {
            String keyName = element.getFirstChild().getNodeValue();
            try {
                Key key = keyStore.getKey(keyName, password);
                if (key instanceof SecretKey) {
                    return (SecretKey) key;
                }
            } catch (Exception e) {
                log.log(java.util.logging.Level.FINE, "Cannot recover the key", e);
            }
        }

        log.log(java.util.logging.Level.FINE, "I can't");
        return null;
    }

    /**
     * Method engineResolvePrivateKey
     * @inheritDoc
     * @param element
     * @param baseURI
     * @param storage
     * @return resolved PrivateKey key or null if no {@link PrivateKey} could be obtained
     * @throws KeyResolverException
     */
    public PrivateKey engineLookupAndResolvePrivateKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        return null;
    }
}
