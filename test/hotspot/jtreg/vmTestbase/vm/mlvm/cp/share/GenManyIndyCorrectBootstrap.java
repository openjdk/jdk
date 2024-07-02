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
import java.lang.constant.*;

import vm.mlvm.share.ClassfileGenerator;
import vm.mlvm.share.Env;

public class GenManyIndyCorrectBootstrap extends GenFullCP {

        /**
         * Generates a class file and writes it to a file
         * @see vm.mlvm.share.ClassfileGenerator
         * @param args Parameters for ClassfileGenerator.main() method
         */
        public static void main(String[] args) {
            ClassfileGenerator.main(args);
        }

        /**
         * Creates static init method, which constructs a call site object, which refers to the target method
         * and invokes Dummy.setMH() on this call site
         * @param bytes Class file bytes
         */
        @Override
        protected byte[] createClassInitMethod(byte[] bytes) {
                ClassModel cm = ClassFile.of().parse(bytes);

                bytes = ClassFile.of().transform(cm,
                                ClassTransform.endHandler(cb -> cb.withMethod(STATIC_INIT_METHOD_NAME,
                                                MethodTypeDesc.ofDescriptor(INIT_METHOD_SIGNATURE),
                                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                                mb -> mb.withCode(
                                                                cob -> {
                                                                        cob.invokestatic(ClassDesc.ofInternalName(
                                                                                        JLI_METHODHANDLES), "lookup",
                                                                                        MethodTypeDesc.ofDescriptor("()"
                                                                                                        + fd(JLI_METHODHANDLES_LOOKUP)));
                                                                        cob.ldc(ClassDesc.ofDescriptor(fullClassName));
                                                                        cob.ldc(TARGET_METHOD_NAME);
                                                                        cob.ldc(TARGET_METHOD_SIGNATURE);
                                                                        cob.invokevirtual(
                                                                                        ClassDesc.ofInternalName(
                                                                                                        JL_CLASS),
                                                                                        "getClassLoader",
                                                                                        MethodTypeDesc.ofDescriptor("()"
                                                                                                        + fd(JL_CLASSLOADER)));
                                                                        cob.invokestatic(
                                                                                        ClassDesc.ofInternalName(
                                                                                                        JLI_METHODTYPE),
                                                                                        "fromMethodDescriptorString",
                                                                                        MethodTypeDesc.ofDescriptor("("
                                                                                                        + fd(JL_STRING)
                                                                                                        + fd(JL_CLASSLOADER)
                                                                                                        + ")"
                                                                                                        + fd(JLI_METHODTYPE)));
                                                                        cob.invokevirtual(ClassDesc.ofInternalName(
                                                                                        JLI_METHODHANDLES_LOOKUP),
                                                                                        "findStatic",
                                                                                        MethodTypeDesc.ofDescriptor("("
                                                                                                        + fd(JL_CLASS)
                                                                                                        + fd(JL_STRING)
                                                                                                        + fd(JLI_METHODTYPE)
                                                                                                        + ")"
                                                                                                        + fd(JLI_METHODHANDLE)));
                                                                        cob.invokestatic(ClassDesc.ofInternalName(
                                                                                        NEW_INVOKE_SPECIAL_CLASS_NAME),
                                                                                        "setMH",
                                                                                        MethodTypeDesc.ofDescriptor("("
                                                                                                        + fd(JLI_METHODHANDLE)
                                                                                                        + ")V"));
                                                                        cob.return_();
                                                                }))));

                return bytes;
        }

        /**
         * Disables invoke dynamic CP entry caching and generate default common data
         * @param bytes Class file bytes
         */
        @Override
        protected byte[] generateCommonData(byte[] bytes) {
                return super.generateCommonData(bytes);
        }

        /**
         * Generates an invokedynamic instruction (plus CP entry)
         * which has a valid reference kind in the CP method handle entry for the
         * bootstrap method
         * @param bytes Class file bytes
         */
        @Override
        protected byte[] generateCPEntryData(byte[] bytes, String methodName, String methodSignature, int accessFlags) {
                ClassModel cm = ClassFile.of().parse(bytes);

                bytes = ClassFile.of().transform(cm,
                                ClassTransform.endHandler(cb -> cb.withMethod(methodName,
                                                MethodTypeDesc.ofDescriptor(methodSignature), accessFlags,
                                                mb -> mb.withCode(
                                                                cob -> {
                                                                        DirectMethodHandleDesc bsm;
                                                                        if (Env.getRNG().nextBoolean()) {
                                                                                 bsm = MethodHandleDesc.ofMethod(
                                                                                        DirectMethodHandleDesc.Kind.SPECIAL,
                                                                                        ClassDesc.of(this.fullClassName),
                                                                                        INIT_METHOD_NAME,
                                                                                        MethodTypeDesc.ofDescriptor(NEW_INVOKE_SPECIAL_BOOTSTRAP_METHOD_SIGNATURE)
                                                                                );
                                                                        } else {
                                                                                 bsm = MethodHandleDesc.ofMethod(
                                                                                        DirectMethodHandleDesc.Kind.STATIC,
                                                                                        ClassDesc.of(this.fullClassName),
                                                                                        BOOTSTRAP_METHOD_NAME,
                                                                                        MethodTypeDesc.ofDescriptor(BOOTSTRAP_METHOD_SIGNATURE)
                                                                                );
                                                                        }
                                                                        cob.invokedynamic(DynamicCallSiteDesc.of(bsm, TARGET_METHOD_NAME, MethodTypeDesc.ofDescriptor(TARGET_METHOD_SIGNATURE)));

                                                                }))));
                return bytes;
        }
}
