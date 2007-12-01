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
import com.sun.org.apache.xml.internal.security.keys.content.keyvalues.DSAKeyValue;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;


/**
 *
 * @author $Author: raul $
 */
public class DSAKeyValueResolver extends KeyResolverSpi {

   /** Field _dsaKeyElement */
   private Element _dsaKeyElement = null;

   /** @inheritDoc */
   public boolean engineCanResolve(Element element, String BaseURI,
                                   StorageResolver storage) {

      if (element == null) {
         return false;
      }

      boolean isKeyValue = XMLUtils.elementIsInSignatureSpace(element,
                              Constants._TAG_KEYVALUE);
      boolean isDSAKeyValue = XMLUtils.elementIsInSignatureSpace(element,
                                 Constants._TAG_DSAKEYVALUE);

      if (isKeyValue) {

            this._dsaKeyElement =
                XMLUtils.selectDsNode(element.getFirstChild(),Constants._TAG_DSAKEYVALUE,0);

            if (this._dsaKeyElement != null) {
               return true;
            }
      } else if (isDSAKeyValue) {

         // this trick is needed to allow the RetrievalMethodResolver to eat a
         // ds:DSAKeyValue directly (without KeyValue)
         this._dsaKeyElement = element;

         return true;
      }

      return false;
   }

   /**
    * Method engineResolvePublicKey
    *
    * @param element
    * @param BaseURI
    * @param storage
    * @return null if no {@link PublicKey} could be obtained
    */
   public PublicKey engineResolvePublicKey(
           Element element, String BaseURI, StorageResolver storage) {

      if (this._dsaKeyElement == null) {
         boolean weCanResolve = this.engineCanResolve(element, BaseURI,
                                   storage);

         if (!weCanResolve || (this._dsaKeyElement == null)) {
            return null;
         }
      }

      try {
         DSAKeyValue dsaKeyValue = new DSAKeyValue(this._dsaKeyElement,
                                                   BaseURI);
         PublicKey pk = dsaKeyValue.getPublicKey();

         return pk;
      } catch (XMLSecurityException ex) {
                //do nothing
      }

      return null;
   }


   /** @inheritDoc */
   public X509Certificate engineResolveX509Certificate(
           Element element, String BaseURI, StorageResolver storage) {
      return null;
   }

   /** @inheritDoc */
   public javax.crypto.SecretKey engineResolveSecretKey(
           Element element, String BaseURI, StorageResolver storage){
      return null;
   }
}
