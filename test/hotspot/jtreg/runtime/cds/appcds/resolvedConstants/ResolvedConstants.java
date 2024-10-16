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
 * @summary Dump time resolutiom of constant pool entries.
 * @requires vm.cds
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib
 * @build ResolvedConstants
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar ResolvedConstantsApp ResolvedConstantsFoo ResolvedConstantsBar
 * @run driver ResolvedConstants
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ResolvedConstants {
    static final String classList = "ResolvedConstants.classlist";
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = ResolvedConstantsApp.class.getName();

    public static void main(String[] args) throws Exception {
        // dump class list
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldContain("Hello ResolvedConstantsApp");
            });

        CDSOptions opts = (new CDSOptions())
            .addPrefix("-XX:ExtraSharedClassListFile=" + classList,
                       "-cp", appJar,
                       "-Xlog:cds+resolve=trace");
        CDSTestUtils.createArchiveAndCheck(opts)
          // Class References ---

            // Always resolve reference when a class references itself
            .shouldMatch("cds,resolve.*archived klass.* ResolvedConstantsApp app => ResolvedConstantsApp app")

            // Always resolve reference when a class references a super class
            .shouldMatch("cds,resolve.*archived klass.* ResolvedConstantsApp app => java/lang/Object boot")
            .shouldMatch("cds,resolve.*archived klass.* ResolvedConstantsBar app => ResolvedConstantsFoo app")

            // Always resolve reference when a class references a super interface
            .shouldMatch("cds,resolve.*archived klass.* ResolvedConstantsApp app => java/lang/Runnable boot")

            // java/lang/System is in the root loader but ResolvedConstantsApp is loaded by the app loader.
            // Even though System is in the vmClasses list, when ResolvedConstantsApp looks up
            // "java/lang/System" in its ConstantPool, the app loader may not have resolved the System
            // class yet (i.e., there's no initiaited class entry for System in the app loader's dictionary)
            .shouldMatch("cds,resolve.*reverted klass.* ResolvedConstantsApp .*java/lang/System")

          // Field References ---

            // Always resolve references to fields in the current class or super class(es)
            .shouldMatch("cds,resolve.*archived field.* ResolvedConstantsBar => ResolvedConstantsBar.b:I")
            .shouldMatch("cds,resolve.*archived field.* ResolvedConstantsBar => ResolvedConstantsBar.a:I")
            .shouldMatch("cds,resolve.*archived field.* ResolvedConstantsBar => ResolvedConstantsFoo.a:I")

            // Do not resolve field references to child classes
            .shouldMatch("cds,resolve.*archived field.* ResolvedConstantsFoo => ResolvedConstantsFoo.a:I")
            .shouldMatch("cds,resolve.*reverted field.* ResolvedConstantsFoo    ResolvedConstantsBar.a:I")
            .shouldMatch("cds,resolve.*reverted field.* ResolvedConstantsFoo    ResolvedConstantsBar.b:I")

            // Do not resolve field references to unrelated classes
            .shouldMatch("cds,resolve.*reverted field.* ResolvedConstantsApp    ResolvedConstantsBar.a:I")
            .shouldMatch("cds,resolve.*reverted field.* ResolvedConstantsApp    ResolvedConstantsBar.b:I")

          // Method References ---

            // Should resolve references to own constructor
            .shouldMatch("cds,resolve.*archived method .* ResolvedConstantsApp ResolvedConstantsApp.<init>:")
            // Should resolve references to super constructor
            .shouldMatch("cds,resolve.*archived method .* ResolvedConstantsApp java/lang/Object.<init>:")

            // Should resolve interface methods in VM classes
            .shouldMatch("cds,resolve.*archived interface method .* ResolvedConstantsApp java/lang/Runnable.run:")

            // Should resolve references to own non-static method (private or public)
            .shouldMatch("archived method.*: ResolvedConstantsBar ResolvedConstantsBar.doBar:")
            .shouldMatch("archived method.*: ResolvedConstantsApp ResolvedConstantsApp.privateInstanceCall:")
            .shouldMatch("archived method.*: ResolvedConstantsApp ResolvedConstantsApp.publicInstanceCall:")

            // Should not resolve references to static method
            .shouldNotMatch(" archived method CP entry.*: ResolvedConstantsApp ResolvedConstantsApp.staticCall:")

            // Should resolve references to method in super type
            .shouldMatch(" archived method CP entry.*: ResolvedConstantsBar ResolvedConstantsFoo.doBar:")

            // App class cannot resolve references to methods in boot classes:
            //    When the app class loader tries to resolve a class X that's normally loaded by
            //    the boot loader, it's possible for the app class loader to get a different copy of
            //    X (by using MethodHandles.Lookup.defineClass(), etc). Therefore, let's be on
            //    the side of safety and revert all such references.
            //
            //    This will be addressed in JDK-8315737.
            .shouldMatch("reverted method.*: ResolvedConstantsApp java/io/PrintStream.println:")
            .shouldMatch("reverted method.*: ResolvedConstantsBar java/lang/Class.getName:")

            // Should not resolve methods in unrelated classes.
            .shouldMatch("reverted method.*: ResolvedConstantsApp ResolvedConstantsBar.doit:")

          // End ---
            ;
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
    }
    private static void staticCall() {}
    private void privateInstanceCall() {}
    public void publicInstanceCall() {}

    public void run() {}
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
