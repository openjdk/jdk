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



import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Iterator;

import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolverException;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolverSpi;


/**
 * Makes the Certificates from a JAVA {@link KeyStore} object available to the
 * {@link com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver}.
 *
 * @author $Author: raul $
 */
public class KeyStoreResolver extends StorageResolverSpi {

   /** Field _keyStore */
   KeyStore _keyStore = null;

   /** Field _iterator */
   Iterator _iterator = null;

   /**
    * Constructor KeyStoreResolver
    *
    * @param keyStore is the keystore which contains the Certificates
    * @throws StorageResolverException
    */
   public KeyStoreResolver(KeyStore keyStore) throws StorageResolverException {
      this._keyStore = keyStore;
      this._iterator = new KeyStoreIterator(this._keyStore);
   }

   /** @inheritDoc */
   public Iterator getIterator() {
      return this._iterator;
   }

   /**
    * Class KeyStoreIterator
    *
    * @author $Author: raul $
    */
   class KeyStoreIterator implements Iterator {

      /** Field _keyStore */
      KeyStore _keyStore = null;

      /** Field _aliases */
      Enumeration _aliases = null;

      /**
       * Constructor KeyStoreIterator
       *
       * @param keyStore
       * @throws StorageResolverException
       */
      public KeyStoreIterator(KeyStore keyStore)
              throws StorageResolverException {

         try {
            this._keyStore = keyStore;
            this._aliases = this._keyStore.aliases();
         } catch (KeyStoreException ex) {
            throw new StorageResolverException("generic.EmptyMessage", ex);
         }
      }

      /** @inheritDoc */
      public boolean hasNext() {
         return this._aliases.hasMoreElements();
      }

      /** @inheritDoc */
      public Object next() {

         String alias = (String) this._aliases.nextElement();

         try {
            return this._keyStore.getCertificate(alias);
         } catch (KeyStoreException ex) {
            return null;
         }
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

      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

      ks.load(
         new java.io.FileInputStream(
         "data/com/sun/org/apache/xml/internal/security/samples/input/keystore.jks"),
            "xmlsecurity".toCharArray());

      KeyStoreResolver krs = new KeyStoreResolver(ks);

      for (Iterator i = krs.getIterator(); i.hasNext(); ) {
         X509Certificate cert = (X509Certificate) i.next();
         byte[] ski =
            com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509SKI
               .getSKIBytesFromCert(cert);

         System.out.println(com.sun.org.apache.xml.internal.security.utils.Base64.encode(ski));
      }
   }
}
