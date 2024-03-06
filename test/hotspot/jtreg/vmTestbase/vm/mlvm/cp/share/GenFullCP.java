/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.mlvm.cp.share;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;


import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ClassWriterExt;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

import vm.mlvm.share.ClassfileGenerator;
import vm.mlvm.share.Env;

public abstract class GenFullCP extends ClassfileGenerator {

    /**
     * Generate field description for object type from class name:
     * return "L" + className + ";";
     * @param className Class name
     * @return field descriptor representing the class type
     */
    protected static String fd(String className) {
        return "L" + className + ";";
    }

    // Universal constants
    protected static final String JL_OBJECT = "java/lang/Object";
    protected static final String JL_CLASS = "java/lang/Class";
    protected static final String JL_CLASSLOADER = "java/lang/ClassLoader";
    protected static final String JL_STRING = "java/lang/String";
    protected static final String JL_RUNTIMEEXCEPTION = "java/lang/RuntimeException";
    protected static final String JL_BOOTSTRAPMETHODERROR = "java/lang/BootstrapMethodError";
    protected static final String JL_THROWABLE = "java/lang/Throwable";
    protected static final String JLI_METHODTYPE = "java/lang/invoke/MethodType";
    protected static final String JLI_METHODHANDLE = "java/lang/invoke/MethodHandle";
    protected static final String JLI_METHODHANDLES = "java/lang/invoke/MethodHandles";
    protected static final String JLI_METHODHANDLES_LOOKUP = "java/lang/invoke/MethodHandles$Lookup";
    protected static final String JLI_CALLSITE = "java/lang/invoke/CallSite";
    protected static final String JLI_CONSTANTCALLSITE = "java/lang/invoke/ConstantCallSite";

    protected static final String VOID_NO_ARG_METHOD_SIGNATURE = "()V";

    protected static final String NEW_INVOKE_SPECIAL_CLASS_NAME = "java/lang/invoke/NewInvokeSpecialCallSite";
    protected static final String NEW_INVOKE_SPECIAL_BOOTSTRAP_METHOD_SIGNATURE = "(" + fd(JLI_METHODHANDLES_LOOKUP)
            + fd(JL_STRING) + fd(JLI_METHODTYPE) + ")V";

    protected static final String INIT_METHOD_NAME = "<init>";
    protected static final String STATIC_INIT_METHOD_NAME = "<clinit>";

    // Generated class constants
    protected static final int CLASSFILE_VERSION = 51;

    protected static final int CP_CONST_COUNT = 65400;
    protected static final int MAX_METHOD_SIZE = 65400;
    protected static final int BYTES_PER_LDC = 5;
    protected static final int LDC_PER_METHOD = MAX_METHOD_SIZE / BYTES_PER_LDC;
    protected static final int METHOD_COUNT = CP_CONST_COUNT / LDC_PER_METHOD;

    protected static final String PARENT_CLASS_NAME = JL_OBJECT;

    protected static final String INIT_METHOD_SIGNATURE = VOID_NO_ARG_METHOD_SIGNATURE;

    protected static final String MAIN_METHOD_NAME = "main";
    protected static final String MAIN_METHOD_SIGNATURE = "(" + "[" + fd(JL_STRING) + ")V";

    protected static final String TEST_METHOD_NAME = "test";
    protected static final String TEST_METHOD_SIGNATURE = VOID_NO_ARG_METHOD_SIGNATURE;

    protected static final MethodTypeDesc TEST_METHOD_TYPE_DESC = MethodTypeDesc.of(ClassDesc.ofDescriptor("V"));

    protected static final String STATIC_FIELD_NAME = "testStatic";
    protected static final String STATIC_FIELD_SIGNATURE = "Z";

    protected static final String INSTANCE_FIELD_NAME = "testInstance";
    protected static final String INSTANCE_FIELD_SIGNATURE = "Z";

    protected static final String STATIC_BOOTSTRAP_FIELD_NAME = "testCSStatic";
    protected static final String STATIC_BOOTSTRAP_FIELD_SIGNATURE = fd(JLI_CALLSITE);

    protected static final String INSTANCE_BOOTSTRAP_FIELD_NAME = "testCSInstance";
    protected static final String INSTANCE_BOOTSTRAP_FIELD_SIGNATURE = fd(JLI_CALLSITE);

    protected static final String BOOTSTRAP_METHOD_NAME = "bootstrap";
    protected static final String BOOTSTRAP_METHOD_SIGNATURE = "(" + fd(JLI_METHODHANDLES_LOOKUP) + fd(JL_STRING)
            + fd(JLI_METHODTYPE) + ")" + fd(JLI_CALLSITE);

    protected static final String INSTANCE_BOOTSTRAP_METHOD_NAME = "bootstrapInstance";
    protected static final String INSTANCE_BOOTSTRAP_METHOD_SIGNATURE = BOOTSTRAP_METHOD_SIGNATURE;

    protected static final String TARGET_METHOD_NAME = "target";
    protected static final String TARGET_METHOD_SIGNATURE = VOID_NO_ARG_METHOD_SIGNATURE;

    protected static final String INSTANCE_TARGET_METHOD_NAME = "targetInstance";
    protected static final String INSTANCE_TARGET_METHOD_SIGNATURE = VOID_NO_ARG_METHOD_SIGNATURE;

    protected interface DummyInterface {
        public void targetInstance();
    }

    // Helper methods

    protected static String getDummyInterfaceClassName() {
        return DummyInterface.class.getName().replace('.', '/');
    }


    // If set_cause is true it expects a Throwable (the cause) to be on top of the stack when called.
    protected static void createThrowRuntimeExceptionCodeHelper(CodeBuilder cob, String msg, boolean set_cause) {

        cob.new_(ClassDesc.ofInternalName(JL_RUNTIMEEXCEPTION))
                .dup()
                .ldc(msg)
                .invokespecial(ClassDesc.ofInternalName(JL_RUNTIMEEXCEPTION),
                        INIT_METHOD_NAME,
                        MethodTypeDesc.ofDescriptor("(" + fd(JL_STRING) + ")V"));
        if (set_cause) {
            cob.dup_x1()
                    .aload(0)
                    .invokevirtual(ClassDesc.ofInternalName(JL_THROWABLE), "initCause",
                            MethodTypeDesc.ofDescriptor(
                                    "(" + fd(JL_THROWABLE) + ")"
                                            + fd(JL_THROWABLE)));
        }
        cob.athrow();
    }

    protected static byte[] createThrowRuntimeExceptionMethod(byte[] bytes, boolean isStatic, String methodName,
            String methodSignature) {
        bytes = ClassFile.of().transform(ClassFile.of().parse(bytes), ClassTransform.endHandler(clb -> clb.withMethod(methodName,
                MethodTypeDesc.ofDescriptor(methodSignature),
                ClassFile.ACC_PUBLIC | (isStatic ? ClassFile.ACC_STATIC : 0),
                mb -> mb.withCode(cob -> {
                        cob.aload(cob.receiverSlot());
                        cob.ldc("Method " + methodName + methodSignature + " should not be called!");
                        cob.invokestatic(ClassDesc.of("vm/mlvm/share/GenFullCP"), "createThrowRuntimeExceptionCode",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                        createThrowRuntimeExceptionCodeHelper(cob, "Method " + methodName + methodSignature + " should not be called!", false);
                        cob.return_();
                }))));

        return bytes;
    }

    protected byte[] createClassInitMethod(byte[] bytes) {
        return bytes;
    }

    protected byte[] createInitMethod(byte[] bytes) {
        return ClassFile.of().transform(ClassFile.of().parse(bytes),
                ClassTransform.endHandler(clb -> clb.withMethod(INIT_METHOD_NAME,
                        MethodTypeDesc.ofDescriptor(VOID_NO_ARG_METHOD_SIGNATURE),
                        ClassFile.ACC_PUBLIC,
                        mb -> mb.withCode(cb -> cb
                                .aload(cb.receiverSlot())
                                .aload(cb.parameterSlot(0))
                                .ldc(fullClassName + " constructor called")
                                .invokestatic(ClassDesc.of("vm/mlvm/share/Env"), "traceVerbose",
                                        MethodTypeDesc.ofDescriptor("Ljava/lang/String;)V"))
                                .invokespecial(ClassDesc.ofInternalName(PARENT_CLASS_NAME), INIT_METHOD_NAME,
                                        MethodTypeDesc.ofDescriptor(VOID_NO_ARG_METHOD_SIGNATURE))
                                .return_()))));
    }

    protected byte[] createTargetMethod(byte[] bytes) {
        return ClassFile.of().transform(ClassFile.of().parse(bytes),
                ClassTransform.endHandler(
                        cb -> cb.withMethod(TARGET_METHOD_NAME, MethodTypeDesc.ofDescriptor(TARGET_METHOD_SIGNATURE),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                mb -> mb.withCode(
                                        CodeBuilder -> CodeBuilder
                                                .ldc(fullClassName + " constructor called")
                                                .invokestatic(ClassDesc.of("vm/mlvm/share/Env"), "traceVerbose",
                                                        MethodTypeDesc.ofDescriptor("Ljava/lang/String;)V"))
                                                .return_()))));
    }

    protected byte[] createBootstrapMethod(byte[] bytes) {
        return createBootstrapMethod(bytes, true, BOOTSTRAP_METHOD_NAME, BOOTSTRAP_METHOD_SIGNATURE);
    }

    protected byte[] createBootstrapMethod(byte[] bytes, boolean isStatic, String methodName, String methodSignature) {
        int argShift = isStatic ? 0 : 1;

        return ClassFile.of().transform(ClassFile.of().parse(bytes),
                ClassTransform.endHandler(cb -> cb.withMethod(methodName, MethodTypeDesc.ofDescriptor(methodSignature),
                        ClassFile.ACC_PUBLIC | (isStatic ? ClassFile.ACC_STATIC : 0),
                        mb -> mb.withCode(
                                CodeBuilder -> CodeBuilder
                                        .ldc(fullClassName + "." + BOOTSTRAP_METHOD_NAME + BOOTSTRAP_METHOD_SIGNATURE
                                                + " called")
                                        .invokestatic(ClassDesc.of("vm/mlvm/share/Env"), "traceVerbose",
                                                MethodTypeDesc.ofDescriptor("Ljava/lang/String;)V"))
                                        .new_(ClassDesc.ofInternalName(JLI_CONSTANTCALLSITE))
                                        .dup()
                                        .aload(0 + argShift)
                                        .ldc(ClassDesc.ofDescriptor(fullClassName))
                                        .aload(1 + argShift)
                                        .aload(2 + argShift)
                                        .invokevirtual(ClassDesc.ofInternalName(JLI_METHODHANDLES_LOOKUP), "findStatic",
                                                MethodTypeDesc.ofDescriptor("(" + fd(JL_CLASS) + fd(JL_STRING)
                                                        + fd(JLI_METHODTYPE) + ")" + fd(JLI_METHODHANDLE)))
                                        .invokespecial(ClassDesc.ofInternalName(JLI_CONSTANTCALLSITE),
                                                INIT_METHOD_NAME,
                                                MethodTypeDesc.ofDescriptor("(" + fd(JLI_METHODHANDLE) + ")V"))
                                        .areturn()))));
    }

    protected static void finishMethodCode(MethodVisitor mv) {
        finishMethodCode(mv, Opcodes.RETURN);
    }

    protected static void finishMethodCode(MethodVisitor mv, int returnOpcode) {
        mv.visitInsn(returnOpcode);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    @Override
    public Klass[] generateBytecodes() {

        // COMPUTE_FRAMES were disabled due to JDK-8079697
        ClassWriterExt cw = new ClassWriterExt(/* ClassWriter.COMPUTE_FRAMES | */ ClassWriter.COMPUTE_MAXS);

        String[] interfaces = new String[1];
        interfaces[0] = getDummyInterfaceClassName();

        byte[] bytes = ClassFile.of().build(ClassDesc.ofInternalName(fullClassName), classBuilder -> {
            classBuilder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                    .withInterfaceSymbols(ClassDesc.ofInternalName(interfaces[0]))
                    .withSuperclass(ClassDesc.ofInternalName(PARENT_CLASS_NAME))
                    .withVersion(CLASSFILE_VERSION, 0);
        });

        bytes = generateCommonData(bytes);

        bytes = ClassFile.of().transform(ClassFile.of().parse(bytes),
                ClassTransform.endHandler(cb -> cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                        .withMethod(MAIN_METHOD_NAME, MethodTypeDesc.ofDescriptor(MAIN_METHOD_SIGNATURE),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                mb -> mb.withCode(
                                        codeBuilder -> codeBuilder
                                                .new_(ClassDesc.ofInternalName(fullClassName))
                                                .dup()
                                                .invokespecial(ClassDesc.ofInternalName(fullClassName),
                                                        INIT_METHOD_NAME,
                                                        MethodTypeDesc.of(ConstantDescs.CD_void))
                                                .return_()))));

        int constCount = 0;
        int methodNum = 0;

        // TODO: check real CP size and also limit number of iterations in this cycle
        while (constCount < CP_CONST_COUNT) {
            final String methodName = TEST_METHOD_NAME + String.format("%02d", methodNum);

            bytes = ClassFile.of().transform(ClassFile.of().parse(bytes),
                    ClassTransform.endHandler(cb -> cb.withMethod(methodName, TEST_METHOD_TYPE_DESC,
                            ClassFile.ACC_PUBLIC,
                            mb -> mb.withCode(
                                    CodeBuilder::return_))));

            bytes = generateTestMethodProlog(bytes);

            // TODO: check real CP size and also limit number of iterations in this cycle
            while (constCount < CP_CONST_COUNT && cw.getBytecodeLength(ClassFile.of().parse(bytes)) < MAX_METHOD_SIZE) {
                bytes = generateCPEntryData(bytes, methodName, TEST_METHOD_SIGNATURE, ClassFile.ACC_PUBLIC);
                ++constCount;
            }

            bytes = generateTestMethodEpilog(bytes);
            Env.traceNormal("Method " + fullClassName + "." + methodName + "(): "
                    + constCount + " constants in CP, "
                    + cw.getBytecodeLength(ClassFile.of().parse(bytes)) + " bytes of code");

            mainMV.visitInsn(Opcodes.DUP);
            mainMV.visitMethodInsn(Opcodes.INVOKEVIRTUAL, fullClassName, methodName, TEST_METHOD_SIGNATURE);

            ++methodNum;
        }

        mainMV.visitInsn(Opcodes.POP);
        finishMethodCode(mainMV);

        return new Klass[] { new Klass(this.pkgName, this.shortClassName, MAIN_METHOD_NAME, MAIN_METHOD_SIGNATURE, cw.toByteArray()) };
    }

    protected byte[] generateCommonData(byte[] bytes) {
        bytes = createClassInitMethod(bytes);
        bytes = createInitMethod(bytes);
        bytes = createTargetMethod(bytes);
        bytes = createBootstrapMethod(bytes);
        return bytes;
    }

    protected byte[] generateTestMethodProlog(byte[] bytes) {
        return bytes;
    }

    protected abstract byte[] generateCPEntryData(byte[] bytes, String methodName, String methodSignature, int accessFlags);

    protected byte[] generateTestMethodEpilog(byte[] bytes) {
        return ClassFile.of().transform(ClassFile.of().parse(bytes), ClassTransform.endHandler(
                cb -> cb.withMethod(methodName, MethodTypeDesc.ofDescriptor(TEST_METHOD_SIGNATURE), ClassFile.ACC_PUBLIC,
                        mb -> mb.withCode(CodeBuilder::return_))));
    }

}
