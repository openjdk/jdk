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

            ;
    }
}

class ResolvedConstantsApp implements Runnable {
    public static void main(String args[]) {
        System.out.println("Hello ResolvedConstantsApp");
        Object a = new ResolvedConstantsApp();
        ((Runnable)a).run();

        ResolvedConstantsFoo foo = new ResolvedConstantsFoo();
        ResolvedConstantsBar bar = new ResolvedConstantsBar();
        bar.a ++;
        bar.b ++;
        bar.doit();
    }
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
    }
}
