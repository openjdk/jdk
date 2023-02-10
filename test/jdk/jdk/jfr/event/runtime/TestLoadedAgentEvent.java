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

package jdk.jfr.event.runtime;

import java.lang.reflect.Method;
import java.security.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.sun.tools.attach.VirtualMachine;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.Event;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @key jfr
 * @summary Tests Loaded Agent event by starting native and Java agents
 * @requires vm.hasJFR
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
 * @run main/othervm -javaagent:JavaAgent.jar=foo=bar
 *      jdk.jfr.event.runtime.TestLoadedAgentEvent
 *      testJavaStatic
 *
 * @run main/othervm -Djdk.attach.allowAttachSelf=true
 *      jdk.jfr.event.runtime.TestLoadedAgentEvent
 *      testJavaDynamic
 *
 * @run main/othervm -agentlib:jdwp=transport=dt_socket,server=y,address=any,onjcmd=y
 *      jdk.jfr.event.runtime.TestLoadedAgentEvent
 *      testNativeStatic
 */
public final class TestLoadedAgentEvent {
    private static final String JAVA_AGENT_JAR = "JavaAgent.jar";
    private static final String EVENT_NAME = "jdk.LoadedAgent";

    public static void main(String[] args) throws Throwable {
        String testMethod = args[0];
        Method m = TestLoadedAgentEvent.class.getDeclaredMethod(testMethod, new Class[0]);
        if (m == null) {
            throw new Exception("Unknown test method: " + testMethod);
        }
        m.invoke(null, new Object[0]);
    }

    private static void testJavaStatic() throws Throwable {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME);
            r.start();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
            RecordedEvent e = events.get(0);
            System.out.println(e);
            Events.assertField(e, "name").equal(JAVA_AGENT_JAR);
            Events.assertField(e, "options").equal("foo=bar");
            Events.assertField(e, "java").equal(true);
            Events.assertField(e, "dynamic").equal(false);
        }
    }

    private static void testNativeStatic() throws Throwable {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME);
            r.start();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
            RecordedEvent e = events.get(0);
            System.out.println(e);
            Events.assertField(e, "name").equal("jdwp");
            Events.assertField(e, "options").equal("transport=dt_socket,server=y,address=any,onjcmd=y");
            Events.assertField(e, "dynamic").equal(false);
            Events.assertField(e, "java").equals(false);
        }
    }

    private static void testJavaDynamic() throws Throwable {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME);
            long start = System.currentTimeMillis();
            r.start();
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
            r.stop();
            long stop = System.currentTimeMillis();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
            Instant endTime = events.get(0).getEndTime();
            for (RecordedEvent e : events) {
                System.out.println(e);
                if (!e.getEndTime().equals(endTime)) {
                    throw new Exception("Expected all events to have the same end time");
                }
                if (!e.getStartTime().equals(endTime)) {
                    throw new Exception("Expected start and end time to be the same");
                }
                long loadTime = e.getInstant("loadTime").toEpochMilli();
                if (loadTime < start) {
                    throw new Exception("Expected agent to be loaded after recording start");
                }
                if (loadTime > stop) {
                    throw new Exception("Expected agent to be loaded before recording stop");
                }
                Events.assertField(e, "name").equal(JAVA_AGENT_JAR);
                Events.assertField(e, "dynamic").equal(true);
                Events.assertField(e, "java").equal(true);
            }
            Events.assertField(events.get(0), "options").equal("bar=baz");
            Events.assertField(events.get(1), "options").equal(null);
            Events.assertField(events.get(2), "options").equal("");
            Events.assertField(events.get(3), "options").equal("=");
        }
    }
}
