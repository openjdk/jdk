/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6958836 8002168
 * @summary javadoc should support -Xmaxerrs and -Xmaxwarns
 * @modules jdk.javadoc
 */

import java.io.*;
import java.util.*;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.RootDoc;



public class Test {
    private static final String ERROR_MARKER = "Error-count";
    private static final String WARNING_MARKER = "Warning-count";

    public static void main(String... args) throws Exception {
        new Test().run();
    }

    void run() throws Exception {
        javadoc("Errors",  list(),                   10,  0);
        javadoc("Errors",  list("-Xmaxerrs",   "0"), 10,  0);
        javadoc("Errors",  list("-Xmaxerrs",   "2"),  2,  0);
        javadoc("Errors",  list("-Xmaxerrs",   "4"),  4,  0);
        javadoc("Errors",  list("-Xmaxerrs",  "20"), 10,  0);

        javadoc("Warnings", list(),                    0, 10);
        javadoc("Warnings", list("-Xmaxwarns",  "0"),  0, 10);
        javadoc("Warnings", list("-Xmaxwarns",  "2"),  0,  2);
        javadoc("Warnings", list("-Xmaxwarns",  "4"),  0,  4);
        javadoc("Warnings", list("-Xmaxwarns", "20"),  0, 10);

        if (errors > 0)
            throw new Exception(errors + " errors occurred.");
    }

    void javadoc(String selector, List<String> testOpts,
                int expectErrs, int expectWarns) {
        System.err.println("Test " + (++count) + ": " + selector + " " + testOpts);
        File testOutDir = new File("test" + count);

        List<String> opts = new ArrayList<String>();
        // Force en_US locale in lieu of something like -XDrawDiagnostics.
        // For some reason, this must be the first option when used.
        opts.addAll(list("-locale", "en_US"));
        opts.add(new File(System.getProperty("test.src"),
                Test.class.getSimpleName() + ".java").getPath());
        opts.addAll(testOpts);
        opts.add("-gen" + selector);

        StringWriter errSW = new StringWriter();
        PrintWriter errPW = new PrintWriter(errSW);
        StringWriter warnSW = new StringWriter();
        PrintWriter warnPW = new PrintWriter(warnSW);
        StringWriter noteSW = new StringWriter();
        PrintWriter notePW = new PrintWriter(noteSW);

        int rc = com.sun.tools.javadoc.Main.execute("javadoc",
                              errPW, warnPW, notePW,
                              "Test$TestDoclet",
                              getClass().getClassLoader(),
                              opts.toArray(new String[opts.size()]));
        System.err.println("rc: " + rc);

        errPW.close();
        String errOut = errSW.toString();
        System.err.println("Errors:\n" + errOut);
        warnPW.close();
        String warnOut = warnSW.toString();
        System.err.println("Warnings:\n" + warnOut);
        notePW.close();
        String noteOut = noteSW.toString();
        System.err.println("Notes:\n" + noteOut);

        check(errOut, ERROR_MARKER, expectErrs);
        check(warnOut, WARNING_MARKER, expectWarns); // requires -locale en_US
    }

    void check(String text, String expectText, int expectCount) {
        int foundCount = 0;
        for (String line: text.split("[\r\n]+")) {
            if (line.contains(expectText))
                foundCount++;
        }
        if (foundCount != expectCount) {
            error("incorrect number of matches found: " + foundCount
                  + ", expected: " + expectCount);
        }
    }

    private List<String> list(String... args) {
        return Arrays.asList(args);
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int count;
    int errors;

    public static class TestDoclet {
        static boolean genErrors = false;
        static boolean genWarnings = false;

        public static boolean start(RootDoc root) {
            // generate 10 errors or warnings
            for (int i = 1 ; i <= 10 ; i++) {
                if (genErrors)
                    root.printError(ERROR_MARKER + " " + i);
                if (genWarnings)
                    root.printWarning(WARNING_MARKER + " " + i);
            }
            return true;
        }

        public static int optionLength(String option) {
            if (option == null) {
                throw new Error("invalid usage: ");
            }
            System.out.println("option: " + option);
            switch (option.trim()) {
                case "-genErrors":
                    return 1;
                case "-genWarnings":
                    return 1;
                default:
                    return 0;
            }
        }

        public static boolean validOptions(String[][] options, DocErrorReporter reporter) {
            for (int i = 0 ; i < options.length; i++) {
               String opt = options[i][0].trim();
               switch (opt) {
                   case "-genErrors":
                       genErrors = true;
                       genWarnings = false;
                       break;
                   case "-genWarnings":
                       genErrors = false;
                       genWarnings = true;
                       break;
               }
            }
            return true;
        }
    }
}
