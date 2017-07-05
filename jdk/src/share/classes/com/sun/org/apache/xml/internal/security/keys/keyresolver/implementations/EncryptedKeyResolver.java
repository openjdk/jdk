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
package com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations;

import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import com.sun.org.apache.xml.internal.security.encryption.EncryptedKey;
import com.sun.org.apache.xml.internal.security.encryption.XMLCipher;
import com.sun.org.apache.xml.internal.security.encryption.XMLEncryptionException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.utils.EncryptionConstants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;

/**
 * The <code>EncryptedKeyResolver</code> is not a generic resolver.  It can
 * only be for specific instantiations, as the key being unwrapped will
 * always be of a particular type and will always have been wrapped by
 * another key which needs to be recursively resolved.
 *
 * The <code>EncryptedKeyResolver</code> can therefore only be instantiated
 * with an algorithm.  It can also be instantiated with a key (the KEK) or
 * will search the static KeyResolvers to find the appropriate key.
 *
 * @author Berin Lautenbach
 */
public class EncryptedKeyResolver extends KeyResolverSpi {

    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(EncryptedKeyResolver.class.getName());

    private Key kek;
    private String algorithm;
    private List<KeyResolverSpi> internalKeyResolvers;

    /**
     * Constructor for use when a KEK needs to be derived from a KeyInfo
     * list
     * @param algorithm
     */
    public EncryptedKeyResolver(String algorithm) {
        kek = null;
        this.algorithm = algorithm;
    }

    /**
     * Constructor used for when a KEK has been set
     * @param algorithm
     * @param kek
     */
    public EncryptedKeyResolver(String algorithm, Key kek) {
        this.algorithm = algorithm;
        this.kek = kek;
    }

    /**
     * This method is used to add a custom {@link KeyResolverSpi} to help
     * resolve the KEK.
     *
     * @param realKeyResolver
     */
    public void registerInternalKeyResolver(KeyResolverSpi realKeyResolver) {
        if (internalKeyResolvers == null) {
            internalKeyResolvers = new ArrayList<KeyResolverSpi>();
        }
        internalKeyResolvers.add(realKeyResolver);
    }

    /** @inheritDoc */
    public PublicKey engineLookupAndResolvePublicKey(
        Element element, String BaseURI, StorageResolver storage
    ) {
        return null;
    }

    /** @inheritDoc */
    public X509Certificate engineLookupResolveX509Certificate(
        Element element, String BaseURI, StorageResolver storage
    ) {
        return null;
    }

    /** @inheritDoc */
    public javax.crypto.SecretKey engineLookupAndResolveSecretKey(
        Element element, String BaseURI, StorageResolver storage
    ) {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "EncryptedKeyResolver - Can I resolve " + element.getTagName());
        }

        if (element == null) {
            return null;
        }

        SecretKey key = null;
        boolean isEncryptedKey =
            XMLUtils.elementIsInEncryptionSpace(element, EncryptionConstants._TAG_ENCRYPTEDKEY);
        if (isEncryptedKey) {
            if (log.isLoggable(java.util.logging.Level.FINE)) {
                log.log(java.util.logging.Level.FINE, "Passed an Encrypted Key");
            }
            try {
                XMLCipher cipher = XMLCipher.getInstance();
                cipher.init(XMLCipher.UNWRAP_MODE, kek);
                if (internalKeyResolvers != null) {
                    int size = internalKeyResolvers.size();
                    for (int i = 0; i < size; i++) {
                        cipher.registerInternalKeyResolver(internalKeyResolvers.get(i));
                    }
                }
                EncryptedKey ek = cipher.loadEncryptedKey(element);
                key = (SecretKey) cipher.decryptKey(ek, algorithm);
            } catch (XMLEncryptionException e) {
                if (log.isLoggable(java.util.logging.Level.FINE)) {
                    log.log(java.util.logging.Level.FINE, e.getMessage(), e);
                }
            }
        }

        return key;
    }
}
