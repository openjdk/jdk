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
 * @test Test AOT-cached flat arrays
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @enablePreview
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes/
 * @modules java.base/jdk.internal.value
 * @modules java.base/jdk.internal.vm.annotation
 * @build FlatArrayTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             FlatArrayTestApp
 *             MyAOTInitedClass
 *             valueclasses.BytePair
 *             valueclasses.BytePairWrapper
 *             valueclasses.BytePairWrapperWrapper
 *             valueclasses.CharPair
 *             valueclasses.IntegerWrapper
 *             valueclasses.ShortPair
 *             valueclasses.ShortPairWrapper
 *             valueclasses.ValueClassHelper
 * @run driver FlatArrayTest AOT --two-step-training
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
import valueclasses.IntegerWrapper;
import valueclasses.ValueClassHelper;

public class FlatArrayTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = FlatArrayTestApp.class.getName();

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
                out.shouldMatch("klasses.* app .*IntegerWrapper .* inited");
                out.shouldMatch("klasses.* app .*ShortPair .* inited");
                out.shouldMatch("klasses.* app .*ShortPairWrapper .* inited");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("Y = 45");
            }
        }
    }
}

// NOTE: this class is NOT aot-initialized.
class FlatArrayTestApp {
    static int X = 45;

    public static void main(String[] args) {
        X = 123;
        MyAOTInitedClass.test(args[0]);
    }
}

// This class is stored in the AOT cache in the initialized state.
class MyAOTInitedClass {
    // Note that when MyAOTInitedClass is initialized in the assembly run, FlatArrayTestApp.main()
    // is not executed, so the cached value of MyAOTInitedClass.Y will be 45;
    static int Y = FlatArrayTestApp.X;

    static Integer[] intArray;
    static CharPair[] charPairArray;
    static IntegerWrapper[] integerWrapperArray;
    static ShortPairWrapper[] spwArray;
    static BytePairWrapperWrapper[] bpwwArray;

    // A non-flattened instance of CharPair.
    static CharPair charPair;

    // We don't have non-flattened instances of IntegerWrapper, but
    // the IntegerWrapper class should still be AOT-initialized, as
    // we can read a reference object of type IntegerWrapper from
    // integerWrapperArray[0]
    //
    // The same is also true for BytePair, BytePairWrapper, BytePairWrapperWrapper,
    // ShortPair, and ShortPairWrapper, for similar reasons.

    static {
        intArray = new Integer[3];
        intArray[0] = new Integer(0);
        intArray[1] = new Integer(1);
        intArray[2] = new Integer(2);

        charPairArray = new CharPair[3];
        charPairArray[0] = new CharPair('a', 'b');
        charPairArray[1] = new CharPair('c', 'd');
        charPairArray[2] = new CharPair('e', 'f');

        integerWrapperArray = new IntegerWrapper[3];
        integerWrapperArray[0] = new IntegerWrapper(0);
        integerWrapperArray[1] = new IntegerWrapper(1);
        integerWrapperArray[2] = new IntegerWrapper(2);

        spwArray = new ShortPairWrapper[3];
        spwArray[0] = new ShortPairWrapper(0, 1);
        spwArray[1] = new ShortPairWrapper(2, 3);
        spwArray[2] = new ShortPairWrapper(4, 5);

        bpwwArray = new BytePairWrapperWrapper[3];
        bpwwArray[0] = new BytePairWrapperWrapper(0, 1);
        bpwwArray[1] = new BytePairWrapperWrapper(2, 3);
        bpwwArray[2] = new BytePairWrapperWrapper(4, 5);

        charPair = new CharPair('x', 'y');
    }

    static void test(String runMode) {
        System.out.println("Y = " + Y);
        if (runMode.equals("PRODUCTION") && Y != 45) {
            throw new RuntimeException("MyAOTInitedClass must be AOT-inited");
        }

        if (!ValueClass.isFlatArray(intArray)) {
            throw new RuntimeException("Integer array should be flat");
        }

        if (!ValueClass.isFlatArray(charPairArray)) {
            throw new RuntimeException("CharPair array should be flat");
        }

        if (!ValueClass.isFlatArray(integerWrapperArray)) {
            throw new RuntimeException("IntegerWrapper array should be flat");
        }

        if (!ValueClass.isFlatArray(spwArray)) {
            throw new RuntimeException("ShortPairWrapper array should be flat");
        }

        if (!ValueClass.isFlatArray(bpwwArray)) {
            throw new RuntimeException("BytePairWrapperWrapper array should be flat");
        }

        // Ensure archived arrays are restored properly
        Integer[] runtime_intArray = new Integer[3];
        runtime_intArray[0] = new Integer(0);
        runtime_intArray[1] = new Integer(1);
        runtime_intArray[2] = new Integer(2);

        CharPair[] runtime_charPairArray = new CharPair[3];
        runtime_charPairArray[0] = new CharPair('a', 'b');
        runtime_charPairArray[1] = new CharPair('c', 'd');
        runtime_charPairArray[2] = new CharPair('e', 'f');

        IntegerWrapper[] runtime_integerWrapperArray = new IntegerWrapper[3];
        runtime_integerWrapperArray[0] = new IntegerWrapper(0);
        runtime_integerWrapperArray[1] = new IntegerWrapper(1);
        runtime_integerWrapperArray[2] = new IntegerWrapper(2);

        ShortPairWrapper[] runtime_spwArray = new ShortPairWrapper[3];
        runtime_spwArray[0] = new ShortPairWrapper(0, 1);
        runtime_spwArray[1] = new ShortPairWrapper(2, 3);
        runtime_spwArray[2] = new ShortPairWrapper(4, 5);

        BytePairWrapperWrapper[] runtime_bpwwArray = new BytePairWrapperWrapper[3];
        runtime_bpwwArray[0] = new BytePairWrapperWrapper(0, 1);
        runtime_bpwwArray[1] = new BytePairWrapperWrapper(2, 3);
        runtime_bpwwArray[2] = new BytePairWrapperWrapper(4, 5);

        if (Arrays.compare(intArray, runtime_intArray) != 0) {
            throw new RuntimeException("Integer array not restored correctly");
        }

        if (Arrays.compare(charPairArray, runtime_charPairArray) != 0) {
            throw new RuntimeException("CharPair array not restored correctly");
        }

        if (Arrays.compare(integerWrapperArray, runtime_integerWrapperArray) != 0) {
            throw new RuntimeException("IntegerWrapper array not restored correctly");
        }

        if (Arrays.compare(spwArray, runtime_spwArray) != 0) {
            throw new RuntimeException("ShortPairWrapper array not restored correctly");
        }

        if (Arrays.compare(bpwwArray, runtime_bpwwArray) != 0) {
            throw new RuntimeException("BytePairWrapperWrapper array not restored correctly");
        }

        if (runMode.equals("PRODUCTION")) {
            ValueClassHelper.assertAOTInited_BytePair();
            ValueClassHelper.assertAOTInited_BytePairWrapper();
            ValueClassHelper.assertAOTInited_BytePairWrapperWrapper();
            ValueClassHelper.assertAOTInited_CharPair();
            ValueClassHelper.assertAOTInited_IntegerWrapper();
            ValueClassHelper.assertAOTInited_ShortPair();
            ValueClassHelper.assertAOTInited_ShortPairWrapper();
        }
    }
}
