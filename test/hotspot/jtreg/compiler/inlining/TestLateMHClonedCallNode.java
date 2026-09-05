/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8370939
 * @summary C2: SIGSEGV in SafePointNode::verify_input when processing MH call from Compile::process_late_inline_calls_no_inline()
 * @run main/othervm -XX:-BackgroundCompilation -XX:CompileOnly=TestLateMHClonedCallNode::test1
 *                   -XX:CompileOnly=TestLateMHClonedCallNode::test2 TestLateMHClonedCallNode
 * @run main TestLateMHClonedCallNode
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestLateMHClonedCallNode {
    private static int field;

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < 20_000; i++) {
            test1(true);
            test1(false);
            test2(true);
            test2(false);
        }
    }

    private static int test1(boolean flag) throws Throwable {
        return inlined1(flag);
    }

    private static int inlined1(boolean flag) throws Throwable {
        MethodHandle mh = mh1;
        for (int i = 0; i < 3; ++i) {
            if (i > 1) {
                mh = mh2;
            }
        }
        int res = 0;
        for (int i = 0; i < 2; i++) {
            if (!flag) {
                field = 42;
            }
            res += (int) mh.invokeExact();
        }
        return res;
    }

    private static int test2(boolean flag) throws Throwable {
        int res = (int)unknownMh.invokeExact();
        return inlined2(flag);
    }

    private static int inlined2(boolean flag) throws Throwable {
        MethodHandle mh = mh1;
        for (int i = 0; i < 3; ++i) {
            if (i > 1) {
                mh = mh2;
            }
        }
        int res = 0;
        for (int i = 0; i < 2; i++) {
            if (!flag) {
                field = 42;
            }
            res += (int) mh.invokeExact();
        }
        return res;
    }

    static final MethodHandle mh1;
    static final MethodHandle mh2;
    static MethodHandle unknownMh;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            mh1 = lookup.findStatic(TestLateMHClonedCallNode.class, "method1", MethodType.methodType(int.class));
            mh2 = lookup.findStatic(TestLateMHClonedCallNode.class, "method2", MethodType.methodType(int.class));
            unknownMh = mh1;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    static int method1() { return 0; }
    static int method2() { return 42; }
}
