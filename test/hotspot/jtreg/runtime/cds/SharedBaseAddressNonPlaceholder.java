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

/**
 * @test SharedBaseAddressNonPlaceholder
 * @summary Test legacy (non-placeholder) archive reservation path.
 * @requires vm.cds
 * @requires vm.bits == 64
 * @requires vm.debug
 * @library /test/lib
 * @run driver SharedBaseAddressNonPlaceholder
*/

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

public class SharedBaseAddressNonPlaceholder {
    static final boolean skipUncompressedOopsTests;
    static boolean checkSkipUncompressedOopsTests(String prop) {
        String opts = System.getProperty(prop);
        return opts.contains("+AOTClassLinking") &&
               opts.matches(".*[+]Use[A-Za-z]+GC.*") && !opts.contains("+UseG1GC");
    }
    static {
        // AOTClassLinking requires the ability to load archived heap objects. However,
        // due to JDK-8341371, only G1GC supports loading archived heap objects
        // with uncompressed oops.
        skipUncompressedOopsTests =
            checkSkipUncompressedOopsTests("test.vm.opts") ||
            checkSkipUncompressedOopsTests("test.java.opts");
    }

    public static void testNonPlaceholderPath() throws Exception {
        // Try to increase chances of success at requested address.
        String filename = "SharedBaseAddress-non-placeholder.jsa";
        CDSOptions opts = (new CDSOptions())
                    .setArchiveName(filename)
                    .addPrefix("-Xlog:cds=debug")
                    .addPrefix("-Xlog:cds+reloc=debug")
                    .addPrefix("-Xlog:aot=info")
                    .addPrefix("-Xmx128m")
                    .addPrefix("-XX:CompressedClassSpaceSize=32m")
                    .addPrefix("-XX:-UseCompressedOops")
                    .addPrefix("-XX:+UnlockDiagnosticVMOptions")
                    .addPrefix("-XX:ArchiveRelocationMode=0")// In debug mode this defaults to relocation fallback
                    .addPrefix("-XX:+IgnoreUnrecognizedVMOptions")
                    .addPrefix("-XX:-TestAOTPlaceholders");// Exercise the non-placeholder, non-fallback paths
        CDSTestUtils.createArchiveAndCheck(opts);
        OutputAnalyzer out = CDSTestUtils.runWithArchiveAndCheck(opts);
        // If we succeeded at the requested address, ensure the non-placeholder path was taken.
        if (!out.getOutput().contains("Try to map archive(s) at an alternative address")) {
            out.shouldContain("Reserved archive_space_rs");
            out.shouldNotContain("Placeholders allocated successfully");
        }
    }

    public static void main(String[] args) throws Exception {
        if (skipUncompressedOopsTests) {
            throw new SkippedException("Test skipped due to JDK-8341371");
        }
        testNonPlaceholderPath();
    }
}
