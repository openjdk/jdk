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
package com.sun.org.apache.xml.internal.security.keys.storage.implementations;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolverException;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolverSpi;

/**
 * Makes the Certificates from a JAVA {@link KeyStore} object available to the
 * {@link com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver}.
 */
public class KeyStoreResolver extends StorageResolverSpi {

    /** Field keyStore */
    private KeyStore keyStore;

    /**
     * Constructor KeyStoreResolver
     *
     * @param keyStore is the keystore which contains the Certificates
     * @throws StorageResolverException
     */
    public KeyStoreResolver(KeyStore keyStore) throws StorageResolverException {
        this.keyStore = keyStore;
        // Do a quick check on the keystore
        try {
            keyStore.aliases();
        } catch (KeyStoreException ex) {
            throw new StorageResolverException(ex);
        }
    }

    /** {@inheritDoc} */
    public Iterator<Certificate> getIterator() {
        return new KeyStoreIterator(this.keyStore);
    }

    /**
     * Class KeyStoreIterator
     */
    static class KeyStoreIterator implements Iterator<Certificate> {

        /** Field keyStore */
        KeyStore keyStore = null;

        /** Field aliases */
        Enumeration<String> aliases = null;

        /** Field nextCert */
        Certificate nextCert = null;

        /**
         * Constructor KeyStoreIterator
         *
         * @param keyStore
         */
        public KeyStoreIterator(KeyStore keyStore) {
            try {
                this.keyStore = keyStore;
                this.aliases = this.keyStore.aliases();
            } catch (KeyStoreException ex) {
                // empty Enumeration
                this.aliases = new Enumeration<String>() {
                    public boolean hasMoreElements() {
                        return false;
                    }
                    public String nextElement() {
                        return null;
                    }
                };
            }
        }

        /** {@inheritDoc} */
        public boolean hasNext() {
            if (nextCert == null) {
                nextCert = findNextCert();
            }

            return nextCert != null;
        }

        /** {@inheritDoc} */
        public Certificate next() {
            if (nextCert == null) {
                // maybe caller did not call hasNext()
                nextCert = findNextCert();

                if (nextCert == null) {
                    throw new NoSuchElementException();
                }
            }

            Certificate ret = nextCert;
            nextCert = null;
            return ret;
        }

        /**
         * Method remove
         */
        public void remove() {
            throw new UnsupportedOperationException("Can't remove keys from KeyStore");
        }

        // Find the next entry that contains a certificate and return it.
        // In particular, this skips over entries containing symmetric keys.
        private Certificate findNextCert() {
            while (this.aliases.hasMoreElements()) {
                String alias = this.aliases.nextElement();
                try {
                    Certificate cert = this.keyStore.getCertificate(alias);
                    if (cert != null) {
                        return cert;
                    }
                } catch (KeyStoreException ex) {
                    return null;
                }
            }

            return null;
        }

    }

}
