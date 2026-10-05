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

package jdk.jfr.event.security;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.security.TestTLSHandshake;
import jdk.test.lib.security.SecurityUtils;

/*
 * @test
 * @bug 8148188 8301626
 * @summary Enhance the security libraries to record events of interest
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.security.TestTLSHandshakeEvent
 */
public class TestTLSHandshakeEvent {
    record TLSConfig(String protocol, String cipherSuite, String namedGroup,
                     long certId) {};

    private static final List<TLSConfig> CONFIGS = List.of(
        new TLSConfig("TLSv1.3", "TLS_AES_256_GCM_SHA384", "X25519MLKEM768",
                      3237675498L),
        new TLSConfig("TLSv1.3", "TLS_CHACHA20_POLY1305_SHA256",
                      "X25519MLKEM768", 3237675498L),
        new TLSConfig("TLSv1.2", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                      "secp256r1", 3237675498L),
        new TLSConfig("TLSv1.2", "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
                      "ffdhe2048", 3188175476L),
        new TLSConfig("TLSv1.2", "TLS_RSA_WITH_AES_256_CBC_SHA",
                      "N/A", 3010289526L)
    );

    public static void main(String[] args) throws Exception {
        // re-enable TLS_RSA suites as one test depends on it
        SecurityUtils.removeFromDisabledTlsAlgs("TLS_RSA_*");
        for (TLSConfig config : CONFIGS) {
            System.out.println(config);
            try (Recording recording = new Recording()) {
                recording.enable(EventNames.TLSHandshake).withStackTrace();
                recording.start();
                TestTLSHandshake handshake = new TestTLSHandshake();
                handshake.protocolVersion = config.protocol();
                handshake.cipherSuite = config.cipherSuite();
                handshake.namedGroup = config.namedGroup();
                handshake.run();
                recording.stop();

                List<RecordedEvent> events = Events.fromRecording(recording);
                Events.hasEvents(events);
                assertEvent(events, handshake, config);
            }
        }
    }

    private static void assertEvent(List<RecordedEvent> events,
            TestTLSHandshake handshake, TLSConfig config) throws Exception {
        System.out.println(events);
        for (RecordedEvent e : events) {
            if (handshake.peerHost.equals(e.getString("peerHost"))) {
                Events.assertField(e, "peerPort").equal(handshake.peerPort);
                Events.assertField(e, "protocolVersion").equal(handshake.protocolVersion);
                Events.assertField(e, "certificateId").equal(config.certId());
                Events.assertField(e, "cipherSuite").equal(handshake.cipherSuite);
                Events.assertField(e, "namedGroup").equal(handshake.namedGroup);
                var method = e.getStackTrace().getFrames().get(0).getMethod();
                if (method.getName().equals("recordEvent")) {
                    throw new Exception("Didn't expected recordEvent as top frame");
                }
                return;
            }
        }
        System.out.println(events);
        throw new Exception("Could not find event with hostname: " + handshake.peerHost);
    }
}
