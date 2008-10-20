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

import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import javax.crypto.SecretKey;

import com.sun.org.apache.xml.internal.security.encryption.EncryptedKey;
import com.sun.org.apache.xml.internal.security.encryption.XMLCipher;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverSpi;
import com.sun.org.apache.xml.internal.security.keys.storage.StorageResolver;
import com.sun.org.apache.xml.internal.security.utils.EncryptionConstants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import org.w3c.dom.Element;


/**
 * The <code>EncryptedKeyResolver</code> is not a generic resolver.  It can
 * only be for specific instantiations, as the key being unwrapped will
 * always be of a particular type and will always have been wrapped by
 * another key which needs to be recursively resolved.
 *
 * The <code>EncryptedKeyResolver</code> can therefore only be instantiated
 * with an algorithm.  It can also be instantiated with a key (the KEK) or
 * will search the static KeyResolvers to find the appropriate key.
 *
 * @author Berin Lautenbach
 */

public class EncryptedKeyResolver extends KeyResolverSpi {

        /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                        RSAKeyValueResolver.class.getName());


        Key _kek;
        String _algorithm;

        /**
         * Constructor for use when a KEK needs to be derived from a KeyInfo
         * list
         * @param algorithm
         */
        public EncryptedKeyResolver(String algorithm) {
                _kek = null;
        _algorithm=algorithm;
        }

        /**
         * Constructor used for when a KEK has been set
         * @param algorithm
         * @param kek
         */

        public EncryptedKeyResolver(String algorithm, Key kek) {
                _algorithm = algorithm;
                _kek = kek;

        }

    /** @inheritDoc */
   public PublicKey engineLookupAndResolvePublicKey(
           Element element, String BaseURI, StorageResolver storage) {

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
           SecretKey key=null;
           if (log.isLoggable(java.util.logging.Level.FINE))
                        log.log(java.util.logging.Level.FINE, "EncryptedKeyResolver - Can I resolve " + element.getTagName());

              if (element == null) {
                 return null;
              }

              boolean isEncryptedKey = XMLUtils.elementIsInEncryptionSpace(element,
                                      EncryptionConstants._TAG_ENCRYPTEDKEY);

              if (isEncryptedKey) {
                          log.log(java.util.logging.Level.FINE, "Passed an Encrypted Key");
                          try {
                                  XMLCipher cipher = XMLCipher.getInstance();
                                  cipher.init(XMLCipher.UNWRAP_MODE, _kek);
                                  EncryptedKey ek = cipher.loadEncryptedKey(element);
                                  key = (SecretKey) cipher.decryptKey(ek, _algorithm);
                          }
                          catch (Exception e) {}
              }

      return key;
   }
}
