/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8301007
 * @key randomness
 * @summary Verify that mismatches of the preload attribute are properly handled in the calling convention.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @compile TestMismatchHandling.jcod TestMismatchHandling.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-Inline -XX:-InlineAccessors -XX:-UseBimorphicInlining -XX:-UseCHA -XX:-UseTypeProfile
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestMismatchHandling::test*
 *                   compiler.valhalla.inlinetypes.TestMismatchHandling
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-Inline -XX:-InlineAccessors -XX:-UseBimorphicInlining -XX:-UseCHA -XX:-UseTypeProfile
 *                   -XX:CompileCommand=compileonly,*::method
 *                   compiler.valhalla.inlinetypes.TestMismatchHandling
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-Inline -XX:-InlineAccessors -XX:-UseBimorphicInlining -XX:-UseCHA -XX:-UseTypeProfile
 *                   compiler.valhalla.inlinetypes.TestMismatchHandling
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-Inline -XX:-InlineAccessors -XX:-UseBimorphicInlining -XX:-UseCHA -XX:-UseTypeProfile
 *                   -XX:-InlineTypePassFieldsAsArgs
 *                   compiler.valhalla.inlinetypes.TestMismatchHandling
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-Inline -XX:-InlineAccessors -XX:-UseBimorphicInlining -XX:-UseCHA -XX:-UseTypeProfile
 *                   -XX:-InlineTypeReturnedAsFields
 *                   compiler.valhalla.inlinetypes.TestMismatchHandling
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+DeoptimizeNMethodBarriersALot
 *                   compiler.valhalla.inlinetypes.TestMismatchHandling
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.valhalla.inlinetypes.TestMismatchHandling
 */

// ##################################### WARNING ######################################
// Use below script to re-generate TestMismatchHandling.jcod, don't modify it manually.
// Be careful when changing anything (even the order) in this test and related files.
// ##################################### WARNING ######################################

/*
  #!/bin/bash
  export PATH=/oracle/valhalla/build/fastdebug/jdk/bin/:$PATH
  ASMTOOLS=/oracle/valhalla/open/test/lib

  # With preload attribute
  javac --enable-preview --source 27 TestMismatchHandlingGenerator.java
  java -cp $ASMTOOLS org.openjdk.asmtools.Main jdec MyValue1Mismatch.class MyValue2Mismatch.class MyValue3Mismatch.class MyValue4Mismatch.class MyValue5Mismatch.class MyValue6Mismatch.class MyValue7Mismatch.class Verifiable.class B.class I3.class I4.class E.class G.class J.class K.class L.class P.class Q.class R.class S.class TestMismatchHandlingHelper.class > TestMismatchHandling.jcod

  # Without preload attribute
  sed -i 's/value class MyValue/class MyValue/g' TestMismatchHandlingGenerator.java
  javac TestMismatchHandlingGenerator.java
  java -cp $ASMTOOLS org.openjdk.asmtools.Main jdec A.class C.class I1.class I2.class D.class F.class H.class I5.class M.class N.class O.class I6.class P.class >> TestMismatchHandling.jcod

  sed -i 's/class MyValue/value class MyValue/g' TestMismatchHandlingGenerator.java
*/

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;

public class TestMismatchHandling {
    public static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        M m = new M();
        // Make sure M::method is C1 compiled once with unloaded MyValue4 and not re-compiled
        for (int i = 0; i < 1000; ++i) {
            TestMismatchHandlingHelper.test4(m, true);
        }
        Method disable = M.class.getDeclaredMethod("method", boolean.class);
        WHITE_BOX.makeMethodNotCompilable(disable, 1, false);
        WHITE_BOX.makeMethodNotCompilable(disable, 2, false);
        WHITE_BOX.makeMethodNotCompilable(disable, 3, false);
        WHITE_BOX.makeMethodNotCompilable(disable, 4, false);

        // Sometimes, exclude some methods from compilation with C2 to stress test the calling convention
        // WARNING: This triggers class loading of argument/return types of all methods!
        if (Utils.getRandomInstance().nextBoolean()) {
            ArrayList<Method> methods = new ArrayList<Method>();
            Collections.addAll(methods, TestMismatchHandlingHelper.class.getDeclaredMethods());
            Collections.addAll(methods, A.class.getDeclaredMethods());
            Collections.addAll(methods, B.class.getDeclaredMethods());
            Collections.addAll(methods, C.class.getDeclaredMethods());
            Collections.addAll(methods, E.class.getDeclaredMethods());
            Collections.addAll(methods, F.class.getDeclaredMethods());
            Collections.addAll(methods, G.class.getDeclaredMethods());
            Collections.addAll(methods, H.class.getDeclaredMethods());
            Collections.addAll(methods, J.class.getDeclaredMethods());
            Collections.addAll(methods, K.class.getDeclaredMethods());
            Collections.addAll(methods, L.class.getDeclaredMethods());
            // Don't do this because it would load MyValue5
            // Collections.addAll(methods, N.class.getDeclaredMethods());
            System.out.println("Excluding methods from C2 compilation:");
            for (Method method : methods) {
                if (Utils.getRandomInstance().nextBoolean()) {
                    System.out.println(method);
                    WHITE_BOX.makeMethodNotCompilable(method, 4, false);
                }
            }
        }

        A a = new A();
        B b = new B();
        C c = new C();
        D d = new D();
        E e = new E();
        H h = new H();
        J j = new J();
        K k = new K();
        N n = new N();
        O o = new O();
        P p = new P();
        Q q = new Q();
        R r = new R();

        // Warmup
        for (int i = 0; i < 50_000; ++i) {
            TestMismatchHandlingHelper.test1(a, a, a, b, c, b, b, c);
            TestMismatchHandlingHelper.test1(b, a, a, b, c, b, b, c);
            TestMismatchHandlingHelper.test1(c, b, a, b, c, c, b, c);
            TestMismatchHandlingHelper.test2(d, d, d, d, d, d,  d, d, d, d, d, d,  e, e, e, e, e, e,  e, e, e, e, e, e,  d, e);
            TestMismatchHandlingHelper.test2(d, d, d, d, d, d,  d, d, d, d, d, d,  e, e, e, e, e, e,  e, e, e, e, e, e,  d, e);
            TestMismatchHandlingHelper.test3(h, h, h,  j, k, j, k, j,  h, k);
            TestMismatchHandlingHelper.test3(h, h, h,  j, k, j, k, k,  h, k);
            TestMismatchHandlingHelper.test4(m, true);
            TestMismatchHandlingHelper.test5(n, true);
            TestMismatchHandlingHelper.test7(o, true);
            TestMismatchHandlingHelper.test8(p, p, p,  q, r, q, r, q,  p, r);
            TestMismatchHandlingHelper.test8(p, p, p,  q, r, q, r, r,  p, r);
        }

        // Only load these now
        F f = new F();
        G g = new G();
        L l = new L();
        S s = new S();

        for (int i = 0; i < 50_000; ++i) {
            TestMismatchHandlingHelper.test1(a, a, a, b, c, b, b, c);
            TestMismatchHandlingHelper.test1(b, a, a, b, c, b, b, c);
            TestMismatchHandlingHelper.test1(c, b, a, b, c, c, b, c);
            TestMismatchHandlingHelper.test2(d, f, g, d, f, d,  d, f, g, d, f, d,  e, f, g, e, f, g,  e, f, g, e, f, g,  d, e);
            TestMismatchHandlingHelper.test2(d, f, g, d, f, f,  d, f, g, d, f, f,  e, f, g, e, f, f,  e, f, g, e, f, f,  d, e);
            TestMismatchHandlingHelper.test2(d, f, g, f, g, g,  d, f, g, f, g, g,  e, f, g, f, g, g,  e, f, g, f, g, g,  d, e);
            TestMismatchHandlingHelper.test3(h, l, h,  j, k, j, k, j,  h, k);
            TestMismatchHandlingHelper.test3(h, l, h,  j, k, k, k, k,  h, k);
            TestMismatchHandlingHelper.test3(h, l, l,  j, k, k, l, l,  h, l);
            TestMismatchHandlingHelper.test4(m, false);
            TestMismatchHandlingHelper.test5(n, false);
            TestMismatchHandlingHelper.test6(f, g, l);
            TestMismatchHandlingHelper.test7TriggerCalleeCompilation(o);
            TestMismatchHandlingHelper.test8(p, s, p,  q, r, q, r, q,  p, r);
            TestMismatchHandlingHelper.test8(p, s, p,  q, r, r, r, r,  p, r);
            TestMismatchHandlingHelper.test8(p, s, s,  q, r, r, s, s,  p, s);
        }
        TestMismatchHandlingHelper.test7(o, false).verify();

        switch (Utils.getRandomInstance().nextInt() % 3) {
        case 0:
            TestMismatchHandlingHelper.test2(d, d, d, d, d, d,  d, d, d, d, d, d,  e, e, e, e, e, e,  e, e, e, e, e, e,  d, e);
            TestMismatchHandlingHelper.test3(l, h, l,  k, l, l, j, j,  h, l);
            TestMismatchHandlingHelper.test8(s, p, s,  r, s, s, q, q,  p, s);
            break;
        case 1:
            TestMismatchHandlingHelper.test2(f, f, f, f, f, f,  f, f, f, f, f, f,  f, f, f, f, f, f,  f, f, f, f, f, f,  d, e);
            TestMismatchHandlingHelper.test3(l, h, l,  l, j, j, k, l,  h, l);
            TestMismatchHandlingHelper.test8(s, p, s,  s, q, q, r, s,  p, s);
            break;
        case 2:
            TestMismatchHandlingHelper.test2(g, g, g, g, g, g,  g, g, g, g, g, g,  g, g, g, g, g, g,  g, g, g, g, g, g,  d, e);
            TestMismatchHandlingHelper.test3(l, h, l,  j, k, k, l, j,  h, l);
            TestMismatchHandlingHelper.test8(s, p, s,  q, r, r, s, q,  p, s);
            break;
        }
    }
}
