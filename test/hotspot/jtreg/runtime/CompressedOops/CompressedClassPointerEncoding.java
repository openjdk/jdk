/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

// These tests test that narrow Klass pointer encoding/decoding work.
//
// Note that we do not enforce the encoding base directly. We enforce base and size of the compressed class space.
// The hotspot then decides on the best encoding range and scheme to chose for the given range.
//
// So what we really test here is that for a given range-to-encode:
//  - the chosen encoding range and architecture-specific mode makes sense - e.g. if range fits into low address
//    space, use base=0 and zero-based encoding.
//  - and that the chosen encoding actually works by starting a simple program which loads a bunch of classes.
//
//  In order for that to work, we have to switch of CDS. Switching off CDS means the hotspot choses the encoding base
//  based on the class space base address (we just know this - see CompressedKlassPointers::initialize() - and if this
//  changes, we may have to adapt this test).
//
//  Switching off CDS also means we use the class space much more fully. More Klass structures stored in that range
//  and we exercise the ability of Metaspace to allocate Klass structures with the correct alignment, compatible to
//  encoding.

/*
 * @test id=x64-area-beyond-encoding-range-use-xor
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.util.Arrays;

public class CompressedClassPointerEncoding {

    // Replace:
    // $1 with force base address
    // $2 with compressed class space size
    final static String[] vmOptionsTemplate = new String[] {
      "-XX:CompressedClassSpaceBaseAddress=$1",
      "-XX:CompressedClassSpaceSize=$2",
      "-Xshare:off",                         // Disable CDS
      "-Xlog:metaspace*",                    // for analysis
      "-XX:+PrintMetaspaceStatisticsAtExit", // for analysis
      "-version"
    };

    // Replace:
    // $1 with expected ccs base address (extended hex printed)
    // $2 with expected encoding base (extended hex printed)
    // $3 with expected encoding shift
    // $4 with expected encoding range
    // $5 with expected encoding mode
    final String[] expectedOutputTemplate = new String[] {
            ".*Sucessfully forced class space address to $1.*",
            ".*CDS archive(s) not mapped.*",
            ".*Narrow klass base: $2, Narrow klass shift: $3, Narrow klass range: $4, Encoding mode $5.*"
    };

    final static long M = 1024 * 1024;
    final static long G = 1024 * M;

    final static long expectedShift = 9;
    final static long expectedEncodingRange = 2 * G;
    final static long defaultCCSSize = 32 * M;

    enum EPlatform {
        // Add more where needed
        // (Note: this would be useful in Platform.java)
        linux_aarch64,
        linux_x64,
        unknown
    };

    static EPlatform getCurrentPlatform() {
        if (Platform.isAArch64() && Platform.isLinux()) {
            return EPlatform.linux_aarch64;
        } else if (Platform.isX64() && Platform.isLinux()) {
            return EPlatform.linux_x64;
        }
        return EPlatform.unknown;
    }

    static class TestDetails {
        public final EPlatform platform;
        public final String name;
        public final long[] baseAdressesToTry;
        public final long compressedClassSpaceSize;
        public final long expectedEncodingBase;
        public final String expectedEncodingMode;

        public TestDetails(EPlatform platform, String name, long[] baseAdressesToTry,
                           long compressedClassSpaceSize, long expectedEncodingBase, String expectedEncodingMode) {
            this.platform = platform;
            this.name = name;
            this.baseAdressesToTry = baseAdressesToTry;
            this.compressedClassSpaceSize = compressedClassSpaceSize;
            this.expectedEncodingBase = expectedEncodingBase;
            this.expectedEncodingMode = expectedEncodingMode;
        }

        // Simplified, common version: one base address (which we assume always works) and 32G ccs size
        public TestDetails(EPlatform platform, String name, long baseAdress,
                           long expectedEncodingBase, String expectedEncodingMode) {
            this(platform, name, new long[]{ baseAdress }, defaultCCSSize,
                 expectedEncodingBase, expectedEncodingMode);
        }
    };

    static TestDetails[] testDetails = new TestDetails[] {

            //////////////////////////////////////////////////////////////////////////
            ////// x64 ///////////////////////////////////////////////////////////////

            // CCS base beyond encoding range (base=2G). Base does does not intersect the uncompressed klass pointer
            // bits. Encoding cannot be zero, and we should use xor+shift mode.
            new TestDetails(EPlatform.linux_x64,
                    "x64-area-beyond-encoding-range-use-xor",
                    2 * G,
                    2 * G,
                    "xor"),

            // CCS partly contained in encoding range. We cannot use zero based encoding. We cannot use xor either,
            // since the first part of the ccs intersects the encoding range. Encoding hould use add+shift.
            /*
            new TestDetails(EPlatform.linux_x64,
                    "x64-area-partly-within-encoding-range-use-add",
                    0x7fc00000,
                    2 * G,
                    "add"),
            */

            // CCS (just) fully contained in encoding range (base=2G-ccs size). Expect zero-based encoding.
            new TestDetails(EPlatform.linux_x64,
                    "x64-area-within-encoding-range-use-zero",
                    0x7e000000, // 2G - 32M (ccs size)
                    0,
                    "zero"),

            // CCS located far beyond the zero-based limit. Base does not intersect with narrow Klass pointer bits.
            // We should use xor.
            new TestDetails(EPlatform.linux_x64,
                    "x64-area-far-out-no-low-bits-use-xor",
                    0x800000000L, // 32G
                    0x800000000L,
                    "xor"),

            // CCS located far beyond the zero-based limit. Base address intersects with narrow Klass pointer bits.
            // We should use add.
            /*
            new TestDetails(EPlatform.linux_x64,
                    "x64-area-far-out-with-low-bits-use-add",
                    0x800800000L, // 32G + 8M (4M is minimum ccs alignment)
                    0x800800000L,
                    "xor"),
            */

            //////////////////////////////////////////////////////////////////////////
            ////// aarch64 ///////////////////////////////////////////////////////////


            // CCS with a base which is a valid immediate, does not intersect the uncompressed klass pointer bits,
            // should use xor+shift
            new TestDetails(EPlatform.linux_aarch64,
                    "aarch64-area-beyond-encoding-range-base-valid-immediate-use-xor",
                    0x800000000L, // 32G
                    800000000L,
                    "xor")

            // ... add more

    };

    // Helper function. Given a string, replace $1 ... $n with
    // replacement_strings[0] ... replacement_strings[n]
    static private String replacePlaceholdersInString(String original, String ...replacement_strings) {
        String result = original;
        int repl_id = 1; // 1 based
        for (String replacement : replacement_strings) {
            String placeholder = "$" + repl_id;
            result = result.replace(placeholder, replacement);
            repl_id ++;
        }
        return result;
    }

    // Helper function. Given a string array, replace $1 ... $n with
    // replacement_strings[0] ... replacement_strings[n]
    static private String[] replacePlaceholdersInArray(String[] original, String ...replacement_strings) {
        String[] copy = new String[original.length];
        for (int n = 0; n < copy.length; n ++) {
            copy[n] = replacePlaceholdersInString(original[n], replacement_strings);
        }
        return copy;
    }

    static void runTest(TestDetails details) throws IOException {
        System.err.println("----------------------------------------------------");
        System.err.println("Running Test: " + details.name);
        System.err.println(details);

        long ccsBaseAddress = details.baseAdressesToTry[0];
        String ccsBaseAddressAsHex = String.format("0x%016x", ccsBaseAddress);

        // VM Options: replace:
        // $1 with force base address
        // $2 with compressed class space size
        String[] vmOptions = replacePlaceholdersInArray(vmOptionsTemplate,
                ccsBaseAddressAsHex,              // $1
                (details.compressedClassSpaceSize / M) + "M");    // $2

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(vmOptions);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        System.err.println("----------------------------------------------------");
        System.err.println(Arrays.toString(vmOptions));
        output.reportDiagnosticSummary();
        System.err.println("----------------------------------------------------");

        output.shouldHaveExitValue(0);

    }

    static void runTestsForPlatform(EPlatform platform) throws IOException {
        for (TestDetails details : testDetails) {
            if (details.platform == platform) {
                runTest(details);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        runTestsForPlatform(getCurrentPlatform());
    }
}
