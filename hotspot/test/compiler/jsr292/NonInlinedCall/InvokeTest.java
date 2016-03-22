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
 * @library /testlibrary /test/lib / ../patches
 * @modules java.base/jdk.internal.vm.annotation
 * @build java.base/java.lang.invoke.MethodHandleHelper
 * @build sun.hotspot.WhiteBox
 * @build compiler.jsr292.NonInlinedCall.InvokeTest
 * @run main/bootclasspath -XX:+IgnoreUnrecognizedVMOptions
 *                         -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                         -Xbatch -XX:-TieredCompilation -XX:CICompilerCount=1
 *                         compiler.jsr292.NonInlinedCall.InvokeTest
 */

package compiler.jsr292.NonInlinedCall;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleHelper;
import java.lang.invoke.MethodHandleHelper.NonInlinedReinvoker;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.internal.vm.annotation.DontInline;

import sun.hotspot.WhiteBox;

import static jdk.test.lib.Asserts.*;

public class InvokeTest {
    static MethodHandles.Lookup LOOKUP = MethodHandleHelper.IMPL_LOOKUP;

    static final MethodHandle virtualMH;  // invokevirtual   T.f1
    static final MethodHandle staticMH;   // invokestatic    T.f2
    static final MethodHandle intfMH;     // invokeinterface I.f1
    static final MethodHandle specialMH;  // invokespecial   T.f4 T
    static final MethodHandle basicMH;

    static final WhiteBox WB = WhiteBox.getWhiteBox();

    static volatile boolean doDeopt = false;

    static {
        try {
            MethodType mtype = MethodType.methodType(Class.class);

            virtualMH  = LOOKUP.findVirtual(T.class, "f1", mtype);
            staticMH   = LOOKUP.findStatic (T.class, "f2", mtype);
            intfMH     = LOOKUP.findVirtual(I.class, "f3", mtype);
            specialMH  = LOOKUP.findSpecial(T.class, "f4", mtype, T.class);
            basicMH    = NonInlinedReinvoker.make(staticMH);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    static class T implements I {
        @DontInline public        Class<?> f1() { if (doDeopt) WB.deoptimizeAll(); return T.class; }
        @DontInline public static Class<?> f2() { if (doDeopt) WB.deoptimizeAll(); return T.class; }
        @DontInline private       Class<?> f4() { if (doDeopt) WB.deoptimizeAll(); return T.class; }
    }

    static class P1 extends T {
        @DontInline public Class<?> f1() { if (doDeopt) WB.deoptimizeAll(); return P1.class; }
        @DontInline public Class<?> f3() { if (doDeopt) WB.deoptimizeAll(); return P1.class; }
    }

    static class P2 extends T {
        @DontInline public Class<?> f1() { if (doDeopt) WB.deoptimizeAll(); return P2.class; }
        @DontInline public Class<?> f3() { if (doDeopt) WB.deoptimizeAll(); return P2.class; }
    }

    static interface I {
        @DontInline default Class<?> f3() { if (doDeopt) WB.deoptimizeAll(); return I.class; }
    }

    @DontInline
    static void linkToVirtual(Object obj, Class<?> extecpted) {
        try {
            Class<?> cls = (Class<?>)virtualMH.invokeExact((T)obj);
            assertEquals(cls, obj.getClass());
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    @DontInline
    static void linkToInterface(Object obj, Class<?> expected) {
        try {
            Class<?> cls = (Class<?>)intfMH.invokeExact((I)obj);
            assertEquals(cls, expected);
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    @DontInline
    static void linkToStatic() {
        try {
            Class<?> cls = (Class<?>)staticMH.invokeExact();
            assertEquals(cls, T.class);
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    @DontInline
    static void linkToSpecial(Object obj, Class<?> expected) {
        try {
            Class<?> cls = (Class<?>)specialMH.invokeExact((T)obj);
            assertEquals(cls, expected);
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    @DontInline
    static void invokeBasic() {
        try {
            Class<?> cls = (Class<?>)MethodHandleHelper.invokeBasicL(basicMH);
            assertEquals(cls, T.class);
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    static void run(Runnable r) {
        for (int i = 0; i < 20_000; i++) {
            r.run();
        }

        doDeopt = true;
        r.run();
        doDeopt = false;

        WB.clearInlineCaches();

        for (int i = 0; i < 20_000; i++) {
            r.run();
        }

        doDeopt = true;
        r.run();
        doDeopt = false;
    }

    static void testVirtual() {
        System.out.println("linkToVirtual");

        // Monomorphic case (optimized virtual call)
        run(() -> linkToVirtual(new T(), T.class));

        // Megamorphic case (virtual call)
        Object[] recv = new Object[] { new T(), new P1(), new P2() };
        run(() -> {
            for (Object r : recv) {
                linkToVirtual(r, r.getClass());
            }});
    }

    static void testInterface() {
        System.out.println("linkToInterface");

        // Monomorphic case (optimized virtual call), concrete target method
        run(() -> linkToInterface(new P1(), P1.class));

        // Monomorphic case (optimized virtual call), default target method
        run(() -> linkToInterface(new T(), I.class));

        // Megamorphic case (virtual call)
        Object[][] recv = new Object[][] {{new T(), I.class}, {new P1(), P1.class}, {new P2(), P2.class}};
        run(() -> {
            for (Object[] r : recv) {
                linkToInterface(r[0], (Class<?>)r[1]);
            }});
    }

    static void testSpecial() {
        System.out.println("linkToSpecial");
        // Monomorphic case (optimized virtual call)
        run(() -> linkToSpecial(new T(), T.class));
    }

    static void testStatic() {
        System.out.println("linkToStatic");
        // static call
        run(() -> linkToStatic());
    }

    static void testBasic() {
        System.out.println("invokeBasic");
        // static call
        run(() -> invokeBasic());
    }

    public static void main(String[] args) {
        testVirtual();
        testInterface();
        testSpecial();
        testStatic();
        testBasic();
    }
}
