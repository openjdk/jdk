/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6302126 6302321 6302271 6302304 8369995
 * @summary KeyManagerFactory.init method throws unspecified exception
 *     for NewSunX509 algorithm
 *     X509KeyManager implementation for NewSunX509 throws unspecified
 *     ProviderException
 *     X509KeyManager implementation for NewSunX509 algorithm returns empty
 *     arrays instead of null
 *     X509KeyManager implementation for NewSunX509 throws unspecified
 *     NullPointerException
 *     Extra logging and/or propagate errors in X509KeyManagerImpl
 *
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @modules java.base/sun.security.x509
 *
 * @run junit NullCases
 */

import jdk.test.lib.Asserts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import static jdk.httpclient.test.lib.common.DynamicKeyStoreUtil.generateCert;
import static jdk.httpclient.test.lib.common.DynamicKeyStoreUtil.generateKeyStore;
import static jdk.httpclient.test.lib.common.DynamicKeyStoreUtil.generateRSAKeyPair;


public class NullCases {

    private static KeyManagerFactory kmf;
    private static X509KeyManager km;

    @BeforeAll
    public static void beforeAll() throws Exception {
        kmf = KeyManagerFactory.getInstance("NewSunX509");

        // creating a new keystore
        final SecureRandom secureRandom = new SecureRandom();
        final KeyPair keyPair = generateRSAKeyPair(secureRandom);
        final X509Certificate originServerCert =
                generateCert(keyPair, secureRandom, "subject");
        final KeyStore ks = generateKeyStore(keyPair.getPrivate(),
                new Certificate[]{originServerCert});

        kmf.init(ks, null);
        km = (X509KeyManager) kmf.getKeyManagers()[0];
    }

    private X509KeyManager generateNullKm() throws Exception {
        char[] password = {' '};
        kmf.init((KeyStore) null, password);
        return (X509KeyManager) kmf.getKeyManagers()[0];
    }

    @Test
    public void JDK6302126Test() throws Exception {
        // check for bug 6302126

        generateNullKm();
    }

    @Test
    public void JDK6302304Test() throws Exception {
        // check for bug 6302304

        final X509KeyManager km = generateNullKm();

        final String[] serverAliases =
                km.getServerAliases(null, null);
        Asserts.assertNull(serverAliases,
                "Should return null if server alias not found");
        final String[] clientAliases =
                km.getClientAliases(null, null);
        Asserts.assertNull(clientAliases,
                "Should return null if client alias not found");

        final X509Certificate[] certs =
                km.getCertificateChain(null);
        final PrivateKey priv =
                km.getPrivateKey(null);
        Asserts.assertNull(certs,
                "Should return null if the alias can't be found");
        Asserts.assertNull(priv,
                "Should return null if the alias can't be found");

        final String serverAlias =
                km.chooseServerAlias(null, null, null);
        Asserts.assertNull(serverAlias,
                "Should return null if the alias can't be chosen");
        final String clientAlias =
                km.chooseClientAlias(null, null, null);
        Asserts.assertNull(clientAlias,
                "Should return null if the alias can't be chosen");
    }

    @Test
    public void JDK6302321Test() {
        // check for bug 6302321

        final X509Certificate[] certs =
                km.getCertificateChain("doesnotexist");
        final PrivateKey priv = km.getPrivateKey("doesnotexist");
        Asserts.assertNull(certs,
                "Should return null if the alias can't be found");
        Asserts.assertNull(priv,
                "Should return null if the alias can't be found");
    }

    @Test
    public void JDK6302271Test() {
        // check for 6302271

        final String[] clis =
                km.getClientAliases("doesnotexist", null);
        Asserts.assertFalse((clis != null && clis.length == 0),
                "Should return null instead of empty array");

        final String[] srvs =
                km.getServerAliases("doesnotexist", null);
        Asserts.assertFalse((srvs != null && srvs.length == 0),
                "Should return null instead of empty array");
    }

    /**
     * The following tests are testing JDK-8369995
     */

    @Test
    public void incompleteChainAndKeyTest() {
        final X509Certificate[] certs = km.getCertificateChain("1.1");
        final PrivateKey priv = km.getPrivateKey("1.1");

        Asserts.assertNull(certs,
                "Should return null if the alias is incomplete");
        Asserts.assertNull(priv,
                "Should return null if the alias is incomplete");
    }

    @Test
    public void nonexistentBuilderTest() {
        final X509Certificate[] certs = km.getCertificateChain("RSA.1.1");
        final PrivateKey priv = km.getPrivateKey("RSA.1.1");

        Asserts.assertNull(certs,
                "Should return null if builder doesn't exist");
        Asserts.assertNull(priv,
                "Should return null if builder doesn't exist");
    }

    @Test
    public void nonexistentKSTest() {
        final X509Certificate[] certs = km.getCertificateChain("RSA.0.1");
        final PrivateKey priv = km.getPrivateKey("RSA.0.1");

        Asserts.assertNull(certs,
                "Should return null if KS doesn't exist");
        Asserts.assertNull(priv,
                "Should return null if KS doesn't exist");
    }

    @Test
    public void wrongNumberFormatTest() {
        final X509Certificate[] certs =
                km.getCertificateChain("RSA.not.exist");
        final PrivateKey priv = km.getPrivateKey("RSA.not.exist");

        Asserts.assertNull(certs,
                "Should return null if number format is wrong in alias");
        Asserts.assertNull(priv,
                "Should return null if number format is wrong in alias");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1..1", "1..",".1.", "..1", ".9.123456789"})
    public void invalidAliasTest(final String alias) {
        final X509Certificate[] certs = km.getCertificateChain(alias);
        final PrivateKey priv = km.getPrivateKey(alias);

        Asserts.assertNull(certs,
                String.format(
                        "Should return null if the alias is invalid <%s>",
                        alias));
        Asserts.assertNull(priv,
                String.format(
                        "Should return null if the alias is invalid <%s>",
                        alias));
    }
}
