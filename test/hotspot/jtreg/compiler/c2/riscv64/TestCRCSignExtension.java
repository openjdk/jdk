/*
 * Copyright (c) 2026, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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
 * @test id=c1
 * @bug 8394822
 * @summary Verify sign extension of RISC-V CRC32 and CRC32C intrinsic results
 * @requires os.arch == "riscv64" & vm.compiler1.enabled
 * @modules java.base/java.util.zip:+open
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+UseCRC32Intrinsics -XX:+UseCRC32CIntrinsics
 *      -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:CompileThreshold=1000
 *      -XX:CompileCommand=compileonly,compiler.c2.riscv64.TestCRCSignExtension::test*
 *      compiler.c2.riscv64.TestCRCSignExtension
 */

/*
 * @test id=c2
 * @bug 8394822
 * @summary Verify sign extension of RISC-V CRC32 and CRC32C intrinsic results
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @modules java.base/java.util.zip:+open
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+UseCRC32Intrinsics -XX:+UseCRC32CIntrinsics
 *      -XX:-TieredCompilation -XX:CompileThreshold=1000
 *      -XX:CompileCommand=compileonly,compiler.c2.riscv64.TestCRCSignExtension::test*
 *      compiler.c2.riscv64.TestCRCSignExtension
 */

package compiler.c2.riscv64;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;

public class TestCRCSignExtension {
    private static final MethodHandle CRC32_UPDATE;
    private static final MethodHandle CRC32_UPDATE_BYTES;
    private static final MethodHandle CRC32C_UPDATE_BYTES;

    static {
        try {
            var lookup32 = MethodHandles.privateLookupIn(CRC32.class, MethodHandles.lookup());
            var lookup32c = MethodHandles.privateLookupIn(CRC32C.class, MethodHandles.lookup());
            var byteType = MethodType.methodType(int.class, int.class, int.class);
            var arrayType = MethodType.methodType(int.class, int.class, byte[].class, int.class, int.class);
            CRC32_UPDATE = lookup32.findStatic(CRC32.class, "update", byteType);
            CRC32_UPDATE_BYTES = lookup32.findStatic(CRC32.class, "updateBytes0", arrayType);
            CRC32C_UPDATE_BYTES = lookup32c.findStatic(CRC32C.class, "updateBytes", arrayType);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Compare before a field store or an interpreted return can normalize the result.
    static boolean testUpdateByte(int b, int expected) throws Throwable {
        // Fresh CRC32 state passed to the private native.
        return (int) CRC32_UPDATE.invokeExact(0, b) == expected;
    }

    static boolean testUpdateBytesCRC32(byte[] data, int expected) throws Throwable {
        return (int) CRC32_UPDATE_BYTES.invokeExact(0, data, 0, data.length) == expected;
    }

    static boolean testUpdateBytesCRC32C(byte[] data, int expected) throws Throwable {
        // A fresh CRC32C starts from the raw state 0xffffffff.
        return (int) CRC32C_UPDATE_BYTES.invokeExact(0xffffffff, data, 0, data.length) == expected;
    }

    static void check(boolean ok, int i, String what) {
        if (!ok) {
            throw new RuntimeException(what + " mismatch at iteration " + i);
        }
    }

    public static void main(String[] args) throws Throwable {
        // One input per polarity, each chosen so CRC32 and the raw CRC32C state agree, so a single
        // pair of arrays covers both stubs.
        byte[] clear = "b".getBytes(StandardCharsets.US_ASCII); // bit 31 clear for both
        byte[] set = "e".getBytes(StandardCharsets.US_ASCII);   // bit 31 set for both

        // Enough iterations to reach -XX:CompileThreshold and compile the test methods with C1 or C2.
        for (int i = 0; i < 2_000; i++) {
            // CRC32.update(int): C1's inline updateCRC32. Its single byte is the array's only byte,
            // so the expected value equals the CRC32 array one below.
            // python3 -c "import zlib; print(hex(zlib.crc32(bytes([ord('b')]), 0)))"  # 0x71beeff9
            check(testUpdateByte('b', 0x71beeff9), i, "CRC32.update(int) bit31 clear");
            // python3 -c "import zlib; print(hex(zlib.crc32(bytes([ord('e')]), 0)))"  # 0xefda7a5a
            check(testUpdateByte('e', 0xefda7a5a), i, "CRC32.update(int) bit31 set");

            // updateBytesCRC32 stub.
            // python3 -c "import zlib; print(hex(zlib.crc32(b'b')))"  # 0x71beeff9
            check(testUpdateBytesCRC32(clear, 0x71beeff9), i, "CRC32.update(byte[]) bit31 clear");
            // python3 -c "import zlib; print(hex(zlib.crc32(b'e')))"  # 0xefda7a5a
            check(testUpdateBytesCRC32(set, 0xefda7a5a), i, "CRC32.update(byte[]) bit31 set");

            // updateBytesCRC32C stub; the raw state is the complement of getValue().
            // python3 -c "from crc32c import crc32c; print(hex((~crc32c(b'b')) & 0xffffffff))"  # 0x2d7f4f3b
            check(testUpdateBytesCRC32C(clear, 0x2d7f4f3b), i, "CRC32C.update(byte[]) bit31 clear");
            // python3 -c "from crc32c import crc32c; print(hex((~crc32c(b'e')) & 0xffffffff))"  # 0xf9b52bd0
            check(testUpdateBytesCRC32C(set, 0xf9b52bd0), i, "CRC32C.update(byte[]) bit31 set");
        }
    }
}
