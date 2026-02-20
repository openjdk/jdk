/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8232069
 * @requires vm.cds
 * @requires vm.bits == 64
 * @requires vm.gc.Z
 * @requires vm.gc.Serial
 * @requires vm.gc == null
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestZGCWithCDS
 */

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

public class TestZGCWithCDS {
    public final static String HELLO = "Hello World";
    public final static String UNABLE_TO_USE_ARCHIVE = "Unable to use shared archive.";
    public final static String ERR_MSG = "The saved state of UseCompressedOops and UseCompressedClassPointers is different from runtime, CDS will be disabled.";

    public static void main(String... args) throws Exception {
        // Is the COH option set?
        String coh = getBooleanFlagOrNull("UseCompactObjectHeaders");
        if (coh == null) {
            // Not set, run with COH on/off.
            runTests("-XX:+UseCompactObjectHeaders");
            runTests("-XX:-UseCompactObjectHeaders");
        } else {
            // Set, just run with whatever is given.
            runTests(coh);
        }
    }

    private static void runTests(String compactHeaders) throws Exception {
        String coops = getBooleanFlagOrNull("UseCompressedOops");
        String compressed = getBooleanFlagOrNull("UseCompressedClassPointers");
        System.out.println("COOPS:" + coops);
        System.out.println("COMPRESSED:" + compressed);

        String helloJar = JarBuilder.build("hello", "Hello");
        System.out.println("[!] Scenario running with " + compactHeaders);
        System.out.println("0. Dump with ZGC");
        OutputAnalyzer out = TestCommon
                                .dump(helloJar,
                                      new String[] {"Hello"},
                                      "-XX:+UseZGC",
                                      compactHeaders,
                                      "-Xlog:cds");
        out.shouldContain("Dumping shared data to file:");
        out.shouldHaveExitValue(0);

        System.out.println("1. Run with same args of dump");
        out = TestCommon
                 .exec(helloJar,
                       "-XX:+UseZGC",
                       compactHeaders,
                       "-Xlog:cds",
                       "Hello");
        out.shouldContain(HELLO);
        out.shouldHaveExitValue(0);

        if (shouldRunPositive(coops) && shouldRunPositive(compressed)) {
            System.out.println("2. Run with +UseCompressedOops +UseCompressedClassPointers");
            out = TestCommon
                     .exec(helloJar,
                           "-XX:-UseZGC",
                           "-XX:+UseCompressedOops",
                           "-XX:+UseCompressedClassPointers",
                           compactHeaders,
                           "-Xlog:cds",
                           "Hello");
            out.shouldContain(UNABLE_TO_USE_ARCHIVE);
            out.shouldContain(ERR_MSG);
            out.shouldHaveExitValue(1);
        }

        if (shouldRunNegative(coops) && shouldRunNegative(compressed)) {
            System.out.println("3. Run with -UseCompressedOops -UseCompressedClassPointers");
            out = TestCommon
                     .exec(helloJar,
                           "-XX:+UseSerialGC",
                           "-XX:-UseCompressedOops",
                           "-XX:-UseCompressedClassPointers",
                           compactHeaders,
                           "-Xlog:cds",
                           "Hello");
            out.shouldContain(UNABLE_TO_USE_ARCHIVE);
            out.shouldContain(ERR_MSG);
            out.shouldHaveExitValue(1);
        }

        if (shouldRunNegative(coops) && shouldRunPositive(compressed)) {
            System.out.println("4. Run with -UseCompressedOops +UseCompressedClassPointers");
            out = TestCommon
                     .exec(helloJar,
                           "-XX:+UseSerialGC",
                           "-XX:-UseCompressedOops",
                           "-XX:+UseCompressedClassPointers",
                           compactHeaders,
                           "-Xlog:cds",
                           "Hello");
            out.shouldContain(HELLO);
            out.shouldHaveExitValue(0);
        }

        if (shouldRunPositive(coops) && shouldRunNegative(compressed)) {
            System.out.println("5. Run with +UseCompressedOops -UseCompressedClassPointers");
            out = TestCommon
                     .exec(helloJar,
                           "-XX:+UseSerialGC",
                           "-XX:+UseCompressedOops",
                           "-XX:-UseCompressedClassPointers",
                           compactHeaders,
                           "-Xlog:cds",
                           "Hello");
            out.shouldContain(UNABLE_TO_USE_ARCHIVE);
            out.shouldContain(ERR_MSG);
            out.shouldHaveExitValue(1);
        }

        if (shouldRunPositive(coops) && shouldRunPositive(compressed)) {
            System.out.println("6. Run with +UseCompressedOops +UseCompressedClassPointers");
            out = TestCommon
                     .exec(helloJar,
                           "-XX:+UseSerialGC",
                           "-XX:+UseCompressedOops",
                           "-XX:+UseCompressedClassPointers",
                           compactHeaders,
                           "-Xlog:cds",
                           "Hello");
            out.shouldContain(UNABLE_TO_USE_ARCHIVE);
            out.shouldContain(ERR_MSG);
            out.shouldHaveExitValue(1);
        }

        if (shouldRunNegative(coops) && shouldRunNegative(compressed)) {
            System.out.println("7. Dump with -UseCompressedOops -UseCompressedClassPointers");
            out = TestCommon
                     .dump(helloJar,
                           new String[] {"Hello"},
                           "-XX:+UseSerialGC",
                           "-XX:-UseCompressedOops",
                           "-XX:+UseCompressedClassPointers",
                           compactHeaders,
                           "-Xlog:cds");
            out.shouldContain("Dumping shared data to file:");
            out.shouldHaveExitValue(0);
        }

        System.out.println("8. Run with ZGC");
        out = TestCommon
                 .exec(helloJar,
                       "-XX:+UseZGC",
                       compactHeaders,
                       "-Xlog:cds",
                       "Hello");
        out.shouldContain(HELLO);
        out.shouldHaveExitValue(0);
    }

    // Gets a boolean flag via name (excl. XX and +/-) if set by jtreg's javaoptions/vmoptions
    // Returns null if the flag is not set, otherwise the value of the flag.
    private static String getBooleanFlagOrNull(String flag) {
        for (String passedFlag : Utils.getTestJavaOpts()) {
            String candidateOn = "-XX:+" + flag;
            String candidateOff = "-XX:-" + flag;
            if (passedFlag.equals(candidateOn) || passedFlag.equals(candidateOff)) {
                return passedFlag;
            }
        }
        return null;
    }

    private static boolean shouldRunPositive(String flag) {
        return flag == null || flag.indexOf("+") > 0;
    }

    private static boolean shouldRunNegative(String flag) {
        return flag == null || flag.indexOf("-") > 0; // ignores first -XX:..
    }
}
