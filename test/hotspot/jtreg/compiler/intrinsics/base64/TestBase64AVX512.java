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
 * @summary Test the AVX-512 Base64 decoder with an incomplete padded tail
 * @requires vm.compiler2.enabled
 * @requires os.simpleArch == "x64"
 * @requires vm.cpu.features ~= ".*avx512_vbmi.*"
 * @requires vm.cpu.features ~= ".*avx512bw.*"
 * @requires vm.cpu.features ~= ".*bmi2.*"
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -Xbootclasspath/a:. compiler.intrinsics.base64.TestBase64AVX512
 */

package compiler.intrinsics.base64;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.Compiler;
import jtreg.SkippedException;

public class TestBase64AVX512 {
    private static final int COMPILATION_LEVEL = CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        if (!Compiler.isIntrinsicAvailable(COMPILATION_LEVEL, "java.util.Base64$Decoder", "decodeBlock",
                byte[].class, int.class, int.class, byte[].class, int.class, boolean.class, boolean.class)) {
            throw new SkippedException("Base64 decoder intrinsic is not available");
        }

        compileDecoder();
        testValidInput();
        testMalformedInput();
    }

    private static void compileDecoder() throws Exception {
        // Initialize the decoder before compiling decode0.
        Base64.getDecoder();
        Method decode0 = Base64.Decoder.class.getDeclaredMethod("decode0",
                byte[].class, int.class, int.class, byte[].class);
        if (!WHITE_BOX.enqueueMethodForCompilation(decode0, COMPILATION_LEVEL)
                || !WHITE_BOX.isMethodCompiled(decode0)
                || WHITE_BOX.getMethodCompilationLevel(decode0) != COMPILATION_LEVEL) {
            throw new IllegalStateException("Base64 decoder was not compiled by C2");
        }
    }

    private static void testValidInput() {
        byte[] encoded = "A".repeat(64).getBytes(StandardCharsets.US_ASCII);
        // decodedOutLength(): 3 * ceil(64 / 4) - 0 padding = 48.
        byte[] decoded = new byte[48];
        if (Base64.getDecoder().decode(encoded, decoded) != decoded.length) {
            throw new IllegalStateException("Valid Base64 input was not fully decoded");
        }
    }

    private static void testMalformedInput() {
        // The final '=' is an invalid one-byte padded tail. The decoder must
        // reject it without writing past the logical output length.
        byte[] encoded = ("A".repeat(64) + "=").getBytes(StandardCharsets.US_ASCII);
        // decodedOutLength(): 3 * ceil(65 / 4) - 1 padding = 50.
        int logicalOutputLength = 50;
        byte canary = (byte) 0xBA;
        byte[] decoded = new byte[logicalOutputLength + 64];
        Arrays.fill(decoded, canary);

        try {
            Base64.getDecoder().decode(encoded, decoded);
            throw new IllegalStateException("Invalid Base64 input was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected: Java validates the incomplete padded tail.
        }

        for (int i = logicalOutputLength; i < decoded.length; i++) {
            if (decoded[i] != canary) {
                throw new IllegalStateException("Base64 decoder overwrote the output buffer");
            }
        }
    }
}
