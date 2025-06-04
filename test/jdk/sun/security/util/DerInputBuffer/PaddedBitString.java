/*
 * Copyright (c) 2002, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4511556
 * @summary Verify BitString value containing padding bits is accepted.
 * @modules java.base/sun.security.util
 * @library /test/lib
 */
import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import sun.security.util.BitArray;
import sun.security.util.DerInputStream;

public class PaddedBitString {

    // Relaxed the BitString parsing routine to accept bit strings
    // with padding bits, ex. treat DER_BITSTRING_PAD6_b as the same
    // bit string as DER_BITSTRING_PAD6_0/DER_BITSTRING_NOPAD.
    // Note:
    // 1. the number of padding bits has to be in [0...7]
    // 2. value of the padding bits is ignored

    // With no padding bits
    private final static byte[] DER_BITSTRING_NOPAD = { 3, 3, 0,
                                                   (byte)0x5d, (byte)0xc0 };
    // With 6 zero padding bits (01011101 11000000)
    private final static byte[] DER_BITSTRING_PAD6_0 = { 3, 3, 6,
                                                   (byte)0x5d, (byte)0xc0 };

    // With 6 nonzero padding bits (01011101 11001011)
    private final static byte[] DER_BITSTRING_PAD6_b = { 3, 3, 6,
                                                   (byte)0x5d, (byte)0xcb };

    // With 8 padding bits
    private final static byte[] DER_BITSTRING_PAD8_0 = { 3, 3, 8,
                                                   (byte)0x5d, (byte)0xc0 };

    private final static byte[] BITS = { (byte)0x5d, (byte)0xc0 };

    static enum Type {
        BIT_STRING,
        UNALIGNED_BIT_STRING;
    }

    public static void main(String args[]) throws Exception {
        test(DER_BITSTRING_NOPAD, new BitArray(16, BITS));
        test(DER_BITSTRING_PAD6_0, new BitArray(10, BITS));
        test(DER_BITSTRING_PAD6_b, new BitArray(10, BITS));
        test(DER_BITSTRING_PAD8_0, null);
        System.out.println("Tests Passed");
    }

    private static void test(byte[] in, BitArray ans) throws IOException {
        System.out.println("Testing " +
                HexFormat.of().withUpperCase().formatHex(in));
        for (Type t : Type.values()) {
            DerInputStream derin = new DerInputStream(in);
            boolean shouldPass = (ans != null);
            switch (t) {
            case BIT_STRING:
                if (shouldPass) {
                    Asserts.assertTrue(Arrays.equals(ans.toByteArray(),
                            derin.getBitString()));
                } else {
                    Utils.runAndCheckException(() -> derin.getBitString(),
                            IOException.class);
                }
                break;
            case UNALIGNED_BIT_STRING:
                if (shouldPass) {
                    Asserts.assertEQ(ans, derin.getUnalignedBitString());
                } else {
                    Utils.runAndCheckException(() ->
                            derin.getUnalignedBitString(), IOException.class);
                }
                break;
            }
        }
    }
}
