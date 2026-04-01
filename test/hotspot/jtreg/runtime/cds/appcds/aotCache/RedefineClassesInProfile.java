/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Class redefinition during training run
 * @bug 8381117
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *
 * @compile test-classes/RedefGeneration0.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar redef0.jar
 *             RedefFoo RedefBar
 *             RedefTaz0 RedefTaz1 RedefTaz2 RedefTaz3 RedefTaz4
 *             Qux0 Qux1 Qux2 Qux3 Qux4
 *             Qux5 Qux6 Qux7 Qux8 Qux9
 *
 * @compile test-classes/RedefGeneration1.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar redef1.jar
 *             RedefFoo RedefBar
 *             RedefTaz0 RedefTaz1 RedefTaz2 RedefTaz3 RedefTaz4

 * @run driver RedefineClassHelper
 * @build RedefineClassesInProfile
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar RedefineClassesInProfileApp Util
 * @run driver RedefineClassesInProfile
 */

import java.io.File;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class RedefineClassesInProfile {
    public static void main(String... args) throws Exception {
        SimpleCDSAppTester.of("RedefineClassesInProfile")
            // redefineagent.jar is created by "@run driver RedefineClassHelper"
            .addVmArgs("-javaagent:redefineagent.jar")
            .addVmArgs("-Xlog:aot,aot+class=debug")
            .addVmArgs("-Xlog:redefine+class+load")
            .addVmArgs("-Xlog:aot+training+data")
            .classpath("redef0.jar" +  File.pathSeparator + "app.jar")
            .appCommandLine("RedefineClassesInProfileApp")
            .setTrainingChecker((OutputAnalyzer out) -> {
                    out.shouldContain("Skipping RedefFoo: Has been redefined");
                    out.shouldContain("Skipping RedefBar: Has been redefined");

                    for (int i = 0; i < RedefineClassesInProfileApp.num_taz; i++) {
                        out.shouldMatch("redefine,class,load.*redefined name=RedefTaz" + i);
                    }
                    for (int i = 0; i < RedefineClassesInProfileApp.num_qux; i++) {
                        out.shouldMatch("aot,class.*klasses.*app *Qux" + i);
                    }
                })
            .setAssemblyChecker((OutputAnalyzer out) -> {
                    out.shouldNotContain("RedefFoo");

                    // The names of the Redef* classes should not appear in training data,
                    // as these classes have been redefined and excluded from the AOT cache.
                    //
                    // Note: do not pass Redef* as parameters in any of the methods that can be
                    // stored into the AOT cache, or else the substring Redef* may appear in
                    // method signatures, and make the following checks fail.
                    String prefix = "aot,training,data.*";

                    out.shouldMatch(prefix + "RedefineClassesInProfileApp"); // sanity
                    out.shouldNotMatch(prefix + "RedefFoo");
                    out.shouldNotMatch(prefix + "RedefBar");
                    out.shouldNotMatch(prefix + "RedefTaz");
                })
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("Redefined: class RedefBar");
                    out.shouldContain("Redefined: class RedefFoo");
                })
            .runAOTWorkflow();
    }
}

class RedefineClassesInProfileApp {
    static final int num_taz = 5;
    static final int num_qux = 10;

    public static void main(String[] args) throws Exception {
        test1();
    }

    // test1
    // (1) Training run should work fine even if ConstantPool from redefined classes
    //     are reused by classes that are loaded later. See JDK-8381117
    // (2) Pointers to redefined classes should be cleaned from TrainingData.
    static void test1() throws Exception {
        String jarFile = "redef1.jar";
        Runnable dummy = () -> {};
        Runnable redef_bar = () -> { redefine(jarFile, RedefBar.class); };
        Runnable redef_foo = () -> { redefine(jarFile, RedefFoo.class); };

        hotspot1();

        int c1 = RedefFoo.foo1(dummy);
        check("c1", c1, 1);

        int c2 = RedefFoo.foo1(redef_bar);
        check("c2", c2, 12);

        int c3 = RedefFoo.foo1(redef_foo);
        check("c3", c3, 22);

        int c4 = RedefFoo.foo1(dummy);
        check("c4", c4, 22);

        // Redefine the RedefTaz* classes. This should free some constant pools
        for (int i = 0; i < num_taz; i++) {
            Class.forName("RedefTaz" + i);
        }
        for (int i = 0; i < num_taz; i++) {
            redefine(jarFile, Class.forName("RedefTaz" + i));
        }

        // Load the Qux* classes. They *might* reuse the constant pools
        // freed from above. See comments in test-classes/RedefGeneration0.java
        // about the crash condition for JDK-8381117.
        for (int i = 0; i < num_qux; i++) {
            Class.forName("Qux" + i);
        }
    }

    static volatile int x;
    static void hotspot1() {
        long start = System.currentTimeMillis();
        // run this loop long enough (400ms) for it to be JIT compiled.
        while (System.currentTimeMillis() - start < 400) {
            // RedefFoo will be excluded fro the AOT configuration file, so
            // any reference to RedefFoo recorded in TrainingData should be
            // also be removed. If not, we are likely to see a crash in
            // the assembly phase or production run.
            x += RedefFoo.foo0();
        }
    }

    static void check(String name, int actual, int expect) {
        System.out.println(name + " = " + actual);
        if (actual != expect) {
            throw new RuntimeException(name + " should be " + expect + ", but is " + actual);
        }
    }

    static void redefine(String jarFile, Class c) {
        try {
            byte[] b = Util.getClassFileFromJar(jarFile, c.getName());
            RedefineClassHelper.redefineClass(c, b);
            System.out.println("Redefined: " + c + ", Length = " + b.length);
        } catch (Throwable t) {
            throw new RuntimeException("Unexpected failure", t);
        }
    }
}
