/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import java.security.cert.CertPathBuilder;
import java.util.Collections;
import java.util.List;
import java.util.function.*;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.EventNames;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

/*
 * @test
 * @bug 8254711
 * @summary Add JFR events for security crypto algorithms
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.events
 * @run main/othervm jdk.jfr.event.security.TestSecurityProviderServiceEvent
 */
public class TestSecurityProviderServiceEvent {

    public static void main(String[] args) throws Exception {
        testAlg(cipherFunc, "AES", "SunJCE",
                "SunEC", "Cipher", 1, Collections.emptyList(),
                javax.crypto.Cipher.class.getName(), "getInstance");
        testAlg(signatureFunc, "SHA256withRSA", "SunRsaSign",
                "SunEC", "Signature", 2, List.of("MessageDigest"),
                "sun.security.jca.GetInstance", "getService");
        testAlg(messageDigestFunc, "SHA-512", "SUN",
                "SunEC", "MessageDigest", 1, Collections.emptyList(),
                "sun.security.jca.GetInstance", "getService");
        testAlg(keystoreFunc, "PKCS12", "SUN",
                "SunEC", "KeyStore", 1, Collections.emptyList(),
                "sun.security.jca.GetInstance", "getService");
        testAlg(certPathBuilderFunc, "PKIX", "SUN",
                "SunEC", "CertPathBuilder", 2, List.of("CertificateFactory"),
                "sun.security.jca.GetInstance", "getService");
    }

    private static void testAlg(BiFunction<String, String, Provider> bif, String alg,
                String workingProv, String brokenProv, String algType,
                                int expected, List<String> other,
                                String clazz, String method) throws Exception {
        // bootstrap security Provider services
        Provider p =  bif.apply(alg, workingProv);

        try (Recording recording = new Recording()) {
            recording.enable(EventNames.SecurityProviderService).withStackTrace();
            recording.start();
            p = bif.apply(alg, workingProv);
            bif.apply(alg, brokenProv);
            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);
            Asserts.assertEquals(events.size(), expected, "Incorrect number of events");
            assertEvent(events, algType, alg, p.getName(), other, clazz, method);
        }
    }

    private static BiFunction<String, String, Provider> cipherFunc = (s1, p1 ) -> {
        Cipher c;
        try {
            c = Cipher.getInstance(s1, p1);
            return c.getProvider();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException e) {
            // expected
        }
        return null;
    };

    private static BiFunction<String, String, Provider> signatureFunc = (s1, p1 ) -> {
        Signature s;
        try {
            s = Signature.getInstance(s1, p1);
            return s.getProvider();
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            // expected
        }
        return null;
    };

    private static BiFunction<String, String, Provider> messageDigestFunc = (s1, p1 ) -> {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance(s1, p1);
            return md.getProvider();
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            // expected
        }
        return null;
    };

    private static BiFunction<String, String, Provider> keystoreFunc = (s1, p1 ) -> {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance(s1, p1);
            return ks.getProvider();
        } catch (NoSuchProviderException | KeyStoreException e) {
            // expected
        }
        return null;
    };

    private static BiFunction<String, String, Provider> certPathBuilderFunc = (s1, p1 ) -> {
        CertPathBuilder cps;
        try {
            cps = CertPathBuilder.getInstance(s1, p1);
            return cps.getProvider();
        } catch (NoSuchProviderException | NoSuchAlgorithmException e) {
            // expected
        }
        return null;
    };

    private static void assertEvent(List<RecordedEvent> events, String type,
            String alg, String workingProv, List<String> other, String clazz,
            String method) {
        boolean secondaryEventOK = other.isEmpty() ? true : false;
        for (RecordedEvent e : events) {
            if (other.contains(e.getValue("type"))) {
                // secondary operation in service stack while constructing this request
                secondaryEventOK = true;
                continue;
            }
            Events.assertField(e, "provider").equal(workingProv);
            Events.assertField(e, "type").equal(type);
            Events.assertField(e, "algorithm").equal(alg);
            Events.assertTopFrame(e, clazz, method);
        }
        if (!secondaryEventOK) {
            throw new RuntimeException("Secondary events missing");
        }
    }
}
