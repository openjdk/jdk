/*
 * Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.security.*;

/**
 * The JSSE provider.
 *
 * The RSA implementation has been removed from JSSE, but we still need to
 * register the same algorithms for compatibility. We just point to the RSA
 * implementation in the SunRsaSign provider. This works because all classes
 * are in the bootclasspath and therefore loaded by the same classloader.
 *
 * SunJSSE now supports an experimental FIPS compliant mode when used with an
 * appropriate FIPS certified crypto provider. In FIPS mode, we:
 *  . allow only TLS 1.0
 *  . allow only FIPS approved ciphersuites
 *  . perform all crypto in the FIPS crypto provider
 *
 * It is currently not possible to use both FIPS compliant SunJSSE and
 * standard JSSE at the same time because of the various static data structures
 * we use.
 *
 * However, we do want to allow FIPS mode to be enabled at runtime and without
 * editing the java.security file. That means we need to allow
 * Security.removeProvider("SunJSSE") to work, which creates an instance of
 * this class in non-FIPS mode. That is why we delay the selection of the mode
 * as long as possible. This is until we open an SSL/TLS connection and the
 * data structures need to be initialized or until SunJSSE is initialized in
 * FIPS mode.
 *
 */
public abstract class SunJSSE extends java.security.Provider {

    private static final long serialVersionUID = 3231825739635378733L;

    private static String info = "Sun JSSE provider" +
        "(PKCS12, SunX509 key/trust factories, SSLv3, TLSv1)";

    private static String fipsInfo =
        "Sun JSSE provider (FIPS mode, crypto provider ";

    // tri-valued flag:
    // null  := no final decision made
    // false := data structures initialized in non-FIPS mode
    // true  := data structures initialized in FIPS mode
    private static Boolean fips;

    // the FIPS certificate crypto provider that we use to perform all crypto
    // operations. null in non-FIPS mode
    static java.security.Provider cryptoProvider;

    protected static synchronized boolean isFIPS() {
        if (fips == null) {
            fips = false;
        }
        return fips;
    }

    // ensure we can use FIPS mode using the specified crypto provider.
    // enable FIPS mode if not already enabled.
    private static synchronized void ensureFIPS(java.security.Provider p) {
        if (fips == null) {
            fips = true;
            cryptoProvider = p;
        } else {
            if (fips == false) {
                throw new ProviderException
                    ("SunJSSE already initialized in non-FIPS mode");
            }
            if (cryptoProvider != p) {
                throw new ProviderException
                    ("SunJSSE already initialized with FIPS crypto provider "
                    + cryptoProvider);
            }
        }
    }

    // standard constructor
    protected SunJSSE() {
        super("SunJSSE", 1.6d, info);
        subclassCheck();
        if (Boolean.TRUE.equals(fips)) {
            throw new ProviderException
                ("SunJSSE is already initialized in FIPS mode");
        }
        registerAlgorithms(false);
    }

    // prefered constructor to enable FIPS mode at runtime
    protected SunJSSE(java.security.Provider cryptoProvider){
        this(checkNull(cryptoProvider), cryptoProvider.getName());
    }

    // constructor to enable FIPS mode from java.security file
    protected SunJSSE(String cryptoProvider){
        this(null, checkNull(cryptoProvider));
    }

    private static <T> T checkNull(T t) {
        if (t == null) {
            throw new ProviderException("cryptoProvider must not be null");
        }
        return t;
    }

    private SunJSSE(java.security.Provider cryptoProvider, String providerName) {
        super("SunJSSE", 1.6d, fipsInfo + providerName + ")");
        subclassCheck();
        if (cryptoProvider == null) {
            // Calling Security.getProvider() will cause other providers to be
            // loaded. That is not good but unavoidable here.
            cryptoProvider = Security.getProvider(providerName);
            if (cryptoProvider == null) {
                throw new ProviderException
                    ("Crypto provider not installed: " + providerName);
            }
        }
        ensureFIPS(cryptoProvider);
        registerAlgorithms(true);
    }

    private void registerAlgorithms(final boolean isfips) {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                doRegister(isfips);
                return null;
            }
        });
    }

    private void doRegister(boolean isfips) {
        if (isfips == false) {
            put("KeyFactory.RSA",
                "sun.security.rsa.RSAKeyFactory");
            put("Alg.Alias.KeyFactory.1.2.840.113549.1.1", "RSA");
            put("Alg.Alias.KeyFactory.OID.1.2.840.113549.1.1", "RSA");

            put("KeyPairGenerator.RSA",
                "sun.security.rsa.RSAKeyPairGenerator");
            put("Alg.Alias.KeyPairGenerator.1.2.840.113549.1.1", "RSA");
            put("Alg.Alias.KeyPairGenerator.OID.1.2.840.113549.1.1", "RSA");

            put("Signature.MD2withRSA",
                "sun.security.rsa.RSASignature$MD2withRSA");
            put("Alg.Alias.Signature.1.2.840.113549.1.1.2", "MD2withRSA");
            put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.2",
                "MD2withRSA");

            put("Signature.MD5withRSA",
                "sun.security.rsa.RSASignature$MD5withRSA");
            put("Alg.Alias.Signature.1.2.840.113549.1.1.4", "MD5withRSA");
            put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.4",
                "MD5withRSA");

            put("Signature.SHA1withRSA",
                "sun.security.rsa.RSASignature$SHA1withRSA");
            put("Alg.Alias.Signature.1.2.840.113549.1.1.5", "SHA1withRSA");
            put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.5",
                "SHA1withRSA");
            put("Alg.Alias.Signature.1.3.14.3.2.29", "SHA1withRSA");
            put("Alg.Alias.Signature.OID.1.3.14.3.2.29", "SHA1withRSA");

        }
        put("Signature.MD5andSHA1withRSA",
            "sun.security.ssl.RSASignature");

        put("KeyManagerFactory.SunX509",
            "sun.security.ssl.KeyManagerFactoryImpl$SunX509");
        put("KeyManagerFactory.NewSunX509",
            "sun.security.ssl.KeyManagerFactoryImpl$X509");
        put("TrustManagerFactory.SunX509",
            "sun.security.ssl.TrustManagerFactoryImpl$SimpleFactory");
        put("TrustManagerFactory.PKIX",
            "sun.security.ssl.TrustManagerFactoryImpl$PKIXFactory");
        put("Alg.Alias.TrustManagerFactory.SunPKIX", "PKIX");
        put("Alg.Alias.TrustManagerFactory.X509", "PKIX");
        put("Alg.Alias.TrustManagerFactory.X.509", "PKIX");
        if (isfips == false) {
            put("SSLContext.SSL",
                "sun.security.ssl.SSLContextImpl");
            put("SSLContext.SSLv3",
                "sun.security.ssl.SSLContextImpl");
        }
        put("SSLContext.TLS",
            "sun.security.ssl.SSLContextImpl");
        put("SSLContext.TLSv1",
            "sun.security.ssl.SSLContextImpl");
        put("SSLContext.Default",
            "sun.security.ssl.DefaultSSLContextImpl");

        /*
         * KeyStore
         */
        put("KeyStore.PKCS12",
            "sun.security.pkcs12.PKCS12KeyStore");
    }

    private void subclassCheck() {
        if (getClass() != com.sun.net.ssl.internal.ssl.Provider.class) {
            throw new AssertionError("Illegal subclass: " + getClass());
        }
    }

    @Override
    protected final void finalize() throws Throwable {
        // empty
        super.finalize();
    }

}
