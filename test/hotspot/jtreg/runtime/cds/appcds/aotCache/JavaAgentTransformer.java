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
 *
 */

import java.lang.System.Logger.Level;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

// This class is available on the classpath so it can be accessed by JavaAgentApp
public class JavaAgentTransformer  implements ClassFileTransformer {
    private static Instrumentation savedInstrumentation;
    private static final System.Logger LOGGER = System.getLogger(JavaAgentTransformer.class.getName());

    public static void premain(String agentArguments, Instrumentation instrumentation) {
        System.out.println("JavaAgentTransformer.premain() is called");
        instrumentation.addTransformer(new JavaAgentTransformer(), /*canRetransform=*/true);
        savedInstrumentation = instrumentation;

        LOGGER.log(Level.WARNING, "JavaAgentTransformer::premain() is finished");
    }

    public static Instrumentation getInstrumentation() {
        return savedInstrumentation;
    }

    public static void agentmain(String args, Instrumentation inst) throws Exception {
        premain(args, inst);
    }

    public byte[] transform(ClassLoader loader, String name, Class<?> classBeingRedefined,
                            ProtectionDomain pd, byte[] buffer) throws IllegalClassFormatException {
        if (name.equals("JavaAgentApp$ShouldBeTransformed")) {
            System.out.println("Transforming: " + name + "; Class<?> = " + classBeingRedefined);
            try {
                replace(buffer, "XXXX", "YYYY");
            } catch (Throwable t) {
                t.printStackTrace();
            }
            Thread.dumpStack();
            return buffer;
        }
        return null;
    }

    static void replace(byte[] buffer, String from, String to) {
        int n = Util.replace(buffer, from, to);
        System.out.println("..... replaced " + n + " occurrence(s) of '" + from + "' to '" + to + "'");
    }
}
