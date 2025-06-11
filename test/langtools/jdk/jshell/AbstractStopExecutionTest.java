/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.io.StringWriter;

import jdk.jshell.JShell;

import static org.testng.Assert.fail;

public abstract class AbstractStopExecutionTest extends KullaTesting {

    private final Object lock = new Object();
    private boolean isStopped;

    protected void scheduleStop(String src) throws InterruptedException {
        JShell state = getState();
        isStopped = false;
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        Thread t = new Thread(() -> {
            int i = 1;
            int n = 30;
            synchronized (lock) {
                do {
                    state.stop();
                    if (!isStopped) {
                        out.println("Not stopped. Try again: " + i);
                        try {
                            lock.wait(1000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                } while (i++ < n && !isStopped);
                if (!isStopped) {
                    System.err.println(writer.toString());
                    fail("Evaluation was not stopped: '" + src + "'");
                }
            }
        });
        t.start();
        assertEval(src);
        synchronized (lock) {
            out.println("Evaluation was stopped successfully: '" + src + "'");
            isStopped = true;
            lock.notify();
        }
        // wait until the background thread finishes to prevent from calling 'stop' on closed state.
        t.join();
    }
}
