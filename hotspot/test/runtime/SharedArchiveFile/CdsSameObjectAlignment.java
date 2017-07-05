/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test CdsSameObjectAlignment
 * @summary Testing CDS (class data sharing) using varying object alignment.
 *          Using same object alignment for each dump/load pair
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.*;

public class CdsSameObjectAlignment {
    public static void main(String[] args) throws Exception {
        String nativeWordSize = System.getProperty("sun.arch.data.model");
        if (!Platform.is64bit()) {
            System.out.println("ObjectAlignmentInBytes for CDS is only " +
                "supported on 64bit platforms; this plaform is " +
                nativeWordSize);
            System.out.println("Skipping the test");
        } else {
            dumpAndLoadSharedArchive(8);
            dumpAndLoadSharedArchive(16);
            dumpAndLoadSharedArchive(32);
            dumpAndLoadSharedArchive(64);
        }
    }

    private static void
    dumpAndLoadSharedArchive(int objectAlignmentInBytes) throws Exception {
        String objectAlignmentArg = "-XX:ObjectAlignmentInBytes="
            + objectAlignmentInBytes;
        System.out.println("dumpAndLoadSharedArchive(): objectAlignmentInBytes = "
            + objectAlignmentInBytes);

        // create shared archive
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./sample.jsa",
            "-Xshare:dump",
            objectAlignmentArg);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);


        // run using the shared archive
        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./sample.jsa",
            "-Xshare:on",
            objectAlignmentArg,
            "-version");

        output = new OutputAnalyzer(pb.start());

        try {
            output.shouldContain("sharing");
            output.shouldHaveExitValue(0);
        } catch (RuntimeException e) {
            // CDS uses absolute addresses for performance.
            // It will try to reserve memory at a specific address;
            // there is a chance such reservation will fail
            // If it does, it is NOT considered a failure of the feature,
            // rather a possible expected outcome, though not likely
            output.shouldContain("Could not allocate metaspace at a compatible address");
            output.shouldHaveExitValue(1);
        }
    }
}
