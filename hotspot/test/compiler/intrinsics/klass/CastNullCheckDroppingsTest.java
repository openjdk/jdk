/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test NullCheckDroppingsTest
 * @bug 8054492
 * @summary Casting can result in redundant null checks in generated code
 * @requires vm.flavor == "server" & !vm.emulatedClient
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                                sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xmixed -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:CompileThreshold=1000
 *                   -XX:CompileCommand=exclude,compiler.intrinsics.klass.CastNullCheckDroppingsTest::runTest
 *                   compiler.intrinsics.klass.CastNullCheckDroppingsTest
 */

package compiler.intrinsics.klass;

import jdk.test.lib.Platform;
import sun.hotspot.WhiteBox;
import sun.hotspot.code.NMethod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

public class CastNullCheckDroppingsTest {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    static final BiFunction<Class, Object, Object> fCast = (c, o) -> c.cast(o);

    static final MethodHandle SET_SSINK;
    static final MethodHandle MH_CAST;

    static {
        try {
            SET_SSINK = MethodHandles.lookup().findSetter(CastNullCheckDroppingsTest.class, "ssink", String.class);
            MH_CAST = MethodHandles.lookup().findVirtual(Class.class,
                                                         "cast",
                                                         MethodType.methodType(Object.class, Object.class));
        }
        catch (Exception e) {
            throw new Error(e);
        }
    }

    static volatile String svalue = "A";
    static volatile String snull = null;
    static volatile Integer iobj = new Integer(0);
    static volatile int[] arr = new int[2];
    static volatile Class objClass = String.class;
    static volatile Class nullClass = null;

    String  ssink;
    Integer isink;
    int[]   asink;

    public static void main(String[] args) throws Exception {
        if (!Platform.isServer() || Platform.isEmulatedClient()) {
            throw new Error("TESTBUG: Not server mode");
        }
        // Make sure background compilation is disabled
        if (WHITE_BOX.getBooleanVMFlag("BackgroundCompilation")) {
            throw new Error("TESTBUG: Background compilation enabled");
        }
        // Make sure Tiered compilation is disabled
        if (WHITE_BOX.getBooleanVMFlag("TieredCompilation")) {
            throw new Error("TESTBUG: Tiered compilation enabled");
        }

        Method methodClassCast = CastNullCheckDroppingsTest.class.getDeclaredMethod("testClassCast", String.class);
        Method methodMHCast    = CastNullCheckDroppingsTest.class.getDeclaredMethod("testMHCast",    String.class);
        Method methodMHSetter  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testMHSetter",  String.class);
        Method methodFunction  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testFunction",  String.class);

        CastNullCheckDroppingsTest t = new CastNullCheckDroppingsTest();
        t.runTest(methodClassCast, false);
        t.runTest(methodMHCast,    false);
        t.runTest(methodMHSetter,  false);
        t.runTest(methodFunction,  false);

        // Edge cases
        Method methodClassCastNull = CastNullCheckDroppingsTest.class.getDeclaredMethod("testClassCastNull", String.class);
        Method methodNullClassCast = CastNullCheckDroppingsTest.class.getDeclaredMethod("testNullClassCast", String.class);
        Method methodClassCastObj  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testClassCastObj",  Object.class);
        Method methodObjClassCast  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testObjClassCast",  String.class);
        Method methodVarClassCast  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testVarClassCast",  String.class);
        Method methodClassCastInt  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testClassCastInt",  Object.class);
        Method methodIntClassCast  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testIntClassCast",  Object.class);
        Method methodClassCastint  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testClassCastint",  Object.class);
        Method methodintClassCast  = CastNullCheckDroppingsTest.class.getDeclaredMethod("testintClassCast",  Object.class);
        Method methodClassCastPrim = CastNullCheckDroppingsTest.class.getDeclaredMethod("testClassCastPrim", Object.class);
        Method methodPrimClassCast = CastNullCheckDroppingsTest.class.getDeclaredMethod("testPrimClassCast", Object.class);

        t.runTest(methodClassCastNull, false);
        t.runTest(methodNullClassCast, false);
        t.runTest(methodClassCastObj,  false);
        t.runTest(methodObjClassCast,  true);
        t.runTest(methodVarClassCast,  true);
        t.runTest(methodClassCastInt,  false);
        t.runTest(methodIntClassCast,  true);
        t.runTest(methodClassCastint,  false);
        t.runTest(methodintClassCast,  false);
        t.runTest(methodClassCastPrim, false);
        t.runTest(methodPrimClassCast, true);
    }

    void testClassCast(String s) {
        try {
            ssink = String.class.cast(s);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testClassCastNull(String s) {
        try {
            ssink = String.class.cast(null);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testNullClassCast(String s) {
        try {
            ssink = (String)nullClass.cast(s);
            throw new AssertionError("NullPointerException is not thrown");
        } catch (NullPointerException t) {
            // Ignore NullPointerException
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testClassCastObj(Object s) {
        try {
            ssink = String.class.cast(s);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testObjClassCast(String s) {
        try {
            ssink = (String)objClass.cast(s);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testVarClassCast(String s) {
        Class cl = (s == null) ? null : String.class;
        try {
            ssink = (String)cl.cast(svalue);
            if (s == null) {
                throw new AssertionError("NullPointerException is not thrown");
            }
        } catch (NullPointerException t) {
            // Ignore NullPointerException
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testClassCastInt(Object s) {
        try {
            ssink = String.class.cast(iobj);
            throw new AssertionError("ClassCastException is not thrown");
        } catch (ClassCastException t) {
            // Ignore ClassCastException: Cannot cast java.lang.Integer to java.lang.String
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testIntClassCast(Object s) {
        try {
            isink = Integer.class.cast(s);
            if (s != null) {
                throw new AssertionError("ClassCastException is not thrown");
            }
        } catch (ClassCastException t) {
            // Ignore ClassCastException: Cannot cast java.lang.String to java.lang.Integer
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testClassCastint(Object s) {
        try {
            ssink = String.class.cast(45);
            throw new AssertionError("ClassCastException is not thrown");
        } catch (ClassCastException t) {
            // Ignore ClassCastException: Cannot cast java.lang.Integer to java.lang.String
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testintClassCast(Object s) {
        try {
            isink = int.class.cast(s);
            if (s != null) {
                throw new AssertionError("ClassCastException is not thrown");
            }
        } catch (ClassCastException t) {
            // Ignore ClassCastException: Cannot cast java.lang.String to java.lang.Integer
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testClassCastPrim(Object s) {
        try {
            ssink = String.class.cast(arr);
            throw new AssertionError("ClassCastException is not thrown");
        } catch (ClassCastException t) {
            // Ignore ClassCastException: Cannot cast [I to java.lang.String
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testPrimClassCast(Object s) {
        try {
            asink = int[].class.cast(s);
            if (s != null) {
                throw new AssertionError("ClassCastException is not thrown");
            }
        } catch (ClassCastException t) {
            // Ignore ClassCastException: Cannot cast java.lang.String to [I
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testMHCast(String s) {
        try {
            ssink = (String) (Object) MH_CAST.invokeExact(String.class, (Object) s);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testMHSetter(String s) {
        try {
            SET_SSINK.invokeExact(this, s);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void testFunction(String s) {
        try {
            ssink = (String) fCast.apply(String.class, s);
        } catch (Throwable t) {
            throw new Error(t);
        }
    }

    void runTest(Method method, boolean deopt) {
        if (method == null) {
            throw new AssertionError("method was not found");
        }
        // Ensure method is compiled
        WHITE_BOX.testSetDontInlineMethod(method, true);
        for (int i = 0; i < 3000; i++) {
            try {
                method.invoke(this, svalue);
            } catch (Exception e) {
                throw new Error("Unexpected exception: ", e);
            }
        }
        NMethod nm = getNMethod(method);

        // Passing null should cause a de-optimization
        // if method is compiled with a null-check.
        try {
            method.invoke(this, snull);
        } catch (Exception e) {
            throw new Error("Unexpected exception: ", e);
        }
        checkDeoptimization(method, nm, deopt);
    }

    static NMethod getNMethod(Method test) {
        // Because background compilation is disabled, method should now be compiled
        if (!WHITE_BOX.isMethodCompiled(test)) {
            throw new AssertionError(test + " not compiled");
        }

        NMethod nm = NMethod.get(test, false); // not OSR nmethod
        if (nm == null) {
            throw new AssertionError(test + " missing nmethod?");
        }
        if (nm.comp_level != 4) {
            throw new AssertionError(test + " compiled by not C2: " + nm);
        }
        return nm;
    }

    static void checkDeoptimization(Method method, NMethod nmOrig, boolean deopt) {
        // Check deoptimization event (intrinsic Class.cast() works).
        if (WHITE_BOX.isMethodCompiled(method) == deopt) {
            throw new AssertionError(method + " was" + (deopt ? " not" : "") + " deoptimized");
        }
        if (deopt) {
            return;
        }
        // Ensure no recompilation when no deoptimization is expected.
        NMethod nm = NMethod.get(method, false); // not OSR nmethod
        if (nm == null) {
            throw new AssertionError(method + " missing nmethod?");
        }
        if (nm.comp_level != 4) {
            throw new AssertionError(method + " compiled by not C2: " + nm);
        }
        if (nm.compile_id != nmOrig.compile_id) {
            throw new AssertionError(method + " was recompiled: old nmethod=" + nmOrig + ", new nmethod=" + nm);
        }
    }
}
