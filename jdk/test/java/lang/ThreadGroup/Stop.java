/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4176355
 * @summary Stopping a ThreadGroup that contains the current thread has
 *          unpredictable results.
 */

public class Stop implements Runnable {
    private static Thread first=null;
    private static Thread second=null;
    private static ThreadGroup group = new ThreadGroup("");

    Stop() {
        Thread thread = new Thread(group, this);
        if (first == null)
            first = thread;
        else
            second = thread;

        thread.start();
    }

    public void run() {
        while (true) {
            try {
                Thread.sleep(1000); // Give other thread a chance to start
                if (Thread.currentThread() == first)
                    group.stop();
            } catch(InterruptedException e){
            }
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i=0; i<2; i++)
            new Stop();
        Thread.sleep(3000);
        boolean failed = second.isAlive();
        first.stop(); second.stop();
        if (failed)
            throw new RuntimeException("Failure.");
    }
}
