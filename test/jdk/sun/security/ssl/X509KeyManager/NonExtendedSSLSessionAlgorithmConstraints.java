/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8365953
 * @summary Key manager returns no certificates when handshakeSession is not
 *          an ExtendedSSLSession
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib
 * @run main/othervm NonExtendedSSLSessionAlgorithmConstraints
 */

import static jdk.test.lib.Asserts.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Principal;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;

/*
 * Make sure Key Managers return the certificates when SSLSocket or SSLEngine
 * use an SSLSession which is not extending ExtendedSSLSession.
 */
public class NonExtendedSSLSessionAlgorithmConstraints extends
        AlgorithmConstraintsCheck {

    public static void main(String[] args) throws Exception {
        new NonExtendedSSLSessionAlgorithmConstraints().runTest();
    }

    private void runTest() throws Exception {
        for (String kmAlg : new String[]{"SunX509", "PKIX"}) {

            X509ExtendedKeyManager km =
                    (X509ExtendedKeyManager) getKeyManager(
                            kmAlg, KEY_TYPE, CERT_SIG_ALG);
            var testSocket = new TestHandshakeSessionSSLSocket();
            var testEngine = new TestHandshakeSessionSSLEngine();

            // Test SSLSocket
            assertEquals(CERT_ALIAS, normalizeAlias(km.chooseServerAlias(
                    KEY_TYPE, null, testSocket)));
            assertEquals(CERT_ALIAS, normalizeAlias(km.chooseClientAlias(
                    new String[]{KEY_TYPE}, null, testSocket)));

            // Test SSLEngine
            assertEquals(CERT_ALIAS, normalizeAlias(km.chooseEngineServerAlias(
                    KEY_TYPE, null, testEngine)));
            assertEquals(CERT_ALIAS, normalizeAlias(km.chooseEngineClientAlias(
                    new String[]{KEY_TYPE}, null, testEngine)));
        }
    }

    private static class TestHandshakeSessionSSLSocket extends SSLSocket {

        TestHandshakeSessionSSLSocket() {
        }

        @Override
        public SSLSession getHandshakeSession() {
            return new TestSSLSession();
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public SSLSession getSession() {
            return null;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return null;
        }

        @Override
        public String[] getSupportedProtocols() {
            return null;
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return null;
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
        }

        @Override
        public String[] getEnabledProtocols() {
            return null;
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
        }

        @Override
        public void addHandshakeCompletedListener
                (HandshakeCompletedListener listener) {
        }

        @Override
        public void removeHandshakeCompletedListener
                (HandshakeCompletedListener listener) {
        }

        @Override
        public void startHandshake() throws IOException {
        }

        @Override
        public void setUseClientMode(boolean mode) {
        }

        @Override
        public boolean getUseClientMode() {
            return false;
        }

        @Override
        public void setNeedClientAuth(boolean need) {
        }

        @Override
        public boolean getNeedClientAuth() {
            return false;
        }

        @Override
        public void setWantClientAuth(boolean want) {
        }

        @Override
        public boolean getWantClientAuth() {
            return false;
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
        }

        @Override
        public boolean getEnableSessionCreation() {
            return true;
        }
    }

    private static class TestHandshakeSessionSSLEngine extends SSLEngine {

        @Override
        public SSLSession getHandshakeSession() {
            return new TestSSLSession();
        }

        @Override
        public String[] getEnabledProtocols() {
            return null;
        }

        @Override
        public SSLEngineResult wrap(ByteBuffer[] src, int off, int len,
                ByteBuffer dst) throws SSLException {
            return null;
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer src,
                ByteBuffer[] dst, int off, int len)
                throws SSLException {
            return null;
        }

        @Override
        public Runnable getDelegatedTask() {
            return null;
        }

        @Override
        public void closeInbound() {
        }

        @Override
        public boolean isInboundDone() {
            return false;
        }

        @Override
        public void closeOutbound() {
        }

        @Override
        public boolean isOutboundDone() {
            return false;
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return null;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return null;
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
        }

        @Override
        public String[] getSupportedProtocols() {
            return null;
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
        }

        @Override
        public SSLSession getSession() {
            return null;
        }

        @Override
        public void beginHandshake() {
        }

        @Override
        public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
            return null;
        }

        @Override
        public void setUseClientMode(boolean mode) {
        }

        @Override
        public boolean getUseClientMode() {
            return false;
        }

        public void setNeedClientAuth(boolean need) {
        }

        @Override
        public boolean getNeedClientAuth() {
            return false;
        }

        @Override
        public void setWantClientAuth(boolean need) {
        }

        @Override
        public boolean getWantClientAuth() {
            return false;
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
        }

        @Override
        public boolean getEnableSessionCreation() {
            return false;
        }
    }

    public static class TestSSLSession implements SSLSession {

        TestSSLSession() {
        }

        @Override
        public String getProtocol() {
            return "TLSv1.3";
        }

        @Override
        public byte[] getId() {
            return null;
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return null;
        }

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void putValue(String name, Object value) {
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public void removeValue(String name) {
        }

        @Override
        public String[] getValueNames() {
            return null;
        }

        @Override
        public java.security.cert.Certificate[] getPeerCertificates() {
            return new java.security.cert.Certificate[0];
        }

        @Override
        public java.security.cert.Certificate[] getLocalCertificates() {
            return new java.security.cert.Certificate[0];
        }

        @Override
        public Principal getPeerPrincipal() {
            return null;
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public String getCipherSuite() {
            return null;
        }

        @Override
        public String getPeerHost() {
            return null;
        }

        @Override
        public int getPeerPort() {
            return 0;
        }

        @Override
        public int getPacketBufferSize() {
            return 0;
        }

        @Override
        public int getApplicationBufferSize() {
            return 0;
        }
    }
}
