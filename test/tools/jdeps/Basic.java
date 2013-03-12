/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003562
 * @summary Basic tests for jdeps tool
 * @build Test p.Foo
 * @run main Basic
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.*;

public class Basic {
    public static void main(String... args) throws Exception {
        int errors = 0;

        errors += new Basic().run();
        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    int run() throws IOException {
        File testDir = new File(System.getProperty("test.classes", "."));
        // test a .class file
        test(new File(testDir, "Test.class"),
             new String[] {"java.lang", "p"});
        // test a directory
        test(new File(testDir, "p"),
             new String[] {"java.lang", "java.util"});
        // test class-level dependency output
        test(new File(testDir, "Test.class"),
             new String[] {"java.lang.Object", "p.Foo"},
             new String[] {"-V", "class"});
        // test -p option
        test(new File(testDir, "Test.class"),
             new String[] {"p.Foo"},
             new String[] {"--verbose-level=class", "-p", "p"});
        // test -e option
        test(new File(testDir, "Test.class"),
             new String[] {"p.Foo"},
             new String[] {"-V", "class", "-e", "p\\..*"});
        test(new File(testDir, "Test.class"),
             new String[] {"java.lang"},
             new String[] {"-V", "package", "-e", "java\\.lang\\..*"});
        // test -classpath and wildcard options
        test(null,
             new String[] {"com.sun.tools.jdeps", "java.lang", "java.util",
                           "java.util.regex", "java.io"},
             new String[] {"--classpath", testDir.getPath(), "*"});
        // -v shows intra-dependency
        test(new File(testDir, "Test.class"),
             new String[] {"java.lang.Object", "p.Foo"},
             new String[] {"-v", "--classpath", testDir.getPath(), "Test.class"});
        return errors;
    }

    void test(File file, String[] expect) {
        test(file, expect, new String[0]);
    }

    void test(File file, String[] expect, String[] options) {
        String[] args;
        if (file != null) {
            args = Arrays.copyOf(options, options.length+1);
            args[options.length] = file.getPath();
        } else {
            args = options;
        }
        String[] deps = jdeps(args);
        checkEqual("dependencies", expect, deps);
    }

    String[] jdeps(String... args) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        System.err.println("jdeps " + Arrays.toString(args));
        int rc = com.sun.tools.jdeps.Main.run(args, pw);
        pw.close();
        String out = sw.toString();
        if (!out.isEmpty())
            System.err.println(out);
        if (rc != 0)
            throw new Error("jdeps failed: rc=" + rc);
        return findDeps(out);
    }

    // Pattern used to parse lines
    private static Pattern linePattern = Pattern.compile(".*\r?\n");
    private static Pattern pattern = Pattern.compile("\\s+ -> (\\S+) +.*");

    // Use the linePattern to break the given String into lines, applying
    // the pattern to each line to see if we have a match
    private static String[] findDeps(String out) {
        List<String> result = new ArrayList<>();
        Matcher lm = linePattern.matcher(out);  // Line matcher
        Matcher pm = null;                      // Pattern matcher
        int lines = 0;
        while (lm.find()) {
            lines++;
            CharSequence cs = lm.group();       // The current line
            if (pm == null)
                pm = pattern.matcher(cs);
            else
                pm.reset(cs);
            if (pm.find())
                result.add(pm.group(1));
            if (lm.end() == out.length())
                break;
        }
        return result.toArray(new String[0]);
    }

    void checkEqual(String label, String[] expect, String[] found) {
        Set<String> s1 = new HashSet<>(Arrays.asList(expect));
        Set<String> s2 = new HashSet<>(Arrays.asList(found));

        if (!s1.equals(s2))
            error("Unexpected " + label + " found: '" + s2 + "', expected: '" + s1 + "'");
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors;
}
