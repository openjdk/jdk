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

package jdk.jfr.jvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm/timeout=300 jdk.jfr.jvm.TestChunkIntegrity
 */
public class TestChunkIntegrity {

    static abstract class StressThread extends Thread {
        private volatile boolean keepAlive = true;
        private final Random random = new Random();

        public void run() {
            try {
                while (keepAlive) {
                    int count = random.nextInt(1_000) + 1;
                    for (int i = 0; i < count; i++) {
                        stress();
                    }
                    System.out.println(count + " " + this.getClass().getName());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        abstract protected void stress() throws Exception;

        public void kill() {
            keepAlive = false;
            try {
                join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public static void main(String... args) throws Throwable {
        Configuration c = Configuration.getConfiguration("profile");
        Path file = Path.of("recording.jfr");
        try (Recording r = new Recording(c)) {
            r.start();
            List<StressThread> threads = new ArrayList<>();
            threads.add(new ClassStressor());
            threads.add(new ThreadStressor());
            threads.add(new StringStressor());
            threads.forEach(StressThread::start);
            for (int i = 0; i < 10; i++) {
                try (Recording t = new Recording()) {
                    t.start();
                }
            }
            threads.forEach(StressThread::kill);
            r.dump(file);

            // Split recording file
            Path directory = Path.of("disassembled");
            Files.createDirectories(directory);
            disassemble(file, directory);

            // Verification
            List<RecordedEvent> full = RecordingFile.readAllEvents(file);
            List<Path> chunkFiles = new ArrayList<>(Files.list(directory).toList());
            Collections.sort(chunkFiles);
            int total = 0;
            for (Path chunkFile : chunkFiles) {
                System.out.println("Veryfying chunk: " + chunkFile + " " + total);
                try (RecordingFile f = new RecordingFile(chunkFile)) {
                    int index = 0;
                    while (f.hasMoreEvents()) {
                        RecordedEvent event = f.readEvent();
                        assertStressEvent(event, f, index);
                        assertEventEquals(full.get(total + index), event, index);
                        index++;
                    }
                    total += index;
                }
            }
            System.out.println("Event count: " + total);
        }
    }

    static void assertStressEvent(RecordedEvent event, RecordingFile f, int index) throws IOException {
        String name = event.getEventType().getName();
        if (name.equals("String") || name.equals("Thread") || name.equals("Clazz")) {
            String fieldName = name.toLowerCase();
            Object value = event.getValue(fieldName);
            if (value == null) {
                writeFailureFile(f, index);
                throw new AssertionError("Null found in " + name + " event. Event number " + index);
            }
            RecordedStackTrace stackTrace = event.getStackTrace();
            if (stackTrace == null) {
                writeFailureFile(f, index);
                throw new AssertionError("Stack trace was null. Event number " + index);
            }
        }
    }

    private static void writeFailureFile(RecordingFile f, int index) throws IOException {
        Path file = Path.of("failure.jfr");
        AtomicInteger count = new AtomicInteger();
        f.write(file, e-> count.incrementAndGet() == index + 1);
        System.out.println("Failure file with only event " + index + " written to: " + file);
    }

    static void assertEventEquals(RecordedEvent a, RecordedEvent b, int index) {
        if (a.getEventType().getId() != b.getEventType().getId()) {
            printRecordedObjects(a, b);
            throw new AssertionError("Event types don't match. Event number " + index);
        }
        for (ValueDescriptor field : a.getEventType().getFields()) {
            String n = field.getName();
            if (!isEqual(a.getValue(n), b.getValue(n))) {
                printRecordedObjects(a, b);
                throw new AssertionError("Events don't match. Event number " + index);
            }
        }
    }

    private static void printRecordedObjects(RecordedObject a, RecordedObject b) {
        System.out.println("Object A:");
        System.out.println(a);
        System.out.println("Object B:");
        System.out.println(b);
    }

    private static boolean isEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            System.out.println("One value null");
            System.out.println("Value A: " + a);
            System.out.println("Value B: " + b);
            return false;
        }
        if (a.getClass() != b.getClass()) {
            System.out.println("Not same class");
            return false;
        }
        if (a instanceof Double d1 && b instanceof Double d2) {
            return Double.doubleToRawLongBits(d1) == Double.doubleToRawLongBits(d2);
        }
        if (a instanceof Float f1 && b instanceof Float f2) {
            return Float.floatToRawIntBits(f1) == Float.floatToRawIntBits(f2);
        }
        if (a instanceof String || a instanceof Number || a instanceof Boolean) {
            return Objects.equals(a, b);
        }
        // Thread name may change, so sufficient to compare ID
        if (a instanceof RecordedThread t1 && b instanceof RecordedThread t2) {
            return t1.getId() == t2.getId();
        }
        if (a instanceof RecordedObject r1 && b instanceof RecordedObject r2) {
            for (ValueDescriptor field : r1.getFields()) {
                String n = field.getName();
                if (!isEqual(r1.getValue(n), r2.getValue(n))) {
                    System.out.println("Field " + n + " doesn't match");
                    System.out.println("Value A: " + r1.getValue(n));
                    System.out.println("Value B: " + r2.getValue(n));
                    return false;
                }
            }
            return true;
        }
        if (a.getClass().isArray()) {
            Object[] array = (Object[]) a;
            Object[] brray = (Object[]) b;
            if (array.length != brray.length) {
                System.out.println("Array size doesn't match");
                return false;
            }
            for (int i = 0; i < array.length; i++) {
                if (!isEqual(array[i], brray[i])) {
                    System.out.println("Array contents doesn't match");
                    return false;
                }
            }
            return true;
        }
        throw new AssertionError("Unknown object type " + a.getClass() + " found");
    }

    public static void disassemble(Path file, Path output) throws Throwable {
        JDKToolLauncher l = JDKToolLauncher.createUsingTestJDK("jfr");
        l.addToolArg("disassemble");
        l.addToolArg("--output");
        l.addToolArg(output.toAbsolutePath().toString());
        l.addToolArg("--max-chunks");
        l.addToolArg("1");
        l.addToolArg(file.toAbsolutePath().toString());
        ProcessTools.executeCommand(l.getCommand());
    }

    static class MyClass {
    }

    static class ClassStressor extends StressThread {
        @Name("Clazz")
        static class ClassEvent extends Event {
            Class<?> clazz;
        }

        @Override
        protected void stress() throws Exception {
            TestClassLoader loader = new TestClassLoader();
            Class<?> clazz = loader.loadClass(MyClass.class.getName());
            if (clazz == null) {
                throw new AssertionError("No class generated");
            }
            ClassEvent e = new ClassEvent();
            e.clazz = clazz;
            e.commit();
        }
    }

    static class ThreadStressor extends StressThread {
        @Name("Thread")
        static class ThreadEvent extends Event {
            Thread thread;
        }

        @Override
        protected void stress() throws Exception {
            Thread t = new Thread(() -> {
                ThreadEvent e = new ThreadEvent();
                e.thread = this;
                e.commit();
            });
            t.start();
            t.join();
        }
    }

    static class StringStressor extends StressThread {
        @Name("String")
        static class StringEvent extends Event {
            String string;
        }

        private long counter = 0;

        @Override
        protected void stress() throws Exception {
            String text = String.valueOf(counter) + "012345678901234567890";
            // Repeat string so characters are stored in check point event
            for (int i = 0; i < 10; i++) {
                StringEvent e = new StringEvent();
                e.string = text;
                e.commit();
            }
            counter++;
            Thread.sleep(1);
        }
    }
}
