/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @test
 * @bug 8395727
 * @summary Certificates added to the default trust store must be visible
 *          after the key store was reloaded through getTrustedKeyStore()
 * @library /test/lib
 * @run main/othervm StaleTrustedCerts
 */

import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.Security;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.SecurityTools.keytool;

public class StaleTrustedCerts {

    private static final String STORE = "trust.p12";

    public static class DelegatingTMF extends TrustManagerFactorySpi {
        private final TrustManagerFactory tmf;

        public DelegatingTMF() throws Exception {
            tmf = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
        }

        @Override
        protected void engineInit(KeyStore ks) throws KeyStoreException {
            tmf.init(ks);
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec)
                throws InvalidAlgorithmParameterException {
            tmf.init(spec);
        }

        @Override
        protected TrustManager[] engineGetTrustManagers() {
            return tmf.getTrustManagers();
        }
    }

    public static void main(String[] args) throws Exception {
        addKey("one");

        System.setProperty("javax.net.ssl.trustStore", STORE);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");

        X509TrustManager first = defaultTrustManager();
        assertEquals(1, first.getAcceptedIssuers().length);

        FileTime before = Files.getLastModifiedTime(Path.of(STORE));
        addKey("two");
        Files.setLastModifiedTime(Path.of(STORE),
                FileTime.fromMillis(before.toMillis() + 2000));

        Security.insertProviderAt(new Provider("DelegatingTMFProvider", "1", "") {
            {
                putService(new Service(this, "TrustManagerFactory", "PKIX",
                        DelegatingTMF.class.getName(), null, null));
            }
        }, 1);
        SSLContext.getDefault();
        Security.removeProvider("DelegatingTMFProvider");

        assertEquals(2, defaultTrustManager().getAcceptedIssuers().length);
        Reference.reachabilityFence(first);
    }

    private static void addKey(String alias) throws Exception {
        keytool("-genkeypair -alias " + alias + " -dname CN=" + alias
                + " -keyalg EC -keystore " + STORE + " -storetype PKCS12"
                + " -storepass changeit").shouldHaveExitValue(0);
    }

    private static X509TrustManager defaultTrustManager() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
        tmf.init((KeyStore) null);
        return (X509TrustManager) tmf.getTrustManagers()[0];
    }
}
