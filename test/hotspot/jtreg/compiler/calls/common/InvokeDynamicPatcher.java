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

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.constant.ConstantDescs.*;

/**
 * A class which patch InvokeDynamic class bytecode with invokydynamic
 instruction, rewriting "caller" method to call "callee" method using
 invokedynamic
 */
public final class InvokeDynamicPatcher {

    private static final ClassDesc CLASS = InvokeDynamic.class.describeConstable().orElseThrow();
    private static final String CALLER_METHOD_NAME = "caller";
    private static final String CALLEE_METHOD_NAME = "callee";
    private static final String NATIVE_CALLEE_METHOD_NAME = "calleeNative";
    private static final String BOOTSTRAP_METHOD_NAME = "bootstrapMethod";
    private static final String CALL_NATIVE_FIELD = "nativeCallee";
    private static final ClassDesc CALL_NATIVE_FIELD_DESC = CD_boolean;
    private static final MethodTypeDesc CALLEE_METHOD_DESC = MethodTypeDesc.of(
            CD_boolean, CLASS, CD_int, CD_long, CD_float, CD_double, CD_String);
    private static final MethodTypeDesc ASSERTTRUE_METHOD_DESC = MethodTypeDesc.of(
            CD_void, CD_boolean, CD_String);
    private static final ClassDesc ASSERTS_CLASS = ClassDesc.ofInternalName("jdk/test/lib/Asserts");
    private static final String ASSERTTRUE_METHOD_NAME = "assertTrue";

    public static void main(String args[]) throws IOException, URISyntaxException {
        Path filePath = Path.of(InvokeDynamic.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).resolve(InvokeDynamic.class.getName().replace('.', '/') +".class");
        var bytes = ClassFile.of().transformClass(ClassFile.of().parse(filePath),
                ClassTransform.transformingMethodBodies(m -> m.methodName().equalsString(CALLER_METHOD_NAME), new CodeTransform() {
                    @Override
                    public void accept(CodeBuilder builder, CodeElement element) {
                        // discard
                    }

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
                    @Override
                    public void atEnd(CodeBuilder builder) {
                        Label nonNativeLabel = builder.newLabel();
                        Label checkLabel = builder.newLabel();
                        MethodType mtype = MethodType.methodType(CallSite.class,
                                MethodHandles.Lookup.class, String.class, MethodType.class);
                        DirectMethodHandleDesc dmh = MethodHandleDesc.of(DirectMethodHandleDesc.Kind.STATIC,
                                CLASS, BOOTSTRAP_METHOD_NAME, mtype.descriptorString());
                        // push callee parameters onto stack
                        builder.aload(builder.receiverSlot())
                               .ldc(1)
                               .ldc(2L)
                               .ldc(3.0f)
                               .ldc(4.0d)
                               .ldc("5")
                               // params loaded. let's decide what method to call
                               .aload(builder.receiverSlot())
                               // get nativeCallee field
                               .getfield(CLASS, CALL_NATIVE_FIELD, CALL_NATIVE_FIELD_DESC)
                               // if nativeCallee == false goto nonNativeLabel
                               .ifeq(nonNativeLabel)
                               // invokedynamic nativeCalleeMethod using bootstrap method
                               .invokedynamic(DynamicCallSiteDesc.of(dmh, NATIVE_CALLEE_METHOD_NAME, CALLEE_METHOD_DESC))
                               // goto checkLabel
                               .goto_(checkLabel)
                               // label: nonNativeLabel
                               .labelBinding(nonNativeLabel)
                               // invokedynamic calleeMethod using bootstrap method
                               .invokedynamic(DynamicCallSiteDesc.of(dmh, CALLEE_METHOD_NAME, CALLEE_METHOD_DESC))
                               .labelBinding(checkLabel)
                               .ldc(CallsBase.CALL_ERR_MSG)
                               .invokestatic(ASSERTS_CLASS, ASSERTTRUE_METHOD_NAME, ASSERTTRUE_METHOD_DESC)
                               // label: return
                               .return_();
                    }
                }));
        Files.write(filePath, bytes, StandardOpenOption.WRITE);
    }
}
