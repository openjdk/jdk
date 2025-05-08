/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.calls.common;

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * A class which patch InvokeDynamic class bytecode with invokydynamic
 instruction, rewriting "caller" method to call "callee" method using
 invokedynamic
 */
public class InvokeDynamicPatcher extends ClassVisitor {

    private static final String CLASS = InvokeDynamic.class.getName()
            .replace('.', '/');
    private static final String CALLER_METHOD_NAME = "caller";
    private static final String CALLEE_METHOD_NAME = "callee";
    private static final String NATIVE_CALLEE_METHOD_NAME = "calleeNative";
    private static final String BOOTSTRAP_METHOD_NAME = "bootstrapMethod";
    private static final String CALL_NATIVE_FIELD = "nativeCallee";
    private static final String CALL_NATIVE_FIELD_DESC = "Z";
    private static final String CALLEE_METHOD_DESC
            = "(L" + CLASS + ";IJFDLjava/lang/String;)Z";
    private static final String ASSERTTRUE_METHOD_DESC
            = "(ZLjava/lang/String;)V";
    private static final String ASSERTS_CLASS = "jdk/test/lib/Asserts";
    private static final String ASSERTTRUE_METHOD_NAME = "assertTrue";

    private static final String SRC = """
            package compiler.calls.common;

            import java.lang.invoke.CallSite;
            import java.lang.invoke.ConstantCallSite;
            import java.lang.invoke.MethodHandles;
            import java.lang.invoke.MethodType;

            /**
             * A test class checking InvokeDynamic instruction.
             * This is not quite "ready-to-use" class, since javac can't generate indy
             * directly(only as part of lambda init) so, this class bytecode should be
             * patched with method "caller" which uses indy. Other methods can be written in
             * java for easier support and readability.
             */

            public class InvokeDynamic extends CallsBase {
                private static final Object LOCK = new Object();

                public static void main(String args[]) {
                    new InvokeDynamic().runTest(args);
                }

                /**
                 * Caller method to call "callee" method. Must be overwritten with InvokeDynamicPatcher
                 */
                @Override
                public void caller() {
                }

                /**
                 * A bootstrap method for invokedynamic
                 * @param lookup a lookup object
                 * @param methodName methodName
                 * @param type method type
                 * @return CallSite for method
                 */
                public static CallSite bootstrapMethod(MethodHandles.Lookup lookup,
                        String methodName, MethodType type) throws IllegalAccessException,
                        NoSuchMethodException {
                    MethodType mtype = MethodType.methodType(boolean.class,
                            new Class<?>[]{int.class, long.class, float.class,
                                double.class, String.class});
                    return new ConstantCallSite(lookup.findVirtual(lookup.lookupClass(),
                            methodName, mtype));
                }

                /**
                 * A callee method, assumed to be called by "caller"
                 */
                public boolean callee(int param1, long param2, float param3, double param4,
                        String param5) {
                    calleeVisited = true;
                    CallsBase.checkValues(param1, param2, param3, param4, param5);
                    return true;
                }

                /**
                 * A native callee method, assumed to be called by "caller"
                 */
                public native boolean calleeNative(int param1, long param2, float param3,
                        double param4, String param5);

                /**
                 * Returns object to lock execution on
                 * @return lock object
                 */
                @Override
                protected Object getLockObject() {
                    return LOCK;
                }

                @Override
                protected void callerNative() {
                    throw new Error("No native call for invokedynamic");
                }
            }
            """;

    public static void main(String args[]) {
        ClassReader cr;
        Path filePath;
        try {
            filePath = Paths.get(InvokeDynamic.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).resolve(CLASS + ".class");
        } catch (URISyntaxException ex) {
            throw new Error("TESTBUG: Can't get code source" + ex, ex);
        }
        cr = new ClassReader(InMemoryJavaCompiler.compile(InvokeDynamic.class.getName(), SRC, "--release", "21"));
        ClassWriter cw = new ClassWriter(cr,
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cr.accept(new InvokeDynamicPatcher(Opcodes.ASM5, cw), 0);
        try {
            Files.write(filePath, cw.toByteArray(),
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public InvokeDynamicPatcher(int api, ClassWriter cw) {
        super(api, cw);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
            final String desc, final String signature,
            final String[] exceptions) {
        /* a code generate looks like
         *  0: aload_0
         *  1: ldc           #125  // int 1
         *  3: ldc2_w        #126  // long 2l
         *  6: ldc           #128  // float 3.0f
         *  8: ldc2_w        #129  // double 4.0d
         * 11: ldc           #132  // String 5
         * 13: aload_0
         * 14: getfield      #135  // Field nativeCallee:Z
         * 17: ifeq          28
         * 20: invokedynamic #181,  0            // InvokeDynamic #1:calleeNative:(Lcompiler/calls/common/InvokeDynamic;IJFDLjava/lang/String;)Z
         * 25: goto          33
         * 28: invokedynamic #183,  0            // InvokeDynamic #1:callee:(Lcompiler/calls/common/InvokeDynamic;IJFDLjava/lang/String;)Z
         * 33: ldc           #185                // String Call insuccessfull
         * 35: invokestatic  #191                // Method jdk/test/lib/Asserts.assertTrue:(ZLjava/lang/String;)V
         * 38: return
         *
         * or, using java-like pseudo-code
         * if (this.nativeCallee == false) {
         *     invokedynamic-call-return-value = invokedynamic-of-callee
         * } else {
         *     invokedynamic-call-return-value = invokedynamic-of-nativeCallee
         * }
         * Asserts.assertTrue(invokedynamic-call-return-value, error-message);
         * return;
         */
        if (name.equals(CALLER_METHOD_NAME)) {
            MethodVisitor mv = cv.visitMethod(access, name, desc,
                    signature, exceptions);
            Label nonNativeLabel = new Label();
            Label checkLabel = new Label();
            MethodType mtype = MethodType.methodType(CallSite.class,
                    MethodHandles.Lookup.class, String.class, MethodType.class);
            Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, CLASS,
                    BOOTSTRAP_METHOD_NAME, mtype.toMethodDescriptorString());
            mv.visitCode();
            // push callee parameters onto stack
            mv.visitVarInsn(Opcodes.ALOAD, 0);//push "this"
            mv.visitLdcInsn(1);
            mv.visitLdcInsn(2L);
            mv.visitLdcInsn(3.0f);
            mv.visitLdcInsn(4.0d);
            mv.visitLdcInsn("5");
            // params loaded. let's decide what method to call
            mv.visitVarInsn(Opcodes.ALOAD, 0); // push "this"
            // get nativeCallee field
            mv.visitFieldInsn(Opcodes.GETFIELD, CLASS, CALL_NATIVE_FIELD,
                    CALL_NATIVE_FIELD_DESC);
            // if nativeCallee == false goto nonNativeLabel
            mv.visitJumpInsn(Opcodes.IFEQ, nonNativeLabel);
            // invokedynamic nativeCalleeMethod using bootstrap method
            mv.visitInvokeDynamicInsn(NATIVE_CALLEE_METHOD_NAME,
                    CALLEE_METHOD_DESC, bootstrap);
            // goto checkLabel
            mv.visitJumpInsn(Opcodes.GOTO, checkLabel);
            // label: nonNativeLabel
            mv.visitLabel(nonNativeLabel);
            // invokedynamic calleeMethod using bootstrap method
            mv.visitInvokeDynamicInsn(CALLEE_METHOD_NAME, CALLEE_METHOD_DESC,
                    bootstrap);
            mv.visitLabel(checkLabel);
            mv.visitLdcInsn(CallsBase.CALL_ERR_MSG);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, ASSERTS_CLASS,
                    ASSERTTRUE_METHOD_NAME, ASSERTTRUE_METHOD_DESC, false);
            // label: return
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return null;
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}
