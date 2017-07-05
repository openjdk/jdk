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

import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import org.w3c.dom.Element;


/**
 *
 * @author $Author: mullan $
 */
public abstract class SignatureAlgorithmSpi {

   /**
    * Returns the URI representation of <code>Transformation algorithm</code>
    *
    * @return the URI representation of <code>Transformation algorithm</code>
    */
   protected abstract String engineGetURI();

   /**
    * Proxy method for {@link java.security.Signature#getAlgorithm}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @return the result of the {@link java.security.Signature#getAlgorithm} method
    */
   protected abstract String engineGetJCEAlgorithmString();

   /**
    * Method engineGetJCEProviderName
    *
    * @return the JCE ProviderName
    */
   protected abstract String engineGetJCEProviderName();

   /**
    * Proxy method for {@link java.security.Signature#update(byte[])}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param input
    * @throws XMLSignatureException
    */
   protected abstract void engineUpdate(byte[] input)
      throws XMLSignatureException;

   /**
    * Proxy method for {@link java.security.Signature#update(byte[])}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param input
    * @throws XMLSignatureException
    */
   protected abstract void engineUpdate(byte input)
      throws XMLSignatureException;

   /**
    * Proxy method for {@link java.security.Signature#update(byte[], int, int)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param buf
    * @param offset
    * @param len
    * @throws XMLSignatureException
    */
   protected abstract void engineUpdate(byte buf[], int offset, int len)
      throws XMLSignatureException;

   /**
    * Proxy method for {@link java.security.Signature#initSign(java.security.PrivateKey)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param signingKey
    * @throws XMLSignatureException if this method is called on a MAC
    */
   protected abstract void engineInitSign(Key signingKey)
      throws XMLSignatureException;

   /**
    * Proxy method for {@link java.security.Signature#initSign(java.security.PrivateKey, java.security.SecureRandom)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param signingKey
    * @param secureRandom
    * @throws XMLSignatureException if this method is called on a MAC
    */
   protected abstract void engineInitSign(
      Key signingKey, SecureRandom secureRandom) throws XMLSignatureException;

   /**
    * Proxy method for {@link javax.crypto.Mac}
    * which is executed on the internal {@link javax.crypto.Mac#init(Key)} object.
    *
    * @param signingKey
    * @param algorithmParameterSpec
    * @throws XMLSignatureException if this method is called on a Signature
    */
   protected abstract void engineInitSign(
      Key signingKey, AlgorithmParameterSpec algorithmParameterSpec)
         throws XMLSignatureException;

   /**
    * Proxy method for {@link java.security.Signature#sign()}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @return the result of the {@link java.security.Signature#sign()} method
    * @throws XMLSignatureException
    */
   protected abstract byte[] engineSign() throws XMLSignatureException;

   /**
    * Method engineInitVerify
    *
    * @param verificationKey
    * @throws XMLSignatureException
    */
   protected abstract void engineInitVerify(Key verificationKey)
      throws XMLSignatureException;

   /**
    * Proxy method for {@link java.security.Signature#verify(byte[])}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param signature
    * @return true if the signature is correct
    * @throws XMLSignatureException
    */
   protected abstract boolean engineVerify(byte[] signature)
      throws XMLSignatureException;

   /**
    * Proxy method for {@link java.security.Signature#setParameter(java.security.spec.AlgorithmParameterSpec)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param params
    * @throws XMLSignatureException
    */
   protected abstract void engineSetParameter(AlgorithmParameterSpec params)
      throws XMLSignatureException;


   /**
    * Method engineGetContextFromElement
    *
    * @param element
    */
   protected void engineGetContextFromElement(Element element) {
   }

   /**
    * Method engineSetHMACOutputLength
    *
    * @param HMACOutputLength
    * @throws XMLSignatureException
    */
   protected abstract void engineSetHMACOutputLength(int HMACOutputLength)
      throws XMLSignatureException;

    public void reset() {
        }
}
