/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8072008
 * @library /testlibrary /../../test/lib
 * @build GCTest NonInlinedReinvoker
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              java.lang.invoke.GCTest
 *                              java.lang.invoke.GCTest$T
 *                              java.lang.invoke.NonInlinedReinvoker
 *                              jdk.test.lib.Asserts
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-TieredCompilation -XX:CICompilerCount=1
 *                      java.lang.invoke.GCTest
 */
package java.lang.invoke;

import sun.hotspot.WhiteBox;

import java.lang.ref.*;
import static jdk.test.lib.Asserts.*;

public class GCTest {
    static final MethodHandles.Lookup LOOKUP = MethodHandles.Lookup.IMPL_LOOKUP;

    static class T {
        static int f1() { return 0; }
        static int f2() { return 1; }
    }

    static @Stable MethodHandle mh;
    static PhantomReference<LambdaForm> lform;

    static final ReferenceQueue<LambdaForm> rq = new ReferenceQueue<>();
    static final WhiteBox WB = WhiteBox.getWhiteBox();

    @DontInline
    static int invokeBasic() {
        try {
            return (int) mh.invokeBasic();
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    static void test(int expected) {
        for (int i = 0; i < 20_000; i++) {
            invokeBasic();
        }
        assertEquals(invokeBasic(), expected);
    }

    public static void main(String[] args) throws Exception {
        mh = NonInlinedReinvoker.make(
                LOOKUP.findStatic(T.class, "f1", MethodType.methodType(int.class)));

        // Monitor LambdaForm GC
        lform = new PhantomReference<>(mh.form, rq);

        test(0);
        WB.clearInlineCaches();
        test(0);

        mh = NonInlinedReinvoker.make(
                LOOKUP.findStatic(T.class, "f2", MethodType.methodType(int.class)));

        Reference<?> ref = null;
        while (ref == null) {
            WB.fullGC();
            try {
                ref = rq.remove(1000);
            } catch (InterruptedException e) { /*ignore*/ }
        }

        test(1);
        WB.clearInlineCaches();
        test(1);

        System.out.println("TEST PASSED");
    }
}
