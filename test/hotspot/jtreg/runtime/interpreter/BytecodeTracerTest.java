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
 * @build jdk.test.whitebox.WhiteBox Linked2 Unlinked2
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI BytecodeTracerTest
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.lang.invoke.MethodHandle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class BytecodeTracerTest {
    private final static String Linked_className = Linked.class.getName();
    private final static String Linked2_className = Linked2.class.getName();

    // This loads the "Unlinked" and "Unlinked2" classes, but doesn't link them.
    private final static String Unlinked_className = Unlinked.class.getName();
    private final static String Unlinked2_className = Unlinked2.class.getName();

    public static void staticMethod()   { }
    public void virtualMethod() { }
    int x;
    static long y;

    public static class Linked {
        public static void doit(String args[]) {
            System.out.println("num args = " + args.length);
        }
        public String test_ldc() {
            Class c = Unloaded.class;
            return "Literal\u5678";
        }
        public void test_invoke(BytecodeTracerTest obj) {
            obj.virtualMethod();
            staticMethod();
        }
        public void test_field(BytecodeTracerTest obj) {
            y = obj.x;
        }
        public void test_invokehandle(MethodHandle mh) throws Throwable {
            mh.invokeExact(4.0f, "String", this);
        }
   }

    public static class Unlinked {
        public String toString() {
            return "Unlinked" + this.hashCode();
        }
        public String test_ldc() {
            Class c = Unloaded.class;
            return "Literal\u1234";
        }
        public void test_invoke(BytecodeTracerTest obj) {
            obj.virtualMethod();
            staticMethod();
        }
        public void test_field(BytecodeTracerTest obj) {
            y = obj.x;
        }
        public void test_invokehandle(MethodHandle mh) throws Throwable {
            mh.invokeExact(4.0f, "String", this);
        }
    }

    // This class is never loaded during the execution of BytecodeTracerTest
    public static class Unloaded { }

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

    BytecodeTracerTest printLinkedMethods(String methodPattern) throws Exception {
        return printMethods(Linked_className, methodPattern, 0xff);
    }

    BytecodeTracerTest printLinked2Methods(String methodPattern) throws Exception {
        return printMethods(Linked2_className, methodPattern, 0xff);
    }

    BytecodeTracerTest printUnlinkedMethods(String methodPattern) throws Exception {
        return printMethods(Unlinked_className, methodPattern, 0xff);
    }

    BytecodeTracerTest printUnlinked2Methods(String methodPattern) throws Exception {
        return printMethods(Unlinked2_className, methodPattern, 0xff);
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
        // Force "Linked" and "Linked2" classes to be linked (and rewritten);
        Linked.doit(args);
        Asserts.assertTrue(Linked2.test_ldc() == 12345, "must be");

        test("invokedynamic in linked class")
            .printClasses("BytecodeTracerTest$Linked", 0xff)
            .mustMatch("invokedynamic bsm=[0-9]+ [0-9]+ <makeConcatWithConstants[(]I[)]Ljava/lang/String;>")
            .mustMatch("BSM: REF_invokeStatic [0-9]+ <java/lang/invoke/StringConcatFactory.makeConcatWithConstants[(]")
            .mustMatch("\"num args = [\\\\]u0001\"") // static param for string concat
            .mustMatch(".*getstatic [0-9]+ <java/lang/System.out:Ljava/io/PrintStream;>");

        test("invokedynamic in unlinked class")
            .printUnlinkedMethods("toString")
            .mustMatch("invokedynamic bsm=[0-9]+ [0-9]+ <makeConcatWithConstants[(]I[)]Ljava/lang/String;>");

        test("ldc in linked class")
            .printLinkedMethods("test_ldc")
            .mustMatch("ldc BytecodeTracerTest[$]Unloaded")
            .mustMatch("fast_aldc \"Literal[\\\\]u5678\""); // ldc of String has been rewritten during linking

        test("ldc in unlinked class")
            .printUnlinkedMethods("test_ldc")
            .mustMatch(" ldc BytecodeTracerTest[$]Unloaded")
            .mustMatch(" ldc \"Literal[\\\\]u1234\""); // ldc of String is not rewritten

        test("More ldc tests in linked class")
            .printLinked2Methods("test_ldc")
            .mustMatch("ldc_w 2")
            .mustMatch("fast_aldc_w \"Hello\"")
            .mustMatch("BSM: REF_invokeStatic [0-9]+ <Linked2.condyBSM[(]Ljava/lang/invoke/MethodHandles")
            .mustMatch("fast_aldc <MethodHandle of kind [0-9]+ index at [0-9]+> [0-9]+ <Linked2.test_ldc[(][)]I>");

        test("More ldc tests in unlinked class")
            .printUnlinked2Methods("test_ldc")
            .mustMatch("ldc_w 2")
            .mustMatch("ldc_w \"Hello\"")
            .mustMatch("BSM: REF_invokeStatic [0-9]+ <Unlinked2.condyBSM[(]Ljava/lang/invoke/MethodHandles")
            .mustMatch("ldc <MethodHandle of kind [0-9]+ index at [0-9]+> [0-9]+ <Unlinked2.test_ldc[(][)]I>");

        test("plain old invoke in linked class")
            .printLinkedMethods("test_invoke")
            .mustMatch("invokevirtual [0-9]+ <BytecodeTracerTest.virtualMethod[(][)]V>")
            .mustMatch("invokestatic [0-9]+ <BytecodeTracerTest.staticMethod[(][)]V>");

        test("plain old invoke in unlinked class")
            .printUnlinkedMethods("test_invoke")
            .mustMatch("invokevirtual [0-9]+ <BytecodeTracerTest.virtualMethod[(][)]V>")
            .mustMatch("invokestatic [0-9]+ <BytecodeTracerTest.staticMethod[(][)]V>");

        test("invokehandle in linked class")
            .printLinkedMethods("test_invokehandle")
            .mustMatch("invokehandle [0-9]+ <java/lang/invoke/MethodHandle.invokeExact[(]FLjava/lang/String;LBytecodeTracerTest[$]Linked;[)]V>");

        test("invokehandle in unlinked class")
            .printUnlinkedMethods("test_invokehandle")
            .mustMatch("invokevirtual [0-9]+ <java/lang/invoke/MethodHandle.invokeExact[(]FLjava/lang/String;LBytecodeTracerTest[$]Unlinked;[)]V>");

        test("field in linked class")
            .printLinkedMethods("test_field")
            .mustMatch("getfield [0-9]+ <BytecodeTracerTest.x:I>")
            .mustMatch("putstatic [0-9]+ <BytecodeTracerTest.y:J>");

        test("field in unlinked class")
            .printUnlinkedMethods("test_field")
            .mustMatch("getfield [0-9]+ <BytecodeTracerTest.x:I>")
            .mustMatch("putstatic [0-9]+ <BytecodeTracerTest.y:J>");
    }
}

