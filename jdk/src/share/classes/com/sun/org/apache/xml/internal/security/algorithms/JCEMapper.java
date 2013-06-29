/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.algorithms;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.org.apache.xml.internal.security.encryption.XMLCipher;
import com.sun.org.apache.xml.internal.security.signature.XMLSignature;
import org.w3c.dom.Element;


/**
 * This class maps algorithm identifier URIs to JAVA JCE class names.
 */
public class JCEMapper {

    /** {@link org.apache.commons.logging} logging facility */
    private static java.util.logging.Logger log =
        java.util.logging.Logger.getLogger(JCEMapper.class.getName());

    private static Map<String, Algorithm> algorithmsMap =
        new ConcurrentHashMap<String, Algorithm>();

    private static String providerName = null;

    /**
     * Method register
     *
     * @param id
     * @param algorithm
     */
    public static void register(String id, Algorithm algorithm) {
        algorithmsMap.put(id, algorithm);
    }

    /**
     * This method registers the default algorithms.
     */
    public static void registerDefaultAlgorithms() {
        algorithmsMap.put(
            MessageDigestAlgorithm.ALGO_ID_DIGEST_NOT_RECOMMENDED_MD5,
            new Algorithm("", "MD5", "MessageDigest")
        );
        algorithmsMap.put(
            MessageDigestAlgorithm.ALGO_ID_DIGEST_RIPEMD160,
            new Algorithm("", "RIPEMD160", "MessageDigest")
        );
        algorithmsMap.put(
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA1,
            new Algorithm("", "SHA-1", "MessageDigest")
        );
        algorithmsMap.put(
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256,
            new Algorithm("", "SHA-256", "MessageDigest")
        );
        algorithmsMap.put(
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA384,
            new Algorithm("", "SHA-384", "MessageDigest")
        );
        algorithmsMap.put(
            MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA512,
            new Algorithm("", "SHA-512", "MessageDigest")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_SIGNATURE_DSA,
            new Algorithm("", "SHA1withDSA", "Signature")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_SIGNATURE_NOT_RECOMMENDED_RSA_MD5,
            new Algorithm("", "MD5withRSA", "Signature")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_SIGNATURE_RSA_RIPEMD160,
            new Algorithm("", "RIPEMD160withRSA", "Signature")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1,
            new Algorithm("", "SHA1withRSA", "Signature")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
            new Algorithm("", "SHA256withRSA", "Signature")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA384,
            new Algorithm("", "SHA384withRSA", "Signature")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA512,
            new Algorithm("", "SHA512withRSA", "Signature")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_SIGNATURE_ECDSA_SHA1,
            new Algorithm("", "SHA1withECDSA", "Signature")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_MAC_HMAC_NOT_RECOMMENDED_MD5,
            new Algorithm("", "HmacMD5", "Mac")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_MAC_HMAC_RIPEMD160,
            new Algorithm("", "HMACRIPEMD160", "Mac")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_MAC_HMAC_SHA1,
            new Algorithm("", "HmacSHA1", "Mac")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_MAC_HMAC_SHA256,
            new Algorithm("", "HmacSHA256", "Mac")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_MAC_HMAC_SHA384,
            new Algorithm("", "HmacSHA384", "Mac")
        );
        algorithmsMap.put(
            XMLSignature.ALGO_ID_MAC_HMAC_SHA512,
            new Algorithm("", "HmacSHA512", "Mac")
        );
        algorithmsMap.put(
            XMLCipher.TRIPLEDES,
            new Algorithm("DESede", "DESede/CBC/ISO10126Padding", "BlockEncryption", 192)
        );
        algorithmsMap.put(
            XMLCipher.AES_128,
            new Algorithm("AES", "AES/CBC/ISO10126Padding", "BlockEncryption", 128)
        );
        algorithmsMap.put(
            XMLCipher.AES_192,
            new Algorithm("AES", "AES/CBC/ISO10126Padding", "BlockEncryption", 192)
        );
        algorithmsMap.put(
            XMLCipher.AES_256,
            new Algorithm("AES", "AES/CBC/ISO10126Padding", "BlockEncryption", 256)
        );
        algorithmsMap.put(
            XMLCipher.RSA_v1dot5,
            new Algorithm("RSA", "RSA/ECB/PKCS1Padding", "KeyTransport")
        );
        algorithmsMap.put(
            XMLCipher.RSA_OAEP,
            new Algorithm("RSA", "RSA/ECB/OAEPPadding", "KeyTransport")
        );
        algorithmsMap.put(
            XMLCipher.DIFFIE_HELLMAN,
            new Algorithm("", "", "KeyAgreement")
        );
        algorithmsMap.put(
            XMLCipher.TRIPLEDES_KeyWrap,
            new Algorithm("DESede", "DESedeWrap", "SymmetricKeyWrap", 192)
        );
        algorithmsMap.put(
            XMLCipher.AES_128_KeyWrap,
            new Algorithm("AES", "AESWrap", "SymmetricKeyWrap", 128)
        );
        algorithmsMap.put(
            XMLCipher.AES_192_KeyWrap,
            new Algorithm("AES", "AESWrap", "SymmetricKeyWrap", 192)
        );
        algorithmsMap.put(
            XMLCipher.AES_256_KeyWrap,
            new Algorithm("AES", "AESWrap", "SymmetricKeyWrap", 256)
        );
    }

    /**
     * Method translateURItoJCEID
     *
     * @param algorithmURI
     * @return the JCE standard name corresponding to the given URI
     */
    public static String translateURItoJCEID(String algorithmURI) {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Request for URI " + algorithmURI);
        }

        Algorithm algorithm = algorithmsMap.get(algorithmURI);
        if (algorithm != null) {
            return algorithm.jceName;
        }
        return null;
    }

    /**
     * Method getAlgorithmClassFromURI
     * @param algorithmURI
     * @return the class name that implements this algorithm
     */
    public static String getAlgorithmClassFromURI(String algorithmURI) {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Request for URI " + algorithmURI);
        }

        Algorithm algorithm = algorithmsMap.get(algorithmURI);
        if (algorithm != null) {
            return algorithm.algorithmClass;
        }
        return null;
    }

    /**
     * Returns the keylength in bits for a particular algorithm.
     *
     * @param algorithmURI
     * @return The length of the key used in the algorithm
     */
    public static int getKeyLengthFromURI(String algorithmURI) {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Request for URI " + algorithmURI);
        }
        Algorithm algorithm = algorithmsMap.get(algorithmURI);
        if (algorithm != null) {
            return algorithm.keyLength;
        }
        return 0;
    }

    /**
     * Method getJCEKeyAlgorithmFromURI
     *
     * @param algorithmURI
     * @return The KeyAlgorithm for the given URI.
     */
    public static String getJCEKeyAlgorithmFromURI(String algorithmURI) {
        if (log.isLoggable(java.util.logging.Level.FINE)) {
            log.log(java.util.logging.Level.FINE, "Request for URI " + algorithmURI);
        }
        Algorithm algorithm = algorithmsMap.get(algorithmURI);
        if (algorithm != null) {
            return algorithm.requiredKey;
        }
        return null;
    }

    /**
     * Gets the default Provider for obtaining the security algorithms
     * @return the default providerId.
     */
    public static String getProviderId() {
        return providerName;
    }

    /**
     * Sets the default Provider for obtaining the security algorithms
     * @param provider the default providerId.
     */
    public static void setProviderId(String provider) {
        providerName = provider;
    }

    /**
     * Represents the Algorithm xml element
     */
    public static class Algorithm {

        final String requiredKey;
        final String jceName;
        final String algorithmClass;
        final int keyLength;

        /**
         * Gets data from element
         * @param el
         */
        public Algorithm(Element el) {
            requiredKey = el.getAttribute("RequiredKey");
            jceName = el.getAttribute("JCEName");
            algorithmClass = el.getAttribute("AlgorithmClass");
            if (el.hasAttribute("KeyLength")) {
                keyLength = Integer.parseInt(el.getAttribute("KeyLength"));
            } else {
                keyLength = 0;
            }
        }

        public Algorithm(String requiredKey, String jceName) {
            this(requiredKey, jceName, null, 0);
        }

        public Algorithm(String requiredKey, String jceName, String algorithmClass) {
            this(requiredKey, jceName, algorithmClass, 0);
        }

        public Algorithm(String requiredKey, String jceName, int keyLength) {
            this(requiredKey, jceName, null, keyLength);
        }

        public Algorithm(String requiredKey, String jceName, String algorithmClass, int keyLength) {
            this.requiredKey = requiredKey;
            this.jceName = jceName;
            this.algorithmClass = algorithmClass;
            this.keyLength = keyLength;
        }
    }

}
