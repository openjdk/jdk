/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.crypto;

import java.util.*;
import java.util.jar.*;
import java.io.*;
import java.net.URL;
import java.security.*;

import java.security.Provider.Service;

import sun.security.jca.*;
import sun.security.jca.GetInstance.Instance;

/**
 * This class instantiates implementations of JCE engine classes from
 * providers registered with the java.security.Security object.
 *
 * @author Jan Luehe
 * @author Sharon Liu
 * @since 1.4
 */

final class JceSecurity {

    static final SecureRandom RANDOM = new SecureRandom();

    // The defaultPolicy and exemptPolicy will be set up
    // in the static initializer.
    private static CryptoPermissions defaultPolicy = null;
    private static CryptoPermissions exemptPolicy = null;

    // Map<Provider,?> of the providers we already have verified
    // value == PROVIDER_VERIFIED is successfully verified
    // value is failure cause Exception in error case
    private final static Map<Provider, Object> verificationResults =
            new IdentityHashMap<>();

    // Map<Provider,?> of the providers currently being verified
    private final static Map<Provider, Object> verifyingProviders =
            new IdentityHashMap<>();

    // Set the default value. May be changed in the static initializer.
    private static boolean isRestricted = true;

    /*
     * Don't let anyone instantiate this.
     */
    private JceSecurity() {
    }

    static {
        try {
            AccessController.doPrivileged(
                new PrivilegedExceptionAction<Object>() {
                    public Object run() throws Exception {
                        setupJurisdictionPolicies();
                        return null;
                    }
                });

            isRestricted = defaultPolicy.implies(
                CryptoAllPermission.INSTANCE) ? false : true;
        } catch (Exception e) {
            throw new SecurityException(
                    "Can not initialize cryptographic mechanism", e);
        }
    }

    static Instance getInstance(String type, Class<?> clazz, String algorithm,
            String provider) throws NoSuchAlgorithmException,
            NoSuchProviderException {
        Service s = GetInstance.getService(type, algorithm, provider);
        Exception ve = getVerificationResult(s.getProvider());
        if (ve != null) {
            String msg = "JCE cannot authenticate the provider " + provider;
            throw (NoSuchProviderException)
                                new NoSuchProviderException(msg).initCause(ve);
        }
        return GetInstance.getInstance(s, clazz);
    }

    static Instance getInstance(String type, Class<?> clazz, String algorithm,
            Provider provider) throws NoSuchAlgorithmException {
        Service s = GetInstance.getService(type, algorithm, provider);
        Exception ve = JceSecurity.getVerificationResult(provider);
        if (ve != null) {
            String msg = "JCE cannot authenticate the provider "
                + provider.getName();
            throw new SecurityException(msg, ve);
        }
        return GetInstance.getInstance(s, clazz);
    }

    static Instance getInstance(String type, Class<?> clazz, String algorithm)
            throws NoSuchAlgorithmException {
        List<Service> services = GetInstance.getServices(type, algorithm);
        NoSuchAlgorithmException failure = null;
        for (Service s : services) {
            if (canUseProvider(s.getProvider()) == false) {
                // allow only signed providers
                continue;
            }
            try {
                Instance instance = GetInstance.getInstance(s, clazz);
                return instance;
            } catch (NoSuchAlgorithmException e) {
                failure = e;
            }
        }
        throw new NoSuchAlgorithmException("Algorithm " + algorithm
                + " not available", failure);
    }

    /**
     * Verify if the JAR at URL codeBase is a signed exempt application
     * JAR file and returns the permissions bundled with the JAR.
     *
     * @throws Exception on error
     */
    static CryptoPermissions verifyExemptJar(URL codeBase) throws Exception {
        JarVerifier jv = new JarVerifier(codeBase, true);
        jv.verify();
        return jv.getPermissions();
    }

    /**
     * Verify if the JAR at URL codeBase is a signed provider JAR file.
     *
     * @throws Exception on error
     */
    static void verifyProviderJar(URL codeBase) throws Exception {
        // Verify the provider JAR file and all
        // supporting JAR files if there are any.
        JarVerifier jv = new JarVerifier(codeBase, false);
        jv.verify();
    }

    private final static Object PROVIDER_VERIFIED = Boolean.TRUE;

    /*
     * Verify that the provider JAR files are signed properly, which
     * means the signer's certificate can be traced back to a
     * JCE trusted CA.
     * Return null if ok, failure Exception if verification failed.
     */
    static synchronized Exception getVerificationResult(Provider p) {
        Object o = verificationResults.get(p);
        if (o == PROVIDER_VERIFIED) {
            return null;
        } else if (o != null) {
            return (Exception)o;
        }
        if (verifyingProviders.get(p) != null) {
            // this method is static synchronized, must be recursion
            // return failure now but do not save the result
            return new NoSuchProviderException("Recursion during verification");
        }
        try {
            verifyingProviders.put(p, Boolean.FALSE);
            URL providerURL = getCodeBase(p.getClass());
            verifyProviderJar(providerURL);
            // Verified ok, cache result
            verificationResults.put(p, PROVIDER_VERIFIED);
            return null;
        } catch (Exception e) {
            verificationResults.put(p, e);
            return e;
        } finally {
            verifyingProviders.remove(p);
        }
    }

    // return whether this provider is properly signed and can be used by JCE
    static boolean canUseProvider(Provider p) {
        return getVerificationResult(p) == null;
    }

    // dummy object to represent null
    private static final URL NULL_URL;

    static {
        try {
            NULL_URL = new URL("http://null.sun.com/");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // reference to a Map we use as a cache for codebases
    private static final Map<Class<?>, URL> codeBaseCacheRef =
            new WeakHashMap<>();

    /*
     * Returns the CodeBase for the given class.
     */
    static URL getCodeBase(final Class<?> clazz) {
        synchronized (codeBaseCacheRef) {
            URL url = codeBaseCacheRef.get(clazz);
            if (url == null) {
                url = AccessController.doPrivileged(new PrivilegedAction<URL>() {
                    public URL run() {
                        ProtectionDomain pd = clazz.getProtectionDomain();
                        if (pd != null) {
                            CodeSource cs = pd.getCodeSource();
                            if (cs != null) {
                                return cs.getLocation();
                            }
                        }
                        return NULL_URL;
                    }
                });
                codeBaseCacheRef.put(clazz, url);
            }
            return (url == NULL_URL) ? null : url;
        }
    }

    private static void setupJurisdictionPolicies() throws Exception {
        String javaHomeDir = System.getProperty("java.home");
        String sep = File.separator;
        String pathToPolicyJar = javaHomeDir + sep + "lib" + sep +
            "security" + sep;

        File exportJar = new File(pathToPolicyJar, "US_export_policy.jar");
        File importJar = new File(pathToPolicyJar, "local_policy.jar");
        URL jceCipherURL = ClassLoader.getSystemResource
                ("javax/crypto/Cipher.class");

        if ((jceCipherURL == null) ||
                !exportJar.exists() || !importJar.exists()) {
            throw new SecurityException
                                ("Cannot locate policy or framework files!");
        }

        // Read jurisdiction policies.
        CryptoPermissions defaultExport = new CryptoPermissions();
        CryptoPermissions exemptExport = new CryptoPermissions();
        loadPolicies(exportJar, defaultExport, exemptExport);

        CryptoPermissions defaultImport = new CryptoPermissions();
        CryptoPermissions exemptImport = new CryptoPermissions();
        loadPolicies(importJar, defaultImport, exemptImport);

        // Merge the export and import policies for default applications.
        if (defaultExport.isEmpty() || defaultImport.isEmpty()) {
            throw new SecurityException("Missing mandatory jurisdiction " +
                                        "policy files");
        }
        defaultPolicy = defaultExport.getMinimum(defaultImport);

        // Merge the export and import policies for exempt applications.
        if (exemptExport.isEmpty())  {
            exemptPolicy = exemptImport.isEmpty() ? null : exemptImport;
        } else {
            exemptPolicy = exemptExport.getMinimum(exemptImport);
        }
    }

    /**
     * Load the policies from the specified file. Also checks that the
     * policies are correctly signed.
     */
    private static void loadPolicies(File jarPathName,
                                     CryptoPermissions defaultPolicy,
                                     CryptoPermissions exemptPolicy)
        throws Exception {

        JarFile jf = new JarFile(jarPathName);

        Enumeration<JarEntry> entries = jf.entries();
        while (entries.hasMoreElements()) {
            JarEntry je = entries.nextElement();
            InputStream is = null;
            try {
                if (je.getName().startsWith("default_")) {
                    is = jf.getInputStream(je);
                    defaultPolicy.load(is);
                } else if (je.getName().startsWith("exempt_")) {
                    is = jf.getInputStream(je);
                    exemptPolicy.load(is);
                } else {
                    continue;
                }
            } finally {
                if (is != null) {
                    is.close();
                }
            }

            // Enforce the signer restraint, i.e. signer of JCE framework
            // jar should also be the signer of the two jurisdiction policy
            // jar files.
            JarVerifier.verifyPolicySigned(je.getCertificates());
        }
        // Close and nullify the JarFile reference to help GC.
        jf.close();
        jf = null;
    }

    static CryptoPermissions getDefaultPolicy() {
        return defaultPolicy;
    }

    static CryptoPermissions getExemptPolicy() {
        return exemptPolicy;
    }

    static boolean isRestricted() {
        return isRestricted;
    }
}
