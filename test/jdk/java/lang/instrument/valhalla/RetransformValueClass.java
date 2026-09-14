/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test for value class retransformation
 *
 * @library /test/lib
 * @modules java.instrument
 * @enablePreview
 *
 * @run main RetransformValueClass buildAgent
 *
 * @run main/othervm -javaagent:testAgent.jar RetransformValueClass
 */

import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import jdk.test.lib.helpers.ClassFileInstaller;

/*
 * The test verifies Instrumentation.retransformClasses() (and JVMTI function RetransformClasses)
 * works with value classes (i.e. JVMTI JvmtiClassFileReconstituter correctly restores class bytes).
 */

value class ValueClass {
    public int f1;
    public String f2;

    public ValueClass(int v1, String v2) {
        f1 = v1;
        f2 = v2;
    }
}

public class RetransformValueClass {

    public static void main (String[] args) throws Exception {
        if (args.length == 1 && "buildAgent".equals(args[0])) {
            buildAgent();
        } else {
            runTest();
        }
    }

    static void buildAgent() throws Exception {
        String manifest = "Premain-Class: RetransformValueClass\nCan-Redefine-Classes: true\nCan-Retransform-Classes: true\n";
        ClassFileInstaller.writeJar("testAgent.jar", ClassFileInstaller.Manifest.fromString(manifest), "RetransformValueClass");
    }

    // agent implementation
    static Instrumentation instrumentation;
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
    }

    // test implementation
    static final Class targetClass = ValueClass.class;
    static final String targetClassName = targetClass.getName();
    static boolean transformToOriginalClassbytes = false;

    static void runTest() throws Exception {
        instrumentation.addTransformer(new Transformer(), true);

        instrumentation.retransformClasses(targetClass);

        transformToOriginalClassbytes = true;
        instrumentation.retransformClasses(targetClass);
    }


    static class Transformer implements ClassFileTransformer {
        public Transformer() {
        }

        public byte[] transform(ClassLoader loader, String className,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

            if (className.equals(targetClassName)) {
                log("Transformer sees '" + className + "' of " + classfileBuffer.length + " bytes.");
                if (transformToOriginalClassbytes) {
                    return classfileBuffer;
                } else {
                    return  null;
                }
            }
            return null;
        }
    }

    static void log(Object o) {
        System.out.println(String.valueOf(o));
    }
}
