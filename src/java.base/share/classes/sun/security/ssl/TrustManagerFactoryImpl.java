/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;
import sun.security.validator.TrustStoreUtil;
import sun.security.validator.Validator;

abstract class TrustManagerFactoryImpl extends TrustManagerFactorySpi {

    private X509TrustManager trustManager = null;
    private boolean isInitialized = false;

    TrustManagerFactoryImpl() {
        // empty
    }

    @Override
    protected void engineInit(KeyStore ks) throws KeyStoreException {
        if (ks == null) {
            try {
                trustManager = getInstance(TrustStoreManager.getTrustedCerts());
            } catch (SecurityException se) {
                // eat security exceptions but report other throwables
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.TRUSTMANAGER)) {
                    SSLLogger.fine(
                            "SunX509: skip default keystore", se);
                }
            } catch (Error err) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.TRUSTMANAGER)) {
                    SSLLogger.fine(
                        "SunX509: skip default keystore", err);
                }
                throw err;
            } catch (RuntimeException re) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.TRUSTMANAGER)) {
                    SSLLogger.fine(
                        "SunX509: skip default keystore", re);
                }
                throw re;
            } catch (Exception e) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.TRUSTMANAGER)) {
                    SSLLogger.fine(
                        "SunX509: skip default keystore", e);
                }
                throw new KeyStoreException(
                    "problem accessing trust store", e);
            }
        } else {
            trustManager = getInstance(TrustStoreUtil.getTrustedCerts(ks));
        }

        isInitialized = true;
    }

    abstract X509TrustManager getInstance(
            Collection<X509Certificate> trustedCerts);

    abstract X509TrustManager getInstance(ManagerFactoryParameters spec)
            throws InvalidAlgorithmParameterException;

    @Override
    protected void engineInit(ManagerFactoryParameters spec) throws
            InvalidAlgorithmParameterException {
        trustManager = getInstance(spec);
        isInitialized = true;
    }

    /**
     * Returns one trust manager for each type of trust material.
     */
    @Override
    protected TrustManager[] engineGetTrustManagers() {
        if (!isInitialized) {
            throw new IllegalStateException(
                        "TrustManagerFactoryImpl is not initialized");
        }
        return new TrustManager[] { trustManager };
    }

    public static final class SimpleFactory extends TrustManagerFactoryImpl {
        @Override
        X509TrustManager getInstance(
                Collection<X509Certificate> trustedCerts) {
            return new X509TrustManagerImpl(
                    Validator.TYPE_SIMPLE, trustedCerts);
        }

        @Override
        X509TrustManager getInstance(ManagerFactoryParameters spec)
                throws InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException
                ("SunX509 TrustManagerFactory does not use "
                + "ManagerFactoryParameters");
        }
    }

    public static final class PKIXFactory extends TrustManagerFactoryImpl {
        @Override
        X509TrustManager getInstance(
                Collection<X509Certificate> trustedCerts) {
            return new X509TrustManagerImpl(Validator.TYPE_PKIX, trustedCerts);
        }

        @Override
        X509TrustManager getInstance(ManagerFactoryParameters spec)
                throws InvalidAlgorithmParameterException {
            if (!(spec instanceof CertPathTrustManagerParameters)) {
                throw new InvalidAlgorithmParameterException
                    ("Parameters must be CertPathTrustManagerParameters");
            }
            CertPathParameters params =
                ((CertPathTrustManagerParameters)spec).getParameters();
            if (!(params instanceof PKIXBuilderParameters pkixParams)) {
                throw new InvalidAlgorithmParameterException
                    ("Encapsulated parameters must be PKIXBuilderParameters");
            }
            return new X509TrustManagerImpl(Validator.TYPE_PKIX, pkixParams);
        }
    }
}
