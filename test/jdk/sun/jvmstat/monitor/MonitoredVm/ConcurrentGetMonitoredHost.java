/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.VmIdentifier;

/*
 * @test
 * @bug 8320687
 * @summary verify that sun.jvmstat.monitor.MonitoredHost.getMonitoredHost() doesn't
 *          unexpectedly throw an exception when invoked by concurrent threads
 *
 * @run main/othervm ConcurrentGetMonitoredHost
 */
public class ConcurrentGetMonitoredHost {

    /*
     * Launches multiple concurrent threads and invokes MonitoredHost.getMonitoredHost()
     * in each of these threads and expects the call to return successfully without any
     * exceptions.
     */
    public static void main(final String[] args) throws Exception {
        final String pidStr = "12345";
        final VmIdentifier vmid = new VmIdentifier(pidStr);
        final int numTasks = 100;
        final List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < numTasks; i++) {
            tasks.add(new Task(vmid));
        }
        System.out.println("Submitting " + numTasks + " concurrent tasks to" +
                " get MonitoredHost for " + vmid);
        try (ExecutorService executor = Executors.newCachedThreadPool()) {
            // wait for all tasks to complete
            final List<Future<MonitoredHost>> results = executor.invokeAll(tasks);
            // verify each one successfully completed and each of
            // the returned MonitoredHost is not null
            for (final Future<MonitoredHost> result : results) {
                final MonitoredHost mh = result.get();
                if (mh == null) {
                    throw new AssertionError("MonitoredHost.getMonitoredHost() returned" +
                            " null for vmid " + vmid);
                }
            }
        }
        System.out.println("All " + numTasks + " completed successfully");
    }

    // a task which just calls MonitoredHost.getMonitoredHost(VmIdentifier) and
    // returns the resultant MonitoredHost
    private static final class Task implements Callable<MonitoredHost> {
        private final VmIdentifier vmid;

        private Task(final VmIdentifier vmid) {
            this.vmid = Objects.requireNonNull(vmid);
        }

        @Override
        public MonitoredHost call() throws Exception {
            return MonitoredHost.getMonitoredHost(this.vmid);
        }
    }
}
