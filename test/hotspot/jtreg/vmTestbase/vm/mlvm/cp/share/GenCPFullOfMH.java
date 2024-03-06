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
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Random;

import vm.mlvm.share.ClassfileGenerator;
import vm.mlvm.share.Env;

public class GenCPFullOfMH extends GenFullCP {

    public static void main(String[] args) {
        ClassfileGenerator.main(args);
    }

    @Override
    protected byte[] generateCommonData(byte[] bytes) {
        ClassModel cm = ClassFile.of().parse(bytes);
        bytes = ClassFile.of().transform(cm, ClassTransform.endHandler(cb -> cb
                .withField(STATIC_FIELD_NAME, ClassDesc.ofDescriptor(STATIC_FIELD_SIGNATURE),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC)
                .withField(INSTANCE_FIELD_NAME, ClassDesc.ofDescriptor(INSTANCE_FIELD_SIGNATURE), ClassFile.ACC_PUBLIC)
                .withMethod(INSTANCE_TARGET_METHOD_NAME, MethodTypeDesc.ofDescriptor(INSTANCE_TARGET_METHOD_SIGNATURE),
                        ClassFile.ACC_PUBLIC,
                        mb -> mb.withCode(
                                CodeBuilder::return_))));

        bytes = createInitMethod(bytes);
        bytes = createTargetMethod(bytes);

        return bytes;
    }

    @Override
    protected byte[] generateCPEntryData(byte[] bytes) {
        ClassModel cm = ClassFile.of().parse(bytes);
        bytes = ClassFile.of().transform(cm,
                ClassTransform.endHandler(cb -> cb.withMethod("generateCPEntryData",
                        MethodTypeDesc.ofDescriptor("()[B"), ClassFile.ACC_PUBLIC,
                        mb -> mb.withCode(
                                cob -> {
                                    DirectMethodHandleDesc.Kind[] kinds = DirectMethodHandleDesc.Kind.values();
                                    DirectMethodHandleDesc.Kind kind = kinds[new Random().nextInt(kinds.length)];

                                    switch (kind) {
                                        case SETTER:
                                        case STATIC_SETTER:
                                            cob.iconst_0();
                                            break;
                                        case SPECIAL:
                                        case VIRTUAL:
                                        case INTERFACE_VIRTUAL:
                                            cob.aconst_null();
                                            break;
                                    }

                                    MethodHandleDesc handle;
                                    switch (kind) {
                                        case GETTER:
                                        case SETTER:
                                            handle = MethodHandleDesc.ofField(kind, ClassDesc.of(fullClassName),
                                                    INSTANCE_FIELD_NAME,
                                                    ClassDesc.ofDescriptor(INSTANCE_FIELD_SIGNATURE));
                                            break;
                                        case CONSTRUCTOR:
                                            handle = MethodHandleDesc.ofConstructor(ClassDesc.of(fullClassName),
                                                    ClassDesc.ofDescriptor(INIT_METHOD_SIGNATURE));
                                            break;
                                        case STATIC:
                                            handle = MethodHandleDesc.ofMethod(kind, ClassDesc.of(fullClassName),
                                                    TARGET_METHOD_NAME,
                                                    MethodTypeDesc.ofDescriptor(TARGET_METHOD_SIGNATURE));
                                            break;
                                        case INTERFACE_VIRTUAL:
                                            handle = MethodHandleDesc.ofMethod(kind,
                                                    ClassDesc.of(getDummyInterfaceClassName()),
                                                    INSTANCE_TARGET_METHOD_NAME,
                                                    MethodTypeDesc.ofDescriptor(INSTANCE_TARGET_METHOD_SIGNATURE));
                                            break;
                                        case SPECIAL:
                                        case VIRTUAL:
                                            handle = MethodHandleDesc.ofMethod(kind, ClassDesc.of(fullClassName),
                                                    INSTANCE_TARGET_METHOD_NAME,
                                                    MethodTypeDesc.ofDescriptor(INSTANCE_TARGET_METHOD_SIGNATURE));
                                            break;
                                        default:
                                            throw new Error("Unexpected handle type " + kind);
                                    }
                                    cob.ldc(handle);

                                    switch (kind) {
                                        case GETTER:
                                        case STATIC_GETTER:
                                            cob.pop();
                                            break;
                                    }
                                }))));

        return bytes;
    }
}
