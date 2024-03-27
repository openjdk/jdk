/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Random;
import java.lang.classfile.TypeKind;

import vm.mlvm.share.ClassfileGenerator;
import vm.mlvm.share.Env;

public class GenManyIndyIncorrectBootstrap extends GenFullCP {

    /**
     * Generates a class file and writes it to a file
     * @see vm.mlvm.share.ClassfileGenerator
     * @param args Parameters for ClassfileGenerator.main() method
     */
    public static void main(String[] args) {
        ClassfileGenerator.main(args);
    }

    /**
     * Create class constructor, which
     * create a call site for target method
     * and puts it into static and instance fields
     * @param bytes Class file bytes
     */
    @Override
    protected byte[] createInitMethod(byte[] bytes) {
        ClassModel cm = ClassFile.of().parse(bytes);

        bytes = ClassFile.of().transform(cm, ClassTransform.endHandler(cb -> cb.withMethod(INIT_METHOD_NAME, MethodTypeDesc.ofDescriptor(INIT_METHOD_SIGNATURE), ClassFile.ACC_PUBLIC,
                mb -> mb.withCode(cob -> {
                    cob.aload(0)
                            .invokespecial(ClassDesc.ofInternalName(PARENT_CLASS_NAME), INIT_METHOD_NAME, MethodTypeDesc.ofDescriptor(INIT_METHOD_SIGNATURE))
                            .aload(0)
                            .new_(ClassDesc.ofInternalName(JLI_CONSTANTCALLSITE))
                            .dup()
                            .invokestatic(ClassDesc.ofInternalName(JLI_METHODHANDLES), "lookup", MethodTypeDesc.ofDescriptor("()" + fd(JLI_METHODHANDLES_LOOKUP)))
                            .ldc(ClassDesc.ofDescriptor(fullClassName))
                            .ldc(TARGET_METHOD_NAME)
                            .ldc(TARGET_METHOD_SIGNATURE)
                            .ldc(ClassDesc.ofDescriptor(fullClassName))
                            .invokevirtual(ClassDesc.ofInternalName(JL_CLASS), "getClassLoader", MethodTypeDesc.ofDescriptor("()" + fd(JL_CLASSLOADER)))
                            .invokestatic(ClassDesc.ofInternalName(JLI_METHODTYPE), "fromMethodDescriptorString", MethodTypeDesc.ofDescriptor("(" + fd(JL_STRING) + fd(JL_CLASSLOADER) + ")" + fd(JLI_METHODTYPE)))
                            .invokevirtual(ClassDesc.ofInternalName(JLI_METHODHANDLES_LOOKUP), "findStatic", MethodTypeDesc.ofDescriptor("(" + fd(JL_CLASS) + fd(JL_STRING) + fd(JLI_METHODTYPE) + ")" + fd(JLI_METHODHANDLE)))
                            .invokespecial(ClassDesc.ofInternalName(JLI_CONSTANTCALLSITE), INIT_METHOD_NAME, MethodTypeDesc.ofDescriptor("(" + fd(JLI_METHODHANDLE) + ")V"))
                            .dup()
                            .putstatic(ClassDesc.ofInternalName(fullClassName), STATIC_BOOTSTRAP_FIELD_NAME, ClassDesc.ofDescriptor(STATIC_BOOTSTRAP_FIELD_SIGNATURE))
                            .putfield(ClassDesc.ofInternalName(fullClassName), INSTANCE_BOOTSTRAP_FIELD_NAME, ClassDesc.ofDescriptor(INSTANCE_BOOTSTRAP_FIELD_SIGNATURE))
                            .return_();
                }))));

        return bytes;
    }

    /**
     * Creates a target method which always throw. It should not be called,
     * since all invokedynamic instructions have invalid bootstrap method types
     * @param bytes Class file bytes
     */
    @Override
    protected byte[] createTargetMethod(byte[] bytes) {
        return createThrowRuntimeExceptionMethod(bytes, true, TARGET_METHOD_NAME, TARGET_METHOD_SIGNATURE);
    }

    /**
     * Creates a bootstrap method which always throw. It should not be called,
     * since all invokedynamic instructions have invalid bootstrap method types
     * @param bytes Class file bytes
     */
    @Override
    protected byte[] createBootstrapMethod(byte[] bytes) {
        return createThrowRuntimeExceptionMethod(bytes, true, BOOTSTRAP_METHOD_NAME, BOOTSTRAP_METHOD_SIGNATURE);
    }

    /**
     * Generates common data for class plus two fields that hold CallSite
     * and used as bootstrap targets
     * @param bytes Class file bytes
     */
    @Override
    protected byte[] generateCommonData(byte[] bytes) {

            ClassModel cm = ClassFile.of().parse(bytes);

            bytes = ClassFile.of().transform(cm, ClassTransform.endHandler(cb -> cb
                            .withField(STATIC_BOOTSTRAP_FIELD_NAME,
                                            ClassDesc.ofDescriptor(STATIC_BOOTSTRAP_FIELD_SIGNATURE),
                                            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC)
                            .withField(INSTANCE_BOOTSTRAP_FIELD_NAME,
                                            ClassDesc.ofDescriptor(INSTANCE_BOOTSTRAP_FIELD_SIGNATURE),
                                            ClassFile.ACC_PUBLIC)));

            bytes = super.generateCommonData(bytes);

            return createThrowRuntimeExceptionMethod(bytes, false, INSTANCE_BOOTSTRAP_METHOD_NAME, INSTANCE_BOOTSTRAP_METHOD_SIGNATURE);
    }

    Label throwMethodLabel;

    // The exception to expect that is wrapped in a BootstrapMethodError
    static final String WRAPPED_EXCEPTION = "java/lang/invoke/WrongMethodTypeException";

    // The error to expect that is not wrapped in a BootstrapMethodError and
    // is thrown directly
    static final String DIRECT_ERROR = "java/lang/IncompatibleClassChangeError";

    /**
     * Generates an invokedynamic instruction (plus CP entry)
     * which has invalid reference kind in the CP method handle entry for the bootstrap method
     * @param bytes Class file bytes
     */
    @Override
    protected byte[] generateCPEntryData(byte[] bytes, String methodName, String methodSignature, int accessFlags) {
        ClassModel cm = ClassFile.of().parse(bytes);

        bytes = ClassFile.of().transform(cm, ClassTransform.endHandler(cb -> cb.withMethod(methodName, MethodTypeDesc.ofDescriptor(methodSignature), accessFlags,
                mb -> mb.withCode(cob -> {
                    DirectMethodHandleDesc.Kind[] kinds = DirectMethodHandleDesc.Kind.values();
                    DirectMethodHandleDesc.Kind kind = kinds[Env.getRNG().nextInt(kinds.length)];

                    switch (kind) {
                        case GETTER:
                        case SETTER:
                        case STATIC_GETTER:
                        case STATIC_SETTER:
                        case SPECIAL:
                        case VIRTUAL:
                        case INTERFACE_VIRTUAL:
                            break;
                        default:
                            return ;
                    }
                    Label indyThrowableBegin = cob.newLabel();
                    Label indyThrowableEnd = cob.newLabel();
                    Label catchThrowableLabel = cob.newLabel();

                    Label indyBootstrapBegin = cob.newLabel();
                    Label indyBootstrapEnd = cob.newLabel();
                    Label catchBootstrapLabel = cob.newLabel();

                    cob.trying(
                            tryBlock -> {
                                tryBlock.labelBinding(indyBootstrapBegin);
                                tryBlock.labelBinding(indyBootstrapEnd);
                            },
                            catchBuilder -> {
                                catchBuilder.catching(ClassDesc.of(JL_BOOTSTRAPMETHODERROR), catchBlock -> {e:
                                    catchBlock.labelBinding(catchBootstrapLabel);
                                    catchBlock.returnInstruction(TypeKind.VoidType);
                                });
                            }
                    );
                    cob.labelBinding(indyThrowableBegin);

                    cob.trying(
                            tryBlock -> {
                                tryBlock.labelBinding(indyThrowableBegin);
                                tryBlock.labelBinding(indyThrowableEnd);
                            },
                            catchBuilder -> {
                                catchBuilder.catching(ClassDesc.of(JL_THROWABLE), catchBlock -> {
                                    catchBlock.labelBinding(catchThrowableLabel);
                                    catchBlock.returnInstruction(TypeKind.VoidType);
                                });
                            }
                    );
                    cob.labelBinding(indyBootstrapBegin);

                    DirectMethodHandleDesc bsm;
                    switch (kind) {
                        case GETTER:
                        case SETTER:
                            bsm = MethodHandleDesc.ofField(kind, ClassDesc.of(fullClassName), INSTANCE_BOOTSTRAP_FIELD_NAME, ClassDesc.ofDescriptor(INSTANCE_BOOTSTRAP_FIELD_SIGNATURE));
                            break;
                        case STATIC_GETTER:
                        case STATIC_SETTER:
                            bsm = MethodHandleDesc.ofField(kind, ClassDesc.of(fullClassName), STATIC_BOOTSTRAP_FIELD_NAME, ClassDesc.ofDescriptor(STATIC_BOOTSTRAP_FIELD_SIGNATURE));
                            break;
                        case SPECIAL:
                        case VIRTUAL:
                        case INTERFACE_VIRTUAL:
                            bsm = MethodHandleDesc.ofMethod(kind, ClassDesc.of(fullClassName), INSTANCE_BOOTSTRAP_METHOD_NAME, MethodTypeDesc.ofDescriptor(INSTANCE_BOOTSTRAP_METHOD_SIGNATURE));
                            break;
                        default:
                            throw new Error("Unexpected handle type " + kind);
                    }

                    cob.invokedynamic(DynamicCallSiteDesc.of(bsm, TARGET_METHOD_NAME, MethodTypeDesc.ofDescriptor(TARGET_METHOD_SIGNATURE)));
                    cob.labelBinding(indyBootstrapEnd);
                    cob.labelBinding(indyThrowableEnd);

                    // No exception at all, throw error
                    Label throwLabel = cob.newLabel();
                    cob.goto_(throwLabel);

                    // Got a bootstrapmethoderror as expected, check that it is wrapping what we expect
                    cob.labelBinding(catchBootstrapLabel);

                    // Save error in case we need to rethrow it
                    cob.dup();
                    cob.astore(1);
                    cob.invokevirtual(ClassDesc.of(JL_THROWABLE), "getCause", MethodTypeDesc.ofDescriptor("()" + fd(JL_THROWABLE)));


                    // If it is the expected exception, goto next block
                    cob.instanceof_(ClassDesc.of(WRAPPED_EXCEPTION)); // Check if the object on top of the stack is of the specified type
                    Label nextBlockLabel = cob.newLabel();
                    cob.ifne(nextBlockLabel);

                    // Not the exception we were expectiong, throw error
                    cob.aload(1);
                    createThrowRuntimeExceptionCodeHelper(cob, "invokedynamic got an unexpected wrapped exception (expected " + WRAPPED_EXCEPTION + ", bootstrap type=" + kind + ", opcode=" + kind.refKind + ")!", true);


                    // JDK-8294976 workaround: we have to generate stackmaps manually and since ClassFile API automatically generates stack map frames, so there is no need to manually generate them.
                    cob.labelBinding(catchThrowableLabel);

                    // Save error in case we need to rethrow it
                    cob.dup();
                    cob.astore(1);

                    // If it is the expected exception, goto next block
                    cob.instanceof_(ClassDesc.of(DIRECT_ERROR)); // Check if the object on top of the stack is of the specified type
                    cob.ifne(nextBlockLabel);

                    // Not the exception we were expecting, throw error
                    cob.aload(1);
                    createThrowRuntimeExceptionCodeHelper(cob,
                            "invokedynamic got an unexpected exception (expected " + DIRECT_ERROR
                                    + ", bootstrap type" + kind
                                    + ", opcode=" + kind.refKind + ")!", true);

                    cob.labelBinding(throwLabel);
                    createThrowRuntimeExceptionCodeHelper(cob,
                            "invokedynamic should always throw (bootstrap type"
                                    + kind +", "
                                    + "opcode=" + kind.refKind + ")!", false);

                    cob.labelBinding(nextBlockLabel);
                    cob.return_();
                }))));

        return bytes;
    }
}
