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
 * @test
 * @summary Store old classes linked state in AOT cache as long as their verification constraints are not excluded.
 * @bug 8317269
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build OldClass OldA OldClassWithVerifierConstraints OldClassWithExcludedVerifierConstraints
 * @build OldClassSupport
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AppUsesOldClass MyIntf OldClass OldA NewB MyEvent MyEvent2
 *                 OldClassWithVerifierConstraints
 *                 OldClassWithExcludedVerifierConstraints
 *                 NewClassWithExcludedVerifierConstraints
 * @run driver OldClassSupport
 */

import jdk.jfr.Event;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class OldClassSupport {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "AppUsesOldClass";

    public static void main(String[] args) throws Exception {
        Tester tester = new Tester();
        tester.run(new String[] {"AOT", "--two-step-training"} );
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
            return new String[] {
                "-Xlog:aot",
                "-Xlog:aot+class=debug",
                "-Xlog:aot+resolve=trace",
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {"-Xlog:cds+class=debug", mainClass};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            Class[] included = {
                OldClass.class,
                OldA.class,
                NewB.class,
                OldClassWithVerifierConstraints.class,
            };

            Class[] excluded = {
                OldClassWithExcludedVerifierConstraints.class,
                NewClassWithExcludedVerifierConstraints.class,
            };


            if (runMode == RunMode.TRAINING) {
                shouldInclude(out, false, included);
                shouldNotInclude(out, excluded);
                shouldSkip(out, excluded);
            } else if (runMode == RunMode.ASSEMBLY) {
                shouldInclude(out, true, included);
                shouldNotInclude(out, excluded);
            }
        }
    }

    static void shouldInclude(OutputAnalyzer out, boolean linked, Class[] classes) {
        for (Class c : classes) {
            out.shouldMatch("aot,class.* = 0x.* app *" + c.getName() + (linked ? " .*aot-linked" : ""));
        }
    }

    static void shouldNotInclude(OutputAnalyzer out, Class[] classes) {
        for (Class c : classes) {
            out.shouldNotMatch("aot,class.* = 0x.* app *" + c.getName());
        }
    }

    static void shouldSkip(OutputAnalyzer out, Class[] classes) {
        for (Class c : classes) {
            out.shouldMatch("Skipping " + c.getName() + ": verification constraint .* is excluded");
        }
    }
}

class AppUsesOldClass {
    public static void main(String args[]) {
        System.out.println("Old Class Instance: " + new OldClass());

        System.out.println(get_OldA_from_NewB());
        System.out.println(OldClassWithVerifierConstraints.get_OldA_from_NewB());
        System.out.println(OldClassWithExcludedVerifierConstraints.get_Event_from_MyEvent());
        System.out.println(NewClassWithExcludedVerifierConstraints.get_MyEvent_from_MyEvent2());
        System.out.println(new MyEvent());

        // OldClassWithExcludedVerifierConstraints should still be excluded even it has been used
        // in a lambda expression during the training run.
        run((OldClassWithExcludedVerifierConstraints x) -> {
                System.out.println(x);
            });
    }

    static OldA get_OldA_from_NewB() {
        return new NewB();
    }

    static void run(MyIntf intf) {
        intf.function(new OldClassWithExcludedVerifierConstraints());
    }
}

interface MyIntf {
    public void function(OldClassWithExcludedVerifierConstraints x);
}

class NewB extends OldA {}

class MyEvent extends Event {}
class MyEvent2 extends MyEvent {}

class NewClassWithExcludedVerifierConstraints {
    static MyEvent get_MyEvent_from_MyEvent2() {
        return new MyEvent2();
    }
}
