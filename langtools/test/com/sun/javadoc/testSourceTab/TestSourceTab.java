/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4510979
 * @summary Test to make sure that the source documentation is indented properly
 * when -linksourcetab is used.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestSourceTab
 * @run main TestSourceTab
 */

import java.io.*;

public class TestSourceTab extends JavadocTester {

    private static final String BUG_ID = "4510979";
    private static final String TMP_SRC_DIR = "tmpSrc";
    private static final String OUTPUT_DIR1 = BUG_ID + "-tabLengthEight";
    private static final String OUTPUT_DIR2 = BUG_ID + "-tabLengthFour";
    private static final String[][] TEST = NO_TEST;
    private static final String[][] NEGATED_TEST = NO_TEST;

    //Run Javadoc on a source file with that is indented with a single tab per line
    private static final String[] ARGS1 =
        new String[] {
            "-d", OUTPUT_DIR1, "-sourcepath", TMP_SRC_DIR,
            "-notimestamp", "-linksource", TMP_SRC_DIR + FS + "SingleTab" + FS + "C.java"
        };

    //Run Javadoc on a source file with that is indented with a two tab per line
    //If we double the tabs and decrease the tab length by a half, the output should
    //be the same as the one generated above.
    private static final String[] ARGS2 =
        new String[] {
            "-d", OUTPUT_DIR2, "-sourcepath", TMP_SRC_DIR,
            "-notimestamp", "-sourcetab", "4", TMP_SRC_DIR + FS + "DoubleTab" + FS + "C.java"
        };

    //Files to diff
    private static final String[][] FILES_TO_DIFF = {
        {OUTPUT_DIR1 + FS + "src-html" + FS + "C.html",
         OUTPUT_DIR2 + FS + "src-html" + FS + "C.html"
        },
        {OUTPUT_DIR1 + FS + "C.html",
         OUTPUT_DIR2 + FS + "C.html"
        }

    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) throws IOException {
        TestSourceTab tester = new TestSourceTab();
        run(tester, ARGS1, TEST, NEGATED_TEST);
        run(tester, ARGS2, TEST, NEGATED_TEST);
        tester.runDiffs(FILES_TO_DIFF);
    }

    TestSourceTab() throws IOException {
        initTabs(new File(SRC_DIR), new File(TMP_SRC_DIR));
    }

    void initTabs(File from, File to) throws IOException {
        for (File f: from.listFiles()) {
            File t = new File(to, f.getName());
            if (f.isDirectory()) {
                initTabs(f, t);
            } else if (f.getName().endsWith(".java")) {
                write(t, read(f).replace("\\t", "\t"));
            }
        }
    }

    String read(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader in = new BufferedReader(new FileReader(f));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
        } finally {
            in.close();
        }
        return sb.toString();
    }

    void write(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        Writer out = new FileWriter(f);
        try {
            out.write(s);
        } finally {
            out.close();
        }
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
}
