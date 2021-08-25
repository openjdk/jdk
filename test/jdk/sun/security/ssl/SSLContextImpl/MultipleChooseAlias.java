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

import sun.security.ssl.SSLContextImpl;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/*
 * @test
 * @bug 8262186
 * @summary Callback semantics of the method X509KeyManager.chooseClientAlias(...)
 * @library /javax/net/ssl/templates
 * @modules java.base/sun.security.ssl:+open
 *          java.base/javax.net.ssl:+open
 * @run main/othervm MultipleChooseAlias
 */
public class MultipleChooseAlias extends SSLSocketTemplate {

    static volatile int numOfCalls = 0;
    
    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        socket.setNeedClientAuth(true);
    }

    @Override
    protected SSLContext createClientSSLContext() throws Exception {
        SSLContext ctxt = super.createClientSSLContext();
        var f = SSLContext.class.getDeclaredField("contextSpi");
        f.setAccessible(true);
        SSLContextSpi spi = (SSLContextSpi) f.get(ctxt);
        var m1 = SSLContextImpl.class.getDeclaredMethod("getX509KeyManager");
        m1.setAccessible(true);
        var m2 = SSLContextImpl.class.getDeclaredMethod("getX509TrustManager");
        m2.setAccessible(true);
        SSLContext newCtxt = SSLContext.getInstance("TLS");
        newCtxt.init(
                new KeyManager[] {new myKM((X509KeyManager) m1.invoke(spi))},
                new TrustManager[] {(X509TrustManager) m2.invoke(spi)},
                null);
        return newCtxt;
    }

    public static void main(String[] args) throws Exception {
        try {
            new MultipleChooseAlias().run();
        } catch (Exception e) {
            // expected
        }
        if (numOfCalls != 1) {
            throw new RuntimeException("Too many times " + numOfCalls);
        }
    }


    static class myKM implements X509KeyManager {

        X509KeyManager km;

        myKM(X509KeyManager km) {
            this.km = km;
        }

        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return km.getClientAliases(keyType, issuers);
        }

        public String chooseClientAlias(String[] keyType, Principal[] issuers,
                Socket socket) {
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

