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
package com.sun.org.apache.xml.internal.security.algorithms.implementations;



import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;

import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;
import com.sun.org.apache.xml.internal.security.algorithms.SignatureAlgorithmSpi;
import com.sun.org.apache.xml.internal.security.signature.XMLSignature;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.Base64;


/**
 *
 * @author $Author: mullan $
 */
public abstract class SignatureECDSA extends SignatureAlgorithmSpi {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(SignatureECDSA.class.getName());

    /** @inheritDoc */
   public abstract String engineGetURI();

   /** Field algorithm */
   private java.security.Signature _signatureAlgorithm = null;

   /**
    * Converts an ASN.1 ECDSA value to a XML Signature ECDSA Value.
    *
    * The JAVA JCE ECDSA Signature algorithm creates ASN.1 encoded (r,s) value
    * pairs; the XML Signature requires the core BigInteger values.
    *
    * @param asn1Bytes
    * @return the decode bytes
    *
    * @throws IOException
    * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#dsa-sha1">6.4.1 DSA</A>
    * @see <A HREF="ftp://ftp.rfc-editor.org/in-notes/rfc4050.txt">3.3. ECDSA Signatures</A>
    */
   private static byte[] convertASN1toXMLDSIG(byte asn1Bytes[])
           throws IOException {

      byte rLength = asn1Bytes[3];
      int i;

      for (i = rLength; (i > 0) && (asn1Bytes[(4 + rLength) - i] == 0); i--);

      byte sLength = asn1Bytes[5 + rLength];
      int j;

      for (j = sLength;
              (j > 0) && (asn1Bytes[(6 + rLength + sLength) - j] == 0); j--);

      if ((asn1Bytes[0] != 48) || (asn1Bytes[1] != asn1Bytes.length - 2)
              || (asn1Bytes[2] != 2) || (i > 24)
              || (asn1Bytes[4 + rLength] != 2) || (j > 24)) {
         throw new IOException("Invalid ASN.1 format of ECDSA signature");
      }
      byte xmldsigBytes[] = new byte[48];

      System.arraycopy(asn1Bytes, (4 + rLength) - i, xmldsigBytes, 24 - i,
                          i);
      System.arraycopy(asn1Bytes, (6 + rLength + sLength) - j, xmldsigBytes,
                          48 - j, j);

       return xmldsigBytes;
   }

   /**
    * Converts a XML Signature ECDSA Value to an ASN.1 DSA value.
    *
    * The JAVA JCE ECDSA Signature algorithm creates ASN.1 encoded (r,s) value
    * pairs; the XML Signature requires the core BigInteger values.
    *
    * @param xmldsigBytes
    * @return the encoded ASN.1 bytes
    *
    * @throws IOException
    * @see <A HREF="http://www.w3.org/TR/xmldsig-core/#dsa-sha1">6.4.1 DSA</A>
    * @see <A HREF="ftp://ftp.rfc-editor.org/in-notes/rfc4050.txt">3.3. ECDSA Signatures</A>
    */
   private static byte[] convertXMLDSIGtoASN1(byte xmldsigBytes[])
           throws IOException {

      if (xmldsigBytes.length != 48) {
         throw new IOException("Invalid XMLDSIG format of ECDSA signature");
      }

      int i;

      for (i = 24; (i > 0) && (xmldsigBytes[24 - i] == 0); i--);

      int j = i;

      if (xmldsigBytes[24 - i] < 0) {
         j += 1;
      }

      int k;

      for (k = 24; (k > 0) && (xmldsigBytes[48 - k] == 0); k--);

      int l = k;

      if (xmldsigBytes[48 - k] < 0) {
         l += 1;
      }

      byte asn1Bytes[] = new byte[6 + j + l];

      asn1Bytes[0] = 48;
      asn1Bytes[1] = (byte) (4 + j + l);
      asn1Bytes[2] = 2;
      asn1Bytes[3] = (byte) j;

      System.arraycopy(xmldsigBytes, 24 - i, asn1Bytes, (4 + j) - i, i);

      asn1Bytes[4 + j] = 2;
      asn1Bytes[5 + j] = (byte) l;

      System.arraycopy(xmldsigBytes, 48 - k, asn1Bytes, (6 + j + l) - k, k);

      return asn1Bytes;
   }

   /**
    * Constructor SignatureRSA
    *
    * @throws XMLSignatureException
    */
   public SignatureECDSA() throws XMLSignatureException {

      String algorithmID = JCEMapper.translateURItoJCEID(this.engineGetURI());

      if (log.isLoggable(java.util.logging.Level.FINE))
        log.log(java.util.logging.Level.FINE, "Created SignatureECDSA using " + algorithmID);
      String provider=JCEMapper.getProviderId();
      try {
         if (provider==null) {
                this._signatureAlgorithm = Signature.getInstance(algorithmID);
         } else {
                this._signatureAlgorithm = Signature.getInstance(algorithmID,provider);
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
   }

   /** @inheritDoc */
   protected void engineSetParameter(AlgorithmParameterSpec params)
           throws XMLSignatureException {

      try {
         this._signatureAlgorithm.setParameter(params);
      } catch (InvalidAlgorithmParameterException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected boolean engineVerify(byte[] signature)
           throws XMLSignatureException {

      try {
         byte[] jcebytes = SignatureECDSA.convertXMLDSIGtoASN1(signature);

         if (log.isLoggable(java.util.logging.Level.FINE))
            log.log(java.util.logging.Level.FINE, "Called ECDSA.verify() on " + Base64.encode(signature));

         return this._signatureAlgorithm.verify(jcebytes);
      } catch (SignatureException ex) {
         throw new XMLSignatureException("empty", ex);
      } catch (IOException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected void engineInitVerify(Key publicKey) throws XMLSignatureException {

      if (!(publicKey instanceof PublicKey)) {
         String supplied = publicKey.getClass().getName();
         String needed = PublicKey.class.getName();
         Object exArgs[] = { supplied, needed };

         throw new XMLSignatureException("algorithms.WrongKeyForThisOperation",
                                         exArgs);
      }

      try {
         this._signatureAlgorithm.initVerify((PublicKey) publicKey);
      } catch (InvalidKeyException ex) {
            // reinstantiate Signature object to work around bug in JDK
            // see: http://bugs.sun.com/view_bug.do?bug_id=4953555
            Signature sig = this._signatureAlgorithm;
            try {
                this._signatureAlgorithm = Signature.getInstance
                    (_signatureAlgorithm.getAlgorithm());
            } catch (Exception e) {
                // this shouldn't occur, but if it does, restore previous
                // Signature
                if (log.isLoggable(java.util.logging.Level.FINE)) {
                    log.log(java.util.logging.Level.FINE, "Exception when reinstantiating Signature:" + e);
                }
                this._signatureAlgorithm = sig;
            }
            throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected byte[] engineSign() throws XMLSignatureException {

      try {
         byte jcebytes[] = this._signatureAlgorithm.sign();

         return SignatureECDSA.convertASN1toXMLDSIG(jcebytes);
      } catch (SignatureException ex) {
         throw new XMLSignatureException("empty", ex);
      } catch (IOException ex) {
          throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected void engineInitSign(Key privateKey, SecureRandom secureRandom)
           throws XMLSignatureException {

      if (!(privateKey instanceof PrivateKey)) {
         String supplied = privateKey.getClass().getName();
         String needed = PrivateKey.class.getName();
         Object exArgs[] = { supplied, needed };

         throw new XMLSignatureException("algorithms.WrongKeyForThisOperation",
                                         exArgs);
      }

      try {
         this._signatureAlgorithm.initSign((PrivateKey) privateKey,
                                           secureRandom);
      } catch (InvalidKeyException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected void engineInitSign(Key privateKey) throws XMLSignatureException {

      if (!(privateKey instanceof PrivateKey)) {
         String supplied = privateKey.getClass().getName();
         String needed = PrivateKey.class.getName();
         Object exArgs[] = { supplied, needed };

         throw new XMLSignatureException("algorithms.WrongKeyForThisOperation",
                                         exArgs);
      }

      try {
         this._signatureAlgorithm.initSign((PrivateKey) privateKey);
      } catch (InvalidKeyException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected void engineUpdate(byte[] input) throws XMLSignatureException {

      try {
         this._signatureAlgorithm.update(input);
      } catch (SignatureException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected void engineUpdate(byte input) throws XMLSignatureException {

      try {
         this._signatureAlgorithm.update(input);
      } catch (SignatureException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected void engineUpdate(byte buf[], int offset, int len)
           throws XMLSignatureException {

      try {
         this._signatureAlgorithm.update(buf, offset, len);
      } catch (SignatureException ex) {
         throw new XMLSignatureException("empty", ex);
      }
   }

   /** @inheritDoc */
   protected String engineGetJCEAlgorithmString() {
      return this._signatureAlgorithm.getAlgorithm();
   }

   /** @inheritDoc */
   protected String engineGetJCEProviderName() {
      return this._signatureAlgorithm.getProvider().getName();
   }

   /** @inheritDoc */
   protected void engineSetHMACOutputLength(int HMACOutputLength)
           throws XMLSignatureException {
      throw new XMLSignatureException("algorithms.HMACOutputLengthOnlyForHMAC");
   }

   /** @inheritDoc */
   protected void engineInitSign(
           Key signingKey, AlgorithmParameterSpec algorithmParameterSpec)
              throws XMLSignatureException {
      throw new XMLSignatureException(
         "algorithms.CannotUseAlgorithmParameterSpecOnRSA");
   }

   /**
    * Class SignatureRSASHA1
    *
    * @author $Author: mullan $
    * @version $Revision: 1.2 $
    */
   public static class SignatureECDSASHA1 extends SignatureECDSA {

      /**
       * Constructor SignatureRSASHA1
       *
       * @throws XMLSignatureException
       */
      public SignatureECDSASHA1() throws XMLSignatureException {
         super();
      }

      /** @inheritDoc */
      public String engineGetURI() {
         return XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA1;
      }
   }

}
