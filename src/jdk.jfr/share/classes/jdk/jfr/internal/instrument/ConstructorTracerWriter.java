/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Predicate;

import jdk.internal.classfile.*;
import jdk.internal.classfile.instruction.ReturnInstruction;

final class ConstructorTracerWriter {

    private static Predicate<MethodModel> adaptTest = new Predicate<MethodModel>() {
        @Override
        public boolean test(MethodModel methodModel) {
            return methodModel.methodName().stringValue().equals("<init>");
        }
    };
    static byte[] generateBytes(Class<?> clz, byte[] oldBytes) {
        String shortClassName = clz.getSimpleName();
        String fullClassName = clz.getName().replace('.', '/');
        return Classfile.parse(oldBytes).transform(
                ClassTransform.transformingMethods(adaptTest, (mb, me) -> {
                    MethodModel mm = mb.original().orElseThrow();
                    boolean useInputParameter = mm.methodType().stringValue().startsWith("(Ljava/lang/String;");
                    if (me instanceof CodeModel cm) {
                        mb.transformCode(cm, (cob, ins) -> {
                            if (ins instanceof ReturnInstruction ret) {
                                //Load 'this' from local variable 0
                                cob.loadInstruction(TypeKind.ReferenceType, 0);
                                if (useInputParameter) {
                                    //Load first input parameter
                                    cob.loadInstruction(TypeKind.ReferenceType, 1);
                                } else {
                                    //Load ""
                                    cob.constantInstruction(Opcode.ACONST_NULL, null);
                                }
                                //Invoke ThrowableTracer.traceCLASS(this, parameter) for current class
                                cob.invokeInstruction(Opcode.INVOKESTATIC, ClassDesc.ofInternalName("jdk/jfr/internal/instrument/ThrowableTracer"),
                                        "trace" + shortClassName, MethodTypeDesc.of(ConstantDescs.CD_void, ClassDesc.ofInternalName(fullClassName), ConstantDescs.CD_String), false);
                            }
                            cob.accept(ins);
                        });
                    } else {
                        mb.accept(me);
                    }
                }));
    }
}