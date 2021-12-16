/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8276422
 * @summary add command-line option to disable finalization
 * @run main/othervm                         FinalizationOption yes
 * @run main/othervm --finalization=enabled  FinalizationOption yes
 * @run main/othervm --finalization=disabled FinalizationOption no
 */
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

    public static void main(String[] args) {
        boolean finalizationEnabled = switch (args[0]) {
            case "yes" -> true;
            case "no"  -> false;
            default -> {
                throw new AssertionError("usage: FinalizationOption yes|no");
            }
        };

        boolean threadPass = checkFinalizerThread(finalizationEnabled);
        boolean calledPass = checkFinalizerCalled(finalizationEnabled);

        if (!threadPass || !calledPass)
            throw new AssertionError("Test failed.");
    }
}
