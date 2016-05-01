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
 * @library /testlibrary /test/lib ../patches
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.vm.annotation
 * @build java.base/java.lang.invoke.MethodHandleHelper
 * @build sun.hotspot.WhiteBox
 * @run main/bootclasspath -XX:+IgnoreUnrecognizedVMOptions
 *                         -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                         -Xbatch -XX:-TieredCompilation -XX:CICompilerCount=1
 *                         -XX:+FoldStableValues
 *                         compiler.jsr292.NonInlinedCall.GCTest
 */

package compiler.jsr292.NonInlinedCall;

import java.lang.invoke.MethodHandleHelper;
import java.lang.invoke.MethodHandleHelper.NonInlinedReinvoker;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;

import java.lang.invoke.MethodType;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.Stable;

import sun.hotspot.WhiteBox;

import static jdk.test.lib.Asserts.*;

public class GCTest {
    static final MethodHandles.Lookup LOOKUP = MethodHandleHelper.IMPL_LOOKUP;

    static class T {
        static int f1() { return 0; }
        static int f2() { return 1; }
    }

    static @Stable MethodHandle mh;
    static PhantomReference<Object> lform;

    static final ReferenceQueue<Object> rq = new ReferenceQueue<>();
    static final WhiteBox WB = WhiteBox.getWhiteBox();

    @DontInline
    static int invokeBasic() {
        try {
            return MethodHandleHelper.invokeBasicI(mh);
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
        lform = new PhantomReference<>(MethodHandleHelper.getLambdaForm(mh), rq);

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
