/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8276422 8387729
 * @summary add command-line option to disable finalization
 * @library /test/lib
 * @run main FinalizationOption enabled  default
 * @run main FinalizationOption enabled  equals
 * @run main FinalizationOption enabled  whitespace
 * @run main FinalizationOption disabled equals
 * @run main FinalizationOption disabled whitespace
 */

import jdk.test.lib.process.ProcessTools;

public class FinalizationOption {
    static volatile boolean finalizerWasCalled = false;

    @SuppressWarnings("deprecation")
    protected void finalize() {
        finalizerWasCalled = true;
    }

    static void create() {
        new FinalizationOption();
    }

    /**
     * Checks whether the finalizer thread is or is not running. The finalizer thread
     * is a thread in the root thread group whose named is "Finalizer".
     * @param expected boolean indicating whether a finalizer thread should exist
     * @return boolean indicating whether the expectation was met
     */
    static boolean checkFinalizerThread(boolean expected) {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        for (ThreadGroup parent = root;
             parent != null;
             root = parent, parent = root.getParent())
            ;

        int nt = 100;
        Thread[] threads;
        while (true) {
            threads = new Thread[nt];
            nt = root.enumerate(threads);
            if (nt < threads.length)
                break;
            threads = new Thread[nt + 100];
        }

        Thread ft = null;
        for (int i = 0; i < nt; i++) {
            if ("Finalizer".equals(threads[i].getName())) {
                ft = threads[i];
                break;
            }
        }

        String msg = (ft == null) ? "(none)" : ft.toString();
        boolean passed = (ft != null) == expected;
        System.out.printf("Finalizer thread.    Expected: %s   Actual: %s   %s%n",
            expected, msg, passed ? "Passed." : "FAILED!");
        return passed;
    }

    /**
     * Checks whether there was a call to the finalize() method.
     * @param expected boolean whether finalize() should be called
     * @return boolean indicating whether the expecation was met
     */
    static boolean checkFinalizerCalled(boolean expected) {
        create();
        for (int i = 0; i < 100; i++) {
            System.gc();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (finalizerWasCalled) {
                break;
            }
        }
        boolean passed = (expected == finalizerWasCalled);
        System.out.printf("Call to finalize().  Expected: %s   Actual: %s   %s%n",
            expected, finalizerWasCalled,
            passed ? "Passed." : "FAILED!");
        return passed;
    }

    /*
     * Each @run invocation enters main() twice:
     *
     * 1. jtreg invokes main() with two arguments. This calls launch()
     *    to start a test process.
     *
     * 2. The launched test process invokes main() with one argument and
     *    performs the actual test.
     */
    public static void main(String[] args) throws Exception {
        switch (args.length) {
            case 2:
                launch(args[0], args[1]);
                return;
            case 1:
                test(args[0]);
                return;
            default:
                throw new AssertionError(
                    "expected one or two arguments");
        }
    }

    /**
     * Launch a test process with the given command-line option form.
     */
    static void launch(String option, String form) throws Exception {
        String[] javaArgs = switch (form) {
            case "default"    -> new String[] {"FinalizationOption", option};
            case "equals"     -> new String[] {"--finalization=" + option,
                                               "FinalizationOption", option};
            case "whitespace" -> new String[] {"--finalization", option,
                                               "FinalizationOption", option};
            default -> throw new AssertionError("Unexpected option form: " + form);
        };

        ProcessTools.executeTestJava(javaArgs).shouldHaveExitValue(0);
    }

    /**
     * Perform the actual finalization test.
     */
    static void test(String option) throws Exception {
        boolean finalizationEnabled = switch (option) {
            case "enabled"  -> true;
            case "disabled" -> false;
            default -> throw new AssertionError(
                "usage: FinalizationOption enabled|disabled");
        };

        boolean threadPass = checkFinalizerThread(finalizationEnabled);
        boolean calledPass = checkFinalizerCalled(finalizationEnabled);

        if (!threadPass || !calledPass)
            throw new AssertionError("Test failed.");
    }
}
