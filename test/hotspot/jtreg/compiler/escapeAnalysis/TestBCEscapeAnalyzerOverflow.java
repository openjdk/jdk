/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8216486
 * @summary Verify BCEscapeAnalyzer handles methods where
 *          (numblocks+1)*(max_stack+max_locals) overflows a 32-bit int.
 *          On a UBSAN build the signed overflow would be caught as UB;
 *          on a normal build the test verifies no crash from the bogus
 *          allocation size that resulted from the overflow.
 *
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      compiler.escapeAnalysis.TestBCEscapeAnalyzerOverflow
 */

package compiler.escapeAnalysis;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestBCEscapeAnalyzerOverflow {

    // Number of goto instructions in the generated method.
    // Creates NUM_GOTOS + 1 basic blocks.  With max_stack = 0xFFFF and
    // max_locals = 0xFFFF the product (numblocks+1)*(max_stack+max_locals)
    // is 16386 * 131070 = 2,147,713,020 which exceeds Integer.MAX_VALUE.
    static final int NUM_GOTOS = 16384;
    static final int TARGET_MAX_STACK = 0xFFFF;
    static final int TARGET_MAX_LOCALS = 0xFFFF;

    static final ClassDesc CD_HELPER =
        ClassDesc.of("compiler.escapeAnalysis.BCEscapeOverflowHelper");

    public static void main(String[] args) throws Throwable {
        byte[] classBytes = buildClass();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> cls = lookup.defineClass(classBytes);

        // caller() allocates an Object and passes it to bigMethod() via
        // invokestatic.  Under -Xcomp -XX:-TieredCompilation, C2 compiles
        // caller() and invokes BCEscapeAnalyzer on bigMethod to determine
        // whether the argument escapes.  Without the fix the 32-bit
        // overflow in iterate_blocks leads to undefined behavior.
        var mh = lookup.findStatic(cls, "caller",
                     MethodType.methodType(void.class));
        mh.invoke();
    }

    /**
     * Builds a minimal class (version 50, no StackMapTable needed) with:
     *   public static void bigMethod(Object o)  -- pathological method
     *   public static void caller()              -- calls bigMethod
     *
     * The ClassFile API generates the bytecode; max_stack and max_locals
     * of bigMethod are then patched to the target overflow-triggering values.
     */
    static byte[] buildClass() {
        var mtd_Obj_void = MethodTypeDesc.of(ConstantDescs.CD_void,
                                             ConstantDescs.CD_Object);
        var mtd_void = MethodTypeDesc.of(ConstantDescs.CD_void);

        byte[] bytes = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
            .build(CD_HELPER, cb -> {
                cb.withVersion(50, 0);
                cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);

                // bigMethod(Object o): aload_0, pop, <goto chain>, return
                cb.withMethod("bigMethod", mtd_Obj_void,
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    mb -> mb.withCode(code -> {
                        code.aload(0);
                        code.pop();
                        for (int i = 0; i < NUM_GOTOS; i++) {
                            Label next = code.newLabel();
                            code.goto_(next);
                            code.labelBinding(next);
                        }
                        code.return_();
                    }));

                // caller(): new Object → dup → invokespecial <init> →
                //           invokestatic bigMethod → return
                cb.withMethod("caller", mtd_void,
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    mb -> mb.withCode(code -> {
                        code.new_(ConstantDescs.CD_Object);
                        code.dup();
                        code.invokespecial(ConstantDescs.CD_Object,
                            "<init>", mtd_void);
                        code.invokestatic(CD_HELPER,
                            "bigMethod", mtd_Obj_void);
                        code.return_();
                    }));
            });

        patchBigMethodMaxes(bytes);
        return bytes;
    }

    /**
     * Locates bigMethod's Code attribute and patches max_stack/max_locals
     * to TARGET_MAX_STACK/TARGET_MAX_LOCALS.  The ClassFile API computes
     * small values (max_stack=1, max_locals=1); we inflate them to create
     * the pathological overflow case.
     *
     * The Code attribute layout is:
     *   attribute_name_index(u2), attribute_length(u4),
     *   max_stack(u2), max_locals(u2), code_length(u4), code[...]...
     *
     * We search for bigMethod's unique code_length and patch the two u2
     * fields immediately before it.
     */
    static void patchBigMethodMaxes(byte[] b) {
        int expectedCodeLen = NUM_GOTOS * 3 + 3;
        for (int i = 4; i <= b.length - 4; i++) {
            int codeLen = ((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16)
                        | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (codeLen == expectedCodeLen) {
                int ms = ((b[i - 4] & 0xFF) << 8) | (b[i - 3] & 0xFF);
                int ml = ((b[i - 2] & 0xFF) << 8) | (b[i - 1] & 0xFF);
                if (ms <= 2 && ml <= 2) {
                    b[i - 4] = (byte)(TARGET_MAX_STACK >>> 8);
                    b[i - 3] = (byte)(TARGET_MAX_STACK);
                    b[i - 2] = (byte)(TARGET_MAX_LOCALS >>> 8);
                    b[i - 1] = (byte)(TARGET_MAX_LOCALS);
                    return;
                }
            }
        }
        throw new RuntimeException("Could not find bigMethod Code attribute");
    }
}
