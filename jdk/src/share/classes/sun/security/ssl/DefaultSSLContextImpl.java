/*
 * Copyright (c) 2005, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import java.security.*;

import javax.net.ssl.*;

/**
 * "Default" SSLContext as returned by SSLContext.getDefault(). It comes
 * initialized with default KeyManagers and TrustManagers created using
 * various system properties.
 *
 * @since   1.6
 */
public final class DefaultSSLContextImpl extends SSLContextImpl {

    private static final String NONE = "NONE";
    private static final String P11KEYSTORE = "PKCS11";
    private static final Debug debug = Debug.getInstance("ssl");

    private static volatile SSLContextImpl defaultImpl;

    private static TrustManager[] defaultTrustManagers;

    private static KeyManager[] defaultKeyManagers;

    public DefaultSSLContextImpl() throws Exception {
        super(defaultImpl);
        try {
            super.engineInit(getDefaultKeyManager(), getDefaultTrustManager(), null);
        } catch (Exception e) {
            if (debug != null && Debug.isOn("defaultctx")) {
                System.out.println("default context init failed: " + e);
            }
            throw e;
        }
        if (defaultImpl == null) {
            defaultImpl = this;
        }
    }

    protected void engineInit(KeyManager[] km, TrustManager[] tm,
            SecureRandom sr) throws KeyManagementException {
        throw new KeyManagementException
            ("Default SSLContext is initialized automatically");
    }

    static synchronized SSLContextImpl getDefaultImpl() throws Exception {
        if (defaultImpl == null) {
            new DefaultSSLContextImpl();
        }
        return defaultImpl;
    }

    private static synchronized TrustManager[] getDefaultTrustManager() throws Exception {
        if (defaultTrustManagers != null) {
            return defaultTrustManagers;
        }

        KeyStore ks = TrustManagerFactoryImpl.getCacertsKeyStore("defaultctx");

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        defaultTrustManagers = tmf.getTrustManagers();
        return defaultTrustManagers;
    }

    private static synchronized KeyManager[] getDefaultKeyManager() throws Exception {
        if (defaultKeyManagers != null) {
            return defaultKeyManagers;
        }

        final Map<String,String> props = new HashMap<String,String>();
        AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Object>() {
            public Object run() throws Exception {
                props.put("keyStore",  System.getProperty(
                            "javax.net.ssl.keyStore", ""));
                props.put("keyStoreType", System.getProperty(
                            "javax.net.ssl.keyStoreType",
                            KeyStore.getDefaultType()));
                props.put("keyStoreProvider", System.getProperty(
                            "javax.net.ssl.keyStoreProvider", ""));
                props.put("keyStorePasswd", System.getProperty(
                            "javax.net.ssl.keyStorePassword", ""));
                return null;
            }
        });

        final String defaultKeyStore = props.get("keyStore");
        String defaultKeyStoreType = props.get("keyStoreType");
        String defaultKeyStoreProvider = props.get("keyStoreProvider");
        if (debug != null && Debug.isOn("defaultctx")) {
            System.out.println("keyStore is : " + defaultKeyStore);
            System.out.println("keyStore type is : " +
                                    defaultKeyStoreType);
            System.out.println("keyStore provider is : " +
                                    defaultKeyStoreProvider);
        }

        if (P11KEYSTORE.equals(defaultKeyStoreType) &&
                !NONE.equals(defaultKeyStore)) {
            throw new IllegalArgumentException("if keyStoreType is "
                + P11KEYSTORE + ", then keyStore must be " + NONE);
        }

        FileInputStream fs = null;
        if (defaultKeyStore.length() != 0 && !NONE.equals(defaultKeyStore)) {
            fs = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<FileInputStream>() {
                public FileInputStream run() throws Exception {
                    return new FileInputStream(defaultKeyStore);
                }
            });
        }

        String defaultKeyStorePassword = props.get("keyStorePasswd");
        char[] passwd = null;
        if (defaultKeyStorePassword.length() != 0) {
            passwd = defaultKeyStorePassword.toCharArray();
        }

        /**
         * Try to initialize key store.
         */
        KeyStore ks = null;
        if ((defaultKeyStoreType.length()) != 0) {
            if (debug != null && Debug.isOn("defaultctx")) {
                System.out.println("init keystore");
            }
            if (defaultKeyStoreProvider.length() == 0) {
                ks = KeyStore.getInstance(defaultKeyStoreType);
            } else {
                ks = KeyStore.getInstance(defaultKeyStoreType,
                                    defaultKeyStoreProvider);
            }

            // if defaultKeyStore is NONE, fs will be null
            ks.load(fs, passwd);
        }
        if (fs != null) {
            fs.close();
            fs = null;
        }

        /*
         * Try to initialize key manager.
         */
        if (debug != null && Debug.isOn("defaultctx")) {
            System.out.println("init keymanager of type " +
                KeyManagerFactory.getDefaultAlgorithm());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());

        if (P11KEYSTORE.equals(defaultKeyStoreType)) {
            kmf.init(ks, null); // do not pass key passwd if using token
        } else {
            kmf.init(ks, passwd);
        }

        defaultKeyManagers = kmf.getKeyManagers();
        return defaultKeyManagers;
    }
}
