/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8240908
 *
 * @library /test/lib
 * @run compile -g -parameters RetransformWithMethodParametersTest.java
 * @run shell MakeJAR.sh retransformAgent
 *
 * @run main/othervm -javaagent:retransformAgent.jar RetransformWithMethodParametersTest
 */

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.ClassTransformer;

class MethodParametersTarget {
    public void method1(
            int intParam1, String stringParam1 // @1 commentout
            // @1 uncomment   int intParam2, String stringParam2
            )
    {
        // @1 uncomment System.out.println(stringParam2);   // change CP
    }
}

public class RetransformWithMethodParametersTest extends ATransformerManagementTestCase {

    public static void main (String[] args) throws Throwable {
        ATestCaseScaffold test = new RetransformWithMethodParametersTest();
        test.runTest();
    }

    private String targetClassName = "MethodParametersTarget";
    private String classFileName = targetClassName + ".class";
    private String sourceFileName = "RetransformWithMethodParametersTest.java";
    private Class targetClass;
    private byte[] originalClassBytes;

    private byte[] seenClassBytes;
    private byte[] newClassBytes;

    public RetransformWithMethodParametersTest() throws Throwable {
        super("RetransformWithMethodParametersTest");

        File origClassFile = new File(System.getProperty("test.classes", "."), classFileName);
        log("Reading test class from " + origClassFile);
        originalClassBytes = Files.readAllBytes(origClassFile.toPath());
        log("Read " + originalClassBytes.length + " bytes.");

        DisassembledClassbytes disasm = new DisassembledClassbytes(originalClassBytes);
        log("original:");
        disasm.print();
        assertTrue("MethodParameters not found", !disasm.methodParameters().lines.isEmpty());
    }

    private void log(Object o) {
        System.out.println(String.valueOf(o));
    }

    //
    private void verifyMethodParams(boolean expectedPresent, String... expectedNames) throws Throwable {
        Class cls = Class.forName(targetClassName);
        // the class contains 1 method (method1)
        Method method = cls.getDeclaredMethods()[0];
        Parameter[] params = method.getParameters();
        log("Params of " + method.getName() + " method (" + params.length + "):");
        if (expectedPresent) {
            assertEquals(expectedNames.length, params.length);
        }
        for (int i = 0; i < params.length; i++) {
            log("  " + i + ": " + params[i].getName());
            assertEquals(expectedPresent, params[i].isNamePresent());
            if (expectedPresent) {
                assertEquals(expectedNames[i], params[i].getName());
            }
        }
    }

    private void reset() {
        seenClassBytes = null;
        newClassBytes = null;
    }

    private byte[] getClassBytes() throws Throwable {
        reset();
        fInst.retransformClasses(targetClass);
        assertTrue(targetClassName + " was not seen by transform()", seenClassBytes != null);
        return seenClassBytes;
    }

    private void setClassBytes(byte[] classBytes) throws Throwable {
        reset();
        newClassBytes = classBytes;
        fInst.retransformClasses(targetClass);
        assertTrue(targetClassName + " was not seen by transform()", seenClassBytes != null);
    }

    private static final String[] expectedDifferentStrings = {
            "^Classfile .+$",
            "^[\\s]+SHA-256 checksum .[^\\s]+$"
    };

    private boolean expectedDifferent(String line) {
        for (String s: expectedDifferentStrings) {
            Pattern p = Pattern.compile(s);
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private void compareClassBytes(byte[] bytes1, byte[] bytes2) throws Throwable {
        if (bytes1.length != bytes2.length) {
            log("Class bytes have different length: " + bytes1.length + " != " + bytes2.length);
        } else {
            int pos = Arrays.mismatch(bytes1, bytes2);
            if (pos < 0) {
                log("Class bytes are identical.");
                return;
            }
            log("Class bytes are different (starting from " + pos + "): " + bytes1.length + " != " + bytes2.length);
        }

        log("Disassembly difference:");
        // compare 'javap -v' output for the class files
        List<String> out1 = new DisassembledClassbytes(bytes1).lines;
        DisassembledClassbytes disasm2 = new DisassembledClassbytes(bytes2);
        List<String> out2 = disasm2.lines;
        boolean different = false;
        boolean orderChanged = false;
        int lineNum = 0;
        for (String line: out1) {
            if (!expectedDifferent(line)) {
                if (!out2.contains(line)) {
                    different = true;
                    System.out.println("< (" + (lineNum + 1) + ") " + line);
                } else {
                    if (lineNum < out2.size() && !out1.get(lineNum).equals(out2.get(lineNum))) {
                        // out2 contains line, but at different position
                        System.out.println("orig (" + lineNum + "): " + line);
                        orderChanged = true;
                    }
                }
            }
            lineNum++;
        }
        lineNum = 0;
        for (String line: out2) {
            if (!expectedDifferent(line)) {
                if (!out1.contains(line)) {
                    different = true;
                    System.out.println("> (" + (lineNum + 1) + ") " + line);
                }
            }
            lineNum++;
        }

        if (different) {
            log("from transformer:");
            disasm2.print();

            fail(targetClassName + " did not match .class file");
        }
        log("Disassembled files are equals" + (orderChanged ? " (order changed)" : ""));
    }

    private class DisassembledClassbytes {
        public final List<String> lines;

        public DisassembledClassbytes(List<String> lines) {
            this.lines = lines;
        }
        public DisassembledClassbytes(byte[] classBytes) throws Throwable {
            this(disassembleClassBytes(classBytes));
        }

        public void print() {
            log("DisassembledClassbytes -------------------");
            lines.forEach(s -> log(s));
            log("==========================================");
        }

        public boolean contains(String s) {
            for (String line: lines) {
                if (line.contains(s)) {
                    return true;
                }
            }
            return false;
        }

        public DisassembledClassbytes getSection(String name) {
            List<String> result = new ArrayList<>();
            boolean inSection = false;
            String sectionPrefix = "";
            Pattern p = Pattern.compile("^( *)" + name + "\\b.*$");

            for (String line: lines) {
                if (inSection) {
                    if (line.startsWith(sectionPrefix)) {
                        result.add(line);
                    } else {
                        inSection = false; // and check if new section is started here
                    }
                }
                if (!inSection) {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        inSection = true;
                        sectionPrefix = m.group(1) + " ";
                        // add section header as well
                        result.add(line);
                    }
                }
            }
            return new DisassembledClassbytes(result);
        }

        public DisassembledClassbytes methodParameters() {
            return getSection("MethodParameters");
        }
    }

    private List<String> disassembleClassBytes(byte[] bytes) throws Throwable {
        File f = new File(classFileName);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(bytes);
        }

        JDKToolLauncher javap = JDKToolLauncher.create("javap")
                .addToolArg("-verbose")
                .addToolArg("-p")       // Shows all classes and members.
                //.addToolArg("-c")       // Prints out disassembled code
                //.addToolArg("-s")       // Prints internal type signatures.
                .addToolArg(f.toString());
        ProcessBuilder pb = new ProcessBuilder(javap.getCommand());
        OutputAnalyzer out = ProcessTools.executeProcess(pb);
        out.shouldHaveExitValue(0);
        try {
            Files.delete(f.toPath());
        } catch (Exception ex) {
            // ignore
        }
        return out.asLines();
    }


    protected final void doRunTest() throws Throwable {
        beVerbose();

        ClassLoader loader = getClass().getClassLoader();
        targetClass = loader.loadClass(targetClassName);
        assertEquals(targetClassName, targetClass.getName());
        verifyMethodParams(true, "intParam1", "stringParam1");

        log("(-1)1st arg name = " + targetClass.getMethods()[0].getParameters()[0].getName());

        addTransformerToManager(fInst, new Transformer(), true);

        {
            log("Testcase 1: ensure ClassFileReconstiruter restores MethodParameters attribute");
            byte[] classBytes = getClassBytes();

            compareClassBytes(originalClassBytes, classBytes);
            log("");
        }

        {
            log("Testcase 2: redefine class with changed parameter names");
            byte[] classBytes = Files.readAllBytes(Paths.get(
                    ClassTransformer.fromTestSource(sourceFileName)
                            .transform(1, targetClassName, "-g", "-parameters")));
            DisassembledClassbytes disasm = new DisassembledClassbytes(classBytes);
            log("transformed class:");
            disasm.print();
            assertTrue("MethodParameters not found", !disasm.methodParameters().lines.isEmpty());

            setClassBytes(classBytes);

            verifyMethodParams(true, "intParam2", "stringParam2");
        }

        {
            log("Testcase 3: redefine class with no parameter names");
            byte[] classBytes = Files.readAllBytes(Paths.get(
                    ClassTransformer.fromTestSource(sourceFileName)
                            .transform(1, targetClassName, "-g")));
            DisassembledClassbytes disasm = new DisassembledClassbytes(classBytes);
            log("transformed class:");
            disasm.print();
            // ensure there is no MethodParameters attr.
            assertTrue("MethodParameters found", disasm.methodParameters().lines.isEmpty());

            setClassBytes(classBytes);

            // MethodParameters attribute should be deleted.
            verifyMethodParams(false);
        }
    }


    public class Transformer implements ClassFileTransformer {
        public Transformer() {
        }

        public String toString() {
            return Transformer.this.getClass().getName();
        }

        public byte[] transform(ClassLoader loader, String className,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

            if (className.equals(targetClassName)) {
                log(this + ".transform() sees '" + className
                        + "' of " + classfileBuffer.length + " bytes.");
                seenClassBytes = classfileBuffer;
                if (newClassBytes != null) {
                    log(this + ".transform() sets new classbytes for '" + className
                            + "' of " + newClassBytes.length + " bytes.");
                }
                return newClassBytes;
            }

            return null;
        }
    }
}
