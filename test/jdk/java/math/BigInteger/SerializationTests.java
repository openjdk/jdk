/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282252
 * @summary Verify BigInteger objects are serialized properly.
 */

import java.math.*;
import java.io.*;
import java.util.Arrays;
import java.util.List;

public class SerializationTests {

    public static void main(String... args) throws Exception {
        checkBigIntegerSerialRoundTrip();
        checkBigIntegerSubSerialRoundTrip();
    }

    private static void checkSerialForm(BigInteger bi) throws Exception {
        checkSerialForm0(bi);
        checkSerialForm0(bi.negate());
    }

    private static void checkSerialForm0(BigInteger bi) throws Exception  {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try(ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(bi);
            oos.flush();
        }

        ObjectInputStream ois = new
            ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
        BigInteger tmp = (BigInteger)ois.readObject();

        if (!bi.equals(tmp) ||
            bi.hashCode() != tmp.hashCode() ||
            bi.getClass() != tmp.getClass() ||
            // For extra measure, directly test equality of components
            bi.signum() != tmp.signum() ||
            !Arrays.equals(bi.toByteArray(), (tmp.toByteArray())) ) {
            System.err.print("  original : " + bi);
            System.err.println(" (hash: 0x" + Integer.toHexString(bi.hashCode()) + ")");
            System.err.print("serialized : " + tmp);
            System.err.println(" (hash: 0x" + Integer.toHexString(tmp.hashCode()) + ")");
            throw new RuntimeException("Bad serial roundtrip");
        }
    }

    private static void checkBigIntegerSerialRoundTrip() throws Exception {
        var values =
            List.of(BigInteger.ZERO,
                    BigInteger.ONE,
                    BigInteger.TWO,
                    BigInteger.TEN,
                    BigInteger.valueOf(100),
                    BigInteger.valueOf(Integer.MAX_VALUE),
                    BigInteger.valueOf(Long.MAX_VALUE-1),
                    new BigInteger("9223372036854775808")); // Long.MAX_VALUE + 1

        for(BigInteger value : values) {
            checkSerialForm(value);
        }
    }

    // Subclass with specialized toString output
    private static class BigIntegerSub extends BigInteger {
        public BigIntegerSub(BigInteger bi) {
            super(bi.toByteArray());
        }

        @Override
        public String toString() {
            return Arrays.toString(toByteArray());
        }
    }

    // Subclass defining a serialVersionUID
    private static class BigIntegerSubSVUID extends BigInteger {
        @java.io.Serial
        private static long serialVesionUID = 0x0123_4567_89ab_cdefL;

        public BigIntegerSubSVUID(BigInteger bi) {
            super(bi.toByteArray());
        }

        @Override
        public String toString() {
            return Arrays.toString(toByteArray());
        }
    }

    // Subclass defining writeReplace
    private static class BigIntegerSubWR extends BigInteger {
        public BigIntegerSubWR(BigInteger bi) {
            super(bi.toByteArray());
        }

        // Just return this; could use a serial proxy instead
        @java.io.Serial
        private Object writeReplace() throws ObjectStreamException {
            return this;
        }
    }


    private static void checkBigIntegerSubSerialRoundTrip() throws Exception {
        var values = List.of(BigInteger.ZERO,
                             BigInteger.ONE,
                             BigInteger.TEN,
                             new BigInteger("9223372036854775808")); // Long.MAX_VALUE + 1

        for(var value : values) {
            checkSerialForm(new BigIntegerSub(value));
            checkSerialForm(new BigIntegerSubSVUID(value));
            checkSerialForm(new BigIntegerSubWR(value));
        }
    }
}
