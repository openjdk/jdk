/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.security.*;

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
 * - LDAP is the CertStore type for LDAP repositories. The
 *   LDAPSchema attribute notes the specification defining the
 *   schema that this provider uses to find certificates and CRLs.
 *
 * - JavaPolicy is the default file-based Policy type.
 *
 * - JavaLoginConfig is the default file-based LoginModule Configuration type.
 */

final class SunEntries {

    private SunEntries() {
        // empty
    }

    static void putEntries(Map<Object, Object> map) {

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

        if (nativeAvailable && useNativePRNG) {
            map.put("SecureRandom.NativePRNG",
                "sun.security.provider.NativePRNG");
        }
        map.put("SecureRandom.SHA1PRNG",
             "sun.security.provider.SecureRandom");
        if (nativeAvailable && !useNativePRNG) {
            map.put("SecureRandom.NativePRNG",
                "sun.security.provider.NativePRNG");
        }

        if (NativePRNG.Blocking.isAvailable()) {
            map.put("SecureRandom.NativePRNGBlocking",
                "sun.security.provider.NativePRNG$Blocking");
        }

        if (NativePRNG.NonBlocking.isAvailable()) {
            map.put("SecureRandom.NativePRNGNonBlocking",
                "sun.security.provider.NativePRNG$NonBlocking");
        }

        /*
         * Signature engines
         */
        map.put("Signature.SHA1withDSA",
                "sun.security.provider.DSA$SHA1withDSA");
        map.put("Signature.NONEwithDSA", "sun.security.provider.DSA$RawDSA");
        map.put("Alg.Alias.Signature.RawDSA", "NONEwithDSA");
        map.put("Signature.SHA224withDSA",
                "sun.security.provider.DSA$SHA224withDSA");
        map.put("Signature.SHA256withDSA",
                "sun.security.provider.DSA$SHA256withDSA");

        String dsaKeyClasses = "java.security.interfaces.DSAPublicKey" +
                "|java.security.interfaces.DSAPrivateKey";
        map.put("Signature.SHA1withDSA SupportedKeyClasses", dsaKeyClasses);
        map.put("Signature.NONEwithDSA SupportedKeyClasses", dsaKeyClasses);
        map.put("Signature.SHA224withDSA SupportedKeyClasses", dsaKeyClasses);
        map.put("Signature.SHA256withDSA SupportedKeyClasses", dsaKeyClasses);

        map.put("Alg.Alias.Signature.DSA", "SHA1withDSA");
        map.put("Alg.Alias.Signature.DSS", "SHA1withDSA");
        map.put("Alg.Alias.Signature.SHA/DSA", "SHA1withDSA");
        map.put("Alg.Alias.Signature.SHA-1/DSA", "SHA1withDSA");
        map.put("Alg.Alias.Signature.SHA1/DSA", "SHA1withDSA");
        map.put("Alg.Alias.Signature.SHAwithDSA", "SHA1withDSA");
        map.put("Alg.Alias.Signature.DSAWithSHA1", "SHA1withDSA");
        map.put("Alg.Alias.Signature.OID.1.2.840.10040.4.3",
                "SHA1withDSA");
        map.put("Alg.Alias.Signature.1.2.840.10040.4.3", "SHA1withDSA");
        map.put("Alg.Alias.Signature.1.3.14.3.2.13", "SHA1withDSA");
        map.put("Alg.Alias.Signature.1.3.14.3.2.27", "SHA1withDSA");
        map.put("Alg.Alias.Signature.OID.2.16.840.1.101.3.4.3.1",
                "SHA224withDSA");
        map.put("Alg.Alias.Signature.2.16.840.1.101.3.4.3.1", "SHA224withDSA");
        map.put("Alg.Alias.Signature.OID.2.16.840.1.101.3.4.3.2",
                "SHA256withDSA");
        map.put("Alg.Alias.Signature.2.16.840.1.101.3.4.3.2", "SHA256withDSA");

        /*
         *  Key Pair Generator engines
         */
        map.put("KeyPairGenerator.DSA",
            "sun.security.provider.DSAKeyPairGenerator");
        map.put("Alg.Alias.KeyPairGenerator.OID.1.2.840.10040.4.1", "DSA");
        map.put("Alg.Alias.KeyPairGenerator.1.2.840.10040.4.1", "DSA");
        map.put("Alg.Alias.KeyPairGenerator.1.3.14.3.2.12", "DSA");

        /*
         * Digest engines
         */
        map.put("MessageDigest.MD2", "sun.security.provider.MD2");
        map.put("MessageDigest.MD5", "sun.security.provider.MD5");
        map.put("MessageDigest.SHA", "sun.security.provider.SHA");

        map.put("Alg.Alias.MessageDigest.SHA-1", "SHA");
        map.put("Alg.Alias.MessageDigest.SHA1", "SHA");
        map.put("Alg.Alias.MessageDigest.1.3.14.3.2.26", "SHA");
        map.put("Alg.Alias.MessageDigest.OID.1.3.14.3.2.26", "SHA");

        map.put("MessageDigest.SHA-224", "sun.security.provider.SHA2$SHA224");
        map.put("Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.4", "SHA-224");
        map.put("Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.4",
                "SHA-224");

        map.put("MessageDigest.SHA-256", "sun.security.provider.SHA2$SHA256");
        map.put("Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.1", "SHA-256");
        map.put("Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.1",
                "SHA-256");
        map.put("MessageDigest.SHA-384", "sun.security.provider.SHA5$SHA384");
        map.put("Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.2", "SHA-384");
        map.put("Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.2",
                "SHA-384");
        map.put("MessageDigest.SHA-512", "sun.security.provider.SHA5$SHA512");
        map.put("Alg.Alias.MessageDigest.2.16.840.1.101.3.4.2.3", "SHA-512");
        map.put("Alg.Alias.MessageDigest.OID.2.16.840.1.101.3.4.2.3",
                "SHA-512");

        /*
         * Algorithm Parameter Generator engines
         */
        map.put("AlgorithmParameterGenerator.DSA",
            "sun.security.provider.DSAParameterGenerator");

        /*
         * Algorithm Parameter engines
         */
        map.put("AlgorithmParameters.DSA",
            "sun.security.provider.DSAParameters");
        map.put("Alg.Alias.AlgorithmParameters.OID.1.2.840.10040.4.1", "DSA");
        map.put("Alg.Alias.AlgorithmParameters.1.2.840.10040.4.1", "DSA");
        map.put("Alg.Alias.AlgorithmParameters.1.3.14.3.2.12", "DSA");

        /*
         * Key factories
         */
        map.put("KeyFactory.DSA", "sun.security.provider.DSAKeyFactory");
        map.put("Alg.Alias.KeyFactory.OID.1.2.840.10040.4.1", "DSA");
        map.put("Alg.Alias.KeyFactory.1.2.840.10040.4.1", "DSA");
        map.put("Alg.Alias.KeyFactory.1.3.14.3.2.12", "DSA");

        /*
         * Certificates
         */
        map.put("CertificateFactory.X.509",
            "sun.security.provider.X509Factory");
        map.put("Alg.Alias.CertificateFactory.X509", "X.509");

        /*
         * KeyStore
         */
        map.put("KeyStore.JKS", "sun.security.provider.JavaKeyStore$JKS");
        map.put("KeyStore.CaseExactJKS",
                        "sun.security.provider.JavaKeyStore$CaseExactJKS");
        map.put("KeyStore.DKS", "sun.security.provider.DomainKeyStore$DKS");

        /*
         * Policy
         */
        map.put("Policy.JavaPolicy", "sun.security.provider.PolicySpiFile");

        /*
         * Configuration
         */
        map.put("Configuration.JavaLoginConfig",
                        "sun.security.provider.ConfigFile$Spi");

        /*
         * CertPathBuilder
         */
        map.put("CertPathBuilder.PKIX",
            "sun.security.provider.certpath.SunCertPathBuilder");
        map.put("CertPathBuilder.PKIX ValidationAlgorithm",
            "RFC5280");

        /*
         * CertPathValidator
         */
        map.put("CertPathValidator.PKIX",
            "sun.security.provider.certpath.PKIXCertPathValidator");
        map.put("CertPathValidator.PKIX ValidationAlgorithm",
            "RFC5280");

        /*
         * CertStores
         */
        map.put("CertStore.LDAP",
            "sun.security.provider.certpath.ldap.LDAPCertStore");
        map.put("CertStore.LDAP LDAPSchema", "RFC2587");
        map.put("CertStore.Collection",
            "sun.security.provider.certpath.CollectionCertStore");
        map.put("CertStore.com.sun.security.IndexedCollection",
            "sun.security.provider.certpath.IndexedCollectionCertStore");

        /*
         * KeySize
         */
        map.put("Signature.NONEwithDSA KeySize", "1024");
        map.put("Signature.SHA1withDSA KeySize", "1024");
        map.put("Signature.SHA224withDSA KeySize", "2048");
        map.put("Signature.SHA256withDSA KeySize", "2048");

        map.put("KeyPairGenerator.DSA KeySize", "2048");
        map.put("AlgorithmParameterGenerator.DSA KeySize", "2048");

        /*
         * Implementation type: software or hardware
         */
        map.put("Signature.SHA1withDSA ImplementedIn", "Software");
        map.put("KeyPairGenerator.DSA ImplementedIn", "Software");
        map.put("MessageDigest.MD5 ImplementedIn", "Software");
        map.put("MessageDigest.SHA ImplementedIn", "Software");
        map.put("AlgorithmParameterGenerator.DSA ImplementedIn",
            "Software");
        map.put("AlgorithmParameters.DSA ImplementedIn", "Software");
        map.put("KeyFactory.DSA ImplementedIn", "Software");
        map.put("SecureRandom.SHA1PRNG ImplementedIn", "Software");
        map.put("CertificateFactory.X.509 ImplementedIn", "Software");
        map.put("KeyStore.JKS ImplementedIn", "Software");
        map.put("CertPathValidator.PKIX ImplementedIn", "Software");
        map.put("CertPathBuilder.PKIX ImplementedIn", "Software");
        map.put("CertStore.LDAP ImplementedIn", "Software");
        map.put("CertStore.Collection ImplementedIn", "Software");
        map.put("CertStore.com.sun.security.IndexedCollection ImplementedIn",
            "Software");

    }

    // name of the *System* property, takes precedence over PROP_RNDSOURCE
    private final static String PROP_EGD = "java.security.egd";
    // name of the *Security* property
    private final static String PROP_RNDSOURCE = "securerandom.source";

    final static String URL_DEV_RANDOM = "file:/dev/random";
    final static String URL_DEV_URANDOM = "file:/dev/urandom";

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
                    System.getProperty("user.dir")).toURI();
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
