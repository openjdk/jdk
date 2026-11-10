/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.runtime.TestExceptionEvents
 */
@SuppressWarnings("serial")
public class TestExceptionEvents {
    private static final String EXCEPTION_EVENT_NAME = EventNames.JavaExceptionThrow;
    private static final String ERROR_EVENT_NAME = EventNames.JavaErrorThrow;
    private static final String EXCEPTION_STATISTICS_NAME = EventNames.ExceptionStatistics;
    private static final String THREAD_NAME = Thread.currentThread().getName();
    private static final int THROWN_EXCEPTION = 5000;
    private static final int NOISE_EXCEPTIONS = 2400;

    public static void main(String[] args) throws Throwable {
        testCheckedException();
        testRuntimeException();
        testThrowableException();
        testError();
        testErrorAndException();
        testCauseMessage();
        testThrottling();
    }

    private static void testCheckedException() throws Exception {
        testException(MyCheckedException.class, "testCheckedException");
    }

    private static void testRuntimeException() throws Exception {
        testException(MyRuntimeException.class, "testRuntimeException");
    }

    private static void testThrowableException() throws Exception {
        testException(MyThrowable.class, "testThrowableException");
    }

    private static void testError() throws Exception {
        String message = "testError";
        try (Recording r = new Recording()) {
            r.enable(ERROR_EVENT_NAME).withStackTrace();
            r.enable(EXCEPTION_STATISTICS_NAME).with("period", "everyChunk");
            r.start();
            for (int i = 0; i < THROWN_EXCEPTION; i++) {
                try {
                    throw new MyError(message);
                } catch (MyError e) {
                }
                try {
                    throw new MyCheckedException(message);
                } catch (MyCheckedException e) {
                }
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            checkEvents(events, ERROR_EVENT_NAME, message, THROWN_EXCEPTION, MyError.class);
            checkEvents(events, EXCEPTION_EVENT_NAME, message, 0, MyError.class);
            checkCount(events, 2 * THROWN_EXCEPTION);
        }
    }

    private static void testErrorAndException() throws Exception {
        String message1 = "message-1";
        String message2 = "message-2";
        try (Recording r = new Recording()) {
            r.enable(EXCEPTION_EVENT_NAME).withStackTrace();
            r.enable(ERROR_EVENT_NAME).withStackTrace();
            r.enable(EXCEPTION_STATISTICS_NAME).with("period", "everyChunk");
            r.start();
            for (int i = 0; i < THROWN_EXCEPTION; i++) {
                try {
                    throw new MyCheckedException(message1);
                } catch (MyCheckedException e) {
                }
                try {
                    throw new MyError(message2);
                } catch (MyError e) {
                }
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            checkEvents(events, EXCEPTION_EVENT_NAME, message1, THROWN_EXCEPTION, MyCheckedException.class);
            checkEvents(events, EXCEPTION_EVENT_NAME, message2, THROWN_EXCEPTION, MyError.class);
            checkEvents(events, ERROR_EVENT_NAME, message1, 0, MyError.class);
            checkEvents(events, ERROR_EVENT_NAME, message2, THROWN_EXCEPTION, MyError.class);
            checkCount(events, 2 * THROWN_EXCEPTION);
        }
    }

    private static void testCauseMessage() throws Exception {
        Exception cause1 = new Exception("cause1-message");
        Exception cause2 = new Exception("cause2-message");
        try (Recording r = new Recording()) {
            r.enable(EXCEPTION_EVENT_NAME).withStackTrace();
            r.enable(ERROR_EVENT_NAME).withStackTrace();
            r.start();
            try {
                throw new Error(cause1);
            } catch (Error e) {
            }
            try {
                throw new Throwable(cause2);
            } catch (Throwable e) {
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            checkEvents(events, EXCEPTION_EVENT_NAME, cause1.toString(), 1, Error.class);
            checkEvents(events, ERROR_EVENT_NAME, cause1.toString(), 1, Error.class);
            checkEvents(events, EXCEPTION_EVENT_NAME, cause2.toString(), 1, Throwable.class);
        }
    }

    private static void testThrottling() throws Exception {
        int thrown = 5000;
        String message = "testThrottling";
        try (Recording r = new Recording()) {
            r.enable(EXCEPTION_EVENT_NAME).withStackTrace().with("throttle", (thrown / 10) + "/s");
            r.enable(EXCEPTION_STATISTICS_NAME).with("period", "everyChunk");
            r.start();
            for (int i = 0; i < thrown; i++) {
                try {
                    throw new MyCheckedException(message);
                } catch (Exception e) {
                }
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            long sampled = 0;
            for (RecordedEvent event : ofType(events, EXCEPTION_EVENT_NAME)) {
                if (message.equals(event.getString("message"))) {
                    sampled++;
                }
            }
            if (sampled >= thrown) {
                throw new Exception("No exception events were throttled");
            }
            if (sampled < 2) {
                throw new Exception("Expected at least two event to be sampled event");
            }
        }
    }

    private static void testException(Class<?> type, String message) throws Exception {
        int count = 5000;
        try (Recording r = new Recording()) {
            r.enable(EXCEPTION_EVENT_NAME).withStackTrace();
            r.enable(EXCEPTION_STATISTICS_NAME).with("period", "everyChunk");
            r.start();
            for (int i = 0; i < count; i++) {
                if (type == MyCheckedException.class) {
                    try {
                        throw new MyCheckedException(message);
                    } catch (MyCheckedException e) {
                    }
                }
                if (type == MyRuntimeException.class) {
                    try {
                        throw new MyRuntimeException(message);
                    } catch (MyRuntimeException e) {
                    }
                }
                if (type == MyThrowable.class) {
                    try {
                        throw new MyThrowable(message);
                    } catch (MyThrowable e) {
                    }
                }
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            checkEvents(events, EXCEPTION_EVENT_NAME, message, count, type);
            checkEvents(events, ERROR_EVENT_NAME, message, 0, type);
            checkCount(events, count);
        }
    }

    private static List<RecordedEvent> ofType(List<RecordedEvent> events, String eventType) {
        List<RecordedEvent> result = new ArrayList<>();
        for (RecordedEvent event : events) {
            if (Events.isEventType(event, eventType)) {
                result.add(event);
            }
        }
        return result;
    }

    private static void checkCount(List<RecordedEvent> events, long expectedCount) throws Exception {
        events.sort(Comparator.comparing(RecordedEvent::getEndTime));
        List<RecordedEvent> statisticsEvents = ofType(events, EXCEPTION_STATISTICS_NAME);
        if (statisticsEvents.size() != 2) {
            throw new Exception("Expected two ExceptionStatistics events");
        }
        RecordedEvent begin = statisticsEvents.get(0);
        RecordedEvent end = statisticsEvents.get(1);
        long count = end.getLong("throwables") - begin.getLong("throwables");
        Asserts.assertGreaterThanOrEqual(count, expectedCount, "Too few exception count in statistics event");
        Asserts.assertLessThan(count, expectedCount + NOISE_EXCEPTIONS, "Too many exception count in statistics event");
    }

    private static void checkEvents(List<RecordedEvent> events, String eventType, String expectedMessage, int expectedCount, Class<?> expectedClass) throws Exception {
        int count = 0;
        for (RecordedEvent event : ofType(events, eventType)) {
            String message = Events.assertField(event, "message").getValue();
            if (expectedMessage.equals(message)) {
                RecordedThread t = event.getThread();
                if (!t.getJavaName().equals(THREAD_NAME)) {
                    System.out.println(event);
                    throw new Exception("Incorrect thread name, expected: " + THREAD_NAME);
                }
                String className = event.getClass("thrownClass").getName();
                if (!className.equals(expectedClass.getName())) {
                    throw new Exception("Incorrect exception class name, expected: " + className);
                }
                RecordedFrame topFrame = event.getStackTrace().getFrames().get(0);
                if (!topFrame.getMethod().getName().equals("<init>")) {
                    System.out.println(event);
                    throw new Exception("Expected name of top frame to be <init>");
                }
                count++;
            }
        }
        if (count != expectedCount) {
            throw new Exception("Expected " + expectedCount + " events with message: " + expectedMessage);
        }
    }

    private static class MyRuntimeException extends RuntimeException {
        public MyRuntimeException(String message) {
            super(message);
        }
    }

    private static class MyCheckedException extends Exception {
        public MyCheckedException(String message) {
            super(message);
        }
    }

    private static class MyError extends Error {
        public MyError(String message) {
            super(message);
        }
    }

    private static class MyThrowable extends Throwable {
        public MyThrowable(String message) {
            super(message);
        }
    }
}
