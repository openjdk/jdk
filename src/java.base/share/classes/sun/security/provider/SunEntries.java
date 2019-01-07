/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.io.*;
import java.net.*;
import java.util.*;
import java.security.*;

import jdk.internal.util.StaticProperty;
import sun.security.action.GetPropertyAction;

/**
 * Defines the entries of the SUN provider.
 *
 * Algorithms supported, and their names:
 *
 * - SHA is the message digest scheme described in FIPS 180-1.
 *   Aliases for SHA are SHA-1 and SHA1.
 *
 * - SHA1withDSA is the signature scheme described in FIPS 186.
 *   (SHA used in DSA is SHA-1: FIPS 186 with Change No 1.)
 *   Aliases for SHA1withDSA are DSA, DSS, SHA/DSA, SHA-1/DSA, SHA1/DSA,
 *   SHAwithDSA, DSAWithSHA1, and the object
 *   identifier strings "OID.1.3.14.3.2.13", "OID.1.3.14.3.2.27" and
 *   "OID.1.2.840.10040.4.3".
 *
 * - SHA-2 is a set of message digest schemes described in FIPS 180-2.
 *   SHA-2 family of hash functions includes SHA-224, SHA-256, SHA-384,
 *   and SHA-512.
 *
 * - SHA-224withDSA/SHA-256withDSA are the signature schemes
 *   described in FIPS 186-3. The associated object identifiers are
 *   "OID.2.16.840.1.101.3.4.3.1", and "OID.2.16.840.1.101.3.4.3.2".

 * - DSA is the key generation scheme as described in FIPS 186.
 *   Aliases for DSA include the OID strings "OID.1.3.14.3.2.12"
 *   and "OID.1.2.840.10040.4.1".
 *
 * - MD5 is the message digest scheme described in RFC 1321.
 *   There are no aliases for MD5.
 *
 * - X.509 is the certificate factory type for X.509 certificates
 *   and CRLs. Aliases for X.509 are X509.
 *
 * - PKIX is the certification path validation algorithm described
 *   in RFC 5280. The ValidationAlgorithm attribute notes the
 *   specification that this provider implements.
 *
 * - JavaPolicy is the default file-based Policy type.
 *
 * - JavaLoginConfig is the default file-based LoginModule Configuration type.
 */

public final class SunEntries {

    // create an aliases List from the specified aliases
    public static List<String> createAliases(String ... aliases) {
        return Arrays.asList(aliases);
    }

    // create an aliases List from the specified oid followed by other aliases
    public static List<String> createAliasesWithOid(String ... oids) {
        String[] result = Arrays.copyOf(oids, oids.length + 1);
        result[result.length - 1] = "OID." + oids[0];
        return Arrays.asList(result);
    }

    // extend LinkedHashSet to preserve the ordering (needed by SecureRandom?)
    SunEntries(Provider p) {
        services = new LinkedHashSet<>(50, 0.9f);

        // start populating content using the specified provider

        // common attribute map
        HashMap<String, String> attrs = new HashMap<>(3);

        /*
         * SecureRandom
         *
         * Register these first to speed up "new SecureRandom()",
         * which iterates through the list of algorithms
         */
        // register the native PRNG, if available
        // if user selected /dev/urandom, we put it before SHA1PRNG,
        // otherwise after it
        boolean nativeAvailable = NativePRNG.isAvailable();
        boolean useNativePRNG = seedSource.equals(URL_DEV_URANDOM) ||
            seedSource.equals(URL_DEV_RANDOM);

        attrs.put("ThreadSafe", "true");
        if (nativeAvailable && useNativePRNG) {
            add(p, "SecureRandom", "NativePRNG",
               "sun.security.provider.NativePRNG", null, attrs);
        }
        attrs.put("ImplementedIn", "Software");
        add(p, "SecureRandom", "DRBG", "sun.security.provider.DRBG", null, attrs);
        add(p, "SecureRandom", "SHA1PRNG",
            "sun.security.provider.SecureRandom", null, attrs);
        attrs.remove("ImplementedIn");
        if (nativeAvailable && !useNativePRNG) {
            add(p, "SecureRandom", "NativePRNG", "sun.security.provider.NativePRNG",
               null, attrs);
        }

        if (NativePRNG.Blocking.isAvailable()) {
            add(p, "SecureRandom", "NativePRNGBlocking",
                "sun.security.provider.NativePRNG$Blocking", null, attrs);
        }
        if (NativePRNG.NonBlocking.isAvailable()) {
            add(p, "SecureRandom", "NativePRNGNonBlocking",
                "sun.security.provider.NativePRNG$NonBlocking", null, attrs);
        }

        /*
         * Signature engines
         */
        attrs.clear();
        String dsaKeyClasses = "java.security.interfaces.DSAPublicKey" +
                "|java.security.interfaces.DSAPrivateKey";
        attrs.put("SupportedKeyClasses", dsaKeyClasses);
        attrs.put("ImplementedIn", "Software");

        attrs.put("KeySize", "1024"); // for NONE and SHA1 DSA signatures

        add(p, "Signature", "SHA1withDSA",
                "sun.security.provider.DSA$SHA1withDSA",
                createAliasesWithOid("1.2.840.10040.4.3", "DSA", "DSS", "SHA/DSA",
                    "SHA-1/DSA", "SHA1/DSA", "SHAwithDSA", "DSAWithSHA1",
                    "1.3.14.3.2.13", "1.3.14.3.2.27"), attrs);
        add(p, "Signature", "NONEwithDSA", "sun.security.provider.DSA$RawDSA",
                createAliases("RawDSA"), attrs);

        attrs.put("KeySize", "2048"); // for SHA224 and SHA256 DSA signatures

        add(p, "Signature", "SHA224withDSA",
                "sun.security.provider.DSA$SHA224withDSA",
                createAliasesWithOid("2.16.840.1.101.3.4.3.1"), attrs);
        add(p, "Signature", "SHA256withDSA",
                "sun.security.provider.DSA$SHA256withDSA",
                createAliasesWithOid("2.16.840.1.101.3.4.3.2"), attrs);

        attrs.remove("KeySize");

        add(p, "Signature", "SHA1withDSAinP1363Format",
                "sun.security.provider.DSA$SHA1withDSAinP1363Format",
                null, null);
        add(p, "Signature", "NONEwithDSAinP1363Format",
                "sun.security.provider.DSA$RawDSAinP1363Format",
                null, null);
        add(p, "Signature", "SHA224withDSAinP1363Format",
                "sun.security.provider.DSA$SHA224withDSAinP1363Format",
                null, null);
        add(p, "Signature", "SHA256withDSAinP1363Format",
                "sun.security.provider.DSA$SHA256withDSAinP1363Format",
                null, null);

        /*
         *  Key Pair Generator engines
         */
        attrs.clear();
        attrs.put("ImplementedIn", "Software");
        attrs.put("KeySize", "2048"); // for DSA KPG and APG only

        String dsaOid = "1.2.840.10040.4.1";
        List<String> dsaAliases = createAliasesWithOid(dsaOid, "1.3.14.3.2.12");
        String dsaKPGImplClass = "sun.security.provider.DSAKeyPairGenerator$";
        dsaKPGImplClass += (useLegacyDSA? "Legacy" : "Current");
        add(p, "KeyPairGenerator", "DSA", dsaKPGImplClass, dsaAliases, attrs);

        /*
         * Algorithm Parameter Generator engines
         */
        add(p, "AlgorithmParameterGenerator", "DSA",
            "sun.security.provider.DSAParameterGenerator", dsaAliases, attrs);
        attrs.remove("KeySize");

        /*
         * Algorithm Parameter engines
         */
        add(p, "AlgorithmParameters", "DSA",
                "sun.security.provider.DSAParameters", dsaAliases, attrs);

        /*
         * Key factories
         */
        add(p, "KeyFactory", "DSA", "sun.security.provider.DSAKeyFactory",
                dsaAliases, attrs);

        /*
         * Digest engines
         */
        add(p, "MessageDigest", "MD2", "sun.security.provider.MD2", null, attrs);
        add(p, "MessageDigest", "MD5", "sun.security.provider.MD5", null, attrs);
        add(p, "MessageDigest", "SHA", "sun.security.provider.SHA",
                createAliasesWithOid("1.3.14.3.2.26", "SHA-1", "SHA1"), attrs);

        String sha2BaseOid = "2.16.840.1.101.3.4.2";
        add(p, "MessageDigest", "SHA-224", "sun.security.provider.SHA2$SHA224",
                createAliasesWithOid(sha2BaseOid + ".4"), attrs);
        add(p, "MessageDigest", "SHA-256", "sun.security.provider.SHA2$SHA256",
                createAliasesWithOid(sha2BaseOid + ".1"), attrs);
        add(p, "MessageDigest", "SHA-384", "sun.security.provider.SHA5$SHA384",
                createAliasesWithOid(sha2BaseOid + ".2"), attrs);
        add(p, "MessageDigest", "SHA-512", "sun.security.provider.SHA5$SHA512",
                createAliasesWithOid(sha2BaseOid + ".3"), attrs);
        add(p, "MessageDigest", "SHA-512/224",
                "sun.security.provider.SHA5$SHA512_224",
                createAliasesWithOid(sha2BaseOid + ".5"), attrs);
        add(p, "MessageDigest", "SHA-512/256",
                "sun.security.provider.SHA5$SHA512_256",
                createAliasesWithOid(sha2BaseOid + ".6"), attrs);
        add(p, "MessageDigest", "SHA3-224", "sun.security.provider.SHA3$SHA224",
                createAliasesWithOid(sha2BaseOid + ".7"), attrs);
        add(p, "MessageDigest", "SHA3-256", "sun.security.provider.SHA3$SHA256",
                createAliasesWithOid(sha2BaseOid + ".8"), attrs);
        add(p, "MessageDigest", "SHA3-384", "sun.security.provider.SHA3$SHA384",
                createAliasesWithOid(sha2BaseOid + ".9"), attrs);
        add(p, "MessageDigest", "SHA3-512", "sun.security.provider.SHA3$SHA512",
                createAliasesWithOid(sha2BaseOid + ".10"), attrs);

        /*
         * Certificates
         */
        add(p, "CertificateFactory", "X.509",
                "sun.security.provider.X509Factory",
                createAliases("X509"), attrs);

        /*
         * KeyStore
         */
        add(p, "KeyStore", "PKCS12",
                "sun.security.pkcs12.PKCS12KeyStore$DualFormatPKCS12",
                null, null);
        add(p, "KeyStore", "JKS",
                "sun.security.provider.JavaKeyStore$DualFormatJKS",
                null, attrs);
        add(p, "KeyStore", "CaseExactJKS",
                "sun.security.provider.JavaKeyStore$CaseExactJKS",
                null, attrs);
        add(p, "KeyStore", "DKS", "sun.security.provider.DomainKeyStore$DKS",
                null, attrs);


        /*
         * CertStores
         */
        add(p, "CertStore", "Collection",
                "sun.security.provider.certpath.CollectionCertStore",
                null, attrs);
        add(p, "CertStore", "com.sun.security.IndexedCollection",
                "sun.security.provider.certpath.IndexedCollectionCertStore",
                null, attrs);

        /*
         * Policy
         */
        add(p, "Policy", "JavaPolicy", "sun.security.provider.PolicySpiFile",
                null, null);

        /*
         * Configuration
         */
        add(p, "Configuration", "JavaLoginConfig",
                "sun.security.provider.ConfigFile$Spi", null, null);

        /*
         * CertPathBuilder and CertPathValidator
         */
        attrs.clear();
        attrs.put("ValidationAlgorithm", "RFC5280");
        attrs.put("ImplementedIn", "Software");

        add(p, "CertPathBuilder", "PKIX",
                "sun.security.provider.certpath.SunCertPathBuilder",
                null, attrs);
        add(p, "CertPathValidator", "PKIX",
                "sun.security.provider.certpath.PKIXCertPathValidator",
                null, attrs);
    }

    Iterator<Provider.Service> iterator() {
        return services.iterator();
    }

    private void add(Provider p, String type, String algo, String cn,
             List<String> aliases, HashMap<String, String> attrs) {
         services.add(new Provider.Service(p, type, algo, cn, aliases, attrs));
    }

    private LinkedHashSet<Provider.Service> services;

    // name of the *System* property, takes precedence over PROP_RNDSOURCE
    private static final String PROP_EGD = "java.security.egd";
    // name of the *Security* property
    private static final String PROP_RNDSOURCE = "securerandom.source";

    private static final boolean useLegacyDSA =
        Boolean.parseBoolean(GetPropertyAction.privilegedGetProperty
            ("jdk.security.legacyDSAKeyPairGenerator"));

    static final String URL_DEV_RANDOM = "file:/dev/random";
    static final String URL_DEV_URANDOM = "file:/dev/urandom";

    private static final String seedSource;

    static {
        seedSource = AccessController.doPrivileged(
                new PrivilegedAction<String>() {

            @Override
            public String run() {
                String egdSource = System.getProperty(PROP_EGD, "");
                if (egdSource.length() != 0) {
                    return egdSource;
                }
                egdSource = Security.getProperty(PROP_RNDSOURCE);
                if (egdSource == null) {
                    return "";
                }
                return egdSource;
            }
        });
    }

    static String getSeedSource() {
        return seedSource;
    }

    /*
     * Use a URI to access this File. Previous code used a URL
     * which is less strict on syntax. If we encounter a
     * URISyntaxException we make best efforts for backwards
     * compatibility. e.g. space character in deviceName string.
     *
     * Method called within PrivilegedExceptionAction block.
     *
     * Moved from SeedGenerator to avoid initialization problems with
     * signed providers.
     */
    static File getDeviceFile(URL device) throws IOException {
        try {
            URI deviceURI = device.toURI();
            if(deviceURI.isOpaque()) {
                // File constructor does not accept opaque URI
                URI localDir = new File(
                    StaticProperty.userDir()).toURI();
                String uriPath = localDir.toString() +
                                     deviceURI.toString().substring(5);
                return new File(URI.create(uriPath));
            } else {
                return new File(deviceURI);
            }
        } catch (URISyntaxException use) {
            /*
             * Make best effort to access this File.
             * We can try using the URL path.
             */
            return new File(device.getPath());
        }
    }
}
