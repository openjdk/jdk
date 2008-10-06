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
import com.sun.org.apache.xml.internal.security.keys.content.keyvalues.RSAKeyValue;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;


/**
 *
 * @author $Author: mullan $
 */
public class RSAKeyValueResolver extends KeyResolverSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                        RSAKeyValueResolver.class.getName());

   /** Field _rsaKeyElement */


   /** @inheritDoc */
   public PublicKey engineLookupAndResolvePublicKey(
           Element element, String BaseURI, StorageResolver storage) {
           if (log.isLoggable(java.util.logging.Level.FINE))
                        log.log(java.util.logging.Level.FINE, "Can I resolve " + element.getTagName());
      if (element == null) {
         return null;
      }

          boolean isKeyValue = XMLUtils.elementIsInSignatureSpace(element,
                                              Constants._TAG_KEYVALUE);
          Element rsaKeyElement=null;
          if (isKeyValue) {
                   rsaKeyElement = XMLUtils.selectDsNode(element.getFirstChild(),
                                    Constants._TAG_RSAKEYVALUE, 0);
          } else if (XMLUtils.elementIsInSignatureSpace(element,
              Constants._TAG_RSAKEYVALUE)) {
         // this trick is needed to allow the RetrievalMethodResolver to eat a
         // ds:RSAKeyValue directly (without KeyValue)
         rsaKeyElement = element;
          }


      if (rsaKeyElement == null) {
         return null;
      }

      try {
         RSAKeyValue rsaKeyValue = new RSAKeyValue(rsaKeyElement,
                                                   BaseURI);

         return rsaKeyValue.getPublicKey();
      } catch (XMLSecurityException ex) {
         log.log(java.util.logging.Level.FINE, "XMLSecurityException", ex);
      }

      return null;
   }

   /** @inheritDoc */
   public X509Certificate engineLookupResolveX509Certificate(
           Element element, String BaseURI, StorageResolver storage) {
      return null;
   }

   /** @inheritDoc */
   public javax.crypto.SecretKey engineLookupAndResolveSecretKey(
           Element element, String BaseURI, StorageResolver storage) {
      return null;
   }
}
