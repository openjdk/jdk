/*
 * Copyright 2023, Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug     8305425
 * @summary Check Thread.isAlive
 * @run main/othervm IsAlive
 */

public class IsAlive {

    static boolean spinnerDone;

    private static void spin() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(100);
            }
        } catch (InterruptedException ie) {
            // Do nothing, just exit
        }
        spinnerDone = true;
    }

    static volatile boolean checkerReady;

    private static void check(Thread t) {
        while (!t.isAlive()) {
            // Burn hard, without any sleeps.
            // Check that we discover the thread is alive eventually.
        }

        checkerReady = true;

        while (t.isAlive()) {
            // Burn hard, without any sleeps.
            // Check that we discover the thread is not alive eventually.
        }

        if (!spinnerDone) {
            throw new RuntimeException("Last write of terminated thread was not seen!");
        }
    }

    private static void assertAlive(Thread t) {
        if (!t.isAlive()) {
            throw new IllegalStateException("Thread " + t + " is not alive, but it should be");
        }
    }

    private static void assertNotAlive(Thread t) {
        if (t.isAlive()) {
            throw new IllegalStateException("Thread " + t + " is alive, but it should not be");
        }
    }

    public static void main(String args[]) throws Exception {
        Thread spinner = new Thread(IsAlive::spin);
        spinner.setName("Spinner");
        spinner.setDaemon(true);

        Thread checker = new Thread(() -> check(spinner));
        checker.setName("Checker");
        checker.setDaemon(true);

        assertNotAlive(spinner);
        assertNotAlive(checker);

        System.out.println("Starting spinner");
        spinner.start();
        assertAlive(spinner);

        System.out.println("Starting checker");
        checker.start();
        assertAlive(checker);

        System.out.println("Waiting for checker to catch up");
        while (!checkerReady) {
            Thread.sleep(100);
        }

        System.out.println("Interrupting and joining spinner");
        spinner.interrupt();
        spinner.join();
        assertNotAlive(spinner);

        System.out.println("Joining checker");
        checker.join();
        assertNotAlive(checker);

        System.out.println("Complete");
    }
}
