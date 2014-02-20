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
 * @ignore 8032222
 * @test CdsWriteError
 * @summary Test how VM handles situation when it is impossible to write the
 *          CDS archive. VM is expected to exit gracefully and display the
 *          correct reason for the error.
 * @library /testlibrary
 * @run main CdsWriteError
 * @bug 8032222
 */

import com.oracle.java.testlibrary.*;
import java.io.File;

public class CdsWriteError {
    public static void main(String[] args) throws Exception {

        if (Platform.isWindows()) {
            System.out.println("This test is ignored on Windows. This test " +
                "manipulates folder writable attribute, which is known to be " +
                "often ignored by Windows");

            return;
        }

        String folderName = "tmp";
        String fileName = folderName + File.separator + "empty.jsa";

        // create an empty archive file and make it read only
        File folder = new File(folderName);
        if (!folder.mkdir())
            throw new RuntimeException("Error when creating a tmp folder");

        File cdsFile = new File(fileName);
        if (!cdsFile.createNewFile())
            throw new RuntimeException("Error when creating an empty CDS file");
        if (!cdsFile.setWritable(false))
            throw new RuntimeException("Error: could not set writable attribute on cds file");
        if (!folder.setWritable(false))
            throw new RuntimeException("Error: could not set writable attribute on the cds folder");

        try {
           ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
             "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=./" + fileName, "-Xshare:dump");

            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("Unable to create shared archive file");
            output.shouldHaveExitValue(1);
        } finally {
            // doing this, just in case, to make sure that files can be deleted by the harness
            // on any subsequent run
            folder.setWritable(true);
            cdsFile.setWritable(true);
        }
    }
}

