/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifier should verify ClassFileLoadHook bytes even if on bootclasspath
 * @bug 8351654
 * @requires vm.jvmti
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @modules java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @compile TestChecker.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller checker/TestChecker
 * @run main TestVerify buildAgent
 * @run main/othervm --patch-module=java.base=. -Dagent.retransform=false -javaagent:redefineagent.jar TestVerify
 * @run main/othervm --patch-module=java.base=. -Dagent.retransform=true -javaagent:redefineagent.jar TestVerify
 */

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;

import java.io.PrintWriter;
import jdk.test.lib.helpers.ClassFileInstaller;
import java.io.FileNotFoundException;

public class TestVerify {

    private static final String CLASS_TO_BREAK = "java.time.Duration";
    private static final String INTERNAL_CLASS_TO_BREAK = CLASS_TO_BREAK.replace('.', '/');
    private static final boolean DEBUG = false;

    private static class BadTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

            if (className.equals(INTERNAL_CLASS_TO_BREAK)) {
                System.out.println("Instrumenting modular class " + INTERNAL_CLASS_TO_BREAK);

                var methodTransform = MethodTransform.transformingCode((builder, element) -> {
                    if (element instanceof ReturnInstruction) {
                        System.out.println("Injecting bug");
                        // THE BUG! insert broken function call

                        var checkerDesc = ClassDesc.of("checker", "TestChecker");
                        builder.invokestatic(checkerDesc, "instance", MethodTypeDesc.of(checkerDesc), true);

                        // dup the instance ref, this is just to get a bad argument to the next method call
                        builder.dup();

                        // then call a check method that doesn't take that type, but we have the wrong desc
                        builder.invokeinterface(checkerDesc, "check", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Integer));

                        System.out.println("Done injecting bug");
                    }
                    builder.with(element);
                });
                var classTransform = ClassTransform.transformingMethods(mm -> mm.methodName().stringValue().equals("getSeconds"), methodTransform);

                byte[] bytes;
                try {
                    var cf = ClassFile.of();
                    var existingClass = cf.parse(classfileBuffer);
                    bytes = cf.transformClass(existingClass, classTransform);

                    if (DEBUG) Files.write(Path.of("bad.class"), bytes);
                } catch (Throwable e) {
                    throw new AssertionError(e);
                }
                return bytes;
            }
            return null;
        }
    }

    static Instrumentation inst = null;

    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        System.out.println("Premain");
        inst = instrumentation;
    }

    private static void buildAgent() {
        try {
            ClassFileInstaller.main("TestVerify");
        } catch (Exception e) {
            throw new RuntimeException("Could not write agent classfile", e);
        }

        try {
            PrintWriter pw = new PrintWriter("MANIFEST.MF");
            pw.println("Premain-Class: TestVerify");
            pw.println("Agent-Class: TestVerify");
            pw.println("Can-Retransform-Classes: true");
            pw.println("Can-Redefine-Classes: true");
            pw.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not write manifest file for the agent", e);
        }

        sun.tools.jar.Main jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(new String[] { "-cmf", "MANIFEST.MF", "redefineagent.jar", "TestVerify.class" })) {
            throw new RuntimeException("Could not write the agent jar file");
        }
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length == 1 && argv[0].equals("buildAgent")) {
            buildAgent();
            return;
        }

        // double check our class hasn't been loaded yet
        for (Class clazz : inst.getAllLoadedClasses()) {
            if (clazz.getName().equals(CLASS_TO_BREAK)) {
                throw new AssertionError("Oops! Class " + CLASS_TO_BREAK + " is already loaded, the test can't work");
            }
        }

        boolean retransform = Boolean.getBoolean("agent.retransform");

        try {
            if (retransform) {
                // Retransform the class for the VerifyError.
                var clazz = Class.forName(CLASS_TO_BREAK);
                inst.addTransformer(new BadTransformer(), true);
                inst.retransformClasses(clazz);
            } else {
                // Load the class instrumented with CFLH for the VerifyError.
                inst.addTransformer(new BadTransformer());
                System.out.println("1 hour is " + Duration.ofHours(1).getSeconds() + " seconds");
            }
            throw new RuntimeException("Failed: Did not throw VerifyError");
        } catch (VerifyError e) {
            System.out.println("Passed: VerifyError " + e.getMessage());
        }
    }
}
