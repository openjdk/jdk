/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8005644
 * @summary set default max errs and max warns
 * @modules jdk.javadoc/com.sun.tools.doclets.standard
 */

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.RootDoc;

public class MaxWarns {
    public static void main(String... args) throws Exception {
        new MaxWarns().run();
    }

    void run() throws Exception {
        final int defaultMaxWarns = 100;
        File f = new File(System.getProperty("test.src"), "MaxWarns.java");
        String out = javadoc(f);
        check(out, defaultMaxWarns);

        if (errors > 0)
            throw new Exception(errors + " errors occurred");
    }

    String javadoc(File f) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        String[] args = { f.getPath() };
        int rc = com.sun.tools.javadoc.Main.execute("javadoc", pw, pw, pw,
                "MaxWarns$TestDoclet",
                getClass().getClassLoader(), args);
        pw.flush();
        return sw.toString();
    }

    private static final String WARNING_TEXT = "count ";

    void check(String out, int count) {
        System.err.println(out);
        Pattern warn = Pattern.compile("warning - " + WARNING_TEXT + "[0-9]+");
        Matcher m = warn.matcher(out);
        int n = 0;
        for (int start = 0; m.find(start); start = m.start() + 1) {
            n++;
        }
        if (n != count)
            error("unexpected number of warnings reported: " + n + "; expected: " + count);

        Pattern warnCount = Pattern.compile("(?ms).*^([0-9]+) warnings$.*");
        m = warnCount.matcher(out);
        if (m.matches()) {
            n = Integer.parseInt(m.group(1));
            if (n != count)
                error("unexpected number of warnings reported: " + n + "; expected: " + count);
        } else
            error("total count not found");
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors;

    public static class TestDoclet {

        public static boolean start(RootDoc root) {
            // generate 150 warnings
            for (int i = 1 ; i <= 150 ; i++) {
                root.printWarning(WARNING_TEXT + i);
            }
            return true;
        }

        public static int optionLength(String option) {
            return 0;
        }

        public static boolean validOptions(String[][] options, DocErrorReporter reporter) {
            return true;
        }
    }
}

