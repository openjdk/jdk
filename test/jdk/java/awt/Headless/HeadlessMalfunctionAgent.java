/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * This agent removes the isHeadless method from java.awt.GraphicsEnvironment.
 */
public class HeadlessMalfunctionAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader,
                                    String className,
                                    Class<?> classBeingRedefined,
                                    ProtectionDomain pd,
                                    byte[] cb) {
                if (!"java/awt/GraphicsEnvironment".equals(className)) {
                    return null;
                }
                System.out.println("Transforming java.awt.GraphicsEnvironment.");
                try {
                    return ClassFile.of().transformClass(ClassFile.of().parse(cb), (classBuilder, element) -> {
                        if (element instanceof MethodModel method) {
                            if ("isHeadless".equals(method.methodName().stringValue()) &&
                                    "()Z".equals(method.methodType().stringValue())) {
                                System.out.println("isHeadless removed from java.awt.GraphicsEnvironment.");
                                return;
                            }
                        }
                        classBuilder.with(element);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
        });
    }
}
