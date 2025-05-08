/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8232613
 * @summary Ensure Object natives stay registered after redefinition
 * @requires vm.jvmti
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main RedefineObject buildagent
 * @run main/othervm -javaagent:redefineagent.jar RedefineObject
 */

import static jdk.test.lib.Asserts.assertTrue;
import jdk.test.lib.helpers.ClassFileInstaller;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.RuntimeException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileVersion;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;

public class RedefineObject {

    static Instrumentation inst;

    public static void premain(String agentArgs, Instrumentation inst) {
        RedefineObject.inst = inst;
    }

    static class Transformer implements ClassFileTransformer {

        public byte[] asm(ClassLoader loader, String className,
                          Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain, byte[] classfileBuffer)
                throws IllegalClassFormatException {
            return ClassFile.of().transformClass(ClassFile.of().parse(classfileBuffer), (cb, ce) -> {
                if (ce instanceof ClassFileVersion cfv) {
                    // Force a redefine with different class file versions
                    cb.with(ClassFileVersion.of(cfv.majorVersion() - 1, 0));
                } else {
                    cb.with(ce);
                }
            });
        }

        @Override public byte[] transform(ClassLoader loader, String className,
                                          Class<?> classBeingRedefined,
                                          ProtectionDomain protectionDomain, byte[] classfileBuffer)
                throws IllegalClassFormatException {

            if (className.contains("java/lang/Object")) {
                try {
                    // Here we remove and re-add the dummy fields. This shuffles the constant pool
                    return asm(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
                } catch (Throwable e) {
                    // The retransform native code that called this method does not propagate
                    // exceptions. Instead of getting an uninformative generic error, catch
                    // problems here and print it, then exit.
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            return null;
        }
    }

    private static void buildAgent() {
        try {
            ClassFileInstaller.main("RedefineObject");
        } catch (Exception e) {
            throw new RuntimeException("Could not write agent classfile", e);
        }

        try {
            PrintWriter pw = new PrintWriter("MANIFEST.MF");
            pw.println("Premain-Class: RedefineObject");
            pw.println("Agent-Class: RedefineObject");
            pw.println("Can-Retransform-Classes: true");
            pw.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not write manifest file for the agent", e);
        }

        sun.tools.jar.Main jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(new String[] { "-cmf", "MANIFEST.MF", "redefineagent.jar", "RedefineObject.class" })) {
            throw new RuntimeException("Could not write the agent jar file");
        }
    }

    public static void main(String[] args) throws Exception {

        int objHash = System.identityHashCode(Object.class);
        System.out.println("Object hashCode: " + objHash);
        if (args.length == 1 && args[0].equals("buildagent")) {
            buildAgent();
            return;
        }

        if (inst == null) {
            throw new RuntimeException("Instrumentation object was null");
        }

        try {
            inst.addTransformer(new RedefineObject.Transformer(), true);
            inst.retransformClasses(Object.class);
        } catch (UnmodifiableClassException e) {
            throw new RuntimeException(e);
        }

        // Exercise native methods on Object after transform
        Object b = new Object();
        b.hashCode();

        C c = new C();
        assertTrue(c.hashCode() != c.clone().hashCode() || c != c.clone());
        assertTrue(c.clone() instanceof C);
        c = (C)c.clone(); // native method on new Object
    }

    private static class C implements Cloneable {
        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }
}
