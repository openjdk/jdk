/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package compiler.exceptions;

import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.test.lib.Asserts;
import test.java.lang.invoke.lib.InstructionHelper;

/**
 * @test
 * @bug 8350208
 * @summary Safepoints added during the processing of exception handlers should never reexecute
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @build test.java.lang.invoke.lib.InstructionHelper
 *
 * @run main/othervm compiler.exceptions.TestDebugDuringExceptionCatching
 */
public class TestDebugDuringExceptionCatching {

    public static class V {
        int v;
    }

    static final int ITERATIONS = 100;
    static final RuntimeException EXCEPTION = new RuntimeException();

    /**
     * Construct something that looks like this:
     * <pre>{@code
     * int snippet(V v) {
     *     int i = 0;
     *     LoopHead: {
     *         if (i >= 100) {
     *             goto LoopEnd;
     *         }
     *         i++;
     *         try {
     *             v.v = 1;
     *         } catch (Throwable) {
     *             // Not really, the LoopHead is the exception Handler
     *             goto LoopHead;
     *         }
     *     }
     *     LoopEnd:
     *     return i;
     * }
     * }</pre>
     */
    static final MethodHandle SNIPPET_HANDLE;
    static final ClassDesc CLASS_DESC = TestDebugDuringExceptionCatching.class.describeConstable().get();
    static {
        SNIPPET_HANDLE = InstructionHelper.buildMethodHandle(MethodHandles.lookup(),
                "snippet",
                MethodType.methodType(int.class, V.class),
                CODE -> {
                    Label loopHead = CODE.newLabel();
                    Label loopEnd = CODE.newLabel();
                    Label tryStart = CODE.newLabel();
                    Label tryEnd = CODE.newLabel();
                    CODE.
                            iconst_0().
                            istore(1).
                            // The loop head should have a RuntimeException as the sole element on the stack
                            getstatic(CLASS_DESC, "EXCEPTION", RuntimeException.class.describeConstable().get()).
                            labelBinding(loopHead).
                            pop().
                            iload(1).
                            ldc(ITERATIONS).
                            if_icmpge(loopEnd).
                            iinc(1, 1).
                            aload(0).
                            iconst_1().
                            labelBinding(tryStart).
                            putfield(V.class.describeConstable().get(), "v", int.class.describeConstable().get()).
                            labelBinding(tryEnd).
                            // The stack is empty here
                            labelBinding(loopEnd).
                            iload(1).
                            ireturn();
                    CODE.exceptionCatchAll(tryStart, tryEnd, loopHead);
                });
    }

    @Test
    private static int testBackwardHandler(V v) throws Throwable {
        return (int) SNIPPET_HANDLE.invokeExact(v);
    }

    @Run(test = "testBackwardHandler")
    public void run() throws Throwable {
        Asserts.assertEQ(ITERATIONS, testBackwardHandler(null));
    }

    public static void main(String[] args) {
        TestFramework.run();
    }
}
