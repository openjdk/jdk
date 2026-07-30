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

/*
 * @test
 * @bug 8160821
 * @summary Ensures a polymorphic call site with non-exact invocation won't
 *          be incorrectly inlined/optimized
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:CompileCommand=PrintCompilation,Main::*
 *                   -XX:CompileCommand=dontinline,Main::payload*
 *                   -Xbatch
 *                   PolymorphicCallSiteInlineTest
 */

import java.lang.invoke.*;
import java.util.concurrent.CountDownLatch;

public class PolymorphicCallSiteInlineTest {

    // C2 should inline m into payload1/payload2 in this many runs
    static final int RUNS = 0x10000;

    static final int X = 0;
    static final long Y = 0L;

    static final VarHandle VH_X;
    static final VarHandle VH_Y;

    static {
        try {
            var lookup = MethodHandles.lookup();
            VH_X = lookup.findStaticVarHandle(PolymorphicCallSiteInlineTest.class, "X", int.class);
            VH_Y = lookup.findStaticVarHandle(PolymorphicCallSiteInlineTest.class, "Y", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    };

    public static void main(String[] args) {

        CountDownLatch latch = new CountDownLatch(2);

        Thread.ofPlatform().start(() -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            System.out.println("T1 running");
            for (int i = 0; i < RUNS; i++) {
                payload1();
            }
        });

        Thread.ofPlatform().start(() -> {
            latch.countDown();
            try {
                latch.await();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            System.out.println("T2 running");
            for (int i = 0; i < RUNS; i++) {
                payload2();
            }
        });
    }

    public static int payload1() {
        return (int) m(VH_X);
    }

    public static long payload2() {
        return (long) m(VH_Y);
    }

    public static Object m(VarHandle vh) {
        // This is a polymorphic site that sees many VarHandle, but each VH
        // is considered "constant" when inlined into payload1/payload2
        // payload1/payload2 will throw exceptions if the incorrect VH gets inlined
        return vh.get();
    }
}
