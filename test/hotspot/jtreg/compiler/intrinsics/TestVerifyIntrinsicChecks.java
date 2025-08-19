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

/*
 * @test
 * @bug 8361842
 * @summary Verify the effectiveness of the `VerifyIntrinsicChecks` VM flag
 *          through (bypassing `StringCoding::encodeAsciiArray`, and) feeding
 *          invalid input to an intrinsified `StringCoding::encodeAsciiArray0`
 *          (note the `0` suffix!).
 * @library /compiler/patches
 * @library /test/lib
 * @build java.base/java.lang.Helper
 * @comment `vm.debug == true` is required since `VerifyIntrinsicChecks` is a
 *          development flag
 * @requires vm.debug == true & vm.flavor == "server" & !vm.graal.enabled
 * @run main/othervm compiler.intrinsics.TestVerifyIntrinsicChecks verify
 */

package compiler.intrinsics;

import java.lang.Helper;
import java.time.Instant;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public final class TestVerifyIntrinsicChecks {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "verify" -> {
                log("Starting JVM in a separate process to verify the crash");
                OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                        "-Xcomp",
                        "-XX:-TieredCompilation",
                        "-XX:CompileCommand=inline,java.lang.StringCoding::encodeAsciiArray0",
                        "-XX:+VerifyIntrinsicChecks",
                        "--patch-module", "java.base=%s/java.base".formatted(System.getProperty("test.patch.path")),
                        "compiler.intrinsics.TestVerifyIntrinsicChecks",
                        "crash");
                outputAnalyzer.shouldContain("unexpected null in intrinsic");
                outputAnalyzer.shouldNotHaveExitValue(0);
            }
            case "crash" -> {
                log("Triggering the crash");
                warmUpIntrinsicMethod();
                violateIntrinsicMethodContract();
            }
            default -> throw new IllegalArgumentException();
        }
    }

    private static void warmUpIntrinsicMethod() {
        log("Warming up the intrinsic method");
        char[] sa = createAsciiChars(8192);
        byte[] sp = new byte[4096];
        for (int i = 0; i < 1_000; i++) {
            Helper.StringCodingEncodeAsciiArray0(sa, i, sp, 0, sp.length - i);
        }
    }

    private static char[] createAsciiChars(int length) {
        char[] buffer = new char[length];
        for (int i = 0; i < length; i++) {
            buffer[i] = (char) (i % '\u0080');
        }
        return buffer;
    }

    private static void violateIntrinsicMethodContract() {
        log("Violating the intrinsic method contract (sa=null)");
        Helper.StringCodingEncodeAsciiArray0(null, 1, null, 1, 1);
    }

    private synchronized static void log(String format, Object... args) {
        Object[] extendedArgs = new Object[2 + args.length];
        extendedArgs[0] = Instant.now();
        extendedArgs[1] = Thread.currentThread().getName();
        System.arraycopy(args, 0, extendedArgs, extendedArgs.length - args.length, args.length);
        String extendedFormat = "%%s [%%s] %s%%n".formatted(format);
        System.out.printf(extendedFormat, extendedArgs);
    }

}
