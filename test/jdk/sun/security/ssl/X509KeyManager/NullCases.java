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
 * @run main NullCases
 * @run main/othervm -Djavax.net.debug=ssl:keymanager NullCases debug
 */

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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

    public static void main(String[] args) throws Exception {
        KeyManagerFactory kmf;
        char[] password = {' '};

        // check for bug 6302126
        kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init((KeyStore) null, password);
        X509KeyManager km = (X509KeyManager) kmf.getKeyManagers()[0];

        KeyManagerFactory kmf2 = KeyManagerFactory.getInstance("NewSunX509");
        final KeyStore ks = createNewKeystore();
        kmf2.init(ks, null);
        X509KeyManager km2 = (X509KeyManager) kmf2.getKeyManagers()[0];


        // check for 6302321
        X509Certificate[] certs = km.getCertificateChain("doesnotexist");
        PrivateKey priv = km.getPrivateKey("doesnotexist");
        if (certs != null || priv != null) {
            throw new Exception("Should return null if the alias can't be found");
        }

        // check for 6302271
        String[] clis = km.getClientAliases("doesnotexist", null);
        if (clis != null && clis.length == 0) {
            throw new Exception("Should return null instead of empty array");
        }
        String[] srvs = km.getServerAliases("doesnotexist", null);
        if (srvs != null && srvs.length == 0) {
            throw new Exception("Should return null instead of empty array");
        }


        // Exceptions check for 8369995

        // recording logs to the output stream
        final PrintStream intialErrStream = System.err;
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final PrintStream newErrStream = new PrintStream(outputStream);
        System.setErr(newErrStream);

        certs = km.getCertificateChain("RSA.not.exist");
        priv = km.getPrivateKey("RSA.not.exist");
        if (certs != null || priv != null) {
            System.setErr(intialErrStream);
            System.err.println(outputStream);
            throw new Exception("Should return null if the alias can't be found");
        }

        certs = km2.getCertificateChain("RSA.0.1");
        priv = km2.getPrivateKey("RSA.0.1");
        if (certs != null || priv != null) {
            System.setErr(intialErrStream);
            System.err.println(outputStream);
            throw new Exception("Should return null if the alias can't be found");
        }

        certs = km2.getCertificateChain("..1");
        priv = km2.getPrivateKey("..1");
        if (certs != null || priv != null) {
            System.setErr(intialErrStream);
            System.err.println(outputStream);
            throw new Exception("Should return null if the alias can't be found");
        }

        System.setErr(intialErrStream);

        if (args.length > 0 && args[0].equals("debug")
            && !outputStream.toString().contains("KeyMgr: exception triggered:")) {
            throw new Exception("No log triggered");
        }

        // check for 6302304
        km.getServerAliases(null, null);
        km.getClientAliases(null, null);
        km.getCertificateChain(null);
        km.getPrivateKey(null);
        km.chooseServerAlias(null, null, null);
        km.chooseClientAlias(null, null, null);
    }

    private static KeyStore createNewKeystore() throws Exception {
        final SecureRandom secureRandom = new SecureRandom();
        final KeyPair keyPair = generateRSAKeyPair(secureRandom);

        final X509Certificate originServerCert = generateCert(keyPair, secureRandom,
                "subject");

        return generateKeyStore(keyPair.getPrivate(),
                new Certificate[]{originServerCert});
    }
}
