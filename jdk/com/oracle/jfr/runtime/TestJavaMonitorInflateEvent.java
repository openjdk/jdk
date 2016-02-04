/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import java.util.List;
import java.util.concurrent.CountDownLatch;

import jrockit.Asserts;
import jrockit.TestThread;
import jrockit.XRun;
import jrockit.jfr.TestRecording;
import jrockit.jfr.ValueFilter;
import oracle.jrockit.jfr.parser.FLREvent;
import oracle.jrockit.jfr.parser.FLRStruct;

/*
 * @test TestJavaMonitorInflateEvent
 * @key jfr
 * @library ../common
 * @modules jdk.jfr/oracle.jrockit.jfr
 *          jdk.jfr/oracle.jrockit.jfr.parser
 * @build jrockit.* jrockit.jfr.*
 * @run main/othervm -XX:+UnlockCommercialFeatures -XX:+FlightRecorder TestJavaMonitorInflateEvent
 */
public class TestJavaMonitorInflateEvent {

    private static final String FIELD_CLASS_NAME = "name";
    private static final String FIELD_KLASS      = "klass";
    private static final String FIELD_ADDRESS    = "address";
    private static final String FIELD_CAUSE      = "cause";

    private final static String EVENT_PATH = "java/monitor_inflate";

    static class Lock {
    }

    public static void main(String[] args) throws Exception {
        TestRecording r = new TestRecording();
        try {
            r.createJVMSetting(EVENT_PATH, true, true, 0, 0);

            final Lock lock = new Lock();
            final CountDownLatch latch = new CountDownLatch(1);

            // create a thread that waits
            TestThread waitThread = new TestThread(new XRun() {
                @Override
                public void xrun() throws Throwable {
                    synchronized (lock) {
                        latch.countDown();
                        lock.wait(123456);
                    }
                }
            });
            try {
                r.start();
                waitThread.start();
                latch.await();
                synchronized (lock) {
                    lock.notifyAll();
                }
            } finally {
                waitThread.join();
                r.stop();
            }

            List<FLREvent> events = r.parser().findJVMEvents(EVENT_PATH);
            System.out.println(events);
            if (events.isEmpty()) {
                throw new Exception("Expected event");
            }

            String thisThreadName = Thread.currentThread().getName();
            String waitThreadName = waitThread.getName();

            // Find at least one event with the correct monitor class and check the other fields
            boolean foundEvent = false;
            for (FLREvent event : events) {
                if (!String.valueOf(event.getThread().getResolvedValue("name")).equals(waitThreadName)) {
                    continue;
                }
                // Check lock class
                FLRStruct klassStruct = (FLRStruct) event.getResolvedValue(FIELD_KLASS);
                String recordedLockClass = String.valueOf(klassStruct.getResolvedValue(FIELD_CLASS_NAME));
                String lockClass = lock.getClass().getName();
                if (!recordedLockClass.equals(lockClass)) {
                    continue;
                }

                foundEvent = true;
                // Check address
                Asserts.assertNotEquals(0L, event.getResolvedValue(FIELD_ADDRESS), FIELD_ADDRESS + " should not be 0");
                // Check cause
                Asserts.assertNotNull(event.getValue(FIELD_CAUSE), FIELD_CAUSE + " should not be null");
            }
            Asserts.assertTrue(foundEvent, "Excepted event from test thread");
        } catch (Throwable e) {
            r.copyTo("failed.jfr");
            throw e;
        } finally {
            r.close();
        }
    }
}
