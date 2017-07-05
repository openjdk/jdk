/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


package sun.security.ssl;

import java.util.*;

import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import sun.security.ssl.CipherSuite.*;
import static sun.security.ssl.CipherSuite.KeyExchange.*;
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
    // It is true because we do not have a Java ECC implementation.
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

    // key exchange, bulk cipher, and mac algorithms. See those classes below.
    final KeyExchange keyExchange;
    final BulkCipher cipher;
    final MacAlg macAlg;

    // whether a CipherSuite qualifies as exportable under 512/40 bit rules.
    final boolean exportable;

    // true iff implemented and enabled at compile time
    final boolean allowed;

    private CipherSuite(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher, boolean allowed) {
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
        } else if (name.endsWith("_NULL")) {
            macAlg = M_NULL;
        } else {
            throw new IllegalArgumentException
                    ("Unknown MAC algorithm for ciphersuite " + name);
        }

        allowed &= keyExchange.allowed;
        allowed &= cipher.allowed;
        this.allowed = allowed;
    }

    private CipherSuite(String name, int id) {
        this.name = name;
        this.id = id;
        this.allowed = false;

        this.priority = 0;
        this.keyExchange = null;
        this.cipher = null;
        this.macAlg = null;
        this.exportable = false;
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
        CipherSuite c = (CipherSuite)nameMap.get(s);
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

    private static void add(String name, int id, int priority,
            KeyExchange keyExchange, BulkCipher cipher, boolean allowed) {
        CipherSuite c = new CipherSuite(name, id, priority, keyExchange,
                                        cipher, allowed);
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
        K_KRB5_EXPORT("KRB5_EXPORT", true);

        // name of the key exchange algorithm, e.g. DHE_DSS
        final String name;
        final boolean allowed;
        private final boolean alwaysAvailable;

        KeyExchange(String name, boolean allowed) {
            this.name = name;
            this.allowed = allowed;
            this.alwaysAvailable = allowed && (name.startsWith("EC") == false);
        }

        boolean isAvailable() {
            if (alwaysAvailable) {
                return true;
            }
            return allowed && JsseJce.isEcAvailable();
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
                                            new HashMap<BulkCipher,Boolean>(8);

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

        BulkCipher(String transformation, int keySize,
                int expandedKeySize, int ivSize, boolean allowed) {
            this.transformation = transformation;
            this.algorithm = transformation.split("/")[0];
            this.description = this.algorithm + "/" + (keySize << 3);
            this.keySize = keySize;
            this.ivSize = ivSize;
            this.allowed = allowed;

            this.expandedKeySize = expandedKeySize;
            this.exportable = true;
        }

        BulkCipher(String transformation, int keySize, int ivSize, boolean allowed) {
            this.transformation = transformation;
            this.algorithm = transformation.split("/")[0];
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
        CipherBox newCipher(ProtocolVersion version, SecretKey key, IvParameterSpec iv,
                boolean encrypt) throws NoSuchAlgorithmException {
            return CipherBox.newCipherBox(version, this, key, iv, encrypt);
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
            Boolean b = (Boolean)availableCache.get(cipher);
            if (b == null) {
                try {
                    SecretKey key = new SecretKeySpec
                            (new byte[cipher.expandedKeySize], cipher.algorithm);
                    IvParameterSpec iv = new IvParameterSpec(new byte[cipher.ivSize]);
                    cipher.newCipher(ProtocolVersion.DEFAULT, key, iv, true);
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
     * Also contains a factory method to obtain in initialized MAC
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
    final static BulkCipher B_NULL    = new BulkCipher("NULL",     0,  0, 0, true);
    final static BulkCipher B_RC4_40  = new BulkCipher(CIPHER_RC4, 5, 16, 0, true);
    final static BulkCipher B_RC2_40  = new BulkCipher("RC2",      5, 16, 8, false);
    final static BulkCipher B_DES_40  = new BulkCipher(CIPHER_DES, 5,  8, 8, true);

    // domestic strength ciphers
    final static BulkCipher B_RC4_128 = new BulkCipher(CIPHER_RC4,  16,  0, true);
    final static BulkCipher B_DES     = new BulkCipher(CIPHER_DES,   8,  8, true);
    final static BulkCipher B_3DES    = new BulkCipher(CIPHER_3DES, 24,  8, true);
    final static BulkCipher B_IDEA    = new BulkCipher("IDEA",      16,  8, false);
    final static BulkCipher B_AES_128 = new BulkCipher(CIPHER_AES,  16, 16, true);
    final static BulkCipher B_AES_256 = new BulkCipher(CIPHER_AES,  32, 16, true);

    // MACs
    final static MacAlg M_NULL = new MacAlg("NULL", 0);
    final static MacAlg M_MD5  = new MacAlg("MD5", 16);
    final static MacAlg M_SHA  = new MacAlg("SHA", 20);

    static {
        idMap = new HashMap<Integer,CipherSuite>();
        nameMap = new HashMap<String,CipherSuite>();

        final boolean F = false;
        final boolean T = true;
        // N: ciphersuites only allowed if we are not in FIPS mode
        final boolean N = (SunJSSE.isFIPS() == false);

add("SSL_NULL_WITH_NULL_NULL",                0x0000,   1, K_NULL,       B_NULL,    F);

        // Definition of the CipherSuites that are enabled by default.
        // They are listed in preference order, most preferred first.
        int p = DEFAULT_SUITES_PRIORITY * 2;

add("SSL_RSA_WITH_RC4_128_MD5",              0x0004, --p, K_RSA,        B_RC4_128, N);
add("SSL_RSA_WITH_RC4_128_SHA",              0x0005, --p, K_RSA,        B_RC4_128, N);
add("TLS_RSA_WITH_AES_128_CBC_SHA",          0x002f, --p, K_RSA,        B_AES_128, T);
add("TLS_RSA_WITH_AES_256_CBC_SHA",          0x0035, --p, K_RSA,        B_AES_256, T);

add("TLS_ECDH_ECDSA_WITH_RC4_128_SHA",       0xC002, --p, K_ECDH_ECDSA, B_RC4_128, N);
add("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",   0xC004, --p, K_ECDH_ECDSA, B_AES_128, T);
add("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",   0xC005, --p, K_ECDH_ECDSA, B_AES_256, T);
add("TLS_ECDH_RSA_WITH_RC4_128_SHA",         0xC00C, --p, K_ECDH_RSA,   B_RC4_128, N);
add("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",     0xC00E, --p, K_ECDH_RSA,   B_AES_128, T);
add("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",     0xC00F, --p, K_ECDH_RSA,   B_AES_256, T);

add("TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",      0xC007, --p, K_ECDHE_ECDSA,B_RC4_128, N);
add("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",  0xC009, --p, K_ECDHE_ECDSA,B_AES_128, T);
add("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",  0xC00A, --p, K_ECDHE_ECDSA,B_AES_256, T);
add("TLS_ECDHE_RSA_WITH_RC4_128_SHA",        0xC011, --p, K_ECDHE_RSA,  B_RC4_128, N);
add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",    0xC013, --p, K_ECDHE_RSA,  B_AES_128, T);
add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",    0xC014, --p, K_ECDHE_RSA,  B_AES_256, T);

add("TLS_DHE_RSA_WITH_AES_128_CBC_SHA",      0x0033, --p, K_DHE_RSA,    B_AES_128, T);
add("TLS_DHE_RSA_WITH_AES_256_CBC_SHA",      0x0039, --p, K_DHE_RSA,    B_AES_256, T);
add("TLS_DHE_DSS_WITH_AES_128_CBC_SHA",      0x0032, --p, K_DHE_DSS,    B_AES_128, T);
add("TLS_DHE_DSS_WITH_AES_256_CBC_SHA",      0x0038, --p, K_DHE_DSS,    B_AES_256, T);

add("SSL_RSA_WITH_3DES_EDE_CBC_SHA",         0x000a, --p, K_RSA,        B_3DES,    T);
add("TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",  0xC003, --p, K_ECDH_ECDSA, B_3DES,    T);
add("TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",    0xC00D, --p, K_ECDH_RSA,   B_3DES,    T);
add("TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA", 0xC008, --p, K_ECDHE_ECDSA,B_3DES,    T);
add("TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",   0xC012, --p, K_ECDHE_RSA,  B_3DES,    T);
add("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",     0x0016, --p, K_DHE_RSA,    B_3DES,    T);
add("SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",     0x0013, --p, K_DHE_DSS,    B_3DES,    N);

add("SSL_RSA_WITH_DES_CBC_SHA",              0x0009, --p, K_RSA,        B_DES,     N);
add("SSL_DHE_RSA_WITH_DES_CBC_SHA",          0x0015, --p, K_DHE_RSA,    B_DES,     N);
add("SSL_DHE_DSS_WITH_DES_CBC_SHA",          0x0012, --p, K_DHE_DSS,    B_DES,     N);
add("SSL_RSA_EXPORT_WITH_RC4_40_MD5",        0x0003, --p, K_RSA_EXPORT, B_RC4_40,  N);
add("SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",     0x0008, --p, K_RSA_EXPORT, B_DES_40,  N);
add("SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", 0x0014, --p, K_DHE_RSA,    B_DES_40,  N);
add("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA", 0x0011, --p, K_DHE_DSS,    B_DES_40,  N);

        // Definition of the CipherSuites that are supported but not enabled
        // by default.
        // They are listed in preference order, preferred first.
        p = DEFAULT_SUITES_PRIORITY;

// Anonymous key exchange and the NULL ciphers
add("SSL_RSA_WITH_NULL_MD5",                 0x0001, --p, K_RSA,        B_NULL,    N);
add("SSL_RSA_WITH_NULL_SHA",                 0x0002, --p, K_RSA,        B_NULL,    N);
add("TLS_ECDH_ECDSA_WITH_NULL_SHA",          0xC001, --p, K_ECDH_ECDSA, B_NULL,    N);
add("TLS_ECDH_RSA_WITH_NULL_SHA",            0xC00B, --p, K_ECDH_RSA,   B_NULL,    N);
add("TLS_ECDHE_ECDSA_WITH_NULL_SHA",         0xC006, --p, K_ECDHE_ECDSA,B_NULL,    N);
add("TLS_ECDHE_RSA_WITH_NULL_SHA",           0xC010, --p, K_ECDHE_RSA,  B_NULL,    N);

add("SSL_DH_anon_WITH_RC4_128_MD5",          0x0018, --p, K_DH_ANON,    B_RC4_128, N);
add("TLS_DH_anon_WITH_AES_128_CBC_SHA",      0x0034, --p, K_DH_ANON,    B_AES_128, N);
add("TLS_DH_anon_WITH_AES_256_CBC_SHA",      0x003a, --p, K_DH_ANON,    B_AES_256, N);
add("SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",     0x001b, --p, K_DH_ANON,    B_3DES,    N);
add("SSL_DH_anon_WITH_DES_CBC_SHA",          0x001a, --p, K_DH_ANON,    B_DES,     N);

add("TLS_ECDH_anon_WITH_RC4_128_SHA",        0xC016, --p, K_ECDH_ANON,  B_RC4_128, N);
add("TLS_ECDH_anon_WITH_AES_128_CBC_SHA",    0xC018, --p, K_ECDH_ANON,  B_AES_128, T);
add("TLS_ECDH_anon_WITH_AES_256_CBC_SHA",    0xC019, --p, K_ECDH_ANON,  B_AES_256, T);
add("TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",   0xC017, --p, K_ECDH_ANON,  B_3DES,    T);

add("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",    0x0017, --p, K_DH_ANON,    B_RC4_40,  N);
add("SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA", 0x0019, --p, K_DH_ANON,    B_DES_40,  N);

add("TLS_ECDH_anon_WITH_NULL_SHA",           0xC015, --p, K_ECDH_ANON,  B_NULL,    N);

// Supported Kerberos ciphersuites from RFC2712
add("TLS_KRB5_WITH_RC4_128_SHA",             0x0020, --p, K_KRB5,        B_RC4_128, N);
add("TLS_KRB5_WITH_RC4_128_MD5",             0x0024, --p, K_KRB5,        B_RC4_128, N);
add("TLS_KRB5_WITH_3DES_EDE_CBC_SHA",        0x001f, --p, K_KRB5,        B_3DES,    N);
add("TLS_KRB5_WITH_3DES_EDE_CBC_MD5",        0x0023, --p, K_KRB5,        B_3DES,    N);
add("TLS_KRB5_WITH_DES_CBC_SHA",             0x001e, --p, K_KRB5,        B_DES,     N);
add("TLS_KRB5_WITH_DES_CBC_MD5",             0x0022, --p, K_KRB5,        B_DES,     N);
add("TLS_KRB5_EXPORT_WITH_RC4_40_SHA",       0x0028, --p, K_KRB5_EXPORT, B_RC4_40,  N);
add("TLS_KRB5_EXPORT_WITH_RC4_40_MD5",       0x002b, --p, K_KRB5_EXPORT, B_RC4_40,  N);
add("TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",   0x0026, --p, K_KRB5_EXPORT, B_DES_40,  N);
add("TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",   0x0029, --p, K_KRB5_EXPORT, B_DES_40,  N);


        // Register the names of a few additional CipherSuites.
        // Makes them show up as names instead of numbers in
        // the debug output.

        // remaining unsupported ciphersuites defined in RFC2246.
        add("SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5",      0x0006);
        add("SSL_RSA_WITH_IDEA_CBC_SHA",               0x0007);
        add("SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA",    0x000b);
        add("SSL_DH_DSS_WITH_DES_CBC_SHA",             0x000c);
        add("SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA",        0x000d);
        add("SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA",    0x000e);
        add("SSL_DH_RSA_WITH_DES_CBC_SHA",             0x000f);
        add("SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA",        0x0010);

        // SSL 3.0 Fortezza ciphersuites
        add("SSL_FORTEZZA_DMS_WITH_NULL_SHA",          0x001c);
        add("SSL_FORTEZZA_DMS_WITH_FORTEZZA_CBC_SHA",  0x001d);

        // 1024/56 bit exportable ciphersuites from expired internet draft
        add("SSL_RSA_EXPORT1024_WITH_DES_CBC_SHA",     0x0062);
        add("SSL_DHE_DSS_EXPORT1024_WITH_DES_CBC_SHA", 0x0063);
        add("SSL_RSA_EXPORT1024_WITH_RC4_56_SHA",      0x0064);
        add("SSL_DHE_DSS_EXPORT1024_WITH_RC4_56_SHA",  0x0065);
        add("SSL_DHE_DSS_WITH_RC4_128_SHA",            0x0066);

        // Netscape old and new SSL 3.0 FIPS ciphersuites
        // see http://www.mozilla.org/projects/security/pki/nss/ssl/fips-ssl-ciphersuites.html
        add("NETSCAPE_RSA_FIPS_WITH_3DES_EDE_CBC_SHA", 0xffe0);
        add("NETSCAPE_RSA_FIPS_WITH_DES_CBC_SHA",      0xffe1);
        add("SSL_RSA_FIPS_WITH_DES_CBC_SHA",           0xfefe);
        add("SSL_RSA_FIPS_WITH_3DES_EDE_CBC_SHA",      0xfeff);

        // Unsupported Kerberos cipher suites from RFC 2712
        add("TLS_KRB5_WITH_IDEA_CBC_SHA",              0x0021);
        add("TLS_KRB5_WITH_IDEA_CBC_MD5",              0x0025);
        add("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA",     0x0027);
        add("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5",     0x002a);

    }

    // ciphersuite SSL_NULL_WITH_NULL_NULL
    final static CipherSuite C_NULL = CipherSuite.valueOf(0, 0);

}
