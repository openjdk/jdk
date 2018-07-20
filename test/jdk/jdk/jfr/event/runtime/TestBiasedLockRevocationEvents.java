/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.event.runtime;

import jdk.jfr.Recording;
import jdk.jfr.consumer.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.process.OutputAnalyzer;

import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 *
 * @run main/othervm jdk.jfr.event.runtime.TestBiasedLockRevocationEvents
 */
public class TestBiasedLockRevocationEvents {

    public static void main(String[] args) throws Throwable {
        testSingleRevocation();
        testBulkRevocation();
        testSelfRevocation();
        testExitedThreadRevocation();
        testBulkRevocationNoRebias();
        testRevocationSafepointIdCorrelation();
    }

    // Default value of BiasedLockingBulkRebiasThreshold is 20, and BiasedLockingBulkRevokeTreshold is 40.
    // Using a value that will hit the first threshold once, and the second one the next time.
    private static final int BULK_REVOKE_THRESHOLD = 25;

    static void touch(Object lock) {
        synchronized(lock) {
        }
    }

    static Thread triggerRevocation(int numRevokes, Class<?> lockClass) throws Throwable {
        Object[] locks = new Object[numRevokes];
        for (int i = 0; i < locks.length; ++i) {
            locks[i] = lockClass.getDeclaredConstructor().newInstance();
            touch(locks[i]);
        }

        Thread biasBreaker = new Thread("BiasBreaker") {
            @Override
            public void run() {
                for (Object lock : locks) {
                    touch(lock);
                }
            }
        };

        biasBreaker.start();
        biasBreaker.join();

        return biasBreaker;
    }

    // Basic stack trace validation, checking the name of the leaf method
    static void validateStackTrace(RecordedStackTrace stackTrace, String leafMethodName) {
        List<RecordedFrame> frames = stackTrace.getFrames();
        Asserts.assertFalse(frames.isEmpty());
        String name = frames.get(0).getMethod().getName();
        Asserts.assertEquals(name, leafMethodName);
    }

    // Validates that the given stack trace refers to lock.touch(); in triggerRevocation
    static void validateStackTrace(RecordedStackTrace stackTrace) {
        validateStackTrace(stackTrace, "touch");
    }

    // Retrieve all biased lock revocation events related to the provided lock class, sorted by start time
    static List<RecordedEvent> getRevocationEvents(Recording recording, String fieldName, Class<?> lockClass) throws Throwable {
        return Events.fromRecording(recording).stream()
                .filter(e -> ((RecordedClass)e.getValue(fieldName)).getName().equals(lockClass.getName()))
                .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                .collect(Collectors.toList());
    }

    static void testSingleRevocation() throws Throwable {
        class MyLock {};

        Recording recording = new Recording();

        recording.enable(EventNames.BiasedLockRevocation);
        recording.start();

        Thread biasBreaker = triggerRevocation(1, MyLock.class);

        recording.stop();
        List<RecordedEvent> events = getRevocationEvents(recording, "lockClass", MyLock.class);
        Asserts.assertEQ(events.size(), 1);

        RecordedEvent event = events.get(0);
        Events.assertEventThread(event, biasBreaker);
        Events.assertEventThread(event, "previousOwner", Thread.currentThread());

        RecordedClass lockClass = event.getValue("lockClass");
        Asserts.assertEquals(lockClass.getName(), MyLock.class.getName());

        validateStackTrace(event.getStackTrace());
    }

    static void testBulkRevocation() throws Throwable {
        class MyLock {};

        Recording recording = new Recording();

        recording.enable(EventNames.BiasedLockClassRevocation);
        recording.start();

        Thread biasBreaker = triggerRevocation(BULK_REVOKE_THRESHOLD, MyLock.class);

        recording.stop();
        List<RecordedEvent> events = getRevocationEvents(recording, "revokedClass", MyLock.class);
        Asserts.assertEQ(events.size(), 1);

        RecordedEvent event = events.get(0);
        Events.assertEventThread(event, biasBreaker);
        Events.assertField(event, "disableBiasing").equal(false);

        RecordedClass lockClass = event.getValue("revokedClass");
        Asserts.assertEquals(lockClass.getName(), MyLock.class.getName());

        validateStackTrace(event.getStackTrace());
    }

    static void testSelfRevocation() throws Throwable {
        class MyLock {};

        Recording recording = new Recording();

        recording.enable(EventNames.BiasedLockSelfRevocation);
        recording.start();

        MyLock l = new MyLock();
        touch(l);
        Thread.holdsLock(l);

        recording.stop();
        List<RecordedEvent> events = getRevocationEvents(recording, "lockClass", MyLock.class);
        Asserts.assertEQ(events.size(), 1);

        RecordedEvent event = events.get(0);
        Events.assertEventThread(event, Thread.currentThread());

        validateStackTrace(event.getStackTrace(), "holdsLock");
    }

    static void testExitedThreadRevocation() throws Throwable {
        class MyLock {};

        Recording recording = new Recording();

        recording.enable(EventNames.BiasedLockRevocation);
        recording.start();

        FutureTask<MyLock> lockerTask = new FutureTask<>(() -> {
           MyLock l = new MyLock();
           touch(l);
           return l;
        });

        Thread locker = new Thread(lockerTask, "BiasLocker");
        locker.start();
        locker.join();

        // Even after joining, the VM has a bit more work to do before the thread is actually removed
        // from the threads list. Ensure that this has happened before proceeding.
        while (true) {
            PidJcmdExecutor jcmd = new PidJcmdExecutor();
            OutputAnalyzer oa = jcmd.execute("Thread.print", true);
            String lockerThreadFound = oa.firstMatch("BiasLocker");
            if (lockerThreadFound == null) {
                break;
            }
        };

        MyLock l = lockerTask.get();
        touch(l);

        recording.stop();
        List<RecordedEvent> events = getRevocationEvents(recording, "lockClass", MyLock.class);
        Asserts.assertEQ(events.size(), 1);

        RecordedEvent event = events.get(0);
        Events.assertEventThread(event, Thread.currentThread());
        // Previous owner will usually be null, but can also be a thread that
        // was created after the BiasLocker thread exited due to address reuse.
        RecordedThread prevOwner = event.getValue("previousOwner");
        if (prevOwner != null) {
            Asserts.assertNE(prevOwner.getJavaName(), "BiasLocker");
        }
        validateStackTrace(event.getStackTrace());
    }

    static void testBulkRevocationNoRebias() throws Throwable {
        class MyLock {};

        Recording recording = new Recording();

        recording.enable(EventNames.BiasedLockClassRevocation);
        recording.start();

        Thread biasBreaker0 = triggerRevocation(BULK_REVOKE_THRESHOLD, MyLock.class);
        Thread biasBreaker1 = triggerRevocation(BULK_REVOKE_THRESHOLD, MyLock.class);

        recording.stop();
        List<RecordedEvent> events = getRevocationEvents(recording, "revokedClass", MyLock.class);
        Asserts.assertEQ(events.size(), 2);

        // The rebias event should occur before the noRebias one
        RecordedEvent eventRebias = events.get(0);
        RecordedEvent eventNoRebias = events.get(1);

        Events.assertEventThread(eventRebias, biasBreaker0);
        Events.assertField(eventRebias, "disableBiasing").equal(false);

        Events.assertEventThread(eventNoRebias, biasBreaker1);
        Events.assertField(eventNoRebias, "disableBiasing").equal(true);

        RecordedClass lockClassRebias = eventRebias.getValue("revokedClass");
        Asserts.assertEquals(lockClassRebias.getName(), MyLock.class.getName());
        RecordedClass lockClassNoRebias = eventNoRebias.getValue("revokedClass");
        Asserts.assertEquals(lockClassNoRebias.getName(), MyLock.class.getName());

        validateStackTrace(eventRebias.getStackTrace());
        validateStackTrace(eventNoRebias.getStackTrace());
    }

    static void testRevocationSafepointIdCorrelation() throws Throwable {
        class MyLock {};

        Recording recording = new Recording();

        recording.enable(EventNames.BiasedLockRevocation);
        recording.enable(EventNames.BiasedLockClassRevocation);
        recording.enable(EventNames.ExecuteVMOperation);
        recording.start();

        triggerRevocation(BULK_REVOKE_THRESHOLD, MyLock.class);

        recording.stop();
        List<RecordedEvent> events = Events.fromRecording(recording);

        // Determine which safepoints included single and bulk revocation VM operations
        Set<Integer> vmOperationsSingle = new HashSet<>();
        Set<Integer> vmOperationsBulk = new HashSet<>();

        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(EventNames.ExecuteVMOperation)) {
                String operation = event.getValue("operation");
                Integer safepointId = event.getValue("safepointId");

                if (operation.equals("RevokeBias")) {
                    vmOperationsSingle.add(safepointId);
                } else if (operation.equals("BulkRevokeBias")) {
                    vmOperationsBulk.add(safepointId);
                }
            }
        }

        int revokeCount = 0;
        int bulkRevokeCount = 0;

        // Match all revoke events to a corresponding VMOperation event
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(EventNames.BiasedLockRevocation)) {
                Integer safepointId = event.getValue("safepointId");
                String lockClass = ((RecordedClass)event.getValue("lockClass")).getName();
                if (lockClass.equals(MyLock.class.getName())) {
                    Asserts.assertTrue(vmOperationsSingle.contains(safepointId));
                    revokeCount++;
                }
            } else if (event.getEventType().getName().equals(EventNames.BiasedLockClassRevocation)) {
                Integer safepointId = event.getValue("safepointId");
                String lockClass = ((RecordedClass)event.getValue("revokedClass")).getName();
                if (lockClass.toString().equals(MyLock.class.getName())) {
                    Asserts.assertTrue(vmOperationsBulk.contains(safepointId));
                    bulkRevokeCount++;
                }
            }
        }

        Asserts.assertGT(bulkRevokeCount, 0);
        Asserts.assertGT(revokeCount, bulkRevokeCount);
    }
}
