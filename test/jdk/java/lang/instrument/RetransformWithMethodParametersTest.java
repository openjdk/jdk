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
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.Arrays;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.ClassTransformer;

/*
 * The test verifies Instrumentation.retransformClasses() (and JVMTI function RetransformClasses)
 * correctly handles MethodParameter attribute:
 * 1) classfile bytes passed to transformers (and JVMTI ClassFileLoadHook event callback) contain the attribute;
 * 2) the attribute is updated if new version has the attribute with different values;
 * 3) the attribute is removed if new version doesn't contain the attribute.
 */

// See ClassTransformer.transform(int) comment for @1 tag explanations.
class MethodParametersTarget {
    // The class contains the only method, so we don't have issue with method sorting
    // and ClassFileReconstituter should restore the same bytes as original classbytes.
    // This method should be ctor, otherwise default ctor will be implicitly declared.
    public MethodParametersTarget(
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
    }

    private void log(Object o) {
        System.out.println(String.valueOf(o));
    }

    private Parameter[] getTargetMethodParameters() throws ClassNotFoundException {
        Class cls = Class.forName(targetClassName);
        // the class contains 1 method (ctor)
        Executable method = cls.getDeclaredConstructors()[0];
        Parameter[] params = method.getParameters();
        log("Params of " + method.getName() + " method (" + params.length + "):");
        for (int i = 0; i < params.length; i++) {
            log("  " + i + ": " + params[i].getName()
                    + " (" + (params[i].isNamePresent() ? "present" : "absent") + ")");
        }
        return params;
    }

    // Verifies MethodParameters attribute is present and contains the expected values.
    private void verifyPresentMethodParams(String... expectedNames) throws Throwable {
        Parameter[] params = getTargetMethodParameters();
        assertEquals(expectedNames.length, params.length);
        for (int i = 0; i < params.length; i++) {
            assertTrue(params[i].isNamePresent());
            assertEquals(expectedNames[i], params[i].getName());
        }
    }

    // Verifies MethodParameters attribute is absent.
    private void verifyAbsentMethodParams() throws Throwable {
        Parameter[] params = getTargetMethodParameters();
        for (int i = 0; i < params.length; i++) {
            assertTrue(!params[i].isNamePresent());
        }
    }

    // Retransforms target class using provided class bytes;
    // Returns class bytes passed to the transformer.
    private byte[] retransform(byte[] classBytes) throws Throwable {
        seenClassBytes = null;
        newClassBytes = classBytes;
        fInst.retransformClasses(targetClass);
        assertTrue(targetClassName + " was not seen by transform()", seenClassBytes != null);
        return seenClassBytes;
    }

    // Prints dissassembled class bytes.
    private void printDisassembled(String description, byte[] bytes) throws Exception {
        log(description + " -------------------");

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
        out.asLines().forEach(s -> log(s));
        log("==========================================");
    }

    // Verifies class bytes are equal.
    private void compareClassBytes(byte[] expected, byte[] actual) throws Exception {

        int pos = Arrays.mismatch(expected, actual);
        if (pos < 0) {
            log("Class bytes are identical.");
            return;
        }
        log("Class bytes are different.");
        printDisassembled("expected", expected);
        printDisassembled("actual", actual);
        fail(targetClassName + " did not match .class file");
    }

    protected final void doRunTest() throws Throwable {
        beVerbose();

        ClassLoader loader = getClass().getClassLoader();
        targetClass = loader.loadClass(targetClassName);
        // sanity check
        assertEquals(targetClassName, targetClass.getName());
        // sanity check
        verifyPresentMethodParams("intParam1", "stringParam1");

        addTransformerToManager(fInst, new Transformer(), true);

        {
            log("Testcase 1: ensure ClassFileReconstituter restores MethodParameters attribute");

            byte[] classBytes = retransform(null);
            compareClassBytes(originalClassBytes, classBytes);

            log("");
        }

        {
            log("Testcase 2: redefine class with changed parameter names");

            byte[] classBytes = Files.readAllBytes(Paths.get(
                    ClassTransformer.fromTestSource(sourceFileName)
                            .transform(1, targetClassName, "-g", "-parameters")));
            retransform(classBytes);
            // MethodParameters attribute should be updated.
            verifyPresentMethodParams("intParam2", "stringParam2");

            log("");
        }

        {
            log("Testcase 3: redefine class with no parameter names");
            // compile without "-parameters"
            byte[] classBytes = Files.readAllBytes(Paths.get(
                    ClassTransformer.fromTestSource(sourceFileName)
                            .transform(1, targetClassName, "-g")));
            retransform(classBytes);
            // MethodParameters attribute should be dropped.
            verifyAbsentMethodParams();

            log("");
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
