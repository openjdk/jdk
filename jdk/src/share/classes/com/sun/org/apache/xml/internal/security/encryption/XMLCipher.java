/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  2003-2004 The Apache Software Foundation.
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
package com.sun.org.apache.xml.internal.security.encryption;


import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.org.apache.xml.internal.security.algorithms.JCEMapper;
import com.sun.org.apache.xml.internal.security.algorithms.MessageDigestAlgorithm;
import com.sun.org.apache.xml.internal.security.c14n.Canonicalizer;
import com.sun.org.apache.xml.internal.security.c14n.InvalidCanonicalizerException;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.KeyInfo;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.KeyResolverException;
import com.sun.org.apache.xml.internal.security.keys.keyresolver.implementations.EncryptedKeyResolver;
import com.sun.org.apache.xml.internal.security.signature.XMLSignatureException;
import com.sun.org.apache.xml.internal.security.transforms.InvalidTransformException;
import com.sun.org.apache.xml.internal.security.transforms.TransformationException;
import com.sun.org.apache.xml.internal.security.utils.Base64;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.ElementProxy;
import com.sun.org.apache.xml.internal.security.utils.EncryptionConstants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.utils.URI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


/**
 * <code>XMLCipher</code> encrypts and decrypts the contents of
 * <code>Document</code>s, <code>Element</code>s and <code>Element</code>
 * contents. It was designed to resemble <code>javax.crypto.Cipher</code> in
 * order to facilitate understanding of its functioning.
 *
 * @author Axl Mattheus (Sun Microsystems)
 * @author Christian Geuer-Pollmann
 */
public class XMLCipher {

    private static java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger(XMLCipher.class.getName());

        //J-
        /** Triple DES EDE (192 bit key) in CBC mode */
    public static final String TRIPLEDES =
        EncryptionConstants.ALGO_ID_BLOCKCIPHER_TRIPLEDES;
    /** AES 128 Cipher */
    public static final String AES_128 =
        EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES128;
    /** AES 256 Cipher */
    public static final String AES_256 =
        EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES256;
    /** AES 192 Cipher */
    public static final String AES_192 =
        EncryptionConstants.ALGO_ID_BLOCKCIPHER_AES192;
    /** RSA 1.5 Cipher */
    public static final String RSA_v1dot5 =
        EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSA15;
    /** RSA OAEP Cipher */
    public static final String RSA_OAEP =
        EncryptionConstants.ALGO_ID_KEYTRANSPORT_RSAOAEP;
    /** DIFFIE_HELLMAN Cipher */
    public static final String DIFFIE_HELLMAN =
        EncryptionConstants.ALGO_ID_KEYAGREEMENT_DH;
    /** Triple DES EDE (192 bit key) in CBC mode KEYWRAP*/
    public static final String TRIPLEDES_KeyWrap =
        EncryptionConstants.ALGO_ID_KEYWRAP_TRIPLEDES;
    /** AES 128 Cipher KeyWrap */
    public static final String AES_128_KeyWrap =
        EncryptionConstants.ALGO_ID_KEYWRAP_AES128;
    /** AES 256 Cipher KeyWrap */
    public static final String AES_256_KeyWrap =
        EncryptionConstants.ALGO_ID_KEYWRAP_AES256;
    /** AES 192 Cipher KeyWrap */
    public static final String AES_192_KeyWrap =
        EncryptionConstants.ALGO_ID_KEYWRAP_AES192;
    /** SHA1 Cipher */
    public static final String SHA1 =
        Constants.ALGO_ID_DIGEST_SHA1;
    /** SHA256 Cipher */
    public static final String SHA256 =
        MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256;
    /** SHA512 Cipher */
    public static final String SHA512 =
        MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA512;
    /** RIPEMD Cipher */
    public static final String RIPEMD_160 =
        MessageDigestAlgorithm.ALGO_ID_DIGEST_RIPEMD160;
    /** XML Signature NS */
    public static final String XML_DSIG =
        Constants.SignatureSpecNS;
    /** N14C_XML */
    public static final String N14C_XML =
        Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS;
    /** N14C_XML with comments*/
    public static final String N14C_XML_WITH_COMMENTS =
        Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS;
    /** N14C_XML excluisve */
    public static final String EXCL_XML_N14C =
        Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS;
    /** N14C_XML exclusive with commetns*/
    public static final String EXCL_XML_N14C_WITH_COMMENTS =
        Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS;
    /** Base64 encoding */
    public static final String BASE64_ENCODING =
        com.sun.org.apache.xml.internal.security.transforms.Transforms.TRANSFORM_BASE64_DECODE;
        //J+

    /** ENCRYPT Mode */
    public static final int ENCRYPT_MODE = Cipher.ENCRYPT_MODE;
    /** DECRYPT Mode */
    public static final int DECRYPT_MODE = Cipher.DECRYPT_MODE;
    /** UNWRAP Mode */
    public static final int UNWRAP_MODE  = Cipher.UNWRAP_MODE;
    /** WRAP Mode */
    public static final int WRAP_MODE    = Cipher.WRAP_MODE;

    private static final String ENC_ALGORITHMS = TRIPLEDES + "\n" +
        AES_128 + "\n" + AES_256 + "\n" + AES_192 + "\n" + RSA_v1dot5 + "\n" +
        RSA_OAEP + "\n" + TRIPLEDES_KeyWrap + "\n" + AES_128_KeyWrap + "\n" +
        AES_256_KeyWrap + "\n" + AES_192_KeyWrap+ "\n";

        /** Cipher created during initialisation that is used for encryption */
    private Cipher _contextCipher;
        /** Mode that the XMLCipher object is operating in */
    private int _cipherMode = Integer.MIN_VALUE;
        /** URI of algorithm that is being used for cryptographic operation */
    private String _algorithm = null;
        /** Cryptographic provider requested by caller */
        private String _requestedJCEProvider = null;
        /** Holds c14n to serialize, if initialized then _always_ use this c14n to serialize */
        private Canonicalizer _canon;
        /** Used for creation of DOM nodes in WRAP and ENCRYPT modes */
    private Document _contextDocument;
        /** Instance of factory used to create XML Encryption objects */
    private Factory _factory;
        /** Internal serializer class for going to/from UTF-8 */
    private Serializer _serializer;

        /** Local copy of user's key */
        private Key _key;
        /** Local copy of the kek (used to decrypt EncryptedKeys during a
     *  DECRYPT_MODE operation */
        private Key _kek;

        // The EncryptedKey being built (part of a WRAP operation) or read
        // (part of an UNWRAP operation)

        private EncryptedKey _ek;

        // The EncryptedData being built (part of a WRAP operation) or read
        // (part of an UNWRAP operation)

        private EncryptedData _ed;

    /**
     * Creates a new <code>XMLCipher</code>.
     *
     * @since 1.0.
     */
    private XMLCipher() {
        logger.log(java.util.logging.Level.FINE, "Constructing XMLCipher...");

        _factory = new Factory();
        _serializer = new Serializer();

    }

    /**
     * Checks to ensure that the supplied algorithm is valid.
     *
     * @param algorithm the algorithm to check.
     * @return true if the algorithm is valid, otherwise false.
     * @since 1.0.
     */
    private static boolean isValidEncryptionAlgorithm(String algorithm) {
        boolean result = (
            algorithm.equals(TRIPLEDES) ||
            algorithm.equals(AES_128) ||
            algorithm.equals(AES_256) ||
            algorithm.equals(AES_192) ||
            algorithm.equals(RSA_v1dot5) ||
            algorithm.equals(RSA_OAEP) ||
            algorithm.equals(TRIPLEDES_KeyWrap) ||
            algorithm.equals(AES_128_KeyWrap) ||
            algorithm.equals(AES_256_KeyWrap) ||
            algorithm.equals(AES_192_KeyWrap)
        );

        return (result);
    }

    /**
     * Returns an <code>XMLCipher</code> that implements the specified
     * transformation and operates on the specified context document.
     * <p>
     * If the default provider package supplies an implementation of the
     * requested transformation, an instance of Cipher containing that
     * implementation is returned. If the transformation is not available in
     * the default provider package, other provider packages are searched.
     * <p>
     * <b>NOTE<sub>1</sub>:</b> The transformation name does not follow the same
     * pattern as that oulined in the Java Cryptography Extension Reference
     * Guide but rather that specified by the XML Encryption Syntax and
     * Processing document. The rational behind this is to make it easier for a
     * novice at writing Java Encryption software to use the library.
     * <p>
     * <b>NOTE<sub>2</sub>:</b> <code>getInstance()</code> does not follow the
     * same pattern regarding exceptional conditions as that used in
     * <code>javax.crypto.Cipher</code>. Instead, it only throws an
     * <code>XMLEncryptionException</code> which wraps an underlying exception.
     * The stack trace from the exception should be self explanitory.
     *
     * @param transformation the name of the transformation, e.g.,
     *   <code>XMLCipher.TRIPLEDES</code> which is shorthand for
     *   &quot;http://www.w3.org/2001/04/xmlenc#tripledes-cbc&quot;
     * @throws XMLEncryptionException
     * @return the XMLCipher
     * @see javax.crypto.Cipher#getInstance(java.lang.String)
     */
    public static XMLCipher getInstance(String transformation) throws
            XMLEncryptionException {
        // sanity checks
        logger.log(java.util.logging.Level.FINE, "Getting XMLCipher...");
        if (null == transformation)
            logger.log(java.util.logging.Level.SEVERE, "Transformation unexpectedly null...");
        if(!isValidEncryptionAlgorithm(transformation))
            logger.log(java.util.logging.Level.WARNING, "Algorithm non-standard, expected one of " + ENC_ALGORITHMS);

                XMLCipher instance = new XMLCipher();

        instance._algorithm = transformation;
                instance._key = null;
                instance._kek = null;


                /* Create a canonicaliser - used when serialising DOM to octets
                 * prior to encryption (and for the reverse) */

                try {
                        instance._canon = Canonicalizer.getInstance
                                (Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS);

                } catch (InvalidCanonicalizerException ice) {
                        throw new XMLEncryptionException("empty", ice);
                }

                String jceAlgorithm = JCEMapper.translateURItoJCEID(transformation);

                try {
            instance._contextCipher = Cipher.getInstance(jceAlgorithm);
            logger.log(java.util.logging.Level.FINE, "cihper.algoritm = " +
                instance._contextCipher.getAlgorithm());
        } catch (NoSuchAlgorithmException nsae) {
            throw new XMLEncryptionException("empty", nsae);
        } catch (NoSuchPaddingException nspe) {
            throw new XMLEncryptionException("empty", nspe);
        }

        return (instance);
    }

        /**
         * Returns an <code>XMLCipher</code> that implements the specified
         * transformation, operates on the specified context document and serializes
         * the document with the specified canonicalization algorithm before it
         * encrypts the document.
         * <p>
         *
         * @param transformation        the name of the transformation, e.g.,
         *                                              <code>XMLCipher.TRIPLEDES</code> which is
         *                                                      shorthand for
         *                              &quot;http://www.w3.org/2001/04/xmlenc#tripledes-cbc&quot;
         * @param canon                         the name of the c14n algorithm, if
         *                                                      <code>null</code> use standard serializer
         * @return
         * @throws XMLEncryptionException
         */

        public static XMLCipher getInstance(String transformation, String canon)
                throws XMLEncryptionException {
                XMLCipher instance = XMLCipher.getInstance(transformation);

                if (canon != null) {
                        try {
                                instance._canon = Canonicalizer.getInstance(canon);
                        } catch (InvalidCanonicalizerException ice) {
                                throw new XMLEncryptionException("empty", ice);
                        }
                }

                return instance;
        }

    public static XMLCipher getInstance(String transformation,Cipher cipher) throws XMLEncryptionException {
        // sanity checks
        logger.log(java.util.logging.Level.FINE, "Getting XMLCipher...");
        if (null == transformation)
            logger.log(java.util.logging.Level.SEVERE, "Transformation unexpectedly null...");
        if(!isValidEncryptionAlgorithm(transformation))
            logger.log(java.util.logging.Level.WARNING, "Algorithm non-standard, expected one of " + ENC_ALGORITHMS);

        XMLCipher instance = new XMLCipher();

        instance._algorithm = transformation;
        instance._key = null;
        instance._kek = null;


        /* Create a canonicaliser - used when serialising DOM to octets
         * prior to encryption (and for the reverse) */

        try {
            instance._canon = Canonicalizer.getInstance
                    (Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS);

        } catch (InvalidCanonicalizerException ice) {
            throw new XMLEncryptionException("empty", ice);
        }

        String jceAlgorithm = JCEMapper.translateURItoJCEID(transformation);

        try {
            instance._contextCipher = cipher;
            //Cipher.getInstance(jceAlgorithm);
            logger.log(java.util.logging.Level.FINE, "cihper.algoritm = " +
                    instance._contextCipher.getAlgorithm());
        }catch(Exception ex) {
            throw new XMLEncryptionException("empty", ex);
        }

        return (instance);
    }

    /**
     * Returns an <code>XMLCipher</code> that implements the specified
     * transformation and operates on the specified context document.
     *
     * @param transformation the name of the transformation, e.g.,
     *   <code>XMLCipher.TRIPLEDES</code> which is shorthand for
     *   &quot;http://www.w3.org/2001/04/xmlenc#tripledes-cbc&quot;
     * @param provider the JCE provider that supplies the transformation
     * @return the XMLCipher
     * @throws XMLEncryptionException
     */

    public static XMLCipher getProviderInstance(String transformation, String provider)
            throws XMLEncryptionException {
        // sanity checks
        logger.log(java.util.logging.Level.FINE, "Getting XMLCipher...");
        if (null == transformation)
            logger.log(java.util.logging.Level.SEVERE, "Transformation unexpectedly null...");
        if(null == provider)
            logger.log(java.util.logging.Level.SEVERE, "Provider unexpectedly null..");
        if("" == provider)
            logger.log(java.util.logging.Level.SEVERE, "Provider's value unexpectedly not specified...");
        if(!isValidEncryptionAlgorithm(transformation))
            logger.log(java.util.logging.Level.WARNING, "Algorithm non-standard, expected one of " + ENC_ALGORITHMS);

                XMLCipher instance = new XMLCipher();

        instance._algorithm = transformation;
                instance._requestedJCEProvider = provider;
                instance._key = null;
                instance._kek = null;

                /* Create a canonicaliser - used when serialising DOM to octets
                 * prior to encryption (and for the reverse) */

                try {
                        instance._canon = Canonicalizer.getInstance
                                (Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS);
                } catch (InvalidCanonicalizerException ice) {
                        throw new XMLEncryptionException("empty", ice);
                }

        try {
                        String jceAlgorithm =
                                JCEMapper.translateURItoJCEID(transformation);

            instance._contextCipher = Cipher.getInstance(jceAlgorithm, provider);

            logger.log(java.util.logging.Level.FINE, "cipher._algorithm = " +
                instance._contextCipher.getAlgorithm());
            logger.log(java.util.logging.Level.FINE, "provider.name = " + provider);
        } catch (NoSuchAlgorithmException nsae) {
            throw new XMLEncryptionException("empty", nsae);
        } catch (NoSuchProviderException nspre) {
            throw new XMLEncryptionException("empty", nspre);
        } catch (NoSuchPaddingException nspe) {
            throw new XMLEncryptionException("empty", nspe);
        }

        return (instance);
    }

        /**
         * Returns an <code>XMLCipher</code> that implements the specified
     * transformation, operates on the specified context document and serializes
     * the document with the specified canonicalization algorithm before it
     * encrypts the document.
     * <p>
         *
         * @param transformation        the name of the transformation, e.g.,
     *                                                  <code>XMLCipher.TRIPLEDES</code> which is
     *                                                  shorthand for
     *                                  &quot;http://www.w3.org/2001/04/xmlenc#tripledes-cbc&quot;
         * @param provider              the JCE provider that supplies the transformation
         * @param canon                         the name of the c14n algorithm, if
         *                                                      <code>null</code> use standard serializer
         * @return
         * @throws XMLEncryptionException
         */
        public static XMLCipher getProviderInstance(
                String transformation,
                String provider,
                String canon)
                throws XMLEncryptionException {

                XMLCipher instance = XMLCipher.getProviderInstance(transformation, provider);
                if (canon != null) {
                        try {
                                instance._canon = Canonicalizer.getInstance(canon);
                        } catch (InvalidCanonicalizerException ice) {
                                throw new XMLEncryptionException("empty", ice);
                        }
                }
                return instance;
        }

    /**
     * Returns an <code>XMLCipher</code> that implements no specific
         * transformation, and can therefore only be used for decrypt or
         * unwrap operations where the encryption method is defined in the
         * <code>EncryptionMethod</code> element.
         *
     * @return The XMLCipher
     * @throws XMLEncryptionException
     */

    public static XMLCipher getInstance()
            throws XMLEncryptionException {
        // sanity checks
        logger.log(java.util.logging.Level.FINE, "Getting XMLCipher for no transformation...");

                XMLCipher instance = new XMLCipher();

        instance._algorithm = null;
                instance._requestedJCEProvider = null;
                instance._key = null;
                instance._kek = null;
                instance._contextCipher = null;

                /* Create a canonicaliser - used when serialising DOM to octets
                 * prior to encryption (and for the reverse) */

                try {
                        instance._canon = Canonicalizer.getInstance
                                (Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS);
                } catch (InvalidCanonicalizerException ice) {
                        throw new XMLEncryptionException("empty", ice);
                }

        return (instance);
    }

    /**
     * Returns an <code>XMLCipher</code> that implements no specific
         * transformation, and can therefore only be used for decrypt or
         * unwrap operations where the encryption method is defined in the
         * <code>EncryptionMethod</code> element.
         *
         * Allows the caller to specify a provider that will be used for
         * cryptographic operations.
     *
     * @param provider the JCE provider that supplies the cryptographic
         * needs.
     * @return the XMLCipher
     * @throws XMLEncryptionException
     */

    public static XMLCipher getProviderInstance(String provider)
            throws XMLEncryptionException {
        // sanity checks

        logger.log(java.util.logging.Level.FINE, "Getting XMLCipher, provider but no transformation");
        if(null == provider)
            logger.log(java.util.logging.Level.SEVERE, "Provider unexpectedly null..");
        if("" == provider)
            logger.log(java.util.logging.Level.SEVERE, "Provider's value unexpectedly not specified...");

                XMLCipher instance = new XMLCipher();

        instance._algorithm = null;
                instance._requestedJCEProvider = provider;
                instance._key = null;
                instance._kek = null;
                instance._contextCipher = null;

                try {
                        instance._canon = Canonicalizer.getInstance
                                (Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS);
                } catch (InvalidCanonicalizerException ice) {
                        throw new XMLEncryptionException("empty", ice);
                }

        return (instance);
    }

    /**
     * Initializes this cipher with a key.
     * <p>
     * The cipher is initialized for one of the following four operations:
     * encryption, decryption, key wrapping or key unwrapping, depending on the
     * value of opmode.
         *
         * For WRAP and ENCRYPT modes, this also initialises the internal
         * EncryptedKey or EncryptedData (with a CipherValue)
         * structure that will be used during the ensuing operations.  This
         * can be obtained (in order to modify KeyInfo elements etc. prior to
         * finalising the encryption) by calling
         * {@link #getEncryptedData} or {@link #getEncryptedKey}.
     *
     * @param opmode the operation mode of this cipher (this is one of the
     *   following: ENCRYPT_MODE, DECRYPT_MODE, WRAP_MODE or UNWRAP_MODE)
     * @param key
     * @see javax.crypto.Cipher#init(int, java.security.Key)
     * @throws XMLEncryptionException
     */
    public void init(int opmode, Key key) throws XMLEncryptionException {
        // sanity checks
        logger.log(java.util.logging.Level.FINE, "Initializing XMLCipher...");

                _ek = null;
                _ed = null;

                switch (opmode) {

                case ENCRYPT_MODE :
                        logger.log(java.util.logging.Level.FINE, "opmode = ENCRYPT_MODE");
                        _ed = createEncryptedData(CipherData.VALUE_TYPE, "NO VALUE YET");
                        break;
                case DECRYPT_MODE :
                        logger.log(java.util.logging.Level.FINE, "opmode = DECRYPT_MODE");
                        break;
                case WRAP_MODE :
                        logger.log(java.util.logging.Level.FINE, "opmode = WRAP_MODE");
                        _ek = createEncryptedKey(CipherData.VALUE_TYPE, "NO VALUE YET");
                        break;
                case UNWRAP_MODE :
                        logger.log(java.util.logging.Level.FINE, "opmode = UNWRAP_MODE");
                        break;
                default :
                        logger.log(java.util.logging.Level.SEVERE, "Mode unexpectedly invalid");
                        throw new XMLEncryptionException("Invalid mode in init");
                }

        _cipherMode = opmode;
                _key = key;

    }

        /**
         * Get the EncryptedData being build
         *
         * Returns the EncryptedData being built during an ENCRYPT operation.
         * This can then be used by applications to add KeyInfo elements and
         * set other parameters.
         *
         * @return The EncryptedData being built
         */

        public EncryptedData getEncryptedData() {

                // Sanity checks
                logger.log(java.util.logging.Level.FINE, "Returning EncryptedData");
                return _ed;

        }

        /**
         * Get the EncryptedData being build
         *
         * Returns the EncryptedData being built during an ENCRYPT operation.
         * This can then be used by applications to add KeyInfo elements and
         * set other parameters.
         *
         * @return The EncryptedData being built
         */

        public EncryptedKey getEncryptedKey() {

                // Sanity checks
                logger.log(java.util.logging.Level.FINE, "Returning EncryptedKey");
                return _ek;
        }

        /**
         * Set a Key Encryption Key.
         * <p>
         * The Key Encryption Key (KEK) is used for encrypting/decrypting
         * EncryptedKey elements.  By setting this separately, the XMLCipher
         * class can know whether a key applies to the data part or wrapped key
         * part of an encrypted object.
         *
         * @param kek The key to use for de/encrypting key data
         */

        public void setKEK(Key kek) {

                _kek = kek;

        }

        /**
         * Martial an EncryptedData
         *
         * Takes an EncryptedData object and returns a DOM Element that
         * represents the appropriate <code>EncryptedData</code>
         * <p>
         * <b>Note:</b> This should only be used in cases where the context
         * document has been passed in via a call to doFinal.
         *
         * @param encryptedData EncryptedData object to martial
         * @return the DOM <code>Element</code> representing the passed in
         * object
     */

        public Element martial(EncryptedData encryptedData) {

                return (_factory.toElement (encryptedData));

        }

        /**
         * Martial an EncryptedKey
         *
         * Takes an EncryptedKey object and returns a DOM Element that
         * represents the appropriate <code>EncryptedKey</code>
         *
         * <p>
         * <b>Note:</b> This should only be used in cases where the context
         * document has been passed in via a call to doFinal.
         *
         * @param encryptedKey EncryptedKey object to martial
         * @return the DOM <code>Element</code> representing the passed in
         * object */

        public Element martial(EncryptedKey encryptedKey) {

                return (_factory.toElement (encryptedKey));

        }

        /**
         * Martial an EncryptedData
         *
         * Takes an EncryptedData object and returns a DOM Element that
         * represents the appropriate <code>EncryptedData</code>
         *
         * @param context The document that will own the returned nodes
         * @param encryptedData EncryptedData object to martial
         * @return the DOM <code>Element</code> representing the passed in
         * object */

        public Element martial(Document context, EncryptedData encryptedData) {

                _contextDocument = context;
                return (_factory.toElement (encryptedData));

        }

        /**
         * Martial an EncryptedKey
         *
         * Takes an EncryptedKey object and returns a DOM Element that
         * represents the appropriate <code>EncryptedKey</code>
         *
         * @param context The document that will own the created nodes
         * @param encryptedKey EncryptedKey object to martial
         * @return the DOM <code>Element</code> representing the passed in
         * object */

        public Element martial(Document context, EncryptedKey encryptedKey) {

                _contextDocument = context;
                return (_factory.toElement (encryptedKey));

        }

    /**
     * Encrypts an <code>Element</code> and replaces it with its encrypted
     * counterpart in the context <code>Document</code>, that is, the
     * <code>Document</code> specified when one calls
     * {@link #getInstance(String) getInstance}.
     *
     * @param element the <code>Element</code> to encrypt.
     * @return the context <code>Document</code> with the encrypted
     *   <code>Element</code> having replaced the source <code>Element</code>.
     *  @throws Exception
     */

    private Document encryptElement(Element element) throws Exception{
        logger.log(java.util.logging.Level.FINE, "Encrypting element...");
        if(null == element)
            logger.log(java.util.logging.Level.SEVERE, "Element unexpectedly null...");
        if(_cipherMode != ENCRYPT_MODE)
            logger.log(java.util.logging.Level.FINE, "XMLCipher unexpectedly not in ENCRYPT_MODE...");

                if (_algorithm == null) {
                throw new XMLEncryptionException("XMLCipher instance without transformation specified");
                }
                encryptData(_contextDocument, element, false);

        Element encryptedElement = _factory.toElement(_ed);

        Node sourceParent = element.getParentNode();
        sourceParent.replaceChild(encryptedElement, element);

        return (_contextDocument);
    }

    /**
     * Encrypts a <code>NodeList</code> (the contents of an
     * <code>Element</code>) and replaces its parent <code>Element</code>'s
     * content with this the resulting <code>EncryptedType</code> within the
     * context <code>Document</code>, that is, the <code>Document</code>
     * specified when one calls
     * {@link #getInstance(String) getInstance}.
     *
     * @param element the <code>NodeList</code> to encrypt.
     * @return the context <code>Document</code> with the encrypted
     *   <code>NodeList</code> having replaced the content of the source
     *   <code>Element</code>.
     * @throws Exception
     */
    private Document encryptElementContent(Element element) throws
            /* XMLEncryption */Exception {
        logger.log(java.util.logging.Level.FINE, "Encrypting element content...");
        if(null == element)
            logger.log(java.util.logging.Level.SEVERE, "Element unexpectedly null...");
        if(_cipherMode != ENCRYPT_MODE)
            logger.log(java.util.logging.Level.FINE, "XMLCipher unexpectedly not in ENCRYPT_MODE...");

                if (_algorithm == null) {
                throw new XMLEncryptionException("XMLCipher instance without transformation specified");
                }
                encryptData(_contextDocument, element, true);

        Element encryptedElement = _factory.toElement(_ed);

        removeContent(element);
        element.appendChild(encryptedElement);

        return (_contextDocument);
    }

    /**
     * Process a DOM <code>Document</code> node. The processing depends on the
     * initialization parameters of {@link #init(int, Key) init()}.
     *
     * @param context the context <code>Document</code>.
     * @param source the <code>Document</code> to be encrypted or decrypted.
     * @return the processed <code>Document</code>.
     * @throws Exception to indicate any exceptional conditions.
     */
    public Document doFinal(Document context, Document source) throws
            /* XMLEncryption */Exception {
        logger.log(java.util.logging.Level.FINE, "Processing source document...");
        if(null == context)
            logger.log(java.util.logging.Level.SEVERE, "Context document unexpectedly null...");
        if(null == source)
            logger.log(java.util.logging.Level.SEVERE, "Source document unexpectedly null...");

        _contextDocument = context;

        Document result = null;

        switch (_cipherMode) {
        case DECRYPT_MODE:
            result = decryptElement(source.getDocumentElement());
            break;
        case ENCRYPT_MODE:
            result = encryptElement(source.getDocumentElement());
            break;
        case UNWRAP_MODE:
            break;
        case WRAP_MODE:
            break;
        default:
            throw new XMLEncryptionException(
                "empty", new IllegalStateException());
        }

        return (result);
    }

    /**
     * Process a DOM <code>Element</code> node. The processing depends on the
     * initialization parameters of {@link #init(int, Key) init()}.
     *
     * @param context the context <code>Document</code>.
     * @param element the <code>Element</code> to be encrypted.
     * @return the processed <code>Document</code>.
     * @throws Exception to indicate any exceptional conditions.
     */
    public Document doFinal(Document context, Element element) throws
            /* XMLEncryption */Exception {
        logger.log(java.util.logging.Level.FINE, "Processing source element...");
        if(null == context)
            logger.log(java.util.logging.Level.SEVERE, "Context document unexpectedly null...");
        if(null == element)
            logger.log(java.util.logging.Level.SEVERE, "Source element unexpectedly null...");

        _contextDocument = context;

        Document result = null;

        switch (_cipherMode) {
        case DECRYPT_MODE:
            result = decryptElement(element);
            break;
        case ENCRYPT_MODE:
            result = encryptElement(element);
            break;
        case UNWRAP_MODE:
            break;
        case WRAP_MODE:
            break;
        default:
            throw new XMLEncryptionException(
                "empty", new IllegalStateException());
        }

        return (result);
    }

    /**
     * Process the contents of a DOM <code>Element</code> node. The processing
     * depends on the initialization parameters of
     * {@link #init(int, Key) init()}.
     *
     * @param context the context <code>Document</code>.
     * @param element the <code>Element</code> which contents is to be
     *   encrypted.
     * @param content
     * @return the processed <code>Document</code>.
     * @throws Exception to indicate any exceptional conditions.
     */
    public Document doFinal(Document context, Element element, boolean content)
            throws /* XMLEncryption*/ Exception {
        logger.log(java.util.logging.Level.FINE, "Processing source element...");
        if(null == context)
            logger.log(java.util.logging.Level.SEVERE, "Context document unexpectedly null...");
        if(null == element)
            logger.log(java.util.logging.Level.SEVERE, "Source element unexpectedly null...");

        _contextDocument = context;

        Document result = null;

        switch (_cipherMode) {
        case DECRYPT_MODE:
            if (content) {
                result = decryptElementContent(element);
            } else {
                result = decryptElement(element);
            }
            break;
        case ENCRYPT_MODE:
            if (content) {
                result = encryptElementContent(element);
            } else {
                result = encryptElement(element);
            }
            break;
        case UNWRAP_MODE:
            break;
        case WRAP_MODE:
            break;
        default:
            throw new XMLEncryptionException(
                "empty", new IllegalStateException());
        }

        return (result);
    }

    /**
     * Returns an <code>EncryptedData</code> interface. Use this operation if
     * you want to have full control over the contents of the
     * <code>EncryptedData</code> structure.
     *
     * this does not change the source document in any way.
     *
     * @param context the context <code>Document</code>.
     * @param element the <code>Element</code> that will be encrypted.
     * @return the <code>EncryptedData</code>
     * @throws Exception
     */
    public EncryptedData encryptData(Document context, Element element) throws
            /* XMLEncryption */Exception {
        return encryptData(context, element, false);
    }

    /**
     * Returns an <code>EncryptedData</code> interface. Use this operation if
     * you want to have full control over the serialization of the element
     * or element content.
     *
     * This does not change the source document in any way.
     *
     * @param context the context <code>Document</code>.
     * @param type a URI identifying type information about the plaintext form
     *    of the encrypted content (may be <code>null</code>)
     * @param serializedData the serialized data
     * @return the <code>EncryptedData</code>
     * @throws Exception
     */
    public EncryptedData encryptData(Document context, String type,
        InputStream serializedData) throws Exception {

        logger.log(java.util.logging.Level.FINE, "Encrypting element...");
        if (null == context)
            logger.log(java.util.logging.Level.SEVERE, "Context document unexpectedly null...");
        if (null == serializedData)
            logger.log(java.util.logging.Level.SEVERE, "Serialized data unexpectedly null...");
        if (_cipherMode != ENCRYPT_MODE)
            logger.log(java.util.logging.Level.FINE, "XMLCipher unexpectedly not in ENCRYPT_MODE...");

        return encryptData(context, null, type, serializedData);
    }

    /**
     * Returns an <code>EncryptedData</code> interface. Use this operation if
     * you want to have full control over the contents of the
     * <code>EncryptedData</code> structure.
     *
     * this does not change the source document in any way.
     *
     * @param context the context <code>Document</code>.
     * @param element the <code>Element</code> that will be encrypted.
     * @param contentMode <code>true</code> to encrypt element's content only,
     *    <code>false</code> otherwise
     * @return the <code>EncryptedData</code>
     * @throws Exception
     */
    public EncryptedData encryptData(
        Document context, Element element, boolean contentMode)
        throws /* XMLEncryption */ Exception {

        logger.log(java.util.logging.Level.FINE, "Encrypting element...");
        if (null == context)
            logger.log(java.util.logging.Level.SEVERE, "Context document unexpectedly null...");
        if (null == element)
            logger.log(java.util.logging.Level.SEVERE, "Element unexpectedly null...");
        if (_cipherMode != ENCRYPT_MODE)
            logger.log(java.util.logging.Level.FINE, "XMLCipher unexpectedly not in ENCRYPT_MODE...");

        if (contentMode) {
            return encryptData
                (context, element, EncryptionConstants.TYPE_CONTENT, null);
        } else {
            return encryptData
                (context, element, EncryptionConstants.TYPE_ELEMENT, null);
        }
    }

    private EncryptedData encryptData(
        Document context, Element element, String type,
        InputStream serializedData) throws /* XMLEncryption */ Exception {

        _contextDocument = context;

        if (_algorithm == null) {
            throw new XMLEncryptionException
                ("XMLCipher instance without transformation specified");
        }

        String serializedOctets = null;
        if (serializedData == null) {
            if (type == EncryptionConstants.TYPE_CONTENT) {
                NodeList children = element.getChildNodes();
                if (null != children) {
                    serializedOctets = _serializer.serialize(children);
                } else {
                    Object exArgs[] = { "Element has no content." };
                    throw new XMLEncryptionException("empty", exArgs);
                }
            } else {
                serializedOctets = _serializer.serialize(element);
            }
            logger.log(java.util.logging.Level.FINE, "Serialized octets:\n" + serializedOctets);
        }

        byte[] encryptedBytes = null;

        // Now create the working cipher if none was created already
        Cipher c;
        if (_contextCipher == null) {
            String jceAlgorithm = JCEMapper.translateURItoJCEID(_algorithm);
            logger.log(java.util.logging.Level.FINE, "alg = " + jceAlgorithm);

            try {
                if (_requestedJCEProvider == null)
                    c = Cipher.getInstance(jceAlgorithm);
                else
                    c = Cipher.getInstance(jceAlgorithm, _requestedJCEProvider);
            } catch (NoSuchAlgorithmException nsae) {
                throw new XMLEncryptionException("empty", nsae);
            } catch (NoSuchProviderException nspre) {
                throw new XMLEncryptionException("empty", nspre);
            } catch (NoSuchPaddingException nspae) {
                throw new XMLEncryptionException("empty", nspae);
            }
        } else {
            c = _contextCipher;
        }
        // Now perform the encryption

        try {
            // Should internally generate an IV
            // todo - allow user to set an IV
            c.init(_cipherMode, _key);
        } catch (InvalidKeyException ike) {
            throw new XMLEncryptionException("empty", ike);
        }

        try {
            if (serializedData != null) {
                int numBytes;
                byte[] buf = new byte[8192];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((numBytes = serializedData.read(buf)) != -1) {
                    byte[] data = c.update(buf, 0, numBytes);
                    baos.write(data);
                }
                baos.write(c.doFinal());
                encryptedBytes = baos.toByteArray();
            } else {
                encryptedBytes = c.doFinal(serializedOctets.getBytes("UTF-8"));
                logger.log(java.util.logging.Level.FINE, "Expected cipher.outputSize = " +
                    Integer.toString(c.getOutputSize(
                        serializedOctets.getBytes().length)));
            }
            logger.log(java.util.logging.Level.FINE, "Actual cipher.outputSize = " +
                Integer.toString(encryptedBytes.length));
        } catch (IllegalStateException ise) {
            throw new XMLEncryptionException("empty", ise);
        } catch (IllegalBlockSizeException ibse) {
            throw new XMLEncryptionException("empty", ibse);
        } catch (BadPaddingException bpe) {
            throw new XMLEncryptionException("empty", bpe);
        } catch (UnsupportedEncodingException uee) {
            throw new XMLEncryptionException("empty", uee);
        }

        // Now build up to a properly XML Encryption encoded octet stream
        // IvParameterSpec iv;
        byte[] iv = c.getIV();
        byte[] finalEncryptedBytes =
                new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, finalEncryptedBytes, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, finalEncryptedBytes, iv.length,
                         encryptedBytes.length);
        String base64EncodedEncryptedOctets = Base64.encode(finalEncryptedBytes);

        logger.log(java.util.logging.Level.FINE, "Encrypted octets:\n" + base64EncodedEncryptedOctets);
        logger.log(java.util.logging.Level.FINE, "Encrypted octets length = " +
            base64EncodedEncryptedOctets.length());

        try {
            CipherData cd = _ed.getCipherData();
            CipherValue cv = cd.getCipherValue();
            // cv.setValue(base64EncodedEncryptedOctets.getBytes());
            cv.setValue(base64EncodedEncryptedOctets);

            if (type != null) {
                _ed.setType(new URI(type).toString());
            }
            EncryptionMethod method =
                _factory.newEncryptionMethod(new URI(_algorithm).toString());
            _ed.setEncryptionMethod(method);
        } catch (URI.MalformedURIException mfue) {
            throw new XMLEncryptionException("empty", mfue);
        }
        return (_ed);
    }

    /**
     * Returns an <code>EncryptedData</code> interface. Use this operation if
     * you want to load an <code>EncryptedData</code> structure from a DOM
         * structure and manipulate the contents
     *
     * @param context the context <code>Document</code>.
     * @param element the <code>Element</code> that will be loaded
     * @throws XMLEncryptionException
     * @return
     */
    public EncryptedData loadEncryptedData(Document context, Element element)
                throws XMLEncryptionException {
        logger.log(java.util.logging.Level.FINE, "Loading encrypted element...");
        if(null == context)
            logger.log(java.util.logging.Level.SEVERE, "Context document unexpectedly null...");
        if(null == element)
            logger.log(java.util.logging.Level.SEVERE, "Element unexpectedly null...");
        if(_cipherMode != DECRYPT_MODE)
            logger.log(java.util.logging.Level.SEVERE, "XMLCipher unexpectedly not in DECRYPT_MODE...");

        _contextDocument = context;
        _ed = _factory.newEncryptedData(element);

                return (_ed);
    }

    /**
     * Returns an <code>EncryptedKey</code> interface. Use this operation if
     * you want to load an <code>EncryptedKey</code> structure from a DOM
         * structure and manipulate the contents.
     *
     * @param context the context <code>Document</code>.
     * @param element the <code>Element</code> that will be loaded
     * @return
     * @throws XMLEncryptionException
     */

    public EncryptedKey loadEncryptedKey(Document context, Element element)
                throws XMLEncryptionException {
        logger.log(java.util.logging.Level.FINE, "Loading encrypted key...");
        if(null == context)
            logger.log(java.util.logging.Level.SEVERE, "Context document unexpectedly null...");
        if(null == element)
            logger.log(java.util.logging.Level.SEVERE, "Element unexpectedly null...");
        if(_cipherMode != UNWRAP_MODE && _cipherMode != DECRYPT_MODE)
            logger.log(java.util.logging.Level.FINE, "XMLCipher unexpectedly not in UNWRAP_MODE or DECRYPT_MODE...");

        _contextDocument = context;
        _ek = _factory.newEncryptedKey(element);
                return (_ek);
    }

    /**
     * Returns an <code>EncryptedKey</code> interface. Use this operation if
     * you want to load an <code>EncryptedKey</code> structure from a DOM
         * structure and manipulate the contents.
         *
         * Assumes that the context document is the document that owns the element
     *
     * @param element the <code>Element</code> that will be loaded
     * @return
     * @throws XMLEncryptionException
     */

    public EncryptedKey loadEncryptedKey(Element element)
                throws XMLEncryptionException {

                return (loadEncryptedKey(element.getOwnerDocument(), element));
    }

    /**
     * Encrypts a key to an EncryptedKey structure
         *
         * @param doc the Context document that will be used to general DOM
         * @param key Key to encrypt (will use previously set KEK to
         * perform encryption
     * @return
     * @throws XMLEncryptionException
     */

    public EncryptedKey encryptKey(Document doc, Key key) throws
            XMLEncryptionException {

        logger.log(java.util.logging.Level.FINE, "Encrypting key ...");

        if(null == key)
            logger.log(java.util.logging.Level.SEVERE, "Key unexpectedly null...");
        if(_cipherMode != WRAP_MODE)
            logger.log(java.util.logging.Level.FINE, "XMLCipher unexpectedly not in WRAP_MODE...");

                if (_algorithm == null) {

                        throw new XMLEncryptionException("XMLCipher instance without transformation specified");
                }

                _contextDocument = doc;

                byte[] encryptedBytes = null;
                Cipher c;

                if (_contextCipher == null) {
                        // Now create the working cipher

                        String jceAlgorithm =
                                JCEMapper.translateURItoJCEID(_algorithm);

                        logger.log(java.util.logging.Level.FINE, "alg = " + jceAlgorithm);

                        try {
                            if (_requestedJCEProvider == null)
                                c = Cipher.getInstance(jceAlgorithm);
                            else
                                c = Cipher.getInstance(jceAlgorithm, _requestedJCEProvider);
                        } catch (NoSuchAlgorithmException nsae) {
                                throw new XMLEncryptionException("empty", nsae);
                        } catch (NoSuchProviderException nspre) {
                                throw new XMLEncryptionException("empty", nspre);
                        } catch (NoSuchPaddingException nspae) {
                                throw new XMLEncryptionException("empty", nspae);
                        }
                } else {
                        c = _contextCipher;
                }
                // Now perform the encryption

                try {
                        // Should internally generate an IV
                        // todo - allow user to set an IV
                        c.init(Cipher.WRAP_MODE, _key);
                        encryptedBytes = c.wrap(key);
                } catch (InvalidKeyException ike) {
                        throw new XMLEncryptionException("empty", ike);
                } catch (IllegalBlockSizeException ibse) {
                        throw new XMLEncryptionException("empty", ibse);
                }

        String base64EncodedEncryptedOctets = Base64.encode(encryptedBytes);

        logger.log(java.util.logging.Level.FINE, "Encrypted key octets:\n" + base64EncodedEncryptedOctets);
        logger.log(java.util.logging.Level.FINE, "Encrypted key octets length = " +
            base64EncodedEncryptedOctets.length());

                CipherValue cv = _ek.getCipherData().getCipherValue();
                cv.setValue(base64EncodedEncryptedOctets);

        try {
            EncryptionMethod method = _factory.newEncryptionMethod(
                new URI(_algorithm).toString());
            _ek.setEncryptionMethod(method);
        } catch (URI.MalformedURIException mfue) {
            throw new XMLEncryptionException("empty", mfue);
        }
                return _ek;

    }

        /**
         * Decrypt a key from a passed in EncryptedKey structure
         *
         * @param encryptedKey Previously loaded EncryptedKey that needs
         * to be decrypted.
         * @param algorithm Algorithm for the decryption
         * @return a key corresponding to the give type
     * @throws XMLEncryptionException
         */

        public Key decryptKey(EncryptedKey encryptedKey, String algorithm) throws
                    XMLEncryptionException {

        logger.log(java.util.logging.Level.FINE, "Decrypting key from previously loaded EncryptedKey...");

        if(_cipherMode != UNWRAP_MODE)
            logger.log(java.util.logging.Level.FINE, "XMLCipher unexpectedly not in UNWRAP_MODE...");

                if (algorithm == null) {
                        throw new XMLEncryptionException("Cannot decrypt a key without knowing the algorithm");
                }

                if (_key == null) {

                        logger.log(java.util.logging.Level.FINE, "Trying to find a KEK via key resolvers");

                        KeyInfo ki = encryptedKey.getKeyInfo();
                        if (ki != null) {
                                try {
                                        _key = ki.getSecretKey();
                                }
                                catch (Exception e) {
                                }
                        }
                        if (_key == null) {
                                logger.log(java.util.logging.Level.SEVERE, "XMLCipher::decryptKey called without a KEK and cannot resolve");
                                throw new XMLEncryptionException("Unable to decrypt without a KEK");
                        }
                }

                // Obtain the encrypted octets
                XMLCipherInput cipherInput = new XMLCipherInput(encryptedKey);
                byte [] encryptedBytes = cipherInput.getBytes();

                String jceKeyAlgorithm =
                        JCEMapper.getJCEKeyAlgorithmFromURI(algorithm);

                Cipher c;
                if (_contextCipher == null) {
                        // Now create the working cipher

                        String jceAlgorithm =
                                JCEMapper.translateURItoJCEID(
                                        encryptedKey.getEncryptionMethod().getAlgorithm());

                        logger.log(java.util.logging.Level.FINE, "JCE Algorithm = " + jceAlgorithm);

                        try {
                            if (_requestedJCEProvider == null)
                                c = Cipher.getInstance(jceAlgorithm);
                            else
                                c = Cipher.getInstance(jceAlgorithm, _requestedJCEProvider);
                        } catch (NoSuchAlgorithmException nsae) {
                                throw new XMLEncryptionException("empty", nsae);
                        } catch (NoSuchProviderException nspre) {
                                throw new XMLEncryptionException("empty", nspre);
                        } catch (NoSuchPaddingException nspae) {
                                throw new XMLEncryptionException("empty", nspae);
                        }
                } else {
                        c = _contextCipher;
                }

                Key ret;

                try {
                        c.init(Cipher.UNWRAP_MODE, _key);
                        ret = c.unwrap(encryptedBytes, jceKeyAlgorithm, Cipher.SECRET_KEY);

                } catch (InvalidKeyException ike) {
                        throw new XMLEncryptionException("empty", ike);
                } catch (NoSuchAlgorithmException nsae) {
                        throw new XMLEncryptionException("empty", nsae);
                }

                logger.log(java.util.logging.Level.FINE, "Decryption of key type " + algorithm + " OK");

                return ret;

    }

        /**
         * Decrypt a key from a passed in EncryptedKey structure.  This version
         * is used mainly internally, when  the cipher already has an
         * EncryptedData loaded.  The algorithm URI will be read from the
         * EncryptedData
         *
         * @param encryptedKey Previously loaded EncryptedKey that needs
         * to be decrypted.
         * @return a key corresponding to the give type
     * @throws XMLEncryptionException
         */

        public Key decryptKey(EncryptedKey encryptedKey) throws
                    XMLEncryptionException {

                return decryptKey(encryptedKey, _ed.getEncryptionMethod().getAlgorithm());

        }

    /**
     * Removes the contents of a <code>Node</code>.
     *
     * @param node the <code>Node</code> to clear.
     */
    private static void removeContent(Node node) {
       while (node.hasChildNodes()) {
            node.removeChild(node.getFirstChild());
        }
    }

    /**
     * Decrypts <code>EncryptedData</code> in a single-part operation.
     *
     * @param element the <code>EncryptedData</code> to decrypt.
     * @return the <code>Node</code> as a result of the decrypt operation.
     * @throws XMLEncryptionException
     */
    private Document decryptElement(Element element) throws
            XMLEncryptionException {

        logger.log(java.util.logging.Level.FINE, "Decrypting element...");

        if(_cipherMode != DECRYPT_MODE)
            logger.log(java.util.logging.Level.SEVERE, "XMLCipher unexpectedly not in DECRYPT_MODE...");

                String octets;
                try {
                        octets = new String(decryptToByteArray(element), "UTF-8");
                } catch (UnsupportedEncodingException uee) {
                        throw new XMLEncryptionException("empty", uee);
                }


        logger.log(java.util.logging.Level.FINE, "Decrypted octets:\n" + octets);

        Node sourceParent =  element.getParentNode();

        DocumentFragment decryptedFragment =
                        _serializer.deserialize(octets, sourceParent);


                // The de-serialiser returns a fragment whose children we need to
                // take on.

                if (sourceParent != null && sourceParent.getNodeType() == Node.DOCUMENT_NODE) {

                    // If this is a content decryption, this may have problems

                    _contextDocument.removeChild(_contextDocument.getDocumentElement());
                    _contextDocument.appendChild(decryptedFragment);
                }
                else {
                    sourceParent.replaceChild(decryptedFragment, element);

                }

        return (_contextDocument);
    }


        /**
         *
         * @param element
     * @return
     * @throws XMLEncryptionException
         */
    private Document decryptElementContent(Element element) throws
                XMLEncryptionException {
        Element e = (Element) element.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS,
                EncryptionConstants._TAG_ENCRYPTEDDATA).item(0);

        if (null == e) {
                throw new XMLEncryptionException("No EncryptedData child element.");
        }

        return (decryptElement(e));
    }

        /**
         * Decrypt an EncryptedData element to a byte array
         *
         * When passed in an EncryptedData node, returns the decryption
         * as a byte array.
         *
         * Does not modify the source document
     * @param element
     * @return
     * @throws XMLEncryptionException
         */

        public byte[] decryptToByteArray(Element element)
                throws XMLEncryptionException {

        logger.log(java.util.logging.Level.FINE, "Decrypting to ByteArray...");

        if(_cipherMode != DECRYPT_MODE)
            logger.log(java.util.logging.Level.SEVERE, "XMLCipher unexpectedly not in DECRYPT_MODE...");

        EncryptedData encryptedData = _factory.newEncryptedData(element);

                if (_key == null) {

                        KeyInfo ki = encryptedData.getKeyInfo();

                        if (ki != null) {
                                try {
                                        // Add a EncryptedKey resolver
                                        ki.registerInternalKeyResolver(
                                     new EncryptedKeyResolver(encryptedData.
                                                                                                  getEncryptionMethod().
                                                                                                  getAlgorithm(),
                                                                                                  _kek));
                                        _key = ki.getSecretKey();
                                } catch (KeyResolverException kre) {
                                        // We will throw in a second...
                                }
                        }

                        if (_key == null) {
                                logger.log(java.util.logging.Level.SEVERE, "XMLCipher::decryptElement called without a key and unable to resolve");

                                throw new XMLEncryptionException("encryption.nokey");
                        }
                }

                // Obtain the encrypted octets
                XMLCipherInput cipherInput = new XMLCipherInput(encryptedData);
                byte [] encryptedBytes = cipherInput.getBytes();

                // Now create the working cipher

                String jceAlgorithm =
                        JCEMapper.translateURItoJCEID(encryptedData.getEncryptionMethod().getAlgorithm());

                Cipher c;
                try {
                    if (_requestedJCEProvider == null)
                        c = Cipher.getInstance(jceAlgorithm);
                    else
                        c = Cipher.getInstance(jceAlgorithm, _requestedJCEProvider);
                } catch (NoSuchAlgorithmException nsae) {
                        throw new XMLEncryptionException("empty", nsae);
                } catch (NoSuchProviderException nspre) {
                        throw new XMLEncryptionException("empty", nspre);
                } catch (NoSuchPaddingException nspae) {
                        throw new XMLEncryptionException("empty", nspae);
                }

                // Calculate the IV length and copy out

                // For now, we only work with Block ciphers, so this will work.
                // This should probably be put into the JCE mapper.

                int ivLen = c.getBlockSize();
                byte[] ivBytes = new byte[ivLen];

                // You may be able to pass the entire piece in to IvParameterSpec
                // and it will only take the first x bytes, but no way to be certain
                // that this will work for every JCE provider, so lets copy the
                // necessary bytes into a dedicated array.

                System.arraycopy(encryptedBytes, 0, ivBytes, 0, ivLen);
                IvParameterSpec iv = new IvParameterSpec(ivBytes);

                try {
                        c.init(_cipherMode, _key, iv);
                } catch (InvalidKeyException ike) {
                        throw new XMLEncryptionException("empty", ike);
                } catch (InvalidAlgorithmParameterException iape) {
                        throw new XMLEncryptionException("empty", iape);
                }

                byte[] plainBytes;

        try {
            plainBytes = c.doFinal(encryptedBytes,
                                                                   ivLen,
                                                                   encryptedBytes.length - ivLen);

        } catch (IllegalBlockSizeException ibse) {
            throw new XMLEncryptionException("empty", ibse);
        } catch (BadPaddingException bpe) {
            throw new XMLEncryptionException("empty", bpe);
        }

        return (plainBytes);
    }

        /*
         * Expose the interface for creating XML Encryption objects
         */

    /**
     * Creates an <code>EncryptedData</code> <code>Element</code>.
     *
         * The newEncryptedData and newEncryptedKey methods create fairly complete
         * elements that are immediately useable.  All the other create* methods
         * return bare elements that still need to be built upon.
         *<p>
         * An EncryptionMethod will still need to be added however
         *
         * @param type Either REFERENCE_TYPE or VALUE_TYPE - defines what kind of
         * CipherData this EncryptedData will contain.
     * @param value the Base 64 encoded, encrypted text to wrap in the
     *   <code>EncryptedData</code> or the URI to set in the CipherReference
         * (usage will depend on the <code>type</code>
     * @return the <code>EncryptedData</code> <code>Element</code>.
     *
     * <!--
     * <EncryptedData Id[OPT] Type[OPT] MimeType[OPT] Encoding[OPT]>
     *     <EncryptionMethod/>[OPT]
     *     <ds:KeyInfo>[OPT]
     *         <EncryptedKey/>[OPT]
     *         <AgreementMethod/>[OPT]
     *         <ds:KeyName/>[OPT]
     *         <ds:RetrievalMethod/>[OPT]
     *         <ds:[MUL]/>[OPT]
     *     </ds:KeyInfo>
     *     <CipherData>[MAN]
     *         <CipherValue/> XOR <CipherReference/>
     *     </CipherData>
     *     <EncryptionProperties/>[OPT]
     * </EncryptedData>
     * -->
     * @throws XMLEncryptionException
     */

    public EncryptedData createEncryptedData(int type, String value) throws
            XMLEncryptionException {
        EncryptedData result = null;
        CipherData data = null;

        switch (type) {
            case CipherData.REFERENCE_TYPE:
                CipherReference cipherReference = _factory.newCipherReference(
                    value);
                data = _factory.newCipherData(type);
                data.setCipherReference(cipherReference);
                result = _factory.newEncryptedData(data);
                                break;
            case CipherData.VALUE_TYPE:
                CipherValue cipherValue = _factory.newCipherValue(value);
                data = _factory.newCipherData(type);
                data.setCipherValue(cipherValue);
                result = _factory.newEncryptedData(data);
        }

        return (result);
    }

    /**
     * Creates an <code>EncryptedKey</code> <code>Element</code>.
     *
         * The newEncryptedData and newEncryptedKey methods create fairly complete
         * elements that are immediately useable.  All the other create* methods
         * return bare elements that still need to be built upon.
         *<p>
         * An EncryptionMethod will still need to be added however
         *
         * @param type Either REFERENCE_TYPE or VALUE_TYPE - defines what kind of
         * CipherData this EncryptedData will contain.
     * @param value the Base 64 encoded, encrypted text to wrap in the
     *   <code>EncryptedKey</code> or the URI to set in the CipherReference
         * (usage will depend on the <code>type</code>
     * @return the <code>EncryptedKey</code> <code>Element</code>.
     *
     * <!--
     * <EncryptedKey Id[OPT] Type[OPT] MimeType[OPT] Encoding[OPT]>
     *     <EncryptionMethod/>[OPT]
     *     <ds:KeyInfo>[OPT]
     *         <EncryptedKey/>[OPT]
     *         <AgreementMethod/>[OPT]
     *         <ds:KeyName/>[OPT]
     *         <ds:RetrievalMethod/>[OPT]
     *         <ds:[MUL]/>[OPT]
     *     </ds:KeyInfo>
     *     <CipherData>[MAN]
     *         <CipherValue/> XOR <CipherReference/>
     *     </CipherData>
     *     <EncryptionProperties/>[OPT]
     * </EncryptedData>
     * -->
     * @throws XMLEncryptionException
     */

    public EncryptedKey createEncryptedKey(int type, String value) throws
            XMLEncryptionException {
        EncryptedKey result = null;
        CipherData data = null;

        switch (type) {
            case CipherData.REFERENCE_TYPE:
                CipherReference cipherReference = _factory.newCipherReference(
                    value);
                data = _factory.newCipherData(type);
                data.setCipherReference(cipherReference);
                result = _factory.newEncryptedKey(data);
                                break;
            case CipherData.VALUE_TYPE:
                CipherValue cipherValue = _factory.newCipherValue(value);
                data = _factory.newCipherData(type);
                data.setCipherValue(cipherValue);
                result = _factory.newEncryptedKey(data);
        }

        return (result);
    }

        /**
         * Create an AgreementMethod object
         *
         * @param algorithm Algorithm of the agreement method
     * @return
         */

        public AgreementMethod createAgreementMethod(String algorithm) {
                return (_factory.newAgreementMethod(algorithm));
        }

        /**
         * Create a CipherData object
         *
         * @param type Type of this CipherData (either VALUE_TUPE or
         * REFERENCE_TYPE)
         * @return
         */

        public CipherData createCipherData(int type) {
                return (_factory.newCipherData(type));
        }

        /**
         * Create a CipherReference object
         *
     * @return
         * @param uri The URI that the reference will refer
         */

        public CipherReference createCipherReference(String uri) {
                return (_factory.newCipherReference(uri));
        }

        /**
         * Create a CipherValue element
         *
         * @param value The value to set the ciphertext to
     * @return
         */

        public CipherValue createCipherValue(String value) {
                return (_factory.newCipherValue(value));
        }

        /**
         * Create an EncryptedMethod object
         *
         * @param algorithm Algorithm for the encryption
     * @return
         */
        public EncryptionMethod createEncryptionMethod(String algorithm) {
                return (_factory.newEncryptionMethod(algorithm));
        }

        /**
         * Create an EncryptedProperties element
         * @return
         */
        public EncryptionProperties createEncryptionProperties() {
                return (_factory.newEncryptionProperties());
        }

        /**
         * Create a new EncryptionProperty element
     * @return
         */
        public EncryptionProperty createEncryptionProperty() {
                return (_factory.newEncryptionProperty());
        }

        /**
         * Create a new ReferenceList object
     * @return
     * @param type
         */
        public ReferenceList createReferenceList(int type) {
                return (_factory.newReferenceList(type));
        }

        /**
         * Create a new Transforms object
         * <p>
         * <b>Note</b>: A context document <i>must</i> have been set
         * elsewhere (possibly via a call to doFinal).  If not, use the
         * createTransforms(Document) method.
     * @return
         */

        public Transforms createTransforms() {
                return (_factory.newTransforms());
        }

        /**
         * Create a new Transforms object
         *
         * Because the handling of Transforms is currently done in the signature
         * code, the creation of a Transforms object <b>requires</b> a
         * context document.
         *
         * @param doc Document that will own the created Transforms node
     * @return
         */
        public Transforms createTransforms(Document doc) {
                return (_factory.newTransforms(doc));
        }

    /**
     * Converts <code>String</code>s into <code>Node</code>s and visa versa.
     * <p>
     * <b>NOTE:</b> For internal use only.
     *
     * @author  Axl Mattheus
     */

    private class Serializer {
        /**
         * Initialize the <code>XMLSerializer</code> with the specified context
         * <code>Document</code>.
         * <p/>
         * Setup OutputFormat in a way that the serialization does <b>not</b>
         * modifiy the contents, that is it shall not do any pretty printing
         * and so on. This would destroy the original content before
         * encryption. If that content was signed before encryption and the
         * serialization modifies the content the signature verification will
         * fail.
         */
        Serializer() {
        }

        /**
         * Returns a <code>String</code> representation of the specified
         * <code>Document</code>.
         * <p/>
         * Refer also to comments about setup of format.
         *
         * @param document the <code>Document</code> to serialize.
         * @return the <code>String</code> representation of the serilaized
         *   <code>Document</code>.
         * @throws Exception
         */
        String serialize(Document document) throws Exception {
            return canonSerialize(document);
        }

        /**
         * Returns a <code>String</code> representation of the specified
         * <code>Element</code>.
         * <p/>
         * Refer also to comments about setup of format.
         *
         * @param element the <code>Element</code> to serialize.
         * @return the <code>String</code> representation of the serilaized
         *   <code>Element</code>.
         * @throws Exception
         */
                String serialize(Element element) throws Exception {
            return canonSerialize(element);
                }

        /**
         * Returns a <code>String</code> representation of the specified
         * <code>NodeList</code>.
         * <p/>
         * This is a special case because the NodeList may represent a
         * <code>DocumentFragment</code>. A document fragement may be a
         * non-valid XML document (refer to appropriate description of
         * W3C) because it my start with a non-element node, e.g. a text
         * node.
         * <p/>
         * The methods first converts the node list into a document fragment.
         * Special care is taken to not destroy the current document, thus
         * the method clones the nodes (deep cloning) before it appends
         * them to the document fragment.
         * <p/>
         * Refer also to comments about setup of format.
         *
         * @param content the <code>NodeList</code> to serialize.
         * @return the <code>String</code> representation of the serilaized
         *   <code>NodeList</code>.
         * @throws Exception
         */
        String serialize(NodeList content) throws Exception { //XMLEncryptionException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            _canon.setWriter(baos);
            _canon.notReset();
            for (int i = 0; i < content.getLength(); i++) {
                _canon.canonicalizeSubtree(content.item(i));
            }
            baos.close();
            return baos.toString("UTF-8");
        }

        /**
         * Use the Canoncializer to serialize the node
         * @param node
         * @return
         * @throws Exception
         */
                String canonSerialize(Node node) throws Exception {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        _canon.setWriter(baos);
            _canon.notReset();
                        _canon.canonicalizeSubtree(node);
                        baos.close();
                        return baos.toString("UTF-8");
                }
        /**
         * @param source
         * @param ctx
         * @return
         * @throws XMLEncryptionException
         *
         */
        DocumentFragment deserialize(String source, Node ctx) throws XMLEncryptionException {
                        DocumentFragment result;
            final String tagname = "fragment";

                        // Create the context to parse the document against
                        StringBuffer sb;

                        sb = new StringBuffer();
                        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><"+tagname);

                        // Run through each node up to the document node and find any
                        // xmlns: nodes

                        Node wk = ctx;

                        while (wk != null) {

                                NamedNodeMap atts = wk.getAttributes();
                                int length;
                                if (atts != null)
                                        length = atts.getLength();
                                else
                                        length = 0;

                                for (int i = 0 ; i < length ; ++i) {
                                        Node att = atts.item(i);
                                        if (att.getNodeName().startsWith("xmlns:") ||
                                                att.getNodeName().equals("xmlns")) {

                                                // Check to see if this node has already been found
                                                Node p = ctx;
                                                boolean found = false;
                                                while (p != wk) {
                                                        NamedNodeMap tstAtts = p.getAttributes();
                                                        if (tstAtts != null &&
                                                                tstAtts.getNamedItem(att.getNodeName()) != null) {
                                                                found = true;
                                                                break;
                                                        }
                                                        p = p.getParentNode();
                                                }
                                                if (found == false) {

                                                        // This is an attribute node
                                                        sb.append(" " + att.getNodeName() + "=\"" +
                                                                          att.getNodeValue() + "\"");
                                                }
                                        }
                                }
                                wk = wk.getParentNode();
                        }
                        sb.append(">" + source + "</" + tagname + ">");
                        String fragment = sb.toString();

            try {
                DocumentBuilderFactory dbf =
                    DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
                dbf.setAttribute("http://xml.org/sax/features/namespaces", Boolean.TRUE);
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document d = db.parse(
                    new InputSource(new StringReader(fragment)));

                Element fragElt = (Element) _contextDocument.importNode(
                                                 d.getDocumentElement(), true);
                result = _contextDocument.createDocumentFragment();
                Node child = fragElt.getFirstChild();
                while (child != null) {
                    fragElt.removeChild(child);
                    result.appendChild(child);
                    child = fragElt.getFirstChild();
                }
                // String outp = serialize(d);

            } catch (SAXException se) {
                throw new XMLEncryptionException("empty", se);
            } catch (ParserConfigurationException pce) {
                throw new XMLEncryptionException("empty", pce);
            } catch (IOException ioe) {
                throw new XMLEncryptionException("empty", ioe);
            }

            return (result);
        }
    }


    /**
     *
     * @author Axl Mattheus
     */
    private class Factory {
        /**
         * @param algorithm
         * @return
         *
         */
        AgreementMethod newAgreementMethod(String algorithm)  {
            return (new AgreementMethodImpl(algorithm));
        }

        /**
         * @param type
         * @return
         *
         */
        CipherData newCipherData(int type) {
            return (new CipherDataImpl(type));
        }

        /**
         * @param uri
         * @return
         *
         */
        CipherReference newCipherReference(String uri)  {
            return (new CipherReferenceImpl(uri));
        }

        /**
         * @param value
         * @return
         *
         */
        CipherValue newCipherValue(String value) {
            return (new CipherValueImpl(value));
        }

        /**
         *

        CipherValue newCipherValue(byte[] value) {
            return (new CipherValueImpl(value));
        }
                */
        /**
         * @param data
         * @return
         *
         */
        EncryptedData newEncryptedData(CipherData data) {
            return (new EncryptedDataImpl(data));
        }

        /**
         * @param data
         * @return
         *
         */
        EncryptedKey newEncryptedKey(CipherData data) {
            return (new EncryptedKeyImpl(data));
        }

        /**
         * @param algorithm
         * @return
         *
         */
        EncryptionMethod newEncryptionMethod(String algorithm) {
            return (new EncryptionMethodImpl(algorithm));
        }

        /**
         * @return
         *
         */
        EncryptionProperties newEncryptionProperties() {
            return (new EncryptionPropertiesImpl());
        }

        /**
         * @return
         *
         */
        EncryptionProperty newEncryptionProperty() {
            return (new EncryptionPropertyImpl());
        }

        /**
         * @param type
         * @return
         *
         */
        ReferenceList newReferenceList(int type) {
            return (new ReferenceListImpl(type));
        }

        /**
         * @return
         *
         */
        Transforms newTransforms() {
            return (new TransformsImpl());
        }

        /**
         * @param doc
         * @return
         *
         */
        Transforms newTransforms(Document doc) {
            return (new TransformsImpl(doc));
        }

        /**
         * @param element
         * @return
         * @throws XMLEncryptionException
         *
         */
        // <element name="AgreementMethod" type="xenc:AgreementMethodType"/>
        // <complexType name="AgreementMethodType" mixed="true">
        //     <sequence>
        //         <element name="KA-Nonce" minOccurs="0" type="base64Binary"/>
        //         <!-- <element ref="ds:DigestMethod" minOccurs="0"/> -->
        //         <any namespace="##other" minOccurs="0" maxOccurs="unbounded"/>
        //         <element name="OriginatorKeyInfo" minOccurs="0" type="ds:KeyInfoType"/>
        //         <element name="RecipientKeyInfo" minOccurs="0" type="ds:KeyInfoType"/>
        //     </sequence>
        //     <attribute name="Algorithm" type="anyURI" use="required"/>
        // </complexType>
        AgreementMethod newAgreementMethod(Element element) throws
                XMLEncryptionException {
            if (null == element) {
                throw new NullPointerException("element is null");
            }

            String algorithm = element.getAttributeNS(null,
                EncryptionConstants._ATT_ALGORITHM);
            AgreementMethod result = newAgreementMethod(algorithm);

            Element kaNonceElement = (Element) element.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS,
                EncryptionConstants._TAG_KA_NONCE).item(0);
            if (null != kaNonceElement) {
                result.setKANonce(kaNonceElement.getNodeValue().getBytes());
            }
            // TODO: ///////////////////////////////////////////////////////////
            // Figure out how to make this pesky line work..
            // <any namespace="##other" minOccurs="0" maxOccurs="unbounded"/>

            // TODO: Work out how to handle relative URI

            Element originatorKeyInfoElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ORIGINATORKEYINFO).item(0);
            if (null != originatorKeyInfoElement) {
                try {
                    result.setOriginatorKeyInfo(
                        new KeyInfo(originatorKeyInfoElement, null));
                } catch (XMLSecurityException xse) {
                    throw new XMLEncryptionException("empty", xse);
                }
            }

            // TODO: Work out how to handle relative URI

            Element recipientKeyInfoElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_RECIPIENTKEYINFO).item(0);
            if (null != recipientKeyInfoElement) {
                try {
                    result.setRecipientKeyInfo(
                        new KeyInfo(recipientKeyInfoElement, null));
                } catch (XMLSecurityException xse) {
                    throw new XMLEncryptionException("empty", xse);
                }
            }

            return (result);
        }

        /**
         * @param element
         * @return
         * @throws XMLEncryptionException
         *
         */
        // <element name='CipherData' type='xenc:CipherDataType'/>
        // <complexType name='CipherDataType'>
        //     <choice>
        //         <element name='CipherValue' type='base64Binary'/>
        //         <element ref='xenc:CipherReference'/>
        //     </choice>
        // </complexType>
        CipherData newCipherData(Element element) throws
                XMLEncryptionException {
            if (null == element) {
                throw new NullPointerException("element is null");
            }

            int type = 0;
            Element e = null;
            if (element.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS,
                EncryptionConstants._TAG_CIPHERVALUE).getLength() > 0) {
                type = CipherData.VALUE_TYPE;
                e = (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_CIPHERVALUE).item(0);
            } else if (element.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS,
                EncryptionConstants._TAG_CIPHERREFERENCE).getLength() > 0) {
                type = CipherData.REFERENCE_TYPE;
                e = (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_CIPHERREFERENCE).item(0);
            }

            CipherData result = newCipherData(type);
            if (type == CipherData.VALUE_TYPE) {
                result.setCipherValue(newCipherValue(e));
            } else if (type == CipherData.REFERENCE_TYPE) {
                result.setCipherReference(newCipherReference(e));
            }

            return (result);
        }

        /**
         * @param element
         * @return
         * @throws XMLEncryptionException
         *
         */
        // <element name='CipherReference' type='xenc:CipherReferenceType'/>
        // <complexType name='CipherReferenceType'>
        //     <sequence>
        //         <element name='Transforms' type='xenc:TransformsType' minOccurs='0'/>
        //     </sequence>
        //     <attribute name='URI' type='anyURI' use='required'/>
        // </complexType>
        CipherReference newCipherReference(Element element) throws
                XMLEncryptionException {

                        Attr URIAttr =
                                element.getAttributeNodeNS(null, EncryptionConstants._ATT_URI);
                        CipherReference result = new CipherReferenceImpl(URIAttr);

                        // Find any Transforms

                        NodeList transformsElements = element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_TRANSFORMS);
            Element transformsElement =
                                (Element) transformsElements.item(0);

                        if (transformsElement != null) {
                                logger.log(java.util.logging.Level.FINE, "Creating a DSIG based Transforms element");
                                try {
                                        result.setTransforms(new TransformsImpl(transformsElement));
                                }
                                catch (XMLSignatureException xse) {
                                        throw new XMLEncryptionException("empty", xse);
                                } catch (InvalidTransformException ite) {
                                        throw new XMLEncryptionException("empty", ite);
                                } catch (XMLSecurityException xse) {
                                        throw new XMLEncryptionException("empty", xse);
                                }

                        }

                        return result;
        }

        /**
         * @param element
         * @return
         *
         */
        CipherValue newCipherValue(Element element) {
            String value = XMLUtils.getFullTextChildrenFromElement(element);

            CipherValue result = newCipherValue(value);

            return (result);
        }

        /**
         * @param element
         * @return
         * @throws XMLEncryptionException
         *
         */
        // <complexType name='EncryptedType' abstract='true'>
        //     <sequence>
        //         <element name='EncryptionMethod' type='xenc:EncryptionMethodType'
        //             minOccurs='0'/>
        //         <element ref='ds:KeyInfo' minOccurs='0'/>
        //         <element ref='xenc:CipherData'/>
        //         <element ref='xenc:EncryptionProperties' minOccurs='0'/>
        //     </sequence>
        //     <attribute name='Id' type='ID' use='optional'/>
        //     <attribute name='Type' type='anyURI' use='optional'/>
        //     <attribute name='MimeType' type='string' use='optional'/>
        //     <attribute name='Encoding' type='anyURI' use='optional'/>
        // </complexType>
        // <element name='EncryptedData' type='xenc:EncryptedDataType'/>
        // <complexType name='EncryptedDataType'>
        //     <complexContent>
        //         <extension base='xenc:EncryptedType'/>
        //     </complexContent>
        // </complexType>
        EncryptedData newEncryptedData(Element element) throws
                        XMLEncryptionException {
            EncryptedData result = null;

            NodeList dataElements = element.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS,
                EncryptionConstants._TAG_CIPHERDATA);

            // Need to get the last CipherData found, as earlier ones will
            // be for elements in the KeyInfo lists

            Element dataElement =
                (Element) dataElements.item(dataElements.getLength() - 1);

            CipherData data = newCipherData(dataElement);

            result = newEncryptedData(data);

            result.setId(element.getAttributeNS(
                null, EncryptionConstants._ATT_ID));
            result.setType(
                element.getAttributeNS(null, EncryptionConstants._ATT_TYPE));
            result.setMimeType(element.getAttributeNS(
                null, EncryptionConstants._ATT_MIMETYPE));
            result.setEncoding(
                element.getAttributeNS(null, Constants._ATT_ENCODING));

            Element encryptionMethodElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTIONMETHOD).item(0);
            if (null != encryptionMethodElement) {
                result.setEncryptionMethod(newEncryptionMethod(
                    encryptionMethodElement));
            }

            // BFL 16/7/03 - simple implementation
            // TODO: Work out how to handle relative URI

            Element keyInfoElement =
                (Element) element.getElementsByTagNameNS(
                    Constants.SignatureSpecNS, Constants._TAG_KEYINFO).item(0);
            if (null != keyInfoElement) {
                try {
                    result.setKeyInfo(new KeyInfo(keyInfoElement, null));
                } catch (XMLSecurityException xse) {
                    throw new XMLEncryptionException("Error loading Key Info",
                                                     xse);
                }
            }

            // TODO: Implement
            Element encryptionPropertiesElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTIONPROPERTIES).item(0);
            if (null != encryptionPropertiesElement) {
                result.setEncryptionProperties(
                    newEncryptionProperties(encryptionPropertiesElement));
            }

            return (result);
        }

        /**
         * @param element
         * @return
         * @throws XMLEncryptionException
         *
         */
        // <complexType name='EncryptedType' abstract='true'>
        //     <sequence>
        //         <element name='EncryptionMethod' type='xenc:EncryptionMethodType'
        //             minOccurs='0'/>
        //         <element ref='ds:KeyInfo' minOccurs='0'/>
        //         <element ref='xenc:CipherData'/>
        //         <element ref='xenc:EncryptionProperties' minOccurs='0'/>
        //     </sequence>
        //     <attribute name='Id' type='ID' use='optional'/>
        //     <attribute name='Type' type='anyURI' use='optional'/>
        //     <attribute name='MimeType' type='string' use='optional'/>
        //     <attribute name='Encoding' type='anyURI' use='optional'/>
        // </complexType>
        // <element name='EncryptedKey' type='xenc:EncryptedKeyType'/>
        // <complexType name='EncryptedKeyType'>
        //     <complexContent>
        //         <extension base='xenc:EncryptedType'>
        //             <sequence>
        //                 <element ref='xenc:ReferenceList' minOccurs='0'/>
        //                 <element name='CarriedKeyName' type='string' minOccurs='0'/>
        //             </sequence>
        //             <attribute name='Recipient' type='string' use='optional'/>
        //         </extension>
        //     </complexContent>
        // </complexType>
        EncryptedKey newEncryptedKey(Element element) throws
                XMLEncryptionException {
            EncryptedKey result = null;
            NodeList dataElements = element.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS,
                EncryptionConstants._TAG_CIPHERDATA);
            Element dataElement =
                (Element) dataElements.item(dataElements.getLength() - 1);

            CipherData data = newCipherData(dataElement);
            result = newEncryptedKey(data);

            result.setId(element.getAttributeNS(
                null, EncryptionConstants._ATT_ID));
            result.setType(
                element.getAttributeNS(null, EncryptionConstants._ATT_TYPE));
            result.setMimeType(element.getAttributeNS(
                null, EncryptionConstants._ATT_MIMETYPE));
            result.setEncoding(
                element.getAttributeNS(null, Constants._ATT_ENCODING));
            result.setRecipient(element.getAttributeNS(
                null, EncryptionConstants._ATT_RECIPIENT));

            Element encryptionMethodElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTIONMETHOD).item(0);
            if (null != encryptionMethodElement) {
                result.setEncryptionMethod(newEncryptionMethod(
                    encryptionMethodElement));
            }

            Element keyInfoElement =
                (Element) element.getElementsByTagNameNS(
                    Constants.SignatureSpecNS, Constants._TAG_KEYINFO).item(0);
            if (null != keyInfoElement) {
                try {
                    result.setKeyInfo(new KeyInfo(keyInfoElement, null));
                } catch (XMLSecurityException xse) {
                    throw new XMLEncryptionException
                        ("Error loading Key Info", xse);
                }
            }

            // TODO: Implement
            Element encryptionPropertiesElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTIONPROPERTIES).item(0);
            if (null != encryptionPropertiesElement) {
                result.setEncryptionProperties(
                    newEncryptionProperties(encryptionPropertiesElement));
            }

            Element referenceListElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_REFERENCELIST).item(0);
            if (null != referenceListElement) {
                result.setReferenceList(newReferenceList(referenceListElement));
            }

            Element carriedNameElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_CARRIEDKEYNAME).item(0);
            if (null != carriedNameElement) {
                result.setCarriedName
                    (carriedNameElement.getFirstChild().getNodeValue());
            }

            return (result);
        }

        /**
         * @param element
         * @return
         *
         */
        // <complexType name='EncryptionMethodType' mixed='true'>
        //     <sequence>
        //         <element name='KeySize' minOccurs='0' type='xenc:KeySizeType'/>
        //         <element name='OAEPparams' minOccurs='0' type='base64Binary'/>
        //         <any namespace='##other' minOccurs='0' maxOccurs='unbounded'/>
        //     </sequence>
        //     <attribute name='Algorithm' type='anyURI' use='required'/>
        // </complexType>
        EncryptionMethod newEncryptionMethod(Element element) {
            String algorithm = element.getAttributeNS(
                null, EncryptionConstants._ATT_ALGORITHM);
            EncryptionMethod result = newEncryptionMethod(algorithm);

            Element keySizeElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_KEYSIZE).item(0);
            if (null != keySizeElement) {
                result.setKeySize(
                    Integer.valueOf(
                        keySizeElement.getFirstChild().getNodeValue()).intValue());
            }

            Element oaepParamsElement =
                (Element) element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_OAEPPARAMS).item(0);
            if (null != oaepParamsElement) {
                result.setOAEPparams(
                    oaepParamsElement.getNodeValue().getBytes());
            }

            // TODO: Make this mess work
            // <any namespace='##other' minOccurs='0' maxOccurs='unbounded'/>

            return (result);
        }

        /**
         * @param element
         * @return
         *
         */
        // <element name='EncryptionProperties' type='xenc:EncryptionPropertiesType'/>
        // <complexType name='EncryptionPropertiesType'>
        //     <sequence>
        //         <element ref='xenc:EncryptionProperty' maxOccurs='unbounded'/>
        //     </sequence>
        //     <attribute name='Id' type='ID' use='optional'/>
        // </complexType>
        EncryptionProperties newEncryptionProperties(Element element) {
            EncryptionProperties result = newEncryptionProperties();

            result.setId(element.getAttributeNS(
                null, EncryptionConstants._ATT_ID));

            NodeList encryptionPropertyList =
                element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTIONPROPERTY);
            for(int i = 0; i < encryptionPropertyList.getLength(); i++) {
                Node n = encryptionPropertyList.item(i);
                if (null != n) {
                    result.addEncryptionProperty(
                        newEncryptionProperty((Element) n));
                }
            }

            return (result);
        }

        /**
         * @param element
         * @return
         *
         */
        // <element name='EncryptionProperty' type='xenc:EncryptionPropertyType'/>
        // <complexType name='EncryptionPropertyType' mixed='true'>
        //     <choice maxOccurs='unbounded'>
        //         <any namespace='##other' processContents='lax'/>
        //     </choice>
        //     <attribute name='Target' type='anyURI' use='optional'/>
        //     <attribute name='Id' type='ID' use='optional'/>
        //     <anyAttribute namespace="http://www.w3.org/XML/1998/namespace"/>
        // </complexType>
        EncryptionProperty newEncryptionProperty(Element element) {
            EncryptionProperty result = newEncryptionProperty();

            result.setTarget(
                element.getAttributeNS(null, EncryptionConstants._ATT_TARGET));
            result.setId(element.getAttributeNS(
                null, EncryptionConstants._ATT_ID));
            // TODO: Make this lot work...
            // <anyAttribute namespace="http://www.w3.org/XML/1998/namespace"/>

            // TODO: Make this work...
            // <any namespace='##other' processContents='lax'/>

            return (result);
        }

        /**
         * @param element
         * @return
         *
         */
        // <element name='ReferenceList'>
        //     <complexType>
        //         <choice minOccurs='1' maxOccurs='unbounded'>
        //             <element name='DataReference' type='xenc:ReferenceType'/>
        //             <element name='KeyReference' type='xenc:ReferenceType'/>
        //         </choice>
        //     </complexType>
        // </element>
        ReferenceList newReferenceList(Element element) {
            int type = 0;
            if (null != element.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS,
                EncryptionConstants._TAG_DATAREFERENCE).item(0)) {
                type = ReferenceList.DATA_REFERENCE;
            } else if (null != element.getElementsByTagNameNS(
                EncryptionConstants.EncryptionSpecNS,
                EncryptionConstants._TAG_KEYREFERENCE).item(0)) {
                type = ReferenceList.KEY_REFERENCE;
            } else {
                // complain
            }

            ReferenceList result = new ReferenceListImpl(type);
            NodeList list = null;
            switch (type) {
            case ReferenceList.DATA_REFERENCE:
                list = element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_DATAREFERENCE);
                for (int i = 0; i < list.getLength() ; i++) {
                    String uri = ((Element) list.item(i)).getAttribute("URI");
                    result.add(result.newDataReference(uri));
                }
                break;
            case ReferenceList.KEY_REFERENCE:
                list = element.getElementsByTagNameNS(
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_KEYREFERENCE);
                for (int i = 0; i < list.getLength() ; i++) {
                    String uri = ((Element) list.item(i)).getAttribute("URI");
                    result.add(result.newKeyReference(uri));
                }
            }

            return (result);
        }

        /**
         * @param element
         * @return
         *
         */
        Transforms newTransforms(Element element) {
            return (null);
        }

        /**
         * @param agreementMethod
         * @return
         *
         */
        Element toElement(AgreementMethod agreementMethod) {
            return ((AgreementMethodImpl) agreementMethod).toElement();
        }

        /**
         * @param cipherData
         * @return
         *
         */
        Element toElement(CipherData cipherData) {
            return ((CipherDataImpl) cipherData).toElement();
        }

        /**
         * @param cipherReference
         * @return
         *
         */
        Element toElement(CipherReference cipherReference) {
            return ((CipherReferenceImpl) cipherReference).toElement();
        }

        /**
         * @param cipherValue
         * @return
         *
         */
        Element toElement(CipherValue cipherValue) {
            return ((CipherValueImpl) cipherValue).toElement();
        }

        /**
         * @param encryptedData
         * @return
         *
         */
        Element toElement(EncryptedData encryptedData) {
            return ((EncryptedDataImpl) encryptedData).toElement();
        }

        /**
         * @param encryptedKey
         * @return
         *
         */
        Element toElement(EncryptedKey encryptedKey) {
            return ((EncryptedKeyImpl) encryptedKey).toElement();
        }

        /**
         * @param encryptionMethod
         * @return
         *
         */
        Element toElement(EncryptionMethod encryptionMethod) {
            return ((EncryptionMethodImpl) encryptionMethod).toElement();
        }

        /**
         * @param encryptionProperties
         * @return
         *
         */
        Element toElement(EncryptionProperties encryptionProperties) {
            return ((EncryptionPropertiesImpl) encryptionProperties).toElement();
        }

        /**
         * @param encryptionProperty
         * @return
         *
         */
        Element toElement(EncryptionProperty encryptionProperty) {
            return ((EncryptionPropertyImpl) encryptionProperty).toElement();
        }

        Element toElement(ReferenceList referenceList) {
            return ((ReferenceListImpl) referenceList).toElement();
        }

        /**
         * @param transforms
         * @return
         *
         */
        Element toElement(Transforms transforms) {
            return ((TransformsImpl) transforms).toElement();
        }

        // <element name="AgreementMethod" type="xenc:AgreementMethodType"/>
        // <complexType name="AgreementMethodType" mixed="true">
        //     <sequence>
        //         <element name="KA-Nonce" minOccurs="0" type="base64Binary"/>
        //         <!-- <element ref="ds:DigestMethod" minOccurs="0"/> -->
        //         <any namespace="##other" minOccurs="0" maxOccurs="unbounded"/>
        //         <element name="OriginatorKeyInfo" minOccurs="0" type="ds:KeyInfoType"/>
        //         <element name="RecipientKeyInfo" minOccurs="0" type="ds:KeyInfoType"/>
        //     </sequence>
        //     <attribute name="Algorithm" type="anyURI" use="required"/>
        // </complexType>
        private class AgreementMethodImpl implements AgreementMethod {
            private byte[] kaNonce = null;
            private List<Element> agreementMethodInformation = null;
            private KeyInfo originatorKeyInfo = null;
            private KeyInfo recipientKeyInfo = null;
            private String algorithmURI = null;

            /**
             * @param algorithm
             */
            public AgreementMethodImpl(String algorithm) {
                agreementMethodInformation = new LinkedList<Element>();
                URI tmpAlgorithm = null;
                try {
                    tmpAlgorithm = new URI(algorithm);
                } catch (URI.MalformedURIException fmue) {
                    //complain?
                }
                algorithmURI = tmpAlgorithm.toString();
            }

            /** @inheritDoc */
            public byte[] getKANonce() {
                return (kaNonce);
            }

            /** @inheritDoc */
            public void setKANonce(byte[] kanonce) {
                kaNonce = kanonce;
            }

            /** @inheritDoc */
            public Iterator<Element> getAgreementMethodInformation() {
                return (agreementMethodInformation.iterator());
            }

            /** @inheritDoc */
            public void addAgreementMethodInformation(Element info) {
                agreementMethodInformation.add(info);
            }

            /** @inheritDoc */
            public void revoveAgreementMethodInformation(Element info) {
                agreementMethodInformation.remove(info);
            }

            /** @inheritDoc */
            public KeyInfo getOriginatorKeyInfo() {
                return (originatorKeyInfo);
            }

            /** @inheritDoc */
            public void setOriginatorKeyInfo(KeyInfo keyInfo) {
                originatorKeyInfo = keyInfo;
            }

            /** @inheritDoc */
            public KeyInfo getRecipientKeyInfo() {
                return (recipientKeyInfo);
            }

            /** @inheritDoc */
            public void setRecipientKeyInfo(KeyInfo keyInfo) {
                recipientKeyInfo = keyInfo;
            }

            /** @inheritDoc */
            public String getAlgorithm() {
                return (algorithmURI);
            }

            /** @param algorithm*/
            public void setAlgorithm(String algorithm) {
                URI tmpAlgorithm = null;
                try {
                    tmpAlgorithm = new URI(algorithm);
                } catch (URI.MalformedURIException mfue) {
                    //complain
                }
                algorithmURI = tmpAlgorithm.toString();
            }

            // <element name="AgreementMethod" type="xenc:AgreementMethodType"/>
            // <complexType name="AgreementMethodType" mixed="true">
            //     <sequence>
            //         <element name="KA-Nonce" minOccurs="0" type="base64Binary"/>
            //         <!-- <element ref="ds:DigestMethod" minOccurs="0"/> -->
            //         <any namespace="##other" minOccurs="0" maxOccurs="unbounded"/>
            //         <element name="OriginatorKeyInfo" minOccurs="0" type="ds:KeyInfoType"/>
            //         <element name="RecipientKeyInfo" minOccurs="0" type="ds:KeyInfoType"/>
            //     </sequence>
            //     <attribute name="Algorithm" type="anyURI" use="required"/>
            // </complexType>
            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument,
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_AGREEMENTMETHOD);
                result.setAttributeNS(
                    null, EncryptionConstants._ATT_ALGORITHM, algorithmURI);
                if (null != kaNonce) {
                    result.appendChild(
                        ElementProxy.createElementForFamily(
                            _contextDocument,
                            EncryptionConstants.EncryptionSpecNS,
                            EncryptionConstants._TAG_KA_NONCE)).appendChild(
                            _contextDocument.createTextNode(new String(kaNonce)));
                }
                if (!agreementMethodInformation.isEmpty()) {
                    Iterator<Element> itr = agreementMethodInformation.iterator();
                    while (itr.hasNext()) {
                        result.appendChild(itr.next());
                    }
                }
                if (null != originatorKeyInfo) {
                    result.appendChild(originatorKeyInfo.getElement());
                }
                if (null != recipientKeyInfo) {
                    result.appendChild(recipientKeyInfo.getElement());
                }

                return (result);
            }
        }

        // <element name='CipherData' type='xenc:CipherDataType'/>
        // <complexType name='CipherDataType'>
        //     <choice>
        //         <element name='CipherValue' type='base64Binary'/>
        //         <element ref='xenc:CipherReference'/>
        //     </choice>
        // </complexType>
        private class CipherDataImpl implements CipherData {
            private static final String valueMessage =
                "Data type is reference type.";
            private static final String referenceMessage =
                "Data type is value type.";
            private CipherValue cipherValue = null;
            private CipherReference cipherReference = null;
            private int cipherType = Integer.MIN_VALUE;

            /**
             * @param type
             */
            public CipherDataImpl(int type) {
                cipherType = type;
            }

            /** @inheritDoc */
            public CipherValue getCipherValue() {
                return (cipherValue);
            }

            /** @inheritDoc */
            public void setCipherValue(CipherValue value) throws
                    XMLEncryptionException {

                if (cipherType == REFERENCE_TYPE) {
                    throw new XMLEncryptionException("empty",
                        new UnsupportedOperationException(valueMessage));
                }

                cipherValue = value;
            }

            /** @inheritDoc */
            public CipherReference getCipherReference() {
                return (cipherReference);
            }

            /** @inheritDoc */
            public void setCipherReference(CipherReference reference) throws
                    XMLEncryptionException {
                if (cipherType == VALUE_TYPE) {
                    throw new XMLEncryptionException("empty",
                        new UnsupportedOperationException(referenceMessage));
                }

                cipherReference = reference;
            }

            /** @inheritDoc */
            public int getDataType() {
                return (cipherType);
            }

            // <element name='CipherData' type='xenc:CipherDataType'/>
            // <complexType name='CipherDataType'>
            //     <choice>
            //         <element name='CipherValue' type='base64Binary'/>
            //         <element ref='xenc:CipherReference'/>
            //     </choice>
            // </complexType>
            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument,
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_CIPHERDATA);
                if (cipherType == VALUE_TYPE) {
                    result.appendChild(
                        ((CipherValueImpl) cipherValue).toElement());
                } else if (cipherType == REFERENCE_TYPE) {
                    result.appendChild(
                        ((CipherReferenceImpl) cipherReference).toElement());
                } else {
                    // complain
                }

                return (result);
            }
        }

        // <element name='CipherReference' type='xenc:CipherReferenceType'/>
        // <complexType name='CipherReferenceType'>
        //     <sequence>
        //         <element name='Transforms' type='xenc:TransformsType' minOccurs='0'/>
        //     </sequence>
        //     <attribute name='URI' type='anyURI' use='required'/>
        // </complexType>
        private class CipherReferenceImpl implements CipherReference {
            private String referenceURI = null;
            private Transforms referenceTransforms = null;
                        private Attr referenceNode = null;

            /**
             * @param uri
             */
            public CipherReferenceImpl(String uri) {
                                /* Don't check validity of URI as may be "" */
                referenceURI = uri;
                                referenceNode = null;
            }

                        /**
                         * @param uri
                         */
                        public CipherReferenceImpl(Attr uri) {
                                referenceURI = uri.getNodeValue();
                                referenceNode = uri;
                        }

            /** @inheritDoc */
            public String getURI() {
                return (referenceURI);
            }

            /** @inheritDoc */
                        public Attr getURIAsAttr() {
                                return (referenceNode);
                        }

            /** @inheritDoc */
            public Transforms getTransforms() {
                return (referenceTransforms);
            }

            /** @inheritDoc */
            public void setTransforms(Transforms transforms) {
                referenceTransforms = transforms;
            }

            // <element name='CipherReference' type='xenc:CipherReferenceType'/>
            // <complexType name='CipherReferenceType'>
            //     <sequence>
            //         <element name='Transforms' type='xenc:TransformsType' minOccurs='0'/>
            //     </sequence>
            //     <attribute name='URI' type='anyURI' use='required'/>
            // </complexType>
            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument,
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_CIPHERREFERENCE);
                result.setAttributeNS(
                    null, EncryptionConstants._ATT_URI, referenceURI);
                if (null != referenceTransforms) {
                    result.appendChild(
                        ((TransformsImpl) referenceTransforms).toElement());
                }

                return (result);
            }
        }

        private class CipherValueImpl implements CipherValue {
                        private String cipherValue = null;

            // public CipherValueImpl(byte[] value) {
               // cipherValue = value;
            // }

            /**
             * @param value
             */
            public CipherValueImpl(String value) {
                                // cipherValue = value.getBytes();
                                cipherValue = value;
            }

            /** @inheritDoc */
                        public String getValue() {
                return (cipherValue);
            }

                        // public void setValue(byte[] value) {
                        // public void setValue(String value) {
               // cipherValue = value;
            // }
                        /** @inheritDoc */
            public void setValue(String value) {
                // cipherValue = value.getBytes();
                                cipherValue = value;
            }

            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument, EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_CIPHERVALUE);
                result.appendChild(_contextDocument.createTextNode(
                    cipherValue));

                return (result);
            }
        }

        // <complexType name='EncryptedType' abstract='true'>
        //     <sequence>
        //         <element name='EncryptionMethod' type='xenc:EncryptionMethodType'
        //             minOccurs='0'/>
        //         <element ref='ds:KeyInfo' minOccurs='0'/>
        //         <element ref='xenc:CipherData'/>
        //         <element ref='xenc:EncryptionProperties' minOccurs='0'/>
        //     </sequence>
        //     <attribute name='Id' type='ID' use='optional'/>
        //     <attribute name='Type' type='anyURI' use='optional'/>
        //     <attribute name='MimeType' type='string' use='optional'/>
        //     <attribute name='Encoding' type='anyURI' use='optional'/>
        // </complexType>
        // <element name='EncryptedData' type='xenc:EncryptedDataType'/>
        // <complexType name='EncryptedDataType'>
        //     <complexContent>
        //         <extension base='xenc:EncryptedType'/>
        //     </complexContent>
        // </complexType>
        private class EncryptedDataImpl extends EncryptedTypeImpl implements
                EncryptedData {
            /**
             * @param data
             */
            public EncryptedDataImpl(CipherData data) {
                super(data);
            }

            // <complexType name='EncryptedType' abstract='true'>
            //     <sequence>
            //         <element name='EncryptionMethod' type='xenc:EncryptionMethodType'
            //             minOccurs='0'/>
            //         <element ref='ds:KeyInfo' minOccurs='0'/>
            //         <element ref='xenc:CipherData'/>
            //         <element ref='xenc:EncryptionProperties' minOccurs='0'/>
            //     </sequence>
            //     <attribute name='Id' type='ID' use='optional'/>
            //     <attribute name='Type' type='anyURI' use='optional'/>
            //     <attribute name='MimeType' type='string' use='optional'/>
            //     <attribute name='Encoding' type='anyURI' use='optional'/>
            // </complexType>
            // <element name='EncryptedData' type='xenc:EncryptedDataType'/>
            // <complexType name='EncryptedDataType'>
            //     <complexContent>
            //         <extension base='xenc:EncryptedType'/>
            //     </complexContent>
            // </complexType>
            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument, EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTEDDATA);

                if (null != super.getId()) {
                    result.setAttributeNS(
                        null, EncryptionConstants._ATT_ID, super.getId());
                }
                if (null != super.getType()) {
                    result.setAttributeNS(
                        null, EncryptionConstants._ATT_TYPE, super.getType());
                }
                if (null != super.getMimeType()) {
                    result.setAttributeNS(
                        null, EncryptionConstants._ATT_MIMETYPE,
                        super.getMimeType());
                }
                if (null != super.getEncoding()) {
                    result.setAttributeNS(
                        null, EncryptionConstants._ATT_ENCODING,
                        super.getEncoding());
                }
                if (null != super.getEncryptionMethod()) {
                    result.appendChild(((EncryptionMethodImpl)
                        super.getEncryptionMethod()).toElement());
                }
                if (null != super.getKeyInfo()) {
                    result.appendChild(super.getKeyInfo().getElement());
                }

                result.appendChild(
                    ((CipherDataImpl) super.getCipherData()).toElement());
                if (null != super.getEncryptionProperties()) {
                    result.appendChild(((EncryptionPropertiesImpl)
                        super.getEncryptionProperties()).toElement());
                }

                return (result);
            }
        }

        // <complexType name='EncryptedType' abstract='true'>
        //     <sequence>
        //         <element name='EncryptionMethod' type='xenc:EncryptionMethodType'
        //             minOccurs='0'/>
        //         <element ref='ds:KeyInfo' minOccurs='0'/>
        //         <element ref='xenc:CipherData'/>
        //         <element ref='xenc:EncryptionProperties' minOccurs='0'/>
        //     </sequence>
        //     <attribute name='Id' type='ID' use='optional'/>
        //     <attribute name='Type' type='anyURI' use='optional'/>
        //     <attribute name='MimeType' type='string' use='optional'/>
        //     <attribute name='Encoding' type='anyURI' use='optional'/>
        // </complexType>
        // <element name='EncryptedKey' type='xenc:EncryptedKeyType'/>
        // <complexType name='EncryptedKeyType'>
        //     <complexContent>
        //         <extension base='xenc:EncryptedType'>
        //             <sequence>
        //                 <element ref='xenc:ReferenceList' minOccurs='0'/>
        //                 <element name='CarriedKeyName' type='string' minOccurs='0'/>
        //             </sequence>
        //             <attribute name='Recipient' type='string' use='optional'/>
        //         </extension>
        //     </complexContent>
        // </complexType>
        private class EncryptedKeyImpl extends EncryptedTypeImpl implements
                EncryptedKey {
            private String keyRecipient = null;
            private ReferenceList referenceList = null;
            private String carriedName = null;

            /**
             * @param data
             */
            public EncryptedKeyImpl(CipherData data) {
                super(data);
            }

            /** @inheritDoc */
            public String getRecipient() {
                return (keyRecipient);
            }

            /** @inheritDoc */
            public void setRecipient(String recipient) {
                keyRecipient = recipient;
            }

            /** @inheritDoc */
            public ReferenceList getReferenceList() {
                return (referenceList);
            }

            /** @inheritDoc */
            public void setReferenceList(ReferenceList list) {
                referenceList = list;
            }

            /** @inheritDoc */
            public String getCarriedName() {
                return (carriedName);
            }

            /** @inheritDoc */
            public void setCarriedName(String name) {
                carriedName = name;
            }

            // <complexType name='EncryptedType' abstract='true'>
            //     <sequence>
            //         <element name='EncryptionMethod' type='xenc:EncryptionMethodType'
            //             minOccurs='0'/>
            //         <element ref='ds:KeyInfo' minOccurs='0'/>
            //         <element ref='xenc:CipherData'/>
            //         <element ref='xenc:EncryptionProperties' minOccurs='0'/>
            //     </sequence>
            //     <attribute name='Id' type='ID' use='optional'/>
            //     <attribute name='Type' type='anyURI' use='optional'/>
            //     <attribute name='MimeType' type='string' use='optional'/>
            //     <attribute name='Encoding' type='anyURI' use='optional'/>
            // </complexType>
            // <element name='EncryptedKey' type='xenc:EncryptedKeyType'/>
            // <complexType name='EncryptedKeyType'>
            //     <complexContent>
            //         <extension base='xenc:EncryptedType'>
            //             <sequence>
            //                 <element ref='xenc:ReferenceList' minOccurs='0'/>
            //                 <element name='CarriedKeyName' type='string' minOccurs='0'/>
            //             </sequence>
            //             <attribute name='Recipient' type='string' use='optional'/>
            //         </extension>
            //     </complexContent>
            // </complexType>
            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument, EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTEDKEY);

                if (null != super.getId()) {
                    result.setAttributeNS(
                        null, EncryptionConstants._ATT_ID, super.getId());
                }
                if (null != super.getType()) {
                    result.setAttributeNS(
                        null, EncryptionConstants._ATT_TYPE, super.getType());
                }
                if (null != super.getMimeType()) {
                    result.setAttributeNS(null,
                        EncryptionConstants._ATT_MIMETYPE, super.getMimeType());
                }
                if (null != super.getEncoding()) {
                    result.setAttributeNS(null, Constants._ATT_ENCODING,
                        super.getEncoding());
                }
                if (null != getRecipient()) {
                    result.setAttributeNS(null,
                        EncryptionConstants._ATT_RECIPIENT, getRecipient());
                }
                if (null != super.getEncryptionMethod()) {
                    result.appendChild(((EncryptionMethodImpl)
                        super.getEncryptionMethod()).toElement());
                }
                if (null != super.getKeyInfo()) {
                    result.appendChild(super.getKeyInfo().getElement());
                }
                result.appendChild(
                    ((CipherDataImpl) super.getCipherData()).toElement());
                if (null != super.getEncryptionProperties()) {
                    result.appendChild(((EncryptionPropertiesImpl)
                        super.getEncryptionProperties()).toElement());
                }
                if (referenceList != null && !referenceList.isEmpty()) {
                    result.appendChild(((ReferenceListImpl)
                        getReferenceList()).toElement());
                }
                if (null != carriedName) {
                    Element element = ElementProxy.createElementForFamily(
                        _contextDocument,
                        EncryptionConstants.EncryptionSpecNS,
                        EncryptionConstants._TAG_CARRIEDKEYNAME);
                    Node node = _contextDocument.createTextNode(carriedName);
                    element.appendChild(node);
                    result.appendChild(element);
                }

                return (result);
            }
        }

        private abstract class EncryptedTypeImpl {
            private String id =  null;
            private String type = null;
            private String mimeType = null;
            private String encoding = null;
            private EncryptionMethod encryptionMethod = null;
            private KeyInfo keyInfo = null;
            private CipherData cipherData = null;
            private EncryptionProperties encryptionProperties = null;

            protected EncryptedTypeImpl(CipherData data) {
                cipherData = data;
            }
            /**
             *
             * @return
             */
            public String getId() {
                return (id);
            }
            /**
             *
             * @param id
             */
            public void setId(String id) {
                this.id = id;
            }
            /**
             *
             * @return
             */
            public String getType() {
                return (type);
            }
            /**
             *
             * @param type
             */
            public void setType(String type) {
                if (type == null || type.length() == 0) {
                    this.type = null;
                } else {
                    URI tmpType = null;
                    try {
                        tmpType = new URI(type);
                    } catch (URI.MalformedURIException mfue) {
                        // complain
                    }
                    this.type = tmpType.toString();
                }
            }
            /**
             *
             * @return
             */
            public String getMimeType() {
                return (mimeType);
            }
            /**
             *
             * @param type
             */
            public void setMimeType(String type) {
                mimeType = type;
            }
            /**
             *
             * @return
             */
            public String getEncoding() {
                return (encoding);
            }
            /**
             *
             * @param encoding
             */
            public void setEncoding(String encoding) {
                if (encoding == null || encoding.length() == 0) {
                    this.encoding = null;
                } else {
                    URI tmpEncoding = null;
                    try {
                        tmpEncoding = new URI(encoding);
                    } catch (URI.MalformedURIException mfue) {
                        // complain
                    }
                    this.encoding = tmpEncoding.toString();
                }
            }
            /**
             *
             * @return
             */
            public EncryptionMethod getEncryptionMethod() {
                return (encryptionMethod);
            }
            /**
             *
             * @param method
             */
            public void setEncryptionMethod(EncryptionMethod method) {
                encryptionMethod = method;
            }
            /**
             *
             * @return
             */
            public KeyInfo getKeyInfo() {
                return (keyInfo);
            }
            /**
             *
             * @param info
             */
            public void setKeyInfo(KeyInfo info) {
                keyInfo = info;
            }
            /**
             *
             * @return
             */
            public CipherData getCipherData() {
                return (cipherData);
            }
            /**
             *
             * @return
             */
            public EncryptionProperties getEncryptionProperties() {
                return (encryptionProperties);
            }
            /**
             *
             * @param properties
             */
            public void setEncryptionProperties(
                    EncryptionProperties properties) {
                encryptionProperties = properties;
            }
        }

        // <complexType name='EncryptionMethodType' mixed='true'>
        //     <sequence>
        //         <element name='KeySize' minOccurs='0' type='xenc:KeySizeType'/>
        //         <element name='OAEPparams' minOccurs='0' type='base64Binary'/>
        //         <any namespace='##other' minOccurs='0' maxOccurs='unbounded'/>
        //     </sequence>
        //     <attribute name='Algorithm' type='anyURI' use='required'/>
        // </complexType>
        private class EncryptionMethodImpl implements EncryptionMethod {
            private String algorithm = null;
            private int keySize = Integer.MIN_VALUE;
            private byte[] oaepParams = null;
            private List<Element> encryptionMethodInformation = null;
            /**
             *
             * @param algorithm
             */
            public EncryptionMethodImpl(String algorithm) {
                URI tmpAlgorithm = null;
                try {
                    tmpAlgorithm = new URI(algorithm);
                } catch (URI.MalformedURIException mfue) {
                    // complain
                }
                this.algorithm = tmpAlgorithm.toString();
                encryptionMethodInformation = new LinkedList<Element>();
            }
            /** @inheritDoc */
            public String getAlgorithm() {
                return (algorithm);
            }
            /** @inheritDoc */
            public int getKeySize() {
                return (keySize);
            }
            /** @inheritDoc */
            public void setKeySize(int size) {
                keySize = size;
            }
            /** @inheritDoc */
            public byte[] getOAEPparams() {
                return (oaepParams);
            }
            /** @inheritDoc */
            public void setOAEPparams(byte[] params) {
                oaepParams = params;
            }
            /** @inheritDoc */
            public Iterator<Element> getEncryptionMethodInformation() {
                return (encryptionMethodInformation.iterator());
            }
            /** @inheritDoc */
            public void addEncryptionMethodInformation(Element info) {
                encryptionMethodInformation.add(info);
            }
            /** @inheritDoc */
            public void removeEncryptionMethodInformation(Element info) {
                encryptionMethodInformation.remove(info);
            }

            // <complexType name='EncryptionMethodType' mixed='true'>
            //     <sequence>
            //         <element name='KeySize' minOccurs='0' type='xenc:KeySizeType'/>
            //         <element name='OAEPparams' minOccurs='0' type='base64Binary'/>
            //         <any namespace='##other' minOccurs='0' maxOccurs='unbounded'/>
            //     </sequence>
            //     <attribute name='Algorithm' type='anyURI' use='required'/>
            // </complexType>
            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument, EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTIONMETHOD);
                result.setAttributeNS(null, EncryptionConstants._ATT_ALGORITHM,
                    algorithm);
                if (keySize > 0) {
                    result.appendChild(
                        ElementProxy.createElementForFamily(_contextDocument,
                            EncryptionConstants.EncryptionSpecNS,
                            EncryptionConstants._TAG_KEYSIZE).appendChild(
                            _contextDocument.createTextNode(
                                String.valueOf(keySize))));
                }
                if (null != oaepParams) {
                    result.appendChild(
                        ElementProxy.createElementForFamily(_contextDocument,
                            EncryptionConstants.EncryptionSpecNS,
                            EncryptionConstants._TAG_OAEPPARAMS).appendChild(
                            _contextDocument.createTextNode(
                                new String(oaepParams))));
                }
                if (!encryptionMethodInformation.isEmpty()) {
                    Iterator<Element> itr = encryptionMethodInformation.iterator();
                    result.appendChild(itr.next());
                }

                return (result);
            }
        }

        // <element name='EncryptionProperties' type='xenc:EncryptionPropertiesType'/>
        // <complexType name='EncryptionPropertiesType'>
        //     <sequence>
        //         <element ref='xenc:EncryptionProperty' maxOccurs='unbounded'/>
        //     </sequence>
        //     <attribute name='Id' type='ID' use='optional'/>
        // </complexType>
        private class EncryptionPropertiesImpl implements EncryptionProperties {
            private String id = null;
            private List<EncryptionProperty> encryptionProperties = null;
            /**
             *
             *
             */
            public EncryptionPropertiesImpl() {
                encryptionProperties = new LinkedList<EncryptionProperty>();
            }
            /** @inheritDoc */
            public String getId() {
                return (id);
            }
            /** @inheritDoc */
            public void setId(String id) {
                this.id = id;
            }
            /** @inheritDoc */
            public Iterator<EncryptionProperty> getEncryptionProperties() {
                return (encryptionProperties.iterator());
            }
            /** @inheritDoc */
            public void addEncryptionProperty(EncryptionProperty property) {
                encryptionProperties.add(property);
            }
            /** @inheritDoc */
            public void removeEncryptionProperty(EncryptionProperty property) {
                encryptionProperties.remove(property);
            }

            // <element name='EncryptionProperties' type='xenc:EncryptionPropertiesType'/>
            // <complexType name='EncryptionPropertiesType'>
            //     <sequence>
            //         <element ref='xenc:EncryptionProperty' maxOccurs='unbounded'/>
            //     </sequence>
            //     <attribute name='Id' type='ID' use='optional'/>
            // </complexType>
            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument, EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTIONPROPERTIES);
                if (null != id) {
                    result.setAttributeNS(null, EncryptionConstants._ATT_ID, id);
                }
                Iterator<EncryptionProperty> itr = getEncryptionProperties();
                while (itr.hasNext()) {
                    result.appendChild(((EncryptionPropertyImpl)
                        itr.next()).toElement());
                }

                return (result);
            }
        }

        // <element name='EncryptionProperty' type='xenc:EncryptionPropertyType'/>
        // <complexType name='EncryptionPropertyType' mixed='true'>
        //     <choice maxOccurs='unbounded'>
        //         <any namespace='##other' processContents='lax'/>
        //     </choice>
        //     <attribute name='Target' type='anyURI' use='optional'/>
        //     <attribute name='Id' type='ID' use='optional'/>
        //     <anyAttribute namespace="http://www.w3.org/XML/1998/namespace"/>
        // </complexType>
        private class EncryptionPropertyImpl implements EncryptionProperty {
            private String target = null;
            private String id = null;
            private HashMap<String,String> attributeMap = new HashMap<String,String>();
            private List<Element> encryptionInformation = null;

            /**
             *
             *
             */
            public EncryptionPropertyImpl() {
                encryptionInformation = new LinkedList<Element>();
            }
            /** @inheritDoc */
            public String getTarget() {
                return (target);
            }
            /** @inheritDoc */
            public void setTarget(String target) {
                if (target == null || target.length() == 0) {
                    this.target = null;
                } else if (target.startsWith("#")) {
                    /*
                     * This is a same document URI reference. Do not parse,
                     * because com.sun.org.apache.xml.internal.utils.URI considers this an
                     * illegal URI because it has no scheme.
                     */
                    this.target = target;
                } else {
                    URI tmpTarget = null;
                    try {
                        tmpTarget = new URI(target);
                    } catch (URI.MalformedURIException mfue) {
                        // complain
                    }
                    this.target = tmpTarget.toString();
                }
            }
            /** @inheritDoc */
            public String getId() {
                return (id);
            }
            /** @inheritDoc */
            public void setId(String id) {
                this.id = id;
            }
            /** @inheritDoc */
            public String getAttribute(String attribute) {
                return attributeMap.get(attribute);
            }
            /** @inheritDoc */
            public void setAttribute(String attribute, String value) {
                attributeMap.put(attribute, value);
            }
            /** @inheritDoc */
            public Iterator<Element> getEncryptionInformation() {
                return (encryptionInformation.iterator());
            }
            /** @inheritDoc */
            public void addEncryptionInformation(Element info) {
                encryptionInformation.add(info);
            }
            /** @inheritDoc */
            public void removeEncryptionInformation(Element info) {
                encryptionInformation.remove(info);
            }

            // <element name='EncryptionProperty' type='xenc:EncryptionPropertyType'/>
            // <complexType name='EncryptionPropertyType' mixed='true'>
            //     <choice maxOccurs='unbounded'>
            //         <any namespace='##other' processContents='lax'/>
            //     </choice>
            //     <attribute name='Target' type='anyURI' use='optional'/>
            //     <attribute name='Id' type='ID' use='optional'/>
            //     <anyAttribute namespace="http://www.w3.org/XML/1998/namespace"/>
            // </complexType>
            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument, EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_ENCRYPTIONPROPERTY);
                if (null != target) {
                    result.setAttributeNS(null, EncryptionConstants._ATT_TARGET,
                        target);
                }
                if (null != id) {
                    result.setAttributeNS(null, EncryptionConstants._ATT_ID,
                        id);
                }
                // TODO: figure out the anyAttribyte stuff...
                // TODO: figure out the any stuff...

                return (result);
            }
        }

        // <complexType name='TransformsType'>
        //     <sequence>
        //         <element ref='ds:Transform' maxOccurs='unbounded'/>
        //     </sequence>
        // </complexType>
        private class TransformsImpl extends
                       com.sun.org.apache.xml.internal.security.transforms.Transforms
                       implements Transforms {

                        /**
                         * Construct Transforms
                         */

                        public TransformsImpl() {
                                super(_contextDocument);
                        }
                        /**
             *
                         * @param doc
                         */
                        public TransformsImpl(Document doc) {
                                if (doc == null) {
                                 throw new RuntimeException("Document is null");
                              }

                              this._doc = doc;
                              this._constructionElement =  createElementForFamilyLocal(this._doc,
                                          this.getBaseNamespace(), this.getBaseLocalName());
                        }
                        /**
             *
                         * @param element
                         * @throws XMLSignatureException
                         * @throws InvalidTransformException
                         * @throws XMLSecurityException
                         * @throws TransformationException
                         */
                        public TransformsImpl(Element element)
                                throws XMLSignatureException,
                                   InvalidTransformException,
                                       XMLSecurityException,
                                       TransformationException {

                                super(element, "");

                        }

            /**
             *
             * @return
             */
                        public Element toElement() {

                                if (_doc == null)
                                        _doc = _contextDocument;

                                return getElement();
                        }

            /** @inheritDoc */
                        public com.sun.org.apache.xml.internal.security.transforms.Transforms getDSTransforms() {
                                return (this);
                        }


                        // Over-ride the namespace
            /** @inheritDoc */
                        public String getBaseNamespace() {
                                return EncryptionConstants.EncryptionSpecNS;
                        }

        }

        //<element name='ReferenceList'>
        //    <complexType>
        //        <choice minOccurs='1' maxOccurs='unbounded'>
        //            <element name='DataReference' type='xenc:ReferenceType'/>
        //            <element name='KeyReference' type='xenc:ReferenceType'/>
        //        </choice>
        //    </complexType>
        //</element>
        private class ReferenceListImpl implements ReferenceList {
            private Class<?> sentry;
            private List<Reference> references;
            /**
             *
             * @param type
             */
            public ReferenceListImpl(int type) {
                if (type == ReferenceList.DATA_REFERENCE) {
                    sentry = DataReference.class;
                } else if (type == ReferenceList.KEY_REFERENCE) {
                    sentry = KeyReference.class;
                } else {
                    throw new IllegalArgumentException();
                }
                references = new LinkedList<Reference>();
            }
            /** @inheritDoc */
            public void add(Reference reference) {
                if (!reference.getClass().equals(sentry)) {
                    throw new IllegalArgumentException();
                }
                 references.add(reference);
            }
            /** @inheritDoc */
            public void remove(Reference reference) {
                if (!reference.getClass().equals(sentry)) {
                    throw new IllegalArgumentException();
                }
                references.remove(reference);
            }
            /** @inheritDoc */
            public int size() {
                return (references.size());
            }
            /** @inheritDoc */
            public boolean isEmpty() {
                return (references.isEmpty());
            }
            /** @inheritDoc */
            public Iterator<Reference> getReferences() {
                return (references.iterator());
            }

            Element toElement() {
                Element result = ElementProxy.createElementForFamily(
                    _contextDocument,
                    EncryptionConstants.EncryptionSpecNS,
                    EncryptionConstants._TAG_REFERENCELIST);
                Iterator<Reference> eachReference = references.iterator();
                while (eachReference.hasNext()) {
                    Reference reference = eachReference.next();
                    result.appendChild(
                        ((ReferenceImpl) reference).toElement());
                }
                return (result);
            }
            /** @inheritDoc */
            public Reference newDataReference(String uri) {
                return (new DataReference(uri));
            }
            /** @inheritDoc */
            public Reference newKeyReference(String uri) {
                return (new KeyReference(uri));
            }

            /**
             * <code>ReferenceImpl</code> is an implementation of
             * <code>Reference</code>.
             *
             * @see Reference
             */
            private abstract class ReferenceImpl implements Reference {
                private String uri;
                private List<Element> referenceInformation;

                ReferenceImpl(String _uri) {
                    this.uri = _uri;
                    referenceInformation = new LinkedList<Element>();
                }
                /** @inheritDoc */
                public String getURI() {
                    return (uri);
                }
                /** @inheritDoc */
                public Iterator<Element> getElementRetrievalInformation() {
                    return (referenceInformation.iterator());
                }
                /** @inheritDoc */
                public void setURI(String _uri) {
                        this.uri = _uri;
                }
                /** @inheritDoc */
                public void removeElementRetrievalInformation(Element node) {
                    referenceInformation.remove(node);
                }
                /** @inheritDoc */
                public void addElementRetrievalInformation(Element node) {
                    referenceInformation.add(node);
                }
                /**
                 *
                 * @return
                 */
                public abstract Element toElement();

                Element toElement(String tagName) {
                    Element result = ElementProxy.createElementForFamily(
                        _contextDocument,
                        EncryptionConstants.EncryptionSpecNS,
                        tagName);
                    result.setAttribute(EncryptionConstants._ATT_URI, uri);

                    // TODO: Need to martial referenceInformation
                    // Figure out how to make this work..
                    // <any namespace="##other" minOccurs="0" maxOccurs="unbounded"/>

                    return (result);
                }
            }

            private class DataReference extends ReferenceImpl {
                DataReference(String uri) {
                    super(uri);
                }
                /** @inheritDoc */
                public Element toElement() {
                    return super.toElement(EncryptionConstants._TAG_DATAREFERENCE);
                }
            }

            private class KeyReference extends ReferenceImpl {
                KeyReference(String uri) {
                    super (uri);
                }
                /** @inheritDoc */
                public Element toElement() {
                    return super.toElement(EncryptionConstants._TAG_KEYREFERENCE);
                }
            }
        }
    }
}
