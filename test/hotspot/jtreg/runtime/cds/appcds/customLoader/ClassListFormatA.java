/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;

/*
 * @test
 * @summary Tests the format checking of class list format.
 *
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile ../test-classes/Hello.java test-classes/CustomLoadee.java test-classes/CustomLoadee2.java
 *          test-classes/CustomInterface2_ia.java test-classes/CustomInterface2_ib.java
 * @run driver ClassListFormatA
 */

public class ClassListFormatA extends ClassListFormatBase {
    static {
        // Uncomment the following line to run only one of the test cases
        // ClassListFormatBase.RUN_ONLY_TEST = "TESTCASE A1";
    }

    public static void main(String[] args) throws Throwable {
        String appJar = JarBuilder.getOrCreateHelloJar();
        String customJarPath = JarBuilder.build("ClassListFormatA", "CustomLoadee",
                                            "CustomLoadee2", "CustomInterface2_ia", "CustomInterface2_ib");
        //----------------------------------------------------------------------
        // TESTGROUP A: general bad input
        //----------------------------------------------------------------------
        dumpShouldFail(
            "TESTCASE A1: bad input - interface: instead of interfaces:",
            appJar, classlist(
                "Hello",
                "java/lang/Object id: 1",
                "CustomLoadee interface: 1"
            ),
            "Unknown input:");

        dumpShouldFail(
            "TESTCASE A2: bad input - negative IDs not allowed",
            appJar, classlist(
                "Hello",
                "java/lang/Object id: -1"
            ),
            "Error: negative integers not allowed");

        dumpShouldFail(
            "TESTCASE A3: bad input - bad ID (not an integer)",
            appJar, classlist(
                "Hello",
                "java/lang/Object id: xyz"
            ),
            "Error: expected integer");

        if (false) {
              // FIXME - classFileParser.cpp needs fixing.
            dumpShouldFail(
                "TESTCASE A4: bad input - bad ID (integer too big)",
                appJar, classlist(
                    "Hello",
                    "java/lang/Object id: 2147483648" // <- this is 0x80000000
                ),
                "Error: expected integer");

              // FIXME
            dumpShouldFail(
                "TESTCASE A5: bad input - bad ID (integer too big)",
                appJar, classlist(
                    "Hello",
                    "java/lang/Object id: 21474836489" // bigger than 32-bit!
                ),
                "Error: expected integer");
        }

        // Good input:
        dumpShouldPass(
            "TESTCASE A6: extraneous spaces, tab characters, trailing new line characters, and trailing comment line",
            appJar, classlist(
                "Hello   ",                   // trailing spaces
                "java/lang/Object\tid:\t1",   // \t instead of ' '
                "CustomLoadee id: 2 super: 1 source: " + customJarPath,
                "CustomInterface2_ia id: 3 super: 1 source: " + customJarPath + " ",
                "CustomInterface2_ib id: 4 super: 1 source: " + customJarPath + "\t\t\r" ,
                "CustomLoadee2 id: 5 super: 1 interfaces: 3 4 source: " + customJarPath,      // preceding spaces
                "#last line is a comment"
            ));

        // Tests for corner cases in the C++ class LineReader, or invalid UTF8. These can't
        // be tested with dumpShouldPass/dumpShouldFail as we need to prepare a special class
        // list file.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6500; i++) {
            sb.append("X123456789");
        }

        {
            System.out.println("TESTCASE A7.1: Long line (65000 chars)");
            String longName = sb.toString(); // 65000 chars long
            String classList = "LongLine.classlist";
            try (FileWriter fw = new FileWriter(classList)) {
                fw.write(longName + "\n");
            }
            CDSOptions opts = (new CDSOptions())
                .addPrefix("-XX:ExtraSharedClassListFile=" + classList, "-Xlog:cds");
            CDSTestUtils.createArchiveAndCheck(opts)
                .shouldContain("Preload Warning: Cannot find " + longName);
        }

        {
            System.out.println("TESTCASE A7.2: Name Length > Symbol::max_length()");
            String tooLongName = sb.toString() + sb.toString();
            String classList = "TooLongLine.classlist";
            try (FileWriter fw = new FileWriter(classList)) {
                fw.write("java/lang/Object\n");
                fw.write(tooLongName + "\n");
            }
            CDSOptions opts = (new CDSOptions())
                .addPrefix("-XX:ExtraSharedClassListFile=" + classList, "-Xlog:cds");
            CDSTestUtils.createArchive(opts)
                .shouldContain(classList + ":2 class name too long") // test line number as well.
                .shouldHaveExitValue(1);
        }

        {
            System.out.println("TESTCASE A7.3: File doesn't end with newline");
            String classList = "NoTrailingNewLine.classlist";
            try (FileWriter fw = new FileWriter(classList)) {
                fw.write("No/Such/ClassABCD");
            }
            CDSOptions opts = (new CDSOptions())
                .addPrefix("-XX:ExtraSharedClassListFile=" + classList, "-Xlog:cds");
            CDSTestUtils.createArchiveAndCheck(opts)
                .shouldContain("Preload Warning: Cannot find No/Such/ClassABCD");
        }
        {
            System.out.println("TESTCASE A7.4: invalid UTF8 character");
            String classList = "BadUTF8.classlist";
            try (FileOutputStream fos = new FileOutputStream(classList)) {
                byte chars[] = new byte[] { (byte)0xa0, (byte)0xa1, '\n'};
                fos.write(chars);
            }
            CDSOptions opts = (new CDSOptions())
                .addPrefix("-XX:ExtraSharedClassListFile=" + classList, "-Xlog:cds");
            CDSTestUtils.createArchive(opts)
                .shouldContain(classList + ":1 class name is not valid UTF8") // test line number as well.
                .shouldHaveExitValue(1);
        }
    }
}
