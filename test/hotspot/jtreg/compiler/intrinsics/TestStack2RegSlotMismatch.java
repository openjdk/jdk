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

/**
 * @test
 * @bug 8359235
 * @summary Test C1 stack2reg after fixing incorrect use of T_LONG in intrinsic
 * @requires vm.debug == true & vm.compiler1.enabled
 * @run main/othervm -XX:TieredStopAtLevel=1
 *                   -XX:C1MaxInlineSize=200
 *                   -XX:CompileThreshold=10
 *                   -XX:CompileCommand=compileonly,java.lang.invoke.LambdaFormEditor::putInCache
 *                   compiler.intrinsics.TestStack2RegSlotMismatch
 */
package compiler.intrinsics;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

public class TestStack2RegSlotMismatch {
    public static int target(int x, int y) {
        return x + y;
    }

    public static void main(String[] args) throws Throwable {
        MethodHandle mh = MethodHandles.lookup().findStatic(
            TestStack2RegSlotMismatch.class,
            "target",
            MethodType.methodType(int.class, int.class, int.class)
        );
        List<Object> argsList = new ArrayList<>();
        int j = 0;

        for (int i = 0; i < 50; i++) {
            mh = MethodHandles.dropArguments(mh, 0, int.class);
            argsList.add(0);
            argsList.add(1);
            argsList.add(2);
            Object result = mh.invokeWithArguments(argsList);
            j += (int) result;
            argsList.remove(argsList.size() - 1);
            argsList.remove(argsList.size() - 1);
            if (i % 5 == 0) {
                Thread.sleep(1000);
            }
        }

        if (j == 150) {
            System.out.println("passed");
        } else {
            throw new Exception("TestStack2RegSlotMismatch Error");
        }
    }
}