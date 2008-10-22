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
package com.sun.org.apache.xml.internal.security.keys.storage;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.org.apache.xml.internal.security.keys.storage.implementations.KeyStoreResolver;
import com.sun.org.apache.xml.internal.security.keys.storage.implementations.SingleCertificateResolver;


/**
 * This class collects customized resolvers for Certificates.
 *
 * @author $Author: mullan $
 */
public class StorageResolver {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(StorageResolver.class.getName());

   /** Field _storageResolvers */
   List _storageResolvers = null;

   /** Field _iterator */
   Iterator _iterator = null;

   /**
    * Constructor StorageResolver
    *
    */
   public StorageResolver() {}

   /**
    * Constructor StorageResolver
    *
    * @param resolver
    */
   public StorageResolver(StorageResolverSpi resolver) {
      this.add(resolver);
   }

   /**
    * Method addResolver
    *
    * @param resolver
    */
   public void add(StorageResolverSpi resolver) {
           if (_storageResolvers==null)
                   _storageResolvers=new ArrayList();
      this._storageResolvers.add(resolver);

      this._iterator = null;
   }

   /**
    * Constructor StorageResolver
    *
    * @param keyStore
    */
   public StorageResolver(KeyStore keyStore) {
      this.add(keyStore);
   }

   /**
    * Method addKeyStore
    *
    * @param keyStore
    */
   public void add(KeyStore keyStore) {

      try {
         this.add(new KeyStoreResolver(keyStore));
      } catch (StorageResolverException ex) {
         log.log(java.util.logging.Level.SEVERE, "Could not add KeyStore because of: ", ex);
      }
   }

   /**
    * Constructor StorageResolver
    *
    * @param x509certificate
    */
   public StorageResolver(X509Certificate x509certificate) {
      this.add(x509certificate);
   }

   /**
    * Method addCertificate
    *
    * @param x509certificate
    */
   public void add(X509Certificate x509certificate) {
      this.add(new SingleCertificateResolver(x509certificate));
   }

   /**
    * Method getIterator
    * @return the iterator for the resolvers.
    *
    */
   public Iterator getIterator() {

      if (this._iterator == null) {
         if (_storageResolvers==null)
                   _storageResolvers=new ArrayList();
         this._iterator = new StorageResolverIterator(this._storageResolvers.iterator());
      }

      return this._iterator;
   }

   /**
    * Method hasNext
    *
    * @return true if there is more elements.
    */
   public boolean hasNext() {

      if (this._iterator == null) {
          if (_storageResolvers==null)
                   _storageResolvers=new ArrayList();
         this._iterator = new StorageResolverIterator(this._storageResolvers.iterator());
      }

      return this._iterator.hasNext();
   }

   /**
    * Method next
    *
    * @return the next element
    */
   public X509Certificate next() {
      return (X509Certificate) this._iterator.next();
   }

   /**
    * Class StorageResolverIterator
    *
    * @author $Author: mullan $
    * @version $Revision: 1.5 $
    */
   static class StorageResolverIterator implements Iterator {

      /** Field _resolvers */
      Iterator _resolvers = null;

      /**
       * Constructor FilesystemIterator
       *
       * @param resolvers
       */
      public StorageResolverIterator(Iterator resolvers) {
         this._resolvers = resolvers;
      }

      /** @inheritDoc */
      public boolean hasNext() {
          return _resolvers.hasNext();
      }

      /** @inheritDoc */
      public Object next() {
          return _resolvers.next();
      }

      /**
       * Method remove
       */
      public void remove() {
         throw new UnsupportedOperationException(
            "Can't remove keys from KeyStore");
      }
   }
}
