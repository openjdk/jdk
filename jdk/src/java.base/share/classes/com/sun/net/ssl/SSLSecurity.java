/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * NOTE:  this file was copied from javax.net.ssl.SSLSecurity,
 * but was heavily modified to allow com.sun.* users to
 * access providers written using the javax.sun.* APIs.
 */

package com.sun.net.ssl;

import java.util.*;
import java.io.*;
import java.security.*;
import java.security.Provider.Service;
import java.net.Socket;

import sun.security.jca.*;

/**
 * This class instantiates implementations of JSSE engine classes from
 * providers registered with the java.security.Security object.
 *
 * @author Jan Luehe
 * @author Jeff Nisewanger
 * @author Brad Wetmore
 */

final class SSLSecurity {

    /*
     * Don't let anyone instantiate this.
     */
    private SSLSecurity() {
    }


    // ProviderList.getService() is not accessible now, implement our own loop
    private static Service getService(String type, String alg) {
        ProviderList list = Providers.getProviderList();
        for (Provider p : list.providers()) {
            Service s = p.getService(type, alg);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    /**
     * The body of the driver for the getImpl method.
     */
    private static Object[] getImpl1(String algName, String engineType,
            Service service) throws NoSuchAlgorithmException
    {
        Provider provider = service.getProvider();
        String className = service.getClassName();
        Class<?> implClass;
        try {
            ClassLoader cl = provider.getClass().getClassLoader();
            if (cl == null) {
                // system class
                implClass = Class.forName(className);
            } else {
                implClass = cl.loadClass(className);
            }
        } catch (ClassNotFoundException e) {
            throw new NoSuchAlgorithmException("Class " + className +
                                                " configured for " +
                                                engineType +
                                                " not found: " +
                                                e.getMessage());
        } catch (SecurityException e) {
            throw new NoSuchAlgorithmException("Class " + className +
                                                " configured for " +
                                                engineType +
                                                " cannot be accessed: " +
                                                e.getMessage());
        }

        /*
         * JSSE 1.0, 1.0.1, and 1.0.2 used the com.sun.net.ssl API as the
         * API was being developed.  As JSSE was folded into the main
         * release, it was decided to promote the com.sun.net.ssl API to
         * be javax.net.ssl.  It is desired to keep binary compatibility
         * with vendors of JSSE implementation written using the
         * com.sun.net.sll API, so we do this magic to handle everything.
         *
         * API used     Implementation used     Supported?
         * ========     ===================     ==========
         * com.sun      javax                   Yes
         * com.sun      com.sun                 Yes
         * javax        javax                   Yes
         * javax        com.sun                 Not Currently
         *
         * Make sure the implementation class is a subclass of the
         * corresponding engine class.
         *
         * In wrapping these classes, there's no way to know how to
         * wrap all possible classes that extend the TrustManager/KeyManager.
         * We only wrap the x509 variants.
         */

        try {   // catch instantiation errors

            /*
             * (The following Class.forName()s should alway work, because
             * this class and all the SPI classes in javax.crypto are
             * loaded by the same class loader.)  That is, unless they
             * give us a SPI class that doesn't exist, say SSLFoo,
             * or someone has removed classes from the jsse.jar file.
             */

            Class<?> typeClassJavax;
            Class<?> typeClassCom;
            Object obj = null;

            /*
             * Odds are more likely that we have a javax variant, try this
             * first.
             */
            if (((typeClassJavax = Class.forName("javax.net.ssl." +
                    engineType + "Spi")) != null) &&
                    (checkSuperclass(implClass, typeClassJavax))) {

                if (engineType.equals("SSLContext")) {
                    obj = new SSLContextSpiWrapper(algName, provider);
                } else if (engineType.equals("TrustManagerFactory")) {
                    obj = new TrustManagerFactorySpiWrapper(algName, provider);
                } else if (engineType.equals("KeyManagerFactory")) {
                    obj = new KeyManagerFactorySpiWrapper(algName, provider);
                } else {
                    /*
                     * We should throw an error if we get
                     * something totally unexpected.  Don't ever
                     * expect to see this one...
                     */
                    throw new IllegalStateException(
                        "Class " + implClass.getName() +
                        " unknown engineType wrapper:" + engineType);
                }

            } else if (((typeClassCom = Class.forName("com.sun.net.ssl." +
                        engineType + "Spi")) != null) &&
                        (checkSuperclass(implClass, typeClassCom))) {
                obj = service.newInstance(null);
            }

            if (obj != null) {
                return new Object[] { obj, provider };
            } else {
                throw new NoSuchAlgorithmException(
                    "Couldn't locate correct object or wrapper: " +
                    engineType + " " + algName);
            }

        } catch (ClassNotFoundException e) {
            IllegalStateException exc = new IllegalStateException(
                "Engine Class Not Found for " + engineType);
            exc.initCause(e);
            throw exc;
        }
    }

    /**
     * Returns an array of objects: the first object in the array is
     * an instance of an implementation of the requested algorithm
     * and type, and the second object in the array identifies the provider
     * of that implementation.
     * The <code>provName</code> argument can be null, in which case all
     * configured providers will be searched in order of preference.
     */
    static Object[] getImpl(String algName, String engineType, String provName)
        throws NoSuchAlgorithmException, NoSuchProviderException
    {
        Service service;
        if (provName != null) {
            ProviderList list = Providers.getProviderList();
            Provider prov = list.getProvider(provName);
            if (prov == null) {
                throw new NoSuchProviderException("No such provider: " +
                                                  provName);
            }
            service = prov.getService(engineType, algName);
        } else {
            service = getService(engineType, algName);
        }
        if (service == null) {
            throw new NoSuchAlgorithmException("Algorithm " + algName
                                               + " not available");
        }
        return getImpl1(algName, engineType, service);
    }


    /**
     * Returns an array of objects: the first object in the array is
     * an instance of an implementation of the requested algorithm
     * and type, and the second object in the array identifies the provider
     * of that implementation.
     * The <code>prov</code> argument can be null, in which case all
     * configured providers will be searched in order of preference.
     */
    static Object[] getImpl(String algName, String engineType, Provider prov)
        throws NoSuchAlgorithmException
    {
        Service service = prov.getService(engineType, algName);
        if (service == null) {
            throw new NoSuchAlgorithmException("No such algorithm: " +
                                               algName);
        }
        return getImpl1(algName, engineType, service);
    }

    /*
     * Checks whether one class is the superclass of another
     */
    private static boolean checkSuperclass(Class<?> subclass, Class<?> superclass) {
        if ((subclass == null) || (superclass == null))
                return false;

        while (!subclass.equals(superclass)) {
            subclass = subclass.getSuperclass();
            if (subclass == null) {
                return false;
            }
        }
        return true;
    }

    /*
     * Return at most the first "resize" elements of an array.
     *
     * Didn't want to use java.util.Arrays, as PJava may not have it.
     */
    static Object[] truncateArray(Object[] oldArray, Object[] newArray) {

        for (int i = 0; i < newArray.length; i++) {
            newArray[i] = oldArray[i];
        }

        return newArray;
    }

}


/*
 * =================================================================
 * The remainder of this file is for the wrapper and wrapper-support
 * classes.  When SSLSecurity finds something which extends the
 * javax.net.ssl.*Spi, we need to go grab a real instance of the
 * thing that the Spi supports, and wrap into a com.sun.net.ssl.*Spi
 * object.  This also mean that anything going down into the SPI
 * needs to be wrapped, as well as anything coming back up.
 */
final class SSLContextSpiWrapper extends SSLContextSpi {

    private javax.net.ssl.SSLContext theSSLContext;

    SSLContextSpiWrapper(String algName, Provider prov) throws
            NoSuchAlgorithmException {
        theSSLContext = javax.net.ssl.SSLContext.getInstance(algName, prov);
    }

    protected void engineInit(KeyManager[] kma, TrustManager[] tma,
            SecureRandom sr) throws KeyManagementException {

        // Keep track of the actual number of array elements copied
        int dst;
        int src;
        javax.net.ssl.KeyManager[] kmaw;
        javax.net.ssl.TrustManager[] tmaw;

        // Convert com.sun.net.ssl.kma to a javax.net.ssl.kma
        // wrapper if need be.
        if (kma != null) {
            kmaw = new javax.net.ssl.KeyManager[kma.length];
            for (src = 0, dst = 0; src < kma.length; ) {
                /*
                 * These key managers may implement both javax
                 * and com.sun interfaces, so if they do
                 * javax, there's no need to wrap them.
                 */
                if (!(kma[src] instanceof javax.net.ssl.KeyManager)) {
                    /*
                     * Do we know how to convert them?  If not, oh well...
                     * We'll have to drop them on the floor in this
                     * case, cause we don't know how to handle them.
                     * This will be pretty rare, but put here for
                     * completeness.
                     */
                    if (kma[src] instanceof X509KeyManager) {
                        kmaw[dst] = (javax.net.ssl.KeyManager)
                            new X509KeyManagerJavaxWrapper(
                            (X509KeyManager)kma[src]);
                        dst++;
                    }
                } else {
                    // We can convert directly, since they implement.
                    kmaw[dst] = (javax.net.ssl.KeyManager)kma[src];
                    dst++;
                }
                src++;
            }

            /*
             * If dst != src, there were more items in the original array
             * than in the new array.  Compress the new elements to avoid
             * any problems down the road.
             */
            if (dst != src) {
                    kmaw = (javax.net.ssl.KeyManager [])
                        SSLSecurity.truncateArray(kmaw,
                            new javax.net.ssl.KeyManager [dst]);
            }
        } else {
            kmaw = null;
        }

        // Now do the same thing with the TrustManagers.
        if (tma != null) {
            tmaw = new javax.net.ssl.TrustManager[tma.length];

            for (src = 0, dst = 0; src < tma.length; ) {
                /*
                 * These key managers may implement both...see above...
                 */
                if (!(tma[src] instanceof javax.net.ssl.TrustManager)) {
                    // Do we know how to convert them?
                    if (tma[src] instanceof X509TrustManager) {
                        tmaw[dst] = (javax.net.ssl.TrustManager)
                            new X509TrustManagerJavaxWrapper(
                            (X509TrustManager)tma[src]);
                        dst++;
                    }
                } else {
                    tmaw[dst] = (javax.net.ssl.TrustManager)tma[src];
                    dst++;
                }
                src++;
            }

            if (dst != src) {
                tmaw = (javax.net.ssl.TrustManager [])
                    SSLSecurity.truncateArray(tmaw,
                        new javax.net.ssl.TrustManager [dst]);
            }
        } else {
            tmaw = null;
        }

        theSSLContext.init(kmaw, tmaw, sr);
    }

    protected javax.net.ssl.SSLSocketFactory
            engineGetSocketFactory() {
        return theSSLContext.getSocketFactory();
    }

    protected javax.net.ssl.SSLServerSocketFactory
            engineGetServerSocketFactory() {
        return theSSLContext.getServerSocketFactory();
    }

}

final class TrustManagerFactorySpiWrapper extends TrustManagerFactorySpi {

    private javax.net.ssl.TrustManagerFactory theTrustManagerFactory;

    TrustManagerFactorySpiWrapper(String algName, Provider prov) throws
            NoSuchAlgorithmException {
        theTrustManagerFactory =
            javax.net.ssl.TrustManagerFactory.getInstance(algName, prov);
    }

    protected void engineInit(KeyStore ks) throws KeyStoreException {
        theTrustManagerFactory.init(ks);
    }

    protected TrustManager[] engineGetTrustManagers() {

        int dst;
        int src;

        javax.net.ssl.TrustManager[] tma =
            theTrustManagerFactory.getTrustManagers();

        TrustManager[] tmaw = new TrustManager[tma.length];

        for (src = 0, dst = 0; src < tma.length; ) {
            if (!(tma[src] instanceof com.sun.net.ssl.TrustManager)) {
                // We only know how to wrap X509TrustManagers, as
                // TrustManagers don't have any methods to wrap.
                if (tma[src] instanceof javax.net.ssl.X509TrustManager) {
                    tmaw[dst] = (TrustManager)
                        new X509TrustManagerComSunWrapper(
                        (javax.net.ssl.X509TrustManager)tma[src]);
                    dst++;
                }
            } else {
                tmaw[dst] = (TrustManager)tma[src];
                dst++;
            }
            src++;
        }

        if (dst != src) {
            tmaw = (TrustManager [])
                SSLSecurity.truncateArray(tmaw, new TrustManager [dst]);
        }

        return tmaw;
    }

}

final class KeyManagerFactorySpiWrapper extends KeyManagerFactorySpi {

    private javax.net.ssl.KeyManagerFactory theKeyManagerFactory;

    KeyManagerFactorySpiWrapper(String algName, Provider prov) throws
            NoSuchAlgorithmException {
        theKeyManagerFactory =
            javax.net.ssl.KeyManagerFactory.getInstance(algName, prov);
    }

    protected void engineInit(KeyStore ks, char[] password)
            throws KeyStoreException, NoSuchAlgorithmException,
            UnrecoverableKeyException {
        theKeyManagerFactory.init(ks, password);
    }

    protected KeyManager[] engineGetKeyManagers() {

        int dst;
        int src;

        javax.net.ssl.KeyManager[] kma =
            theKeyManagerFactory.getKeyManagers();

        KeyManager[] kmaw = new KeyManager[kma.length];

        for (src = 0, dst = 0; src < kma.length; ) {
            if (!(kma[src] instanceof com.sun.net.ssl.KeyManager)) {
                // We only know how to wrap X509KeyManagers, as
                // KeyManagers don't have any methods to wrap.
                if (kma[src] instanceof javax.net.ssl.X509KeyManager) {
                    kmaw[dst] = (KeyManager)
                        new X509KeyManagerComSunWrapper(
                        (javax.net.ssl.X509KeyManager)kma[src]);
                    dst++;
                }
            } else {
                kmaw[dst] = (KeyManager)kma[src];
                dst++;
            }
            src++;
        }

        if (dst != src) {
            kmaw = (KeyManager [])
                SSLSecurity.truncateArray(kmaw, new KeyManager [dst]);
        }

        return kmaw;
    }

}

// =================================

final class X509KeyManagerJavaxWrapper implements
        javax.net.ssl.X509KeyManager {

    private X509KeyManager theX509KeyManager;

    X509KeyManagerJavaxWrapper(X509KeyManager obj) {
        theX509KeyManager = obj;
    }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return theX509KeyManager.getClientAliases(keyType, issuers);
    }

    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {
        String retval;

        if (keyTypes == null) {
            return null;
        }

        /*
         * Scan the list, look for something we can pass back.
         */
        for (int i = 0; i < keyTypes.length; i++) {
            if ((retval = theX509KeyManager.chooseClientAlias(keyTypes[i],
                    issuers)) != null)
                return retval;
        }
        return null;

    }

    /*
     * JSSE 1.0.x was only socket based, but it's possible someone might
     * want to install a really old provider.  We should at least
     * try to be nice.
     */
    public String chooseEngineClientAlias(
            String[] keyTypes, Principal[] issuers,
            javax.net.ssl.SSLEngine engine) {
        String retval;

        if (keyTypes == null) {
            return null;
        }

        /*
         * Scan the list, look for something we can pass back.
         */
        for (int i = 0; i < keyTypes.length; i++) {
            if ((retval = theX509KeyManager.chooseClientAlias(keyTypes[i],
                    issuers)) != null)
                return retval;
        }

        return null;
    }

    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return theX509KeyManager.getServerAliases(keyType, issuers);
    }

    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {

        if (keyType == null) {
            return null;
        }
        return theX509KeyManager.chooseServerAlias(keyType, issuers);
    }

    /*
     * JSSE 1.0.x was only socket based, but it's possible someone might
     * want to install a really old provider.  We should at least
     * try to be nice.
     */
    public String chooseEngineServerAlias(
            String keyType, Principal[] issuers,
            javax.net.ssl.SSLEngine engine) {

        if (keyType == null) {
            return null;
        }
        return theX509KeyManager.chooseServerAlias(keyType, issuers);
    }

    public java.security.cert.X509Certificate[]
            getCertificateChain(String alias) {
        return theX509KeyManager.getCertificateChain(alias);
    }

    public PrivateKey getPrivateKey(String alias) {
        return theX509KeyManager.getPrivateKey(alias);
    }
}

final class X509TrustManagerJavaxWrapper implements
        javax.net.ssl.X509TrustManager {

    private X509TrustManager theX509TrustManager;

    X509TrustManagerJavaxWrapper(X509TrustManager obj) {
        theX509TrustManager = obj;
    }

    public void checkClientTrusted(
            java.security.cert.X509Certificate[] chain, String authType)
        throws java.security.cert.CertificateException {
        if (!theX509TrustManager.isClientTrusted(chain)) {
            throw new java.security.cert.CertificateException(
                "Untrusted Client Certificate Chain");
        }
    }

    public void checkServerTrusted(
            java.security.cert.X509Certificate[] chain, String authType)
        throws java.security.cert.CertificateException {
        if (!theX509TrustManager.isServerTrusted(chain)) {
            throw new java.security.cert.CertificateException(
                "Untrusted Server Certificate Chain");
        }
    }

    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return theX509TrustManager.getAcceptedIssuers();
    }
}

final class X509KeyManagerComSunWrapper implements X509KeyManager {

    private javax.net.ssl.X509KeyManager theX509KeyManager;

    X509KeyManagerComSunWrapper(javax.net.ssl.X509KeyManager obj) {
        theX509KeyManager = obj;
    }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return theX509KeyManager.getClientAliases(keyType, issuers);
    }

    public String chooseClientAlias(String keyType, Principal[] issuers) {
        String [] keyTypes = new String [] { keyType };
        return theX509KeyManager.chooseClientAlias(keyTypes, issuers, null);
    }

    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return theX509KeyManager.getServerAliases(keyType, issuers);
    }

    public String chooseServerAlias(String keyType, Principal[] issuers) {
        return theX509KeyManager.chooseServerAlias(keyType, issuers, null);
    }

    public java.security.cert.X509Certificate[]
            getCertificateChain(String alias) {
        return theX509KeyManager.getCertificateChain(alias);
    }

    public PrivateKey getPrivateKey(String alias) {
        return theX509KeyManager.getPrivateKey(alias);
    }
}

final class X509TrustManagerComSunWrapper implements X509TrustManager {

    private javax.net.ssl.X509TrustManager theX509TrustManager;

    X509TrustManagerComSunWrapper(javax.net.ssl.X509TrustManager obj) {
        theX509TrustManager = obj;
    }

    public boolean isClientTrusted(
            java.security.cert.X509Certificate[] chain) {
        try {
            theX509TrustManager.checkClientTrusted(chain, "UNKNOWN");
            return true;
        } catch (java.security.cert.CertificateException e) {
            return false;
        }
    }

    public boolean isServerTrusted(
            java.security.cert.X509Certificate[] chain) {
        try {
            theX509TrustManager.checkServerTrusted(chain, "UNKNOWN");
            return true;
        } catch (java.security.cert.CertificateException e) {
            return false;
        }
    }

    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return theX509TrustManager.getAcceptedIssuers();
    }
}
