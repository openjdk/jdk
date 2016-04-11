/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test CdsDifferentObjectAlignment
 * @summary Testing CDS (class data sharing) using varying object alignment.
 *          Using different object alignment for each dump/load pair.
 *          This is a negative test; using  object alignment for loading that
 *          is different from object alignment for creating a CDS file
 *          should fail when loading.
 * @library /testlibrary
 * @bug 8025642
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import jdk.test.lib.*;

public class CdsDifferentObjectAlignment {
    public static void main(String[] args) throws Exception {
        String nativeWordSize = System.getProperty("sun.arch.data.model");
        if (!Platform.is64bit()) {
            System.out.println("ObjectAlignmentInBytes for CDS is only " +
                "supported on 64bit platforms; this plaform is " +
                nativeWordSize);
            System.out.println("Skipping the test");
        } else {
            createAndLoadSharedArchive(16, 64);
            createAndLoadSharedArchive(64, 32);
        }
    }


    // Parameters are object alignment expressed in bytes
    private static void
    createAndLoadSharedArchive(int createAlignment, int loadAlignment)
    throws Exception {
        String createAlignmentArgument = "-XX:ObjectAlignmentInBytes=" +
            createAlignment;
        String loadAlignmentArgument = "-XX:ObjectAlignmentInBytes=" +
            loadAlignment;
        String filename = "./CdsDifferentObjectAlignment" + createAlignment + ".jsa";

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + filename,
            "-Xshare:dump",
            createAlignmentArgument);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + filename,
            "-Xshare:on",
            loadAlignmentArgument,
            "-version");

        output = new OutputAnalyzer(pb.start());
        String expectedErrorMsg =
            String.format(
            "The shared archive file's ObjectAlignmentInBytes of %d " +
            "does not equal the current ObjectAlignmentInBytes of %d",
            createAlignment,
            loadAlignment);

        try {
            output.shouldContain(expectedErrorMsg);
        } catch (RuntimeException e) {
            output.shouldContain("Unable to use shared archive");
        }
        output.shouldHaveExitValue(1);
    }
}
