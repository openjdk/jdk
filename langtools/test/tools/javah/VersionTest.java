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
 * @bug 6890226
 * @summary javah -version is broken
 */

import java.io.*;
import java.util.Locale;

public class VersionTest {
    public static void main(String... args) {
        Locale prev = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            System.err.println(Locale.getDefault());
            test("-version", "\\S+ version \"\\S+\"");
            test("-fullversion", "\\S+ full version \"\\S+\"");
        } finally {
            Locale.setDefault(prev);
        }
    }

    static void test(String option, String regex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        String[] args = { option };
        int rc = com.sun.tools.javah.Main.run(args, pw);
        pw.close();
        if (rc != 0)
            throw new Error("javah failed: rc=" + rc);
        String out = sw.toString().trim();
        System.err.println(out);
        if (!out.matches(regex))
            throw new Error("output does not match pattern: " + regex);
    }
}
