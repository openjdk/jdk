/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8048357
 * @summary PKCS8 Standards Conformance Tests
 * @library /test/lib
 * @requires (os.family != "solaris")
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.util
 *          java.base/sun.security.provider
 *          java.base/sun.security.x509
 * @compile -XDignore.symbol.file PKCS8Test.java
 * @run main PKCS8Test
 */

/*
 * Skip Solaris since the DSAPrivateKeys returned by
 * SunPKCS11 Provider are not subclasses of PKCS8Key
 */
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.util.Arrays;
import sun.security.pkcs.PKCS8Key;
import sun.security.provider.DSAPrivateKey;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.x509.AlgorithmId;
import jdk.test.lib.hexdump.HexPrinter;
import static java.lang.System.out;

public class PKCS8Test {

    static final DerOutputStream derOutput = new DerOutputStream();

    static final String FORMAT = "PKCS#8";
    static final String EXPECTED_ALG_ID_CHRS = "DSA\n\tp:     02\n\tq:     03\n"
            + "\tg:     04\n";
    static final String ALGORITHM = "DSA";
    static final String EXCEPTION_MESSAGE = "version mismatch: (supported:     "
            + "00, parsed:     01";

    // test second branch in byte[] encode()
    // DER encoding,include (empty) set of attributes
    static final int[] NEW_ENCODED_KEY_INTS = { 0x30,
            // length 30 = 0x1e
            0x1e,
            // first element
            // version Version (= INTEGER)
            0x02,
            // length 1
            0x01,
            // value 0
            0x00,
            // second element
            // privateKeyAlgorithmIdentifier PrivateKeyAlgorithmIdentifier
            // (sequence)
            // (an object identifier?)
            0x30,
            // length 18
            0x12,
            // contents
            // object identifier, 5 bytes
            0x06, 0x05,
            // { 1 3 14 3 2 12 }
            0x2b, 0x0e, 0x03, 0x02, 0x0c,
            // sequence, 9 bytes
            0x30, 0x09,
            // integer 2
            0x02, 0x01, 0x02,
            // integer 3
            0x02, 0x01, 0x03,
            // integer 4
            0x02, 0x01, 0x04,
            // third element
            // privateKey PrivateKey (= OCTET STRING)
            0x04,
            // length
            0x03,
            // privateKey contents
            0x02, 0x01, 0x01,
            // 4th (optional) element -- attributes [0] IMPLICIT Attributes
            // OPTIONAL
            // (Attributes = SET OF Attribute) Here, it will be empty.
            0xA0,
            // length
            0x00 };

    // encoding originally created, but with the version changed
    static final int[] NEW_ENCODED_KEY_INTS_2 = {
            // sequence
            0x30,
            // length 28 = 0x1c
            0x1c,
            // first element
            // version Version (= INTEGER)
            0x02,
            // length 1
            0x01,
            // value 1 (illegal)
            0x01,
            // second element
            // privateKeyAlgorithmIdentifier PrivateKeyAlgorithmIdentifier
            // (sequence)
            // (an object identifier?)
            0x30,
            // length 18
            0x12,
            // contents
            // object identifier, 5 bytes
            0x06, 0x05,
            // { 1 3 14 3 2 12 }
            0x2b, 0x0e, 0x03, 0x02, 0x0c,
            // sequence, 9 bytes
            0x30, 0x09,
            // integer 2
            0x02, 0x01, 0x02,
            // integer 3
            0x02, 0x01, 0x03,
            // integer 4
            0x02, 0x01, 0x04,
            // third element
            // privateKey PrivateKey (= OCTET STRING)
            0x04,
            // length
            0x03,
            // privateKey contents
            0x02, 0x01, 0x01 };

    // 0000: 30 1E 02 01 00 30 14 06 07 2A 86 48 CE 38 04 01 0....0...*.H.8..
    // 0010: 30 09 02 01 02 02 01 03 02 01 04 04 03 02 01 01 0...............
    static final int[] EXPECTED = { 0x30,
            // length 30 = 0x1e
            0x1e,
            // first element
            // version Version (= INTEGER)
            0x02,
            // length 1
            0x01,
            // value 0
            0x00,
            // second element
            // privateKeyAlgorithmIdentifier PrivateKeyAlgorithmIdentifier
            // (sequence)
            // (an object identifier?)
            0x30, 0x14, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x38, 0x04, 0x01,
            // integer 2
            0x30, 0x09, 0x02,
            // integer 3
            0x01, 0x02, 0x02,
            // integer 4
            0x01, 0x03, 0x02,
            // third element
            // privateKey PrivateKey (= OCTET STRING)
            0x01,
            // length
            0x04,
            // privateKey contents
            0x04, 0x03, 0x02,
            // 4th (optional) element -- attributes [0] IMPLICIT Attributes
            // OPTIONAL
            // (Attributes = SET OF Attribute) Here, it will be empty.
            0x01,
            // length
            0x01 };

    static void raiseException(String expected, String received) {
        throw new RuntimeException(
                "Expected " + expected + "; Received " + received);
    }

    public static void main(String[] args)
            throws IOException, InvalidKeyException {

        BigInteger x = BigInteger.valueOf(1);
        BigInteger p = BigInteger.valueOf(2);
        BigInteger q = BigInteger.valueOf(3);
        BigInteger g = BigInteger.valueOf(4);

        DSAPrivateKey priv = new DSAPrivateKey(x, p, q, g);

        byte[] encodedKey = priv.getEncoded();
        byte[] expectedBytes = new byte[EXPECTED.length];
        for (int i = 0; i < EXPECTED.length; i++) {
            expectedBytes[i] = (byte) EXPECTED[i];
        }

        dumpByteArray("encodedKey :", encodedKey);
        if (!Arrays.equals(encodedKey, expectedBytes)) {
            raiseException(new String(expectedBytes), new String(encodedKey));
        }

        PKCS8Key decodedKey = PKCS8Key.parse(new DerValue(encodedKey));

        String alg = decodedKey.getAlgorithm();
        AlgorithmId algId = decodedKey.getAlgorithmId();
        out.println("Algorithm :" + alg);
        out.println("AlgorithmId: " + algId);

        if (!ALGORITHM.equals(alg)) {
            raiseException(ALGORITHM, alg);
        }
        if (!EXPECTED_ALG_ID_CHRS.equalsIgnoreCase(algId.toString())) {
            raiseException(EXPECTED_ALG_ID_CHRS, algId.toString());
        }

        decodedKey.encode(derOutput);
        dumpByteArray("Stream encode: ", derOutput.toByteArray());
        if (!Arrays.equals(derOutput.toByteArray(), expectedBytes)) {
            raiseException(new String(expectedBytes), derOutput.toString());
        }

        dumpByteArray("byte[] encoding: ", decodedKey.getEncoded());
        if (!Arrays.equals(decodedKey.getEncoded(), expectedBytes)) {
            raiseException(new String(expectedBytes),
                    new String(decodedKey.getEncoded()));
        }

        if (!FORMAT.equals(decodedKey.getFormat())) {
            raiseException(FORMAT, decodedKey.getFormat());
        }

        try {
            byte[] newEncodedKey = new byte[NEW_ENCODED_KEY_INTS.length];
            for (int i = 0; i < newEncodedKey.length; i++) {
                newEncodedKey[i] = (byte) NEW_ENCODED_KEY_INTS[i];
            }
            PKCS8Key newDecodedKey = PKCS8Key
                    .parse(new DerValue(newEncodedKey));

            throw new RuntimeException(
                    "key1: Expected an IOException during " + "parsing");
        } catch (IOException e) {
            System.out.println("newEncodedKey: should have excess data due to "
                    + "attributes, which are not supported");
        }

        try {
            byte[] newEncodedKey2 = new byte[NEW_ENCODED_KEY_INTS_2.length];
            for (int i = 0; i < newEncodedKey2.length; i++) {
                newEncodedKey2[i] = (byte) NEW_ENCODED_KEY_INTS_2[i];
            }

            PKCS8Key newDecodedKey2 = PKCS8Key
                    .parse(new DerValue(newEncodedKey2));

            throw new RuntimeException(
                    "key2: Expected an IOException during " + "parsing");
        } catch (IOException e) {
            out.println("Key 2: should be illegal version");
            out.println(e.getMessage());
            if (!EXCEPTION_MESSAGE.equals(e.getMessage())) {
                throw new RuntimeException("Key2: expected: "
                        + EXCEPTION_MESSAGE + " get: " + e.getMessage());
            }
        }
    }

    static void dumpByteArray(String nm, byte[] bytes) throws IOException {
        out.println(nm + " length: " + bytes.length);
        HexPrinter.simple().dest(out).format(bytes);
    }
}
