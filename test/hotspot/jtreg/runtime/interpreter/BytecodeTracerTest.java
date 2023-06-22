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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class BytecodeTracerTest {
    private final static String Linked_className = Linked.class.getName();

    // This loads the "Unlinked" class, but doesn't link it.
    private final static String Unlinked_className = Unlinked.class.getName();

    public static class Linked {
        public static void doit(String args[]) {
            System.out.println("num args = " + args.length);
        }
         public String test_ldc() {
            Class c = Unloaded.class;
            return "Literal";
        }
   }

    public static class Unlinked {
        public String toString() {
            return "Unlinked" + this.hashCode();
        }
        public String test_ldc() {
            Class c = Unloaded.class;
            return "Literal";
        }
    }

    // This class is never loaded during the execution of BytecodeTracerTest
    public static class Unloaded {


    }

    static int testCount = 0;
    String testNote;
    String output;

    BytecodeTracerTest(String note) {
        ++ testCount;
        testNote = "======================================================================\nTest case "
            + testCount + ": " + note + "\n    ";
    }

    void logOutput() throws Exception {
        String logFileName = "log-" + testCount + ".txt";
        System.out.println("Output saved in " + logFileName);
        BufferedWriter writer = new BufferedWriter(new FileWriter(logFileName));
        writer.write(output);
        writer.close();
    }

    BytecodeTracerTest printClasses(String classNamePattern, int flags) throws Exception {
        System.out.println(testNote + "printClasses(\"" + classNamePattern + "\", " + flags + ")");
        output = WhiteBox.getWhiteBox().printClasses(classNamePattern, flags);
        logOutput();
        return this;
    }

    BytecodeTracerTest printMethods(String classNamePattern, String methodPattern, int flags) throws Exception {
        System.out.println(testNote + "printMethods(\"" + classNamePattern + "\", \"" + methodPattern + "\", " + flags + ")");
        output = WhiteBox.getWhiteBox().printMethods(classNamePattern, methodPattern, flags);
        logOutput();
        return this;
    }

    BytecodeTracerTest printLinkedMethods(String methodPattern, int flags) throws Exception {
        return printMethods(Linked_className, methodPattern, flags);
    }

    BytecodeTracerTest printLinkedMethods(String methodPattern) throws Exception {
        return printLinkedMethods(methodPattern, 0xff);
    }

    BytecodeTracerTest printUnlinkedMethods(String methodPattern, int flags) throws Exception {
        return printMethods(Unlinked_className, methodPattern, flags);
    }

    BytecodeTracerTest printUnlinkedMethods(String methodPattern) throws Exception {
        return printUnlinkedMethods(methodPattern, 0xff);
    }

    BytecodeTracerTest mustMatch(String pattern) {
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
        return this;
    }

    static BytecodeTracerTest test(String note) {
        return new BytecodeTracerTest(note);
    }

    public static void main(String args[]) throws Exception {
        Linked.doit(args); // Force "Linked" class to be linked (and rewritten);

        test("invokedynamic in linked class")
            .printClasses("BytecodeTracerTest$Linked", 0xff)
            .mustMatch("invokedynamic bsm=[0-9]+ [0-9]+ <makeConcatWithConstants[(]I[)]Ljava/lang/String;>")
            .mustMatch("BSM: REF_invokeStatic [0-9]+ <java/lang/invoke/StringConcatFactory.makeConcatWithConstants[(]");

        test("invokedynamic in unlinked class")
            .printUnlinkedMethods("toString")
            .mustMatch("invokedynamic bsm=[0-9]+ [0-9]+ <makeConcatWithConstants[(]I[)]Ljava/lang/String;>");

        test("ldc in linked class")
            .printLinkedMethods("test_ldc")
            .mustMatch("ldc BytecodeTracerTest[$]Unloaded")
            .mustMatch("fast_aldc \"Literal\""); // ldc of String has been rewritten during linking

        test("ldc in unlinked class")
            .printUnlinkedMethods("test_ldc")
            .mustMatch(" ldc BytecodeTracerTest[$]Unloaded")
            .mustMatch(" ldc \"Literal\""); // ldc of String is not rewritten

        // field
        // invokevirtual, invokestatic, invokevfinal, invokeinterface
        
    }
}

