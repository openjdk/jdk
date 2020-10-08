/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jvm;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.internal.Overridden;
import jdk.jfr.internal.Overridden.Target;
import jdk.test.lib.Utils;

/**
 * @test TextCustomThreadJavaEvent
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main/othervm jdk.jfr.jvm.TestCustomThreadJavaEvent
 */
public class TestCustomThreadJavaEvent {

    private static final int EVENTS_PER_THREAD = 50;
    private static final int THREAD_COUNT = 100;

    public static class MyEvent extends Event {
        @Overridden(Target.EVENT_THREAD)
        public Thread customThread;
    }

    public static void main(String... args) throws IOException, InterruptedException {
        Recording r = new Recording();
        // for (Method m : MyEvent.class.getMethods()) {
        //     System.err.println("===> " + m);
        // }
        r.enable(MyEvent.class).withThreshold(Duration.ofNanos(0)).withStackTrace();
        r.enable("jdk.SafepointBegin").withStackTrace();
        r.start();

        LongAdder adder = new LongAdder();
        Thread worker = new Thread(()-> {
            while (!Thread.currentThread().isInterrupted()) {
                adder.add(call1());
                LockSupport.parkNanos(10_000_000L); // 10ms pause
            }
        }, "Worker Thread");
        worker.setDaemon(true);
        worker.start();

        ThreadGroup rootGroup = getRootGroup();
        for (int i = 0; i < 5000; i++) {
            processThreadGroup(rootGroup);
            Thread.sleep(1);
        }

        r.stop();
        System.out.println("Worker result: " + adder.sum());
        prettyPrint();
        File file = Utils.createTempFile("test", ".jfr").toFile();
        r.dump(file.toPath());
        System.err.println("===> Dumped to: " + file);
        int eventCount = 0;
        for (RecordedEvent e : RecordingFile.readAllEvents(file.toPath())) {
            if (e.getEventType().getName().equals(MyEvent.class.getName())) {
                eventCount++;
            }
            if (e.getThread() != null && e.getThread().getJavaName() != null && e.getThread().getJavaName().contains("Worker Thread")) {
                System.out.println(e);
            }
        }
        System.out.println("Event count was " + eventCount + ", expected " + THREAD_COUNT * EVENTS_PER_THREAD);
        r.close();
    }

    private static ThreadGroup getRootGroup() {
        ThreadGroup currentTG = Thread.currentThread().getThreadGroup();
        ThreadGroup parentTG = currentTG.getParent();
        while (parentTG != null) {
            currentTG = parentTG;
            parentTG = currentTG.getParent();
        }
        return currentTG;
    }

    private static void processThreadGroup(ThreadGroup group) {
        ThreadGroup[] groups = new ThreadGroup[group.activeGroupCount()];
        Thread[] threads = new Thread[group.activeCount()];
        group.enumerate(groups);
        group.enumerate(threads);
        for (Thread t : threads) {
            if (t.isAlive()) {
                MyEvent event = new MyEvent();
                if (event.shouldCommit()) {
                    event.customThread = t;
                    event.commit();
                }
            }
        }
        for (ThreadGroup subgroup : groups) {
            processThreadGroup(subgroup);
        }
    }

    static void prettyPrint() {
        for (EventType type : FlightRecorder.getFlightRecorder().getEventTypes()) {
            for (AnnotationElement a : type.getAnnotationElements()) {
                printAnnotation("", a);
            }
            System.out.print("class " + removePackage(type.getName()));
            System.out.print(" extends Event");

            System.out.println(" {");
            List<ValueDescriptor> values = type.getFields();
            for (int i = 0; i < values.size(); i++) {
                ValueDescriptor v = values.get(i);
                for (AnnotationElement a : v.getAnnotationElements()) {
                    printAnnotation("  ", a);
                }
                System.out.println("  " + removePackage(v.getTypeName() + brackets(v.isArray())) + " " + v.getName());
                if (i != values.size() - 1) {
                    System.out.println();
                }
            }
            System.out.println("}");
            System.out.println();
        }
    }

    private static String brackets(boolean isArray) {
        return isArray ? "[]" : "";
    }

    private static String removePackage(String s) {

        int index = s.lastIndexOf(".");
        return s.substring(index + 1);
    }

    private static void printAnnotation(String indent, AnnotationElement a) {
        String name = removePackage(a.getTypeName());
        if (a.getValues().isEmpty()) {
            System.out.println(indent + "@" + name);
            return;
        }
        System.out.print(indent + "@" + name + "(");
        for (Object o : a.getValues()) {
            printAnnotationValue(o);
        }
        System.out.println(")");
    }

    private static void printAnnotationValue(Object o) {
        if (o instanceof String) {
            System.out.print("\"" + o + "\"");
        } else {
            System.out.print(String.valueOf(o));
        }
    }

    private static int call1() {
        LockSupport.parkNanos(1_000L); // 1us pause
        return call2() + 17;
    }

    private static int call2() {
        LockSupport.parkNanos(1_000_000L); // 1ms pause
        return call3() % 872451;
    }

    private static int call3() {
        int num = ThreadLocalRandom.current().nextInt();
        int add = ThreadLocalRandom.current().nextInt();
        for (int i = 0; i < add; i++) {
            num += i;
        }
        return num;
    }

}
