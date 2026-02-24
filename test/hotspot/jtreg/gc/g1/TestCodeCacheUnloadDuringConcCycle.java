/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 */

package gc.g1;

/*
 * @test TestCodeCacheUnloadDuringConcCycle
 * @bug 8350621
 * @summary Test to make sure that code cache unloading does not hang when receiving
 * a request to unload code cache during concurrent mark.
 * We do that by triggering a code cache gc request (by triggering compilations)
 * during concurrent mark, and verify that after the concurrent cycle additional code
 * cache gc requests start more concurrent cycles.
 * @requires vm.gc.G1
 * @requires vm.flagless
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xmx20M -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. gc.g1.TestCodeCacheUnloadDuringConcCycle
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Field;

import java.net.URL;
import java.net.URLClassLoader;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestCodeCacheUnloadDuringConcCycle {
    public static final String AFTER_FIRST_CYCLE_MARKER = "Marker for this test";

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static OutputAnalyzer runTest(String concPhase) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-XX:+UseG1GC",
                                                                    "-Xmx20M",
                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                    "-Xbootclasspath/a:.",
                                                                    "-Xlog:gc=trace,codecache",
                                                                    "-XX:+WhiteBoxAPI",
                                                                    "-XX:ReservedCodeCacheSize=" + (Platform.is32bit() ? "4M" : "8M"),
                                                                    "-XX:StartAggressiveSweepingAt=50",
                                                                    "-XX:CompileCommand=compileonly,gc.g1.SomeClass::*",
                                                                    "-XX:CompileCommand=compileonly,gc.g1.Foo*::*",
                                                                    TestCodeCacheUnloadDuringConcCycleRunner.class.getName(),
                                                                    concPhase);
        return output;
    }

    private static void runAndCheckTest(String test) throws Exception {
        OutputAnalyzer output;

        output = runTest(test);
        output.shouldHaveExitValue(0);
        System.out.println(output.getStdout());

        String[] parts = output.getStdout().split(AFTER_FIRST_CYCLE_MARKER);

        // Either "Threshold" or "Aggressive" CodeCache GC are fine for the test.
        final String codecacheGCStart = "Pause Young (Concurrent Start) (CodeCache GC ";

        boolean success = parts.length == 2 && parts[1].indexOf(codecacheGCStart) != -1;
        Asserts.assertTrue(success, "Could not find a CodeCache GC Threshold GC after finishing the concurrent cycle");
    }

    private static void allTests() throws Exception {
        runAndCheckTest(WB.BEFORE_MARKING_COMPLETED);
        runAndCheckTest(WB.G1_BEFORE_REBUILD_COMPLETED);
        runAndCheckTest(WB.G1_BEFORE_CLEANUP_COMPLETED);
    }

    public static void main(String[] args) throws Exception {
        allTests();
    }
}

class TestCodeCacheUnloadDuringConcCycleRunner {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static void refClass(Class clazz) throws Exception {
        Field name = clazz.getDeclaredField("NAME");
        name.setAccessible(true);
        name.get(null);
    }

    private static class MyClassLoader extends URLClassLoader {
        public MyClassLoader(URL url) {
            super(new URL[]{url}, null);
        }
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            try {
                return super.loadClass(name, resolve);
            } catch (ClassNotFoundException e) {
                return Class.forName(name, resolve, TestCodeCacheUnloadDuringConcCycleRunner.class.getClassLoader());
            }
        }
    }

    private static void triggerCodeCacheGC() throws Exception {
        URL url = TestCodeCacheUnloadDuringConcCycleRunner.class.getProtectionDomain().getCodeSource().getLocation();

        try {
            int i = 0;
            do {
                ClassLoader cl = new MyClassLoader(url);
                refClass(cl.loadClass("gc.g1.SomeClass"));

                if (i % 20 == 0) {
                    System.out.println("Compiled " + i + " classes");
                }
                i++;
            } while (i < 200);
            System.out.println("Compilation done, compiled " + i + " classes");
        } catch (Throwable t) {
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Running to breakpoint: " + args[0]);
        try {
            WB.concurrentGCAcquireControl();
            WB.concurrentGCRunTo(args[0]);

            System.out.println("Try to trigger code cache GC");

            triggerCodeCacheGC();

            WB.concurrentGCRunToIdle();
        } finally {
            // Make sure that the marker we use to find the expected log message is printed
            // before we release whitebox control, i.e. before the expected garbage collection
            // can start.
            System.out.println(TestCodeCacheUnloadDuringConcCycle.AFTER_FIRST_CYCLE_MARKER);
            WB.concurrentGCReleaseControl();
        }
        Thread.sleep(1000);
        triggerCodeCacheGC();
    }
}

abstract class Foo {
    public abstract int foo();
}

class Foo1 extends Foo {
    private int a;
    public int foo() { return a; }
}

class Foo2 extends Foo {
    private int a;
    public int foo() { return a; }
}

class Foo3 extends Foo {
    private int a;
    public int foo() { return a; }
}

class Foo4 extends Foo {
    private int a;
    public int foo() { return a; }
}

class SomeClass {
    static final String NAME = "name";

    static {
        int res =0;
        Foo[] foos = new Foo[] { new Foo1(), new Foo2(), new Foo3(), new Foo4() };
        for (int i = 0; i < 100000; i++) {
            res = foos[i % foos.length].foo();
        }
    }
}
