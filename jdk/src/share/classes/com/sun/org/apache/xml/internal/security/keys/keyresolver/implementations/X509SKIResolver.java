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
import com.sun.org.apache.xml.internal.security.keys.content.x509.XMLX509SKI;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;


/**
 *
 *
 * @author $Author: raul $
 */
public class X509SKIResolver extends KeyResolverSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(X509SKIResolver.class.getName());

   /** Field _x509childNodes */
   private Element _x509childNodes[] = null;

   /** Field _x509childObject[] */
   private XMLX509SKI _x509childObject[] = null;

   /**
    * Method engineCanResolve
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    */
   public boolean engineCanResolve(Element element, String BaseURI,
                                   StorageResolver storage) {
      if (true) {
        if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName() + "?");
      }

         if (!XMLUtils.elementIsInSignatureSpace(element,
                 Constants._TAG_X509DATA)) {
         if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "I can't");

         return false;
      }




         this._x509childNodes = XMLUtils.selectDsNodes(element,
                  Constants._TAG_X509SKI);

         if ((this._x509childNodes != null)
                 && (this._x509childNodes.length > 0)) {
            if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Yes Sir, I can");

            return true;
         }

      if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "I can't");

      return false;
   }

   /**
    * Method engineResolvePublicKey
    *
    * @param element
    * @param BaseURI
    * @param storage
    * @return null if no {@link PublicKey} could be obtained
    * @throws KeyResolverException
    */
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

   /**
    * Method engineResolveX509Certificate
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    * @throws KeyResolverException
    */
   public X509Certificate engineResolveX509Certificate(
           Element element, String BaseURI, StorageResolver storage)
              throws KeyResolverException {

      try {
         if (this._x509childNodes == null) {
            boolean weCanResolve = this.engineCanResolve(element, BaseURI,
                                      storage);

            if (!weCanResolve || (this._x509childNodes == null)) {
               return null;
            }
         }

         if (storage == null) {
            Object exArgs[] = { Constants._TAG_X509SKI };
            KeyResolverException ex =
               new KeyResolverException("KeyResolver.needStorageResolver",
                                        exArgs);

            if (log.isLoggable(java.util.logging.Level.INFO))                                  log.log(java.util.logging.Level.INFO, "", ex);

            throw ex;
         }

         this._x509childObject =
            new XMLX509SKI[this._x509childNodes.length];

         for (int i = 0; i < this._x509childNodes.length; i++) {
            this._x509childObject[i] =
               new XMLX509SKI(this._x509childNodes[i], BaseURI);
         }

         while (storage.hasNext()) {
            X509Certificate cert = storage.next();
            XMLX509SKI certSKI = new XMLX509SKI(element.getOwnerDocument(), cert);

            for (int i = 0; i < this._x509childObject.length; i++) {
               if (certSKI.equals(this._x509childObject[i])) {
                  if (log.isLoggable(java.util.logging.Level.FINE))                                     log.log(java.util.logging.Level.FINE, "Return PublicKey from "
                            + cert.getSubjectDN().getName());

                  return cert;
               }
            }
         }
      } catch (XMLSecurityException ex) {
         throw new KeyResolverException("empty", ex);
      }

      return null;
   }

   /**
    * Method engineResolveSecretKey
    * @inheritDoc
    * @param element
    * @param BaseURI
    * @param storage
    *
    */
   public javax.crypto.SecretKey engineResolveSecretKey(
           Element element, String BaseURI, StorageResolver storage)
    {
      return null;
   }
}
