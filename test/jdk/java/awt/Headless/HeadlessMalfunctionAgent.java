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
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

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
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain pd, byte[] cb) {
                if ("java/awt/GraphicsEnvironment".equals(className)) {
                    System.out.println("Transforming java.awt.GraphicsEnvironment.");
                    try {
                        final ClassReader cr = new ClassReader(cb);
                        final ClassWriter cw = new ClassWriter(cr, 0);
                        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {

                            @Override
                            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                             String[] exceptions) {
                                if ("isHeadless".equals(name) && "()Z".equals(descriptor)) {
                                    System.out.println("isHeadless removed from java.awt.GraphicsEnvironment.");
                                    // WHACK! Remove the isHeadless method.
                                    return null;
                                }
                                return super.visitMethod(access, name, descriptor, signature, exceptions);
                            }
                        }, 0);
                        return cw.toByteArray();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        });
    }
}
