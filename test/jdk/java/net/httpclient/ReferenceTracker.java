/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.common.OperationTrackers;
import jdk.internal.net.http.common.OperationTrackers.Tracker;

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A small helper class to help track clients which still
 * have pending operations at the end of a test.
 */
public class ReferenceTracker {
    private final ConcurrentLinkedQueue<Tracker> TRACKERS
            = new ConcurrentLinkedQueue<Tracker>();

    public static final ReferenceTracker INSTANCE
            = new ReferenceTracker();

    public HttpClient track(HttpClient client) {
        Tracker tracker = OperationTrackers.getTracker(client);
        assert tracker != null;
        TRACKERS.add(tracker);
        return client;
    }

    public long getTrackedClientCount() {
        return TRACKERS.size();
    }

    public StringBuilder diagnose(StringBuilder warnings) {
        return diagnose(warnings, (t) -> t.getOutstandingHttpOperations() > 0);
    }

    public StringBuilder diagnose(StringBuilder warnings, Predicate<Tracker> hasOutstanding) {
        for (Tracker tracker : TRACKERS) {
            checkOutstandingOperations(warnings, tracker, hasOutstanding);
        }
        return warnings;
    }

    public boolean hasOutstandingOperations() {
        return TRACKERS.stream().anyMatch(t -> t.getOutstandingOperations() > 0);
    }

    public long getOutstandingOperationsCount() {
        return TRACKERS.stream()
                .map(Tracker::getOutstandingOperations)
                .filter(n -> n > 0)
                .collect(Collectors.summingLong(n -> n));
    }

    public long getOutstandingClientCount() {
        return TRACKERS.stream()
                .map(Tracker::getOutstandingOperations)
                .filter(n -> n > 0)
                .count();
    }

    public AssertionError check(long graceDelayMs) {
        return check(graceDelayMs,
                (t) -> t.getOutstandingHttpOperations() > 0,
                "outstanding operations", true);
    }

    private void printThreads(String why, PrintStream out) {
        out.println(why);
        Arrays.stream(ManagementFactory.getThreadMXBean()
                        .dumpAllThreads(true, true))
                .forEach(out::println);
    }

    public AssertionError check(long graceDelayMs,
                                Predicate<Tracker> hasOutstanding,
                                String description,
                                boolean printThreads) {
        AssertionError fail = null;
        graceDelayMs = Math.max(graceDelayMs, 100);
        long delay = Math.min(graceDelayMs, 500);
        var count = delay > 0 ? graceDelayMs / delay : 1;
        for (int i = 0; i < count; i++) {
            if (TRACKERS.stream().anyMatch(hasOutstanding)) {
                System.gc();
                try {
                    System.out.println("Waiting for HTTP operations to terminate...");
                    Thread.sleep(Math.min(graceDelayMs, Math.max(delay, 1)));
                } catch (InterruptedException x) {
                    // OK
                }
            } else break;
        }
        if (TRACKERS.stream().anyMatch(hasOutstanding)) {
            StringBuilder warnings = diagnose(new StringBuilder(), hasOutstanding);
            addSummary(warnings);
            if (TRACKERS.stream().anyMatch(hasOutstanding)) {
                fail = new AssertionError(warnings.toString());
            }
        } else {
            System.out.println("PASSED: No " + description + " found in "
                    + getTrackedClientCount() + " clients");
        }
        if (fail != null) {
            Predicate<Tracker> isAlive = Tracker::isSelectorAlive;
            if (printThreads && TRACKERS.stream().anyMatch(isAlive)) {
                printThreads("Some selector manager threads are still alive: ", System.out);
                printThreads("Some selector manager threads are still alive: ", System.err);
            }
        }
        return fail;
    }

    private void addSummary(StringBuilder warning) {
        long activeClients = getOutstandingClientCount();
        long operations = getOutstandingOperationsCount();
        long tracked = getTrackedClientCount();
        if (warning.length() > 0) warning.append("\n");
        int pos = warning.length();
        warning.append("Found ")
                .append(activeClients)
                .append(" client still active, with ")
                .append(operations)
                .append(" operations still pending out of ")
                .append(tracked)
                .append(" tracked clients.");
        System.out.println(warning.substring(pos));
        System.err.println(warning.substring(pos));
    }

    private static void checkOutstandingOperations(StringBuilder warning,
                                                   Tracker tracker,
                                                   Predicate<Tracker> hasOutsanding) {
        if (hasOutsanding.test(tracker)) {
            if (warning.length() > 0) warning.append("\n");
            int pos = warning.length();
            warning.append("WARNING: tracker for " + tracker.getName() + " has outstanding operations:");
            warning.append("\n\tPending HTTP Requests: " + tracker.getOutstandingHttpRequests());
            warning.append("\n\tPending HTTP/1.1 operations: " + tracker.getOutstandingHttpOperations());
            warning.append("\n\tPending HTTP/2 streams: " + tracker.getOutstandingHttp2Streams());
            warning.append("\n\tPending WebSocket operations: " + tracker.getOutstandingWebSocketOperations());
            warning.append("\n\tPending TCP connections: " + tracker.getOutstandingTcpConnections());
            warning.append("\n\tTotal pending operations: " + tracker.getOutstandingOperations());
            warning.append("\n\tFacade referenced: " + tracker.isFacadeReferenced());
            warning.append("\n\tSelector alive: " + tracker.isSelectorAlive());
            System.out.println(warning.substring(pos));
            System.err.println(warning.substring(pos));
        }
    }

    private boolean isSelectorManager(Thread t) {
        String name = t.getName();
        if (name == null) return false;
        return name.contains("SelectorManager");
    }

    // This is a slightly more permissive check than the default checks,
    // it only verifies that all CFs returned by send/sendAsync have been
    // completed, and that all opened channels have been closed, and that
    // the selector manager thread has exited.
    // It doesn't check that all refcounts have reached 0.
    // This is typically useful to only check that resources have been released.
    public AssertionError checkShutdown(long graceDelayMs) {
        Predicate<Tracker> isAlive = Tracker::isSelectorAlive;
        Predicate<Tracker> hasPendingRequests = (t) -> t.getOutstandingHttpRequests() > 0;
        Predicate<Tracker> hasPendingConnections = (t) -> t.getOutstandingTcpConnections() > 0;
        AssertionError failed = check(graceDelayMs,
                isAlive.or(hasPendingRequests).or(hasPendingConnections),
                "outstanding unclosed resources", true);
        return failed;
    }
}
