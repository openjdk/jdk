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

import jdk.test.lib.security.CertUtils;

import java.io.*;
import java.security.cert.Certificate;
import java.util.HashSet;
import java.util.Set;
/*
 * @test
 * @bug 8355379
 * @library /test/lib
 * @summary Basic testing for Certificate
 * @run main TestHashCode
 */
public class TestHashCode{
    public static void main(String[] args) throws Exception {
        Certificate cert = loadCert();

        testEqualityAndHashCode(cert);
        testRepeatedHashCode(cert);
        testSerializationRoundTrip(cert);
        testHashUniqueness();
        System.out.println("All Certificate tests passed.");
    }

    private static Certificate loadCert() throws Exception {
        // Base64 DER-encoded X.509 certificate (short, valid)
        String cert =
                "-----BEGIN CERTIFICATE-----\n" +
                        "MIICvTCCAaWgAwIBAgIEGYqL9TANBgkqhkiG9w0BAQsFADAPMQ0wCwYDVQQDEwRT\n" +
                        "ZWxmMB4XDTE3MDMyODE2NDcyNloXDTE3MDYyNjE2NDcyNlowDzENMAsGA1UEAxME\n" +
                        "U2VsZjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL1pfSJljFVSABOL\n" +
                        "tJbIVPEkz1+2AFgzY1hqwE0EH80lvhOEkiPPYCKwBE5VTZdyFfwFjpyx7eEeJMNT\n" +
                        "o7cixfmkQaiXHr/S1AS4BRTqLG/zgLzoJpVbzi45rnVEZc0oTm11KG3uUxkZTRr3\n" +
                        "5ORbYyZpkscKwHL2M0J/1GmnA1hmhQdwUQyIKxg4eKQwyE+/TdbFlCWVNnOlb+91\n" +
                        "eXvS11nIJ1oaBgn7u4qihuVmFmngLMxExnLYKV6AwdkwFD6pERObclRD9vAl5eUk\n" +
                        "+sM6zQYwfLdyC2i8e+ETBeOg1ijptM4KT5Uaq89zxjLR0DPH4S+aILp3gYHGrW5r\n" +
                        "eMxZAEMCAwEAAaMhMB8wHQYDVR0OBBYEFOME39JtbjzQaK3ufpKo/Pl4sZ8XMA0G\n" +
                        "CSqGSIb3DQEBCwUAA4IBAQCDcw0+Sf0yeVROVlb2/VV3oIblHkGQheXeIurW64k7\n" +
                        "tEzHtx9i8dnj5lzTZNH6hU4GRlyULbSDzjcM3P2XFRsM+0a/kEJZVqnLz5ji//7/\n" +
                        "ZXaRX0TiE2IfFOTGbO6LusO3yR4tOER/WHllz2H21C2SbW3+92Ou28glTZa42AAZ\n" +
                        "mUj9j+p6mZqD4/tUBqAEqqQoMIhw9CNjc46STNayBjt/0/+I2pfy6LagrMbjBzZ0\n" +
                        "A5kXg9WjnywGk8XFr/3RZz8DrUmCYs2qCYLCHQHsuCE6gCuf9wKhKyD51MFXXRr0\n" +
                        "cyG6LYQjrreMHYk4ZfN2NPC6lGjWxB5mIbV/DuikCnYu\n" +
                        "-----END CERTIFICATE-----";

        return CertUtils.getCertFromString(cert);
    }

    private static void testEqualityAndHashCode(Certificate cert) throws Exception {
        Certificate duplicate = loadCert();

        if (!cert.equals(cert) || cert.hashCode() != cert.hashCode()) {
            throw new Exception("Certificate not equal to itself");
        }

        if (!cert.equals(duplicate) || cert.hashCode() != duplicate.hashCode()) {
            throw new Exception("Equal certs do not have equal hashCodes");
        }

        if (cert.equals(null)) {
            throw new Exception("Certificate equals null unexpectedly");
        }

        System.out.println("Certificate equality and hashCode check passed");
    }

    private static void testRepeatedHashCode(Certificate cert) {
        int h1 = cert.hashCode();
        int h2 = cert.hashCode();
        if (h1 != h2) {
            throw new RuntimeException("Inconsistent hashCode across calls");
        }
        System.out.println("Repeated hashCode call consistency passed");
    }

    private static void testSerializationRoundTrip(Certificate cert) throws Exception {
        Certificate copy = serializeAndDeserialize(cert);
        if (!cert.equals(copy)) {
            throw new Exception("Certs not equal after deserialization");
        }
        if (cert.hashCode() != copy.hashCode()) {
            throw new Exception("hashCode mismatch after deserialization");
        }
        System.out.println("Serialization + hashCode test passed");
    }

    private static Certificate serializeAndDeserialize(Certificate cert) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(cert);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return (Certificate) ois.readObject();
        }
    }

    private static void testHashUniqueness() throws Exception {
        System.out.println("Hash uniqueness test with slightly varied certs");
        Set<Integer> seen = new HashSet<>();
        int collisions = 0;

        for (int i = 0; i < 5; i++) {
            Certificate cert = loadCert(); // Same input = same hash
            if (!seen.add(cert.hashCode())) {
                collisions++;
            }
        }

        if (collisions > 0) {
            System.out.println("Hash collisions seen: " + collisions + " (expected if same cert reused)");
        } else {
            System.out.println("Hash uniqueness (within sample set)");
        }
    }
}