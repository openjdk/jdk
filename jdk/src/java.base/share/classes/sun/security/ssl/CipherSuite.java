/*
 * Copyright (c) 2002, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package sun.security.ssl;

import java.util.*;

import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.KeyManagementException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static sun.security.ssl.CipherSuite.KeyExchange.*;
import static sun.security.ssl.CipherSuite.PRF.*;
import static sun.security.ssl.CipherSuite.CipherType.*;
import static sun.security.ssl.CipherSuite.MacAlg.*;
import static sun.security.ssl.CipherSuite.BulkCipher.*;
import static sun.security.ssl.JsseJce.*;
import static sun.security.ssl.NamedGroupType.*;

/**
 * An SSL/TLS CipherSuite. Constants for the standard key exchange, cipher,
 * and mac algorithms are also defined in this class.
 *
 * The CipherSuite class and the inner classes defined in this file roughly
 * follow the type safe enum pattern described in Effective Java. This means:
 *
 *  . instances are immutable, classes are final
 *
 *  . there is a unique instance of every value, i.e. there are never two
 *    instances representing the same CipherSuite, etc. This means equality
 *    tests can be performed using == instead of equals() (although that works
 *    as well). [A minor exception are *unsupported* CipherSuites read from a
 *    handshake message, but this is usually irrelevant]
 *
 *  . instances are obtained using the static valueOf() factory methods.
 *
 *  . properties are defined as final variables and made available as
 *    package private variables without method accessors
 *
 *  . if the member variable allowed is false, the given algorithm is either
 *    unavailable or disabled at compile time
 *
 */
final class CipherSuite implements Comparable<CipherSuite> {

    // minimum priority for supported CipherSuites
    static final int SUPPORTED_SUITES_PRIORITY = 1;

    // minimum priority for default enabled CipherSuites
    static final int DEFAULT_SUITES_PRIORITY = 300;

    private static final boolean ALLOW_ECC = Debug.getBooleanProperty
        ("com.sun.net.ssl.enableECC", true);

    // Map Integer(id) -> CipherSuite
    // contains all known CipherSuites
    private static final Map<Integer,CipherSuite> idMap;

    // Map String(name) -> CipherSuite
    // contains only supported CipherSuites (i.e. allowed == true)
    private static final Map<String,CipherSuite> nameMap;

    // Protocol defined CipherSuite name, e.g. SSL_RSA_WITH_RC4_128_MD5
    // we use TLS_* only for new CipherSuites, still SSL_* for old ones
    final String name;

    // id in 16 bit MSB format, i.e. 0x0004 for SSL_RSA_WITH_RC4_128_MD5
    final int id;

    // priority for the internal default preference order. the higher the
    // better. Each supported CipherSuite *must* have a unique priority.
    // Ciphersuites with priority >= DEFAULT_SUITES_PRIORITY are enabled
    // by default
    final int priority;

    // key exchange, bulk cipher, mac and prf algorithms. See those
    // classes below.
    final KeyExchange keyExchange;
    final BulkCipher cipher;
    final MacAlg macAlg;
    final PRF prfAlg;

    // whether a CipherSuite qualifies as exportable under 512/40 bit rules.
    // TLS 1.1+ (RFC 4346) must not negotiate to these suites.
    final boolean exportable;

    // true iff implemented and enabled at compile time
    final boolean allowed;

    // obsoleted since protocol version
    //
    // TLS version is used.  If checking DTLS versions, please map to
    // TLS version firstly.  See ProtocolVersion.mapToTLSProtocol().
    final int obsoleted;

    // supported since protocol version (TLS version is used)
    //
    // TLS version is used.  If checking DTLS versions, please map to
    // TLS version firstly.  See ProtocolVersion.mapToTLSProtocol().
    final int supported;

    /**
     * Constructor for implemented CipherSuites.
     */
    private CipherSuite(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher, MacAlg mac,
            boolean allowed, int obsoleted, int supported, PRF prfAlg) {
        this.name = name;
        this.id = id;
        this.priority = priority;
        this.keyExchange = keyExchange;
        this.cipher = cipher;
        this.macAlg = mac;
        this.exportable = cipher.exportable;
        allowed &= keyExchange.allowed;
        allowed &= cipher.allowed;
        this.allowed = allowed;
        this.obsoleted = obsoleted;
        this.supported = supported;
        this.prfAlg = prfAlg;
    }

    /**
     * Constructor for unimplemented CipherSuites.
     */
    private CipherSuite(String name, int id) {
        this.name = name;
        this.id = id;
        this.allowed = false;

        this.priority = 0;
        this.keyExchange = null;
        this.cipher = null;
        this.macAlg = null;
        this.exportable = false;
        this.obsoleted = ProtocolVersion.LIMIT_MAX_VALUE;
        this.supported = ProtocolVersion.LIMIT_MIN_VALUE;
        this.prfAlg = P_NONE;
    }

    /**
     * Return whether this CipherSuite is available for use. A
     * CipherSuite may be unavailable even if it is supported
     * (i.e. allowed == true) if the required JCE cipher is not installed.
     */
    boolean isAvailable() {
        return allowed && keyExchange.isAvailable() && cipher.isAvailable();
    }

    boolean isNegotiable() {
        return this != C_SCSV && isAvailable();
    }

    // See also CipherBox.calculatePacketSize().
    int calculatePacketSize(int fragmentSize,
            ProtocolVersion protocolVersion, boolean isDTLS) {

        int packetSize = fragmentSize;
        if (cipher != B_NULL) {
            int blockSize = cipher.ivSize;
            switch (cipher.cipherType) {
                case BLOCK_CIPHER:
                    packetSize += macAlg.size;
                    packetSize += 1;        // 1 byte padding length field
                    packetSize +=           // use the minimal padding
                            (blockSize - (packetSize % blockSize)) % blockSize;
                    if (protocolVersion.useTLS11PlusSpec()) {
                        packetSize += blockSize;        // explicit IV
                    }

                    break;
            case AEAD_CIPHER:
                packetSize += cipher.ivSize - cipher.fixedIvSize;   // record IV
                packetSize += cipher.tagSize;

                break;
            default:    // NULL_CIPHER or STREAM_CIPHER
                packetSize += macAlg.size;
            }
        }

        return packetSize +
            (isDTLS ? DTLSRecord.headerSize : SSLRecord.headerSize);
    }

    // See also CipherBox.calculateFragmentSize().
    int calculateFragSize(int packetLimit,
            ProtocolVersion protocolVersion, boolean isDTLS) {

        int fragSize = packetLimit -
                (isDTLS ? DTLSRecord.headerSize : SSLRecord.headerSize);
        if (cipher != B_NULL) {
            int blockSize = cipher.ivSize;
            switch (cipher.cipherType) {
            case BLOCK_CIPHER:
                if (protocolVersion.useTLS11PlusSpec()) {
                    fragSize -= blockSize;              // explicit IV
                }
                fragSize -= (fragSize % blockSize);     // cannot hold a block
                // No padding for a maximum fragment.
                fragSize -= 1;        // 1 byte padding length field: 0x00
                fragSize -= macAlg.size;

                break;
            case AEAD_CIPHER:
                fragSize -= cipher.tagSize;
                fragSize -= cipher.ivSize - cipher.fixedIvSize;     // record IV

                break;
            default:    // NULL_CIPHER or STREAM_CIPHER
                fragSize -= macAlg.size;
            }
        }

        return fragSize;
    }

    /**
     * Compares CipherSuites based on their priority. Has the effect of
     * sorting CipherSuites when put in a sorted collection, which is
     * used by CipherSuiteList. Follows standard Comparable contract.
     *
     * Note that for unsupported CipherSuites parsed from a handshake
     * message we violate the equals() contract.
     */
    @Override
    public int compareTo(CipherSuite o) {
        return o.priority - priority;
    }

    /**
     * Returns this.name.
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Return a CipherSuite for the given name. The returned CipherSuite
     * is supported by this implementation but may not actually be
     * currently useable. See isAvailable().
     *
     * @exception IllegalArgumentException if the CipherSuite is unknown or
     * unsupported.
     */
    static CipherSuite valueOf(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Name must not be null");
        }

        CipherSuite c = nameMap.get(s);
        if ((c == null) || (c.allowed == false)) {
            throw new IllegalArgumentException("Unsupported ciphersuite " + s);
        }

        return c;
    }

    /**
     * Return a CipherSuite with the given ID. A temporary object is
     * constructed if the ID is unknown. Use isAvailable() to verify that
     * the CipherSuite can actually be used.
     */
    static CipherSuite valueOf(int id1, int id2) {
        id1 &= 0xff;
        id2 &= 0xff;
        int id = (id1 << 8) | id2;
        CipherSuite c = idMap.get(id);
        if (c == null) {
            String h1 = Integer.toString(id1, 16);
            String h2 = Integer.toString(id2, 16);
            c = new CipherSuite("Unknown 0x" + h1 + ":0x" + h2, id);
        }
        return c;
    }

    // for use by SSLContextImpl only
    static Collection<CipherSuite> allowedCipherSuites() {
        return nameMap.values();
    }

    /*
     * Use this method when all of the values need to be specified.
     * This is primarily used when defining a new ciphersuite for
     * TLS 1.2+ that doesn't use the "default" PRF.
     */
    private static void add(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher, MacAlg mac,
            boolean allowed, int obsoleted, int supported, PRF prf) {

        CipherSuite c = new CipherSuite(name, id, priority, keyExchange,
            cipher, mac, allowed, obsoleted, supported, prf);
        if (idMap.put(id, c) != null) {
            throw new RuntimeException("Duplicate ciphersuite definition: "
                                        + id + ", " + name);
        }
        if (c.allowed) {
            if (nameMap.put(name, c) != null) {
                throw new RuntimeException("Duplicate ciphersuite definition: "
                                            + id + ", " + name);
            }
        }
    }

    /*
     * Use this method when there is no lower protocol limit where this
     * suite can be used, and the PRF is P_SHA256.  That is, the
     * existing ciphersuites.  From RFC 5246:
     *
     *     All cipher suites in this document use P_SHA256.
     */
    private static void add(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher, MacAlg mac,
            boolean allowed, int obsoleted) {
        PRF prf = obsoleted < ProtocolVersion.TLS12.v ? P_NONE : P_SHA256;

        add(name, id, priority, keyExchange, cipher, mac, allowed, obsoleted,
            ProtocolVersion.LIMIT_MIN_VALUE, prf);
    }

    /*
     * Use this method when there is no upper protocol limit.  That is,
     * suites which have not been obsoleted.
     */
    private static void add(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher, MacAlg mac,
            boolean allowed) {
        add(name, id, priority, keyExchange, cipher, mac, allowed,
                ProtocolVersion.LIMIT_MAX_VALUE);
    }

    /*
     * Use this method to define an unimplemented suite.  This provides
     * a number<->name mapping that can be used for debugging.
     */
    private static void add(String name, int id) {
        CipherSuite c = new CipherSuite(name, id);
        if (idMap.put(id, c) != null) {
            throw new RuntimeException("Duplicate ciphersuite definition: "
                                        + id + ", " + name);
        }
    }

    /**
     * An SSL/TLS key exchange algorithm.
     */
    static enum KeyExchange {

        // key exchange algorithms
        K_NULL       ("NULL",       false,      NAMED_GROUP_NONE),
        K_RSA        ("RSA",        true,       NAMED_GROUP_NONE),
        K_RSA_EXPORT ("RSA_EXPORT", true,       NAMED_GROUP_NONE),
        K_DH_RSA     ("DH_RSA",     false,      NAMED_GROUP_NONE),
        K_DH_DSS     ("DH_DSS",     false,      NAMED_GROUP_NONE),
        K_DHE_DSS    ("DHE_DSS",    true,       NAMED_GROUP_FFDHE),
        K_DHE_RSA    ("DHE_RSA",    true,       NAMED_GROUP_FFDHE),
        K_DH_ANON    ("DH_anon",    true,       NAMED_GROUP_FFDHE),

        K_ECDH_ECDSA ("ECDH_ECDSA",  ALLOW_ECC, NAMED_GROUP_ECDHE),
        K_ECDH_RSA   ("ECDH_RSA",    ALLOW_ECC, NAMED_GROUP_ECDHE),
        K_ECDHE_ECDSA("ECDHE_ECDSA", ALLOW_ECC, NAMED_GROUP_ECDHE),
        K_ECDHE_RSA  ("ECDHE_RSA",   ALLOW_ECC, NAMED_GROUP_ECDHE),
        K_ECDH_ANON  ("ECDH_anon",   ALLOW_ECC, NAMED_GROUP_ECDHE),

        // Kerberos cipher suites
        K_KRB5       ("KRB5", true,             NAMED_GROUP_NONE),
        K_KRB5_EXPORT("KRB5_EXPORT", true,      NAMED_GROUP_NONE),

        // renegotiation protection request signaling cipher suite
        K_SCSV       ("SCSV",        true,      NAMED_GROUP_NONE);

        // name of the key exchange algorithm, e.g. DHE_DSS
        final String name;
        final boolean allowed;
        final NamedGroupType groupType;
        private final boolean alwaysAvailable;

        KeyExchange(String name, boolean allowed, NamedGroupType groupType) {
            this.name = name;
            this.allowed = allowed;
            this.groupType = groupType;
            this.alwaysAvailable = allowed &&
                (!name.startsWith("EC")) && (!name.startsWith("KRB"));
        }

        boolean isAvailable() {
            if (alwaysAvailable) {
                return true;
            }

            if (groupType == NAMED_GROUP_ECDHE) {
                return (allowed && JsseJce.isEcAvailable());
            } else if (name.startsWith("KRB")) {
                return (allowed && JsseJce.isKerberosAvailable());
            } else {
                return allowed;
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static enum CipherType {
        NULL_CIPHER,           // null cipher
        STREAM_CIPHER,         // stream cipher
        BLOCK_CIPHER,          // block cipher in CBC mode
        AEAD_CIPHER            // AEAD cipher
    }

    /**
     * An SSL/TLS bulk cipher algorithm. One instance per combination of
     * cipher and key length.
     *
     * Also contains a factory method to obtain in initialized CipherBox
     * for this algorithm.
     */
    static enum BulkCipher {

        // export strength ciphers
        B_NULL("NULL", NULL_CIPHER, 0, 0, 0, 0, true),
        B_RC4_40(CIPHER_RC4, STREAM_CIPHER, 5, 16, 0, 0, true),
        B_RC2_40("RC2", BLOCK_CIPHER, 5, 16, 8, 0, false),
        B_DES_40(CIPHER_DES,  BLOCK_CIPHER, 5, 8, 8, 0, true),

        // domestic strength ciphers
        B_RC4_128(CIPHER_RC4, STREAM_CIPHER, 16, 0, 0, true),
        B_DES(CIPHER_DES, BLOCK_CIPHER, 8, 8, 0, true),
        B_3DES(CIPHER_3DES, BLOCK_CIPHER, 24, 8, 0, true),
        B_IDEA("IDEA", BLOCK_CIPHER, 16, 8, 0, false),
        B_AES_128(CIPHER_AES, BLOCK_CIPHER, 16, 16, 0, true),
        B_AES_256(CIPHER_AES, BLOCK_CIPHER, 32, 16, 0, true),
        B_AES_128_GCM(CIPHER_AES_GCM, AEAD_CIPHER, 16, 12, 4, true),
        B_AES_256_GCM(CIPHER_AES_GCM, AEAD_CIPHER, 32, 12, 4, true);

        // descriptive name including key size, e.g. AES/128
        final String description;

        // JCE cipher transformation string, e.g. AES/CBC/NoPadding
        final String transformation;

        // algorithm name, e.g. AES
        final String algorithm;

        // supported and compile time enabled. Also see isAvailable()
        final boolean allowed;

        // number of bytes of entropy in the key
        final int keySize;

        // length of the actual cipher key in bytes.
        // for non-exportable ciphers, this is the same as keySize
        final int expandedKeySize;

        // size of the IV
        final int ivSize;

        // size of fixed IV
        //
        // record_iv_length = ivSize - fixedIvSize
        final int fixedIvSize;

        // exportable under 512/40 bit rules
        final boolean exportable;

        // Is the cipher algorithm of Cipher Block Chaining (CBC) mode?
        final CipherType cipherType;

        // size of the authentication tag, only applicable to cipher suites in
        // Galois Counter Mode (GCM)
        //
        // As far as we know, all supported GCM cipher suites use 128-bits
        // authentication tags.
        final int tagSize = 16;

        // The secure random used to detect the cipher availability.
        private static final SecureRandom secureRandom;

        // runtime availability
        private final boolean isAvailable;

        static {
            try {
                secureRandom = JsseJce.getSecureRandom();
            } catch (KeyManagementException kme) {
                throw new RuntimeException(kme);
            }
        }

        BulkCipher(String transformation, CipherType cipherType, int keySize,
                int expandedKeySize, int ivSize,
                int fixedIvSize, boolean allowed) {

            this.transformation = transformation;
            String[] splits = transformation.split("/");
            this.algorithm = splits[0];
            this.cipherType = cipherType;
            this.description = this.algorithm + "/" + (keySize << 3);
            this.keySize = keySize;
            this.ivSize = ivSize;
            this.fixedIvSize = fixedIvSize;
            this.allowed = allowed;

            this.expandedKeySize = expandedKeySize;
            this.exportable = true;

            // availability of this bulk cipher
            //
            // Currently all supported ciphers except AES are always available
            // via the JSSE internal implementations. We also assume AES/128 of
            // CBC mode is always available since it is shipped with the SunJCE
            // provider.  However, AES/256 is unavailable when the default JCE
            // policy jurisdiction files are installed because of key length
            // restrictions.
            this.isAvailable =
                    allowed ? isUnlimited(keySize, transformation) : false;
        }

        BulkCipher(String transformation, CipherType cipherType, int keySize,
                int ivSize, int fixedIvSize, boolean allowed) {
            this.transformation = transformation;
            String[] splits = transformation.split("/");
            this.algorithm = splits[0];
            this.cipherType = cipherType;
            this.description = this.algorithm + "/" + (keySize << 3);
            this.keySize = keySize;
            this.ivSize = ivSize;
            this.fixedIvSize = fixedIvSize;
            this.allowed = allowed;

            this.expandedKeySize = keySize;
            this.exportable = false;

            // availability of this bulk cipher
            //
            // Currently all supported ciphers except AES are always available
            // via the JSSE internal implementations. We also assume AES/128 of
            // CBC mode is always available since it is shipped with the SunJCE
            // provider.  However, AES/256 is unavailable when the default JCE
            // policy jurisdiction files are installed because of key length
            // restrictions.
            this.isAvailable =
                    allowed ? isUnlimited(keySize, transformation) : false;
        }

        /**
         * Return an initialized CipherBox for this BulkCipher.
         * IV must be null for stream ciphers.
         *
         * @exception NoSuchAlgorithmException if anything goes wrong
         */
        CipherBox newCipher(ProtocolVersion version, SecretKey key,
                IvParameterSpec iv, SecureRandom random,
                boolean encrypt) throws NoSuchAlgorithmException {
            return CipherBox.newCipherBox(version, this,
                                            key, iv, random, encrypt);
        }

        /**
         * Test if this bulk cipher is available. For use by CipherSuite.
         */
        boolean isAvailable() {
            return this.isAvailable;
        }

        private static boolean isUnlimited(int keySize, String transformation) {
            int keySizeInBits = keySize * 8;
            if (keySizeInBits > 128) {    // need the JCE unlimited
                                          // strength jurisdiction policy
                try {
                    if (Cipher.getMaxAllowedKeyLength(
                            transformation) < keySizeInBits) {

                        return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * An SSL/TLS key MAC algorithm.
     *
     * Also contains a factory method to obtain an initialized MAC
     * for this algorithm.
     */
    static enum MacAlg {
        // MACs
        M_NULL      ("NULL",     0,   0,   0),
        M_MD5       ("MD5",     16,  64,   9),
        M_SHA       ("SHA",     20,  64,   9),
        M_SHA256    ("SHA256",  32,  64,   9),
        M_SHA384    ("SHA384",  48, 128,  17);

        // descriptive name, e.g. MD5
        final String name;

        // size of the MAC value (and MAC key) in bytes
        final int size;

        // block size of the underlying hash algorithm
        final int hashBlockSize;

        // minimal padding size of the underlying hash algorithm
        final int minimalPaddingSize;

        MacAlg(String name, int size,
                int hashBlockSize, int minimalPaddingSize) {
            this.name = name;
            this.size = size;
            this.hashBlockSize = hashBlockSize;
            this.minimalPaddingSize = minimalPaddingSize;
        }

        /**
         * Return an initialized MAC for this MacAlg. ProtocolVersion
         * must either be SSL30 (SSLv3 custom MAC) or TLS10 (std. HMAC).
         *
         * @exception NoSuchAlgorithmException if anything goes wrong
         */
        MAC newMac(ProtocolVersion protocolVersion, SecretKey secret)
                throws NoSuchAlgorithmException, InvalidKeyException {
            return new MAC(this, protocolVersion, secret);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * PRFs (PseudoRandom Function) from TLS specifications.
     *
     * TLS 1.1- uses a single MD5/SHA1-based PRF algorithm for generating
     * the necessary material.
     *
     * In TLS 1.2+, all existing/known CipherSuites use SHA256, however
     * new Ciphersuites (e.g. RFC 5288) can define specific PRF hash
     * algorithms.
     */
    static enum PRF {

        // PRF algorithms
        P_NONE(     "NONE",  0,   0),
        P_SHA256("SHA-256", 32,  64),
        P_SHA384("SHA-384", 48, 128),
        P_SHA512("SHA-512", 64, 128);  // not currently used.

        // PRF characteristics
        private final String prfHashAlg;
        private final int prfHashLength;
        private final int prfBlockSize;

        PRF(String prfHashAlg, int prfHashLength, int prfBlockSize) {
            this.prfHashAlg = prfHashAlg;
            this.prfHashLength = prfHashLength;
            this.prfBlockSize = prfBlockSize;
        }

        String getPRFHashAlg() {
            return prfHashAlg;
        }

        int getPRFHashLength() {
            return prfHashLength;
        }

        int getPRFBlockSize() {
            return prfBlockSize;
        }
    }

    static {
        idMap = new HashMap<Integer,CipherSuite>();
        nameMap = new HashMap<String,CipherSuite>();

        final boolean F = false;
        final boolean T = true;
        // N: ciphersuites only allowed if we are not in FIPS mode
        final boolean N = (SunJSSE.isFIPS() == false);

        /*
         * TLS Cipher Suite Registry, as of November 2015.
         *
         * http://www.iana.org/assignments/tls-parameters/tls-parameters.xml
         *
         * Range      Registration Procedures   Notes
         * 000-191    Standards Action          Refers to value of first byte
         * 192-254    Specification Required    Refers to value of first byte
         * 255        Reserved for Private Use  Refers to value of first byte
         *
         * Value      Description                                   Reference
         * 0x00,0x00  TLS_NULL_WITH_NULL_NULL                       [RFC5246]
         * 0x00,0x01  TLS_RSA_WITH_NULL_MD5                         [RFC5246]
         * 0x00,0x02  TLS_RSA_WITH_NULL_SHA                         [RFC5246]
         * 0x00,0x03  TLS_RSA_EXPORT_WITH_RC4_40_MD5                [RFC4346]
         * 0x00,0x04  TLS_RSA_WITH_RC4_128_MD5                      [RFC5246]
         * 0x00,0x05  TLS_RSA_WITH_RC4_128_SHA                      [RFC5246]
         * 0x00,0x06  TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5            [RFC4346]
         * 0x00,0x07  TLS_RSA_WITH_IDEA_CBC_SHA                     [RFC5469]
         * 0x00,0x08  TLS_RSA_EXPORT_WITH_DES40_CBC_SHA             [RFC4346]
         * 0x00,0x09  TLS_RSA_WITH_DES_CBC_SHA                      [RFC5469]
         * 0x00,0x0A  TLS_RSA_WITH_3DES_EDE_CBC_SHA                 [RFC5246]
         * 0x00,0x0B  TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA          [RFC4346]
         * 0x00,0x0C  TLS_DH_DSS_WITH_DES_CBC_SHA                   [RFC5469]
         * 0x00,0x0D  TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA              [RFC5246]
         * 0x00,0x0E  TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA          [RFC4346]
         * 0x00,0x0F  TLS_DH_RSA_WITH_DES_CBC_SHA                   [RFC5469]
         * 0x00,0x10  TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA              [RFC5246]
         * 0x00,0x11  TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA         [RFC4346]
         * 0x00,0x12  TLS_DHE_DSS_WITH_DES_CBC_SHA                  [RFC5469]
         * 0x00,0x13  TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA             [RFC5246]
         * 0x00,0x14  TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA         [RFC4346]
         * 0x00,0x15  TLS_DHE_RSA_WITH_DES_CBC_SHA                  [RFC5469]
         * 0x00,0x16  TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA             [RFC5246]
         * 0x00,0x17  TLS_DH_anon_EXPORT_WITH_RC4_40_MD5            [RFC4346]
         * 0x00,0x18  TLS_DH_anon_WITH_RC4_128_MD5                  [RFC5246]
         * 0x00,0x19  TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA         [RFC4346]
         * 0x00,0x1A  TLS_DH_anon_WITH_DES_CBC_SHA                  [RFC5469]
         * 0x00,0x1B  TLS_DH_anon_WITH_3DES_EDE_CBC_SHA             [RFC5246]
         * 0x00,0x1C-1D Reserved to avoid conflicts with SSLv3      [RFC5246]
         * 0x00,0x1E  TLS_KRB5_WITH_DES_CBC_SHA                     [RFC2712]
         * 0x00,0x1F  TLS_KRB5_WITH_3DES_EDE_CBC_SHA                [RFC2712]
         * 0x00,0x20  TLS_KRB5_WITH_RC4_128_SHA                     [RFC2712]
         * 0x00,0x21  TLS_KRB5_WITH_IDEA_CBC_SHA                    [RFC2712]
         * 0x00,0x22  TLS_KRB5_WITH_DES_CBC_MD5                     [RFC2712]
         * 0x00,0x23  TLS_KRB5_WITH_3DES_EDE_CBC_MD5                [RFC2712]
         * 0x00,0x24  TLS_KRB5_WITH_RC4_128_MD5                     [RFC2712]
         * 0x00,0x25  TLS_KRB5_WITH_IDEA_CBC_MD5                    [RFC2712]
         * 0x00,0x26  TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA           [RFC2712]
         * 0x00,0x27  TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA           [RFC2712]
         * 0x00,0x28  TLS_KRB5_EXPORT_WITH_RC4_40_SHA               [RFC2712]
         * 0x00,0x29  TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5           [RFC2712]
         * 0x00,0x2A  TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5           [RFC2712]
         * 0x00,0x2B  TLS_KRB5_EXPORT_WITH_RC4_40_MD5               [RFC2712]
         * 0x00,0x2C  TLS_PSK_WITH_NULL_SHA                         [RFC4785]
         * 0x00,0x2D  TLS_DHE_PSK_WITH_NULL_SHA                     [RFC4785]
         * 0x00,0x2E  TLS_RSA_PSK_WITH_NULL_SHA                     [RFC4785]
         * 0x00,0x2F  TLS_RSA_WITH_AES_128_CBC_SHA                  [RFC5246]
         * 0x00,0x30  TLS_DH_DSS_WITH_AES_128_CBC_SHA               [RFC5246]
         * 0x00,0x31  TLS_DH_RSA_WITH_AES_128_CBC_SHA               [RFC5246]
         * 0x00,0x32  TLS_DHE_DSS_WITH_AES_128_CBC_SHA              [RFC5246]
         * 0x00,0x33  TLS_DHE_RSA_WITH_AES_128_CBC_SHA              [RFC5246]
         * 0x00,0x34  TLS_DH_anon_WITH_AES_128_CBC_SHA              [RFC5246]
         * 0x00,0x35  TLS_RSA_WITH_AES_256_CBC_SHA                  [RFC5246]
         * 0x00,0x36  TLS_DH_DSS_WITH_AES_256_CBC_SHA               [RFC5246]
         * 0x00,0x37  TLS_DH_RSA_WITH_AES_256_CBC_SHA               [RFC5246]
         * 0x00,0x38  TLS_DHE_DSS_WITH_AES_256_CBC_SHA              [RFC5246]
         * 0x00,0x39  TLS_DHE_RSA_WITH_AES_256_CBC_SHA              [RFC5246]
         * 0x00,0x3A  TLS_DH_anon_WITH_AES_256_CBC_SHA              [RFC5246]
         * 0x00,0x3B  TLS_RSA_WITH_NULL_SHA256                      [RFC5246]
         * 0x00,0x3C  TLS_RSA_WITH_AES_128_CBC_SHA256               [RFC5246]
         * 0x00,0x3D  TLS_RSA_WITH_AES_256_CBC_SHA256               [RFC5246]
         * 0x00,0x3E  TLS_DH_DSS_WITH_AES_128_CBC_SHA256            [RFC5246]
         * 0x00,0x3F  TLS_DH_RSA_WITH_AES_128_CBC_SHA256            [RFC5246]
         * 0x00,0x40  TLS_DHE_DSS_WITH_AES_128_CBC_SHA256           [RFC5246]
         * 0x00,0x41  TLS_RSA_WITH_CAMELLIA_128_CBC_SHA             [RFC5932]
         * 0x00,0x42  TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA          [RFC5932]
         * 0x00,0x43  TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA          [RFC5932]
         * 0x00,0x44  TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA         [RFC5932]
         * 0x00,0x45  TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA         [RFC5932]
         * 0x00,0x46  TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA         [RFC5932]
         * 0x00,0x47-4F Reserved to avoid conflicts with
         *            deployed implementations                  [Pasi_Eronen]
         * 0x00,0x50-58 Reserved to avoid conflicts             [Pasi Eronen]
         * 0x00,0x59-5C Reserved to avoid conflicts with
         *            deployed implementations                  [Pasi_Eronen]
         * 0x00,0x5D-5F Unassigned
         * 0x00,0x60-66 Reserved to avoid conflicts with widely
         *            deployed implementations                  [Pasi_Eronen]
         * 0x00,0x67  TLS_DHE_RSA_WITH_AES_128_CBC_SHA256           [RFC5246]
         * 0x00,0x68  TLS_DH_DSS_WITH_AES_256_CBC_SHA256            [RFC5246]
         * 0x00,0x69  TLS_DH_RSA_WITH_AES_256_CBC_SHA256            [RFC5246]
         * 0x00,0x6A  TLS_DHE_DSS_WITH_AES_256_CBC_SHA256           [RFC5246]
         * 0x00,0x6B  TLS_DHE_RSA_WITH_AES_256_CBC_SHA256           [RFC5246]
         * 0x00,0x6C  TLS_DH_anon_WITH_AES_128_CBC_SHA256           [RFC5246]
         * 0x00,0x6D  TLS_DH_anon_WITH_AES_256_CBC_SHA256           [RFC5246]
         * 0x00,0x6E-83 Unassigned
         * 0x00,0x84  TLS_RSA_WITH_CAMELLIA_256_CBC_SHA             [RFC5932]
         * 0x00,0x85  TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA          [RFC5932]
         * 0x00,0x86  TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA          [RFC5932]
         * 0x00,0x87  TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA         [RFC5932]
         * 0x00,0x88  TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA         [RFC5932]
         * 0x00,0x89  TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA         [RFC5932]
         * 0x00,0x8A  TLS_PSK_WITH_RC4_128_SHA                      [RFC4279]
         * 0x00,0x8B  TLS_PSK_WITH_3DES_EDE_CBC_SHA                 [RFC4279]
         * 0x00,0x8C  TLS_PSK_WITH_AES_128_CBC_SHA                  [RFC4279]
         * 0x00,0x8D  TLS_PSK_WITH_AES_256_CBC_SHA                  [RFC4279]
         * 0x00,0x8E  TLS_DHE_PSK_WITH_RC4_128_SHA                  [RFC4279]
         * 0x00,0x8F  TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA             [RFC4279]
         * 0x00,0x90  TLS_DHE_PSK_WITH_AES_128_CBC_SHA              [RFC4279]
         * 0x00,0x91  TLS_DHE_PSK_WITH_AES_256_CBC_SHA              [RFC4279]
         * 0x00,0x92  TLS_RSA_PSK_WITH_RC4_128_SHA                  [RFC4279]
         * 0x00,0x93  TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA             [RFC4279]
         * 0x00,0x94  TLS_RSA_PSK_WITH_AES_128_CBC_SHA              [RFC4279]
         * 0x00,0x95  TLS_RSA_PSK_WITH_AES_256_CBC_SHA              [RFC4279]
         * 0x00,0x96  TLS_RSA_WITH_SEED_CBC_SHA                     [RFC4162]
         * 0x00,0x97  TLS_DH_DSS_WITH_SEED_CBC_SHA                  [RFC4162]
         * 0x00,0x98  TLS_DH_RSA_WITH_SEED_CBC_SHA                  [RFC4162]
         * 0x00,0x99  TLS_DHE_DSS_WITH_SEED_CBC_SHA                 [RFC4162]
         * 0x00,0x9A  TLS_DHE_RSA_WITH_SEED_CBC_SHA                 [RFC4162]
         * 0x00,0x9B  TLS_DH_anon_WITH_SEED_CBC_SHA                 [RFC4162]
         * 0x00,0x9C  TLS_RSA_WITH_AES_128_GCM_SHA256               [RFC5288]
         * 0x00,0x9D  TLS_RSA_WITH_AES_256_GCM_SHA384               [RFC5288]
         * 0x00,0x9E  TLS_DHE_RSA_WITH_AES_128_GCM_SHA256           [RFC5288]
         * 0x00,0x9F  TLS_DHE_RSA_WITH_AES_256_GCM_SHA384           [RFC5288]
         * 0x00,0xA0  TLS_DH_RSA_WITH_AES_128_GCM_SHA256            [RFC5288]
         * 0x00,0xA1  TLS_DH_RSA_WITH_AES_256_GCM_SHA384            [RFC5288]
         * 0x00,0xA2  TLS_DHE_DSS_WITH_AES_128_GCM_SHA256           [RFC5288]
         * 0x00,0xA3  TLS_DHE_DSS_WITH_AES_256_GCM_SHA384           [RFC5288]
         * 0x00,0xA4  TLS_DH_DSS_WITH_AES_128_GCM_SHA256            [RFC5288]
         * 0x00,0xA5  TLS_DH_DSS_WITH_AES_256_GCM_SHA384            [RFC5288]
         * 0x00,0xA6  TLS_DH_anon_WITH_AES_128_GCM_SHA256           [RFC5288]
         * 0x00,0xA7  TLS_DH_anon_WITH_AES_256_GCM_SHA384           [RFC5288]
         * 0x00,0xA8  TLS_PSK_WITH_AES_128_GCM_SHA256               [RFC5487]
         * 0x00,0xA9  TLS_PSK_WITH_AES_256_GCM_SHA384               [RFC5487]
         * 0x00,0xAA  TLS_DHE_PSK_WITH_AES_128_GCM_SHA256           [RFC5487]
         * 0x00,0xAB  TLS_DHE_PSK_WITH_AES_256_GCM_SHA384           [RFC5487]
         * 0x00,0xAC  TLS_RSA_PSK_WITH_AES_128_GCM_SHA256           [RFC5487]
         * 0x00,0xAD  TLS_RSA_PSK_WITH_AES_256_GCM_SHA384           [RFC5487]
         * 0x00,0xAE  TLS_PSK_WITH_AES_128_CBC_SHA256               [RFC5487]
         * 0x00,0xAF  TLS_PSK_WITH_AES_256_CBC_SHA384               [RFC5487]
         * 0x00,0xB0  TLS_PSK_WITH_NULL_SHA256                      [RFC5487]
         * 0x00,0xB1  TLS_PSK_WITH_NULL_SHA384                      [RFC5487]
         * 0x00,0xB2  TLS_DHE_PSK_WITH_AES_128_CBC_SHA256           [RFC5487]
         * 0x00,0xB3  TLS_DHE_PSK_WITH_AES_256_CBC_SHA384           [RFC5487]
         * 0x00,0xB4  TLS_DHE_PSK_WITH_NULL_SHA256                  [RFC5487]
         * 0x00,0xB5  TLS_DHE_PSK_WITH_NULL_SHA384                  [RFC5487]
         * 0x00,0xB6  TLS_RSA_PSK_WITH_AES_128_CBC_SHA256           [RFC5487]
         * 0x00,0xB7  TLS_RSA_PSK_WITH_AES_256_CBC_SHA384           [RFC5487]
         * 0x00,0xB8  TLS_RSA_PSK_WITH_NULL_SHA256                  [RFC5487]
         * 0x00,0xB9  TLS_RSA_PSK_WITH_NULL_SHA384                  [RFC5487]
         * 0x00,0xBA  TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256          [RFC5932]
         * 0x00,0xBB  TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256       [RFC5932]
         * 0x00,0xBC  TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256       [RFC5932]
         * 0x00,0xBD  TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256      [RFC5932]
         * 0x00,0xBE  TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256      [RFC5932]
         * 0x00,0xBF  TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256      [RFC5932]
         * 0x00,0xC0  TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256          [RFC5932]
         * 0x00,0xC1  TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256       [RFC5932]
         * 0x00,0xC2  TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256       [RFC5932]
         * 0x00,0xC3  TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256      [RFC5932]
         * 0x00,0xC4  TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256      [RFC5932]
         * 0x00,0xC5  TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256      [RFC5932]
         * 0x00,0xC6-FE         Unassigned
         * 0x00,0xFF  TLS_EMPTY_RENEGOTIATION_INFO_SCSV             [RFC5746]
         * 0x01-55,*  Unassigned
         * 0x56,0x00  TLS_FALLBACK_SCSV                             [RFC7507]
         * 0x56,0x01-0xC0,0x00  Unassigned
         * 0xC0,0x01  TLS_ECDH_ECDSA_WITH_NULL_SHA                  [RFC4492]
         * 0xC0,0x02  TLS_ECDH_ECDSA_WITH_RC4_128_SHA               [RFC4492]
         * 0xC0,0x03  TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA          [RFC4492]
         * 0xC0,0x04  TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA           [RFC4492]
         * 0xC0,0x05  TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA           [RFC4492]
         * 0xC0,0x06  TLS_ECDHE_ECDSA_WITH_NULL_SHA                 [RFC4492]
         * 0xC0,0x07  TLS_ECDHE_ECDSA_WITH_RC4_128_SHA              [RFC4492]
         * 0xC0,0x08  TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA         [RFC4492]
         * 0xC0,0x09  TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA          [RFC4492]
         * 0xC0,0x0A  TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA          [RFC4492]
         * 0xC0,0x0B  TLS_ECDH_RSA_WITH_NULL_SHA                    [RFC4492]
         * 0xC0,0x0C  TLS_ECDH_RSA_WITH_RC4_128_SHA                 [RFC4492]
         * 0xC0,0x0D  TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA            [RFC4492]
         * 0xC0,0x0E  TLS_ECDH_RSA_WITH_AES_128_CBC_SHA             [RFC4492]
         * 0xC0,0x0F  TLS_ECDH_RSA_WITH_AES_256_CBC_SHA             [RFC4492]
         * 0xC0,0x10  TLS_ECDHE_RSA_WITH_NULL_SHA                   [RFC4492]
         * 0xC0,0x11  TLS_ECDHE_RSA_WITH_RC4_128_SHA                [RFC4492]
         * 0xC0,0x12  TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA           [RFC4492]
         * 0xC0,0x13  TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA            [RFC4492]
         * 0xC0,0x14  TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA            [RFC4492]
         * 0xC0,0x15  TLS_ECDH_anon_WITH_NULL_SHA                   [RFC4492]
         * 0xC0,0x16  TLS_ECDH_anon_WITH_RC4_128_SHA                [RFC4492]
         * 0xC0,0x17  TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA           [RFC4492]
         * 0xC0,0x18  TLS_ECDH_anon_WITH_AES_128_CBC_SHA            [RFC4492]
         * 0xC0,0x19  TLS_ECDH_anon_WITH_AES_256_CBC_SHA            [RFC4492]
         * 0xC0,0x1A  TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA             [RFC5054]
         * 0xC0,0x1B  TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA         [RFC5054]
         * 0xC0,0x1C  TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA         [RFC5054]
         * 0xC0,0x1D  TLS_SRP_SHA_WITH_AES_128_CBC_SHA              [RFC5054]
         * 0xC0,0x1E  TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA          [RFC5054]
         * 0xC0,0x1F  TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA          [RFC5054]
         * 0xC0,0x20  TLS_SRP_SHA_WITH_AES_256_CBC_SHA              [RFC5054]
         * 0xC0,0x21  TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA          [RFC5054]
         * 0xC0,0x22  TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA          [RFC5054]
         * 0xC0,0x23  TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256       [RFC5289]
         * 0xC0,0x24  TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384       [RFC5289]
         * 0xC0,0x25  TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256        [RFC5289]
         * 0xC0,0x26  TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384        [RFC5289]
         * 0xC0,0x27  TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256         [RFC5289]
         * 0xC0,0x28  TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384         [RFC5289]
         * 0xC0,0x29  TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256          [RFC5289]
         * 0xC0,0x2A  TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384          [RFC5289]
         * 0xC0,0x2B  TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256       [RFC5289]
         * 0xC0,0x2C  TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384       [RFC5289]
         * 0xC0,0x2D  TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256        [RFC5289]
         * 0xC0,0x2E  TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384        [RFC5289]
         * 0xC0,0x2F  TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256         [RFC5289]
         * 0xC0,0x30  TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384         [RFC5289]
         * 0xC0,0x31  TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256          [RFC5289]
         * 0xC0,0x32  TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384          [RFC5289]
         * 0xC0,0x33  TLS_ECDHE_PSK_WITH_RC4_128_SHA                [RFC5489]
         * 0xC0,0x34  TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA           [RFC5489]
         * 0xC0,0x35  TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA            [RFC5489]
         * 0xC0,0x36  TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA            [RFC5489]
         * 0xC0,0x37  TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256         [RFC5489]
         * 0xC0,0x38  TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384         [RFC5489]
         * 0xC0,0x39  TLS_ECDHE_PSK_WITH_NULL_SHA                   [RFC5489]
         * 0xC0,0x3A  TLS_ECDHE_PSK_WITH_NULL_SHA256                [RFC5489]
         * 0xC0,0x3B  TLS_ECDHE_PSK_WITH_NULL_SHA384                [RFC5489]
         * 0xC0,0x3C  TLS_RSA_WITH_ARIA_128_CBC_SHA256              [RFC6209]
         * 0xC0,0x3D  TLS_RSA_WITH_ARIA_256_CBC_SHA384              [RFC6209]
         * 0xC0,0x3E  TLS_DH_DSS_WITH_ARIA_128_CBC_SHA256           [RFC6209]
         * 0xC0,0x3F  TLS_DH_DSS_WITH_ARIA_256_CBC_SHA384           [RFC6209]
         * 0xC0,0x40  TLS_DH_RSA_WITH_ARIA_128_CBC_SHA256           [RFC6209]
         * 0xC0,0x41  TLS_DH_RSA_WITH_ARIA_256_CBC_SHA384           [RFC6209]
         * 0xC0,0x42  TLS_DHE_DSS_WITH_ARIA_128_CBC_SHA256          [RFC6209]
         * 0xC0,0x43  TLS_DHE_DSS_WITH_ARIA_256_CBC_SHA384          [RFC6209]
         * 0xC0,0x44  TLS_DHE_RSA_WITH_ARIA_128_CBC_SHA256          [RFC6209]
         * 0xC0,0x45  TLS_DHE_RSA_WITH_ARIA_256_CBC_SHA384          [RFC6209]
         * 0xC0,0x46  TLS_DH_anon_WITH_ARIA_128_CBC_SHA256          [RFC6209]
         * 0xC0,0x47  TLS_DH_anon_WITH_ARIA_256_CBC_SHA384          [RFC6209]
         * 0xC0,0x48  TLS_ECDHE_ECDSA_WITH_ARIA_128_CBC_SHA256      [RFC6209]
         * 0xC0,0x49  TLS_ECDHE_ECDSA_WITH_ARIA_256_CBC_SHA384      [RFC6209]
         * 0xC0,0x4A  TLS_ECDH_ECDSA_WITH_ARIA_128_CBC_SHA256       [RFC6209]
         * 0xC0,0x4B  TLS_ECDH_ECDSA_WITH_ARIA_256_CBC_SHA384       [RFC6209]
         * 0xC0,0x4C  TLS_ECDHE_RSA_WITH_ARIA_128_CBC_SHA256        [RFC6209]
         * 0xC0,0x4D  TLS_ECDHE_RSA_WITH_ARIA_256_CBC_SHA384        [RFC6209]
         * 0xC0,0x4E  TLS_ECDH_RSA_WITH_ARIA_128_CBC_SHA256         [RFC6209]
         * 0xC0,0x4F  TLS_ECDH_RSA_WITH_ARIA_256_CBC_SHA384         [RFC6209]
         * 0xC0,0x50  TLS_RSA_WITH_ARIA_128_GCM_SHA256              [RFC6209]
         * 0xC0,0x51  TLS_RSA_WITH_ARIA_256_GCM_SHA384              [RFC6209]
         * 0xC0,0x52  TLS_DHE_RSA_WITH_ARIA_128_GCM_SHA256          [RFC6209]
         * 0xC0,0x53  TLS_DHE_RSA_WITH_ARIA_256_GCM_SHA384          [RFC6209]
         * 0xC0,0x54  TLS_DH_RSA_WITH_ARIA_128_GCM_SHA256           [RFC6209]
         * 0xC0,0x55  TLS_DH_RSA_WITH_ARIA_256_GCM_SHA384           [RFC6209]
         * 0xC0,0x56  TLS_DHE_DSS_WITH_ARIA_128_GCM_SHA256          [RFC6209]
         * 0xC0,0x57  TLS_DHE_DSS_WITH_ARIA_256_GCM_SHA384          [RFC6209]
         * 0xC0,0x58  TLS_DH_DSS_WITH_ARIA_128_GCM_SHA256           [RFC6209]
         * 0xC0,0x59  TLS_DH_DSS_WITH_ARIA_256_GCM_SHA384           [RFC6209]
         * 0xC0,0x5A  TLS_DH_anon_WITH_ARIA_128_GCM_SHA256          [RFC6209]
         * 0xC0,0x5B  TLS_DH_anon_WITH_ARIA_256_GCM_SHA384          [RFC6209]
         * 0xC0,0x5C  TLS_ECDHE_ECDSA_WITH_ARIA_128_GCM_SHA256      [RFC6209]
         * 0xC0,0x5D  TLS_ECDHE_ECDSA_WITH_ARIA_256_GCM_SHA384      [RFC6209]
         * 0xC0,0x5E  TLS_ECDH_ECDSA_WITH_ARIA_128_GCM_SHA256       [RFC6209]
         * 0xC0,0x5F  TLS_ECDH_ECDSA_WITH_ARIA_256_GCM_SHA384       [RFC6209]
         * 0xC0,0x60  TLS_ECDHE_RSA_WITH_ARIA_128_GCM_SHA256        [RFC6209]
         * 0xC0,0x61  TLS_ECDHE_RSA_WITH_ARIA_256_GCM_SHA384        [RFC6209]
         * 0xC0,0x62  TLS_ECDH_RSA_WITH_ARIA_128_GCM_SHA256         [RFC6209]
         * 0xC0,0x63  TLS_ECDH_RSA_WITH_ARIA_256_GCM_SHA384         [RFC6209]
         * 0xC0,0x64  TLS_PSK_WITH_ARIA_128_CBC_SHA256              [RFC6209]
         * 0xC0,0x65  TLS_PSK_WITH_ARIA_256_CBC_SHA384              [RFC6209]
         * 0xC0,0x66  TLS_DHE_PSK_WITH_ARIA_128_CBC_SHA256          [RFC6209]
         * 0xC0,0x67  TLS_DHE_PSK_WITH_ARIA_256_CBC_SHA384          [RFC6209]
         * 0xC0,0x68  TLS_RSA_PSK_WITH_ARIA_128_CBC_SHA256          [RFC6209]
         * 0xC0,0x69  TLS_RSA_PSK_WITH_ARIA_256_CBC_SHA384          [RFC6209]
         * 0xC0,0x6A  TLS_PSK_WITH_ARIA_128_GCM_SHA256              [RFC6209]
         * 0xC0,0x6B  TLS_PSK_WITH_ARIA_256_GCM_SHA384              [RFC6209]
         * 0xC0,0x6C  TLS_DHE_PSK_WITH_ARIA_128_GCM_SHA256          [RFC6209]
         * 0xC0,0x6D  TLS_DHE_PSK_WITH_ARIA_256_GCM_SHA384          [RFC6209]
         * 0xC0,0x6E  TLS_RSA_PSK_WITH_ARIA_128_GCM_SHA256          [RFC6209]
         * 0xC0,0x6F  TLS_RSA_PSK_WITH_ARIA_256_GCM_SHA384          [RFC6209]
         * 0xC0,0x70  TLS_ECDHE_PSK_WITH_ARIA_128_CBC_SHA256        [RFC6209]
         * 0xC0,0x71  TLS_ECDHE_PSK_WITH_ARIA_256_CBC_SHA384        [RFC6209]
         * 0xC0,0x72  TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256  [RFC6367]
         * 0xC0,0x73  TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384  [RFC6367]
         * 0xC0,0x74  TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256   [RFC6367]
         * 0xC0,0x75  TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384   [RFC6367]
         * 0xC0,0x76  TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256    [RFC6367]
         * 0xC0,0x77  TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384    [RFC6367]
         * 0xC0,0x78  TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256     [RFC6367]
         * 0xC0,0x79  TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384     [RFC6367]
         * 0xC0,0x7A  TLS_RSA_WITH_CAMELLIA_128_GCM_SHA256          [RFC6367]
         * 0xC0,0x7B  TLS_RSA_WITH_CAMELLIA_256_GCM_SHA384          [RFC6367]
         * 0xC0,0x7C  TLS_DHE_RSA_WITH_CAMELLIA_128_GCM_SHA256      [RFC6367]
         * 0xC0,0x7D  TLS_DHE_RSA_WITH_CAMELLIA_256_GCM_SHA384      [RFC6367]
         * 0xC0,0x7E  TLS_DH_RSA_WITH_CAMELLIA_128_GCM_SHA256       [RFC6367]
         * 0xC0,0x7F  TLS_DH_RSA_WITH_CAMELLIA_256_GCM_SHA384       [RFC6367]
         * 0xC0,0x80  TLS_DHE_DSS_WITH_CAMELLIA_128_GCM_SHA256      [RFC6367]
         * 0xC0,0x81  TLS_DHE_DSS_WITH_CAMELLIA_256_GCM_SHA384      [RFC6367]
         * 0xC0,0x82  TLS_DH_DSS_WITH_CAMELLIA_128_GCM_SHA256       [RFC6367]
         * 0xC0,0x83  TLS_DH_DSS_WITH_CAMELLIA_256_GCM_SHA384       [RFC6367]
         * 0xC0,0x84  TLS_DH_anon_WITH_CAMELLIA_128_GCM_SHA256      [RFC6367]
         * 0xC0,0x85  TLS_DH_anon_WITH_CAMELLIA_256_GCM_SHA384      [RFC6367]
         * 0xC0,0x86  TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_GCM_SHA256  [RFC6367]
         * 0xC0,0x87  TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_GCM_SHA384  [RFC6367]
         * 0xC0,0x88  TLS_ECDH_ECDSA_WITH_CAMELLIA_128_GCM_SHA256   [RFC6367]
         * 0xC0,0x89  TLS_ECDH_ECDSA_WITH_CAMELLIA_256_GCM_SHA384   [RFC6367]
         * 0xC0,0x8A  TLS_ECDHE_RSA_WITH_CAMELLIA_128_GCM_SHA256    [RFC6367]
         * 0xC0,0x8B  TLS_ECDHE_RSA_WITH_CAMELLIA_256_GCM_SHA384    [RFC6367]
         * 0xC0,0x8C  TLS_ECDH_RSA_WITH_CAMELLIA_128_GCM_SHA256     [RFC6367]
         * 0xC0,0x8D  TLS_ECDH_RSA_WITH_CAMELLIA_256_GCM_SHA384     [RFC6367]
         * 0xC0,0x8E  TLS_PSK_WITH_CAMELLIA_128_GCM_SHA256          [RFC6367]
         * 0xC0,0x8F  TLS_PSK_WITH_CAMELLIA_256_GCM_SHA384          [RFC6367]
         * 0xC0,0x90  TLS_DHE_PSK_WITH_CAMELLIA_128_GCM_SHA256      [RFC6367]
         * 0xC0,0x91  TLS_DHE_PSK_WITH_CAMELLIA_256_GCM_SHA384      [RFC6367]
         * 0xC0,0x92  TLS_RSA_PSK_WITH_CAMELLIA_128_GCM_SHA256      [RFC6367]
         * 0xC0,0x93  TLS_RSA_PSK_WITH_CAMELLIA_256_GCM_SHA384      [RFC6367]
         * 0xC0,0x94  TLS_PSK_WITH_CAMELLIA_128_CBC_SHA256          [RFC6367]
         * 0xC0,0x95  TLS_PSK_WITH_CAMELLIA_256_CBC_SHA384          [RFC6367]
         * 0xC0,0x96  TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256      [RFC6367]
         * 0xC0,0x97  TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384      [RFC6367]
         * 0xC0,0x98  TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256      [RFC6367]
         * 0xC0,0x99  TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384      [RFC6367]
         * 0xC0,0x9A  TLS_ECDHE_PSK_WITH_CAMELLIA_128_CBC_SHA256    [RFC6367]
         * 0xC0,0x9B  TLS_ECDHE_PSK_WITH_CAMELLIA_256_CBC_SHA384    [RFC6367]
         * 0xC0,0x9C  TLS_RSA_WITH_AES_128_CCM                      [RFC6655]
         * 0xC0,0x9D  TLS_RSA_WITH_AES_256_CCM                      [RFC6655]
         * 0xC0,0x9E  TLS_DHE_RSA_WITH_AES_128_CCM                  [RFC6655]
         * 0xC0,0x9F  TLS_DHE_RSA_WITH_AES_256_CCM                  [RFC6655]
         * 0xC0,0xA0  TLS_RSA_WITH_AES_128_CCM_8                    [RFC6655]
         * 0xC0,0xA1  TLS_RSA_WITH_AES_256_CCM_8                    [RFC6655]
         * 0xC0,0xA2  TLS_DHE_RSA_WITH_AES_128_CCM_8                [RFC6655]
         * 0xC0,0xA3  TLS_DHE_RSA_WITH_AES_256_CCM_8                [RFC6655]
         * 0xC0,0xA4  TLS_PSK_WITH_AES_128_CCM                      [RFC6655]
         * 0xC0,0xA5  TLS_PSK_WITH_AES_256_CCM                      [RFC6655]
         * 0xC0,0xA6  TLS_DHE_PSK_WITH_AES_128_CCM                  [RFC6655]
         * 0xC0,0xA7  TLS_DHE_PSK_WITH_AES_256_CCM                  [RFC6655]
         * 0xC0,0xA8  TLS_PSK_WITH_AES_128_CCM_8                    [RFC6655]
         * 0xC0,0xA9  TLS_PSK_WITH_AES_256_CCM_8                    [RFC6655]
         * 0xC0,0xAA  TLS_PSK_DHE_WITH_AES_128_CCM_8                [RFC6655]
         * 0xC0,0xAB  TLS_PSK_DHE_WITH_AES_256_CCM_8                [RFC6655]
         * 0xC0,0xAC  TLS_ECDHE_ECDSA_WITH_AES_128_CCM              [RFC7251]
         * 0xC0,0xAD  TLS_ECDHE_ECDSA_WITH_AES_256_CCM              [RFC7251]
         * 0xC0,0xAE  TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8            [RFC7251]
         * 0xC0,0xAF  TLS_ECDHE_ECDSA_WITH_AES_256_CCM_8            [RFC7251]
         * 0xC0,0xB0-FF  Unassigned
         * 0xC1-FD,*  Unassigned
         * 0xFE,0x00-FD Unassigned
         * 0xFE,0xFE-FF Reserved to avoid conflicts with widely
         *            deployed implementations                  [Pasi_Eronen]
         * 0xFF,0x00-FF Reserved for Private Use                [RFC5246]
         */

        add("SSL_NULL_WITH_NULL_NULL", 0x0000,
                1,      K_NULL,     B_NULL,     M_NULL,     F);

        /*
         * Definition of the CipherSuites that are enabled by default.
         * They are listed in preference order, most preferred first, using
         * the following criteria:
         * 1. Prefer Suite B compliant cipher suites, see RFC6460 (To be
         *    changed later, see below).
         * 2. Prefer the stronger bulk cipher, in the order of AES_256(GCM),
         *    AES_128(GCM), AES_256, AES_128, 3DES-EDE.
         * 3. Prefer the stronger MAC algorithm, in the order of SHA384,
         *    SHA256, SHA, MD5.
         * 4. Prefer the better performance of key exchange and digital
         *    signature algorithm, in the order of ECDHE-ECDSA, ECDHE-RSA,
         *    RSA, ECDH-ECDSA, ECDH-RSA, DHE-RSA, DHE-DSS.
         */
        int p = DEFAULT_SUITES_PRIORITY * 2;

        // shorten names to fit the following table cleanly.
        int max = ProtocolVersion.LIMIT_MAX_VALUE;
        int tls11 = ProtocolVersion.TLS11.v;
        int tls12 = ProtocolVersion.TLS12.v;

        //  ID           Key Exchange   Cipher     A  obs  suprt  PRF
        //  ======       ============   =========  =  ===  =====  ========

        // Suite B compliant cipher suites, see RFC 6460.
        //
        // Note that, at present this provider is not Suite B compliant. The
        // preference order of the GCM cipher suites does not follow the spec
        // of RFC 6460.  In this section, only two cipher suites are listed
        // so that applications can make use of Suite-B compliant cipher
        // suite firstly.
        add("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",  0xc02c, --p,
            K_ECDHE_ECDSA, B_AES_256_GCM, M_NULL,   T, max, tls12, P_SHA384);
        add("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",  0xc02b, --p,
            K_ECDHE_ECDSA, B_AES_128_GCM, M_NULL,   T, max, tls12, P_SHA256);

        // AES_256(GCM)
        add("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",    0xc030, --p,
            K_ECDHE_RSA,   B_AES_256_GCM, M_NULL,   T, max, tls12, P_SHA384);
        add("TLS_RSA_WITH_AES_256_GCM_SHA384",          0x009d, --p,
            K_RSA,         B_AES_256_GCM, M_NULL,   T, max, tls12, P_SHA384);
        add("TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",   0xc02e, --p,
            K_ECDH_ECDSA,  B_AES_256_GCM, M_NULL,   T, max, tls12, P_SHA384);
        add("TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",     0xc032, --p,
            K_ECDH_RSA,    B_AES_256_GCM, M_NULL,   T, max, tls12, P_SHA384);
        add("TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",      0x009f, --p,
            K_DHE_RSA,     B_AES_256_GCM, M_NULL,   T, max, tls12, P_SHA384);
        add("TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",      0x00a3, --p,
            K_DHE_DSS,     B_AES_256_GCM, M_NULL,   T, max, tls12, P_SHA384);

        // AES_128(GCM)
        add("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",    0xc02f, --p,
            K_ECDHE_RSA,   B_AES_128_GCM, M_NULL,   T, max, tls12, P_SHA256);
        add("TLS_RSA_WITH_AES_128_GCM_SHA256",          0x009c, --p,
            K_RSA,         B_AES_128_GCM, M_NULL,   T, max, tls12, P_SHA256);
        add("TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",   0xc02d, --p,
            K_ECDH_ECDSA,  B_AES_128_GCM, M_NULL,   T, max, tls12, P_SHA256);
        add("TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",     0xc031, --p,
            K_ECDH_RSA,    B_AES_128_GCM, M_NULL,   T, max, tls12, P_SHA256);
        add("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",      0x009e, --p,
            K_DHE_RSA,     B_AES_128_GCM, M_NULL,   T, max, tls12, P_SHA256);
        add("TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",      0x00a2, --p,
            K_DHE_DSS,     B_AES_128_GCM, M_NULL,   T, max, tls12, P_SHA256);

        // AES_256(CBC)
        add("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",  0xc024, --p,
            K_ECDHE_ECDSA, B_AES_256,     M_SHA384, T, max, tls12, P_SHA384);
        add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",    0xc028, --p,
            K_ECDHE_RSA,   B_AES_256,     M_SHA384, T, max, tls12, P_SHA384);
        add("TLS_RSA_WITH_AES_256_CBC_SHA256",          0x003d, --p,
            K_RSA,         B_AES_256,     M_SHA256, T, max, tls12, P_SHA256);
        add("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",   0xc026, --p,
            K_ECDH_ECDSA,  B_AES_256,     M_SHA384, T, max, tls12, P_SHA384);
        add("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",     0xc02a, --p,
            K_ECDH_RSA,    B_AES_256,     M_SHA384, T, max, tls12, P_SHA384);
        add("TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",      0x006b, --p,
            K_DHE_RSA,     B_AES_256,     M_SHA256, T, max, tls12, P_SHA256);
        add("TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",      0x006a, --p,
            K_DHE_DSS,     B_AES_256,     M_SHA256, T, max, tls12, P_SHA256);

        add("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",     0xC00A, --p,
            K_ECDHE_ECDSA, B_AES_256,     M_SHA,    T);
        add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",       0xC014, --p,
            K_ECDHE_RSA,   B_AES_256,     M_SHA,    T);
        add("TLS_RSA_WITH_AES_256_CBC_SHA",             0x0035, --p,
            K_RSA,         B_AES_256,     M_SHA,    T);
        add("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",      0xC005, --p,
            K_ECDH_ECDSA,  B_AES_256,     M_SHA,    T);
        add("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",        0xC00F, --p,
            K_ECDH_RSA,    B_AES_256,     M_SHA,    T);
        add("TLS_DHE_RSA_WITH_AES_256_CBC_SHA",         0x0039, --p,
            K_DHE_RSA,     B_AES_256,     M_SHA,    T);
        add("TLS_DHE_DSS_WITH_AES_256_CBC_SHA",         0x0038, --p,
            K_DHE_DSS,     B_AES_256,     M_SHA,    T);

        // AES_128(CBC)
        add("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",  0xc023, --p,
            K_ECDHE_ECDSA, B_AES_128,     M_SHA256, T, max, tls12, P_SHA256);
        add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",    0xc027, --p,
            K_ECDHE_RSA,   B_AES_128,     M_SHA256, T, max, tls12, P_SHA256);
        add("TLS_RSA_WITH_AES_128_CBC_SHA256",          0x003c, --p,
            K_RSA,         B_AES_128,     M_SHA256, T, max, tls12, P_SHA256);
        add("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",   0xc025, --p,
            K_ECDH_ECDSA,  B_AES_128,     M_SHA256, T, max, tls12, P_SHA256);
        add("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",     0xc029, --p,
            K_ECDH_RSA,    B_AES_128,     M_SHA256, T, max, tls12, P_SHA256);
        add("TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",      0x0067, --p,
            K_DHE_RSA,     B_AES_128,     M_SHA256, T, max, tls12, P_SHA256);
        add("TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",      0x0040, --p,
            K_DHE_DSS,     B_AES_128,     M_SHA256, T, max, tls12, P_SHA256);

        add("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",     0xC009, --p,
            K_ECDHE_ECDSA, B_AES_128,     M_SHA,    T);
        add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",       0xC013, --p,
            K_ECDHE_RSA,   B_AES_128,     M_SHA,    T);
        add("TLS_RSA_WITH_AES_128_CBC_SHA",             0x002f, --p,
            K_RSA,         B_AES_128,     M_SHA,    T);
        add("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",      0xC004, --p,
            K_ECDH_ECDSA,  B_AES_128,     M_SHA,    T);
        add("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",        0xC00E, --p,
            K_ECDH_RSA,    B_AES_128,     M_SHA,    T);
        add("TLS_DHE_RSA_WITH_AES_128_CBC_SHA",         0x0033, --p,
            K_DHE_RSA,     B_AES_128,     M_SHA,    T);
        add("TLS_DHE_DSS_WITH_AES_128_CBC_SHA",         0x0032, --p,
            K_DHE_DSS,     B_AES_128,     M_SHA,    T);

        // 3DES_EDE
        add("TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",    0xC008, --p,
            K_ECDHE_ECDSA, B_3DES,        M_SHA,    T);
        add("TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",      0xC012, --p,
            K_ECDHE_RSA,   B_3DES,        M_SHA,    T);
        add("SSL_RSA_WITH_3DES_EDE_CBC_SHA",            0x000a, --p,
            K_RSA,         B_3DES,        M_SHA,    T);
        add("TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",     0xC003, --p,
            K_ECDH_ECDSA,  B_3DES,        M_SHA,    T);
        add("TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",       0xC00D, --p,
            K_ECDH_RSA,    B_3DES,        M_SHA,    T);
        add("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",        0x0016, --p,
            K_DHE_RSA,     B_3DES,        M_SHA,    T);
        add("SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",        0x0013, --p,
            K_DHE_DSS,     B_3DES,        M_SHA,    N);

        // Renegotiation protection request Signalling Cipher Suite Value (SCSV)
        add("TLS_EMPTY_RENEGOTIATION_INFO_SCSV",        0x00ff, --p,
            K_SCSV,        B_NULL,        M_NULL,   T);

        /*
         * Definition of the CipherSuites that are supported but not enabled
         * by default.
         * They are listed in preference order, preferred first, using the
         * following criteria:
         * 1. CipherSuites for KRB5 need additional KRB5 service
         *    configuration, and these suites are not common in practice,
         *    so we put KRB5 based cipher suites at the end of the supported
         *    list.
         * 2. If a cipher suite has been obsoleted, we put it at the end of
         *    the list.
         * 3. Prefer the stronger bulk cipher, in the order of AES_256,
         *    AES_128, 3DES-EDE, RC-4, DES, DES40, RC4_40, NULL.
         * 4. Prefer the stronger MAC algorithm, in the order of SHA384,
         *    SHA256, SHA, MD5.
         * 5. Prefer the better performance of key exchange and digital
         *    signature algorithm, in the order of ECDHE-ECDSA, ECDHE-RSA,
         *    RSA, ECDH-ECDSA, ECDH-RSA, DHE-RSA, DHE-DSS, anonymous.
         */
        p = DEFAULT_SUITES_PRIORITY;

        add("TLS_DH_anon_WITH_AES_256_GCM_SHA384",      0x00a7, --p,
            K_DH_ANON,     B_AES_256_GCM, M_NULL,   N, max, tls12, P_SHA384);
        add("TLS_DH_anon_WITH_AES_128_GCM_SHA256",      0x00a6, --p,
            K_DH_ANON,     B_AES_128_GCM, M_NULL,   N, max, tls12, P_SHA256);

        add("TLS_DH_anon_WITH_AES_256_CBC_SHA256",      0x006d, --p,
            K_DH_ANON,     B_AES_256,     M_SHA256, N, max, tls12, P_SHA256);
        add("TLS_ECDH_anon_WITH_AES_256_CBC_SHA",       0xC019, --p,
            K_ECDH_ANON,   B_AES_256,     M_SHA,    N);
        add("TLS_DH_anon_WITH_AES_256_CBC_SHA",         0x003a, --p,
            K_DH_ANON,     B_AES_256,     M_SHA,    N);

        add("TLS_DH_anon_WITH_AES_128_CBC_SHA256",      0x006c, --p,
            K_DH_ANON,     B_AES_128,     M_SHA256, N, max, tls12, P_SHA256);
        add("TLS_ECDH_anon_WITH_AES_128_CBC_SHA",       0xC018, --p,
            K_ECDH_ANON,   B_AES_128,     M_SHA,    N);
        add("TLS_DH_anon_WITH_AES_128_CBC_SHA",         0x0034, --p,
            K_DH_ANON,     B_AES_128,     M_SHA,    N);

        add("TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",      0xC017, --p,
            K_ECDH_ANON,   B_3DES,        M_SHA,    N);
        add("SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",        0x001b, --p,
            K_DH_ANON,     B_3DES,        M_SHA,    N);

        // RC-4
        add("TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",         0xC007, --p,
            K_ECDHE_ECDSA, B_RC4_128,     M_SHA,    N);
        add("TLS_ECDHE_RSA_WITH_RC4_128_SHA",           0xC011, --p,
            K_ECDHE_RSA,   B_RC4_128,     M_SHA,    N);
        add("SSL_RSA_WITH_RC4_128_SHA",                 0x0005, --p,
            K_RSA,         B_RC4_128,     M_SHA,    N);
        add("TLS_ECDH_ECDSA_WITH_RC4_128_SHA",          0xC002, --p,
            K_ECDH_ECDSA,  B_RC4_128,     M_SHA,    N);
        add("TLS_ECDH_RSA_WITH_RC4_128_SHA",            0xC00C, --p,
            K_ECDH_RSA,    B_RC4_128,     M_SHA,    N);
        add("SSL_RSA_WITH_RC4_128_MD5",                 0x0004, --p,
            K_RSA,         B_RC4_128,     M_MD5,    N);

        add("TLS_ECDH_anon_WITH_RC4_128_SHA",           0xC016, --p,
            K_ECDH_ANON,   B_RC4_128,     M_SHA,    N);
        add("SSL_DH_anon_WITH_RC4_128_MD5",             0x0018, --p,
            K_DH_ANON,     B_RC4_128,     M_MD5,    N);

        // weak cipher suites obsoleted in TLS 1.2
        add("SSL_RSA_WITH_DES_CBC_SHA",                 0x0009, --p,
            K_RSA,         B_DES,         M_SHA,    N, tls12);
        add("SSL_DHE_RSA_WITH_DES_CBC_SHA",             0x0015, --p,
            K_DHE_RSA,     B_DES,         M_SHA,    N, tls12);
        add("SSL_DHE_DSS_WITH_DES_CBC_SHA",             0x0012, --p,
            K_DHE_DSS,     B_DES,         M_SHA,    N, tls12);
        add("SSL_DH_anon_WITH_DES_CBC_SHA",             0x001a, --p,
            K_DH_ANON,     B_DES,         M_SHA,    N, tls12);

        // weak cipher suites obsoleted in TLS 1.1
        add("SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",        0x0008, --p,
            K_RSA_EXPORT,  B_DES_40,      M_SHA,    N, tls11);
        add("SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",    0x0014, --p,
            K_DHE_RSA,     B_DES_40,      M_SHA,    N, tls11);
        add("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",    0x0011, --p,
            K_DHE_DSS,     B_DES_40,      M_SHA,    N, tls11);
        add("SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",    0x0019, --p,
            K_DH_ANON,     B_DES_40,      M_SHA,    N, tls11);

        add("SSL_RSA_EXPORT_WITH_RC4_40_MD5",           0x0003, --p,
            K_RSA_EXPORT,  B_RC4_40,      M_MD5,    N, tls11);
        add("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",       0x0017, --p,
            K_DH_ANON,     B_RC4_40,      M_MD5,    N, tls11);

        add("TLS_RSA_WITH_NULL_SHA256",                 0x003b, --p,
            K_RSA,         B_NULL,        M_SHA256, N, max, tls12, P_SHA256);
        add("TLS_ECDHE_ECDSA_WITH_NULL_SHA",            0xC006, --p,
            K_ECDHE_ECDSA, B_NULL,        M_SHA,    N);
        add("TLS_ECDHE_RSA_WITH_NULL_SHA",              0xC010, --p,
            K_ECDHE_RSA,   B_NULL,        M_SHA,    N);
        add("SSL_RSA_WITH_NULL_SHA",                    0x0002, --p,
            K_RSA,         B_NULL,        M_SHA,    N);
        add("TLS_ECDH_ECDSA_WITH_NULL_SHA",             0xC001, --p,
            K_ECDH_ECDSA,  B_NULL,        M_SHA,    N);
        add("TLS_ECDH_RSA_WITH_NULL_SHA",               0xC00B, --p,
            K_ECDH_RSA,    B_NULL,        M_SHA,    N);
        add("TLS_ECDH_anon_WITH_NULL_SHA",              0xC015, --p,
            K_ECDH_ANON,   B_NULL,        M_SHA,    N);
        add("SSL_RSA_WITH_NULL_MD5",                    0x0001, --p,
            K_RSA,         B_NULL,        M_MD5,    N);

        // Supported Kerberos ciphersuites from RFC2712
        add("TLS_KRB5_WITH_3DES_EDE_CBC_SHA",           0x001f, --p,
            K_KRB5,        B_3DES,        M_SHA,    N);
        add("TLS_KRB5_WITH_3DES_EDE_CBC_MD5",           0x0023, --p,
            K_KRB5,        B_3DES,        M_MD5,    N);
        add("TLS_KRB5_WITH_RC4_128_SHA",                0x0020, --p,
            K_KRB5,        B_RC4_128,     M_SHA,    N);
        add("TLS_KRB5_WITH_RC4_128_MD5",                0x0024, --p,
            K_KRB5,        B_RC4_128,     M_MD5,    N);
        add("TLS_KRB5_WITH_DES_CBC_SHA",                0x001e, --p,
            K_KRB5,        B_DES,         M_SHA,    N, tls12);
        add("TLS_KRB5_WITH_DES_CBC_MD5",                0x0022, --p,
            K_KRB5,        B_DES,         M_MD5,    N, tls12);
        add("TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",      0x0026, --p,
            K_KRB5_EXPORT, B_DES_40,      M_SHA,    N, tls11);
        add("TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",      0x0029, --p,
            K_KRB5_EXPORT, B_DES_40,      M_MD5,    N, tls11);
        add("TLS_KRB5_EXPORT_WITH_RC4_40_SHA",          0x0028, --p,
            K_KRB5_EXPORT, B_RC4_40,      M_SHA,    N, tls11);
        add("TLS_KRB5_EXPORT_WITH_RC4_40_MD5",          0x002b, --p,
            K_KRB5_EXPORT, B_RC4_40,      M_MD5,    N, tls11);

        /*
         * Other values from the TLS Cipher Suite Registry, as of August 2010.
         *
         * http://www.iana.org/assignments/tls-parameters/tls-parameters.xml
         *
         * Range      Registration Procedures   Notes
         * 000-191    Standards Action          Refers to value of first byte
         * 192-254    Specification Required    Refers to value of first byte
         * 255        Reserved for Private Use  Refers to value of first byte
         */

        // Register the names of a few additional CipherSuites.
        // Makes them show up as names instead of numbers in
        // the debug output.

        // remaining unsupported ciphersuites defined in RFC2246.
        add("SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5",           0x0006);
        add("SSL_RSA_WITH_IDEA_CBC_SHA",                    0x0007);
        add("SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA",         0x000b);
        add("SSL_DH_DSS_WITH_DES_CBC_SHA",                  0x000c);
        add("SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA",             0x000d);
        add("SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA",         0x000e);
        add("SSL_DH_RSA_WITH_DES_CBC_SHA",                  0x000f);
        add("SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA",             0x0010);

        // SSL 3.0 Fortezza ciphersuites
        add("SSL_FORTEZZA_DMS_WITH_NULL_SHA",               0x001c);
        add("SSL_FORTEZZA_DMS_WITH_FORTEZZA_CBC_SHA",       0x001d);

        // 1024/56 bit exportable ciphersuites from expired internet draft
        add("SSL_RSA_EXPORT1024_WITH_DES_CBC_SHA",          0x0062);
        add("SSL_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA",      0x0063);
        add("SSL_RSA_EXPORT1024_WITH_RC4_56_SHA",           0x0064);
        add("SSL_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA",       0x0065);
        add("SSL_DHE_DSS_WITH_RC4_128_SHA",                 0x0066);

        // Netscape old and new SSL 3.0 FIPS ciphersuites
        // see http://www.mozilla.org/projects/security/pki/nss/ssl/fips-ssl-ciphersuites.html
        add("NETSCAPE_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",      0xffe0);
        add("NETSCAPE_RSA_FIPS_WITH_DES_CBC_SHA",           0xffe1);
        add("SSL_RSA_FIPS_WITH_DES_CBC_SHA",                0xfefe);
        add("SSL_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",           0xfeff);

        // Unsupported Kerberos cipher suites from RFC 2712
        add("TLS_KRB5_WITH_IDEA_CBC_SHA",                   0x0021);
        add("TLS_KRB5_WITH_IDEA_CBC_MD5",                   0x0025);
        add("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA",          0x0027);
        add("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5",          0x002a);

        // Unsupported cipher suites from RFC 4162
        add("TLS_RSA_WITH_SEED_CBC_SHA",                    0x0096);
        add("TLS_DH_DSS_WITH_SEED_CBC_SHA",                 0x0097);
        add("TLS_DH_RSA_WITH_SEED_CBC_SHA",                 0x0098);
        add("TLS_DHE_DSS_WITH_SEED_CBC_SHA",                0x0099);
        add("TLS_DHE_RSA_WITH_SEED_CBC_SHA",                0x009a);
        add("TLS_DH_anon_WITH_SEED_CBC_SHA",                0x009b);

        // Unsupported cipher suites from RFC 4279
        add("TLS_PSK_WITH_RC4_128_SHA",                     0x008a);
        add("TLS_PSK_WITH_3DES_EDE_CBC_SHA",                0x008b);
        add("TLS_PSK_WITH_AES_128_CBC_SHA",                 0x008c);
        add("TLS_PSK_WITH_AES_256_CBC_SHA",                 0x008d);
        add("TLS_DHE_PSK_WITH_RC4_128_SHA",                 0x008e);
        add("TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA",            0x008f);
        add("TLS_DHE_PSK_WITH_AES_128_CBC_SHA",             0x0090);
        add("TLS_DHE_PSK_WITH_AES_256_CBC_SHA",             0x0091);
        add("TLS_RSA_PSK_WITH_RC4_128_SHA",                 0x0092);
        add("TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA",            0x0093);
        add("TLS_RSA_PSK_WITH_AES_128_CBC_SHA",             0x0094);
        add("TLS_RSA_PSK_WITH_AES_256_CBC_SHA",             0x0095);

        // Unsupported cipher suites from RFC 4785
        add("TLS_PSK_WITH_NULL_SHA",                        0x002c);
        add("TLS_DHE_PSK_WITH_NULL_SHA",                    0x002d);
        add("TLS_RSA_PSK_WITH_NULL_SHA",                    0x002e);

        // Unsupported cipher suites from RFC 5246
        add("TLS_DH_DSS_WITH_AES_128_CBC_SHA",              0x0030);
        add("TLS_DH_RSA_WITH_AES_128_CBC_SHA",              0x0031);
        add("TLS_DH_DSS_WITH_AES_256_CBC_SHA",              0x0036);
        add("TLS_DH_RSA_WITH_AES_256_CBC_SHA",              0x0037);
        add("TLS_DH_DSS_WITH_AES_128_CBC_SHA256",           0x003e);
        add("TLS_DH_RSA_WITH_AES_128_CBC_SHA256",           0x003f);
        add("TLS_DH_DSS_WITH_AES_256_CBC_SHA256",           0x0068);
        add("TLS_DH_RSA_WITH_AES_256_CBC_SHA256",           0x0069);

        // Unsupported cipher suites from RFC 5288
        add("TLS_DH_RSA_WITH_AES_128_GCM_SHA256",           0x00a0);
        add("TLS_DH_RSA_WITH_AES_256_GCM_SHA384",           0x00a1);
        add("TLS_DH_DSS_WITH_AES_128_GCM_SHA256",           0x00a4);
        add("TLS_DH_DSS_WITH_AES_256_GCM_SHA384",           0x00a5);

        // Unsupported cipher suites from RFC 5487
        add("TLS_PSK_WITH_AES_128_GCM_SHA256",              0x00a8);
        add("TLS_PSK_WITH_AES_256_GCM_SHA384",              0x00a9);
        add("TLS_DHE_PSK_WITH_AES_128_GCM_SHA256",          0x00aa);
        add("TLS_DHE_PSK_WITH_AES_256_GCM_SHA384",          0x00ab);
        add("TLS_RSA_PSK_WITH_AES_128_GCM_SHA256",          0x00ac);
        add("TLS_RSA_PSK_WITH_AES_256_GCM_SHA384",          0x00ad);
        add("TLS_PSK_WITH_AES_128_CBC_SHA256",              0x00ae);
        add("TLS_PSK_WITH_AES_256_CBC_SHA384",              0x00af);
        add("TLS_PSK_WITH_NULL_SHA256",                     0x00b0);
        add("TLS_PSK_WITH_NULL_SHA384",                     0x00b1);
        add("TLS_DHE_PSK_WITH_AES_128_CBC_SHA256",          0x00b2);
        add("TLS_DHE_PSK_WITH_AES_256_CBC_SHA384",          0x00b3);
        add("TLS_DHE_PSK_WITH_NULL_SHA256",                 0x00b4);
        add("TLS_DHE_PSK_WITH_NULL_SHA384",                 0x00b5);
        add("TLS_RSA_PSK_WITH_AES_128_CBC_SHA256",          0x00b6);
        add("TLS_RSA_PSK_WITH_AES_256_CBC_SHA384",          0x00b7);
        add("TLS_RSA_PSK_WITH_NULL_SHA256",                 0x00b8);
        add("TLS_RSA_PSK_WITH_NULL_SHA384",                 0x00b9);

        // Unsupported cipher suites from RFC 5932
        add("TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",            0x0041);
        add("TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA",         0x0042);
        add("TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA",         0x0043);
        add("TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",        0x0044);
        add("TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",        0x0045);
        add("TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA",        0x0046);
        add("TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",            0x0084);
        add("TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA",         0x0085);
        add("TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA",         0x0086);
        add("TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",        0x0087);
        add("TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",        0x0088);
        add("TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA",        0x0089);
        add("TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256",         0x00ba);
        add("TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256",      0x00bb);
        add("TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256",      0x00bc);
        add("TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256",     0x00bd);
        add("TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",     0x00be);
        add("TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256",     0x00bf);
        add("TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256",         0x00c0);
        add("TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256",      0x00c1);
        add("TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256",      0x00c2);
        add("TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256",     0x00c3);
        add("TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256",     0x00c4);
        add("TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256",     0x00c5);

        // TLS Fallback Signaling Cipher Suite Value (SCSV) RFC 7507
        add("TLS_FALLBACK_SCSV", 0x5600);

        // Unsupported cipher suites from RFC 5054
        add("TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA",            0xc01a);
        add("TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA",        0xc01b);
        add("TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA",        0xc01c);
        add("TLS_SRP_SHA_WITH_AES_128_CBC_SHA",             0xc01d);
        add("TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA",         0xc01e);
        add("TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA",         0xc01f);
        add("TLS_SRP_SHA_WITH_AES_256_CBC_SHA",             0xc020);
        add("TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA",         0xc021);
        add("TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA",         0xc022);

        // Unsupported cipher suites from RFC 5489
        add("TLS_ECDHE_PSK_WITH_RC4_128_SHA",               0xc033);
        add("TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA",          0xc034);
        add("TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA",           0xc035);
        add("TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA",           0xc036);
        add("TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256",        0xc037);
        add("TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384",        0xc038);
        add("TLS_ECDHE_PSK_WITH_NULL_SHA",                  0xc039);
        add("TLS_ECDHE_PSK_WITH_NULL_SHA256",               0xc03a);
        add("TLS_ECDHE_PSK_WITH_NULL_SHA384",               0xc03b);

        // Unsupported cipher suites from RFC 6209
        add("TLS_RSA_WITH_ARIA_128_CBC_SHA256",             0xc03c);
        add("TLS_RSA_WITH_ARIA_256_CBC_SHA384",             0xc03d);
        add("TLS_DH_DSS_WITH_ARIA_128_CBC_SHA256",          0xc03e);
        add("TLS_DH_DSS_WITH_ARIA_256_CBC_SHA384",          0xc03f);
        add("TLS_DH_RSA_WITH_ARIA_128_CBC_SHA256",          0xc040);
        add("TLS_DH_RSA_WITH_ARIA_256_CBC_SHA384",          0xc041);
        add("TLS_DHE_DSS_WITH_ARIA_128_CBC_SHA256",         0xc042);
        add("TLS_DHE_DSS_WITH_ARIA_256_CBC_SHA384",         0xc043);
        add("TLS_DHE_RSA_WITH_ARIA_128_CBC_SHA256",         0xc044);
        add("TLS_DHE_RSA_WITH_ARIA_256_CBC_SHA384",         0xc045);
        add("TLS_DH_anon_WITH_ARIA_128_CBC_SHA256",         0xc046);
        add("TLS_DH_anon_WITH_ARIA_256_CBC_SHA384",         0xc047);
        add("TLS_ECDHE_ECDSA_WITH_ARIA_128_CBC_SHA256",     0xc048);
        add("TLS_ECDHE_ECDSA_WITH_ARIA_256_CBC_SHA384",     0xc049);
        add("TLS_ECDH_ECDSA_WITH_ARIA_128_CBC_SHA256",      0xc04a);
        add("TLS_ECDH_ECDSA_WITH_ARIA_256_CBC_SHA384",      0xc04b);
        add("TLS_ECDHE_RSA_WITH_ARIA_128_CBC_SHA256",       0xc04c);
        add("TLS_ECDHE_RSA_WITH_ARIA_256_CBC_SHA384",       0xc04d);
        add("TLS_ECDH_RSA_WITH_ARIA_128_CBC_SHA256",        0xc04e);
        add("TLS_ECDH_RSA_WITH_ARIA_256_CBC_SHA384",        0xc04f);
        add("TLS_RSA_WITH_ARIA_128_GCM_SHA256",             0xc050);
        add("TLS_RSA_WITH_ARIA_256_GCM_SHA384",             0xc051);
        add("TLS_DHE_RSA_WITH_ARIA_128_GCM_SHA256",         0xc052);
        add("TLS_DHE_RSA_WITH_ARIA_256_GCM_SHA384",         0xc053);
        add("TLS_DH_RSA_WITH_ARIA_128_GCM_SHA256",          0xc054);
        add("TLS_DH_RSA_WITH_ARIA_256_GCM_SHA384",          0xc055);
        add("TLS_DHE_DSS_WITH_ARIA_128_GCM_SHA256",         0xc056);
        add("TLS_DHE_DSS_WITH_ARIA_256_GCM_SHA384",         0xc057);
        add("TLS_DH_DSS_WITH_ARIA_128_GCM_SHA256",          0xc058);
        add("TLS_DH_DSS_WITH_ARIA_256_GCM_SHA384",          0xc059);
        add("TLS_DH_anon_WITH_ARIA_128_GCM_SHA256",         0xc05a);
        add("TLS_DH_anon_WITH_ARIA_256_GCM_SHA384",         0xc05b);
        add("TLS_ECDHE_ECDSA_WITH_ARIA_128_GCM_SHA256",     0xc05c);
        add("TLS_ECDHE_ECDSA_WITH_ARIA_256_GCM_SHA384",     0xc05d);
        add("TLS_ECDH_ECDSA_WITH_ARIA_128_GCM_SHA256",      0xc05e);
        add("TLS_ECDH_ECDSA_WITH_ARIA_256_GCM_SHA384",      0xc05f);
        add("TLS_ECDHE_RSA_WITH_ARIA_128_GCM_SHA256",       0xc060);
        add("TLS_ECDHE_RSA_WITH_ARIA_256_GCM_SHA384",       0xc061);
        add("TLS_ECDH_RSA_WITH_ARIA_128_GCM_SHA256",        0xc062);
        add("TLS_ECDH_RSA_WITH_ARIA_256_GCM_SHA384",        0xc063);
        add("TLS_PSK_WITH_ARIA_128_CBC_SHA256",             0xc064);
        add("TLS_PSK_WITH_ARIA_256_CBC_SHA384",             0xc065);
        add("TLS_DHE_PSK_WITH_ARIA_128_CBC_SHA256",         0xc066);
        add("TLS_DHE_PSK_WITH_ARIA_256_CBC_SHA384",         0xc067);
        add("TLS_RSA_PSK_WITH_ARIA_128_CBC_SHA256",         0xc068);
        add("TLS_RSA_PSK_WITH_ARIA_256_CBC_SHA384",         0xc069);
        add("TLS_PSK_WITH_ARIA_128_GCM_SHA256",             0xc06a);
        add("TLS_PSK_WITH_ARIA_256_GCM_SHA384",             0xc06b);
        add("TLS_DHE_PSK_WITH_ARIA_128_GCM_SHA256",         0xc06c);
        add("TLS_DHE_PSK_WITH_ARIA_256_GCM_SHA384",         0xc06d);
        add("TLS_RSA_PSK_WITH_ARIA_128_GCM_SHA256",         0xc06e);
        add("TLS_RSA_PSK_WITH_ARIA_256_GCM_SHA384",         0xc06f);
        add("TLS_ECDHE_PSK_WITH_ARIA_128_CBC_SHA256",       0xc070);
        add("TLS_ECDHE_PSK_WITH_ARIA_256_CBC_SHA384",       0xc071);

        // Unsupported cipher suites from RFC 6367
        add("TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256", 0xc072);
        add("TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384", 0xc073);
        add("TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256",  0xc074);
        add("TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384",  0xc075);
        add("TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",   0xc076);
        add("TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384",   0xc077);
        add("TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256",    0xc078);
        add("TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384",    0xc079);
        add("TLS_RSA_WITH_CAMELLIA_128_GCM_SHA256",         0xc07a);
        add("TLS_RSA_WITH_CAMELLIA_256_GCM_SHA384",         0xc07b);
        add("TLS_DHE_RSA_WITH_CAMELLIA_128_GCM_SHA256",     0xc07c);
        add("TLS_DHE_RSA_WITH_CAMELLIA_256_GCM_SHA384",     0xc07d);
        add("TLS_DH_RSA_WITH_CAMELLIA_128_GCM_SHA256",      0xc07e);
        add("TLS_DH_RSA_WITH_CAMELLIA_256_GCM_SHA384",      0xc07f);
        add("TLS_DHE_DSS_WITH_CAMELLIA_128_GCM_SHA256",     0xc080);
        add("TLS_DHE_DSS_WITH_CAMELLIA_256_GCM_SHA384",     0xc081);
        add("TLS_DH_DSS_WITH_CAMELLIA_128_GCM_SHA256",      0xc082);
        add("TLS_DH_DSS_WITH_CAMELLIA_256_GCM_SHA384",      0xc083);
        add("TLS_DH_anon_WITH_CAMELLIA_128_GCM_SHA256",     0xc084);
        add("TLS_DH_anon_WITH_CAMELLIA_256_GCM_SHA384",     0xc085);
        add("TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_GCM_SHA256", 0xc086);
        add("TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_GCM_SHA384", 0xc087);
        add("TLS_ECDH_ECDSA_WITH_CAMELLIA_128_GCM_SHA256",  0xc088);
        add("TLS_ECDH_ECDSA_WITH_CAMELLIA_256_GCM_SHA384",  0xc089);
        add("TLS_ECDHE_RSA_WITH_CAMELLIA_128_GCM_SHA256",   0xc08a);
        add("TLS_ECDHE_RSA_WITH_CAMELLIA_256_GCM_SHA384",   0xc08b);
        add("TLS_ECDH_RSA_WITH_CAMELLIA_128_GCM_SHA256",    0xc08c);
        add("TLS_ECDH_RSA_WITH_CAMELLIA_256_GCM_SHA384",    0xc08d);
        add("TLS_PSK_WITH_CAMELLIA_128_GCM_SHA256",         0xc08e);
        add("TLS_PSK_WITH_CAMELLIA_256_GCM_SHA384",         0xc08f);
        add("TLS_DHE_PSK_WITH_CAMELLIA_128_GCM_SHA256",     0xc090);
        add("TLS_DHE_PSK_WITH_CAMELLIA_256_GCM_SHA384",     0xc091);
        add("TLS_RSA_PSK_WITH_CAMELLIA_128_GCM_SHA256",     0xc092);
        add("TLS_RSA_PSK_WITH_CAMELLIA_256_GCM_SHA384",     0xc093);
        add("TLS_PSK_WITH_CAMELLIA_128_CBC_SHA256",         0xc094);
        add("TLS_PSK_WITH_CAMELLIA_256_CBC_SHA384",         0xc095);
        add("TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",     0xc096);
        add("TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",     0xc097);
        add("TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256",     0xc098);
        add("TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384",     0xc099);
        add("TLS_ECDHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",   0xc09a);
        add("TLS_ECDHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",   0xc09b);

        // Unsupported cipher suites from RFC 6655
        add("TLS_RSA_WITH_AES_128_CCM",                     0xc09c);
        add("TLS_RSA_WITH_AES_256_CCM",                     0xc09d);
        add("TLS_DHE_RSA_WITH_AES_128_CCM",                 0xc09e);
        add("TLS_DHE_RSA_WITH_AES_256_CCM",                 0xc09f);
        add("TLS_RSA_WITH_AES_128_CCM_8",                   0xc0A0);
        add("TLS_RSA_WITH_AES_256_CCM_8",                   0xc0A1);
        add("TLS_DHE_RSA_WITH_AES_128_CCM_8",               0xc0A2);
        add("TLS_DHE_RSA_WITH_AES_256_CCM_8",               0xc0A3);
        add("TLS_PSK_WITH_AES_128_CCM",                     0xc0A4);
        add("TLS_PSK_WITH_AES_256_CCM",                     0xc0A5);
        add("TLS_DHE_PSK_WITH_AES_128_CCM",                 0xc0A6);
        add("TLS_DHE_PSK_WITH_AES_256_CCM",                 0xc0A7);
        add("TLS_PSK_WITH_AES_128_CCM_8",                   0xc0A8);
        add("TLS_PSK_WITH_AES_256_CCM_8",                   0xc0A9);
        add("TLS_PSK_DHE_WITH_AES_128_CCM_8",               0xc0Aa);
        add("TLS_PSK_DHE_WITH_AES_256_CCM_8",               0xc0Ab);

        // Unsupported cipher suites from RFC 7251
        add("TLS_ECDHE_ECDSA_WITH_AES_128_CCM",             0xc0Ac);
        add("TLS_ECDHE_ECDSA_WITH_AES_256_CCM",             0xc0Ad);
        add("TLS_ECDHE_ECDSA_WITH_AES_128_CCM_8",           0xc0Ae);
        add("TLS_ECDHE_ECDSA_WITH_AES_256_CCM_8",           0xc0Af);
    }

    // ciphersuite SSL_NULL_WITH_NULL_NULL
    static final CipherSuite C_NULL = CipherSuite.valueOf(0, 0);

    // ciphersuite TLS_EMPTY_RENEGOTIATION_INFO_SCSV
    static final CipherSuite C_SCSV = CipherSuite.valueOf(0x00, 0xff);
}
