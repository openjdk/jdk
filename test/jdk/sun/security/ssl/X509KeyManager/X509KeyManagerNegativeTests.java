/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Enumeration;

/*
 * @test
 * @bug 8369995
 * @summary X509KeyManagerImpl negative tests causing exceptions
 * @library /test/lib
 * @run junit/othervm X509KeyManagerNegativeTests
 */
public class X509KeyManagerNegativeTests {
    private static X509KeyManager exceptionThrowingKM;

    @BeforeAll
    public static void beforeAll() throws Exception {

        // initialising exception throwing ks
        // cleaned up after the tests are complete
        final KeyManagerFactory exceptionThrowingKMF =
                KeyManagerFactory.getInstance("NewSunX509");

        // adding dummy provider
        Security.addProvider(new MyCustomKSProvider());
        final KeyStore exceptionThrowingKS =
                KeyStore.getInstance("MyExceptionKS");
        exceptionThrowingKS.load(null, null);

        exceptionThrowingKMF
                .init(exceptionThrowingKS, null);
        exceptionThrowingKM =
                (X509KeyManager) exceptionThrowingKMF.getKeyManagers()[0];
    }

    @AfterAll
    public static void cleanup() {
        // remove custom provider
        Security.removeProvider("MyCustomKSProvider");
    }

    @Test
    public void ksExceptionTest() {
        Asserts.assertThrows(ConcurrentModificationException.class,
                () -> exceptionThrowingKM.getCertificateChain("RSA.0.0"));
        Asserts.assertThrows(ConcurrentModificationException.class,
                () -> exceptionThrowingKM.getPrivateKey("RSA.0.0"));
    }

    public static class MyCustomKSProvider extends Provider {
        public MyCustomKSProvider() {
            super("MyCustomKSProvider",
                    "1.0",
                    "My Custom KS Provider");
            put("KeyStore.MyExceptionKS", MyExceptionKS.class.getName());
        }
    }

    public static class MyExceptionKS extends KeyStoreSpi {

        @Override
        public KeyStore.Entry engineGetEntry(String alias,
                                             KeyStore.ProtectionParameter param)
        {
            throw new ConcurrentModificationException("getEntry exception");
        }

        @Override
        public Key engineGetKey(String alias, char[] password) {
            return null;
        }

        @Override
        public Certificate[] engineGetCertificateChain(String alias) {
            return null;
        }

        @Override
        public Certificate engineGetCertificate(String alias) {
            return null;
        }

        @Override
        public Date engineGetCreationDate(String alias) {
            return null;
        }

        @Override
        public Enumeration<String> engineAliases() {
            return null;
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return false;
        }

        @Override
        public int engineSize() {
            return 0;
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            return false;
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            return false;
        }

        @Override
        public String engineGetCertificateAlias(Certificate cert) {
            return null;
        }

        @Override
        public void engineStore(OutputStream stream, char[] password) {
        }

        @Override
        public void engineLoad(InputStream stream, char[] password) {
        }

        @Override
        public void engineSetKeyEntry(String alias,
                                      Key key,
                                      char[] password,
                                      Certificate[] chain) {
        }

        @Override
        public void engineSetKeyEntry(String alias,
                                      byte[] key,
                                      Certificate[] chain) {
        }

        @Override
        public void engineSetCertificateEntry(String alias,
                                              Certificate cert) {
        }

        @Override
        public void engineDeleteEntry(String alias) {
        }
    }
}
