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
 * @test Test AOT-cached flattened fields
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @enablePreview
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes/
 * @modules java.base/jdk.internal.value
 * @modules java.base/jdk.internal.vm.annotation
 * @build FlattenedFieldTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             FlattenedFieldTestApp
 *             MyAOTInitedClass
 *             valueclasses.BytePair
 *             valueclasses.BytePairWrapper
 *             valueclasses.BytePairWrapperWrapper
 *             valueclasses.CharPair
 *             valueclasses.ShortPair
 *             valueclasses.ShortPairWrapper
 *             valueclasses.ValueClassHelper
 * @run driver FlattenedFieldTest AOT --two-step-training
 */

import java.util.Arrays;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.StringArrayUtils;

// From ../test-classes/
import valueclasses.BytePair;
import valueclasses.BytePairWrapper;
import valueclasses.BytePairWrapperWrapper;
import valueclasses.CharPair;
import valueclasses.ShortPair;
import valueclasses.ShortPairWrapper;
import valueclasses.ValueClassHelper;

public class FlattenedFieldTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = FlattenedFieldTestApp.class.getName();

    public static void main(String[] args) throws Exception {
        new Tester().run(args);
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
            String args[] = StringArrayUtils.concat("--enable-preview",
                                                    "--add-exports",
                                                    "java.base/jdk.internal.value=ALL-UNNAMED");
            if (runMode == RunMode.ASSEMBLY) {
                args = StringArrayUtils.concat(args,
                                               "-Xlog:aot+class=debug",
                                               "-XX:AOTInitTestClass=MyAOTInitedClass");
            }

            return args;
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
            if (runMode == RunMode.TRAINING) {
                out.shouldContain("Y = 123");
            } else if (runMode == RunMode.ASSEMBLY) {
                out.shouldMatch("klasses.* app .*MyAOTInitedClass .* inited");
                out.shouldMatch("klasses.* app .*BytePair .* inited");
                out.shouldMatch("klasses.* app .*BytePairWrapper .* inited");
                out.shouldMatch("klasses.* app .*BytePairWrapperWrapper .* inited");
                out.shouldMatch("klasses.* app .*CharPair .* inited");
                out.shouldMatch("klasses.* app .*ShortPair .* inited");
                out.shouldMatch("klasses.* app .*ShortPairWrapper .* inited");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("Y = 45");
            }
        }
    }
}

// NOTE: this class is NOT aot-initialized.
class FlattenedFieldTestApp {
    static int X = 45;

    public static void main(String[] args) {
        X = 123;
        MyAOTInitedClass.test(args[0]);
    }
}

// This class is stored in the AOT cache in the initialized state.
class MyAOTInitedClass {
    // Note that when MyAOTInitedClass is initialized in the assembly run, FlattenedFieldTestApp.main()
    // is not executed, so the cached value of MyAOTInitedClass.Y will be 45;
    static int Y = FlattenedFieldTestApp.X;

    static CharPair cp = new CharPair('a', 'b');
    static ShortPairWrapper spw = new ShortPairWrapper(2, 3);
    static BytePairWrapperWrapper bpww = new BytePairWrapperWrapper(4, 5);

    static void test(String runMode) {
        System.out.println("Y = " + Y);
        if (runMode.equals("PRODUCTION") && Y != 45) {
            throw new RuntimeException("MyAOTInitedClass must be AOT-inited");
        }

        CharPair runtime_cp = new CharPair('a', 'b');
        ShortPairWrapper runtime_spw = new ShortPairWrapper(2, 3);
        BytePairWrapperWrapper runtime_bpww = new BytePairWrapperWrapper(4, 5);

        if (runtime_cp.compareTo(cp) != 0) {
            throw new RuntimeException("CharPair not restored correctly");
        }

        if (runtime_spw.compareTo(spw) != 0) {
            throw new RuntimeException("ShortPairWrapper not restored correctly");
        }

        if (runtime_bpww.compareTo(bpww) != 0) {
            throw new RuntimeException("BytePairWrapperWrapper not not restored correctly");
        }

        if (runMode.equals("PRODUCTION")) {
            ValueClassHelper.assertAOTInited_BytePair();
            ValueClassHelper.assertAOTInited_BytePairWrapper();
            ValueClassHelper.assertAOTInited_BytePairWrapperWrapper();
            ValueClassHelper.assertAOTInited_CharPair();
            ValueClassHelper.assertAOTInited_ShortPairWrapper();
        }
    }
}
