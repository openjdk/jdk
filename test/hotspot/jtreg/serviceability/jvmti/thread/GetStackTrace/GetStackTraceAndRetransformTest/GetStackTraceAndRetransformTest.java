/*
 * Copyright (c) 2023 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Datadog, Inc. All rights reserved.
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

/**
 * @test
 * @bug 8313816
 * @summary Test that a sequence of method retransformation and stacktrace capture while the old method
 *          version is still on stack does not lead to a crash when that's method jmethodID is used as
 *          an argument for JVMTI functions.
 * @requires vm.jvmti
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @modules java.instrument java.base/jdk.internal.org.objectweb.asm
 * @compile GetStackTraceAndRetransformTest.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver GetStackTraceAndRetransformTest build-jar
 * @run main/othervm/native -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -javaagent:./agent.jar -agentlib:GetStackTraceAndRetransformTest GetStackTraceAndRetransformTest
 */

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.LockSupport;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class GetStackTraceAndRetransformTest {
    public static final class Shared {
        public static volatile Instrumentation inst;

        public static void retransform() {
            try {
                Shared.inst.retransformClasses(new Class[] { Transformable.class });
            } catch (UnmodifiableClassException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String agentManifest =
            "Premain-Class: " + GetStackTraceAndRetransformTest.Agent.class.getName() + "\n"
                    + "Can-Retransform-Classes: true\n";

    public static void main(String args[]) throws Throwable {
        if (args.length == 1 && args[0].equals("build-jar")) {
            buildAgent();
            return;
        }
        initialize(Transformable.class);

        Transformable.retransformAndStacktrace();
        Transformable.stacktrace();

        WhiteBox.getWhiteBox().cleanMetaspaces();
        check(2);
    }

    private static String buildAgent() throws Exception {
        Path jar = Paths.get(".", "agent.jar");
        String jarPath = jar.toAbsolutePath().toString();
        ClassFileInstaller.writeJar(jarPath,
                ClassFileInstaller.Manifest.fromString(agentManifest),
                Agent.class.getName());
        return jarPath;
    }

    private static class Transformable {
        static void retransformAndStacktrace() {
            Shared.retransform();
            capture(Thread.currentThread());
        }

        static void stacktrace() {
            capture(Thread.currentThread());
        }
    }

    public static class Agent implements ClassFileTransformer {
        public static void premain(String args, Instrumentation inst) {
            inst.addTransformer(new SimpleTransformer(), true);
            Shared.inst = inst;
        }
    }

    private static class SimpleTransformer implements ClassFileTransformer {
        private static int counter = 0;
        @Override
        public byte[] transform(ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer
        ) throws IllegalClassFormatException {
            // only if Transformable is being retransformed
            if (classBeingRedefined != null && className.equals("GetStackTraceAndRetransformTest$Transformable")) {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

                try {
                    ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                            return new MethodVisitor(Opcodes.ASM5, mv) {
                                @Override
                                public void visitCode() {
                                    super.visitCode();
                                    mv.visitFieldInsn(Opcodes.GETSTATIC, System.class.getName().replace('.', '/'), "err", Type.getDescriptor(java.io.PrintStream.class));
                                    mv.visitLdcInsn("Hello from transformed method: " + name + "#" + (++counter));
                                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, java.io.PrintStream.class.getName().replace('.', '/'), "println", "(Ljava/lang/String;)V", false);
                                }
                            };
                        }
                    };
                    cr.accept(cv, 0);
                    return cw.toByteArray();
                } catch (Throwable t) {
                    t.printStackTrace(System.err);
                    throw t;
                }
            }
            return null;
        }
    }

    public static native void initialize(Class<?> target);
    public static native void capture(Thread thread);
    public static native void check(int expected);
}
