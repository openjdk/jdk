/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test ArchiveDoesNotExist
 * @summary Test how VM handles "file does not exist" situation while
 *          attempting to use CDS archive. JVM should exit gracefully
 *          when sharing mode is ON, and continue w/o sharing if sharing
 *          mode is AUTO.
 * @library /testlibrary
 * @run main ArchiveDoesNotExist
 */

import com.oracle.java.testlibrary.*;
import java.io.File;

public class ArchiveDoesNotExist {
    public static void main(String[] args) throws Exception {
        String fileName = "test.jsa";

        File cdsFile = new File(fileName);
        if (cdsFile.exists())
            throw new RuntimeException("Test error: cds file already exists");

        // Sharing: on
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
           "-XX:+UnlockDiagnosticVMOptions",
           "-XX:SharedArchiveFile=./" + fileName,
           "-Xshare:on",
           "-version");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Specified shared archive not found");
        output.shouldHaveExitValue(1);

        // Sharing: auto
        pb = ProcessTools.createJavaProcessBuilder(
           "-XX:+UnlockDiagnosticVMOptions",
           "-XX:SharedArchiveFile=./" + fileName,
           "-Xshare:auto",
           "-version");

        output = new OutputAnalyzer(pb.start());
        output.shouldMatch("(java|openjdk) version");
        output.shouldNotContain("sharing");
        output.shouldHaveExitValue(0);
    }
}
