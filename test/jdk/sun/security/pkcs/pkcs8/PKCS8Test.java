/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8048357 8244565
 * @summary PKCS8 Standards Conformance Tests
 * @library /test/lib
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.util
 *          java.base/sun.security.provider
 *          java.base/sun.security.x509
 * @run main PKCS8Test
 * @run main/othervm -Dtest.provider.name=SunJCE PKCS8Test
 */

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.HexFormat;

import jdk.test.lib.hexdump.ASN1Formatter;
import jdk.test.lib.hexdump.HexPrinter;
import sun.security.pkcs.PKCS8Key;
import sun.security.provider.DSAPrivateKey;

public class PKCS8Test {

    static Provider provider;
    static final String FORMAT = "PKCS#8";
    static final String EXPECTED_ALG_ID_CHRS = "DSA, \n" +
            "\tp:     02\n\tq:     03\n\tg:     04\n";
    static final String ALGORITHM = "DSA";

    static final byte[] EXPECTED = HexFormat.of().parseHex(
            "301e" + // SEQUENCE
                "020100" +  // Version int 0
                "3014" +    // PrivateKeyAlgorithmIdentifier
                    "06072a8648ce380401" +      // OID DSA 1.2.840.10040.4.1
                    "3009020102020103020104" +  // p=2, q=3, g=4
                "0403020101");  // PrivateKey OCTET int x = 1

    public static void main(String[] args) throws Exception {
        provider = Security.getProvider(System.getProperty("test.provider.name"));
        byte[] encodedKey = new DSAPrivateKey(
                BigInteger.valueOf(1),
                BigInteger.valueOf(2),
                BigInteger.valueOf(3),
                BigInteger.valueOf(4)).getEncoded();

        if (!Arrays.equals(encodedKey, EXPECTED)) {
            throw new AssertionError(
                HexPrinter.simple()
                    .formatter(ASN1Formatter.formatter())
                    .toString(encodedKey));
        }

        PKCS8Key decodedKey = provider == null ? (PKCS8Key)PKCS8Key.parseKey(encodedKey) :
                (PKCS8Key)PKCS8Key.parseKey(encodedKey, provider);

        assert(ALGORITHM.equalsIgnoreCase(decodedKey.getAlgorithm()));
        assert(FORMAT.equalsIgnoreCase(decodedKey.getFormat()));
        assert(EXPECTED_ALG_ID_CHRS.equalsIgnoreCase(decodedKey.getAlgorithmId().toString()));

        byte[] encodedOutput = decodedKey.getEncoded();
        if (!Arrays.equals(encodedOutput, EXPECTED)) {

            throw new AssertionError(
                HexPrinter.simple()
                    .formatter(ASN1Formatter.formatter())
                    .toString(encodedOutput));
        }

        // Test additional fields
        enlarge(0, "8000");    // attributes

        // PKCSv2 testing done by PEMEncoder/PEMDecoder tests

        assertThrows(() -> enlarge(2));
        assertThrows(() -> enlarge(0, "8000", "8000")); // no dup
        assertThrows(() -> enlarge(0, "810100")); // no public in v1
        assertThrows(() -> enlarge(1, "810100", "8000")); // bad order
        assertThrows(() -> enlarge(1, "820100")); // bad tag
    }

    private static void assertThrows(Runnable o) {
        try {
            o.run();
            throw new AssertionError("Test failed");
        } catch (Exception e) {}
    }

    /**
     * Add more fields to EXPECTED and see if it's still valid PKCS8.
     *
     * @param newVersion new version
     * @param fields extra fields to add, in hex
     */
    static void enlarge(int newVersion, String... fields) {
        byte[] original = EXPECTED.clone();
        int length = original.length;
        for (String field : fields) {   // append fields
            byte[] add = HexFormat.of().parseHex(field);
            original = Arrays.copyOf(original, length + add.length);
            System.arraycopy(add, 0, original, length, add.length);
            length += add.length;
        }
        assert (length < 127);
        original[1] = (byte) (length - 2);   // the length field inside DER
        original[4] = (byte) newVersion;     // the version inside DER
        try {
            if (provider == null) {
                PKCS8Key.parseKey(original);
            } else {
                PKCS8Key.parseKey(original, provider);
            }
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
