/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.bytecode;

import static org.graalvm.compiler.bytecode.Bytecodes.ATHROW;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static org.graalvm.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;

import java.lang.annotation.Annotation;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Utilities for working around the absence of method annotations and parameter annotations on
 * bridge methods where the bridged methods have method annotations or parameter annotations. Not
 * all Java compilers copy method annotations and parameter annotations to bridge methods.
 *
 * @see <a href="http://bugs.java.com/view_bug.do?bug_id=6695379">6695379</a>
 * @see <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=495396">495396</a>
 * @see <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=427745">427745</a>
 */
public class BridgeMethodUtils {

    /**
     * Gets the method bridged to by a {@linkplain ResolvedJavaMethod#isBridge() bridge} method. The
     * value returned is the method called by {@code method} that has the same name as
     * {@code bridge}.
     *
     * @param bridge a bridge method
     * @return the method called by {@code bridge} whose name is the same as
     *         {@code bridge.getName()}
     */
    public static ResolvedJavaMethod getBridgedMethod(ResolvedJavaMethod bridge) {
        assert bridge.isBridge();
        Bytecode code = new ResolvedJavaMethodBytecode(bridge);
        BytecodeStream stream = new BytecodeStream(code.getCode());
        int opcode = stream.currentBC();
        ResolvedJavaMethod bridged = null;
        boolean calledAbstractMethodErrorConstructor = false;
        while (opcode != Bytecodes.END) {
            switch (opcode) {
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEINTERFACE: {
                    int cpi = stream.readCPI();
                    ConstantPool cp = code.getConstantPool();
                    cp.loadReferencedType(cpi, opcode);
                    ResolvedJavaMethod method = (ResolvedJavaMethod) cp.lookupMethod(cpi, opcode);
                    if (method.getName().equals(bridge.getName())) {
                        if (!assertionsEnabled()) {
                            return method;
                        }
                        assert bridged == null || bridged.equals(method) : String.format("Found calls to different methods named %s in bridge method %s%n  callee 1: %s%n  callee 2: %s",
                                        bridge.getName(), bridge.format("%R %H.%n(%P)"), bridged.format("%R %H.%n(%P)"), method.format("%R %H.%n(%P)"));
                        bridged = method;
                    } else if (method.getName().equals("<init>") && method.getDeclaringClass().getName().equals("Ljava/lang/AbstractMethodError;")) {
                        calledAbstractMethodErrorConstructor = true;
                    }
                    break;
                }
                case ATHROW: {
                    if (calledAbstractMethodErrorConstructor) {
                        // This is a miranda method
                        return null;
                    }
                }
            }
            stream.next();
            opcode = stream.currentBC();
        }
        if (bridged == null) {
            String dis = new BytecodeDisassembler().disassemble(bridge);
            throw new InternalError(String.format("Couldn't find method bridged by %s:%n%s", bridge.format("%R %H.%n(%P)"), dis));
        }
        return bridged;
    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    /**
     * A helper for {@link ResolvedJavaMethod#getAnnotation(Class)} that handles the absence of
     * annotations on bridge methods where the bridged method has annotations.
     */
    public static <T extends Annotation> T getAnnotation(Class<T> annotationClass, ResolvedJavaMethod method) {
        T a = method.getAnnotation(annotationClass);
        if (a == null && method.isBridge()) {
            ResolvedJavaMethod bridged = getBridgedMethod(method);
            if (bridged != null) {
                a = bridged.getAnnotation(annotationClass);
            }
        }
        return a;
    }

    /**
     * A helper for {@link ResolvedJavaMethod#getAnnotations()} that handles the absence of
     * annotations on bridge methods where the bridged method has annotations.
     */
    public static Annotation[] getAnnotations(ResolvedJavaMethod method) {
        Annotation[] a = method.getAnnotations();
        if (a.length == 0 && method.isBridge()) {
            ResolvedJavaMethod bridged = getBridgedMethod(method);
            if (bridged != null) {
                a = bridged.getAnnotations();
            }
        }
        return a;
    }

    /**
     * A helper for {@link ResolvedJavaMethod#getDeclaredAnnotations()} that handles the absence of
     * annotations on bridge methods where the bridged method has annotations.
     */
    public static Annotation[] getDeclaredAnnotations(ResolvedJavaMethod method) {
        Annotation[] a = method.getAnnotations();
        if (a.length == 0 && method.isBridge()) {
            ResolvedJavaMethod bridged = getBridgedMethod(method);
            if (bridged != null) {
                a = bridged.getDeclaredAnnotations();
            }
        }
        return a;
    }

    /**
     * A helper for {@link ResolvedJavaMethod#getParameterAnnotations()} that handles the absence of
     * parameter annotations on bridge methods where the bridged method has parameter annotations.
     */
    public static Annotation[][] getParameterAnnotations(ResolvedJavaMethod method) {
        Annotation[][] a = method.getParameterAnnotations();
        if (a.length == 0 && method.isBridge()) {
            ResolvedJavaMethod bridged = getBridgedMethod(method);
            if (bridged != null) {
                a = bridged.getParameterAnnotations();
            }
        }
        return a;
    }

    /**
     * A helper for {@link ResolvedJavaMethod#getParameterAnnotation(Class, int)} that handles the
     * absence of parameter annotations on bridge methods where the bridged method has parameter
     * annotations.
     */
    public static <T extends Annotation> T getParameterAnnotation(Class<T> annotationClass, int parameterIndex, ResolvedJavaMethod method) {
        T a = method.getParameterAnnotation(annotationClass, parameterIndex);
        if (a == null && method.isBridge()) {
            ResolvedJavaMethod bridged = getBridgedMethod(method);
            if (bridged != null) {
                a = bridged.getParameterAnnotation(annotationClass, parameterIndex);
            }
        }
        return a;
    }
}
