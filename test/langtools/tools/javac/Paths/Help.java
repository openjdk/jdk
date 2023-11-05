/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4940642 8293877
 * @summary Check for -help and -X flags
 */

/*
 * Converted from Help.sh, originally written by Martin Buchholz
 *
 * For the last version of the original, Help.sh, see
 * https://git.openjdk.org/jdk/blob/jdk-19%2B36/test/langtools/tools/javac/Paths/Help.sh
 *
 * This class provides rudimentary tests of the javac command-line help.
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.spi.ToolProvider;

public class Help {
    public static void main(String... args) throws Exception {
        new Help().run(args);
    }

    void run(String... args) throws Exception {
        String helpText = javac("-help");
        check(helpText,
                "-X ", "-J", "-classpath ", "-cp ", "-bootclasspath ", "-sourcepath ");

        String xText = javac("-X");
        check(xText, "-Xbootclasspath/p:");
    }

    void check(String text, String... options) throws Exception {
        for (String opt : options) {
            System.err.println("Checking '" + opt + "'");
            if (!text.contains(opt)) {
                text.lines().forEach(System.err::println);
                throw new Exception("Bad help output");
            }
        }
    }

    String javac(String... args) throws Exception {
        var javac = ToolProvider.findFirst("javac")
                .orElseThrow(() -> new Exception("cannot find javac"));
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
             int rc = javac.run(pw, pw, args);
             if (rc != 0) {
                 throw new Error("unexpected exit from javac: " + rc);
             }
             pw.flush();
             return sw.toString();
        }
    }
}

