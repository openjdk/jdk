/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests that java.lang.Object cannot be redefined/retransformed
 * @requires vm.jvmti
 * @library /test/lib
 * @modules java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main RedefineObject buildagent
 * @run main/othervm -javaagent:redefineagent.jar RedefineObject
 */

import jdk.test.lib.helpers.ClassFileInstaller;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;

public class RedefineObject {

    static Instrumentation inst;

    public static void premain(String agentArgs, Instrumentation inst) {
        RedefineObject.inst = inst;
    }

    static class Transformer implements ClassFileTransformer {
        // set to true if transform method called to transform java.lang.Object
        private boolean transformObjectInvoked;

        @Override
        public byte[] transform(ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
            if (className.contains("java/lang/Object")) {
                transformObjectInvoked = true;
            }
            return null;
        }

        boolean transformObjectInvoked() {
            return transformObjectInvoked;
        }
    }

    private static void buildAgent() {
        try {
            ClassFileInstaller.main("RedefineObject");
        } catch (Exception e) {
            throw new RuntimeException("Could not write agent classfile", e);
        }

        try (PrintWriter pw = new PrintWriter("MANIFEST.MF")) {
            pw.println("Premain-Class: RedefineObject");
            pw.println("Agent-Class: RedefineObject");
            pw.println("Can-Retransform-Classes: true");
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not write manifest file for the agent", e);
        }

        sun.tools.jar.Main jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(new String[] { "-cmf", "MANIFEST.MF", "redefineagent.jar", "RedefineObject.class" })) {
            throw new RuntimeException("Could not write the agent jar file");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("buildagent")) {
            buildAgent();
            return;
        }

        if (inst == null) {
            throw new RuntimeException("Instrumentation object was null");
        }

        if (inst.isModifiableClass(Object.class)) {
            throw new RuntimeException("java.lang.Object should not be modifable");
        }

        var transformer = new Transformer();
        inst.addTransformer(transformer, true);
        try {
            inst.retransformClasses(Object.class);
            throw new RuntimeException("UnmodifiableClassException not thrown by retransformClasses");
        } catch (UnmodifiableClassException e) {
            // expected
        }
        if (transformer.transformObjectInvoked()) {
            throw new RuntimeException();
        }
    }
}
