/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import jdk.internal.access.foreign.MemoryAddressProxy;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ConstantDynamic;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.util.TraceClassVisitor;
import jdk.internal.vm.annotation.ForceInline;
import sun.security.action.GetBooleanAction;
import sun.security.action.GetPropertyAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;

import static jdk.internal.org.objectweb.asm.Opcodes.AALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ARETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.BIPUSH;
import static jdk.internal.org.objectweb.asm.Opcodes.CHECKCAST;
import static jdk.internal.org.objectweb.asm.Opcodes.GETFIELD;
import static jdk.internal.org.objectweb.asm.Opcodes.GETSTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_1;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_2;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_3;
import static jdk.internal.org.objectweb.asm.Opcodes.ILOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static jdk.internal.org.objectweb.asm.Opcodes.LALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.LASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.LLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.NEWARRAY;
import static jdk.internal.org.objectweb.asm.Opcodes.PUTFIELD;
import static jdk.internal.org.objectweb.asm.Opcodes.PUTSTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP;
import static jdk.internal.org.objectweb.asm.Opcodes.SIPUSH;
import static jdk.internal.org.objectweb.asm.Opcodes.T_LONG;
import static jdk.internal.org.objectweb.asm.Opcodes.V14;

class MemoryAccessVarHandleGenerator {
    private static final String DEBUG_DUMP_CLASSES_DIR_PROPERTY = "jdk.internal.foreign.ClassGenerator.DEBUG_DUMP_CLASSES_DIR";

    private static final boolean DEBUG =
        GetBooleanAction.privilegedGetProperty("jdk.internal.foreign.ClassGenerator.DEBUG");

    private static final Class<?> BASE_CLASS = MemoryAccessVarHandleBase.class;

    private static final HashMap<Class<?>, Class<?>> helperClassCache;

    private final static MethodType OFFSET_OP_TYPE;

    private final static MethodHandle ADD_OFFSETS_HANDLE;
    private final static MethodHandle MUL_OFFSETS_HANDLE;

    static {
        helperClassCache = new HashMap<>();
        helperClassCache.put(byte.class, MemoryAccessVarHandleByteHelper.class);
        helperClassCache.put(short.class, MemoryAccessVarHandleShortHelper.class);
        helperClassCache.put(char.class, MemoryAccessVarHandleCharHelper.class);
        helperClassCache.put(int.class, MemoryAccessVarHandleIntHelper.class);
        helperClassCache.put(long.class, MemoryAccessVarHandleLongHelper.class);
        helperClassCache.put(float.class, MemoryAccessVarHandleFloatHelper.class);
        helperClassCache.put(double.class, MemoryAccessVarHandleDoubleHelper.class);

        OFFSET_OP_TYPE = MethodType.methodType(long.class, long.class, long.class, MemoryAddressProxy.class);

        try {
            ADD_OFFSETS_HANDLE = MethodHandles.Lookup.IMPL_LOOKUP.findStatic(MemoryAddressProxy.class, "addOffsets", OFFSET_OP_TYPE);
            MUL_OFFSETS_HANDLE = MethodHandles.Lookup.IMPL_LOOKUP.findStatic(MemoryAddressProxy.class, "multiplyOffsets", OFFSET_OP_TYPE);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static final File DEBUG_DUMP_CLASSES_DIR;

    static {
        String path = GetPropertyAction.privilegedGetProperty(DEBUG_DUMP_CLASSES_DIR_PROPERTY);
        if (path == null) {
            DEBUG_DUMP_CLASSES_DIR = null;
        } else {
            DEBUG_DUMP_CLASSES_DIR = new File(path);
        }
    }

    private final String implClassName;
    private final int dimensions;
    private final Class<?> carrier;
    private final Class<?> helperClass;
    private final VarForm form;
    private final Object[] classData;

    MemoryAccessVarHandleGenerator(Class<?> carrier, int dims) {
        this.dimensions = dims;
        this.carrier = carrier;
        Class<?>[] components = new Class<?>[dimensions];
        Arrays.fill(components, long.class);
        this.form = new VarForm(BASE_CLASS, MemoryAddressProxy.class, carrier, components);
        this.helperClass = helperClassCache.get(carrier);
        this.implClassName = helperClass.getName().replace('.', '/') + dimensions;
        // live constants
        Class<?>[] intermediate = new Class<?>[dimensions];
        Arrays.fill(intermediate, long.class);
        this.classData = new Object[] { carrier, intermediate, ADD_OFFSETS_HANDLE, MUL_OFFSETS_HANDLE };
    }

    /*
     * Generate a VarHandle memory access factory.
     * The factory has type (ZJJ[J)VarHandle.
     */
    MethodHandle generateHandleFactory() {
        byte[] classBytes = generateClassBytes();
        if (DEBUG_DUMP_CLASSES_DIR != null) {
            debugWriteClassToFile(classBytes);
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClassWithClassData(classBytes, classData);
            Class<?> implCls = lookup.lookupClass();
            Class<?>[] components = new Class<?>[dimensions];
            Arrays.fill(components, long.class);

            VarForm form = new VarForm(implCls, MemoryAddressProxy.class, carrier, components);

            MethodType constrType = MethodType.methodType(void.class, VarForm.class, boolean.class, long.class, long.class, long.class, long[].class);
            MethodHandle constr = lookup.findConstructor(implCls, constrType);
            constr = MethodHandles.insertArguments(constr, 0, form);
            return constr;
        } catch (Throwable ex) {
            debugPrintClass(classBytes);
            throw new AssertionError(ex);
        }
    }

    /*
     * Generate a specialized VarHandle class for given carrier
     * and access coordinates.
     */
    byte[] generateClassBytes() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        if (DEBUG) {
            System.out.println("Generating header implementation class");
        }

        cw.visit(V14, ACC_PUBLIC | ACC_SUPER, implClassName, null, Type.getInternalName(BASE_CLASS), null);

        //add dimension fields
        for (int i = 0; i < dimensions; i++) {
            cw.visitField(ACC_PRIVATE | ACC_FINAL, "dim" + i, "J", null, null);
        }

        addStaticInitializer(cw);

        addConstructor(cw);

        addAccessModeTypeMethod(cw);

        addStridesAccessor(cw);

        addCarrierAccessor(cw);

        for (VarHandle.AccessMode mode : VarHandle.AccessMode.values()) {
            addAccessModeMethodIfNeeded(mode, cw);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    void addStaticInitializer(ClassWriter cw) {
        // carrier and intermediate
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "carrier", Class.class.descriptorString(), null, null);
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "intermediate", Class[].class.descriptorString(), null, null);
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "addHandle", MethodHandle.class.descriptorString(), null, null);
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "mulHandle", MethodHandle.class.descriptorString(), null, null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        // extract class data in static final fields
        MethodType mtype = MethodType.methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class);
        Handle bsm = new Handle(H_INVOKESTATIC, Type.getInternalName(MethodHandles.class), "classData",
                    mtype.descriptorString(), false);
        ConstantDynamic dynamic = new ConstantDynamic("classData", Object[].class.descriptorString(), bsm);
        mv.visitLdcInsn(dynamic);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Object[].class));
        mv.visitVarInsn(ASTORE, 0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Class.class));
        mv.visitFieldInsn(PUTSTATIC, implClassName, "carrier", Class.class.descriptorString());
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Class[].class));
        mv.visitFieldInsn(PUTSTATIC, implClassName, "intermediate", Class[].class.descriptorString());
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_2);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(MethodHandle.class));
        mv.visitFieldInsn(PUTSTATIC, implClassName, "addHandle", MethodHandle.class.descriptorString());
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_3);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(MethodHandle.class));
        mv.visitFieldInsn(PUTSTATIC, implClassName, "mulHandle", MethodHandle.class.descriptorString());
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    void addConstructor(ClassWriter cw) {
        MethodType constrType = MethodType.methodType(void.class, VarForm.class, boolean.class, long.class, long.class, long.class, long[].class);
        MethodVisitor mv = cw.visitMethod(0, "<init>", constrType.toMethodDescriptorString(), null, null);
        mv.visitCode();
        //super call
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(VarForm.class));
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(LLOAD, 3);
        mv.visitVarInsn(LLOAD, 5);
        mv.visitVarInsn(LLOAD, 7);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(BASE_CLASS), "<init>",
                MethodType.methodType(void.class, VarForm.class, boolean.class, long.class, long.class, long.class).toMethodDescriptorString(), false);
        //init dimensions
        for (int i = 0 ; i < dimensions ; i++) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 9);
            mv.visitLdcInsn(i);
            mv.visitInsn(LALOAD);
            mv.visitFieldInsn(PUTFIELD, implClassName, "dim" + i, "J");
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    void addAccessModeTypeMethod(ClassWriter cw) {
        MethodType modeMethType = MethodType.methodType(MethodType.class, VarHandle.AccessMode.class);
        MethodVisitor mv = cw.visitMethod(ACC_FINAL, "accessModeTypeUncached", modeMethType.toMethodDescriptorString(), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(GETFIELD, Type.getInternalName(VarHandle.AccessMode.class), "at", VarHandle.AccessType.class.descriptorString());
        mv.visitLdcInsn(Type.getType(MemoryAddressProxy.class));
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Class.class));
        mv.visitFieldInsn(GETSTATIC, implClassName, "carrier", Class.class.descriptorString());
        mv.visitFieldInsn(GETSTATIC, implClassName, "intermediate", Class[].class.descriptorString());

        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(VarHandle.AccessType.class),
                "accessModeType", MethodType.methodType(MethodType.class, Class.class, Class.class, Class[].class).toMethodDescriptorString(), false);

        mv.visitInsn(ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    void addAccessModeMethodIfNeeded(VarHandle.AccessMode mode, ClassWriter cw) {
        String methName = mode.methodName();
        MethodType methType = form.getMethodType(mode.at.ordinal())
                .insertParameterTypes(0, VarHandle.class);

        try {
            MethodType helperType = methType.insertParameterTypes(2, long.class);
            if (dimensions > 0) {
                helperType = helperType.dropParameterTypes(3, 3 + dimensions);
            }
            //try to resolve...
            String helperMethodName = methName + "0";
            MethodHandles.Lookup.IMPL_LOOKUP
                    .findStatic(helperClass,
                            helperMethodName,
                            helperType);


            MethodVisitor mv = cw.visitMethod(ACC_STATIC, methName, methType.toMethodDescriptorString(), null, null);
            mv.visitAnnotation(Type.getDescriptor(ForceInline.class), true);
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 0); // handle impl
            mv.visitVarInsn(ALOAD, 1); // receiver

            // offset calculation
            int slot = 2;
            mv.visitVarInsn(ALOAD, 0); // load recv
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(BASE_CLASS));
            mv.visitFieldInsn(GETFIELD, Type.getInternalName(BASE_CLASS), "offset", "J");
            for (int i = 0 ; i < dimensions ; i++) {
                // load ADD MH
                mv.visitFieldInsn(GETSTATIC, implClassName, "addHandle", MethodHandle.class.descriptorString());

                //fixup stack so that ADD MH ends up bottom
                mv.visitInsn(Opcodes.DUP_X2);
                mv.visitInsn(Opcodes.POP);

                // load MUL MH
                mv.visitFieldInsn(GETSTATIC, implClassName, "mulHandle", MethodHandle.class.descriptorString());
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(MethodHandle.class));

                mv.visitVarInsn(ALOAD, 0); // load recv
                mv.visitTypeInsn(CHECKCAST, implClassName);
                mv.visitFieldInsn(GETFIELD, implClassName, "dim" + i, "J");
                mv.visitVarInsn(LLOAD, slot);

                mv.visitVarInsn(ALOAD, 1); // receiver
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(MemoryAddressProxy.class));

                //MUL
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MethodHandle.class), "invokeExact",
                        OFFSET_OP_TYPE.toMethodDescriptorString(), false);

                mv.visitVarInsn(ALOAD, 1); // receiver
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(MemoryAddressProxy.class));

                //ADD
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(MethodHandle.class), "invokeExact",
                        OFFSET_OP_TYPE.toMethodDescriptorString(), false);
                slot += 2;
            }

            for (int i = 2 + dimensions; i < methType.parameterCount() ; i++) {
                Class<?> param = methType.parameterType(i);
                mv.visitVarInsn(loadInsn(param), slot); // load index
                slot += getSlotsForType(param);
            }

            //call helper
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(helperClass), helperMethodName,
                    helperType.toMethodDescriptorString(), false);

            mv.visitInsn(returnInsn(helperType.returnType()));

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        } catch (ReflectiveOperationException ex) {
            //not found, skip
        }
    }

    void addStridesAccessor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_FINAL, "strides", "()[J", null, null);
        mv.visitCode();
        iConstInsn(mv, dimensions);
        mv.visitIntInsn(NEWARRAY, T_LONG);

        for (int i = 0 ; i < dimensions ; i++) {
            mv.visitInsn(DUP);
            iConstInsn(mv, i);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, implClassName, "dim" + i, "J");
            mv.visitInsn(LASTORE);
        }

        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    void addCarrierAccessor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_FINAL, "carrier", "()Ljava/lang/Class;", null, null);
        mv.visitCode();
        mv.visitFieldInsn(GETSTATIC, implClassName, "carrier", Class.class.descriptorString());
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    // shared code generation helpers

    private static int getSlotsForType(Class<?> c) {
        if (c == long.class || c == double.class) {
            return 2;
        }
        return 1;
    }

    /**
     * Emits an actual return instruction conforming to the given return type.
     */
    private int returnInsn(Class<?> type) {
        return switch (LambdaForm.BasicType.basicType(type)) {
            case I_TYPE -> Opcodes.IRETURN;
            case J_TYPE -> Opcodes.LRETURN;
            case F_TYPE -> Opcodes.FRETURN;
            case D_TYPE -> Opcodes.DRETURN;
            case L_TYPE -> Opcodes.ARETURN;
            case V_TYPE -> RETURN;
        };
    }

    private int loadInsn(Class<?> type) {
        return switch (LambdaForm.BasicType.basicType(type)) {
            case I_TYPE -> Opcodes.ILOAD;
            case J_TYPE -> LLOAD;
            case F_TYPE -> Opcodes.FLOAD;
            case D_TYPE -> Opcodes.DLOAD;
            case L_TYPE -> Opcodes.ALOAD;
            case V_TYPE -> throw new IllegalStateException("Cannot load void");
        };
    }

    private static void iConstInsn(MethodVisitor mv, int i) {
        switch (i) {
            case -1, 0, 1, 2, 3, 4, 5:
                mv.visitInsn(ICONST_0 + i);
                break;
            default:
                if(i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
                    mv.visitIntInsn(BIPUSH, i);
                } else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
                    mv.visitIntInsn(SIPUSH, i);
                } else {
                    mv.visitLdcInsn(i);
                }
        }
    }

    // debug helpers

    private static String debugPrintClass(byte[] classFile) {
        ClassReader cr = new ClassReader(classFile);
        StringWriter sw = new StringWriter();
        cr.accept(new TraceClassVisitor(new PrintWriter(sw)), 0);
        return sw.toString();
    }

    private void debugWriteClassToFile(byte[] classFile) {
        File file = new File(DEBUG_DUMP_CLASSES_DIR, implClassName + ".class");

        if (DEBUG) {
            System.err.println("Dumping class " + implClassName + " to " + file);
        }

        try {
            debugWriteDataToFile(classFile, file);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write class " + implClassName + " to file " + file);
        }
    }

    private void debugWriteDataToFile(byte[] data, File file) {
        if (file.exists()) {
            file.delete();
        }
        if (file.exists()) {
            throw new RuntimeException("Failed to remove pre-existing file " + file);
        }

        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        if (!parent.exists()) {
            throw new RuntimeException("Failed to create " + parent);
        }
        if (!parent.isDirectory()) {
            throw new RuntimeException(parent + " is not a directory");
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write class " + implClassName + " to file " + file);
        }
    }
}
