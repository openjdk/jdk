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
 *
 */

/*
 * @test various test cases for archived MethodHandle and VarHandle objects.
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build MethodHandleTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar mh.jar
 *             MethodHandleTestApp MethodHandleTestApp$A MethodHandleTestApp$B
 *             UnsupportedBSMs UnsupportedBSMs$MyEnum
 *             ObjectMethodsTest ObjectMethodsTest$C
 * @run driver MethodHandleTest AOT --two-step-training
 */

import java.io.Serializable;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.runtime.ObjectMethods;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;
import static java.lang.invoke.MethodType.methodType;

public class MethodHandleTest {
    static final String appJar = ClassFileInstaller.getJarPath("mh.jar");
    static final String mainClass = "MethodHandleTestApp";

    public static void main(String[] args) throws Exception {
        Tester t = new Tester();
        t.run(args);
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.ASSEMBLY) {
                return new String[] {
                    "-Xlog:gc,cds+class=debug",
                    "-XX:AOTInitTestClass=MethodHandleTestApp",
                };
            } else {
                return new String[] {};
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                runMode.toString(),
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            out.shouldHaveExitValue(0);
            out.shouldContain("SwitchBootstraps.typeSwitch: 5678");

            if (!runMode.isProductionRun()) {
                // MethodHandleTestApp should be initialized in the assembly phase as well,
                // due to -XX:AOTInitTestClass.
                out.shouldContain("MethodHandleTestApp.<clinit>");
            } else {
                // Make sure MethodHandleTestApp is aot-initialized in the production run.
                out.shouldNotContain("MethodHandleTestApp.<clinit>");
                out.shouldContain("intElm = 777");
            }
        }
    }
}

// This class is cached in the AOT-initialized state. At the beginning of the production
// run, all of the static fields in MethodHandleTestApp will retain their values
// at the end of the assembly phase. MethodHandleTestApp::<clinit> is NOT executed in the
// production run.
//
// Note that the inner classes A and B are NOT cached in the AOT-initialized state.
class MethodHandleTestApp {
    static int state_A;
    static int state_B;

    static class A {
        public void virtualMethod() {}

        public static void staticMethod() {
            System.out.println("MethodHandleTestApp$A.staticMethod()");
            state_A *= 2;
        }

        static {
            System.out.println("MethodHandleTestApp$A.<clinit>");
            state_A += 3;
        }
    }

    static class B {
        static long staticField;
        long instanceField;

        static {
            System.out.println("MethodHandleTestApp$B.<clinit>");
            staticField = state_B;
            state_B += 1234;
        }
    }

    static MethodHandle staticMH;
    static MethodHandle virtualMH;

    static VarHandle staticVH;
    static VarHandle instanceVH;

    static MethodHandle arrayGetMH;

    // Created in assembly phase.
    // Used in production run.
    static MethodHandle ObjectMethodsTest_handle;

    static {
        System.out.println("MethodHandleTestApp.<clinit>");

        try {
            setupCachedMHs();
            ObjectMethodsTest_handle = ObjectMethodsTest.makeHandle();
            UnsupportedBSMs.invokeUnsupportedBSMs();
        } catch (Throwable t) {
            throw new RuntimeException("Unexpected exception", t);
        }
    }

    // This method is executed during the assembly phase.
    //
    // Store some MHs into the AOT cache. Make sure they can be used during the production run.
    // Also check that the class initialization order is consistent with specification.
    static void setupCachedMHs() throws Throwable {
        MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        virtualMH = LOOKUP.findVirtual(A.class, "virtualMethod", MethodType.methodType(void.class));
        instanceVH = LOOKUP.findVarHandle(B.class, "instanceField", long.class);

        // Make sure A is initialized before create staticMH, but the AOT-cached staticMH
        // should still include the init barrier even if A was initialized in the assembly phase.
        A.staticMethod();
        staticMH = LOOKUP.findStatic(A.class, "staticMethod", MethodType.methodType(void.class));

        // Make sure B is initialized before create staticVH, but the AOT-cached staticVH
        // should still include the init barrier even if B was initialized in the assembly phase.
        B.staticField += 5678;
        staticVH = LOOKUP.findStaticVarHandle(B.class, "staticField", long.class);

        // Array access MHs
        arrayGetMH = MethodHandles.arrayElementGetter(int[].class);
    }

    private static Object invoke(MethodHandle mh, Object ... args) {
        try {
            for (Object o : args) {
                mh = MethodHandles.insertArguments(mh, 0, o);
            }
            return mh.invoke();
        } catch (Throwable t) {
            throw new RuntimeException("Unexpected exception", t);
        }
    }

    public static void main(String[] args) throws Throwable {
        boolean isProduction = args[0].equals("PRODUCTION");

        testMethodHandles(isProduction);
        testVarHandles(isProduction);

        ObjectMethodsTest.testEqualsC(ObjectMethodsTest_handle);

        UnsupportedBSMs.invokeUnsupportedBSMs();
    }

    static void testMethodHandles(boolean isProduction) throws Throwable {
        state_A = 0;

        // (1) Invoking virtual method handle should not initialize the class
        try {
            virtualMH.invoke(null);
            throw new RuntimeException("virtualMH.invoke(null) must not succeed");
        } catch (NullPointerException t) {
            System.out.println("Expected: " + t);
        }

        if (isProduction) {
            if (state_A != 0) {
                throw new RuntimeException("state_A should be 0 but is: " + state_A);
            }
        }

        // (2) Invoking static method handle must ensure A is initialized.
        invoke(staticMH);
        if (isProduction) {
            if (state_A != 6) {
                // A.<clinit> must be executed before A.staticMethod.
                throw new RuntimeException("state_A should be 6 but is: " + state_A);
            }
        }

        // (3) Test an array access MH
        int[] intArray = new int[] {111, 222, 777};
        int intElm = (Integer)arrayGetMH.invoke(intArray, 2);
        System.out.println("intElm = " + intElm);
        if (intElm != 777) {
            throw new RuntimeException("intElm should be 777 but is: " + intElm);
        }
    }

    static void testVarHandles(boolean isProduction) throws Throwable {
        int n = 3;
        state_B = n;

        // (1) Invoking virtual method handle should not initialize the class
        try {
            instanceVH.get(null);
            throw new RuntimeException("instanceVH.get(null) must not succeed");
        } catch (NullPointerException t) {
            System.out.println("Expected: " + t);
        }

        if (isProduction) {
            if (state_B != n) {
                throw new RuntimeException("state_B should be " + n + " but is: " + state_B);
            }
        }

        // (2) Invoking static method handle must ensure B is initialized.
        long v = (long)staticVH.get();
        if (isProduction) {
            if (v != n) {
                // If you get to here, B might have been incorrectly cached in the initialized state.
                throw new RuntimeException("staticVH.get() should be " + n + " but is: " + v);
            }
            if (state_B != 1234 + n) {
                // B.<clinit> must be executed before B.staticMethod.
                throw new RuntimeException("state_B should be " + (1234 + n) + " but is: " + state_B);
            }
        }
    }
}

// Excerpt from test/jdk/java/lang/runtime/ObjectMethodsTest.java
class ObjectMethodsTest {
    public static class C {
        static final MethodType EQUALS_DESC = methodType(boolean.class, C.class, Object.class);
        static final MethodType HASHCODE_DESC = methodType(int.class, C.class);
        static final MethodType TO_STRING_DESC = methodType(String.class, C.class);

        static final MethodHandle[] ACCESSORS = accessors();
        static final String NAME_LIST = "x;y";
        private static MethodHandle[] accessors() {
            try {
                return  new MethodHandle[]{
                        MethodHandles.lookup().findGetter(C.class, "x", int.class),
                        MethodHandles.lookup().findGetter(C.class, "y", int.class),
                };
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        private final int x;
        private final int y;
        C (int x, int y) { this.x = x; this.y = y; }
        public int x() { return x; }
        public int y() { return y; }
    }

    public static MethodHandle makeHandle() throws Throwable {
        MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

        CallSite cs = (CallSite)ObjectMethods.bootstrap(LOOKUP, "equals", C.EQUALS_DESC, C.class, C.NAME_LIST, C.ACCESSORS);
        return cs.dynamicInvoker();
    }

    public static void testEqualsC(MethodHandle handle) throws Throwable {
        C c = new C(5, 5);
        assertTrue((boolean)handle.invokeExact(c, (Object)c));
        assertTrue((boolean)handle.invokeExact(c, (Object)new C(5, 5)));
        assertFalse((boolean)handle.invokeExact(c, (Object)new C(5, 4)));
        assertFalse((boolean)handle.invokeExact(c, (Object)new C(4, 5)));
        assertFalse((boolean)handle.invokeExact(c, (Object)null));
        assertFalse((boolean)handle.invokeExact(c, new Object()));
    }

    private static void assertTrue(boolean b) {
        if (b != true) {
            throw new RuntimeException("Assertion fails");
        }
    }

    private static void assertFalse(boolean b) {
        assertTrue(!b);
    }
}

class UnsupportedBSMs {
    // This method is executed during the assembly phase.
    //
    // Try to invoke some BSMs that are normally not executed in the assembly phase. However, these
    // BSMs may be executed in rare cases (such as when loading signed classes -- see JDK-8353330.)
    // Let's make sure the assembly phase can tolerate such BSMs, even if the call sites that they
    // produce are not stored into the AOT cache.
    //
    // Hopefully with enough testing in here, we can avoid situations where innocent changes in
    // core libs might cause the AOT assembly phase to fail.
    static void invokeUnsupportedBSMs() throws Throwable {
        int n = testTypeSwitch((Integer)1234);
        System.out.println("SwitchBootstraps.typeSwitch: " + n);
        if (n != 5678) {
            throw new RuntimeException("n should be " + 5678 + " but is: " + n);
        }

        Object o = getRunnableAndSerializable();
        System.out.println(o.getClass());
        if (!(o instanceof Runnable) || !(o instanceof Serializable)) {
            throw new RuntimeException("o has wrong interfaces");
        }

        String s = statementEnum(MyEnum.A);
        if (!s.equals("A")) {
            throw new RuntimeException("enum switch incorrect");
        }
    }

    static int testTypeSwitch(Number n) {
        // BSM = java/lang/runtime/SwitchBootstraps::typeSwitch
        return switch (n) {
            case Integer in -> {
                yield 5678;
            }
            default -> {
                yield 0;
            }
        };
    }

    static Runnable getRunnableAndSerializable() {
        // BSM = java/lang/invoke/LambdaMetafactory.altMetafactory
        return (Runnable & Serializable) () -> {
            System.out.println("Inside getRunnableAndSerializable");
        };
    }

    // Excerpt from test/langtools/tools/javac/patterns/EnumTypeChanges.java
    enum MyEnum { A, B; }
    static String statementEnum(MyEnum e) {
        // BSM = java/lang/runtime/SwitchBootstraps.enumSwitch
        switch (e) {
            case A ->  { return "A"; }
            case B ->  { return "B"; }
            case MyEnum e1 when e1 == null -> throw new AssertionError();
            default -> { return "D"; }
        }
    }
}
