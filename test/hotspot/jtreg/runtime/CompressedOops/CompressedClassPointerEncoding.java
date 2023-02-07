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


// x64

/*
 * @test id=x64-area-beyond-encoding-range-use-xor
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding x64-area-beyond-encoding-range-use-xor
 */

/*
 * @test id=x64-area-partly-within-encoding-range-use-add
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding x64-area-partly-within-encoding-range-use-add
 */

/*
 * @test id=x64-area-within-encoding-range-use-zero
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding x64-area-within-encoding-range-use-zero
 */

/*
 * @test id=x64-area-far-out-no-low-bits-use-xor
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding x64-area-far-out-no-low-bits-use-xor
 */

/*
 * @test id=x64-area-far-out-with-low-bits-use-add
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding x64-area-far-out-with-low-bits-use-add
 */

// aarch64

/*
 * @test id=aarch64-xor
 * @requires os.arch=="aarch64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding aarch64-xor
 */

/*
 * @test id=aarch64-movk-1
 * @requires os.arch=="aarch64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding aarch64-movk-1
 */

/*
 * @test id=aarch64-movk-2
 * @requires os.arch=="aarch64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding aarch64-movk-2
 */

/*
 * @test id=aarch64-movk-3
 * @requires os.arch=="aarch64"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointerEncoding aarch64-movk-3
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

public class CompressedClassPointerEncoding {

    final static long M = 1024 * 1024;
    final static long G = 1024 * M;

    final static int narrowKlassBitSize = 22;
    final static int narrowKlassShift = 9;
    final static long narrowKlassValueSpan = 1L << narrowKlassBitSize;
    final static long encodingRangeSpan = narrowKlassValueSpan << narrowKlassShift;

    final static long defaultCCSSize = 128 * M;
    final static long ccsGranularity = 16 * M; // root chunk size



    static class TestDetails {
        public final String name;
        public final long ccsBaseAddress;
        public final long compressedClassSpaceSize;
        public final long expectedEncodingBase;
        public final String expectedEncodingMode;
        // The test relies on -XX:CompressedClassSpaceBaseAddress to force the CCS base address to a certain value.
        // That can fail due to ASLR. If tolerateCCSMappingError is true, we tolerate this.
        public final boolean tolerateCCSMappingError;

        public TestDetails(String name, long ccsBaseAddress,
                           long expectedEncodingBase, String expectedEncodingMode, boolean tolerateCCSMappingError) {
            this.name = name;
            this.ccsBaseAddress = ccsBaseAddress;
            this.compressedClassSpaceSize = defaultCCSSize;
            this.expectedEncodingBase = expectedEncodingBase;
            this.expectedEncodingMode = expectedEncodingMode;
            this.tolerateCCSMappingError = tolerateCCSMappingError;
        }
    };

    static void runTestWithName(String name) throws IOException {

        // How this works:
        // 1) we enforce a CCS base address with -XX:CompressedClassSpaceBaseAddress. This bypasses the automatic
        //    CCS placement we do in Metaspace/CDS initialization; CCS begins at that address.
        // 2) We set CCS size to 128M.
        // 3) We expect the zero-based encoding range to be [0...<max encoding span>)
        // 4) Depending on where CCS is located wrt the zero-based encoding range, we expect different encoding
        //    platform dependent mechanisms. For example,
        //    a) If CCS range fits completely into the zero-based encoding range, we expect zero-based encoding
        //    b) If CCs range lies partly or completely outside the zero-based encoding range, zero-base encoding
        //       cannot work. Typically, if CCS base address and Klass* offset range can be appended, something like
        //       xor would be used, otherwise some for of add+shift

        String expectedMode;
        long ccsBaseAddress;
        long expectedEncodingRangeStart;
        boolean tolerateMappingFailure = false;
        switch (name) {

            //////////////////////////////////////////////////////////////////////////
            ////// x64 ///////////////////////////////////////////////////////////////

            case "x64-area-beyond-encoding-range-use-xor":
                // CCS base starts just (beyond) zero-based encoding range.
                // - Encoding cannot be zero-based
                // - We should be able to use xor mode since CCS base address and Klass* offset range don't intersect
                ccsBaseAddress = encodingRangeSpan;
                expectedEncodingRangeStart = ccsBaseAddress;
                expectedMode = "xor";
                break;
            case "x64-area-partly-within-encoding-range-use-add":
                // CCS partly contained within zero-based encoding range, but last part (a single granule) outside.
                // - Encoding cannot be zero-based
                // - We cannot use xor since CCS base address intersects with max left-shifted narrow Klass pointer
                ccsBaseAddress = encodingRangeSpan - defaultCCSSize + ccsGranularity;
                expectedEncodingRangeStart = ccsBaseAddress;
                expectedMode = "add";
                break;
            case "x64-area-within-encoding-range-use-zero":
                // CCS fully contained within zero-based encoding range
                // - Encoding can be zero-based
                ccsBaseAddress = encodingRangeSpan - defaultCCSSize;
                expectedEncodingRangeStart = 0;
                expectedMode = "zero";
                break;
            case "x64-area-far-out-no-low-bits-use-xor":
                // CCS located far beyond zero-based encoding range, at a nicely aligned base.
                // - Encoding cannot be zero-based
                // - We should be able to use xor mode since CCS base address does not intersect with
                //   max left-shifted narrow Klass pointer
                ccsBaseAddress = encodingRangeSpan * 4;
                expectedEncodingRangeStart = ccsBaseAddress;
                expectedMode = "xor";
                break;
            case "x64-area-far-out-with-low-bits-use-add":
                // CCS located far beyond zero-based encoding range, but address is unfit for xor mode.
                // - Encoding cannot be zero-based
                // - We cannot use xor since CCS base address has lower bits set that intersect with
                //   max left-shifted narrow Klass pointer
                ccsBaseAddress = (encodingRangeSpan * 4) + ccsGranularity;
                expectedEncodingRangeStart = ccsBaseAddress;
                expectedMode = "add";
                break;

            //////////////////////////////////////////////////////////////////////////
            ////// aarch64 ///////////////////////////////////////////////////////////

            case "aarch64-xor":
                // CCS with a base which is a valid immediate, does not intersect the uncompressed klass pointer bits,
                // should use xor+shift
                ccsBaseAddress = 0x1000000000L;
                expectedEncodingRangeStart = ccsBaseAddress;
                expectedMode = "xor";
                break;

            // Attempt to test movk:
            // Addresses that would need movk mode are quite high for lilliput (9 bit shift), with the lowest
            // needing 40bits. Therefore the following tests may fail because CCS cannot be mapped. This depends on
            // how the kernel was compiled (39, 42 or 48 bit virtual addresses).
            // Therefore we tolerate mapping failures for the following tests.
            case "aarch64-movk-1":
                ccsBaseAddress = 0x00000a0000000000L;
                expectedEncodingRangeStart = ccsBaseAddress;
                expectedMode = "movk";
                tolerateMappingFailure = true;
                break;

            case "aarch64-movk-2":
                ccsBaseAddress = 0x120000000000L;
                expectedEncodingRangeStart = ccsBaseAddress;
                expectedMode = "movk";
                tolerateMappingFailure = true;
                break;

            case "aarch64-movk-3":
                ccsBaseAddress = 0x160000000000L;
                expectedEncodingRangeStart = ccsBaseAddress;
                expectedMode = "movk";
                tolerateMappingFailure = true;
                break;

            default:
                throw new RuntimeException("Bad test name: " + name);
        }

        TestDetails details = new TestDetails(name, ccsBaseAddress, expectedEncodingRangeStart, expectedMode, tolerateMappingFailure);
        runTest(details);
    }

    ;

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

        // Replace:
        // $1 with force base address
        // $2 with compressed class space size
        String[] vmOptionsTemplate = new String[] {
                "-XX:CompressedClassSpaceBaseAddress=$1",
                "-XX:CompressedClassSpaceSize=$2",
                "-Xshare:off",                         // Disable CDS
                "-Xlog:metaspace*",                    // for analysis
                "-XX:+PrintMetaspaceStatisticsAtExit", // for analysis
                "-version"
        };

        String ccsBaseAddressAsHex = String.format("0x%016x", details.ccsBaseAddress);

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

        if (details.tolerateCCSMappingError) {
            String template = "CompressedClassSpaceBaseAddress=$1 given, but reserving class space failed";
            String pat = replacePlaceholdersInString(template, ccsBaseAddressAsHex);
            if (output.getStdout().contains(pat)) {
                throw new SkippedException("Skipping test: failed to force CCS to base address " + ccsBaseAddressAsHex);
            }
        }

        // Replace:
        // $1 with expected ccs base address (extended hex printed)
        // $2 with expected encoding base (extended hex printed)
        // $3 with expected encoding shift
        // $4 with expected encoding range
        // $5 with expected encoding mode
        String[] expectedOutputTemplate = new String[] {
                "Successfully forced class space address to $1",
                "CDS archive(s) not mapped",
                "CompressedClassSpaceSize: 128.00 MB",
                "KlassAlignmentInBytes: 512",
                "KlassEncodingMetaspaceMax: 2.00 GB",
                "Narrow klass base: $2, Narrow klass shift: $3, Narrow klass range: $4, Encoding mode $5"
        };

        String expectedOutput[] = replacePlaceholdersInArray(expectedOutputTemplate,
                ccsBaseAddressAsHex, // $1
                String.format("0x%016x", details.expectedEncodingBase), // $2
                Long.toString(narrowKlassShift), // $3
                String.format("0x%x", encodingRangeSpan), // $4
                details.expectedEncodingMode  // $5
        );

        String[] lines = output.asLines().toArray(new String[0]);
        int found = 0;
        int lineno = 0;
        while (lineno < lines.length && found < expectedOutput.length) {
            String pat = expectedOutput[found];
            String line = lines[lineno];
            if (line.contains(pat)) {
                System.out.println("Found: " + pat + " at line " + lineno);
                found ++;
            }
            lineno++;
        }
        if (found < expectedOutput.length) {
            throw new RuntimeException("Not all expected pattern found. First missing: " + expectedOutput[found]);
        }

        output.shouldHaveExitValue(0);
    }

    public static void main(String[] args) throws Exception {
        runTestWithName(args[0]);
    }
}
