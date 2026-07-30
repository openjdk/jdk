/*
 * Copyright (c) 2013 SAP SE. All rights reserved.
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

/**
 * @test
 * @summary Volatile store in C1 followed by volatile load in interpreter.
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -Xbatch
 *      -XX:CompileCommand=dontinline,*DekkerTest_Interpreter_C1::interpreter_get_field_*
 *      -XX:CompileCommand=exclude,*DekkerTest_Interpreter_C1::interpreter_get_field_*
 *      compiler.membars.DekkerTest_Interpreter_C1
 */

package compiler.membars;

public class DekkerTest_Interpreter_C1 {
    /*
      Read After Write Test (basically a simple Dekker test with volatile variables)
      Derived from the DekkerTest:
        test/hotspot/jtreg/compiler/membars/DekkerTest.java
     */

    static final int ITERATIONS = 1000000;

    static class TestData {
        public volatile int a;
        public volatile int b;
    }

    static class ResultData {
        public int a;
        public int b;
    }

    TestData[]   testDataArray;
    ResultData[] results;

    volatile boolean start;

    public DekkerTest_Interpreter_C1() {
        testDataArray = new TestData[ITERATIONS];
        results = new ResultData[ITERATIONS];
        for (int i = 0; i < ITERATIONS; ++i) {
            testDataArray[i] = new TestData();
            results[i] = new ResultData();
        }
        start = false;
    }

    public void reset() {
        for (int i = 0; i < ITERATIONS; ++i) {
            testDataArray[i].a = 0;
            testDataArray[i].b = 0;
            results[i].a = 0;
            results[i].b = 0;
        }
        start = false;
    }

    int interpreter_get_field_a(TestData t) {
        return t.a;
    }

    int interpreter_get_field_b(TestData t) {
        return t.b;
    }

    int actor1(TestData t) {
        t.a = 1;
        return interpreter_get_field_b(t);
    }

    int actor2(TestData t) {
        t.b = 1;
        return interpreter_get_field_a(t);
    }

    class Runner1 extends Thread {
        public void run() {
            do {} while (!start);
            for (int i = 0; i < ITERATIONS; ++i) {
                results[i].a = actor1(testDataArray[i]);
            }
        }
    }

    class Runner2 extends Thread {
        public void run() {
            do {} while (!start);
            for (int i = 0; i < ITERATIONS; ++i) {
                results[i].b = actor2(testDataArray[i]);
            }
        }
    }

    void testRunner() {
        Thread thread1 = new Runner1();
        Thread thread2 = new Runner2();
        thread1.start();
        thread2.start();
        do {} while (!thread1.isAlive());
        do {} while (!thread2.isAlive());
        start = true;
        Thread.yield();
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            System.out.println("interrupted!");
            System.exit(1);
        }
    }

    boolean printResult() {
        int[] count = new int[4];
        for (int i = 0; i < ITERATIONS; ++i) {
            int event_kind = (results[i].a << 1) + results[i].b;
            ++count[event_kind];
        }
        if (count[0] == 0 && count[3] == 0) {
            System.out.println("[not interesting]");
            return false; // not interesting
        }
        String error = (count[0] == 0) ? " ok" : " disallowed!";
        System.out.println("[0,0] " + count[0] + error);
        System.out.println("[0,1] " + count[1]);
        System.out.println("[1,0] " + count[2]);
        System.out.println("[1,1] " + count[3]);
        return (count[0] != 0);
    }

    public static void main(String args[]) {
        DekkerTest_Interpreter_C1 test = new DekkerTest_Interpreter_C1();
        final int runs = 100;
        int failed = 0;
        for (int c = 0; c < runs; ++c) {
            test.testRunner();
            if (test.printResult()) {
                failed++;
            }
            test.reset();
        }
        if (failed > 0) {
            throw new InternalError("FAILED. Got " + failed + " failed ITERATIONS");
        }
        System.out.println("PASSED.");
    }

}
