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
package com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations;



import java.security.PublicKey;
import java.security.cert.X509Certificate;

import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.content.X509Data;
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509IssuerSerial;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import org.w3c.dom.Element;


/**
 *
 * @author $Author: raul $
 */
public class X509IssuerSerialResolver extends KeyResolverSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                    X509IssuerSerialResolver.class.getName());

    /** @inheritDoc */
   public boolean engineCanResolve(Element element, String BaseURI,
                                   StorageResolver storage) {
      if (true)
        if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName() + "?");

      X509Data x509data = null;
      try {
         x509data = new X509Data(element, BaseURI);
      } catch (XMLSignatureException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "I can't");

         return false;
      } catch (XMLSecurityException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "I can't");

         return false;
      }

      if (x509data == null) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "I can't");
         return false;
      }

      if (x509data.containsIssuerSerial()) {
            return true;
      }

      if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "I can't");
      return false;
   }

   /** @inheritDoc */
   public PublicKey engineResolvePublicKey(
           Element element, String BaseURI, StorageResolver storage)
              throws KeyResolverException {

      X509Certificate cert = this.engineResolveX509Certificate(element,
                                BaseURI, storage);

      if (cert != null) {
         return cert.getPublicKey();
      }

      return null;
   }

   /** @inheritDoc */
   public X509Certificate engineResolveX509Certificate(
           Element element, String BaseURI, StorageResolver storage)
              throws KeyResolverException {

      try {
         if (storage == null) {
            Object exArgs[] = { Constants._TAG_X509ISSUERSERIAL };
            KeyResolverException ex =
               new KeyResolverException("KeyResolver.needStorageResolver",
                                        exArgs);

            if (log.isLoggable(java.util.logging.Level.INFO))                                  log.log(java.util.logging.Level.INFO, "", ex);
            throw ex;
         }

         X509Data x509data = new X509Data(element, BaseURI);
         int noOfISS = x509data.lengthIssuerSerial();

         while (storage.hasNext()) {
            X509Certificate cert = storage.next();
            XMLX509IssuerSerial certSerial = new XMLX509IssuerSerial(element.getOwnerDocument(), cert);

            if (true) {
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Found Certificate Issuer: "
                      + certSerial.getIssuerName());
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Found Certificate Serial: "
                      + certSerial.getSerialNumber().toString());
            }

            for (int i=0; i<noOfISS; i++) {
               XMLX509IssuerSerial xmliss = x509data.itemIssuerSerial(i);

               if (true) {
                    if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Found Element Issuer:     "
                         + xmliss.getIssuerName());
                    if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Found Element Serial:     "
                         + xmliss.getSerialNumber().toString());
               }

               if (certSerial.equals(xmliss)) {
                  if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "match !!! ");

                  return cert;
               }
                if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "no match...");
            }
         }

         return null;
      } catch (XMLSecurityException ex) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "XMLSecurityException", ex);

         throw new KeyResolverException("generic.EmptyMessage", ex);
      }
   }

   /** @inheritDoc */
   public javax.crypto.SecretKey engineResolveSecretKey(
           Element element, String BaseURI, StorageResolver storage) {
      return null;
   }
}
