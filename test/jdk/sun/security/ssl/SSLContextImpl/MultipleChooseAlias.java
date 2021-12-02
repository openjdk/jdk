/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/*
 * @test
 * @bug 8262186
 * @summary Callback semantics of the method X509KeyManager.chooseClientAlias(...)
 * @library /javax/net/ssl/templates
 * @modules java.base/sun.security.ssl:+open
 *          java.base/javax.net.ssl:+open
 * @run main/othervm MultipleChooseAlias PKIX
 * @run main/othervm MultipleChooseAlias SunX509
 */
public class MultipleChooseAlias extends SSLSocketTemplate {

    static volatile int numOfCalls = 0;
    static String kmfAlgorithm = null;

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        socket.setNeedClientAuth(true);
    }

    @Override
    protected ContextParameters getClientContextParameters() {
        return new ContextParameters("TLS", "PKIX", "Mine");
    }

    public static void main(String[] args) throws Exception {
        kmfAlgorithm = args[0];
        Security.addProvider(new MyProvider());
        try {
            new MultipleChooseAlias().run();
        } catch (Exception e) {
            // expected
        }
        if (numOfCalls != 1) {
            throw new RuntimeException("Too many times " + numOfCalls);
        }
    }

    static class MyProvider extends Provider {
        public MyProvider() {
            super("Mine", "1", "many many things");
            put("KeyManagerFactory.Mine", "MultipleChooseAlias$MyKMF");
        }
    }

    // This KeyManagerFactory impl returns key managers
    // wrapped in MyKM
    public static class MyKMF extends KeyManagerFactorySpi {
        KeyManagerFactory fac;

        public MyKMF() {
            try {
                fac = KeyManagerFactory.getInstance(kmfAlgorithm);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        protected void engineInit(KeyStore ks, char[] password)
                throws KeyStoreException, NoSuchAlgorithmException,
                UnrecoverableKeyException {
            fac.init(ks, password);
        }

        @Override
        protected void engineInit(ManagerFactoryParameters spec)
                throws InvalidAlgorithmParameterException {
            fac.init(spec);
        }

        @Override
        protected KeyManager[] engineGetKeyManagers() {
            KeyManager[] result = fac.getKeyManagers();
            for (int i = 0; i < result.length; i++) {
                result[i] = new MyKM((X509KeyManager)result[i]);
            }
            return result;
        }
    }

    // This KeyManager remembers how many times  chooseClientAlias is called.
    static class MyKM implements X509KeyManager {

        X509KeyManager km;

        MyKM(X509KeyManager km) {
            this.km = km;
        }

        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return km.getClientAliases(keyType, issuers);
        }

        public String chooseClientAlias(String[] keyType, Principal[] issuers,
                Socket socket) {
            System.out.println("chooseClientAlias called on "
                    + Arrays.toString(keyType));
            numOfCalls++;
            return null; // so it will try all key types and finally fails
        }

        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return getServerAliases(keyType, issuers);
        }

        public String chooseServerAlias(String keyType, Principal[] issuers,
                Socket socket) {
            return km.chooseServerAlias(keyType, issuers, socket);
        }

        public X509Certificate[] getCertificateChain(String alias) {
            return km.getCertificateChain(alias);
        }

        public PrivateKey getPrivateKey(String alias) {
            return km.getPrivateKey(alias);
        }
    }
}
