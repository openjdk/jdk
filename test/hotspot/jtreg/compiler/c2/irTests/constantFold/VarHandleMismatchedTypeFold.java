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

package compiler.c2.irTests.constantFold;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

/*
 * @test
 * @bug 8160821
 * @summary Verify constant folding is possible for mismatched VarHandle access
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.constantFold.VarHandleMismatchedTypeFold
 */
public class VarHandleMismatchedTypeFold {

    public static void main(String[] args) {
        TestFramework.run();
    }

    static final int a = 5;

    static final VarHandle vh;

    static {
        try {
            vh = MethodHandles.lookup().findStaticVarHandle(VarHandleMismatchedTypeFold.class,
                    "a", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.LOAD_L})
    public long testSum() {
        return 2L + (long) vh.get();
    }

    @Check(test = "testSum")
    public void runTestSum() {
        long sum = testSum();
        if (sum != 2L + 5L) {
            throw new IllegalStateException("Failed, unexpected sum " + sum);
        }
    }

}
