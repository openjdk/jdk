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

import java.util.List;
import java.util.Collections;

import java.security.*;
import java.security.KeyStore.*;

import javax.net.ssl.*;

abstract class KeyManagerFactoryImpl extends KeyManagerFactorySpi {

    X509ExtendedKeyManager keyManager;
    boolean isInitialized;

    KeyManagerFactoryImpl() {
        // empty
    }

    /**
     * Returns one key manager for each type of key material.
     */
    protected KeyManager[] engineGetKeyManagers() {
        if (!isInitialized) {
            throw new IllegalStateException(
                        "KeyManagerFactoryImpl is not initialized");
        }
        return new KeyManager[] { keyManager };
    }

    // Factory for the SunX509 keymanager
    public static final class SunX509 extends KeyManagerFactoryImpl {

        protected void engineInit(KeyStore ks, char[] password) throws
                KeyStoreException, NoSuchAlgorithmException,
                UnrecoverableKeyException {
            if ((ks != null) && SunJSSE.isFIPS()) {
                if (ks.getProvider() != SunJSSE.cryptoProvider) {
                    throw new KeyStoreException("FIPS mode: KeyStore must be "
                        + "from provider " + SunJSSE.cryptoProvider.getName());
                }
            }
            keyManager = new SunX509KeyManagerImpl(ks, password);
            isInitialized = true;
        }

        protected void engineInit(ManagerFactoryParameters spec) throws
                InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException(
                "SunX509KeyManager does not use ManagerFactoryParameters");
        }

    }

    // Factory for the X509 keymanager
    public static final class X509 extends KeyManagerFactoryImpl {

        protected void engineInit(KeyStore ks, char[] password) throws
                KeyStoreException, NoSuchAlgorithmException,
                UnrecoverableKeyException {
            if (ks == null) {
                keyManager = new X509KeyManagerImpl(
                        Collections.<Builder>emptyList());
            } else {
                if (SunJSSE.isFIPS() && (ks.getProvider() != SunJSSE.cryptoProvider)) {
                    throw new KeyStoreException("FIPS mode: KeyStore must be "
                        + "from provider " + SunJSSE.cryptoProvider.getName());
                }
                try {
                    Builder builder = Builder.newInstance(ks,
                        new PasswordProtection(password));
                    keyManager = new X509KeyManagerImpl(builder);
                } catch (RuntimeException e) {
                    throw new KeyStoreException("initialization failed", e);
                }
            }
            isInitialized = true;
        }

        protected void engineInit(ManagerFactoryParameters params) throws
                InvalidAlgorithmParameterException {
            if (params instanceof KeyStoreBuilderParameters == false) {
                throw new InvalidAlgorithmParameterException(
                "Parameters must be instance of KeyStoreBuilderParameters");
            }
            if (SunJSSE.isFIPS()) {
                // XXX should be fixed
                throw new InvalidAlgorithmParameterException
                    ("FIPS mode: KeyStoreBuilderParameters not supported");
            }
            List<Builder> builders =
                ((KeyStoreBuilderParameters)params).getParameters();
            keyManager = new X509KeyManagerImpl(builders);
            isInitialized = true;
        }

    }

}
