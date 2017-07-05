/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  1999-2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sun.org.apache.xml.internal.security.keys.storage.implementations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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
import com.sun.org.apache.xml.internal.security.utils.Base64;

/**
 * This {@link StorageResolverSpi} makes all raw (binary) {@link X509Certificate}s
 * which reside as files in a single directory available to the {@link com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver}.
 *
 * @author $Author: mullan $
 */
public class CertsInFilesystemDirectoryResolver extends StorageResolverSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                    CertsInFilesystemDirectoryResolver.class.getName());

   /** Field _merlinsCertificatesDir */
   String _merlinsCertificatesDir = null;

   /** Field _certs */
   private List _certs = new ArrayList();

   /** Field _iterator */
   Iterator _iterator = null;

   /**
    *
    *
    * @param directoryName
    * @throws StorageResolverException
    */
   public CertsInFilesystemDirectoryResolver(String directoryName)
           throws StorageResolverException {

      this._merlinsCertificatesDir = directoryName;

      this.readCertsFromHarddrive();

      this._iterator = new FilesystemIterator(this._certs);
   }

   /**
    * Method readCertsFromHarddrive
    *
    * @throws StorageResolverException
    */
   private void readCertsFromHarddrive() throws StorageResolverException {

      File certDir = new File(this._merlinsCertificatesDir);
      ArrayList al = new ArrayList();
      String[] names = certDir.list();

      for (int i = 0; i < names.length; i++) {
         String currentFileName = names[i];

         if (currentFileName.endsWith(".crt")) {
            al.add(names[i]);
         }
      }

      CertificateFactory cf = null;

      try {
         cf = CertificateFactory.getInstance("X.509");
      } catch (CertificateException ex) {
         throw new StorageResolverException("empty", ex);
      }

      if (cf == null) {
         throw new StorageResolverException("empty");
      }

      for (int i = 0; i < al.size(); i++) {
         String filename = certDir.getAbsolutePath() + File.separator
                           + (String) al.get(i);
         File file = new File(filename);
         boolean added = false;
         String dn = null;

         try {
            FileInputStream fis = new FileInputStream(file);
            X509Certificate cert =
               (X509Certificate) cf.generateCertificate(fis);

            fis.close();

            //add to ArrayList
            cert.checkValidity();
            this._certs.add(cert);

            dn = cert.getSubjectDN().getName();
            added = true;
         } catch (FileNotFoundException ex) {
            log.log(java.util.logging.Level.FINE, "Could not add certificate from file " + filename, ex);
         } catch (IOException ex) {
            log.log(java.util.logging.Level.FINE, "Could not add certificate from file " + filename, ex);
         } catch (CertificateNotYetValidException ex) {
            log.log(java.util.logging.Level.FINE, "Could not add certificate from file " + filename, ex);
         } catch (CertificateExpiredException ex) {
            log.log(java.util.logging.Level.FINE, "Could not add certificate from file " + filename, ex);
         } catch (CertificateException ex) {
            log.log(java.util.logging.Level.FINE, "Could not add certificate from file " + filename, ex);
         }

         if (added) {
            if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "Added certificate: " + dn);
         }
      }
   }

   /** @inheritDoc */
   public Iterator getIterator() {
      return this._iterator;
   }

   /**
    * Class FilesystemIterator
    *
    * @author $Author: mullan $
    * @version $Revision: 1.5 $
    */
   private static class FilesystemIterator implements Iterator {

      /** Field _certs */
      List _certs = null;

      /** Field _i */
      int _i;

      /**
       * Constructor FilesystemIterator
       *
       * @param certs
       */
      public FilesystemIterator(List certs) {
         this._certs = certs;
         this._i = 0;
      }

      /** @inheritDoc */
      public boolean hasNext() {
         return (this._i < this._certs.size());
      }

      /** @inheritDoc */
      public Object next() {
         return this._certs.get(this._i++);
      }

      /**
       * Method remove
       *
       */
      public void remove() {
         throw new UnsupportedOperationException(
            "Can't remove keys from KeyStore");
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

      for (Iterator i = krs.getIterator(); i.hasNext(); ) {
         X509Certificate cert = (X509Certificate) i.next();
         byte[] ski =
            com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509SKI
               .getSKIBytesFromCert(cert);

         System.out.println();
         System.out.println("Base64(SKI())=                 \""
                            + Base64.encode(ski) + "\"");
         System.out.println("cert.getSerialNumber()=        \""
                            + cert.getSerialNumber().toString() + "\"");
         System.out.println("cert.getSubjectDN().getName()= \""
                            + cert.getSubjectDN().getName() + "\"");
         System.out.println("cert.getIssuerDN().getName()=  \""
                            + cert.getIssuerDN().getName() + "\"");
      }
   }
}
