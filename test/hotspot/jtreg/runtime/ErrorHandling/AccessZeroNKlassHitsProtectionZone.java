/*
 * Copyright (c) 2025, Red Hat, Inc.
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
 * @test id=no_coh_no_cds
 * @summary Test that dereferencing a Klass that is the result of a decode(0) crashes accessing the nKlass guard zone
 * @library /test/lib
 * @requires vm.bits == 64 & vm.debug == true & vm.flagless
 * @requires os.family != "aix"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver AccessZeroNKlassHitsProtectionZone no_coh_no_cds
 */

/*
 * @test id=no_coh_cds
 * @summary Test that dereferencing a Klass that is the result of a decode(0) crashes accessing the nKlass guard zone
 * @requires vm.cds & vm.bits == 64 & vm.debug == true & vm.flagless
 * @requires os.family != "aix"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver AccessZeroNKlassHitsProtectionZone no_coh_cds
 */

/*
 * @test id=coh_no_cds
 * @summary Test that dereferencing a Klass that is the result of a decode(0) crashes accessing the nKlass guard zone
 * @requires vm.bits == 64 & vm.debug == true & vm.flagless
 * @requires os.family != "aix"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver AccessZeroNKlassHitsProtectionZone coh_no_cds
 */

/*
 * @test id=coh_cds
 * @summary Test that dereferencing a Klass that is the result of a decode(0) crashes accessing the nKlass guard zone
 * @requires vm.cds & vm.bits == 64 & vm.debug == true & vm.flagless
 * @requires os.family != "aix"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver AccessZeroNKlassHitsProtectionZone coh_cds
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;

// Test that dereferencing a Klass that is the result of a narrowKlass=0 will give us immediate crashes
// that hit the protection zone at encoding base.
public class AccessZeroNKlassHitsProtectionZone {

    private static OutputAnalyzer run_test(boolean COH, boolean CDS, String forceBaseString) throws IOException, SkippedException {
        ArrayList<String> args = new ArrayList<>();
        args.add("-Xbootclasspath/a:.");
        args.add("-XX:+UnlockDiagnosticVMOptions");
        args.add("-XX:+WhiteBoxAPI");
        args.add("-XX:CompressedClassSpaceSize=128m");
        args.add("-Xmx128m");
        args.add("-XX:-CreateCoredumpOnCrash");
        args.add("-Xlog:metaspace*");
        args.add("-Xlog:cds");
        if (COH) {
            args.add("-XX:+UseCompactObjectHeaders");
        }
        if (CDS) {
            args.add("-Xshare:on");
        } else {
            args.add("-Xshare:off");
            args.add("-XX:CompressedClassSpaceBaseAddress=" + forceBaseString);
        }
        args.add(AccessZeroNKlassHitsProtectionZone.class.getName());
        args.add("runwb");
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args.toArray(new String[0]));

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        return output;
    }

    private static void run_test(boolean COH, boolean CDS) throws IOException, SkippedException {
        // Notes:
        // We want to enforce zero-based encoding, to test the protection page in that case. For zero-based encoding,
        // protection page is at address zero, no need to test that.
        // If CDS is on, we never use zero-based, forceBase is ignored.
        // If CDS is off, we use forceBase to (somewhat) reliably force the encoding base to beyond 32G,
        // in order to prevent zero-based encoding. Since that may fail, we try several times.
        OutputAnalyzer output = null;
        long forceBase = -1;
        if (CDS) {
            output = run_test(COH, CDS, "");
            // Not all distributions build COH archives. We tolerate that.
            if (COH) {
                String s = output.firstMatch("Specified shared archive file not found .*coh.jsa");
                if (s != null) {
                    throw new SkippedException("Failed to find COH archive, was it not built? Skipping test.");
                }
            }
        } else {
            long g4 = 0x1_0000_0000L;
            long start = g4 * 8; // 32g
            long step = g4;
            long end = start + step * 16;
            for (forceBase = start; forceBase < end; forceBase += step) {
                String thisBaseString = String.format("0x%016X", forceBase).toLowerCase();
                output = run_test(COH, CDS, thisBaseString);
                if (output.contains("CompressedClassSpaceBaseAddress=" + thisBaseString + " given, but reserving class space failed.")) {
                    // try next one
                } else if (output.contains("Successfully forced class space address to " + thisBaseString)) {
                    break;
                } else {
                    throw new RuntimeException("Unexpected");
                }
            }
            if (forceBase >= end) {
                throw new SkippedException("Failed to force ccs to any of the given bases. Skipping test.");
            }
        }

        // Parse the encoding base from the output. In case of CDS, it depends on ASLR. Even in case of CDS=off, we want
        // to double-check it is the force address.
        String nKlassBaseString = output.firstMatch("Narrow klass base: 0x([0-9a-f]+)", 1);
        if (nKlassBaseString == null) {
            throw new RuntimeException("did not find Narrow klass base in log output");
        }
        long nKlassBase = Long.valueOf(nKlassBaseString, 16);

        if (!CDS && nKlassBase != forceBase) {
            throw new RuntimeException("Weird - we should have mapped at force base"); // .. otherwise we would have skipped out above
        }
        if (nKlassBase == 0) {
            throw new RuntimeException("We should not be running zero-based at this point.");
        }

        // Calculate the expected crash address pattern. The precise crash address is unknown, but should be located
        // in the lower part of the guard page following the encoding base. We just accept any address matching the
        // upper 52 digits (leaving 4K = 12 bits = 4 nibbles of wiggle room)
        String expectedCrashAddressString = nKlassBaseString.substring(0, nKlassBaseString.length() - 3);

        // output from whitebox function: Klass* should point to encoding base
        output.shouldMatch("WB_DecodeNKlassAndAccessKlass: nk 0 k 0x" + nKlassBaseString);

        // Then, we should have crashed
        output.shouldNotHaveExitValue(0);
        output.shouldContain("# A fatal error has been detected");

        // The hs-err file should contain a reference to the nKlass protection zone, like this:
        // "RDI=0x0000000800000000 points into nKlass protection zone"
        File hsErrFile = HsErrFileUtils.openHsErrFileFromOutput(output);

        ArrayList<Pattern> hsErrPatternList = new ArrayList<>();
        hsErrPatternList.add(Pattern.compile(".*(SIGBUS|SIGSEGV|EXCEPTION_ACCESS_VIOLATION).*"));

        hsErrPatternList.add(Pattern.compile(".*siginfo:.*" + expectedCrashAddressString + ".*"));
        hsErrPatternList.add(Pattern.compile(".*" + expectedCrashAddressString + ".*points into nKlass protection zone.*"));
        Pattern[] hsErrPattern = hsErrPatternList.toArray(new Pattern[0]);
        HsErrFileUtils.checkHsErrFileContent(hsErrFile, hsErrPattern, true);
    }

    enum Argument { runwb, no_coh_no_cds, no_coh_cds, coh_no_cds, coh_cds };
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Expecting one argument");
        }
        Argument arg = Argument.valueOf(args[0]);
        System.out.println(arg);
        switch (arg) {
            case runwb -> WhiteBox.getWhiteBox().decodeNKlassAndAccessKlass(0);
            case no_coh_no_cds -> run_test(false, false);
            case no_coh_cds -> run_test(false, true);
            case coh_no_cds -> run_test(true, false);
            case coh_cds -> run_test(true, true);
        }
    }
}
