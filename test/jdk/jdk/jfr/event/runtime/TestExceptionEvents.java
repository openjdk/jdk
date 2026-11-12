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
import java.util.Objects;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/*
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
    private static final int THROWN_EXCEPTIONS = 5000;
    private static final int NOISE_EXCEPTIONS = 2400;

    public static void main(String[] args) throws Throwable {
        testCheckedException();
        testRuntimeException();
        testThrowableException();
        testError();
        testErrorAndException();
        testErrorConstructors();
        testThrowableConstructors();
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
            r.enable(EXCEPTION_STATISTICS_NAME)
            .with("period", "everyChunk"); // Emits events at the beginning and end of the recording
            r.start();
            for (int i = 0; i < THROWN_EXCEPTIONS; i++) {
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
            checkEvents(events, ERROR_EVENT_NAME, message, THROWN_EXCEPTIONS, MyError.class);
            checkEvents(events, EXCEPTION_EVENT_NAME, message, 0, MyError.class);
            checkCount(events, 2 * THROWN_EXCEPTIONS);
        }
    }

    private static void testErrorAndException() throws Exception {
        String message1 = "message-1";
        String message2 = "message-2";
        try (Recording r = new Recording()) {
            r.enable(EXCEPTION_EVENT_NAME).withStackTrace();
            r.enable(ERROR_EVENT_NAME).withStackTrace();
            r.enable(EXCEPTION_STATISTICS_NAME)
             .with("period", "everyChunk"); // Emits events at the beginning and end of the recording
            r.start();
            for (int i = 0; i < THROWN_EXCEPTIONS; i++) {
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
            checkEvents(events, EXCEPTION_EVENT_NAME, message1, THROWN_EXCEPTIONS, MyCheckedException.class);
            checkEvents(events, EXCEPTION_EVENT_NAME, message2, THROWN_EXCEPTIONS, MyError.class);
            checkEvents(events, ERROR_EVENT_NAME, message1, 0, MyError.class);
            checkEvents(events, ERROR_EVENT_NAME, message2, THROWN_EXCEPTIONS, MyError.class);
            checkCount(events, 2 * THROWN_EXCEPTIONS);
        }
    }

    private static void testThrowableConstructors() throws Exception {
        Exception cause = new Exception("cause-message");
        try (Recording r = new Recording()) {
            r.enable(EXCEPTION_EVENT_NAME).withStackTrace();
            r.enable(ERROR_EVENT_NAME).withStackTrace();
            r.start();
            try {
                throw new Throwable();
            } catch (Throwable e) {
            }
            try {
                throw new Throwable("throwable-message");
            } catch (Throwable e) {
            }
            try {
                throw new Throwable(cause);
            } catch (Throwable e) {
            }
            try {
                throw new Throwable("throwable-message-and-cause", cause);
            } catch (Throwable e) {
            }
            try {
                throw new MyThrowable("my-throwable-message", cause, true, true);
            } catch (MyThrowable e) {
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            // new Throwable()
            checkEvents(events, EXCEPTION_EVENT_NAME, null, 1, Throwable.class);
            // new Throwable("throwable-message");
            checkEvents(events, EXCEPTION_EVENT_NAME, "throwable-message", 1, Throwable.class);
            // new Throwable(cause)
            checkEvents(events, EXCEPTION_EVENT_NAME, cause.toString(), 1, Throwable.class);
            // new Throwable("throwable-message-and-cause", cause);
            checkEvents(events, EXCEPTION_EVENT_NAME, "throwable-message-and-cause", 1, Throwable.class);
            // new MyThrowable("my-throwable-message", cause, true, true);
            checkEvents(events, EXCEPTION_EVENT_NAME, "my-throwable-message", 1, MyThrowable.class);
        }
    }

    private static void testErrorConstructors() throws Exception {
        Exception cause = new Exception("cause-message");
        try (Recording r = new Recording()) {
            r.enable(EXCEPTION_EVENT_NAME).withStackTrace();
            r.enable(ERROR_EVENT_NAME).withStackTrace();
            r.start();
            try {
                throw new Error();
            } catch (Error e) {
            }
            try {
                throw new Error("error-message");
            } catch (Error e) {
            }
            try {
                throw new Error(cause);
            } catch (Error e) {
            }
            try {
                throw new Error("error-message-and-cause", cause);
            } catch (Error e) {
            }
            try {
                throw new MyError("my-error-message", cause, true, true);
            } catch (MyError e) {
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            // new Error()
            checkErrorEvents(events, null, Error.class);
            // new Error("error-message");
            checkErrorEvents(events, "error-message", Error.class);
            // new Error(cause)
            checkErrorEvents(events, cause.toString(), Error.class);
            // new Error("error-message-and-cause", cause);
            checkErrorEvents(events, "error-message-and-cause", Error.class);
            // new MyError("my-error-message", cause, true, true);
            checkErrorEvents(events, "my-error-message", MyError.class);
        }
    }

    private static void checkErrorEvents(List<RecordedEvent> events, String expectedMessage, Class<?> expectedClass) throws Exception {
        checkEvents(events, EXCEPTION_EVENT_NAME, expectedMessage, 1, expectedClass);
        checkEvents(events, ERROR_EVENT_NAME, expectedMessage, 1, expectedClass);
    }

    private static void testThrottling() throws Exception {
        int thrown = 5000;
        String message = "testThrottling";
        try (Recording r = new Recording()) {
            r.enable(EXCEPTION_EVENT_NAME).withStackTrace().with("throttle", (thrown / 10) + "/s");
            r.enable(EXCEPTION_STATISTICS_NAME)
             .with("period", "everyChunk"); // Emits events at the beginning and end of the recording
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
                throw new Exception("Expected at least two events to be sampled");
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
        Asserts.assertGreaterThanOrEqual(count, expectedCount, "Too few exceptions counted in statistics event");
        Asserts.assertLessThan(count, expectedCount + NOISE_EXCEPTIONS, "Too many exceptions counted in statistics event");
    }

    private static void checkEvents(List<RecordedEvent> events, String eventType, String expectedMessage, int expectedCount, Class<?> expectedClass) throws Exception {
        int count = 0;
        for (RecordedEvent event : ofType(events, eventType)) {
            String message = Events.assertField(event, "message").getValue();
            if (Objects.equals(expectedMessage, message)) {
                if (message == null && !isThrownFromTestClass(event)) {
                    continue;
                }
                RecordedThread t = event.getThread();
                if (!t.getJavaName().equals(THREAD_NAME)) {
                    System.out.println(event);
                    throw new Exception("Incorrect thread name, expected: " + THREAD_NAME);
                }
                String className = event.getClass("thrownClass").getName();
                if (!className.equals(expectedClass.getName())) {
                    throw new Exception("Incorrect exception class name, expected: " + expectedClass.getName());
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
            throw new Exception("Expected " + expectedCount + " events, but found " + count + " with the message: " + expectedMessage);
        }
    }

    private static boolean isThrownFromTestClass(RecordedEvent event) {
        for (RecordedFrame f : event.getStackTrace().getFrames()) {
            if (f.isJavaFrame()) {
                if (f.getMethod().getType().getName().equals(TestExceptionEvents.class.getName())) {
                    return true;
                }
            }
        }
        return false;
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

        public MyError(String message, Exception cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    private static class MyThrowable extends Throwable {
        public MyThrowable(String message) {
            super(message);
        }

        public MyThrowable(String message, Exception cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
