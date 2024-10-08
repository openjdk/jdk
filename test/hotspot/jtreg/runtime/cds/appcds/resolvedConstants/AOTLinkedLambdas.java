/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary AOT resolution of lambda expressions
 * @bug 8340836
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes/
 * @build AOTLinkedLambdas
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AOTLinkedLambdasApp InitTracker
 *                 IntfWithNoClinit IntfWithNoClinit2
 *                 IntfWithClinit IntfWithClinit2
 *                 FooA FooB
 *                 BarA BarB BarC
 * @run driver AOTLinkedLambdas
 */

import java.util.function.Supplier;
import static java.util.stream.Collectors.*;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTLinkedLambdas {
    static final String classList = "AOTLinkedLambdas.classlist";
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = AOTLinkedLambdasApp.class.getName();

    public static void main(String[] args) throws Exception {
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldContain("Hello AOTLinkedLambdasApp");
            });

        CDSOptions opts = (new CDSOptions())
            .addPrefix("-XX:ExtraSharedClassListFile=" + classList,
                       "-XX:+AOTClassLinking",
                       "-Xlog:cds+resolve=trace",
                       "-Xlog:cds+class=debug",
                       "-cp", appJar);

        OutputAnalyzer dumpOut = CDSTestUtils.createArchiveAndCheck(opts);
        dumpOut.shouldContain("Cannot aot-resolve Lambda proxy of interface type IntfWithClinit (has <clinit>)");
        dumpOut.shouldNotContain("Cannot aot-resolve Lambda proxy of interface type IntfWithClinit2 (has <clinit>)");

        CDSOptions runOpts = (new CDSOptions())
            .setUseVersion(false)
            .addPrefix("-Xlog:cds",
                       "-esa",         // see JDK-8340836
                       "-cp", appJar)
            .addSuffix(mainClass);

        CDSTestUtils.run(runOpts)
            .assertNormalExit("Hello AOTLinkedLambdasApp",
                              "hello, world");
    }
}

class AOTLinkedLambdasApp {
    static {
        System.out.println("AOTLinkedLambdasApp.<clinit>");
    }
    public static void main(String args[]) {
        System.out.println("Hello AOTLinkedLambdasApp");

        // (1) Simple tests
        var words = java.util.List.of("hello", "fuzzy", "world");
        System.out.println(words.stream().filter(w->!w.contains("u")).collect(joining(", ")));
        // => hello, world

        // (2) Test for <clinit> order.
        testClinitOrder();
    }


    // Check that aot-linking of indys do not run <clinit> in unexpected order:
    //
    //     For any type T that *appears* in an indy callsite, if T::<clinit> exists and
    //     must be executed during the linking of this callsite, then T::<clinit>
    //     must be executed the first time this indy instruction is executed, not earlier.
    //
    // This test case tries to enumerate all possibilities where a type T may appear
    // in an indy callsite, and asserts when T is initialized.
    //
    // Note: although T may appear in many parts of an indy callsite, ONLY the return type
    // of factoryType must be initialized during the linking of this callsite. All other types
    // will be initialized per the regular "4 bytecode rule" of getstatic/putstatic/new/instanceof.
    static void testClinitOrder() {
        /*
         * An indy callsite is associated with the following MethodType and MethodHandles:
         *
         * https://github.com/openjdk/jdk/blob/580eb62dc097efeb51c76b095c1404106859b673/src/java.base/share/classes/java/lang/invoke/LambdaMetafactory.java#L293-L309
         *
         * MethodType factoryType         The expected signature of the {@code CallSite}.  The
         *                                parameter types represent the types of capture variables;
         *                                the return type is the interface to implement.   When
         *                                used with {@code invokedynamic}, this is provided by
         *                                the {@code NameAndType} of the {@code InvokeDynamic}
         *
         * MethodType interfaceMethodType Signature and return type of method to be
         *                                implemented by the function object.
         *
         * MethodHandle implementation    A direct method handle describing the implementation
         *                                method which should be called (with suitable adaptation
         *                                of argument types and return types, and with captured
         *                                arguments prepended to the invocation arguments) at
         *                                invocation time.
         *
         * MethodType dynamicMethodType   The signature and return type that should
         *                                be enforced dynamically at invocation time.
         *                                In simple use cases this is the same as
         *                                {@code interfaceMethodType}.
         */

        // Initial condition: no <clinit> used by our Foo? and Bar? types have been called.
        InitTracker.assertOrder("InitTracker");

        //==============================
        // Case (i) -- Check for types used by factoryType, interfaceMethodType and dynamicMethodType
        //             (Note: no tests for captured variables in factoryType yet; will be tested in case (ii))
        // factoryType         = "()LIntfWithNoClinit;
        // interfaceMethodType = "(LFooB;)LFooA;"
        // implementation      = "REF_invokeStatic AOTLinkedLambdasApp.implAB:(LBarB;)LBarA;"
        // dynamicMethodType   = "(LBarB;)LBarA;"
        IntfWithNoClinit<BarA, BarB> noclinit = AOTLinkedLambdasApp::implAB;

        // None of the Foo? and Bar? types used by the lambda should have been initialized yet, even though
        // the indy callsite has been resolved now.
        InitTracker.assertOrder("InitTracker");

        BarB barB = new BarB();
        InitTracker.assertOrder("InitTracker, FooB, BarB");
        BarA barA = noclinit.doit(barB);
        System.out.println(barB);
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA");

        //==============================
        // Case (ii) -- Check for types used by captured variables in factoryType
        BarC barC = null;
        IntfWithNoClinit2 noclinit2 = () -> { return barC.hashCode(); };
        try {
            noclinit2.doit();
            throw new RuntimeException("NullPointerException should have been thrown");
        } catch (NullPointerException npe) {
            // expected
        }
        // BarC shouldn't be initialized as no instances of it has been created.
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA");

        //==============================
        // Case (iii) -- factoryType has a <clinit> and must be initialized during
        // the indy resolution. This lambda CANNOT be aot-linked.
        // factoryType = "()LIntfWithClinit;"
        IntfWithClinit hasclinit = () -> { return 1234; };

        // must be initialized even before the lambda is used.
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IntfWithClinit");

        //==============================
        // Case (iv) -- factoryType has a <clinit>, but it doesn't need to be initialized during
        // the indy resolution (see IntfWithClinit2). This lambda can be aot-linked.
        // factoryType = "()LIntfWithClinit2;"
        IntfWithClinit2 hasclinit2 = () -> { return 1234; };
        // IntfWithClinit2 must not be initialized yet
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IntfWithClinit");

        System.out.println(hasclinit2.doit());
        // IntfWithClinit2 must not be initialized yet
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IntfWithClinit");

         System.out.println(IntfWithClinit2._dummy);
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IntfWithClinit, IntfWithClinit2");
    }

    static BarA implAB(BarB param) {
        return new BarA(param);
    }
}


// An interface with no <clinit> method. A lambda implementing this
// interface can be AOT-linked.
@FunctionalInterface
interface IntfWithNoClinit<X extends FooA, Y extends FooB> {
    X doit(Y param);
}

// Another interface with no <clinit> method. A lambda implementing this
// interface can be AOT-linked.
@FunctionalInterface
interface IntfWithNoClinit2 {
    int doit();
}

// A functional interface with a <clinit> that must be executed before we instantiate
// any lambda that implements this interface. Requirements:
// - the interface has a <clinit> function.
// - the interface has at least one default method.
@FunctionalInterface
interface IntfWithClinit {
    static final int _dummy = InitTracker.trackEvent("IntfWithClinit");
    default int dummy() { return _dummy; }
    int doit();
}

// A functional interface with a <clinit>, but it doesn't need to be initialized because
// it doesn't have any default methods.
// The <clinit> should be accessed only when IntfWithClinit2._dummy is accessed.
@FunctionalInterface
interface IntfWithClinit2 {
    static final int _dummy = InitTracker.trackEvent("IntfWithClinit2");
    int doit();
}

class InitTracker {
    static String actualOrder = "InitTracker";
    static int trackEvent(String event) {
        actualOrder += ", " + event;
        return actualOrder.lastIndexOf(',');
    }
    static void assertOrder(String wantOrder) {
        System.out.println("wantOrder   = " + wantOrder);
        System.out.println("actualOrder = " + actualOrder);
        if (!actualOrder.equals(wantOrder)) {
            throw new RuntimeException("Want <clinit> order: {" + wantOrder + "}, but got {" + actualOrder + "}");
        }
    }
}

interface FooA {
    static final int _dummy = InitTracker.trackEvent("FooA");
    default int dummy() { return _dummy; }
}

interface FooB {
    static final int _dummy = InitTracker.trackEvent("FooB");
    default int dummy() { return _dummy; }
}

class BarA implements FooA {
    static {InitTracker.trackEvent("BarA");}
    BarA(BarB dummy) {}
}

class BarB implements FooB {
    static {InitTracker.trackEvent("BarB");}
}

class BarC {
    static {InitTracker.trackEvent("BarC");}
}
