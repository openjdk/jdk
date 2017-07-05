/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static java.lang.String.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.Type;
import jdk.vm.ci.common.*;
import sun.misc.*;

/**
 * A {@link ClassVisitor} that verifies {@link HotSpotVMConfig} does not access {@link Unsafe} from
 * any of its non-static, non-constructor methods. This ensures that a deserialized
 * {@link HotSpotVMConfig} object does not perform any unsafe reads on addresses that are only valid
 * in the context in which the object was serialized. Note that this does not catch cases where a
 * client uses an address stored in a {@link HotSpotVMConfig} field.
 */
final class HotSpotVMConfigVerifier extends ClassVisitor {

    public static boolean check() {
        Class<?> cls = HotSpotVMConfig.class;
        String classFilePath = "/" + cls.getName().replace('.', '/') + ".class";
        try {
            InputStream classfile = cls.getResourceAsStream(classFilePath);
            ClassReader cr = new ClassReader(Objects.requireNonNull(classfile, "Could not find class file for " + cls.getName()));
            ClassVisitor cv = new HotSpotVMConfigVerifier();
            cr.accept(cv, 0);
            return true;
        } catch (IOException e) {
            throw new JVMCIError(e);
        }
    }

    /**
     * Source file context for error reporting.
     */
    String sourceFile = null;

    /**
     * Line number for error reporting.
     */
    int lineNo = -1;

    private static Class<?> resolve(String name) {
        try {
            return Class.forName(name.replace('/', '.'));
        } catch (ClassNotFoundException e) {
            throw new JVMCIError(e);
        }
    }

    HotSpotVMConfigVerifier() {
        super(Opcodes.ASM5);
    }

    @Override
    public void visitSource(String source, String debug) {
        this.sourceFile = source;
    }

    void verify(boolean condition, String message) {
        if (!condition) {
            error(message);
        }
    }

    void error(String message) {
        String errorMessage = format("%s:%d: %s is not allowed in the context of compilation replay. The unsafe access should be moved into the %s constructor and the result cached in a field",
                        sourceFile, lineNo, message, HotSpotVMConfig.class.getSimpleName());
        throw new JVMCIError(errorMessage);

    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String d, String signature, String[] exceptions) {
        if (!Modifier.isStatic(access) && Modifier.isPublic(access) && !name.equals("<init>")) {
            return new MethodVisitor(Opcodes.ASM5) {

                @Override
                public void visitLineNumber(int line, Label start) {
                    lineNo = line;
                }

                private Executable resolveMethod(String owner, String methodName, String methodDesc) {
                    Class<?> declaringClass = resolve(owner);
                    while (declaringClass != null) {
                        if (methodName.equals("<init>")) {
                            for (Constructor<?> c : declaringClass.getDeclaredConstructors()) {
                                if (methodDesc.equals(Type.getConstructorDescriptor(c))) {
                                    return c;
                                }
                            }
                        } else {
                            Type[] argumentTypes = Type.getArgumentTypes(methodDesc);
                            for (Method m : declaringClass.getDeclaredMethods()) {
                                if (m.getName().equals(methodName)) {
                                    if (Arrays.equals(argumentTypes, Type.getArgumentTypes(m))) {
                                        if (Type.getReturnType(methodDesc).equals(Type.getReturnType(m))) {
                                            return m;
                                        }
                                    }
                                }
                            }
                        }
                        declaringClass = declaringClass.getSuperclass();
                    }
                    throw new NoSuchMethodError(owner + "." + methodName + methodDesc);
                }

                /**
                 * Checks whether a given method is allowed to be called.
                 */
                private boolean checkInvokeTarget(Executable method) {
                    if (method.getDeclaringClass().equals(Unsafe.class)) {
                        return false;
                    }
                    return true;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
                    Executable callee = resolveMethod(owner, methodName, methodDesc);
                    verify(checkInvokeTarget(callee), "invocation of " + callee);
                }
            };
        } else {
            return null;
        }
    }
}
