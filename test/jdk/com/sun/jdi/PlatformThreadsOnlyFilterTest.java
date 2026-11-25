/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8372536
 * @summary Verify the JDI platform-threads-only filter for thread start and death events
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g PlatformThreadsOnlyFilterTest.java
 * @run driver PlatformThreadsOnlyFilterTest
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.ArrayList;
import java.util.List;

class PlatformThreadsOnlyFilterTarg {
    static final int PLATFORM_WORKERS = 2;
    static final int VIRTUAL_WORKERS = 2;

    // Read by the debugger. Also keeps the terminated workers reachable.
    static Thread[] phase1Workers;
    static Thread[] phase2Workers;

    // breakpoint here, all workers are created and none is started
    static void ready() {
    }

    // breakpoint here, the phase 1 workers have terminated
    static void phase1Done() {
    }

    // breakpoint here, the phase 2 workers have terminated
    static void phase2Done() {
    }

    public static void main(String[] args) throws Exception {
        phase1Workers = createWorkers("phase1");
        phase2Workers = createWorkers("phase2");
        ready();
        startAndJoin(phase1Workers);
        phase1Done();
        startAndJoin(phase2Workers);
        phase2Done();
    }

    static Thread[] createWorkers(String prefix) {
        Thread[] workers = new Thread[PLATFORM_WORKERS + VIRTUAL_WORKERS];
        int n = 0;
        for (int i = 0; i < PLATFORM_WORKERS; i++) {
            workers[n++] = Thread.ofPlatform().name(prefix + "-platform-" + i).unstarted(() -> { });
        }
        for (int i = 0; i < VIRTUAL_WORKERS; i++) {
            workers[n++] = Thread.ofVirtual().name(prefix + "-virtual-" + i).unstarted(() -> { });
        }
        return workers;
    }

    static void startAndJoin(Thread[] workers) throws InterruptedException {
        for (Thread worker : workers) {
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
    }
}

public class PlatformThreadsOnlyFilterTest extends TestScaffold {
    private static final String TARGET = "PlatformThreadsOnlyFilterTarg";

    private record Worker(ThreadReference thread, String name, boolean virtual) { }

    private record ReceivedEvent(EventRequest request, ThreadReference thread) { }

    private ThreadStartRequest filteredStart;
    private ThreadStartRequest unfilteredStart;
    private ThreadDeathRequest filteredDeath;
    private ThreadDeathRequest unfilteredDeath;

    private final List<ReceivedEvent> received = new ArrayList<>();

    PlatformThreadsOnlyFilterTest(String[] args) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        new PlatformThreadsOnlyFilterTest(args).startTests();
    }

    private void addEvent(EventRequest request, ThreadReference thread) {
        synchronized (received) {
            received.add(new ReceivedEvent(request, thread));
        }
    }

    private List<Worker> readWorkers(ReferenceType targetClass, String fieldName) {
        Field field = targetClass.fieldByName(fieldName);
        ArrayReference array = (ArrayReference) targetClass.getValue(field);
        List<Worker> workers = new ArrayList<>();
        for (Value value : array.getValues()) {
            ThreadReference thread = (ThreadReference) value;
            Worker worker = new Worker(thread, thread.name(), thread.isVirtual());
            println(fieldName + ": " + worker.name() + (worker.virtual() ? " is virtual" : " is platform"));
            if (worker.virtual() != worker.name().contains("-virtual-")) {
                failure("FAILED: isVirtual() is " + worker.virtual() + " for " + worker.name());
            }
            workers.add(worker);
        }
        return workers;
    }

    private void expect(String phase, List<ReceivedEvent> events, String what, EventRequest request,
                        Worker worker, int expected) {
        int actual = 0;
        for (ReceivedEvent event : events) {
            if (request.equals(event.request()) && worker.thread().equals(event.thread())) {
                actual++;
            }
        }
        println(phase + ": " + what + " events for " + worker.name() + ": " + actual);
        if (actual != expected) {
            failure("FAILED: " + phase + ": expected " + expected + " " + what + " events for "
                    + worker.name() + ", got " + actual);
        }
    }

    private boolean isVirtual(ThreadReference thread) {
        try {
            return thread.isVirtual();
        } catch (ObjectCollectedException e) {
            // The thread is gone and its kind can no longer be read.
            return false;
        }
    }

    private void check(String phase, List<Worker> workers, boolean unfilteredEnabled) {
        List<ReceivedEvent> events;
        synchronized (received) {
            events = new ArrayList<>(received);
            received.clear();
        }
        println(phase + ": got " + events.size() + " thread start and death events, other threads included");

        // Only the workers of this phase are counted.
        for (Worker worker : workers) {
            int expected = worker.virtual() ? 0 : 1;
            expect(phase, events, "filtered start", filteredStart, worker, expected);
            expect(phase, events, "filtered death", filteredDeath, worker, expected);
            if (unfilteredEnabled) {
                expect(phase, events, "unfiltered start", unfilteredStart, worker, 1);
                expect(phase, events, "unfiltered death", unfilteredDeath, worker, 1);
            }
        }
        // Other threads, like carrier threads, can show up too. None of them may be virtual.
        for (ReceivedEvent event : events) {
            boolean filtered = filteredStart.equals(event.request()) || filteredDeath.equals(event.request());
            boolean counted = workers.stream().anyMatch(worker -> worker.thread().equals(event.thread()));
            if (filtered && !counted && isVirtual(event.thread())) {
                failure("FAILED: " + phase + ": filtered request got an event for another virtual thread, id "
                        + event.thread().uniqueID());
            }
        }
    }

    @Override
    protected void runTests() throws Exception {
        BreakpointEvent bpe = startToMain(TARGET);
        ReferenceType targetClass = bpe.location().declaringType();
        EventRequestManager erm = vm().eventRequestManager();

        resumeTo(TARGET, "ready", "()V");
        List<Worker> phase1 = readWorkers(targetClass, "phase1Workers");
        List<Worker> phase2 = readWorkers(targetClass, "phase2Workers");

        filteredStart = erm.createThreadStartRequest();
        filteredStart.addPlatformThreadsOnlyFilter();
        unfilteredStart = erm.createThreadStartRequest();
        filteredDeath = erm.createThreadDeathRequest();
        filteredDeath.addPlatformThreadsOnlyFilter();
        unfilteredDeath = erm.createThreadDeathRequest();
        for (EventRequest request : List.of(filteredStart, unfilteredStart, filteredDeath, unfilteredDeath)) {
            request.setSuspendPolicy(EventRequest.SUSPEND_NONE);
            request.enable();
        }

        TargetAdapter collector = new TargetAdapter() {
            @Override
            public void threadStarted(ThreadStartEvent event) {
                addEvent(event.request(), event.thread());
            }

            @Override
            public void threadDied(ThreadDeathEvent event) {
                addEvent(event.request(), event.thread());
            }
        };
        addListener(collector);

        // Phase 1: filtered and unfiltered requests side by side.
        // The workers are joined before the breakpoint, so their events arrive before it.
        resumeTo(TARGET, "phase1Done", "()V");
        check("phase 1", phase1, true);

        // Phase 2: only the filtered requests are left.
        erm.deleteEventRequest(unfilteredStart);
        erm.deleteEventRequest(unfilteredDeath);
        resumeTo(TARGET, "phase2Done", "()V");
        check("phase 2", phase2, false);

        erm.deleteEventRequest(filteredStart);
        erm.deleteEventRequest(filteredDeath);
        removeListener(collector);

        listenUntilVMDisconnect();

        if (!testFailed) {
            println("PlatformThreadsOnlyFilterTest: passed");
        } else {
            throw new Exception("PlatformThreadsOnlyFilterTest: failed");
        }
    }
}
