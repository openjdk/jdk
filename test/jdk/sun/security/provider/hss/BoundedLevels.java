/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8396408
 * @summary Reject invalid HSS public key hierarchy levels
 * @run main BoundedLevels
 */

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

public class BoundedLevels {

    private static final byte[] SPKI_PREFIX = HexFormat.of().parseHex(
            "304e300d060b2a864886f70d0109100311033d00");

    private record RawPublicKey(byte[] data) implements PublicKey {
        @Override
        public String getAlgorithm() {
            return "HSS/LMS";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return data.clone();
        }
    }

    private static byte[] raw(int levels) {
        byte[] result = new byte[60];
        // HSS levels, LMS type (5), and LM-OTS type (1).
        ByteBuffer.wrap(result).putInt(levels).putInt(5).putInt(1);
        return result;
    }

    private static byte[] encoded(int levels) {
        return ByteBuffer.allocate(SPKI_PREFIX.length + 60)
                .put(SPKI_PREFIX)
                .put(raw(levels))
                .array();
    }

    private static void testInvalidLevels(KeyFactory factory)
            throws Exception {
        for (int levels : new int[] {
                Integer.MIN_VALUE, 0, 9, Integer.MAX_VALUE}) {
            try {
                factory.generatePublic(
                        new X509EncodedKeySpec(encoded(levels)));
                throw new AssertionError(
                        "Invalid X.509 key accepted: " + levels);
            } catch (InvalidKeySpecException expected) {
                if (!(expected.getCause() instanceof InvalidKeyException)) {
                    throw expected;
                }
            }

            try {
                factory.translateKey(new RawPublicKey(raw(levels)));
                throw new AssertionError(
                        "Invalid RAW key accepted: " + levels);
            } catch (InvalidKeyException expected) {
            }
        }
    }

    private static void testValidLevels(KeyFactory factory)
            throws Exception {
        for (int levels = 1; levels <= 8; levels++) {
            factory.generatePublic(new X509EncodedKeySpec(encoded(levels)));
            factory.translateKey(new RawPublicKey(raw(levels)));
        }
    }

    public static void main(String[] args) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("HSS/LMS");
        testValidLevels(factory);
        testInvalidLevels(factory);
    }
}
