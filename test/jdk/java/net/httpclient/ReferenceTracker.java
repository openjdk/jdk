/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
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

    public StringBuilder diagnose(Tracker tracker) {
        return diagnose(tracker, new StringBuilder(), (t) -> t.getOutstandingHttpOperations() > 0);
    }

    public StringBuilder diagnose(HttpClient client) {
        return diagnose(getTracker(client));
    }

    public StringBuilder diagnose(Tracker tracker, StringBuilder warnings, Predicate<Tracker> hasOutstanding) {
        checkOutstandingOperations(warnings, tracker, hasOutstanding);
        return warnings;
    }

    public StringBuilder diagnose(StringBuilder warnings, Predicate<Tracker> hasOutstanding) {
        for (Tracker tracker : TRACKERS) {
            diagnose(tracker, warnings, hasOutstanding);
        }
        return warnings;
    }

    public boolean hasOutstandingOperations() {
        return TRACKERS.stream().anyMatch(t -> t.getOutstandingOperations() > 0);
    }

    public boolean hasOutstandingSubscribers() {
        return TRACKERS.stream().anyMatch(t -> t.getOutstandingSubscribers() > 0);
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

    public AssertionError check(Tracker tracker, long graceDelayMs) {
        Predicate<Tracker> hasOperations = (t) -> t.getOutstandingOperations() > 0;
        Predicate<Tracker> hasSubscribers = (t) -> t.getOutstandingSubscribers() > 0;
        return check(tracker, graceDelayMs,
                hasOperations.or(hasSubscribers)
                        .or(Tracker::isFacadeReferenced)
                        .or(Tracker::isSelectorAlive),
                "outstanding operations or unreleased resources", true);
    }

    public AssertionError checkFinished(Tracker tracker, long graceDelayMs) {
        Predicate<Tracker> hasOperations = (t) -> t.getOutstandingOperations() > 0;
        Predicate<Tracker> hasSubscribers = (t) -> t.getOutstandingSubscribers() > 0;
        return check(tracker, graceDelayMs,
                hasOperations.or(hasSubscribers),
                "outstanding operations or unreleased resources", false);
    }

    public AssertionError check(long graceDelayMs) {
        Predicate<Tracker> hasOperations = (t) -> t.getOutstandingOperations() > 0;
        Predicate<Tracker> hasSubscribers = (t) -> t.getOutstandingSubscribers() > 0;
        return check(graceDelayMs,
                hasOperations.or(hasSubscribers)
                .or(Tracker::isFacadeReferenced)
                .or(Tracker::isSelectorAlive),
        "outstanding operations or unreleased resources", true);
    }

    // This method is copied from ThreadInfo::toString, but removes the
    // limit on the stack trace depth (8 frames max) that ThreadInfo::toString
    // forcefully implement. We want to print all frames for better diagnosis.
    private static String toString(ThreadInfo info) {
        StringBuilder sb = new StringBuilder("\"" + info.getThreadName() + "\"" +
                (info.isDaemon() ? " daemon" : "") +
                " prio=" + info.getPriority() +
                " Id=" + info.getThreadId() + " " +
                info.getThreadState());
        if (info.getLockName() != null) {
            sb.append(" on " + info.getLockName());
        }
        if (info.getLockOwnerName() != null) {
            sb.append(" owned by \"" + info.getLockOwnerName() +
                    "\" Id=" + info.getLockOwnerId());
        }
        if (info.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (info.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        int i = 0;
        var stackTrace = info.getStackTrace();
        for (; i < stackTrace.length ; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && info.getLockInfo() != null) {
                Thread.State ts = info.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + info.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + info.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + info.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : info.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
        }
        if (i < stackTrace.length) {
            sb.append("\t...");
            sb.append('\n');
        }

        LockInfo[] locks = info.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = " + locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- " + li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    private void printThreads(String why, PrintStream out) {
        out.println(why);
        Arrays.stream(ManagementFactory.getThreadMXBean()
                        .dumpAllThreads(true, true))
                .map(ReferenceTracker::toString)
                .forEach(out::println);
    }

    public Tracker getTracker(HttpClient client) {
        return OperationTrackers.getTracker(Objects.requireNonNull(client));
    }

    public AssertionError check(Tracker tracker,
                                long graceDelayMs,
                                Predicate<Tracker> hasOutstanding,
                                String description,
                                boolean printThreads) {
        AssertionError fail = null;
        graceDelayMs = Math.max(graceDelayMs, 100);
        long delay = Math.min(graceDelayMs, 10);
        var count = delay > 0 ? graceDelayMs / delay : 1;
        long waitStart = System.nanoTime();
        long waited = 0;
        long toWait = Math.min(graceDelayMs, Math.max(delay, 1));
        int i = 0;
        for (i = 0; i < count; i++) {
            if (hasOutstanding.test(tracker)) {
                System.gc();
                try {
                    if (i == 0) {
                        System.out.println("Waiting for HTTP operations to terminate...");
                        System.out.println("\tgracedelay: " + graceDelayMs
                                + " ms, iterations: " + count + ", wait/iteration: " + toWait + "ms");
                    }
                    waited += toWait;
                    Thread.sleep(toWait);
                } catch (InterruptedException x) {
                    // OK
                }
            } else {
                System.out.println("No outstanding HTTP operations remaining after "
                        + i + "/" + count + " iterations and " + waited + "/" + graceDelayMs
                        + " ms, (wait/iteration " + toWait + " ms)");
                break;
            }
        }
        long duration = Duration.ofNanos(System.nanoTime() - waitStart).toMillis();
        if (hasOutstanding.test(tracker)) {
            if (i == 0 && waited == 0) {
                // we found nothing and didn't wait expecting success, but then found
                // something. Respin to make sure we wait.
                return check(tracker, graceDelayMs, hasOutstanding, description, printThreads);
            }
            StringBuilder warnings = diagnose(tracker, new StringBuilder(), hasOutstanding);
            if (hasOutstanding.test(tracker)) {
                fail = new AssertionError(warnings.toString());
            }
        } else {
            System.out.println("PASSED: No " + description + " found in "
                    + tracker.getName() + " in " + duration + " ms");
        }
        if (fail != null) {
            if (printThreads && tracker.isSelectorAlive()) {
                var msg = "Selector manager threads are still alive for " + tracker.getName() + ": ";
                printThreads(msg, System.out);
                printThreads(msg, System.err);
            }
            System.out.println("AssertionError: Found some " + description + " in "
                    + tracker.getName() + " after " + i + " iterations and " + duration
                    + " ms, waited " + waited + " ms");
        }
        return fail;
    }

    public AssertionError check(long graceDelayMs,
                                Predicate<Tracker> hasOutstanding,
                                String description,
                                boolean printThreads) {
        AssertionError fail = null;
        graceDelayMs = Math.max(graceDelayMs, 100);
        long waitStart = System.nanoTime();
        long delay = Math.min(graceDelayMs, 10);
        long toWait = Math.min(graceDelayMs, Math.max(delay, 1));
        long waited = 0;
        var count = delay > 0 ? graceDelayMs / delay : 1;
        int i = 0;
        for (i = 0; i < count; i++) {
            if (TRACKERS.stream().anyMatch(hasOutstanding)) {
                System.gc();
                try {
                    if (i == 0) {
                        System.out.println("Waiting for HTTP operations to terminate...");
                        System.out.println("\tgracedelay: " + graceDelayMs
                                + " ms, iterations: " + count + ", wait/iteration: " + toWait + "ms");
                    }
                    waited += toWait;
                    Thread.sleep(toWait);
                } catch (InterruptedException x) {
                    // OK
                }
            } else {
                System.out.println("No outstanding HTTP operations remaining after "
                        + i + "/" + count + " iterations and " + waited + "/" + graceDelayMs
                        + " ms, (wait/iteration " + toWait + " ms)");
                break;
            }
        }
        long duration = Duration.ofNanos(System.nanoTime() - waitStart).toMillis();
        if (TRACKERS.stream().anyMatch(hasOutstanding)) {
            if (i == 0 && waited == 0) {
                // we found nothing and didn't wait expecting success, but then found
                // something. Respin to make sure we wait.
                return check(graceDelayMs, hasOutstanding, description, printThreads);
            }
            StringBuilder warnings = diagnose(new StringBuilder(), hasOutstanding);
            addSummary(warnings);
            if (TRACKERS.stream().anyMatch(hasOutstanding)) {
                fail = new AssertionError(warnings.toString());
            }
        } else {
            System.out.println("PASSED: No " + description + " found in "
                    + getTrackedClientCount() + " clients in " + duration + " ms");
        }
        if (fail != null) {
            Predicate<Tracker> isAlive = Tracker::isSelectorAlive;
            if (printThreads && TRACKERS.stream().anyMatch(isAlive)) {
                printThreads("Some selector manager threads are still alive: ", System.out);
                printThreads("Some selector manager threads are still alive: ", System.err);
            }
            System.out.println("AssertionError: Found some " + description + " in "
                    + getTrackedClientCount() + " clients after " + i + " iterations and " + duration
                    + " ms, waited " + waited + " ms");
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
            warning.append("\n\tPending Subscribers: " + tracker.getOutstandingSubscribers());
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
        Predicate<Tracker> hasPendingSubscribers = (t) -> t.getOutstandingSubscribers() > 0;
        AssertionError failed = check(graceDelayMs,
                isAlive.or(hasPendingRequests)
                        .or(hasPendingConnections)
                        .or(hasPendingSubscribers),
                "outstanding unclosed resources", true);
        return failed;
    }
}
