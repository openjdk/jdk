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

import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Iterator;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.content.X509Data;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509IssuerSerial;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import org.w3c.dom.Element;

public class X509IssuerSerialResolver extends KeyResolverSpi {

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(X509IssuerSerialResolver.class);


    /** {@inheritDoc} */
    public PublicKey engineLookupAndResolvePublicKey(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {

        X509Certificate cert =
            this.engineLookupResolveX509Certificate(element, baseURI, storage);

        if (cert != null) {
            return cert.getPublicKey();
        }

        return null;
    }

    /** {@inheritDoc} */
    public X509Certificate engineLookupResolveX509Certificate(
        Element element, String baseURI, StorageResolver storage
    ) throws KeyResolverException {
        LOG.debug("Can I resolve {}?", element.getTagName());

        X509Data x509data = null;
        try {
            x509data = new X509Data(element, baseURI);
        } catch (XMLSignatureException ex) {
            LOG.debug("I can't");
            return null;
        } catch (XMLSecurityException ex) {
            LOG.debug("I can't");
            return null;
        }

        if (!x509data.containsIssuerSerial()) {
            return null;
        }
        try {
            if (storage == null) {
                Object exArgs[] = { Constants._TAG_X509ISSUERSERIAL };
                KeyResolverException ex =
                    new KeyResolverException("KeyResolver.needStorageResolver", exArgs);

                LOG.debug("", ex);
                throw ex;
            }

            int noOfISS = x509data.lengthIssuerSerial();

            Iterator<Certificate> storageIterator = storage.getIterator();
            while (storageIterator.hasNext()) {
                X509Certificate cert = (X509Certificate)storageIterator.next();
                XMLX509IssuerSerial certSerial = new XMLX509IssuerSerial(element.getOwnerDocument(), cert);

                LOG.debug("Found Certificate Issuer: {}", certSerial.getIssuerName());
                LOG.debug("Found Certificate Serial: {}", certSerial.getSerialNumber().toString());

                for (int i = 0; i < noOfISS; i++) {
                    XMLX509IssuerSerial xmliss = x509data.itemIssuerSerial(i);

                    LOG.debug("Found Element Issuer:     {}", xmliss.getIssuerName());
                    LOG.debug("Found Element Serial:     {}", xmliss.getSerialNumber().toString());

                    if (certSerial.equals(xmliss)) {
                        LOG.debug("match !!! ");
                        return cert;
                    }
                    LOG.debug("no match...");
                }
            }

            return null;
        } catch (XMLSecurityException ex) {
            LOG.debug("XMLSecurityException", ex);

            throw new KeyResolverException(ex);
        }
    }

    /** {@inheritDoc} */
    public javax.crypto.SecretKey engineLookupAndResolveSecretKey(
        Element element, String baseURI, StorageResolver storage
    ) {
        return null;
    }
}
