/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6994608
 * @summary javah no longer accepts parameter files as input
 */

import java.io.*;
import java.util.*;

public class T6994608 {
    public static void main(String... args) throws Exception {
        new T6994608().run();
    }

    void run() throws Exception {
        Locale prev = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            File f = writeFile(new File("classList"), "java.lang.Object");
            test(Arrays.asList("@" + f.getPath()), 0, null);
            test(Arrays.asList("@badfile"), 1, "Can't find file badfile");
            if (errors > 0)
                throw new Exception(errors + " errors occurred");
        } finally {
            Locale.setDefault(prev);
        }
    }

    void test(List<String> args, int expectRC, String expectOut) {
        System.err.println("Test: " + args
                + " rc:" + expectRC
                + ((expectOut != null) ? " out:" + expectOut : ""));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javah.Main.run(args.toArray(new String[args.size()]), pw);
        pw.close();
        String out = sw.toString();
        if (!out.isEmpty())
            System.err.println(out);

        if (rc != expectRC)
            error("Unexpected exit code: " + rc + "; expected: " + expectRC);
        if (expectOut != null && !out.contains(expectOut))
            error("Expected string not found: " + expectOut);

        System.err.println();
    }

    File writeFile(File f, String s) throws IOException {
        if (f.getParentFile() != null)
            f.getParentFile().mkdirs();
        try (FileWriter out = new FileWriter(f)) {
            out.write(s);
        }
        return f;
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;
}

