/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.security.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import com.sun.tools.attach.VirtualMachine;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.Event;
import jdk.jfr.StackTrace;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @key jfr
 * @summary Tests Agent Loaded event by starting native and Java agents
 * @requires vm.hasJFR & vm.jvmti
 *
 * @library /test/lib
 * @modules java.instrument
 *
 * @build jdk.jfr.event.runtime.JavaAgent
 *
 * @run driver jdk.test.lib.util.JavaAgentBuilder
 *      jdk.jfr.event.runtime.JavaAgent
 *      JavaAgent.jar
 *
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-UseFastUnorderedTimeStamps -javaagent:JavaAgent.jar=foo=bar
 *      jdk.jfr.event.runtime.TestAgentEvent
 *      testJavaStatic
 *
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-UseFastUnorderedTimeStamps -Djdk.attach.allowAttachSelf=true
 *      jdk.jfr.event.runtime.TestAgentEvent
 *      testJavaDynamic
 *
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-UseFastUnorderedTimeStamps -agentlib:jdwp=transport=dt_socket,server=y,address=any,onjcmd=y
 *      jdk.jfr.event.runtime.TestAgentEvent
 *      testNativeStatic
 */
public final class TestAgentEvent {
    @StackTrace(false)
    static class RecordingInterval extends Event {
    }
    private static final String JAVA_AGENT_JAR = "JavaAgent.jar";

    public static void main(String[] args) throws Throwable {
        String testMethod = args[0];
        Method m = TestAgentEvent.class.getDeclaredMethod(testMethod, new Class[0]);
        if (m == null) {
            throw new Exception("Unknown test method: " + testMethod);
        }
        m.invoke(null, new Object[0]);
    }

    private static void testJavaStatic() throws Throwable {
        try (Recording r = new Recording()) {
            r.enable(EventNames.JavaAgent).with("period", "endChunk");
            r.start();
            RecordingInterval intervalEvent = new RecordingInterval();
            intervalEvent.commit();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            events.sort(Comparator.comparing(RecordedEvent::getEndTime));
            RecordedEvent interval = events.getFirst();
            System.out.println(interval);
            RecordedEvent e = events.get(1);
            System.out.println(e);
            Events.assertField(e, "name").equal(JAVA_AGENT_JAR);
            Events.assertField(e, "options").equal("foo=bar");
            Events.assertField(e, "dynamic").equal(false);
            Instant initializationTime = e.getInstant("initializationTime");
            if (initializationTime.isAfter(interval.getStartTime())) {
                throw new Exception("Expected a static JavaAgent to be initialized before recording start");
            }
            Events.assertField(e, "initializationDuration").atLeast(0L);
        }
    }

    private static void testNativeStatic() throws Throwable {
        try (Recording r = new Recording()) {
            r.enable(EventNames.NativeAgent).with("period", "endChunk");
            r.start();
            RecordingInterval intervalEvent = new RecordingInterval();
            intervalEvent.commit();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            events.sort(Comparator.comparing(RecordedEvent::getEndTime));
            RecordedEvent interval = events.getFirst();
            System.out.println(interval);
            RecordedEvent e = events.get(1);
            System.out.println(e);
            Events.assertField(e, "name").equal("jdwp");
            Events.assertField(e, "options").equal("transport=dt_socket,server=y,address=any,onjcmd=y");
            Events.assertField(e, "dynamic").equal(false);
            Instant initializationTime = e.getInstant("initializationTime");
            if (initializationTime.isAfter(interval.getStartTime())) {
                throw new Exception("Expected a static NativeAgent to be initialized before recording start");
            }
            Events.assertField(e, "initializationDuration").atLeast(0L);
        }
    }

    private static void testJavaDynamic() throws Throwable {
        try (Recording r = new Recording()) {
            r.enable(EventNames.JavaAgent).with("period", "endChunk");
            r.start();
            RecordingInterval intervalEvent = new RecordingInterval();
            intervalEvent.begin();
            long pid = ProcessHandle.current().pid();
            VirtualMachine vm = VirtualMachine.attach(Long.toString(pid));
            vm.loadAgent(JAVA_AGENT_JAR, "bar=baz");
            vm.detach();
            vm = VirtualMachine.attach(Long.toString(pid));
            vm.loadAgent(JAVA_AGENT_JAR); // options = null
            vm.detach();
            vm = VirtualMachine.attach(Long.toString(pid));
            vm.loadAgent(JAVA_AGENT_JAR, "");
            vm.loadAgent(JAVA_AGENT_JAR, "=");
            vm.detach();
            intervalEvent.commit();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            events.sort(Comparator.comparing(RecordedEvent::getEndTime));
            RecordedEvent interval = events.getFirst();
            System.out.println(interval);
            for (RecordedEvent e : events.subList(1, events.size())) {
                System.out.println(e);
                Instant initializationTime = e.getInstant("initializationTime");
                if (initializationTime.isBefore(interval.getStartTime())) {
                    throw new Exception("Expected a dynamic JavaAgent to be initialized after recording start");
                }
                if (initializationTime.isAfter(interval.getEndTime())) {
                    throw new Exception("Expected a dynamic JavaAgent to be initialized before recording stop");
                }
                Duration initializationDuration = e.getDuration("initializationDuration");
                if (initializationDuration.isNegative()) {
                    throw new Exception("Expected initalizationDuration to be positive value");
                }
                if (initializationDuration.toSeconds() > 3600) {
                    throw new Exception("Expected initializationDuration to be less than 1 hour");
                }
                Events.assertField(e, "name").equal(JAVA_AGENT_JAR);
            }
            Events.assertField(events.get(1), "options").equal("bar=baz");
            Events.assertField(events.get(2), "options").equal(null);
            Events.assertField(events.get(3), "options").equal("");
            Events.assertField(events.get(4), "options").equal("=");
        }
    }
}
