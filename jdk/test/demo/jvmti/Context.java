/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 *
 *   Sample target application for jvmti demos
 *
 *     java Context [threadCount [iterationCount [sleepContention]]]
 *           Default: java Context 5 10 0
 *
 *      threadCount     Number of threads
 *      iterationCount  Total turns taken for all threads
 *      sleepContention Time for main thread to sleep while holding lock
 *                      (creates monitor contention on all other threads)
 *
 */

/* Used to sync up turns and keep track of who's turn it is */
final class TurnChecker {
    int thread_index;
    TurnChecker(int thread_index) {
        this.thread_index = thread_index;
    }
}

/* Creates a bunch of threads that sequentially take turns */
public final class Context extends Thread {
    /* Used to track threads */
    private static long startTime;
    private static TurnChecker turn = new TurnChecker(-1);
    private static int total_turns_taken;

    /* Used for each Context thread */
    private final int thread_count;
    private final int thread_index;
    private final int thread_turns;

    /* Main program */
    public static void main(String[] argv) throws InterruptedException {
        int default_thread_count = 5;
        int default_thread_turns = 10;
        int default_contention_sleep = 0;
        int expected_turns_taken;
        long sleepTime = 10L;

        /* Override defaults */
        if ( argv.length >= 1 ) {
            default_thread_count = Integer.parseInt(argv[0]);
        }
        if ( argv.length >= 2 ) {
            expected_turns_taken = Integer.parseInt(argv[1]);
            default_thread_turns = expected_turns_taken/default_thread_count;
        }
        expected_turns_taken = default_thread_count*default_thread_turns;
        if ( argv.length >= 3 ) {
            default_contention_sleep = Integer.parseInt(argv[2]);
        }

        System.out.println("Context started with "
                 + default_thread_count + " threads and "
                 + default_thread_turns + " turns per thread");

        /* Get all threads running (they will block until we set turn) */
        for (int i = 0; i < default_thread_count; i++) {
            new Context(default_thread_count, i, default_thread_turns).start();
        }

        /* Sleep to make sure thread_index 0 make it to the wait call */
        System.out.println("Context sleeping, so threads will start wait");
        Thread.yield();
        Thread.sleep(sleepTime);

        /* Save start time */
        startTime = System.currentTimeMillis();

        /* This triggers the starting of taking turns */
        synchronized (turn) {
            turn.thread_index = 0;
            turn.notifyAll();
        }
        System.out.println("Context sleeping, so threads can run");
        Thread.yield();
        Thread.sleep(sleepTime);

        /* Wait for threads to finish (after everyone has had their turns) */
        while ( true ) {
            boolean done;
            done = false;
            synchronized (turn) {
                if ( total_turns_taken == expected_turns_taken ) {
                    done = true;
                }
                /* Create some monitor contention by sleeping with lock */
                if ( default_contention_sleep > 0 ) {
                    System.out.println("Context sleeping, to create contention");
                    Thread.yield();
                    Thread.sleep((long)default_contention_sleep);
                }
            }
            if ( done )
                break;
            System.out.println("Context sleeping, so threads will complete");
            Thread.sleep(sleepTime);
        }

        long endTime   = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("Total time (milliseconds): " + totalTime);
        System.out.println("Milliseconds per thread: " +
                           ((double)totalTime / (default_thread_count)));

        System.out.println("Context completed");
        System.exit(0);
    }

    /* Thread object to run */
    Context(int thread_count, int thread_index, int thread_turns) {
        this.thread_count = thread_count;
        this.thread_index = thread_index;
        this.thread_turns = thread_turns;
    }

    /* Main for thread */
    public void run() {
        int next_thread_index = (thread_index + 1) % thread_count;
        int turns_taken       = 0;

        try {

            /* Loop until we make sure we get all our turns */
            for (int i = 0; i < thread_turns * thread_count; i++) {
                synchronized (turn) {
                    /* Keep waiting for our turn */
                    while (turn.thread_index != thread_index)
                        turn.wait();
                    /* MY TURN! Each thread gets thread_turns */
                    total_turns_taken++;
                    turns_taken++;
                    System.out.println("Turn #" + total_turns_taken
                                + " taken by thread " + thread_index
                                + ", " + turns_taken
                                + " turns taken by this thread");
                    /* Give next thread a turn */
                    turn.thread_index = next_thread_index;
                    turn.notifyAll();
                }
                /* If we've had all our turns, break out of this loop */
                if ( thread_turns == turns_taken ) {
                    break;
                }
            }
        } catch (InterruptedException intEx) { /* skip */ }

        /* Make sure we got all our turns */
        if ( thread_turns != turns_taken ) {
            System.out.println("ERROR: thread got " + turns_taken
                                        + " turns, expected " + thread_turns);
            System.exit(1);
        }
    }
}
