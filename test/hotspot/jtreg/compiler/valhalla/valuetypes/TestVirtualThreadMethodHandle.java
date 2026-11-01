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
 * @bug 8388596
 * @summary Test freezing a c2 compiled MethodHandle target frame with a value class argument
 * @enablePreview
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm -XX:-Inline -Xbatch -XX:-TieredCompilation TestVirtualThreadMethodHandle
 */

import java.lang.invoke.*;

import jdk.test.lib.Asserts;

public class TestVirtualThreadMethodHandle {
    static value class V { int a=0, b=0, c=0, d=0, e=0, f=0, g=0; }
    static boolean failed;

    static MethodHandle MH;

    static void target(V v) { Thread.yield(); }

    static void run() {
        try {
            for (int n = 0; n < 20_000; n++) MH.invokeExact(new V());
        } catch (Throwable t) {
            failed = true;
            throw new RuntimeException("MethodHandle invocation failed", t);
        }
    }

    public static void main(String[] args) throws Exception {
        MH = MethodHandles.lookup().findStatic(TestVirtualThreadMethodHandle.class, "target", MethodType.methodType(void.class, V.class));
        run();
        Thread.startVirtualThread(TestVirtualThreadMethodHandle::run).join();
        Asserts.assertFalse(failed, "MethodHandle invocation failed");
    }
}
