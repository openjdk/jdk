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

import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.ClassTransform;
import java.lang.constant.ConstantDescs;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.lang.System.Logger.Level;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.AccessFlag;
import java.security.ProtectionDomain;

// This class is available on the classpath so it can be accessed by JavaAgentRetransformApp
public class JavaAgentRetransformer  implements ClassFileTransformer {
    private static Instrumentation savedInstrumentation;
    private static final System.Logger LOGGER = System.getLogger(JavaAgentRetransformer.class.getName());
    private static Class<?> current = null;
    public static void premain(String agentArguments, Instrumentation instrumentation) {
        System.out.println("JavaAgentRetransformer.premain() is called");
        instrumentation.addTransformer(new JavaAgentRetransformer(), /*canRetransform=*/true);
        savedInstrumentation = instrumentation;

        LOGGER.log(Level.WARNING, "JavaAgentRetransformer::premain() is finished");
    }

    public static Instrumentation getInstrumentation() {
        return savedInstrumentation;
    }

    public static void agentmain(String args, Instrumentation inst) throws Exception {
        premain(args, inst);
    }

    public static void doRetransform(Class<?> clazz) {
        current = clazz;
        Class<?> classes[] = new Class<?>[1];
        classes[0] = clazz;
        try {
            savedInstrumentation.retransformClasses(classes);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public byte[] transform(ClassLoader loader, String name, Class<?> classBeingRedefined,
                            ProtectionDomain pd, byte[] buffer) throws IllegalClassFormatException {
        // only accept retransform requests for the current class
        if (classBeingRedefined == null) {
            return null;
        }
        if (classBeingRedefined == current) {
            System.out.println("Transforming: " + name);
            try {
                buffer = transformDoWork(buffer);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            Thread.dumpStack();
            return buffer;
        }
        return null;
    }

    final static ClassDesc CD_int = ClassDesc.ofDescriptor("I");
    final static MethodTypeDesc MTD_int_int = MethodTypeDesc.of(CD_int, CD_int);

    // replace the body of any public void instance method that looks
    // like "int doWork(int arg0)" with "return arg0"
    static byte[] transformDoWork(byte[] buffer) {
        ClassTransform t = (builder, element) -> {
            if (element instanceof MethodModel method &&
                method.methodName().equalsString("doWork") &&
                method.methodTypeSymbol() == MTD_int_int &&
                method.flags().has(AccessFlag.PUBLIC) &&
                !method.flags().has(AccessFlag.STATIC)) {
                builder.withMethodBody("doWork",
                                       MTD_int_int,
                                       ClassFile.ACC_PUBLIC,
                                       cob -> cob.iload(0).ireturn());
            } else {
                builder.with(element);  // leaves the element in place
            }
        };
        var c = ClassFile.of();
        byte[] newBytes = c.transformClass(c.parse(buffer), t);
        return newBytes;
    }
}
