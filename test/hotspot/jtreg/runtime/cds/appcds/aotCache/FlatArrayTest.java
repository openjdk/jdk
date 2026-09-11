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
 * @library /test/jdk/lib/testlibrary /test/lib
 * @modules java.base/jdk.internal.value
 * @build FlatArrayTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *             FlatArrayTestApp MyAOTInitedClass CharPair Wrapper
 * @run driver FlatArrayTest AOT --two-step-training
 */

import java.util.Arrays;
import jdk.internal.value.ValueClass;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.StringArrayUtils;

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
                out.shouldMatch("klasses.* app .*CharPair .* inited");
                out.shouldMatch("klasses.* app .*Wrapper .* inited");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("Y = 45");
            }
        }
    }
}

class FlatArrayTestApp {
    static int X = 45;
    public static void main(String[] args) {
        X = 123;
        MyAOTInitedClass.test(args[0]);
    }
}

value class CharPair implements Comparable<CharPair> {
    char c0, c1;

    public String toString() {
        return "(" + c0 + ", " + c1 + ")";
    }

    public int compareTo(CharPair o) {
        return (c0 - o.c0) - (c1 - o.c1);
    }

    public CharPair(char c0, char c1) {
        this.c0 = c0;
        this.c1 = c1;
    }
}

value class Wrapper implements Comparable<Wrapper> {
    Integer i;

    public String toString() {
        return i.toString();
    }

    public int compareTo(Wrapper o) {
        return i - o.i;
    }

    Wrapper(int i) {
        this.i = new Integer(i);
    }
}

// This class is stored in the AOT cache in the initialized state.
class MyAOTInitedClass {
    // Note that when MyAOTInitedClass is initialized in the assembly run, FlatArrayTestApp.main()
    // is not executed, so the cached value of MyAOTInitedClass.Y will be 45;
    static int Y = FlatArrayTestApp.X;

    static Integer[] intArray;
    static CharPair[] charPairArray;
    static Wrapper[] wrapperArray;

    static CharPair charPair;
    static Wrapper wrapper;

    static {
        intArray = new Integer[3];
        intArray[0] = null;
        System.out.println("TEST: " + (intArray[0] == null));
        intArray[0] = new Integer(0);
        intArray[1] = new Integer(1);
        intArray[2] = new Integer(2);

        charPairArray = new CharPair[3];
        charPairArray[0] = new CharPair('a', 'b');
        charPairArray[1] = new CharPair('c', 'd');
        charPairArray[2] = new CharPair('e', 'f');

        wrapperArray = new Wrapper[3];
        wrapperArray[0] = new Wrapper(0);
        wrapperArray[1] = new Wrapper(1);
        wrapperArray[2] = new Wrapper(2);

        charPair = new CharPair('x', 'y');
        wrapper = new Wrapper(5);
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

        if (!ValueClass.isFlatArray(wrapperArray)) {
            throw new RuntimeException("Wrapper array should be flat");
        }

        // Ensure archived arrays are restored properly
        Integer[] runtimeIntArray = new Integer[3];
        runtimeIntArray[0] = new Integer(0);
        runtimeIntArray[1] = new Integer(1);
        runtimeIntArray[2] = new Integer(2);

        CharPair[] runtimeCharPairArray = new CharPair[3];
        runtimeCharPairArray[0] = new CharPair('a', 'b');
        runtimeCharPairArray[1] = new CharPair('c', 'd');
        runtimeCharPairArray[2] = new CharPair('e', 'f');

        Wrapper[] runtimeWrapperArray = new Wrapper[3];
        runtimeWrapperArray[0] = new Wrapper(0);
        runtimeWrapperArray[1] = new Wrapper(1);
        runtimeWrapperArray[2] = new Wrapper(2);

        if (Arrays.compare(intArray, runtimeIntArray) != 0) {
            throw new RuntimeException("Integer array not restored correctly");
        }

        if (Arrays.compare(charPairArray, runtimeCharPairArray) != 0) {
            throw new RuntimeException("CharPair array not restored correctly");
        }

        if (Arrays.compare(wrapperArray, runtimeWrapperArray) != 0) {
            throw new RuntimeException("Wrapper array not restored correctly");
        }
    }
}
