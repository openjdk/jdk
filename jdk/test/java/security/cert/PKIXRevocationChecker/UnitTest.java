/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6854712 7171570
 * @summary Basic unit test for PKIXRevocationChecker
 */

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathChecker;
import java.security.cert.CertPathValidator;
import java.security.cert.Extension;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.PKIXRevocationChecker.Option;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UnitTest {

    public static void main(String[] args) throws Exception {

        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
        CertPathChecker cpc = cpv.getRevocationChecker();
        PKIXRevocationChecker prc = (PKIXRevocationChecker)cpc;

        System.out.println("Testing that get methods return null or " +
                           "empty lists/sets/maps");
        requireNull(prc.getOCSPResponder(), "getOCSPResponder()");
        requireNull(prc.getOCSPResponderCert(), "getOCSPResponderCert()");
        requireEmpty(prc.getOCSPExtensions(), "getOCSPExtensions()");
        requireEmpty(prc.getOCSPResponses(), "getOCSPResponses()");
        requireEmpty(prc.getOptions(), "getOptions()");

        System.out.println("Testing that get methods return same parameters " +
                           "that are passed to set methods");
        URI uri = new URI("http://localhost");
        prc.setOCSPResponder(uri);
        requireEquals(uri, prc.getOCSPResponder(), "getOCSPResponder()");

        X509Certificate cert = getCert();
        prc.setOCSPResponderCert(cert);
        requireEquals(cert, prc.getOCSPResponderCert(),
                      "getOCSPResponderCert()");

        List<Extension> exts = new ArrayList<>();
        for (String oid : cert.getNonCriticalExtensionOIDs()) {
            System.out.println(oid);
            exts.add(new ExtensionImpl(oid,
                                       cert.getExtensionValue(oid), false));
        }
        prc.setOCSPExtensions(exts);
        requireEquals(exts, prc.getOCSPExtensions(), "getOCSPExtensions()");

        Set<Option> options = EnumSet.of(Option.ONLY_END_ENTITY);
        prc.setOptions(options);
        requireEquals(options, prc.getOptions(), "getOptions()");

        System.out.println("Testing that parameters are re-initialized to " +
                           "default values if null is passed to set methods");
        prc.setOCSPResponder(null);
        requireNull(prc.getOCSPResponder(), "getOCSPResponder()");
        prc.setOCSPResponderCert(null);
        requireNull(prc.getOCSPResponderCert(), "getOCSPResponderCert()");
        prc.setOCSPExtensions(null);
        requireEmpty(prc.getOCSPExtensions(), "getOCSPExtensions()");
        prc.setOCSPResponses(null);
        requireEmpty(prc.getOCSPResponses(), "getOCSPResponses()");
        prc.setOptions(null);
        requireEmpty(prc.getOptions(), "getOptions()");

        System.out.println("Testing that getRevocationChecker returns new " +
                           "instance each time");
        CertPathChecker first = cpv.getRevocationChecker();
        CertPathChecker second = cpv.getRevocationChecker();
        if (first == second) {
            throw new Exception("FAILED: CertPathCheckers not new instances");
        }
        CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
        first = cpb.getRevocationChecker();
        second = cpb.getRevocationChecker();
        if (first == second) {
            throw new Exception("FAILED: CertPathCheckers not new instances");
        }
    }

    static void requireNull(Object o, String msg) throws Exception {
        if (o != null) {
            throw new Exception("FAILED: " + msg + " must return null");
        }
    }

    static void requireEmpty(Map<?,?> m, String msg) throws Exception {
        if (!m.isEmpty()) {
            throw new Exception("FAILED: " + msg + " must return an empty map");
        }
    }

    static void requireEmpty(List<?> l, String msg) throws Exception {
        if (!l.isEmpty()) {
            throw new Exception("FAILED: " + msg +" must return an empty list");
        }
    }

    static void requireEmpty(Set<?> s, String msg) throws Exception {
        if (!s.isEmpty()) {
            throw new Exception("FAILED: " + msg + " must return an empty set");
        }
    }

    static void requireEquals(Object a, Object b, String msg) throws Exception {
        if (!a.equals(b)) {
            throw new Exception("FAILED: " + msg + " does not return the " +
                                "same object that was set");
        }
    }

    static X509Certificate getCert() throws Exception {
        String b64 =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIBLTCB2KADAgECAgEDMA0GCSqGSIb3DQEBBAUAMA0xCzAJBgNVBAMTAkNBMB4X\n" +
        "DTAyMTEwNzExNTcwM1oXDTIyMTEwNzExNTcwM1owFTETMBEGA1UEAxMKRW5kIEVu\n" +
        "dGl0eTBcMA0GCSqGSIb3DQEBAQUAA0sAMEgCQQDVBDfF+uBr5s5jzzDs1njKlZNt\n" +
        "h8hHzEt3ASh67Peos+QrDzgpUyFXT6fdW2h7iPf0ifjM8eW2xa+3EnPjjU5jAgMB\n" +
        "AAGjGzAZMBcGA1UdIAQQMA4wBgYEVR0gADAEBgIqADANBgkqhkiG9w0BAQQFAANB\n" +
        "AFo//WOboCNOCcA1fvcWW9oc4MvV8ZPvFIAbyEbgyFd4id5lGDTRbRPvvNZRvdsN\n" +
        "NM2gXYr+f87NHIXc9EF3pzw=\n" +
        "-----END CERTIFICATE-----";

        InputStream is = new ByteArrayInputStream(b64.getBytes("UTF-8"));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate)cf.generateCertificate(is);
    }

    static class ExtensionImpl implements Extension {
        private final String oid;
        private final byte[] val;
        private final boolean critical;

        ExtensionImpl(String oid, byte[] val, boolean critical) {
            this.oid = oid;
            this.val = val;
            this.critical = critical;
        }

        public void encode(OutputStream out) throws IOException {
            throw new UnsupportedOperationException();
        }

        public String getId() {
            return oid;
        }

        public byte[] getValue() {
            return val.clone();
        }

        public boolean isCritical() {
            return critical;
        }
    }
}
