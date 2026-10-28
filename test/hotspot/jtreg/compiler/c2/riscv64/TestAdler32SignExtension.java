/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
 * @bug 8395824
 * @summary Verify sign extension of RISC-V Adler32 intrinsic result
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @requires vm.cpu.features ~= ".*rvv.*"
 * @modules java.base/java.util.zip:+open
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+UseAdler32Intrinsics
 *      -XX:-TieredCompilation -XX:CompileThreshold=1000
 *      -XX:CompileCommand=compileonly,compiler.c2.riscv64.TestAdler32SignExtension::test*
 *      compiler.c2.riscv64.TestAdler32SignExtension
 */

package compiler.c2.riscv64;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.zip.Adler32;

public class TestAdler32SignExtension {
    private static final MethodHandle ADLER32_UPDATE_BYTES;

    static {
        try {
            var lookup = MethodHandles.privateLookupIn(Adler32.class, MethodHandles.lookup());
            var arrayType = MethodType.methodType(int.class, int.class, byte[].class, int.class, int.class);
            ADLER32_UPDATE_BYTES = lookup.findStatic(Adler32.class, "updateBytes", arrayType);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Compare before a field store or an interpreted return can normalize the result.
    static boolean testUpdateBytes(byte[] data, int expected) throws Throwable {
        return (int) ADLER32_UPDATE_BYTES.invokeExact(1, data, 0, data.length) == expected;
    }

    static void check(boolean ok, int i, String what) {
        if (!ok) {
            throw new RuntimeException(what + " mismatch at iteration " + i);
        }
    }

    public static void main(String[] args) throws Throwable {
        // python3 -c "import zlib; print(hex(zlib.adler32(b'b')))"  # 0x630063
        byte[] clear = new byte[] {'b'}; // bit 31 clear
        // python3 -c "import zlib; print(hex(zlib.adler32(b'\xff' * 16)))"  # 0x87880ff1
        byte[] set = new byte[16];       // bit 31 set
        Arrays.fill(set, (byte) 0xff);

        // Enough iterations to reach -XX:CompileThreshold and compile the test methods with C2.
        for (int i = 0; i < 2_000; i++) {
            check(testUpdateBytes(clear, 0x630063), i, "Adler32.update(byte[]) bit31 clear");
            check(testUpdateBytes(set, 0x87880ff1), i, "Adler32.update(byte[]) bit31 set");
        }
    }
}