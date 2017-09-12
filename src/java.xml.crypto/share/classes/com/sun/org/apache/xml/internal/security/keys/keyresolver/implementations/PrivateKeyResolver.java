/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations;

import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import javax.crypto.SecretKey;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.content.X509Data;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509Certificate;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509IssuerSerial;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509SKI;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509SubjectName;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;

/**
 * Resolves a PrivateKey within a KeyStore based on the KeyInfo hints.
 * For X509Data hints, the certificate associated with the private key entry must match.
 * For a KeyName hint, the KeyName must match the alias of a PrivateKey entry within the KeyStore.
 */
public class PrivateKeyResolver extends KeyResolverSpi {
    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(PrivateKeyResolver.class.getName());

    private KeyStore keyStore;
    private char[] password;

    /**
     * Constructor.
     */
    public PrivateKeyResolver(KeyStore keyStore, char[] password) {
        this.keyStore = keyStore;
        this.password = password;
    }

    /**
     * This method returns whether the KeyResolverSpi is able to perform the requested action.
     *
     * @param element
     * @param BaseURI
     * @param storage
     * @return whether the KeyResolverSpi is able to perform the requested action.
     */
    public boolean engineCanResolve(Element element, String BaseURI, StorageResolver storage) {
        if (XMLUtils.elementIsInSignatureSpace(element, Constants._TAG_X509DATA)
            || XMLUtils.elementIsInSignatureSpace(element, Constants._TAG_KEYNAME)) {
            return true;
        }

        return false;
    }

    /**
     * Method engineLookupAndResolvePublicKey
     *
     * @param element
     * @param BaseURI
     * @param storage
     * @return null if no {@link PublicKey} could be obtained
     * @throws KeyResolverException
     */
    public PublicKey engineLookupAndResolvePublicKey(
        Element element, String BaseURI, StorageResolver storage
    ) throws KeyResolverException {
        return null;
    }

    /**
     * Method engineResolveX509Certificate
     * @inheritDoc
     * @param element
     * @param BaseURI
     * @param storage
     * @throws KeyResolverException
     */
    public X509Certificate engineLookupResolveX509Certificate(
        Element element, String BaseURI, StorageResolver storage
    ) throws KeyResolverException {
        return null;
    }

    /**
     * Method engineResolveSecretKey
     *
     * @param element
     * @param BaseURI
     * @param storage
     * @return resolved SecretKey key or null if no {@link SecretKey} could be obtained
     *
     * @throws KeyResolverException
     */
    public SecretKey engineResolveSecretKey(
        Element element, String BaseURI, StorageResolver storage
    ) throws KeyResolverException {
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
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName() + "?");
        }

        if (XMLUtils.elementIsInSignatureSpace(element, Constants._TAG_X509DATA)) {
            PrivateKey privKey = resolveX509Data(element, baseURI);
            if (privKey != null) {
                return privKey;
            }
        } else if (XMLUtils.elementIsInSignatureSpace(element, Constants._TAG_KEYNAME)) {
            log.log(java.util.logging.Level.FINE, "Can I resolve KeyName?");
            String keyName = element.getFirstChild().getNodeValue();

            try {
                Key key = keyStore.getKey(keyName, password);
                if (key instanceof PrivateKey) {
                    return (PrivateKey) key;
                }
            } catch (Exception e) {
                log.log(java.util.logging.Level.FINE, "Cannot recover the key", e);
            }
        }

        log.log(java.util.logging.Level.FINE, "I can't");
        return null;
    }

    private PrivateKey resolveX509Data(Element element, String baseURI) {
        log.log(java.util.logging.Level.FINE, "Can I resolve X509Data?");

        try {
            X509Data x509Data = new X509Data(element, baseURI);

            int len = x509Data.lengthSKI();
            for (int i = 0; i < len; i++) {
                XMLX509SKI x509SKI = x509Data.itemSKI(i);
                PrivateKey privKey = resolveX509SKI(x509SKI);
                if (privKey != null) {
                    return privKey;
                }
            }

            len = x509Data.lengthIssuerSerial();
            for (int i = 0; i < len; i++) {
                XMLX509IssuerSerial x509Serial = x509Data.itemIssuerSerial(i);
                PrivateKey privKey = resolveX509IssuerSerial(x509Serial);
                if (privKey != null) {
                    return privKey;
                }
            }

            len = x509Data.lengthSubjectName();
            for (int i = 0; i < len; i++) {
                XMLX509SubjectName x509SubjectName = x509Data.itemSubjectName(i);
                PrivateKey privKey = resolveX509SubjectName(x509SubjectName);
                if (privKey != null) {
                    return privKey;
                }
            }

            len = x509Data.lengthCertificate();
            for (int i = 0; i < len; i++) {
                XMLX509Certificate x509Cert = x509Data.itemCertificate(i);
                PrivateKey privKey = resolveX509Certificate(x509Cert);
                if (privKey != null) {
                    return privKey;
                }
            }
        } catch (XMLSecurityException e) {
            log.log(java.util.logging.Level.FINE, "XMLSecurityException", e);
        } catch (KeyStoreException e) {
            log.log(java.util.logging.Level.FINE, "KeyStoreException", e);
        }

        return null;
    }

    /*
     * Search for a private key entry in the KeyStore with the same Subject Key Identifier
     */
    private PrivateKey resolveX509SKI(XMLX509SKI x509SKI) throws XMLSecurityException, KeyStoreException {
        log.log(java.util.logging.Level.FINE, "Can I resolve X509SKI?");

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {

                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    XMLX509SKI certSKI = new XMLX509SKI(x509SKI.getDocument(), (X509Certificate) cert);

                    if (certSKI.equals(x509SKI)) {
                        log.log(java.util.logging.Level.FINE, "match !!! ");

                        try {
                            Key key = keyStore.getKey(alias, password);
                            if (key instanceof PrivateKey) {
                                return (PrivateKey) key;
                            }
                        } catch (Exception e) {
                            log.log(java.util.logging.Level.FINE, "Cannot recover the key", e);
                            // Keep searching
                        }
                    }
                }
            }
        }

        return null;
    }

    /*
     * Search for a private key entry in the KeyStore with the same Issuer/Serial Number pair.
     */
    private PrivateKey resolveX509IssuerSerial(XMLX509IssuerSerial x509Serial) throws KeyStoreException {
        log.log(java.util.logging.Level.FINE, "Can I resolve X509IssuerSerial?");

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {

                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    XMLX509IssuerSerial certSerial =
                        new XMLX509IssuerSerial(x509Serial.getDocument(), (X509Certificate) cert);

                    if (certSerial.equals(x509Serial)) {
                        log.log(java.util.logging.Level.FINE, "match !!! ");

                        try {
                            Key key = keyStore.getKey(alias, password);
                            if (key instanceof PrivateKey) {
                                return (PrivateKey) key;
                            }
                        } catch (Exception e) {
                            log.log(java.util.logging.Level.FINE, "Cannot recover the key", e);
                            // Keep searching
                        }
                    }
                }
            }
        }

        return null;
    }

    /*
     * Search for a private key entry in the KeyStore with the same Subject Name.
     */
    private PrivateKey resolveX509SubjectName(XMLX509SubjectName x509SubjectName) throws KeyStoreException {
        log.log(java.util.logging.Level.FINE, "Can I resolve X509SubjectName?");

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {

                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    XMLX509SubjectName certSN =
                        new XMLX509SubjectName(x509SubjectName.getDocument(), (X509Certificate) cert);

                    if (certSN.equals(x509SubjectName)) {
                        log.log(java.util.logging.Level.FINE, "match !!! ");

                        try {
                            Key key = keyStore.getKey(alias, password);
                            if (key instanceof PrivateKey) {
                                return (PrivateKey) key;
                            }
                        } catch (Exception e) {
                            log.log(java.util.logging.Level.FINE, "Cannot recover the key", e);
                            // Keep searching
                        }
                    }
                }
            }
        }

        return null;
    }

    /*
     * Search for a private key entry in the KeyStore with the same Certificate.
     */
    private PrivateKey resolveX509Certificate(
        XMLX509Certificate x509Cert
    ) throws XMLSecurityException, KeyStoreException {
        log.log(java.util.logging.Level.FINE, "Can I resolve X509Certificate?");
        byte[] x509CertBytes = x509Cert.getCertificateBytes();

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {

                Certificate cert = keyStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    byte[] certBytes = null;

                    try {
                        certBytes = cert.getEncoded();
                    } catch (CertificateEncodingException e1) {
                    }

                    if (certBytes != null && Arrays.equals(certBytes, x509CertBytes)) {
                        log.log(java.util.logging.Level.FINE, "match !!! ");

                        try {
                            Key key = keyStore.getKey(alias, password);
                            if (key instanceof PrivateKey) {
                                return (PrivateKey) key;
                            }
                        }
                        catch (Exception e) {
                            log.log(java.util.logging.Level.FINE, "Cannot recover the key", e);
                            // Keep searching
                        }
                    }
                }
            }
        }

        return null;
    }
}
