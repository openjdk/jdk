/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *                 IA IB IC ID1 ID2 IE1 IE2 IF1 IF2 IG1 IG2 IH1 IH2 IH3
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
                       "-Xlog:aot+resolve=trace",
                       "-Xlog:aot+class=debug",
                       "-Xlog:cds+class=debug",
                       "-cp", appJar);

        OutputAnalyzer dumpOut = CDSTestUtils.createArchiveAndCheck(opts);
        dumpOut.shouldContain("Can aot-resolve Lambda proxy of interface type IA");
        dumpOut.shouldContain("Can aot-resolve Lambda proxy of interface type IB");
        dumpOut.shouldContain("Cannot aot-resolve Lambda proxy of interface type IC");
        dumpOut.shouldContain("Can aot-resolve Lambda proxy of interface type ID2");
        dumpOut.shouldContain("Cannot aot-resolve Lambda proxy of interface type IE2"); // unsupported = IE1
        dumpOut.shouldContain("Cannot aot-resolve Lambda proxy of interface type IF2");
        dumpOut.shouldContain("Cannot aot-resolve Lambda proxy of interface type IG2");
        dumpOut.shouldContain("Cannot aot-resolve Lambda proxy of interface type IH3"); // unsupported = IH1

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


    // Check that aot-linking of lambdas does not cause <clinit> to be skipped or
    // otherwise executed in the wrong order.
    //
    // A lambda is declared to implement an interface X, but it also implicitly
    // implements all super interfaces of X.
    //
    // For any interface IN that's implemented by a lambda, if IN has declared
    // a non-abstract, non-static method (JVMS 5.5. Initialization), IN must be
    // initialized before the lambda can be linked. If IN::<clinit> exists, the
    // initialization of IN can have side effects.
    //
    // AOTConstantPoolResolver::is_indy_resolution_deterministic() excludes
    // any lambda if initializing its interfaces can cause side effects. This test
    // checks that such exclusions are working as expected.
    //
    // This test also proves that is_indy_resolution_deterministic() doen't need to check
    // for all other types that are mentioned by the lambda call site, as those classes
    // will not be initialized as part of linking the lambda.
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

        // Initial condition: no <clinit> used by our Foo* and Bar* types have been called.
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
        // (IA) No default methods -- is not initialized during lambda linking. Lambda can be archived.
        IA ia = () -> {};
        ia.doit();
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA");
        System.out.println(IA._dummy);
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA");

        //==============================
        // (IB) Has default method but has not <clinit> -- OK to initialize IB during lambda linking. Lambda can be archived.
        IB ib = () -> {};
        ib.doit();
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA");

        //==============================
        // (IC) Has both default method and <clinit> -- cannot AOT link the lambda
        IC ic = () -> {};
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC");
        ic.doit();

        //==============================
        // ID1 - has default method, but no <clinit>
        // ID2 - has <clinit>, but no default method
        ID2 id2 = () -> {};
        id2.doit();
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC");
        System.out.println(ID2._dummy);
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2");

       //==============================
        // IE1 - has both default method and <clinit>
        // IE2 - has <clinit>, but no default method
        IE2 ie2 = () -> {};
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2, IE1");
        System.out.println(IE2._dummy);
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2, IE1, IE2");

       //==============================
        // IF1 - has <clinit>, but no default method
        // IF2 - has both default method and <clinit>
        IF2 if2 = () -> {};
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2, IE1, IE2, IF2");
        System.out.println(IF1._dummy);
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2, IE1, IE2, IF2, IF1");

       //==============================
        // IG1 - has both default method and <clinit>
        // IG2 - has both default method and <clinit>
        IG2 ig2 = () -> {};
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2, IE1, IE2, IF2, IF1, IG1, IG2");

       //==============================
        // Similar to IE1/IE2, but IH3 is one more level away from IH1
        // IH1 - has both default method and <clinit>
        // IH2 - has <clinit>, but no default method
        // IH3 - has <clinit>, but no default method
        IH3 ih3 = () -> {};
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2, IE1, IE2, IF2, IF1, IG1, IG2, IH1");
        System.out.println(IH3._dummy);
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2, IE1, IE2, IF2, IF1, IG1, IG2, IH1, IH3");
        System.out.println(IH2._dummy);
        InitTracker.assertOrder("InitTracker, FooB, BarB, FooA, BarA, IA, IC, ID2, IE1, IE2, IF2, IF1, IG1, IG2, IH1, IH3, IH2");
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


// (IA) No default methods -- is not initialized during lambda linking. Lambda can be archived.
@FunctionalInterface interface IA {
    static int _dummy = InitTracker.trackEvent("IA");
    void doit();
}

// (IB) Has default method but has not <clinit> -- OK to initialize IB during lambda linking. Lambda can be archived.
@FunctionalInterface interface IB {
    default int dummy() { return 0; }
    void doit();
}

// (IC) Has both default method and <clinit> -- cannot AOT link the lambda
@FunctionalInterface interface IC {
    static int _dummy = InitTracker.trackEvent("IC");
    default int dummy() { return _dummy; }
    void doit();
}

// (ID1/ID2)
@FunctionalInterface interface ID1 { // has default method, but no <clinit>
    default int dummy() { return 0; }
    void doit();
}

@FunctionalInterface interface ID2 extends ID1 { // has <clinit>, but no default method
    static int _dummy = InitTracker.trackEvent("ID2");
}

// (IE1/IE2)
@FunctionalInterface interface IE1 { // has default method and <clinit>
    static int _dummy = InitTracker.trackEvent("IE1");
    default int dummy() { return _dummy; }
    void doit();
}

@FunctionalInterface interface IE2 extends IE1 { // has <clinit>, but no default method
    static int _dummy = InitTracker.trackEvent("IE2");
}

// (IF1/IF2)
@FunctionalInterface interface IF1 { // has <clinit>, but no default method
    static int _dummy = InitTracker.trackEvent("IF1");
    void doit();
}

@FunctionalInterface interface IF2 extends IF1 { // has default method and <clinit>
    static int _dummy = InitTracker.trackEvent("IF2");
    default int dummy() { return 0; }
}

// (IG1/IG2)
@FunctionalInterface interface IG1 { // has default method and <clinit>
    static int _dummy = InitTracker.trackEvent("IG1");
    default int dummy() { return _dummy; }
    void doit();
}

@FunctionalInterface interface IG2 extends IG1 { // has default method and <clinit>
    static int _dummy = InitTracker.trackEvent("IG2");
    default int dummy() { return _dummy; }
}

// (IH1/IH2/IH3)
@FunctionalInterface interface IH1 { // has default method and <clinit>
    static int _dummy = InitTracker.trackEvent("IH1");
    default int dummy() { return _dummy; }
    void doit();
}

@FunctionalInterface interface IH2 extends IH1 { // has <clinit> but no default method
    static int _dummy = InitTracker.trackEvent("IH2");
}

@FunctionalInterface interface IH3 extends IH2 { // has <clinit> but no default method
    static int _dummy = InitTracker.trackEvent("IH3");
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
