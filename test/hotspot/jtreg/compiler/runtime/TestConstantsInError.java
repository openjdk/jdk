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
 * @bug 8279822
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 *
 * @run main compiler.runtime.TestConstantsInError
 */
package compiler.runtime;

import jdk.internal.org.objectweb.asm.*;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import static jdk.internal.org.objectweb.asm.ClassWriter.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

interface OutputProcessor {
    default void process(OutputAnalyzer output, boolean isC1) {}
}

public abstract class TestConstantsInError implements OutputProcessor {
    static final String TEST_PREFIX = class2desc(TestConstantsInError.class) + "$Test";

    public interface Test extends Runnable {}


    interface Generator {
        void generate(MethodVisitor mv);
    }

    static String class2desc(Class<?> cls) {
        return cls.getName().replace('.', '/');
    }

    public static final String PATH = System.getProperty("test.classes", ".") + java.io.File.separator;

    static byte[] generateClassFile(String suffix, Generator g) throws IOException {
        var cw = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
        String name = TEST_PREFIX + "_" + suffix;
        cw.visit(V19, ACC_PUBLIC | ACC_SUPER, name, null, "java/lang/Object", null);

        {
            var mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "test", "()V", null, null);
            mv.visitCode();
            g.generate(mv);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
        }
        byte[] classFile = cw.toByteArray();

        try (FileOutputStream fos = new FileOutputStream(PATH + name + ".class")) {
            fos.write(classFile);
        }

        return classFile;
    }

    static Test generate(String suffix, Class<? extends LinkageError> expectedError, Generator g) {
        try {
            byte[] classFile = generateClassFile(suffix, g);
            MethodHandles.Lookup testLookup = MethodHandles.lookup().defineHiddenClass(classFile, true);
            MethodHandle testMH = testLookup.findStatic(testLookup.lookupClass(), "test", MethodType.methodType(void.class));

            testMH = MethodHandles.filterReturnValue(testMH,
                    MethodHandles.insertArguments(
                            MethodHandles.throwException(void.class, AssertionError.class),
                            0, new AssertionError("no exception thrown")));

            // Install empty handler for linkage exceptions.
            testMH = MethodHandles.catchException(testMH, expectedError,
                     MethodHandles.empty(MethodType.methodType(void.class, expectedError)));

            return MethodHandleProxies.asInterfaceInstance(Test.class, testMH);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    static void run(String name, Class<? extends LinkageError> expectedError, Generator g) {
        Test test = generate(name, expectedError, g);
        for (int i = 0; i < 1000; i++) {
            test.run();
        }
    }

    static class TestConstantClass extends TestConstantsInError {
        public static void main(String[] args) {
            run("C1", NoClassDefFoundError.class, mv -> mv.visitLdcInsn(Type.getType("LUnknownClass;")));                // non-existent class
            run("C2",   IllegalAccessError.class, mv -> mv.visitLdcInsn(Type.getType("Ljava/lang/invoke/LambdaForm;"))); // inaccessible

            // class loader constraints?
        }

        public void process(OutputAnalyzer results, boolean isC1) {
            results.shouldMatch("Test_C1/.*::test \\(3 bytes\\)$")
                   .shouldMatch("Test_C2/.*::test \\(3 bytes\\)$");

            if (isC1 && (Platform.isAArch64() || Platform.isRISCV64())) { // no code patching
                results.shouldMatch("Test_C1/.*::test \\(3 bytes\\)   made not entrant")
                       .shouldMatch("Test_C2/.*::test \\(3 bytes\\)   made not entrant");
            } else {
                results.shouldNotContain("made not entrant");
            }
        }

        public void processC2(OutputAnalyzer results) {
            results.shouldNotContain("made not entrant");
        }
    }

    static class TestConstantMethodHandle extends TestConstantsInError {
        public static void main(String[] args) {
            // Non-existent holder class
            run("MH1", NoClassDefFoundError.class,
                mv -> mv.visitLdcInsn(new Handle(H_INVOKESTATIC, "UnknownClass", "ignored", "()V", false)));

            // Inaccessible holder class
            run("MH2", IllegalAccessError.class,
                    mv -> mv.visitLdcInsn(new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaForm", "ignored", "()V", false)));

            // Method vs InterfaceMethod mismatch
            run("MH3", IncompatibleClassChangeError.class,
                mv -> mv.visitLdcInsn(new Handle(H_INVOKESTATIC, "java/lang/Object", "ignored", "()V", true)));

            // Non-existent method
            run("MH4", NoSuchMethodError.class,
                mv -> mv.visitLdcInsn(new Handle(H_INVOKESTATIC, "java/lang/Object", "cast", "()V", false)));
        }

        public void process(OutputAnalyzer results, boolean isC1) {
            results.shouldMatch("Test_MH1/.*::test \\(3 bytes\\)$")
                   .shouldMatch("Test_MH2/.*::test \\(3 bytes\\)$")
                   .shouldMatch("Test_MH3/.*::test \\(3 bytes\\)$")
                   .shouldMatch("Test_MH4/.*::test \\(3 bytes\\)$");

            if (isC1 && (Platform.isAArch64() || Platform.isRISCV64())) { // no code patching
                results.shouldMatch("Test_MH1/.*::test \\(3 bytes\\)   made not entrant")
                       .shouldMatch("Test_MH2/.*::test \\(3 bytes\\)   made not entrant")
                       .shouldMatch("Test_MH3/.*::test \\(3 bytes\\)   made not entrant")
                       .shouldMatch("Test_MH4/.*::test \\(3 bytes\\)   made not entrant");
            } else {
                results.shouldNotContain("made not entrant");
            }
        }
    }

    static class TestConstantMethodType extends TestConstantsInError {
        public static void main(String[] args) {
            run("MT1", NoClassDefFoundError.class,
                mv -> mv.visitLdcInsn(Type.getMethodType("(LUnknownClass;)V")));
            run("MT2", NoClassDefFoundError.class,
                mv -> mv.visitLdcInsn(Type.getMethodType("()LUnknownClass;")));
        }

        public void process(OutputAnalyzer results, boolean isC1) {
            results.shouldMatch("Test_MT1/.*::test \\(3 bytes\\)$")
                   .shouldMatch("Test_MT2/.*::test \\(3 bytes\\)$");

            if (isC1 && (Platform.isAArch64() || Platform.isRISCV64())) { // no code patching
                results.shouldMatch("Test_MT1/.*::test \\(3 bytes\\)   made not entrant")
                       .shouldMatch("Test_MT2/.*::test \\(3 bytes\\)   made not entrant");
            } else {
                results.shouldNotContain("made not entrant");
            }
        }
    }

    static class TestConstantDynamic extends TestConstantsInError {
        static int bsm1() throws Exception {
            throw new AssertionError("should not be invoked");
        }

        static int bsm2(MethodHandles.Lookup lookup, String name, Class c) throws Exception {
            throw new Exception("expected");
        }

        static final Handle BSM1 = new Handle(H_INVOKESTATIC, class2desc(TestConstantDynamic.class), "bsm1", "()I", false);
        static final Handle BSM2 = new Handle(H_INVOKESTATIC, class2desc(TestConstantDynamic.class), "bsm2",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)I",
                false);

        public static void main(String[] args) {
            run("CD1", NoClassDefFoundError.class,
                    mv -> {
                        Handle bsm = new Handle(H_INVOKESTATIC, "UnknownClass", "unknown", "()LUnknownClass;", false);
                        mv.visitLdcInsn(new ConstantDynamic("tmp", "LUnknownClass;", bsm));
                    });
            run("CD2", NoSuchMethodError.class,
                    mv -> {
                        Handle bsm = new Handle(H_INVOKESTATIC, class2desc(TestConstantDynamic.class), "unknown", "()I", false);
                        mv.visitLdcInsn(new ConstantDynamic("tmp", "LUnknownClass;", bsm));
                    });
            run("CD3", BootstrapMethodError.class, mv -> mv.visitLdcInsn(new ConstantDynamic("tmp", "I", BSM1)));
            run("CD4", BootstrapMethodError.class, mv -> mv.visitLdcInsn(new ConstantDynamic("tmp", "I", BSM2)));
        }

        public void process(OutputAnalyzer results, boolean isC1) {
            results.shouldMatch("Test_CD1.*::test \\(3 bytes\\)$")
                   .shouldMatch("Test_CD2.*::test \\(3 bytes\\)$")
                   .shouldMatch("Test_CD3.*::test \\(3 bytes\\)$")
                   .shouldMatch("Test_CD4.*::test \\(3 bytes\\)$");

            if (isC1 && (Platform.isAArch64() || Platform.isRISCV64())) { // no code patching
                results.shouldMatch("Test_CD1.*::test \\(3 bytes\\)   made not entrant")
                       .shouldMatch("Test_CD2.*::test \\(3 bytes\\)   made not entrant")
                       .shouldMatch("Test_CD3.*::test \\(3 bytes\\)   made not entrant")
                       .shouldMatch("Test_CD4.*::test \\(3 bytes\\)   made not entrant");
            } else {
                results.shouldNotContain("made not entrant");
            }
        }
    }

    static void run(TestConstantsInError test) throws Exception {
        List<String> commonArgs = List.of(
                "--add-exports", "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
                "-Xbatch", "-XX:CompileThreshold=100",
                "-XX:CompileCommand=quiet", "-XX:CompileCommand=compileonly,*::test",
                "-XX:+PrintCompilation",
                "-XX:CompileCommand=print,*::test",
                "-Dtest.classes=" + System.getProperty("test.classes", "."),
                "-XX:+IgnoreUnrecognizedVMOptions",
                test.getClass().getName());

        ArrayList<String> c1Args = new ArrayList<>();
        c1Args.addAll(List.of("-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", "-XX:+TracePatching"));
        c1Args.addAll(commonArgs);

        OutputAnalyzer outputC1 = ProcessTools.executeTestJava(c1Args)
                .shouldHaveExitValue(0);

        test.process(outputC1, true);

        ArrayList<String> c2Args = new ArrayList<>();
        c2Args.add("-XX:-TieredCompilation");
        c2Args.addAll(commonArgs);

        OutputAnalyzer outputC2 = ProcessTools.executeTestJava(c2Args)
                .shouldHaveExitValue(0);

        test.process(outputC2, false);
    }

    public static void main(String[] args) throws Exception {
        run(new TestConstantClass());
        run(new TestConstantMethodType());
        run(new TestConstantMethodHandle());
        run(new TestConstantDynamic());
    }
}
