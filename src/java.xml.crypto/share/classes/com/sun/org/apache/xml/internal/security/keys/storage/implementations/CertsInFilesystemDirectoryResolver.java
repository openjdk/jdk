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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolverException;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolverSpi;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;

/**
 * This {@link StorageResolverSpi} makes all raw (binary) {@link X509Certificate}s
 * which reside as files in a single directory available to the
 * {@link com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver}.
 */
public class CertsInFilesystemDirectoryResolver extends StorageResolverSpi {

    private static final com.sun.org.slf4j.internal.Logger LOG =
        com.sun.org.slf4j.internal.LoggerFactory.getLogger(
            CertsInFilesystemDirectoryResolver.class
        );

    /** Field merlinsCertificatesDir */
    private String merlinsCertificatesDir;

    /** Field certs */
    private List<X509Certificate> certs = new ArrayList<>();

    /**
     * @param directoryName
     * @throws StorageResolverException
     */
    public CertsInFilesystemDirectoryResolver(String directoryName)
        throws StorageResolverException {
        this.merlinsCertificatesDir = directoryName;

        this.readCertsFromHarddrive();
    }

    /**
     * Method readCertsFromHarddrive
     *
     * @throws StorageResolverException
     */
    private void readCertsFromHarddrive() throws StorageResolverException {

        File certDir = new File(this.merlinsCertificatesDir);
        List<String> al = new ArrayList<>();
        String[] names = certDir.list();

        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                String currentFileName = names[i];

                if (currentFileName.endsWith(".crt")) {
                    al.add(names[i]);
                }
            }
        }

        CertificateFactory cf = null;

        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException ex) {
            throw new StorageResolverException(ex);
        }

        for (int i = 0; i < al.size(); i++) {
            String filename = certDir.getAbsolutePath() + File.separator + al.get(i);
            boolean added = false;
            String dn = null;

            try (InputStream inputStream = Files.newInputStream(Paths.get(filename))) {
                X509Certificate cert =
                    (X509Certificate) cf.generateCertificate(inputStream);

                //add to ArrayList
                cert.checkValidity();
                this.certs.add(cert);

                dn = cert.getSubjectX500Principal().getName();
                added = true;
            } catch (FileNotFoundException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not add certificate from file " + filename, ex);
                }
            } catch (CertificateNotYetValidException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not add certificate from file " + filename, ex);
                }
            } catch (CertificateExpiredException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not add certificate from file " + filename, ex);
                }
            } catch (CertificateException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not add certificate from file " + filename, ex);
                }
            } catch (IOException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not add certificate from file " + filename, ex);
                }
            }

            if (added) {
                LOG.debug("Added certificate: {}", dn);
            }
        }
    }

    /** {@inheritDoc} */
    public Iterator<Certificate> getIterator() {
        return new FilesystemIterator(this.certs);
    }

    /**
     * Class FilesystemIterator
     */
    private static class FilesystemIterator implements Iterator<Certificate> {

        /** Field certs */
        private List<X509Certificate> certs;

        /** Field i */
        private int i;

        /**
         * Constructor FilesystemIterator
         *
         * @param certs
         */
        public FilesystemIterator(List<X509Certificate> certs) {
            this.certs = certs;
            this.i = 0;
        }

        /** {@inheritDoc} */
        public boolean hasNext() {
            return this.i < this.certs.size();
        }

        /** {@inheritDoc} */
        public Certificate next() {
            return this.certs.get(this.i++);
        }

        /**
         * Method remove
         *
         */
        public void remove() {
            throw new UnsupportedOperationException("Can't remove keys from KeyStore");
        }
    }

    /**
     * Method main
     *
     * @param unused
     * @throws Exception
     */
    public static void main(String unused[]) throws Exception {

        CertsInFilesystemDirectoryResolver krs =
            new CertsInFilesystemDirectoryResolver(
                "data/ie/baltimore/merlin-examples/merlin-xmldsig-eighteen/certs");

        for (Iterator<Certificate> i = krs.getIterator(); i.hasNext(); ) {
            X509Certificate cert = (X509Certificate) i.next();
            byte[] ski =
                com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509SKI.getSKIBytesFromCert(cert);

            System.out.println();
            System.out.println("Base64(SKI())=                 \""
                               + XMLUtils.encodeToString(ski) + "\"");
            System.out.println("cert.getSerialNumber()=        \""
                               + cert.getSerialNumber().toString() + "\"");
            System.out.println("cert.getSubjectX500Principal().getName()= \""
                               + cert.getSubjectX500Principal().getName() + "\"");
            System.out.println("cert.getIssuerX500Principal().getName()=  \""
                               + cert.getIssuerX500Principal().getName() + "\"");
        }
    }
}
