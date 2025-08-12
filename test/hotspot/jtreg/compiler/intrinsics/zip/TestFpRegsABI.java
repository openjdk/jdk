/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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
 * @test TestFpRegsABI
 * @bug 8324874
 * @summary ABI for the Arm 64-bit Architecture requires to preserve registers v8-v15 by a callee across subroutine calls
 *
 * @run main/othervm -XX:-TieredCompilation -Xbatch -XX:CompileCommand=inline,*::calcValue compiler.intrinsics.zip.TestFpRegsABI
 * @run main/othervm -XX:-TieredCompilation -Xbatch -XX:CompileCommand=dontinline,*::calcValue compiler.intrinsics.zip.TestFpRegsABI
 * @run main/othervm -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xbatch -XX:CompileCommand=inline,*::calcValue compiler.intrinsics.zip.TestFpRegsABI
 * @run main/othervm -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xbatch -XX:CompileCommand=dontinline,*::calcValue compiler.intrinsics.zip.TestFpRegsABI
 * @run main/othervm -Xbatch -XX:CompileCommand=inline,*::calcValue compiler.intrinsics.zip.TestFpRegsABI
 * @run main/othervm -Xbatch -XX:CompileCommand=dontinline,*::calcValue compiler.intrinsics.zip.TestFpRegsABI
 * @run main/othervm -Xint compiler.intrinsics.zip.TestFpRegsABI
 */

package compiler.intrinsics.zip;

import java.util.zip.Checksum;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;

public class TestFpRegsABI {
    private static byte[] buf;

    static {
        buf = new byte[1024];
        for (int i = 0; i < buf.length; ++i) {
          buf[i] = (byte)i;
        }
    }

    private static class RegressionTest {
        Checksum checksum;

        RegressionTest(Checksum checksum) {
            this.checksum = checksum;
        }

        public void run(byte[] buf, long expectedValue) {
            for (int i = 0; i < 20_000; ++i) {
                runIteration(buf, expectedValue);
            }
        }

        // If checksum intrinsic does not save fp registers as ABI requires,
        // the second call of calcValue might produce a wrong result.
        private void runIteration(byte[] buf, long expectedValue) {
            int v1 = calcValue(buf);
            checksum.reset();
            checksum.update(buf, 0, buf.length);
            long checksumValue = checksum.getValue();
            if (checksumValue != expectedValue) {
                System.err.printf("ERROR: checksum = 0x%016x, expected = 0x%016x\n",
                                  checksumValue, expectedValue);
                throw new RuntimeException("Checksum Error");
            }
            int v2 = calcValue(buf);
            if (v1 != v2) {
                throw new RuntimeException("Expect v2(" + v2 + ") to equal v1(" + v1 + ")");
            }
        }

        private int calcValue(byte[] buf) {
            return (int)(2.5 * buf.length);
        }
    }

    private static class TestIntrinsic {
        Checksum checksum;

        TestIntrinsic(Checksum checksum) {
            this.checksum = checksum;
        }

        public void run(byte[] buf, long expectedValue) {
            for (int i = 0; i < 20_000; ++i) {
                runIteration(buf, expectedValue);
            }
        }

        // If checksum intrinsic does not save fp registers as ABI requires,
        // the second call of calcValue might produce a wrong result.
        private void runIteration(byte[] buf, long expectedValue) {
            int v1 = calcValue(buf);
            checksum.reset();
            checksum.update(buf, 0, buf.length);
            long checksumValue = checksum.getValue();
            if (checksumValue != expectedValue) {
                System.err.printf("ERROR: checksum = 0x%016x, expected = 0x%016x\n",
                                  checksumValue, expectedValue);
                throw new RuntimeException("Checksum Error");
            }
            int v2 = calcValue(buf);
            if (v1 != v2) {
                throw new RuntimeException("Expect v2(" + v2 + ") to equal v1(" + v1 + ")");
            }
        }

        // ABI can require some fp registers to be saved by a callee, e.g. v8-15 in ARM64 ABI.
        // We create fp register pressure to get as many fp registers used as possible.
        private int calcValue(byte[] buf) {
            double v = 0.0;
            for (int i = 24; i <= buf.length; i += 24) {
                v += buf[i - 1] * ((double)i - 1.0) + (double)i - 1.0;
                v += buf[i - 2] * ((double)i - 2.0) + (double)i - 2.0;
                v += buf[i - 3] * ((double)i - 3.0) + (double)i - 3.0;
                v += buf[i - 4] * ((double)i - 4.0) + (double)i - 4.0;
                v += buf[i - 5] * ((double)i - 5.0) + (double)i - 5.0;
                v += buf[i - 6] * ((double)i - 6.0) + (double)i - 6.0;
                v += buf[i - 7] * ((double)i - 7.0) + (double)i - 7.0;
                v += buf[i - 8] * ((double)i - 8.0) + (double)i - 8.0;
                v += buf[i - 9] * ((double)i - 9.0) + (double)i - 9.0;
                v += buf[i - 10] * ((double)i - 10.0) + (double)i - 10.0;
                v += buf[i - 11] * ((double)i - 11.0) + (double)i - 11.0;
                v += buf[i - 12] * ((double)i - 12.0) + (double)i - 12.0;
                v += buf[i - 13] * ((double)i - 13.0) + (double)i - 13.0;
                v += buf[i - 14] * ((double)i - 14.0) + (double)i - 14.0;
                v += buf[i - 15] * ((double)i - 15.0) + (double)i - 15.0;
                v += buf[i - 16] * ((double)i - 16.0) + (double)i - 16.0;
                v += buf[i - 17] * ((double)i - 17.0) + (double)i - 17.0;
                v += buf[i - 18] * ((double)i - 18.0) + (double)i - 18.0;
                v += buf[i - 19] * ((double)i - 19.0) + (double)i - 19.0;
                v += buf[i - 20] * ((double)i - 20.0) + (double)i - 20.0;
                v += buf[i - 21] * ((double)i - 21.0) + (double)i - 21.0;
                v += buf[i - 22] * ((double)i - 22.0) + (double)i - 22.0;
                v += buf[i - 23] * ((double)i - 23.0) + (double)i - 23.0;
                v += buf[i - 24] * ((double)i - 24.0) + (double)i - 24.0;
            }
            return (int)v;
        }
    }

    public static void main(final String[] argv) {
        new TestIntrinsic(new CRC32()).run(buf, 0x00000000b70b4c26L);
        new TestIntrinsic(new CRC32C()).run(buf, 0x000000002cdf6e8fL);
        new RegressionTest(new CRC32()).run(buf, 0x00000000b70b4c26L);
        new RegressionTest(new CRC32C()).run(buf, 0x000000002cdf6e8fL);
    }
}

