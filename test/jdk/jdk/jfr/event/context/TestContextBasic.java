/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Datadog, Inc. All rights reserved.
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

package jdk.jfr.event.context;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.time.Duration;

import jdk.jfr.Event;
import jdk.jfr.Registered;
import jdk.jfr.Enabled;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.Contextual;

import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.EventNames;
import static jdk.test.lib.Asserts.*;

/*
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal jdk.jfr/jdk.jfr.internal.settings
 * @run main/othervm jdk.jfr.event.context.TestContextBasic
 */
public class TestContextBasic {
    @Label("My Context Event")
    @Name("jdk.TestContext")
    @Registered
    @Enabled
    @Contextual
    public static class MyContextEvent extends Event {
        @Label("spanId")
        private String spanId;

        public MyContextEvent(String spanId) {
            this.spanId = spanId;
        }
    }

    @Label("My Work Event")
    @Name("jdk.TestWork")
    @Registered
    @Enabled
    public static class MyWorkEvent extends Event {
    }

    public static void main(String[] args) throws Exception {
        checkInactiveContext();
        checkSimpleContext();
        checkNestedContext();
        checkWithExecutionSamples();
    }

    private static void checkInactiveContext() throws IOException {
        Recording r = new Recording();
        r.enable(MyContextEvent.class);
        r.enable(MyWorkEvent.class);
        r.start();

        // a simple work event, not enveloped in a context at all
        new MyWorkEvent().commit();

        MyContextEvent ctx = new MyContextEvent("123");
        // events committed before the context is started do not activate the context
        new MyWorkEvent().commit();
        ctx.begin();
        ctx.end();
        // events committed after the context is ended do not activate the context
        new MyWorkEvent().commit();
        // this context event has no enveloped events; it should not be written to the recording
        ctx.commit();

        r.stop();

        List<RecordedEvent> events = Events.fromRecording(r);
        // expecting three work events and zero context events
        assertEquals(3, eventCount(events, "jdk.TestWork"), "event: jdk.TestWork");
        assertEquals(0, eventCount(events, "jdk.TestContext"), "event: jdk.TestContext");
    }

    private static void checkSimpleContext() throws IOException {
        Recording r = new Recording();
        r.enable(MyContextEvent.class);
        r.enable(MyWorkEvent.class);
        r.start();


        MyContextEvent ctx = new MyContextEvent("123");
        ctx = new MyContextEvent("456");
        ctx.begin();
        // this event is enveloped in a context; it should be written to the recording if selector is not "none"
        new MyWorkEvent().commit();
        ctx.end();
        ctx.commit();

        r.stop();

        List<RecordedEvent> events = Events.fromRecording(r);
        // expecting one work event and one context event
        assertEquals(1, eventCount(events, "jdk.TestWork"), "event: jdk.TestWork");
        assertEquals(1, eventCount(events, "jdk.TestContext"), "event: jdk.TestContext");
    }

    private static void checkNestedContext() throws IOException {
        Recording r = new Recording();
        r.enable(MyContextEvent.class);
        r.enable(MyWorkEvent.class);
        r.start();


        MyContextEvent ctx1 = new MyContextEvent("123");
        MyContextEvent ctx2 = new MyContextEvent("456");
        MyContextEvent ctx3 = new MyContextEvent("789");
        ctx1.begin();
        ctx2.begin();
        // this event is enveloped in a context and activates it
        new MyWorkEvent().commit();
        ctx3.begin();
        ctx3.end();
        // no event is enveloped in context ctx3; it should not be written to the recording
        ctx3.commit();
        ctx2.end();
        ctx2.commit();
        ctx1.end();
        // although no work event was committed for context ctx1, the nested context event ctx2 should have activated this context
        ctx1.commit();

        r.stop();

        List<RecordedEvent> events = Events.fromRecording(r);
        // expecting one work event and two context events
        assertEquals(1, eventCount(events, "jdk.TestWork"), "event: jdk.TestWork");
        assertEquals(2, eventCount(events, "jdk.TestContext"), "event: jdk.TestContext");
    }

    /**
     * This check asserts that the execution sample events enveloped in a context are activating
     * that particular context.
     * @throws Exception
     */
    private static void checkWithExecutionSamples() throws Exception {
        Thread t = new Thread(() -> {
            try {
                System.out.println("===> running thread");
                MyContextEvent ctx1 = new MyContextEvent("123");

                Random rand = new Random();
                long ts = System.nanoTime();
                long accumulated = 0;
                ctx1.begin();
                for (int i = 0; i < 1000000; i++) {
                    accumulated += sieve(rand.nextInt(10000));
                }
                synchronized (TestContextBasic.class) {
                    TestContextBasic.class.wait(500);
                }
                ctx1.end();
                ctx1.commit();

                synchronized (TestContextBasic.class) {
                    TestContextBasic.class.wait(200);
                }

                System.out.println();
                System.out.println("=== " + accumulated);
                System.out.println("Duration: " + (System.nanoTime() - ts) / 1000000 + " ms");

                assertTrue(ctx1.shouldCommit());

                new MyWorkEvent().commit();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.setDaemon(true);
        t.start();

        Recording r = new Recording();
        r.enable(MyWorkEvent.class);
        r.enable(MyContextEvent.class);
        r.enable(EventNames.ExecutionSample).withPeriod(Duration.ofMillis(1));
        r.start();

        t.join();

        r.stop();
        r.dump(java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "recording.jfr"));
        List<RecordedEvent> events = Events.fromRecording(r);
        assertEquals(1, eventCount(events, "jdk.TestContext"));
    }

    private static int eventCount(List<RecordedEvent> events, String name) throws IOException {
        int count = 0;
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(name)) {
                count++;
            }
        }
        return count;
    }

    private static long sieve(int n) {
        // Create a boolean array "prime[0..n]" and initialize
        // all entries it as true. A value in prime[i] will
        // finally be false if i is not a prime, true otherwise.
        boolean prime[] = new boolean[n+1];
        for(int i=0;i<=n;i++)
            prime[i] = true;

        for(int p = 2; p*p <=n; p++) {
            // If prime[p] is not changed, then it is a prime
            if(prime[p]) {
                // Updating all multiples of p
                for(int i = p*p; i <= n; i += p)
                    prime[i] = false;
            }
        }

        long hash = 13;
        // Print all prime numbers
        for(int i = 2; i <= n; i++) {
            hash = hash * 31 + (prime[i] ? 17 : 0);
        }
        return hash;
    }
}
