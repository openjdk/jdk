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
import java.util.HashMap;
import java.util.Map;

import com.sun.org.apache.xml.internal.security.algorithms.implementations.IntegrityHmac;
import com.sun.org.apache.xml.internal.security.exceptions.AlgorithmAlreadyRegisteredException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


/**
 * Allows selection of digital signature's algorithm, private keys, other security parameters, and algorithm's ID.
 *
 * @author Christian Geuer-Pollmann
 */
public class SignatureAlgorithm extends Algorithm {

   /** {@link java.util.logging} logging facility */
    static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(SignatureAlgorithm.class.getName());

   /** Field _alreadyInitialized */
   static boolean _alreadyInitialized = false;

   /** All available algorithm classes are registered here */
   static HashMap _algorithmHash = null;

   static ThreadLocal instancesSigning=new ThreadLocal() {
           protected Object initialValue() {
                   return new HashMap();
           };
   };

   static ThreadLocal instancesVerify=new ThreadLocal() {
           protected Object initialValue() {
                   return new HashMap();
           };
   };

   static ThreadLocal keysSigning=new ThreadLocal() {
           protected Object initialValue() {
                   return new HashMap();
           };
   };
   static ThreadLocal keysVerify=new ThreadLocal() {
           protected Object initialValue() {
                   return new HashMap();
           };
   };
//   boolean isForSigning=false;

   /** Field _signatureAlgorithm */
   protected SignatureAlgorithmSpi _signatureAlgorithm = null;

   private String algorithmURI;

   /**
    * Constructor SignatureAlgorithm
    *
    * @param doc
    * @param algorithmURI
    * @throws XMLSecurityException
    */
   public SignatureAlgorithm(Document doc, String algorithmURI)
           throws XMLSecurityException {
      super(doc, algorithmURI);
      this.algorithmURI = algorithmURI;
   }


   private void initializeAlgorithm(boolean isForSigning) throws XMLSignatureException {
           if (_signatureAlgorithm!=null) {
                   return;
           }
           _signatureAlgorithm=isForSigning ? getInstanceForSigning(algorithmURI) : getInstanceForVerify(algorithmURI);
                this._signatureAlgorithm
                      .engineGetContextFromElement(this._constructionElement);
   }
   private static SignatureAlgorithmSpi getInstanceForSigning(String algorithmURI) throws XMLSignatureException {
           SignatureAlgorithmSpi result=(SignatureAlgorithmSpi) ((Map)instancesSigning.get()).get(algorithmURI);
           if (result!=null) {
                   result.reset();
                   return result;
           }
           result=buildSigner(algorithmURI, result);
           ((Map)instancesSigning.get()).put(algorithmURI,result);
           return result;
   }
   private static SignatureAlgorithmSpi getInstanceForVerify(String algorithmURI) throws XMLSignatureException {
           SignatureAlgorithmSpi result=(SignatureAlgorithmSpi) ((Map)instancesVerify.get()).get(algorithmURI);
           if (result!=null) {
                   result.reset();
                   return result;
           }
           result=buildSigner(algorithmURI, result);
           ((Map)instancesVerify.get()).put(algorithmURI,result);
           return result;
   }

   private static SignatureAlgorithmSpi buildSigner(String algorithmURI, SignatureAlgorithmSpi result) throws XMLSignatureException {
        try {
         Class implementingClass =
            SignatureAlgorithm.getImplementingClass(algorithmURI);
         if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "Create URI \"" + algorithmURI + "\" class \""
                   + implementingClass + "\"");
         result=(SignatureAlgorithmSpi) implementingClass.newInstance();
         return   result;
      }  catch (IllegalAccessException ex) {
         Object exArgs[] = { algorithmURI, ex.getMessage() };

         throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs,
                                         ex);
      } catch (InstantiationException ex) {
         Object exArgs[] = { algorithmURI, ex.getMessage() };

         throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs,
                                         ex);
      } catch (NullPointerException ex) {
         Object exArgs[] = { algorithmURI, ex.getMessage() };

         throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs,
                                         ex);
      }
}

   /**
    * Constructor SignatureAlgorithm
    *
    * @param doc
    * @param algorithmURI
    * @param HMACOutputLength
    * @throws XMLSecurityException
    */
   public SignatureAlgorithm(
           Document doc, String algorithmURI, int HMACOutputLength)
              throws XMLSecurityException {

      this(doc, algorithmURI);
      this.algorithmURI=algorithmURI;
      initializeAlgorithm(true);
      this._signatureAlgorithm.engineSetHMACOutputLength(HMACOutputLength);
      ((IntegrityHmac)this._signatureAlgorithm)
         .engineAddContextToElement(this._constructionElement);
   }

   /**
    * Constructor SignatureAlgorithm
    *
    * @param element
    * @param BaseURI
    * @throws XMLSecurityException
    */
   public SignatureAlgorithm(Element element, String BaseURI)
           throws XMLSecurityException {

      super(element, BaseURI);
      algorithmURI = this.getURI();
   }

   /**
    * Proxy method for {@link java.security.Signature#sign()}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @return the result of the {@link java.security.Signature#sign()} method
    * @throws XMLSignatureException
    */
   public byte[] sign() throws XMLSignatureException {
      return this._signatureAlgorithm.engineSign();
   }

   /**
    * Proxy method for {@link java.security.Signature#getAlgorithm}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @return the result of the {@link java.security.Signature#getAlgorithm} method
    */
   public String getJCEAlgorithmString() {
      try {
                return getInstanceForVerify(algorithmURI).engineGetJCEAlgorithmString();
        } catch (XMLSignatureException e) {
                //Ignore.
                return null;
        }
   }

   /**
    * Method getJCEProviderName
    *
    * @return The Provider of this Signature Alogrithm
    */
   public String getJCEProviderName() {
      try {
                return getInstanceForVerify(algorithmURI).engineGetJCEProviderName();
        } catch (XMLSignatureException e) {
                return null;
        }
   }

   /**
    * Proxy method for {@link java.security.Signature#update(byte[])}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param input
    * @throws XMLSignatureException
    */
   public void update(byte[] input) throws XMLSignatureException {
      this._signatureAlgorithm.engineUpdate(input);
   }

   /**
    * Proxy method for {@link java.security.Signature#update(byte)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param input
    * @throws XMLSignatureException
    */
   public void update(byte input) throws XMLSignatureException {
      this._signatureAlgorithm.engineUpdate(input);
   }

   /**
    * Proxy method for {@link java.security.Signature#update(byte[], int, int)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param buf
    * @param offset
    * @param len
    * @throws XMLSignatureException
    */
   public void update(byte buf[], int offset, int len)
           throws XMLSignatureException {
      this._signatureAlgorithm.engineUpdate(buf, offset, len);
   }

   /**
    * Proxy method for {@link java.security.Signature#initSign(java.security.PrivateKey)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param signingKey
    * @throws XMLSignatureException
    */
   public void initSign(Key signingKey) throws XMLSignatureException {
           initializeAlgorithm(true);
           Map map=(Map)keysSigning.get();
       if (map.get(this.algorithmURI)==signingKey) {
           return;
       }
       map.put(this.algorithmURI,signingKey);
           this._signatureAlgorithm.engineInitSign(signingKey);
   }

   /**
    * Proxy method for {@link java.security.Signature#initSign(java.security.PrivateKey, java.security.SecureRandom)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param signingKey
    * @param secureRandom
    * @throws XMLSignatureException
    */
   public void initSign(Key signingKey, SecureRandom secureRandom)
           throws XMLSignatureException {
           initializeAlgorithm(true);
      this._signatureAlgorithm.engineInitSign(signingKey, secureRandom);
   }

   /**
    * Proxy method for {@link java.security.Signature#initSign(java.security.PrivateKey)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param signingKey
    * @param algorithmParameterSpec
    * @throws XMLSignatureException
    */
   public void initSign(
           Key signingKey, AlgorithmParameterSpec algorithmParameterSpec)
              throws XMLSignatureException {
           initializeAlgorithm(true);
      this._signatureAlgorithm.engineInitSign(signingKey,
                                              algorithmParameterSpec);
   }

   /**
    * Proxy method for {@link java.security.Signature#setParameter(java.security.spec.AlgorithmParameterSpec)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param params
    * @throws XMLSignatureException
    */
   public void setParameter(AlgorithmParameterSpec params)
           throws XMLSignatureException {
      this._signatureAlgorithm.engineSetParameter(params);
   }

   /**
    * Proxy method for {@link java.security.Signature#initVerify(java.security.PublicKey)}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param verificationKey
    * @throws XMLSignatureException
    */
   public void initVerify(Key verificationKey) throws XMLSignatureException {
           initializeAlgorithm(false);
           Map map=(Map)keysVerify.get();
           if (map.get(this.algorithmURI)==verificationKey) {
           return;
       }
           map.put(this.algorithmURI,verificationKey);
           this._signatureAlgorithm.engineInitVerify(verificationKey);
   }

   /**
    * Proxy method for {@link java.security.Signature#verify(byte[])}
    * which is executed on the internal {@link java.security.Signature} object.
    *
    * @param signature
    * @return true if if the signature is valid.
    *
    * @throws XMLSignatureException
    */
   public boolean verify(byte[] signature) throws XMLSignatureException {
      return this._signatureAlgorithm.engineVerify(signature);
   }

   /**
    * Returns the URI representation of Transformation algorithm
    *
    * @return the URI representation of Transformation algorithm
    */
   public final String getURI() {
      return this._constructionElement.getAttributeNS(null,
              Constants._ATT_ALGORITHM);
   }

   /**
    * Initalizes for this {@link com.sun.org.apache.xml.internal.security.transforms.Transform}
    *
    */
   public static void providerInit() {

      if (SignatureAlgorithm.log == null) {
         SignatureAlgorithm.log =
            java.util.logging.Logger
               .getLogger(SignatureAlgorithm.class.getName());
      }

      log.log(java.util.logging.Level.FINE, "Init() called");

      if (!SignatureAlgorithm._alreadyInitialized) {
         SignatureAlgorithm._algorithmHash = new HashMap(10);
         SignatureAlgorithm._alreadyInitialized = true;
      }
   }

   /**
    * Registers implementing class of the Transform algorithm with algorithmURI
    *
    * @param algorithmURI algorithmURI URI representation of <code>Transform algorithm</code>.
    * @param implementingClass <code>implementingClass</code> the implementing class of {@link SignatureAlgorithmSpi}
    * @throws AlgorithmAlreadyRegisteredException if specified algorithmURI is already registered
    * @throws XMLSignatureException
    */
   public static void register(String algorithmURI, String implementingClass)
           throws AlgorithmAlreadyRegisteredException,XMLSignatureException {

      {
         if (log.isLoggable(java.util.logging.Level.FINE))
                log.log(java.util.logging.Level.FINE, "Try to register " + algorithmURI + " " + implementingClass);

         // are we already registered?
         Class registeredClassClass =
            SignatureAlgorithm.getImplementingClass(algorithmURI);
                 if (registeredClassClass!=null) {
                         String registeredClass = registeredClassClass.getName();

                         if ((registeredClass != null) && (registeredClass.length() != 0)) {
                                 Object exArgs[] = { algorithmURI, registeredClass };

                                 throw new AlgorithmAlreadyRegisteredException(
                                                 "algorithm.alreadyRegistered", exArgs);
                         }
                 }
                 try {
                         SignatureAlgorithm._algorithmHash.put(algorithmURI, Class.forName(implementingClass));
              } catch (ClassNotFoundException ex) {
                 Object exArgs[] = { algorithmURI, ex.getMessage() };

                 throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs,
                                                 ex);
              } catch (NullPointerException ex) {
                 Object exArgs[] = { algorithmURI, ex.getMessage() };

                 throw new XMLSignatureException("algorithms.NoSuchAlgorithm", exArgs,
                                                 ex);
              }

      }
   }

   /**
    * Method getImplementingClass
    *
    * @param URI
    * @return the class that implements the URI
    */
   private static Class getImplementingClass(String URI) {

      if (SignatureAlgorithm._algorithmHash == null) {
         return null;
      }

      return (Class) SignatureAlgorithm._algorithmHash.get(URI);
   }

   /**
    * Method getBaseNamespace
    *
    * @return URI of this element
    */
   public String getBaseNamespace() {
      return Constants.SignatureSpecNS;
   }

   /**
    * Method getBaseLocalName
    *
    * @return Local name
    */
   public String getBaseLocalName() {
      return Constants._TAG_SIGNATUREMETHOD;
   }
}
