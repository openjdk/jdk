/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309808 8309811
 * @summary Test the output of the HotSpot BytecodeTracer and ClassPrinter classes.
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI BytecodeTracerTest
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class BytecodeTracerTest {
    public static class Linked {
        public static void doit(String args[]) {
            System.out.println("num args = " + args.length);
        }
    }

    public static class Unlinked implements Serializable {
        public String toString() {
            return "Unlinked" + this.hashCode();
        }
    }

    static String output;
    static int testCount = 0;

    static String nextCase(String testName) {
        ++ testCount;
        return "======================================================================\nTest case "
            + testCount + ": " + testName + "\n    ";
    }

    static void logOutput() throws Exception {
        String logFileName = "log-" + testCount + ".txt";
        System.out.println("Output saved in " + logFileName);
        BufferedWriter writer = new BufferedWriter(new FileWriter(logFileName));
        writer.write(output);
        writer.close();
    }

    static void printClasses(String testName, String classNamePattern, int flags) throws Exception {
        System.out.println(nextCase(testName) + "printClasses(\"" + classNamePattern + "\", " + flags + ")");
        output = WhiteBox.getWhiteBox().printClasses(classNamePattern, flags);
        logOutput();
    }

    static void printMethods(String testName, String classNamePattern, String methodPattern, int flags) throws Exception {
        System.out.println(nextCase(testName) + "printMethods(\"" + classNamePattern + "\", \"" + methodPattern + "\", " + flags + ")");
        output = WhiteBox.getWhiteBox().printMethods(classNamePattern, methodPattern, flags);
        logOutput();
    }

    static void mustMatch(String pattern) {
        Pattern p = Pattern.compile(pattern, Pattern.MULTILINE);
        Matcher m = p.matcher(output);
        boolean found = m.find();
        if (!found) {
            System.err.println("********* output ********");
            System.err.println(output);
            System.err.println("*************************");
        }
        Asserts.assertTrue(found,
                           "Missing pattern: \"" + pattern + "\"");
        System.out.println("Found pattern: " + pattern);
        System.out.println("          ==>: " + m.group());
    }

    public static void main(String args[]) throws Exception {
        Linked.doit(args); // Force "Linked" class to be linked (and rewritten);

        // ======
        printClasses("invokedynamic in linked class",
                     "BytecodeTracerTest$Linked", 0xff);
        mustMatch("invokedynamic bsm=[0-9]+ [0-9]+ <makeConcatWithConstants[(]I[)]Ljava/lang/String;>");
        mustMatch("BSM: REF_invokeStatic [0-9]+ <java/lang/invoke/StringConcatFactory.makeConcatWithConstants[(]");

        // ======
        if (false) { // disabled due to JDK-8309811
        printMethods("invokedynamic in unlinked class",
                     "BytecodeTracerTest$Unlinked", "toString", 0xff);
        mustMatch("invokedynamic bsm=[0-9]+ [0-9]+ <makeConcatWithConstants[(]I[)]Ljava/lang/String;>");
        mustMatch("BSM: REF_invokeStatic [0-9]+ <java/lang/invoke/StringConcatFactory.makeConcatWithConstants[(]");
        }
    }

    public Serializable cast(Unlinked f) {
        // Verifying this method causes the "Unlinked" class to be loaded. However
        // the "Unlinked" class is never used during the execution of
        // BytecodeTracerTest.main(), so it is not linked by HotSpot.
        return f;
    }
}

