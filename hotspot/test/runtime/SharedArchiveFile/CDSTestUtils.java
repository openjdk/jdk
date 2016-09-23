/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import jdk.test.lib.process.OutputAnalyzer;


// This class contains common test utilities for CDS testing
public class CDSTestUtils {

    // check result of 'dump' operation
    public static void checkDump(OutputAnalyzer output, String... extraMatches)
        throws Exception {

        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);

        for (String match : extraMatches) {
            output.shouldContain(match);
        }
    }


    // check the output for indication that mapping of the archive failed
    public static boolean isUnableToMap(OutputAnalyzer output) {
        String outStr = output.getOutput();
        if ((output.getExitValue() == 1) && (
            outStr.contains("Unable to reserve shared space at required address") ||
            outStr.contains("Unable to map ReadOnly shared space at required address") ||
            outStr.contains("Unable to map ReadWrite shared space at required address") ||
            outStr.contains("Unable to map MiscData shared space at required address") ||
            outStr.contains("Unable to map MiscCode shared space at required address") ||
            outStr.contains("Unable to map shared string space at required address") ||
            outStr.contains("Could not allocate metaspace at a compatible address") ||
            outStr.contains("Unable to allocate shared string space: range is not within java heap") ))
        {
            return true;
        }

        return false;
    }

    // check result of 'exec' operation, that is when JVM is run using the archive
    public static void checkExec(OutputAnalyzer output, String... extraMatches) throws Exception {
        if (isUnableToMap(output)) {
            System.out.println("Unable to map shared archive: test did not complete; assumed PASS");
            return;
        }
        output.shouldContain("sharing");
        output.shouldHaveExitValue(0);

        for (String match : extraMatches) {
            output.shouldContain(match);
        }
    }


    // get the file object for the test artifact
    private static File getTestArtifactFile(String prefix, String name) {
        File dir = new File(System.getProperty("test.classes", "."));
        return new File(dir, prefix + name);
    }


    // create file containing the specified class list
    public static File makeClassList(String testCaseName, String classes[])
        throws Exception {

        File classList = getTestArtifactFile(testCaseName, "test.classlist");
        FileOutputStream fos = new FileOutputStream(classList);
        PrintStream ps = new PrintStream(fos);

        addToClassList(ps, classes);

        ps.close();
        fos.close();

        return classList;
    }


    private static void addToClassList(PrintStream ps, String classes[])
        throws IOException
    {
        if (classes != null) {
            for (String s : classes) {
                ps.println(s);
            }
        }
    }

}
