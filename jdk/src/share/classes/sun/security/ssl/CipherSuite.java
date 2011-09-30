/*
 * Copyright (c) 2002, 2011, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import sun.security.ssl.CipherSuite.*;
import static sun.security.ssl.CipherSuite.KeyExchange.*;
import static sun.security.ssl.CipherSuite.PRF.*;
import static sun.security.ssl.JsseJce.*;

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
final class CipherSuite implements Comparable {

    // minimum priority for supported CipherSuites
    final static int SUPPORTED_SUITES_PRIORITY = 1;

    // minimum priority for default enabled CipherSuites
    final static int DEFAULT_SUITES_PRIORITY = 300;

    // Flag indicating if CipherSuite availability can change dynamically.
    // This is the case when we rely on a JCE cipher implementation that
    // may not be available in the installed JCE providers.
    // It is true because we might not have an ECC implementation.
    final static boolean DYNAMIC_AVAILABILITY = true;

    private final static boolean ALLOW_ECC = Debug.getBooleanProperty
        ("com.sun.net.ssl.enableECC", true);

    // Map Integer(id) -> CipherSuite
    // contains all known CipherSuites
    private final static Map<Integer,CipherSuite> idMap;

    // Map String(name) -> CipherSuite
    // contains only supported CipherSuites (i.e. allowed == true)
    private final static Map<String,CipherSuite> nameMap;

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
    final int obsoleted;

    // supported since protocol version
    final int supported;

    /**
     * Constructor for implemented CipherSuites.
     */
    private CipherSuite(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher,
            boolean allowed, int obsoleted, int supported, PRF prfAlg) {
        this.name = name;
        this.id = id;
        this.priority = priority;
        this.keyExchange = keyExchange;
        this.cipher = cipher;
        this.exportable = cipher.exportable;
        if (name.endsWith("_MD5")) {
            macAlg = M_MD5;
        } else if (name.endsWith("_SHA")) {
            macAlg = M_SHA;
        } else if (name.endsWith("_SHA256")) {
            macAlg = M_SHA256;
        } else if (name.endsWith("_SHA384")) {
            macAlg = M_SHA384;
        } else if (name.endsWith("_NULL")) {
            macAlg = M_NULL;
        } else if (name.endsWith("_SCSV")) {
            macAlg = M_NULL;
        } else {
            throw new IllegalArgumentException
                    ("Unknown MAC algorithm for ciphersuite " + name);
        }

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
     * In some configuration, this situation may change over time, call
     * CipherSuiteList.clearAvailableCache() before this method to obtain
     * the most current status.
     */
    boolean isAvailable() {
        return allowed && keyExchange.isAvailable() && cipher.isAvailable();
    }

    boolean isNegotiable() {
        return this != C_SCSV && isAvailable();
    }

    /**
     * Compares CipherSuites based on their priority. Has the effect of
     * sorting CipherSuites when put in a sorted collection, which is
     * used by CipherSuiteList. Follows standard Comparable contract.
     *
     * Note that for unsupported CipherSuites parsed from a handshake
     * message we violate the equals() contract.
     */
    public int compareTo(Object o) {
        return ((CipherSuite)o).priority - priority;
    }

    /**
     * Returns this.name.
     */
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

    // for use by CipherSuiteList only
    static Collection<CipherSuite> allowedCipherSuites() {
        return nameMap.values();
    }

    /*
     * Use this method when all of the values need to be specified.
     * This is primarily used when defining a new ciphersuite for
     * TLS 1.2+ that doesn't use the "default" PRF.
     */
    private static void add(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher,
            boolean allowed, int obsoleted, int supported, PRF prf) {

        CipherSuite c = new CipherSuite(name, id, priority, keyExchange,
            cipher, allowed, obsoleted, supported, prf);
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
            KeyExchange keyExchange, BulkCipher cipher,
            boolean allowed, int obsoleted) {
        // If this is an obsoleted suite, then don't let the TLS 1.2
        // protocol have a valid PRF value.
        PRF prf = P_SHA256;
        if (obsoleted < ProtocolVersion.TLS12.v) {
            prf = P_NONE;
        }

        add(name, id, priority, keyExchange, cipher, allowed, obsoleted,
            ProtocolVersion.LIMIT_MIN_VALUE, prf);
    }

    /*
     * Use this method when there is no upper protocol limit.  That is,
     * suites which have not been obsoleted.
     */
    private static void add(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher, boolean allowed) {
        add(name, id, priority, keyExchange,
            cipher, allowed, ProtocolVersion.LIMIT_MAX_VALUE);
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
        K_NULL       ("NULL",       false),
        K_RSA        ("RSA",        true),
        K_RSA_EXPORT ("RSA_EXPORT", true),
        K_DH_RSA     ("DH_RSA",     false),
        K_DH_DSS     ("DH_DSS",     false),
        K_DHE_DSS    ("DHE_DSS",    true),
        K_DHE_RSA    ("DHE_RSA",    true),
        K_DH_ANON    ("DH_anon",    true),

        K_ECDH_ECDSA ("ECDH_ECDSA",  ALLOW_ECC),
        K_ECDH_RSA   ("ECDH_RSA",    ALLOW_ECC),
        K_ECDHE_ECDSA("ECDHE_ECDSA", ALLOW_ECC),
        K_ECDHE_RSA  ("ECDHE_RSA",   ALLOW_ECC),
        K_ECDH_ANON  ("ECDH_anon",   ALLOW_ECC),

        // Kerberos cipher suites
        K_KRB5       ("KRB5", true),
        K_KRB5_EXPORT("KRB5_EXPORT", true),

        // renegotiation protection request signaling cipher suite
        K_SCSV       ("SCSV",        true);

        // name of the key exchange algorithm, e.g. DHE_DSS
        final String name;
        final boolean allowed;
        private final boolean alwaysAvailable;

        KeyExchange(String name, boolean allowed) {
            this.name = name;
            this.allowed = allowed;
            this.alwaysAvailable = allowed &&
                (!name.startsWith("EC")) && (!name.startsWith("KRB"));
        }

        boolean isAvailable() {
            if (alwaysAvailable) {
                return true;
            }

            if (name.startsWith("EC")) {
                return (allowed && JsseJce.isEcAvailable());
            } else if (name.startsWith("KRB")) {
                return (allowed && JsseJce.isKerberosAvailable());
            } else {
                return allowed;
            }
        }

        public String toString() {
            return name;
        }
    }

    /**
     * An SSL/TLS bulk cipher algorithm. One instance per combination of
     * cipher and key length.
     *
     * Also contains a factory method to obtain in initialized CipherBox
     * for this algorithm.
     */
    final static class BulkCipher {

        // Map BulkCipher -> Boolean(available)
        private final static Map<BulkCipher,Boolean> availableCache =
                                            new HashMap<>(8);

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

        // size of the IV (also block size)
        final int ivSize;

        // exportable under 512/40 bit rules
        final boolean exportable;

        // Is the cipher algorithm of Cipher Block Chaining (CBC) mode?
        final boolean isCBCMode;

        BulkCipher(String transformation, int keySize,
                int expandedKeySize, int ivSize, boolean allowed) {
            this.transformation = transformation;
            String[] splits = transformation.split("/");
            this.algorithm = splits[0];
            this.isCBCMode =
                splits.length <= 1 ? false : "CBC".equalsIgnoreCase(splits[1]);
            this.description = this.algorithm + "/" + (keySize << 3);
            this.keySize = keySize;
            this.ivSize = ivSize;
            this.allowed = allowed;

            this.expandedKeySize = expandedKeySize;
            this.exportable = true;
        }

        BulkCipher(String transformation, int keySize,
                int ivSize, boolean allowed) {
            this.transformation = transformation;
            String[] splits = transformation.split("/");
            this.algorithm = splits[0];
            this.isCBCMode =
                splits.length <= 1 ? false : "CBC".equalsIgnoreCase(splits[1]);
            this.description = this.algorithm + "/" + (keySize << 3);
            this.keySize = keySize;
            this.ivSize = ivSize;
            this.allowed = allowed;

            this.expandedKeySize = keySize;
            this.exportable = false;
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
         *
         * Currently all supported ciphers except AES are always available
         * via the JSSE internal implementations. We also assume AES/128
         * is always available since it is shipped with the SunJCE provider.
         * However, AES/256 is unavailable when the default JCE policy
         * jurisdiction files are installed because of key length restrictions.
         */
        boolean isAvailable() {
            if (allowed == false) {
                return false;
            }
            if (this == B_AES_256) {
                return isAvailable(this);
            }

            // always available
            return true;
        }

        // for use by CipherSuiteList.clearAvailableCache();
        static synchronized void clearAvailableCache() {
            if (DYNAMIC_AVAILABILITY) {
                availableCache.clear();
            }
        }

        private static synchronized boolean isAvailable(BulkCipher cipher) {
            Boolean b = availableCache.get(cipher);
            if (b == null) {
                try {
                    SecretKey key = new SecretKeySpec
                        (new byte[cipher.expandedKeySize], cipher.algorithm);
                    IvParameterSpec iv =
                        new IvParameterSpec(new byte[cipher.ivSize]);
                    cipher.newCipher(ProtocolVersion.DEFAULT,
                                                key, iv, null, true);
                    b = Boolean.TRUE;
                } catch (NoSuchAlgorithmException e) {
                    b = Boolean.FALSE;
                }
                availableCache.put(cipher, b);
            }
            return b.booleanValue();
        }

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
    final static class MacAlg {

        // descriptive name, e.g. MD5
        final String name;

        // size of the MAC value (and MAC key) in bytes
        final int size;

        MacAlg(String name, int size) {
            this.name = name;
            this.size = size;
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

        public String toString() {
            return name;
        }
    }

    // export strength ciphers
    final static BulkCipher B_NULL    =
                        new BulkCipher("NULL",         0,  0, 0, true);
    final static BulkCipher B_RC4_40  =
                        new BulkCipher(CIPHER_RC4,     5, 16, 0, true);
    final static BulkCipher B_RC2_40  =
                        new BulkCipher("RC2",          5, 16, 8, false);
    final static BulkCipher B_DES_40  =
                        new BulkCipher(CIPHER_DES,     5,  8, 8, true);

    // domestic strength ciphers
    final static BulkCipher B_RC4_128 =
                        new BulkCipher(CIPHER_RC4,     16,  0, true);
    final static BulkCipher B_DES     =
                        new BulkCipher(CIPHER_DES,      8,  8, true);
    final static BulkCipher B_3DES    =
                        new BulkCipher(CIPHER_3DES,    24,  8, true);
    final static BulkCipher B_IDEA    =
                        new BulkCipher("IDEA",         16,  8, false);
    final static BulkCipher B_AES_128 =
                        new BulkCipher(CIPHER_AES,     16, 16, true);
    final static BulkCipher B_AES_256 =
                        new BulkCipher(CIPHER_AES,     32, 16, true);

    // MACs
    final static MacAlg M_NULL = new MacAlg("NULL", 0);
    final static MacAlg M_MD5  = new MacAlg("MD5", 16);
    final static MacAlg M_SHA  = new MacAlg("SHA", 20);
    final static MacAlg M_SHA256  = new MacAlg("SHA256", 32);
    final static MacAlg M_SHA384  = new MacAlg("SHA384", 48);

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
         * TLS Cipher Suite Registry, as of August 2010.
         *
         * http://www.iana.org/assignments/tls-parameters/tls-parameters.xml
         *
         * Range      Registration Procedures   Notes
         * 000-191    Standards Action          Refers to value of first byte
         * 192-254    Specification Required    Refers to value of first byte
         * 255        Reserved for Private Use  Refers to value of first byte
         *
         * Value      Description                               Reference
         * 0x00,0x00  TLS_NULL_WITH_NULL_NULL                   [RFC5246]
         * 0x00,0x01  TLS_RSA_WITH_NULL_MD5                     [RFC5246]
         * 0x00,0x02  TLS_RSA_WITH_NULL_SHA                     [RFC5246]
         * 0x00,0x03  TLS_RSA_EXPORT_WITH_RC4_40_MD5            [RFC4346]
         * 0x00,0x04  TLS_RSA_WITH_RC4_128_MD5                  [RFC5246]
         * 0x00,0x05  TLS_RSA_WITH_RC4_128_SHA                  [RFC5246]
         * 0x00,0x06  TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5        [RFC4346]
         * 0x00,0x07  TLS_RSA_WITH_IDEA_CBC_SHA                 [RFC5469]
         * 0x00,0x08  TLS_RSA_EXPORT_WITH_DES40_CBC_SHA         [RFC4346]
         * 0x00,0x09  TLS_RSA_WITH_DES_CBC_SHA                  [RFC5469]
         * 0x00,0x0A  TLS_RSA_WITH_3DES_EDE_CBC_SHA             [RFC5246]
         * 0x00,0x0B  TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA      [RFC4346]
         * 0x00,0x0C  TLS_DH_DSS_WITH_DES_CBC_SHA               [RFC5469]
         * 0x00,0x0D  TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA          [RFC5246]
         * 0x00,0x0E  TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA      [RFC4346]
         * 0x00,0x0F  TLS_DH_RSA_WITH_DES_CBC_SHA               [RFC5469]
         * 0x00,0x10  TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA          [RFC5246]
         * 0x00,0x11  TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA     [RFC4346]
         * 0x00,0x12  TLS_DHE_DSS_WITH_DES_CBC_SHA              [RFC5469]
         * 0x00,0x13  TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA         [RFC5246]
         * 0x00,0x14  TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA     [RFC4346]
         * 0x00,0x15  TLS_DHE_RSA_WITH_DES_CBC_SHA              [RFC5469]
         * 0x00,0x16  TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA         [RFC5246]
         * 0x00,0x17  TLS_DH_anon_EXPORT_WITH_RC4_40_MD5        [RFC4346]
         * 0x00,0x18  TLS_DH_anon_WITH_RC4_128_MD5              [RFC5246]
         * 0x00,0x19  TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA     [RFC4346]
         * 0x00,0x1A  TLS_DH_anon_WITH_DES_CBC_SHA              [RFC5469]
         * 0x00,0x1B  TLS_DH_anon_WITH_3DES_EDE_CBC_SHA         [RFC5246]
         * 0x00,0x1C-1D Reserved to avoid conflicts with SSLv3  [RFC5246]
         * 0x00,0x1E  TLS_KRB5_WITH_DES_CBC_SHA                 [RFC2712]
         * 0x00,0x1F  TLS_KRB5_WITH_3DES_EDE_CBC_SHA            [RFC2712]
         * 0x00,0x20  TLS_KRB5_WITH_RC4_128_SHA                 [RFC2712]
         * 0x00,0x21  TLS_KRB5_WITH_IDEA_CBC_SHA                [RFC2712]
         * 0x00,0x22  TLS_KRB5_WITH_DES_CBC_MD5                 [RFC2712]
         * 0x00,0x23  TLS_KRB5_WITH_3DES_EDE_CBC_MD5            [RFC2712]
         * 0x00,0x24  TLS_KRB5_WITH_RC4_128_MD5                 [RFC2712]
         * 0x00,0x25  TLS_KRB5_WITH_IDEA_CBC_MD5                [RFC2712]
         * 0x00,0x26  TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA       [RFC2712]
         * 0x00,0x27  TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA       [RFC2712]
         * 0x00,0x28  TLS_KRB5_EXPORT_WITH_RC4_40_SHA           [RFC2712]
         * 0x00,0x29  TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5       [RFC2712]
         * 0x00,0x2A  TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5       [RFC2712]
         * 0x00,0x2B  TLS_KRB5_EXPORT_WITH_RC4_40_MD5           [RFC2712]
         * 0x00,0x2C  TLS_PSK_WITH_NULL_SHA                     [RFC4785]
         * 0x00,0x2D  TLS_DHE_PSK_WITH_NULL_SHA                 [RFC4785]
         * 0x00,0x2E  TLS_RSA_PSK_WITH_NULL_SHA                 [RFC4785]
         * 0x00,0x2F  TLS_RSA_WITH_AES_128_CBC_SHA              [RFC5246]
         * 0x00,0x30  TLS_DH_DSS_WITH_AES_128_CBC_SHA           [RFC5246]
         * 0x00,0x31  TLS_DH_RSA_WITH_AES_128_CBC_SHA           [RFC5246]
         * 0x00,0x32  TLS_DHE_DSS_WITH_AES_128_CBC_SHA          [RFC5246]
         * 0x00,0x33  TLS_DHE_RSA_WITH_AES_128_CBC_SHA          [RFC5246]
         * 0x00,0x34  TLS_DH_anon_WITH_AES_128_CBC_SHA          [RFC5246]
         * 0x00,0x35  TLS_RSA_WITH_AES_256_CBC_SHA              [RFC5246]
         * 0x00,0x36  TLS_DH_DSS_WITH_AES_256_CBC_SHA           [RFC5246]
         * 0x00,0x37  TLS_DH_RSA_WITH_AES_256_CBC_SHA           [RFC5246]
         * 0x00,0x38  TLS_DHE_DSS_WITH_AES_256_CBC_SHA          [RFC5246]
         * 0x00,0x39  TLS_DHE_RSA_WITH_AES_256_CBC_SHA          [RFC5246]
         * 0x00,0x3A  TLS_DH_anon_WITH_AES_256_CBC_SHA          [RFC5246]
         * 0x00,0x3B  TLS_RSA_WITH_NULL_SHA256                  [RFC5246]
         * 0x00,0x3C  TLS_RSA_WITH_AES_128_CBC_SHA256           [RFC5246]
         * 0x00,0x3D  TLS_RSA_WITH_AES_256_CBC_SHA256           [RFC5246]
         * 0x00,0x3E  TLS_DH_DSS_WITH_AES_128_CBC_SHA256        [RFC5246]
         * 0x00,0x3F  TLS_DH_RSA_WITH_AES_128_CBC_SHA256        [RFC5246]
         * 0x00,0x40  TLS_DHE_DSS_WITH_AES_128_CBC_SHA256       [RFC5246]
         * 0x00,0x41  TLS_RSA_WITH_CAMELLIA_128_CBC_SHA         [RFC5932]
         * 0x00,0x42  TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA      [RFC5932]
         * 0x00,0x43  TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA      [RFC5932]
         * 0x00,0x44  TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA     [RFC5932]
         * 0x00,0x45  TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA     [RFC5932]
         * 0x00,0x46  TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA     [RFC5932]
         * 0x00,0x47-4F Reserved to avoid conflicts with
         *            deployed implementations                  [Pasi_Eronen]
         * 0x00,0x50-58 Reserved to avoid conflicts             [Pasi Eronen]
         * 0x00,0x59-5C Reserved to avoid conflicts with
         *            deployed implementations                  [Pasi_Eronen]
         * 0x00,0x5D-5F Unassigned
         * 0x00,0x60-66 Reserved to avoid conflicts with widely
         *            deployed implementations                  [Pasi_Eronen]
         * 0x00,0x67  TLS_DHE_RSA_WITH_AES_128_CBC_SHA256       [RFC5246]
         * 0x00,0x68  TLS_DH_DSS_WITH_AES_256_CBC_SHA256        [RFC5246]
         * 0x00,0x69  TLS_DH_RSA_WITH_AES_256_CBC_SHA256        [RFC5246]
         * 0x00,0x6A  TLS_DHE_DSS_WITH_AES_256_CBC_SHA256       [RFC5246]
         * 0x00,0x6B  TLS_DHE_RSA_WITH_AES_256_CBC_SHA256       [RFC5246]
         * 0x00,0x6C  TLS_DH_anon_WITH_AES_128_CBC_SHA256       [RFC5246]
         * 0x00,0x6D  TLS_DH_anon_WITH_AES_256_CBC_SHA256       [RFC5246]
         * 0x00,0x6E-83 Unassigned
         * 0x00,0x84  TLS_RSA_WITH_CAMELLIA_256_CBC_SHA         [RFC5932]
         * 0x00,0x85  TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA      [RFC5932]
         * 0x00,0x86  TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA      [RFC5932]
         * 0x00,0x87  TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA     [RFC5932]
         * 0x00,0x88  TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA     [RFC5932]
         * 0x00,0x89  TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA     [RFC5932]
         * 0x00,0x8A  TLS_PSK_WITH_RC4_128_SHA                  [RFC4279]
         * 0x00,0x8B  TLS_PSK_WITH_3DES_EDE_CBC_SHA             [RFC4279]
         * 0x00,0x8C  TLS_PSK_WITH_AES_128_CBC_SHA              [RFC4279]
         * 0x00,0x8D  TLS_PSK_WITH_AES_256_CBC_SHA              [RFC4279]
         * 0x00,0x8E  TLS_DHE_PSK_WITH_RC4_128_SHA              [RFC4279]
         * 0x00,0x8F  TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA         [RFC4279]
         * 0x00,0x90  TLS_DHE_PSK_WITH_AES_128_CBC_SHA          [RFC4279]
         * 0x00,0x91  TLS_DHE_PSK_WITH_AES_256_CBC_SHA          [RFC4279]
         * 0x00,0x92  TLS_RSA_PSK_WITH_RC4_128_SHA              [RFC4279]
         * 0x00,0x93  TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA         [RFC4279]
         * 0x00,0x94  TLS_RSA_PSK_WITH_AES_128_CBC_SHA          [RFC4279]
         * 0x00,0x95  TLS_RSA_PSK_WITH_AES_256_CBC_SHA          [RFC4279]
         * 0x00,0x96  TLS_RSA_WITH_SEED_CBC_SHA                 [RFC4162]
         * 0x00,0x97  TLS_DH_DSS_WITH_SEED_CBC_SHA              [RFC4162]
         * 0x00,0x98  TLS_DH_RSA_WITH_SEED_CBC_SHA              [RFC4162]
         * 0x00,0x99  TLS_DHE_DSS_WITH_SEED_CBC_SHA             [RFC4162]
         * 0x00,0x9A  TLS_DHE_RSA_WITH_SEED_CBC_SHA             [RFC4162]
         * 0x00,0x9B  TLS_DH_anon_WITH_SEED_CBC_SHA             [RFC4162]
         * 0x00,0x9C  TLS_RSA_WITH_AES_128_GCM_SHA256           [RFC5288]
         * 0x00,0x9D  TLS_RSA_WITH_AES_256_GCM_SHA384           [RFC5288]
         * 0x00,0x9E  TLS_DHE_RSA_WITH_AES_128_GCM_SHA256       [RFC5288]
         * 0x00,0x9F  TLS_DHE_RSA_WITH_AES_256_GCM_SHA384       [RFC5288]
         * 0x00,0xA0  TLS_DH_RSA_WITH_AES_128_GCM_SHA256        [RFC5288]
         * 0x00,0xA1  TLS_DH_RSA_WITH_AES_256_GCM_SHA384        [RFC5288]
         * 0x00,0xA2  TLS_DHE_DSS_WITH_AES_128_GCM_SHA256       [RFC5288]
         * 0x00,0xA3  TLS_DHE_DSS_WITH_AES_256_GCM_SHA384       [RFC5288]
         * 0x00,0xA4  TLS_DH_DSS_WITH_AES_128_GCM_SHA256        [RFC5288]
         * 0x00,0xA5  TLS_DH_DSS_WITH_AES_256_GCM_SHA384        [RFC5288]
         * 0x00,0xA6  TLS_DH_anon_WITH_AES_128_GCM_SHA256       [RFC5288]
         * 0x00,0xA7  TLS_DH_anon_WITH_AES_256_GCM_SHA384       [RFC5288]
         * 0x00,0xA8  TLS_PSK_WITH_AES_128_GCM_SHA256           [RFC5487]
         * 0x00,0xA9  TLS_PSK_WITH_AES_256_GCM_SHA384           [RFC5487]
         * 0x00,0xAA  TLS_DHE_PSK_WITH_AES_128_GCM_SHA256       [RFC5487]
         * 0x00,0xAB  TLS_DHE_PSK_WITH_AES_256_GCM_SHA384       [RFC5487]
         * 0x00,0xAC  TLS_RSA_PSK_WITH_AES_128_GCM_SHA256       [RFC5487]
         * 0x00,0xAD  TLS_RSA_PSK_WITH_AES_256_GCM_SHA384       [RFC5487]
         * 0x00,0xAE  TLS_PSK_WITH_AES_128_CBC_SHA256           [RFC5487]
         * 0x00,0xAF  TLS_PSK_WITH_AES_256_CBC_SHA384           [RFC5487]
         * 0x00,0xB0  TLS_PSK_WITH_NULL_SHA256                  [RFC5487]
         * 0x00,0xB1  TLS_PSK_WITH_NULL_SHA384                  [RFC5487]
         * 0x00,0xB2  TLS_DHE_PSK_WITH_AES_128_CBC_SHA256       [RFC5487]
         * 0x00,0xB3  TLS_DHE_PSK_WITH_AES_256_CBC_SHA384       [RFC5487]
         * 0x00,0xB4  TLS_DHE_PSK_WITH_NULL_SHA256              [RFC5487]
         * 0x00,0xB5  TLS_DHE_PSK_WITH_NULL_SHA384              [RFC5487]
         * 0x00,0xB6  TLS_RSA_PSK_WITH_AES_128_CBC_SHA256       [RFC5487]
         * 0x00,0xB7  TLS_RSA_PSK_WITH_AES_256_CBC_SHA384       [RFC5487]
         * 0x00,0xB8  TLS_RSA_PSK_WITH_NULL_SHA256              [RFC5487]
         * 0x00,0xB9  TLS_RSA_PSK_WITH_NULL_SHA384              [RFC5487]
         * 0x00,0xBA  TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256      [RFC5932]
         * 0x00,0xBB  TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256   [RFC5932]
         * 0x00,0xBC  TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256   [RFC5932]
         * 0x00,0xBD  TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256  [RFC5932]
         * 0x00,0xBE  TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256  [RFC5932]
         * 0x00,0xBF  TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256  [RFC5932]
         * 0x00,0xC0  TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256      [RFC5932]
         * 0x00,0xC1  TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256   [RFC5932]
         * 0x00,0xC2  TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256   [RFC5932]
         * 0x00,0xC3  TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256  [RFC5932]
         * 0x00,0xC4  TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256  [RFC5932]
         * 0x00,0xC5  TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256  [RFC5932]
         * 0x00,0xC6-FE         Unassigned
         * 0x00,0xFF  TLS_EMPTY_RENEGOTIATION_INFO_SCSV         [RFC5746]
         * 0x01-BF,*  Unassigned
         * 0xC0,0x01  TLS_ECDH_ECDSA_WITH_NULL_SHA              [RFC4492]
         * 0xC0,0x02  TLS_ECDH_ECDSA_WITH_RC4_128_SHA           [RFC4492]
         * 0xC0,0x03  TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA      [RFC4492]
         * 0xC0,0x04  TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA       [RFC4492]
         * 0xC0,0x05  TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA       [RFC4492]
         * 0xC0,0x06  TLS_ECDHE_ECDSA_WITH_NULL_SHA             [RFC4492]
         * 0xC0,0x07  TLS_ECDHE_ECDSA_WITH_RC4_128_SHA          [RFC4492]
         * 0xC0,0x08  TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA     [RFC4492]
         * 0xC0,0x09  TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA      [RFC4492]
         * 0xC0,0x0A  TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA      [RFC4492]
         * 0xC0,0x0B  TLS_ECDH_RSA_WITH_NULL_SHA                [RFC4492]
         * 0xC0,0x0C  TLS_ECDH_RSA_WITH_RC4_128_SHA             [RFC4492]
         * 0xC0,0x0D  TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA        [RFC4492]
         * 0xC0,0x0E  TLS_ECDH_RSA_WITH_AES_128_CBC_SHA         [RFC4492]
         * 0xC0,0x0F  TLS_ECDH_RSA_WITH_AES_256_CBC_SHA         [RFC4492]
         * 0xC0,0x10  TLS_ECDHE_RSA_WITH_NULL_SHA               [RFC4492]
         * 0xC0,0x11  TLS_ECDHE_RSA_WITH_RC4_128_SHA            [RFC4492]
         * 0xC0,0x12  TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA       [RFC4492]
         * 0xC0,0x13  TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA        [RFC4492]
         * 0xC0,0x14  TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA        [RFC4492]
         * 0xC0,0x15  TLS_ECDH_anon_WITH_NULL_SHA               [RFC4492]
         * 0xC0,0x16  TLS_ECDH_anon_WITH_RC4_128_SHA            [RFC4492]
         * 0xC0,0x17  TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA       [RFC4492]
         * 0xC0,0x18  TLS_ECDH_anon_WITH_AES_128_CBC_SHA        [RFC4492]
         * 0xC0,0x19  TLS_ECDH_anon_WITH_AES_256_CBC_SHA        [RFC4492]
         * 0xC0,0x1A  TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA         [RFC5054]
         * 0xC0,0x1B  TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA     [RFC5054]
         * 0xC0,0x1C  TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA     [RFC5054]
         * 0xC0,0x1D  TLS_SRP_SHA_WITH_AES_128_CBC_SHA          [RFC5054]
         * 0xC0,0x1E  TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA      [RFC5054]
         * 0xC0,0x1F  TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA      [RFC5054]
         * 0xC0,0x20  TLS_SRP_SHA_WITH_AES_256_CBC_SHA          [RFC5054]
         * 0xC0,0x21  TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA      [RFC5054]
         * 0xC0,0x22  TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA      [RFC5054]
         * 0xC0,0x23  TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256   [RFC5289]
         * 0xC0,0x24  TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384   [RFC5289]
         * 0xC0,0x25  TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256    [RFC5289]
         * 0xC0,0x26  TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384    [RFC5289]
         * 0xC0,0x27  TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256     [RFC5289]
         * 0xC0,0x28  TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384     [RFC5289]
         * 0xC0,0x29  TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256      [RFC5289]
         * 0xC0,0x2A  TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384      [RFC5289]
         * 0xC0,0x2B  TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256   [RFC5289]
         * 0xC0,0x2C  TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384   [RFC5289]
         * 0xC0,0x2D  TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256    [RFC5289]
         * 0xC0,0x2E  TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384    [RFC5289]
         * 0xC0,0x2F  TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256     [RFC5289]
         * 0xC0,0x30  TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384     [RFC5289]
         * 0xC0,0x31  TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256      [RFC5289]
         * 0xC0,0x32  TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384      [RFC5289]
         * 0xC0,0x33  TLS_ECDHE_PSK_WITH_RC4_128_SHA            [RFC5489]
         * 0xC0,0x34  TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA       [RFC5489]
         * 0xC0,0x35  TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA        [RFC5489]
         * 0xC0,0x36  TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA        [RFC5489]
         * 0xC0,0x37  TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256     [RFC5489]
         * 0xC0,0x38  TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384     [RFC5489]
         * 0xC0,0x39  TLS_ECDHE_PSK_WITH_NULL_SHA               [RFC5489]
         * 0xC0,0x3A  TLS_ECDHE_PSK_WITH_NULL_SHA256            [RFC5489]
         * 0xC0,0x3B  TLS_ECDHE_PSK_WITH_NULL_SHA384            [RFC5489]
         * 0xC0,0x3C-FF Unassigned
         * 0xC1-FD,*  Unassigned
         * 0xFE,0x00-FD Unassigned
         * 0xFE,0xFE-FF Reserved to avoid conflicts with widely
         *            deployed implementations                  [Pasi_Eronen]
         * 0xFF,0x00-FF Reserved for Private Use                [RFC5246]
         */

        add("SSL_NULL_WITH_NULL_NULL",
                              0x0000,   1, K_NULL,       B_NULL,    F);

        /*
         * Definition of the CipherSuites that are enabled by default.
         * They are listed in preference order, most preferred first, using
         * the following criteria:
         * 1. Prefer the stronger buld cipher, in the order of AES_256,
         *    AES_128, RC-4, 3DES-EDE.
         * 2. Prefer the stronger MAC algorithm, in the order of SHA384,
         *    SHA256, SHA, MD5.
         * 3. Prefer the better performance of key exchange and digital
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
        add("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            0xc024, --p, K_ECDHE_ECDSA, B_AES_256, T, max, tls12, P_SHA384);
        add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            0xc028, --p, K_ECDHE_RSA,   B_AES_256, T, max, tls12, P_SHA384);
        add("TLS_RSA_WITH_AES_256_CBC_SHA256",
            0x003d, --p, K_RSA,         B_AES_256, T, max, tls12, P_SHA256);
        add("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
            0xc026, --p, K_ECDH_ECDSA,  B_AES_256, T, max, tls12, P_SHA384);
        add("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            0xc02a, --p, K_ECDH_RSA,    B_AES_256, T, max, tls12, P_SHA384);
        add("TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
            0x006b, --p, K_DHE_RSA,     B_AES_256, T, max, tls12, P_SHA256);
        add("TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
            0x006a, --p, K_DHE_DSS,     B_AES_256, T, max, tls12, P_SHA256);

        add("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            0xC00A, --p, K_ECDHE_ECDSA, B_AES_256, T);
        add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            0xC014, --p, K_ECDHE_RSA,   B_AES_256, T);
        add("TLS_RSA_WITH_AES_256_CBC_SHA",
            0x0035, --p, K_RSA,         B_AES_256, T);
        add("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            0xC005, --p, K_ECDH_ECDSA,  B_AES_256, T);
        add("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            0xC00F, --p, K_ECDH_RSA,    B_AES_256, T);
        add("TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            0x0039, --p, K_DHE_RSA,     B_AES_256, T);
        add("TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
            0x0038, --p, K_DHE_DSS,     B_AES_256, T);

        add("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            0xc023, --p, K_ECDHE_ECDSA, B_AES_128, T, max, tls12, P_SHA256);
        add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            0xc027, --p, K_ECDHE_RSA,   B_AES_128, T, max, tls12, P_SHA256);
        add("TLS_RSA_WITH_AES_128_CBC_SHA256",
            0x003c, --p, K_RSA,         B_AES_128, T, max, tls12, P_SHA256);
        add("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
            0xc025, --p, K_ECDH_ECDSA,  B_AES_128, T, max, tls12, P_SHA256);
        add("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            0xc029, --p, K_ECDH_RSA,    B_AES_128, T, max, tls12, P_SHA256);
        add("TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
            0x0067, --p, K_DHE_RSA,     B_AES_128, T, max, tls12, P_SHA256);
        add("TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
            0x0040, --p, K_DHE_DSS,     B_AES_128, T, max, tls12, P_SHA256);

        add("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            0xC009, --p, K_ECDHE_ECDSA, B_AES_128, T);
        add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            0xC013, --p, K_ECDHE_RSA,   B_AES_128, T);
        add("TLS_RSA_WITH_AES_128_CBC_SHA",
            0x002f, --p, K_RSA,         B_AES_128, T);
        add("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            0xC004, --p, K_ECDH_ECDSA,  B_AES_128, T);
        add("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            0xC00E, --p, K_ECDH_RSA,    B_AES_128, T);
        add("TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            0x0033, --p, K_DHE_RSA,     B_AES_128, T);
        add("TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            0x0032, --p, K_DHE_DSS,     B_AES_128, T);

        add("TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            0xC007, --p, K_ECDHE_ECDSA, B_RC4_128, N);
        add("TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            0xC011, --p, K_ECDHE_RSA,   B_RC4_128, N);
        add("SSL_RSA_WITH_RC4_128_SHA",
            0x0005, --p, K_RSA,         B_RC4_128, N);
        add("TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            0xC002, --p, K_ECDH_ECDSA,  B_RC4_128, N);
        add("TLS_ECDH_RSA_WITH_RC4_128_SHA",
            0xC00C, --p, K_ECDH_RSA,    B_RC4_128, N);

        add("TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            0xC008, --p, K_ECDHE_ECDSA, B_3DES,    T);
        add("TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            0xC012, --p, K_ECDHE_RSA,   B_3DES,    T);
        add("SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            0x000a, --p, K_RSA,         B_3DES,    T);
        add("TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            0xC003, --p, K_ECDH_ECDSA,  B_3DES,    T);
        add("TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            0xC00D, --p, K_ECDH_RSA,    B_3DES,    T);
        add("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            0x0016, --p, K_DHE_RSA,     B_3DES,    T);
        add("SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            0x0013, --p, K_DHE_DSS,     B_3DES,    N);

        add("SSL_RSA_WITH_RC4_128_MD5",
            0x0004, --p, K_RSA,         B_RC4_128, N);

        // Renegotiation protection request Signalling Cipher Suite Value (SCSV)
        add("TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
            0x00ff, --p, K_SCSV,        B_NULL,    T);

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
         *    AES_128, RC-4, 3DES-EDE, DES, RC4_40, DES40, NULL.
         * 4. Prefer the stronger MAC algorithm, in the order of SHA384,
         *    SHA256, SHA, MD5.
         * 5. Prefer the better performance of key exchange and digital
         *    signature algorithm, in the order of ECDHE-ECDSA, ECDHE-RSA,
         *    RSA, ECDH-ECDSA, ECDH-RSA, DHE-RSA, DHE-DSS, anonymous.
         */
        p = DEFAULT_SUITES_PRIORITY;

        add("TLS_DH_anon_WITH_AES_256_CBC_SHA256",
            0x006d, --p, K_DH_ANON,     B_AES_256, N, max, tls12, P_SHA256);
        add("TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
            0xC019, --p, K_ECDH_ANON,   B_AES_256, T);
        add("TLS_DH_anon_WITH_AES_256_CBC_SHA",
            0x003a, --p, K_DH_ANON,     B_AES_256, N);

        add("TLS_DH_anon_WITH_AES_128_CBC_SHA256",
            0x006c, --p, K_DH_ANON,     B_AES_128, N, max, tls12, P_SHA256);
        add("TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            0xC018, --p, K_ECDH_ANON,   B_AES_128, T);
        add("TLS_DH_anon_WITH_AES_128_CBC_SHA",
            0x0034, --p, K_DH_ANON,     B_AES_128, N);

        add("TLS_ECDH_anon_WITH_RC4_128_SHA",
            0xC016, --p, K_ECDH_ANON,   B_RC4_128, N);
        add("SSL_DH_anon_WITH_RC4_128_MD5",
            0x0018, --p, K_DH_ANON,     B_RC4_128, N);

        add("TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
            0xC017, --p, K_ECDH_ANON,   B_3DES,    T);
        add("SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
            0x001b, --p, K_DH_ANON,     B_3DES,    N);

        add("TLS_RSA_WITH_NULL_SHA256",
            0x003b, --p, K_RSA,         B_NULL,    N, max, tls12, P_SHA256);
        add("TLS_ECDHE_ECDSA_WITH_NULL_SHA",
            0xC006, --p, K_ECDHE_ECDSA, B_NULL,    N);
        add("TLS_ECDHE_RSA_WITH_NULL_SHA",
            0xC010, --p, K_ECDHE_RSA,   B_NULL,    N);
        add("SSL_RSA_WITH_NULL_SHA",
            0x0002, --p, K_RSA,         B_NULL,    N);
        add("TLS_ECDH_ECDSA_WITH_NULL_SHA",
            0xC001, --p, K_ECDH_ECDSA,  B_NULL,    N);
        add("TLS_ECDH_RSA_WITH_NULL_SHA",
            0xC00B, --p, K_ECDH_RSA,    B_NULL,    N);
        add("TLS_ECDH_anon_WITH_NULL_SHA",
            0xC015, --p, K_ECDH_ANON,   B_NULL,    N);
        add("SSL_RSA_WITH_NULL_MD5",
            0x0001, --p, K_RSA,         B_NULL,    N);

        // weak cipher suites obsoleted in TLS 1.2
        add("SSL_RSA_WITH_DES_CBC_SHA",
            0x0009, --p, K_RSA,         B_DES,     N, tls12);
        add("SSL_DHE_RSA_WITH_DES_CBC_SHA",
            0x0015, --p, K_DHE_RSA,     B_DES,     N, tls12);
        add("SSL_DHE_DSS_WITH_DES_CBC_SHA",
            0x0012, --p, K_DHE_DSS,     B_DES,     N, tls12);
        add("SSL_DH_anon_WITH_DES_CBC_SHA",
            0x001a, --p, K_DH_ANON,     B_DES,     N, tls12);

        // weak cipher suites obsoleted in TLS 1.1
        add("SSL_RSA_EXPORT_WITH_RC4_40_MD5",
            0x0003, --p, K_RSA_EXPORT,  B_RC4_40,  N, tls11);
        add("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
            0x0017, --p, K_DH_ANON,     B_RC4_40,  N, tls11);

        add("SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            0x0008, --p, K_RSA_EXPORT,  B_DES_40,  N, tls11);
        add("SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
            0x0014, --p, K_DHE_RSA,     B_DES_40,  N, tls11);
        add("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
            0x0011, --p, K_DHE_DSS,     B_DES_40,  N, tls11);
        add("SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
            0x0019, --p, K_DH_ANON,     B_DES_40,  N, tls11);

        // Supported Kerberos ciphersuites from RFC2712
        add("TLS_KRB5_WITH_RC4_128_SHA",
            0x0020, --p, K_KRB5,        B_RC4_128, N);
        add("TLS_KRB5_WITH_RC4_128_MD5",
            0x0024, --p, K_KRB5,        B_RC4_128, N);
        add("TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
            0x001f, --p, K_KRB5,        B_3DES,    N);
        add("TLS_KRB5_WITH_3DES_EDE_CBC_MD5",
            0x0023, --p, K_KRB5,        B_3DES,    N);
        add("TLS_KRB5_WITH_DES_CBC_SHA",
            0x001e, --p, K_KRB5,        B_DES,     N, tls12);
        add("TLS_KRB5_WITH_DES_CBC_MD5",
            0x0022, --p, K_KRB5,        B_DES,     N, tls12);
        add("TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
            0x0028, --p, K_KRB5_EXPORT, B_RC4_40,  N, tls11);
        add("TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
            0x002b, --p, K_KRB5_EXPORT, B_RC4_40,  N, tls11);
        add("TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
            0x0026, --p, K_KRB5_EXPORT, B_DES_40,  N, tls11);
        add("TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",
            0x0029, --p, K_KRB5_EXPORT, B_DES_40,  N, tls11);

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
        add("SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5",          0x0006);
        add("SSL_RSA_WITH_IDEA_CBC_SHA",                   0x0007);
        add("SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA",        0x000b);
        add("SSL_DH_DSS_WITH_DES_CBC_SHA",                 0x000c);
        add("SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA",            0x000d);
        add("SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA",        0x000e);
        add("SSL_DH_RSA_WITH_DES_CBC_SHA",                 0x000f);
        add("SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA",            0x0010);

        // SSL 3.0 Fortezza ciphersuites
        add("SSL_FORTEZZA_DMS_WITH_NULL_SHA",              0x001c);
        add("SSL_FORTEZZA_DMS_WITH_FORTEZZA_CBC_SHA",      0x001d);

        // 1024/56 bit exportable ciphersuites from expired internet draft
        add("SSL_RSA_EXPORT1024_WITH_DES_CBC_SHA",         0x0062);
        add("SSL_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA",     0x0063);
        add("SSL_RSA_EXPORT1024_WITH_RC4_56_SHA",          0x0064);
        add("SSL_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA",      0x0065);
        add("SSL_DHE_DSS_WITH_RC4_128_SHA",                0x0066);

        // Netscape old and new SSL 3.0 FIPS ciphersuites
        // see http://www.mozilla.org/projects/security/pki/nss/ssl/fips-ssl-ciphersuites.html
        add("NETSCAPE_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",     0xffe0);
        add("NETSCAPE_RSA_FIPS_WITH_DES_CBC_SHA",          0xffe1);
        add("SSL_RSA_FIPS_WITH_DES_CBC_SHA",               0xfefe);
        add("SSL_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",          0xfeff);

        // Unsupported Kerberos cipher suites from RFC 2712
        add("TLS_KRB5_WITH_IDEA_CBC_SHA",                  0x0021);
        add("TLS_KRB5_WITH_IDEA_CBC_MD5",                  0x0025);
        add("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA",         0x0027);
        add("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5",         0x002a);

        // Unsupported cipher suites from RFC 4162
        add("TLS_RSA_WITH_SEED_CBC_SHA",                   0x0096);
        add("TLS_DH_DSS_WITH_SEED_CBC_SHA",                0x0097);
        add("TLS_DH_RSA_WITH_SEED_CBC_SHA",                0x0098);
        add("TLS_DHE_DSS_WITH_SEED_CBC_SHA",               0x0099);
        add("TLS_DHE_RSA_WITH_SEED_CBC_SHA",               0x009a);
        add("TLS_DH_anon_WITH_SEED_CBC_SHA",               0x009b);

        // Unsupported cipher suites from RFC 4279
        add("TLS_PSK_WITH_RC4_128_SHA",                    0x008a);
        add("TLS_PSK_WITH_3DES_EDE_CBC_SHA",               0x008b);
        add("TLS_PSK_WITH_AES_128_CBC_SHA",                0x008c);
        add("TLS_PSK_WITH_AES_256_CBC_SHA",                0x008d);
        add("TLS_DHE_PSK_WITH_RC4_128_SHA",                0x008e);
        add("TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA",           0x008f);
        add("TLS_DHE_PSK_WITH_AES_128_CBC_SHA",            0x0090);
        add("TLS_DHE_PSK_WITH_AES_256_CBC_SHA",            0x0091);
        add("TLS_RSA_PSK_WITH_RC4_128_SHA",                0x0092);
        add("TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA",           0x0093);
        add("TLS_RSA_PSK_WITH_AES_128_CBC_SHA",            0x0094);
        add("TLS_RSA_PSK_WITH_AES_256_CBC_SHA",            0x0095);

        // Unsupported cipher suites from RFC 4785
        add("TLS_PSK_WITH_NULL_SHA",                       0x002c);
        add("TLS_DHE_PSK_WITH_NULL_SHA",                   0x002d);
        add("TLS_RSA_PSK_WITH_NULL_SHA",                   0x002e);

        // Unsupported cipher suites from RFC 5246
        add("TLS_DH_DSS_WITH_AES_128_CBC_SHA",             0x0030);
        add("TLS_DH_RSA_WITH_AES_128_CBC_SHA",             0x0031);
        add("TLS_DH_DSS_WITH_AES_256_CBC_SHA",             0x0036);
        add("TLS_DH_RSA_WITH_AES_256_CBC_SHA",             0x0037);
        add("TLS_DH_DSS_WITH_AES_128_CBC_SHA256",          0x003e);
        add("TLS_DH_RSA_WITH_AES_128_CBC_SHA256",          0x003f);
        add("TLS_DH_DSS_WITH_AES_256_CBC_SHA256",          0x0068);
        add("TLS_DH_RSA_WITH_AES_256_CBC_SHA256",          0x0069);

        // Unsupported cipher suites from RFC 5288
        add("TLS_RSA_WITH_AES_128_GCM_SHA256",             0x009c);
        add("TLS_RSA_WITH_AES_256_GCM_SHA384",             0x009d);
        add("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",         0x009e);
        add("TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",         0x009f);
        add("TLS_DH_RSA_WITH_AES_128_GCM_SHA256",          0x00a0);
        add("TLS_DH_RSA_WITH_AES_256_GCM_SHA384",          0x00a1);
        add("TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",         0x00a2);
        add("TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",         0x00a3);
        add("TLS_DH_DSS_WITH_AES_128_GCM_SHA256",          0x00a4);
        add("TLS_DH_DSS_WITH_AES_256_GCM_SHA384",          0x00a5);
        add("TLS_DH_anon_WITH_AES_128_GCM_SHA256",         0x00a6);
        add("TLS_DH_anon_WITH_AES_256_GCM_SHA384",         0x00a7);

        // Unsupported cipher suites from RFC 5487
        add("TLS_PSK_WITH_AES_128_GCM_SHA256",             0x00a8);
        add("TLS_PSK_WITH_AES_256_GCM_SHA384",             0x00a9);
        add("TLS_DHE_PSK_WITH_AES_128_GCM_SHA256",         0x00aa);
        add("TLS_DHE_PSK_WITH_AES_256_GCM_SHA384",         0x00ab);
        add("TLS_RSA_PSK_WITH_AES_128_GCM_SHA256",         0x00ac);
        add("TLS_RSA_PSK_WITH_AES_256_GCM_SHA384",         0x00ad);
        add("TLS_PSK_WITH_AES_128_CBC_SHA256",             0x00ae);
        add("TLS_PSK_WITH_AES_256_CBC_SHA384",             0x00af);
        add("TLS_PSK_WITH_NULL_SHA256",                    0x00b0);
        add("TLS_PSK_WITH_NULL_SHA384",                    0x00b1);
        add("TLS_DHE_PSK_WITH_AES_128_CBC_SHA256",         0x00b2);
        add("TLS_DHE_PSK_WITH_AES_256_CBC_SHA384",         0x00b3);
        add("TLS_DHE_PSK_WITH_NULL_SHA256",                0x00b4);
        add("TLS_DHE_PSK_WITH_NULL_SHA384",                0x00b5);
        add("TLS_RSA_PSK_WITH_AES_128_CBC_SHA256",         0x00b6);
        add("TLS_RSA_PSK_WITH_AES_256_CBC_SHA384",         0x00b7);
        add("TLS_RSA_PSK_WITH_NULL_SHA256",                0x00b8);
        add("TLS_RSA_PSK_WITH_NULL_SHA384",                0x00b9);

        // Unsupported cipher suites from RFC 5932
        add("TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",           0x0041);
        add("TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA",        0x0042);
        add("TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA",        0x0043);
        add("TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",       0x0044);
        add("TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",       0x0045);
        add("TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA",       0x0046);
        add("TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",           0x0084);
        add("TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA",        0x0085);
        add("TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA",        0x0086);
        add("TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",       0x0087);
        add("TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",       0x0088);
        add("TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA",       0x0089);
        add("TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256",        0x00ba);
        add("TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256",     0x00bb);
        add("TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256",     0x00bc);
        add("TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256",    0x00bd);
        add("TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",    0x00be);
        add("TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256",    0x00bf);
        add("TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256",        0x00c0);
        add("TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256",     0x00c1);
        add("TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256",     0x00c2);
        add("TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256",    0x00c3);
        add("TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256",    0x00c4);
        add("TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256",    0x00c5);

        // Unsupported cipher suites from RFC 5054
        add("TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA",           0xc01a);
        add("TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA",       0xc01b);
        add("TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA",       0xc01c);
        add("TLS_SRP_SHA_WITH_AES_128_CBC_SHA",            0xc01d);
        add("TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA",        0xc01e);
        add("TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA",        0xc01f);
        add("TLS_SRP_SHA_WITH_AES_256_CBC_SHA",            0xc020);
        add("TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA",        0xc021);
        add("TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA",        0xc022);

        // Unsupported cipher suites from RFC 5289
        add("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",     0xc02b);
        add("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",     0xc02c);
        add("TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",      0xc02d);
        add("TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",      0xc02e);
        add("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",       0xc02f);
        add("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",       0xc030);
        add("TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",        0xc031);
        add("TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",        0xc032);

        // Unsupported cipher suites from RFC 5489
        add("TLS_ECDHE_PSK_WITH_RC4_128_SHA",              0xc033);
        add("TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA",         0xc034);
        add("TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA",          0xc035);
        add("TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA",          0xc036);
        add("TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256",       0xc037);
        add("TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384",       0xc038);
        add("TLS_ECDHE_PSK_WITH_NULL_SHA",                 0xc039);
        add("TLS_ECDHE_PSK_WITH_NULL_SHA256",              0xc03a);
        add("TLS_ECDHE_PSK_WITH_NULL_SHA384",              0xc03b);
    }

    // ciphersuite SSL_NULL_WITH_NULL_NULL
    final static CipherSuite C_NULL = CipherSuite.valueOf(0, 0);

    // ciphersuite TLS_EMPTY_RENEGOTIATION_INFO_SCSV
    final static CipherSuite C_SCSV = CipherSuite.valueOf(0x00, 0xff);
}
