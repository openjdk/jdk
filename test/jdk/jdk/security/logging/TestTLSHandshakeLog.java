/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.security.logging;

import jdk.test.lib.security.TestTLSHandshake;

/*
 * @test
 * @bug 8148188 8301626
 * @summary Enhance the security libraries to record events of interest
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.security.logging.TestTLSHandshakeLog LOGGING_ENABLED
 * @run main/othervm jdk.security.logging.TestTLSHandshakeLog LOGGING_DISABLED
 */
public class TestTLSHandshakeLog {
    record TLSConfig(String protocol, String cipherSuite, String namedGroup) {};

    private static TLSConfig CONFIG =
        new TLSConfig("TLSv1.2",
                      "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                      "secp256r1");

    public static void main(String[] args) throws Exception {
        LogJvm l = new LogJvm(TLSHandshake.class, args);
        l.addExpected("FINE: X509Certificate: Alg:SHA256withRSA, Serial:" + TestTLSHandshake.CERT_SERIAL);
        l.addExpected("Subject:CN=Regression Test");
        l.addExpected("Key type:EC, Length:256");
        l.addExpected("FINE: ValidationChain: " +
                TestTLSHandshake.ANCHOR_CERT_ID +
                ", " + TestTLSHandshake.CERT_ID);
        l.addExpected("SunJSSE Test Serivce");
        l.addExpected("TLSHandshake:");
        l.addExpected(CONFIG.protocol());
        l.addExpected(CONFIG.cipherSuite());
        l.addExpected(CONFIG.namedGroup());
        l.addExpected(Long.toString(TestTLSHandshake.CERT_ID));
        l.testExpected();
    }

    public static class TLSHandshake {
        public static void main(String[] args) throws Exception {
            TestTLSHandshake handshake = new TestTLSHandshake();
            handshake.protocolVersion = CONFIG.protocol();
            handshake.cipherSuite = CONFIG.cipherSuite();
            handshake.namedGroup = CONFIG.namedGroup();
            handshake.run();
        }
    }
}
