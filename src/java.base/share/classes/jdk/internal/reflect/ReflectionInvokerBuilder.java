/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.reflect;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ConstantDynamic;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.misc.VM;

import static jdk.internal.org.objectweb.asm.Opcodes.*;
import static java.lang.invoke.MethodType.*;

/**
 * ReflectionInvokerBuilder generates the bytecode for a hidden class that
 * implements MHInvoker or VHInvoker for fast invocation. It loads
 * a MethodHandle or VarHandle from the class data as a constant for
 * better JIT optimization.
 *
 * @see java.lang.invoke.MethodHandles#classData(MethodHandles.Lookup, String, Class)
 */
class ReflectionInvokerBuilder extends ClassWriter {
    private static final int CLASSFILE_VERSION = VM.classFileVersion();
    private static final String OBJECT_CLS = "java/lang/Object";
    private static final String MHS_CLS = "java/lang/invoke/MethodHandles";
    private static final String MH_CLS = "java/lang/invoke/MethodHandle";
    private static final String VH_CLS = "java/lang/invoke/VarHandle";
    private static final String CLASS_DATA_BSM_DESCR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;";
    private static final String[] MH_INVOKER_INTF = new String[] { "jdk/internal/reflect/MHInvoker" };
    private static final String[] VH_INVOKER_INTF = new String[] {  "jdk/internal/reflect/VHInvoker" };
    private static final String[] THROWS_THROWABLE = new String[] { "java/lang/Throwable" };

    private final String classname;
    private final ConstantDynamic classDataCondy;

    /**
     * A builder to generate a class file to access class data
     */
    ReflectionInvokerBuilder(String classname, Class<?> classDataType) {
        super(ClassWriter.COMPUTE_FRAMES);
        this.classname = classname;
        Handle bsm = new Handle(H_INVOKESTATIC, MHS_CLS, "classData",
                                CLASS_DATA_BSM_DESCR,
                                false);
        this.classDataCondy = new ConstantDynamic("_", classDataType.descriptorString(), bsm);
    }

    byte[] buildVarHandleInvoker(Field field) {
        visit(CLASSFILE_VERSION, ACC_FINAL, classname, null, OBJECT_CLS, VH_INVOKER_INTF);
        addConstructor();

        Class<?> type = field.getType().isPrimitive() ? field.getType() : Object.class;
        var isStatic = Modifier.isStatic(field.getModifiers());
        var isVolatile = Modifier.isVolatile(field.getModifiers());
        addGetter(type, isStatic, isVolatile);
        addSetter(type, isStatic, isVolatile);
        visitEnd();
        return toByteArray();
    }

    byte[] buildMethodHandleInvoker(Method method, MethodType mtype, boolean hasCallerParameter) {
        visit(CLASSFILE_VERSION, ACC_FINAL, classname, null, OBJECT_CLS, MH_INVOKER_INTF);
        addConstructor();

        var isStatic = Modifier.isStatic(method.getModifiers());
        // check if this method type is specialized form or not
        int lastArgIndex = mtype.parameterCount() - 1 - (hasCallerParameter ? 1 : 0);
        if (lastArgIndex >= 0 && mtype.parameterType(lastArgIndex) == Object[].class) {
            addInvokeMethod(mtype);
        } else {
            addSpecializedInvokeMethod(mtype, true, hasCallerParameter, method.getParameterCount());
        }
        visitEnd();
        return toByteArray();
    }

    byte[] buildMethodHandleInvoker(Constructor<?> ctor, MethodType mtype) {
        visit(CLASSFILE_VERSION, ACC_FINAL, classname, null, OBJECT_CLS, MH_INVOKER_INTF);
        addConstructor();

        int paramCount = ctor.getParameterCount();
        if (mtype.lastParameterType() == Object[].class) {
            addInvokeMethod(mtype);
        } else {
            addSpecializedInvokeMethod(mtype, false /* no receiver */, false, paramCount);
        }
        visitEnd();
        return toByteArray();
    }

    private void addConstructor() {
        MethodVisitor mv = visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, OBJECT_CLS, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void addGetter(Class<?> type, boolean isStatic, boolean isVolatile) {
        MethodType mtype = isStatic ? methodType(type) : methodType(type, Object.class);
        MethodVisitor mv = visitMethod(ACC_PUBLIC,
                                       methodName("get", type),
                                       mtype.descriptorString(), null, THROWS_THROWABLE);
        mv.visitCode();
        mv.visitLdcInsn(classDataCondy);    // load VarHandle constant
        mv.visitTypeInsn(CHECKCAST, VH_CLS);
        if (!isStatic) {
            mv.visitVarInsn(ALOAD, 1);
        }
        mv.visitMethodInsn(INVOKEVIRTUAL, VH_CLS, isVolatile ? "getVolatile" : "get",
                           mtype.descriptorString(), false);
        emitReturn(mv, type);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void addSetter(Class<?> type, boolean isStatic, boolean isVolatile) {
        MethodType mtype = isStatic ? methodType(void.class, type) : methodType(void.class, Object.class, type);
        MethodVisitor mv = visitMethod(ACC_PUBLIC,
                                       methodName("set", type),
                                       mtype.descriptorString(), null, THROWS_THROWABLE);
        mv.visitCode();
        mv.visitLdcInsn(classDataCondy);    // load VarHandle constant
        mv.visitTypeInsn(CHECKCAST, VH_CLS);
        if (isStatic) {
            emitLoad(mv, type, 1);
        } else {
            mv.visitVarInsn(ALOAD, 1);
            emitLoad(mv, type, 2);
        }
        mv.visitMethodInsn(INVOKEVIRTUAL, VH_CLS, isVolatile ? "setVolatile" : "set",
                           mtype.descriptorString(), false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void addSpecializedInvokeMethod(MethodType mtype, boolean hasReceiver, boolean hasCallerParameter, int paramCount) {
        assert mtype.lastParameterType() != Object[].class;
        MethodVisitor mv = visitMethod(ACC_PUBLIC, "invoke",
                                       mtype.descriptorString(), null, THROWS_THROWABLE);
        mv.visitCode();
        mv.visitLdcInsn(classDataCondy);    // load VarHandle constant
        mv.visitTypeInsn(CHECKCAST, MH_CLS);
        int slot = 1;   // first argument
        if (hasReceiver) {
            mv.visitVarInsn(ALOAD, slot++);
        }
        for (int i=0; i < paramCount; i++) {
            mv.visitVarInsn(ALOAD, slot++);
        }
        if (hasCallerParameter) {
            mv.visitVarInsn(ALOAD, slot++);
        }
        mv.visitMethodInsn(INVOKEVIRTUAL, MH_CLS, "invokeExact",
                           mtype.descriptorString(), false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void addInvokeMethod(MethodType mtype) {
        MethodVisitor mv = visitMethod(ACC_PUBLIC, "invoke",
                                       mtype.descriptorString(), null, THROWS_THROWABLE);
        mv.visitCode();
        mv.visitLdcInsn(classDataCondy);    // load VarHandle constant
        mv.visitTypeInsn(CHECKCAST, MH_CLS);
        for (int slot=1; slot <= mtype.parameterCount(); slot++) {
            mv.visitVarInsn(ALOAD, slot);
        }
        mv.visitMethodInsn(INVOKEVIRTUAL, MH_CLS, "invokeExact",
                           mtype.descriptorString(), false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static String methodName(String prefix, Class<?> type) {
        if (type == Boolean.TYPE) {
            return prefix + "Boolean";
        } else if (type == Byte.TYPE) {
            return prefix + "Byte";
        } else if (type == Short.TYPE) {
            return prefix + "Short";
        } else if (type == Character.TYPE) {
            return prefix + "Char";
        } else if (type == Integer.TYPE) {
            return prefix + "Int";
        } else if (type == Long.TYPE) {
            return prefix + "Long";
        } else if (type == Float.TYPE) {
            return prefix + "Float";
        } else if (type == Double.TYPE) {
            return prefix + "Double";
        } else {
            return prefix;
        }
    }

    private static void emitLoad(MethodVisitor mv, Class<?> type, int slot) {
        if (type == Boolean.TYPE) {
            mv.visitVarInsn(ILOAD, slot);
        } else if (type == Byte.TYPE) {
            mv.visitVarInsn(ILOAD, slot);
        } else if (type == Character.TYPE) {
            mv.visitVarInsn(ILOAD, slot);
        } else if (type == Short.TYPE) {
            mv.visitVarInsn(ILOAD, slot);
        } else if (type == Integer.TYPE) {
            mv.visitVarInsn(ILOAD, slot);
        } else if (type == Long.TYPE) {
            mv.visitVarInsn(LLOAD, slot);
        } else if (type == Float.TYPE) {
            mv.visitVarInsn(FLOAD, slot);
        } else if (type == Double.TYPE) {
            mv.visitVarInsn(DLOAD, slot);
        } else {
            mv.visitVarInsn(ALOAD, slot);
        }
    }

    private static void emitReturn(MethodVisitor mv, Class<?> type) {
        if (type == Boolean.TYPE) {
            mv.visitInsn(IRETURN);
        } else if (type == Byte.TYPE) {
            mv.visitInsn(IRETURN);
        } else if (type == Short.TYPE) {
            mv.visitInsn(IRETURN);
        } else if (type == Character.TYPE) {
            mv.visitInsn(IRETURN);
        } else if (type == Integer.TYPE) {
            mv.visitInsn(IRETURN);
        } else if (type == Long.TYPE) {
            mv.visitInsn(LRETURN);
        } else if (type == Float.TYPE) {
            mv.visitInsn(FRETURN);
        } else if (type == Double.TYPE) {
            mv.visitInsn(DRETURN);
        } else {
            mv.visitInsn(ARETURN);
        }
    }
}
