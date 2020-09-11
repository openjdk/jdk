/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * @test
 * @bug 8241390
 * @summary Test recursively redefines the same class. The test hangs if
 * a deadlock happens.
 * @run build TransformerDeadlockTest
 * @run shell MakeAgentJAR.sh TransformerDeadlockTest TransformerDeadlockTest
 * @run main/othervm/native -javaagent:TransformerDeadlockTest.jar TransformerDeadlockTest
 */
public class TransformerDeadlockTest implements ClassFileTransformer {
    private static Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer)
            throws IllegalClassFormatException {

        if (!TransformerDeadlockTest.class.getName().replace(".", "/").equals(className)) {
            return null;
        }
        invokeRetransform();
        return classfileBuffer;

    }

    public static void main(String[] args) throws Exception {
        instrumentation.addTransformer(new TransformerDeadlockTest(), true);

        try {
            instrumentation.retransformClasses(TransformerDeadlockTest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void invokeRetransform() {
        try {
            instrumentation.retransformClasses(TransformerDeadlockTest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
        }
    }
}
