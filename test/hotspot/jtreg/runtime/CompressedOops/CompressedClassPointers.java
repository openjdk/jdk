/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8024927
 * @summary Testing address of compressed class pointer space as best as possible.
 * @requires vm.bits == 64 & !vm.graal.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedClassPointers
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

public class CompressedClassPointers {

    static final String logging_option = "-Xlog:gc+metaspace=trace,cds=trace";

    // Returns true if we are to test the narrow klass base; we only do this on
    // platforms where we can be reasonably shure that we get reproducable placement).
    static boolean testNarrowKlassBase() {
        if (Platform.isWindows() || Platform.isPPC()) {
            return false;
        }
        return true;

    }

    // CDS off, small heap, ccs size default (1G)
    // A small heap should allow us to place the ccs within the lower 32G and thus allow zero based encoding.
    public static void smallHeapTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedBaseAddress=8g",
            "-Xmx128m",
            logging_option,
            "-Xshare:off",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (testNarrowKlassBase()) {
            output.shouldContain("Narrow klass base: 0x0000000000000000");
        }
        output.shouldHaveExitValue(0);
    }

    // CDS off, small heap, ccs size explicitely set to 1G
    // A small heap should allow us to place the ccs within the lower 32G and thus allow zero based encoding.
    public static void smallHeapTestWith1G() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:CompressedClassSpaceSize=1g",
            "-Xmx128m",
            logging_option,
            "-Xshare:off",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (testNarrowKlassBase()) {
            output.shouldContain("Narrow klass base: 0x0000000000000000, Narrow klass shift: 3");
        }
        output.shouldHaveExitValue(0);
    }

    // CDS off, a very large heap, ccs size left to 1G default.
    // We expect the ccs to be mapped somewhere far beyond the heap, such that it is not possible
    // to use zero based encoding.
    public static void largeHeapTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UnlockExperimentalVMOptions",
            "-Xmx30g",
            "-XX:-UseAOT", // AOT explicitly set klass shift to 3.
            logging_option,
            "-Xshare:off",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (testNarrowKlassBase()) {
            output.shouldNotContain("Narrow klass base: 0x0000000000000000");
            output.shouldContain("Narrow klass shift: 0");
        }
        output.shouldHaveExitValue(0);
    }

    // Using large paged heap, metaspace uses small pages.
    public static void largePagesForHeapTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-Xmx128m",
                "-XX:+UseLargePages",
                logging_option,
                "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (testNarrowKlassBase()) {
            output.shouldContain("Narrow klass base:");
        }
        output.shouldHaveExitValue(0);
    }

    // Using large pages for heap and metaspace.
    // Note that this is still unexciting since the compressed class space always uses small pages;
    // UseLargePagesInMetaspace only affects non-class metaspace.
    public static void largePagesForHeapAndMetaspaceTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-Xmx128m",
            "-XX:+UseLargePages", "-XX:+UseLargePagesInMetaspace",
            logging_option,
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (testNarrowKlassBase()) {
            output.shouldContain("Narrow klass base:");
        }
        output.shouldHaveExitValue(0);
    }

    public static void heapBaseMinAddressTest() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:HeapBaseMinAddress=1m",
            "-Xlog:gc+heap+coops=debug",
            "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("HeapBaseMinAddress must be at least");
        output.shouldHaveExitValue(0);
    }

    public static void sharingTest() throws Exception {
        // Test small heaps
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./CompressedClassPointers.jsa",
            "-Xmx128m",
            "-XX:SharedBaseAddress=8g",
            "-XX:+VerifyBeforeGC",
            "-Xshare:dump",
            "-Xlog:cds,gc+heap+coops=debug");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (output.firstMatch("Shared spaces are not supported in this VM") != null) {
            return;
        }
        try {
          output.shouldContain("Loading classes to share");
          output.shouldHaveExitValue(0);

          pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./CompressedClassPointers.jsa",
            "-Xmx128m",
            "-XX:SharedBaseAddress=8g",
            "-Xlog:gc+heap+coops=debug",
            "-Xshare:on",
            "-version");
          output = new OutputAnalyzer(pb.start());
          output.shouldContain("sharing");
          output.shouldHaveExitValue(0);

        } catch (RuntimeException e) {
          output.shouldContain("Unable to use shared archive");
          output.shouldHaveExitValue(1);
        }
    }

    public static void smallHeapTestNoCoop() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:-UseCompressedOops",
            "-XX:+UseCompressedClassPointers",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedBaseAddress=8g",
            "-Xmx128m",
            "-Xlog:gc+metaspace=trace",
            "-Xshare:off",
            "-Xlog:cds=trace",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Narrow klass base: 0x0000000000000000");
        output.shouldHaveExitValue(0);
    }

    public static void smallHeapTestWith1GNoCoop() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:-UseCompressedOops",
            "-XX:+UseCompressedClassPointers",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:CompressedClassSpaceSize=1g",
            "-Xmx128m",
            "-Xlog:gc+metaspace=trace",
            "-Xshare:off",
            "-Xlog:cds=trace",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Narrow klass base: 0x0000000000000000");
        if (!Platform.isAArch64()) {
            // Currently relax this test for Aarch64.
            output.shouldContain("Narrow klass shift: 0");
        }
        output.shouldHaveExitValue(0);
    }

    public static void largeHeapTestNoCoop() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:-UseCompressedOops",
            "-XX:+UseCompressedClassPointers",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UnlockExperimentalVMOptions",
            "-Xmx30g",
            "-XX:-UseAOT", // AOT explicitly set klass shift to 3.
            "-Xlog:gc+metaspace=trace",
            "-Xshare:off",
            "-Xlog:cds=trace",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Narrow klass base: 0x0000000000000000");
        if (!Platform.isAArch64()) {
            // Currently relax this test for Aarch64.
            output.shouldContain("Narrow klass shift: 0");
        }
        output.shouldHaveExitValue(0);
    }

    public static void largePagesTestNoCoop() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:-UseCompressedOops",
            "-XX:+UseCompressedClassPointers",
            "-XX:+UnlockDiagnosticVMOptions",
            "-Xmx128m",
            "-XX:+UseLargePages",
            "-Xlog:gc+metaspace=trace",
            "-XX:+VerifyBeforeGC", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Narrow klass base:");
        output.shouldHaveExitValue(0);
    }

    public static void heapBaseMinAddressTestNoCoop() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:-UseCompressedOops",
            "-XX:+UseCompressedClassPointers",
            "-XX:HeapBaseMinAddress=1m",
            "-Xlog:gc+heap+coops=debug",
            "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("HeapBaseMinAddress must be at least");
        output.shouldHaveExitValue(0);
    }

    public static void sharingTestNoCoop() throws Exception {
        // Test small heaps
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:-UseCompressedOops",
            "-XX:+UseCompressedClassPointers",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./CompressedClassPointers.jsa",
            "-Xmx128m",
            "-XX:SharedBaseAddress=8g",
            "-XX:+VerifyBeforeGC",
            "-Xshare:dump",
            "-Xlog:cds,gc+heap+coops=debug");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (output.firstMatch("Shared spaces are not supported in this VM") != null) {
            return;
        }
        try {
          output.shouldContain("Loading classes to share");
          output.shouldHaveExitValue(0);

          pb = ProcessTools.createJavaProcessBuilder(
            "-XX:-UseCompressedOops",
            "-XX:+UseCompressedClassPointers",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./CompressedClassPointers.jsa",
            "-Xmx128m",
            "-XX:SharedBaseAddress=8g",
            "-Xlog:gc+heap+coops=debug",
            "-Xshare:on",
            "-version");
          output = new OutputAnalyzer(pb.start());
          output.shouldContain("sharing");
          output.shouldHaveExitValue(0);

        } catch (RuntimeException e) {
          output.shouldContain("Unable to use shared archive");
          output.shouldHaveExitValue(1);
        }
    }

    public static void main(String[] args) throws Exception {
        smallHeapTest();
        smallHeapTestWith1G();
        largeHeapTest();
        largePagesForHeapTest();
        largePagesForHeapAndMetaspaceTest();
        heapBaseMinAddressTest();
        sharingTest();

        if (!Platform.isOSX()) {
            // Testing compressed class pointers without compressed oops.
            // This is only possible if the platform supports it. Notably,
            // on macOS, when compressed oops is disabled and the heap is
            // given an arbitrary address, that address occasionally collides
            // with where we would ideally have placed the compressed class
            // space. Therefore, macOS is omitted for now.
            smallHeapTestNoCoop();
            smallHeapTestWith1GNoCoop();
            largeHeapTestNoCoop();
            largePagesTestNoCoop();
            heapBaseMinAddressTestNoCoop();
            sharingTestNoCoop();
        }
    }
}
