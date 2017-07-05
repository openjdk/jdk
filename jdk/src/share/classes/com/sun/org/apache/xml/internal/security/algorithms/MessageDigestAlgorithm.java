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
package com.sun.org.apache.xml.internal.security.algorithms;



import java.security.MessageDigest;
import java.security.NoSuchProviderException;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.EncryptionConstants;
import org.w3c.dom.Document;


/**
 * Digest Message wrapper & selector class.
 *
 * <pre>
 * MessageDigestAlgorithm.getInstance()
 * </pre>
 *
 */
public class MessageDigestAlgorithm extends Algorithm {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(
                    MessageDigestAlgorithm.class.getName());

    /** Message Digest - NOT RECOMMENDED MD5*/
   public static final String ALGO_ID_DIGEST_NOT_RECOMMENDED_MD5 = Constants.MoreAlgorithmsSpecNS + "md5";
   /** Digest - Required SHA1*/
   public static final String ALGO_ID_DIGEST_SHA1 = Constants.SignatureSpecNS + "sha1";
   /** Message Digest - RECOMMENDED SHA256*/
   public static final String ALGO_ID_DIGEST_SHA256 = EncryptionConstants.EncryptionSpecNS + "sha256";
   /** Message Digest - OPTIONAL SHA384*/
   public static final String ALGO_ID_DIGEST_SHA384 = Constants.MoreAlgorithmsSpecNS + "sha384";
   /** Message Digest - OPTIONAL SHA512*/
   public static final String ALGO_ID_DIGEST_SHA512 = EncryptionConstants.EncryptionSpecNS + "sha512";
   /** Message Digest - OPTIONAL RIPEMD-160*/
   public static final String ALGO_ID_DIGEST_RIPEMD160 = EncryptionConstants.EncryptionSpecNS + "ripemd160";

   /** Field algorithm stores the actual {@link java.security.MessageDigest} */
   java.security.MessageDigest algorithm = null;

   /**
    * Constructor for the brave who pass their own message digest algorithms and the corresponding URI.
    * @param doc
    * @param messageDigest
    * @param algorithmURI
    */
   private MessageDigestAlgorithm(Document doc, MessageDigest messageDigest,
                                  String algorithmURI) {

      super(doc, algorithmURI);

      this.algorithm = messageDigest;
   }

   /**
    * Factory method for constructing a message digest algorithm by name.
    *
    * @param doc
    * @param algorithmURI
    * @return The MessageDigestAlgorithm element to attach in document and to digest
    * @throws XMLSignatureException
    */
   public static MessageDigestAlgorithm getInstance(
           Document doc, String algorithmURI) throws XMLSignatureException {

      String algorithmID = JCEMapper.translateURItoJCEID(algorithmURI);

          if (algorithmID == null) {
                  Object[] exArgs = { algorithmURI };
                  throw new XMLSignatureException("algorithms.NoSuchMap", exArgs);
          }

      MessageDigest md;
      String provider=JCEMapper.getProviderId();
      try {
         if (provider==null) {
                md = MessageDigest.getInstance(algorithmID);
         } else {
                md = MessageDigest.getInstance(algorithmID,provider);
         }
      } catch (java.security.NoSuchAlgorithmException ex) {
         Object[] exArgs = { algorithmID,
                             ex.getLocalizedMessage() };

         throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs);
      } catch (NoSuchProviderException ex) {
        Object[] exArgs = { algorithmID,
                                                ex.getLocalizedMessage() };

        throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs);
        }
      return new MessageDigestAlgorithm(doc, md, algorithmURI);
   }

   /**
    * Returns the actual {@link java.security.MessageDigest} algorithm object
    *
    * @return the actual {@link java.security.MessageDigest} algorithm object
    */
   public java.security.MessageDigest getAlgorithm() {
      return this.algorithm;
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#isEqual}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @param digesta
    * @param digestb
    * @return the result of the {@link java.security.MessageDigest#isEqual} method
    */
   public static boolean isEqual(byte[] digesta, byte[] digestb) {
      return java.security.MessageDigest.isEqual(digesta, digestb);
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#digest()}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @return the result of the {@link java.security.MessageDigest#digest()} method
    */
   public byte[] digest() {
      return this.algorithm.digest();
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#digest(byte[])}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @param input
    * @return the result of the {@link java.security.MessageDigest#digest(byte[])} method
    */
   public byte[] digest(byte input[]) {
      return this.algorithm.digest(input);
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#digest(byte[], int, int)}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @param buf
    * @param offset
    * @param len
    * @return the result of the {@link java.security.MessageDigest#digest(byte[], int, int)} method
    * @throws java.security.DigestException
    */
   public int digest(byte buf[], int offset, int len)
           throws java.security.DigestException {
      return this.algorithm.digest(buf, offset, len);
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#getAlgorithm}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @return the result of the {@link java.security.MessageDigest#getAlgorithm} method
    */
   public String getJCEAlgorithmString() {
      return this.algorithm.getAlgorithm();
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#getProvider}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @return the result of the {@link java.security.MessageDigest#getProvider} method
    */
   public java.security.Provider getJCEProvider() {
      return this.algorithm.getProvider();
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#getDigestLength}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @return the result of the {@link java.security.MessageDigest#getDigestLength} method
    */
   public int getDigestLength() {
      return this.algorithm.getDigestLength();
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#reset}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    */
   public void reset() {
      this.algorithm.reset();
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#update(byte[])}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @param input
    */
   public void update(byte[] input) {
      this.algorithm.update(input);
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#update(byte)}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @param input
    */
   public void update(byte input) {
      this.algorithm.update(input);
   }

   /**
    * Proxy method for {@link java.security.MessageDigest#update(byte[], int, int)}
    * which is executed on the internal {@link java.security.MessageDigest} object.
    *
    * @param buf
    * @param offset
    * @param len
    */
   public void update(byte buf[], int offset, int len) {
      this.algorithm.update(buf, offset, len);
   }

   /** @inheritDoc */
   public String getBaseNamespace() {
      return Constants.SignatureSpecNS;
   }

   /** @inheritDoc */
   public String getBaseLocalName() {
      return Constants._TAG_DIGESTMETHOD;
   }
}
