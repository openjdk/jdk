/*
 * Copyright (c) 2024, Red Hat and/or its affiliates. All rights reserved.
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
 * @bug 8335843
 * @summary C2 hits assert(_print_inlining_stream->size() > 0) failed: missing inlining msg
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-BackgroundCompilation -XX:+PrintCompilation -XX:+PrintInlining TestPrintInliningLateMHCall
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestPrintInliningLateMHCall {
    static final MethodHandle mh1;
    static MethodHandle mh2;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            mh1 = lookup.findStatic(TestPrintInliningLateMHCall.class, "lateResolved", MethodType.methodType(void.class));
            mh2 = mh1;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < 20_000; i++) {
            testHelper(0);
            testHelper(10);
            test();
        }
    }

    private static void testHelper(int i) throws Throwable {
        MethodHandle mh = null;
        if (i == 10) {
            mh = mh1;
        } else {
            mh = mh2;
        }
        mh.invokeExact();
    }

    private static void test() throws Throwable {
        int i;
        for (i = 0; i < 10; i++) {

        }
        testHelper(i);
    }

    private static void lateResolved() {
        // noop
    }
}
