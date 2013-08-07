/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4979486
 * @summary  Make sure tool parses CR line separators properly.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestCRLineSeparator
 * @run main TestCRLineSeparator
 */

import java.io.*;
import java.util.*;

public class TestCRLineSeparator extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4979486-8014636";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", ".", "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "MyClass.html", "Line 1" + NL + " Line 2"}
    };

    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) throws Exception {
        initFiles(new File(SRC_DIR), new File("."), "pkg");
        TestCRLineSeparator tester = new TestCRLineSeparator();
        run(tester, ARGS, TEST, NEGATED_TEST);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }

    // recursively copy files from fromDir to toDir, replacing newlines
    // with \r
    static void initFiles(File fromDir, File toDir, String f) throws IOException {
        File from_f = new File(fromDir, f);
        File to_f = new File(toDir, f);
        if (from_f.isDirectory()) {
            to_f.mkdirs();
            for (String child: from_f.list()) {
                initFiles(from_f, to_f, child);
            }
        } else {
            List<String> lines = new ArrayList<String>();
            BufferedReader in = new BufferedReader(new FileReader(from_f));
            try {
                String line;
                while ((line = in.readLine()) != null)
                    lines.add(line);
            } finally {
                in.close();
            }
            BufferedWriter out = new BufferedWriter(new FileWriter(to_f));
            try {
                for (String line: lines) {
                    out.write(line);
                    out.write("\r");
                }
            } finally {
                out.close();
            }
        }
    }
}
