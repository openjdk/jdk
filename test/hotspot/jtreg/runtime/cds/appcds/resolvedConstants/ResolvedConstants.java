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
 * @test id=static
 * @summary Dump time resolution of constant pool entries (Static CDS archive).
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes/
 * @build OldProvider OldClass OldConsumer StringConcatTestOld
 * @build ResolvedConstants
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 ResolvedConstantsApp ResolvedConstantsFoo ResolvedConstantsBar
 *                 MyInterface InterfaceWithClinit NormalClass
 *                 OldProvider OldClass OldConsumer SubOfOldClass
 *                 StringConcatTest StringConcatTestOld
 * @run driver ResolvedConstants STATIC
 */

/*
 * @test id=dynamic
 * @summary Dump time resolution of constant pool entries (Dynamic CDS archive)
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes/
 * @build OldProvider OldClass OldConsumer StringConcatTestOld
 * @build ResolvedConstants
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 ResolvedConstantsApp ResolvedConstantsFoo ResolvedConstantsBar
 *                 MyInterface InterfaceWithClinit NormalClass
 *                 OldProvider OldClass OldConsumer SubOfOldClass
 *                 StringConcatTest StringConcatTestOld
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Dcds.app.tester.workflow=DYNAMIC -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. ResolvedConstants DYNAMIC
 */

import java.util.function.Consumer;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class ResolvedConstants {
    static final String classList = "ResolvedConstants.classlist";
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = ResolvedConstantsApp.class.getName();

    static boolean aotClassLinking;
    public static void main(String[] args) throws Exception {
        test(args, false);
        test(args, true);
    }

    static void test(String[] args, boolean testMode) throws Exception {
        aotClassLinking = testMode;

        SimpleCDSAppTester.of("ResolvedConstantsApp" + (aotClassLinking ? "1" : "0"))
            .addVmArgs(aotClassLinking ? "-XX:+AOTClassLinking" : "-XX:-AOTClassLinking",
                       "-Xlog:aot+resolve=trace",
                       "-Xlog:aot+class=debug",
                       "-Xlog:cds+class=debug")
            .classpath(appJar)
            .appCommandLine(mainClass)
            .setAssemblyChecker((OutputAnalyzer out) -> {
                    checkAssemblyOutput(args, out);
                })
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("Hello ResolvedConstantsApp");
                })
            .run(args);
    }

    static void checkAssemblyOutput(String args[], OutputAnalyzer out) {
        testGroup("Class References", out)
            // Always resolve reference when a class references itself
            .shouldMatch(ALWAYS("klass.* ResolvedConstantsApp app => ResolvedConstantsApp app"))

            // Always resolve reference when a class references a super class
            .shouldMatch(ALWAYS("klass.* ResolvedConstantsApp app => java/lang/Object boot"))
            .shouldMatch(ALWAYS("klass.* ResolvedConstantsBar app => ResolvedConstantsFoo app"))

            // Always resolve reference when a class references a super interface
            .shouldMatch(ALWAYS("klass.* ResolvedConstantsApp app => java/lang/Runnable boot"))

            // Without -XX:+AOTClassLinking:
            //   java/lang/System is in the boot loader but ResolvedConstantsApp is loaded by the app loader.
            //   Even though System is in the vmClasses list, when ResolvedConstantsApp looks up
            //   "java/lang/System" in its ConstantPool, the app loader may not have resolved the System
            //   class yet (i.e., there's no initiaited class entry for System in the app loader's dictionary)
            .shouldMatch(AOTLINK_ONLY("klass.* ResolvedConstantsApp .*java/lang/System"));

        testGroup("Field References", out)
            // Always resolve references to fields in the current class or super class(es)
            .shouldMatch(ALWAYS("field.* ResolvedConstantsBar => ResolvedConstantsBar.b:I"))
            .shouldMatch(ALWAYS("field.* ResolvedConstantsBar => ResolvedConstantsBar.a:I"))
            .shouldMatch(ALWAYS("field.* ResolvedConstantsBar => ResolvedConstantsFoo.a:I"))
            .shouldMatch(ALWAYS("field.* ResolvedConstantsFoo => ResolvedConstantsFoo.a:I"))

            // Resolve field references to child classes ONLY when using -XX:+AOTClassLinking
            .shouldMatch(AOTLINK_ONLY("field.* ResolvedConstantsFoo => ResolvedConstantsBar.a:I"))
            .shouldMatch(AOTLINK_ONLY("field.* ResolvedConstantsFoo => ResolvedConstantsBar.b:I"))

            // Resolve field references to unrelated classes ONLY when using -XX:+AOTClassLinking
            .shouldMatch(AOTLINK_ONLY("field.* ResolvedConstantsApp => ResolvedConstantsBar.a:I"))
            .shouldMatch(AOTLINK_ONLY("field.* ResolvedConstantsApp => ResolvedConstantsBar.b:I"));

        if (args[0].equals("DYNAMIC")) {
            // AOT resolution of CP methods/indy references is not implemeted
            return;
        }

        testGroup("Method References", out)
            // Should resolve references to own constructor
            .shouldMatch(ALWAYS("method.* ResolvedConstantsApp ResolvedConstantsApp.<init>:"))
            // Should resolve references to super constructor
            .shouldMatch(ALWAYS("method.* ResolvedConstantsApp java/lang/Object.<init>:"))

            // Should resolve interface methods in VM classes
            .shouldMatch(ALWAYS("interface method .* ResolvedConstantsApp java/lang/Runnable.run:"))

            // Should resolve references to own non-static method (private or public)
            .shouldMatch(ALWAYS("method.*: ResolvedConstantsBar ResolvedConstantsBar.doBar:"))
            .shouldMatch(ALWAYS("method.*: ResolvedConstantsApp ResolvedConstantsApp.privateInstanceCall:"))
            .shouldMatch(ALWAYS("method.*: ResolvedConstantsApp ResolvedConstantsApp.publicInstanceCall:"))

            // Should not resolve references to static method
            .shouldNotMatch(ALWAYS("method.*: ResolvedConstantsApp ResolvedConstantsApp.staticCall:"))

            // Should resolve references to method in super type
            .shouldMatch(ALWAYS("method.*: ResolvedConstantsBar ResolvedConstantsFoo.doBar:"))

            // Without -XX:+AOTClassLinking App class cannot resolve references to methods in boot classes:
            //    When the app class loader tries to resolve a class X that's normally loaded by
            //    the boot loader, it's possible for the app class loader to get a different copy of
            //    X (by using MethodHandles.Lookup.defineClass(), etc). Therefore, let's be on
            //    the side of safety and revert all such references.
            .shouldMatch(AOTLINK_ONLY("method.*: ResolvedConstantsApp java/io/PrintStream.println:"))
            .shouldMatch(AOTLINK_ONLY("method.*: ResolvedConstantsBar java/lang/Class.getName:"))

            // Resole resolve methods in unrelated classes ONLY when using -XX:+AOTClassLinking
            .shouldMatch(AOTLINK_ONLY("method.*: ResolvedConstantsApp ResolvedConstantsBar.doit:"))

          // End ---
            ;


        // Indy References ---
        if (aotClassLinking) {
            testGroup("Indy References", out)
               .shouldContain("Cannot aot-resolve Lambda proxy because OldConsumer is excluded")
               .shouldContain("Cannot aot-resolve Lambda proxy because OldProvider is excluded")
               .shouldContain("Cannot aot-resolve Lambda proxy because OldClass is excluded")
               .shouldContain("Cannot aot-resolve Lambda proxy of interface type InterfaceWithClinit")
               .shouldMatch("klasses.* app *NormalClass[$][$]Lambda/.* hidden aot-linked inited")
               .shouldNotMatch("klasses.* app *SubOfOldClass[$][$]Lambda/")
               .shouldMatch("archived indy *CP entry.*StringConcatTest .* => java/lang/invoke/StringConcatFactory.makeConcatWithConstants")
               .shouldNotMatch("archived indy *CP entry.*StringConcatTestOld .* => java/lang/invoke/StringConcatFactory.makeConcatWithConstants");
        }
    }

    static String ALWAYS(String s) {
        return ",resolve.*archived " + s;
    }

    static String AOTLINK_ONLY(String s) {
        if (aotClassLinking) {
            return ALWAYS(s);
        } else {
            return ",resolve.*reverted " + s;
        }
    }

    static OutputAnalyzer testGroup(String name, OutputAnalyzer out) {
        System.out.println("Checking for: " + name);
        return out;
    }
}

class ResolvedConstantsApp implements Runnable {
    public static void main(String args[]) {
        System.out.println("Hello ResolvedConstantsApp");
        ResolvedConstantsApp app = new ResolvedConstantsApp();
        ResolvedConstantsApp.staticCall();
        app.privateInstanceCall();
        app.publicInstanceCall();
        Object a = app;
        ((Runnable)a).run();

        ResolvedConstantsFoo foo = new ResolvedConstantsFoo();
        ResolvedConstantsBar bar = new ResolvedConstantsBar();
        bar.a ++;
        bar.b ++;
        bar.doit();

        testLambda();
        StringConcatTest.test();
        StringConcatTestOld.main(null);
    }
    private static void staticCall() {}
    private void privateInstanceCall() {}
    public void publicInstanceCall() {}

    public void run() {}

    static void testLambda() {
        // The functional type used in the Lambda is an excluded class
        OldProvider op = () -> {
            return null;
        };

        // A captured value is an instance of an excluded Class
        OldClass c = new OldClass();
        Runnable r = () -> {
            System.out.println("Test 1 " + c);
        };
        r.run();

        // The functional interface accepts an argument that's an excluded class
        MyInterface i = (o) -> {
            System.out.println("Test 2 " + o);
        };
        i.dispatch(c);

        // Method reference to old class
        OldConsumer oldConsumer = new OldConsumer();
        Consumer<String> wrapper = oldConsumer::consumeString;
        wrapper.accept("Hello");

        // Lambda of interfaces that have <clinit> are not archived.
        InterfaceWithClinit i2 = () -> {
            System.out.println("Test 3");
        };
        i2.dispatch();

        // These two classes have almost identical source code, but
        // only NormalClass should have its lambdas pre-resolved.
        // SubOfOldClass is "old" -- it should be excluded from the AOT cache,
        // so none of its lambda proxies should be cached
        NormalClass.testLambda();   // Lambda proxy should be cached
        SubOfOldClass.testLambda(); // Lambda proxy shouldn't be cached
    }
}

class StringConcatTest {
    static void test() {
        System.out.println("StringConcatTest <concat> " + new StringConcatTest()); // concat should be aot-resolved
    }
}

/* see StringConcatTestOld.jasm

class StringConcatTestOld {
    public static void main(String args[]) {
        // concat should be aot-resolved => the MethodType refers to an old class
        System.out.println("StringConcatTestOld <concat> " + new OldConsumer());
    }
}
*/

class NormalClass {
    static void testLambda() {
        Runnable r = () -> {
            System.out.println("NormalClass testLambda");
        };
        r.run();
    }
}

class SubOfOldClass extends OldClass {
    static void testLambda() {
        Runnable r = () -> {
            System.out.println("SubOfOldClass testLambda");
        };
        r.run();
    }
}

interface MyInterface {
    void dispatch(OldClass c);
}

interface InterfaceWithClinit {
    static final long X = System.currentTimeMillis();
    void dispatch();
    default long dummy() { return X; }
}

class ResolvedConstantsFoo {
    int a = 1;
    void doit() {
    }

    void doBar(ResolvedConstantsBar bar) {
        bar.a ++;
        bar.b ++;
    }
}

class ResolvedConstantsBar extends ResolvedConstantsFoo {
    int b = 2;
    void doit() {
        System.out.println("Hello ResolvedConstantsBar and " + ResolvedConstantsFoo.class.getName());
        System.out.println("a = " + a);
        System.out.println("a = " + ((ResolvedConstantsFoo)this).a);
        System.out.println("b = " + b);

        doBar(this);

        ((ResolvedConstantsFoo)this).doBar(this);
    }
}
