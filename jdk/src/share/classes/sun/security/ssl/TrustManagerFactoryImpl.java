/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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
import java.io.*;
import java.math.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;
import java.security.spec.AlgorithmParameterSpec;

import sun.security.validator.Validator;

abstract class TrustManagerFactoryImpl extends TrustManagerFactorySpi {

    private static final Debug debug = Debug.getInstance("ssl");
    private X509TrustManager trustManager = null;
    private boolean isInitialized = false;

    TrustManagerFactoryImpl() {
        // empty
    }

    protected void engineInit(KeyStore ks) throws KeyStoreException {
        if (ks == null) {
            try {
                ks = getCacertsKeyStore("trustmanager");
            } catch (SecurityException se) {
                // eat security exceptions but report other throwables
                if (debug != null && Debug.isOn("trustmanager")) {
                    System.out.println(
                        "SunX509: skip default keystore: " + se);
                }
            } catch (Error err) {
                if (debug != null && Debug.isOn("trustmanager")) {
                    System.out.println(
                        "SunX509: skip default keystore: " + err);
                }
                throw err;
            } catch (RuntimeException re) {
                if (debug != null && Debug.isOn("trustmanager")) {
                    System.out.println(
                        "SunX509: skip default keystore: " + re);
                }
                throw re;
            } catch (Exception e) {
                if (debug != null && Debug.isOn("trustmanager")) {
                    System.out.println(
                        "SunX509: skip default keystore: " + e);
                }
                throw new KeyStoreException(
                    "problem accessing trust store" + e);
            }
        }
        trustManager = getInstance(ks);
        isInitialized = true;
    }

    abstract X509TrustManager getInstance(KeyStore ks) throws KeyStoreException;

    abstract X509TrustManager getInstance(ManagerFactoryParameters spec)
            throws InvalidAlgorithmParameterException;

    protected void engineInit(ManagerFactoryParameters spec) throws
            InvalidAlgorithmParameterException {
        trustManager = getInstance(spec);
        isInitialized = true;
    }

    /**
     * Returns one trust manager for each type of trust material.
     */
    protected TrustManager[] engineGetTrustManagers() {
        if (!isInitialized) {
            throw new IllegalStateException(
                        "TrustManagerFactoryImpl is not initialized");
        }
        return new TrustManager[] { trustManager };
    }

    /*
     * Try to get an InputStream based on the file we pass in.
     */
    private static FileInputStream getFileInputStream(final File file)
            throws Exception {
        return AccessController.doPrivileged(
                new PrivilegedExceptionAction<FileInputStream>() {
                    public FileInputStream run() throws Exception {
                        try {
                            if (file.exists()) {
                                return new FileInputStream(file);
                            } else {
                                return null;
                            }
                        } catch (FileNotFoundException e) {
                            // couldn't find it, oh well.
                            return null;
                        }
                    }
                });
    }

    /**
     * Returns the keystore with the configured CA certificates.
     */
    static KeyStore getCacertsKeyStore(String dbgname) throws Exception
    {
        String storeFileName = null;
        File storeFile = null;
        FileInputStream fis = null;
        String defaultTrustStoreType;
        String defaultTrustStoreProvider;
        final HashMap<String,String> props = new HashMap<String,String>();
        final String sep = File.separator;
        KeyStore ks = null;

        AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
            public Void run() throws Exception {
                props.put("trustStore", System.getProperty(
                                "javax.net.ssl.trustStore"));
                props.put("javaHome", System.getProperty(
                                        "java.home"));
                props.put("trustStoreType", System.getProperty(
                                "javax.net.ssl.trustStoreType",
                                KeyStore.getDefaultType()));
                props.put("trustStoreProvider", System.getProperty(
                                "javax.net.ssl.trustStoreProvider", ""));
                props.put("trustStorePasswd", System.getProperty(
                                "javax.net.ssl.trustStorePassword", ""));
                return null;
            }
        });

        /*
         * Try:
         *      javax.net.ssl.trustStore  (if this variable exists, stop)
         *      jssecacerts
         *      cacerts
         *
         * If none exists, we use an empty keystore.
         */

        storeFileName = props.get("trustStore");
        if (!"NONE".equals(storeFileName)) {
            if (storeFileName != null) {
                storeFile = new File(storeFileName);
                fis = getFileInputStream(storeFile);
            } else {
                String javaHome = props.get("javaHome");
                storeFile = new File(javaHome + sep + "lib" + sep
                                                + "security" + sep +
                                                "jssecacerts");
                if ((fis = getFileInputStream(storeFile)) == null) {
                    storeFile = new File(javaHome + sep + "lib" + sep
                                                + "security" + sep +
                                                "cacerts");
                    fis = getFileInputStream(storeFile);
                }
            }

            if (fis != null) {
                storeFileName = storeFile.getPath();
            } else {
                storeFileName = "No File Available, using empty keystore.";
            }
        }

        defaultTrustStoreType = props.get("trustStoreType");
        defaultTrustStoreProvider = props.get("trustStoreProvider");
        if (debug != null && Debug.isOn(dbgname)) {
            System.out.println("trustStore is: " + storeFileName);
            System.out.println("trustStore type is : " +
                                defaultTrustStoreType);
            System.out.println("trustStore provider is : " +
                                defaultTrustStoreProvider);
        }

        /*
         * Try to initialize trust store.
         */
        if (defaultTrustStoreType.length() != 0) {
            if (debug != null && Debug.isOn(dbgname)) {
                System.out.println("init truststore");
            }
            if (defaultTrustStoreProvider.length() == 0) {
                ks = KeyStore.getInstance(defaultTrustStoreType);
            } else {
                ks = KeyStore.getInstance(defaultTrustStoreType,
                                        defaultTrustStoreProvider);
            }
            char[] passwd = null;
            String defaultTrustStorePassword = props.get("trustStorePasswd");
            if (defaultTrustStorePassword.length() != 0)
                passwd = defaultTrustStorePassword.toCharArray();

            // if trustStore is NONE, fis will be null
            ks.load(fis, passwd);

            // Zero out the temporary password storage
            if (passwd != null) {
                for (int i = 0; i < passwd.length; i++) {
                    passwd[i] = (char)0;
                }
            }
        }

        if (fis != null) {
            fis.close();
        }

        return ks;
    }

    public static final class SimpleFactory extends TrustManagerFactoryImpl {
        X509TrustManager getInstance(KeyStore ks) throws KeyStoreException {
            return new X509TrustManagerImpl(Validator.TYPE_SIMPLE, ks);
        }
        X509TrustManager getInstance(ManagerFactoryParameters spec)
                throws InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException
                ("SunX509 TrustManagerFactory does not use "
                + "ManagerFactoryParameters");
        }
   }

    public static final class PKIXFactory extends TrustManagerFactoryImpl {
        X509TrustManager getInstance(KeyStore ks) throws KeyStoreException {
            return new X509TrustManagerImpl(Validator.TYPE_PKIX, ks);
        }
        X509TrustManager getInstance(ManagerFactoryParameters spec)
                throws InvalidAlgorithmParameterException {
            if (spec instanceof CertPathTrustManagerParameters == false) {
                throw new InvalidAlgorithmParameterException
                    ("Parameters must be CertPathTrustManagerParameters");
            }
            CertPathParameters params =
                ((CertPathTrustManagerParameters)spec).getParameters();
            if (params instanceof PKIXBuilderParameters == false) {
                throw new InvalidAlgorithmParameterException
                    ("Encapsulated parameters must be PKIXBuilderParameters");
            }
            PKIXBuilderParameters pkixParams = (PKIXBuilderParameters)params;
            return new X509TrustManagerImpl(Validator.TYPE_PKIX, pkixParams);
        }
    }
}
