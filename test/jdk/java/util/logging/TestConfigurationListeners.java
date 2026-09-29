/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.LogManager;

/**
 * @test
 * @bug 8043306
 * @summary tests LogManager.addConfigurationListener and
 *                LogManager.removeConfigurationListener;
 * @build TestConfigurationListeners
 * @run main/othervm TestConfigurationListeners
 * @author danielfuchs
 */
public class TestConfigurationListeners {

    // We will test add and remove ConfigurationListeners
    public static void main(String... args) throws Exception {
       test("foo.bar");
    }

    static class TestConfigurationListener implements Runnable {
        final AtomicLong  count = new AtomicLong(0);
        final String name;
        TestConfigurationListener(String name) {
            this.name = name;
        }
        @Override
        public void run() {
            final long times = count.incrementAndGet();
            System.out.println("Configured \"" + name + "\": " + times);
        }
    }

    static class ConfigurationListenerException extends RuntimeException {
        public ConfigurationListenerException(String msg) {
            super(msg);
        }

        @Override
        public String toString() {
            return this.getClass().getName() + ": " + getMessage();
        }
    }
    static class ConfigurationListenerError extends Error {
        public ConfigurationListenerError(String msg) {
            super(msg);
        }

        @Override
        public String toString() {
            return this.getClass().getName() + ": " + getMessage();
        }
    }

    static class ThrowingConfigurationListener extends TestConfigurationListener {

        final boolean error;
        public ThrowingConfigurationListener(String name, boolean error) {
            super(name);
            this.error = error;
        }

        @Override
        public void run() {
            if (error)
                throw new ConfigurationListenerError(name);
            else
                throw new ConfigurationListenerException(name);
        }

        @Override
        public String toString() {
            final Class<? extends Throwable> type =
                    error ? ConfigurationListenerError.class
                          : ConfigurationListenerException.class;
            return  type.getName()+ ": " + name;
        }

    }

    private static void expect(TestConfigurationListener listener, long value) {
        final long got = listener.count.longValue();
        if (got != value) {
            throw new RuntimeException(listener.name + " expected " + value +", got " + got);
        }

    }

    public interface ThrowingConsumer<T, I extends Exception> {
        public void accept(T t) throws I;
    }

    public static class ReadConfiguration implements ThrowingConsumer<LogManager, IOException> {

        @Override
        public void accept(LogManager t) throws IOException {
            t.readConfiguration();
        }

    }

    /**
     * Main test runner.
     * @param loggerName The logger to use.
     * @throws Exception if the test fails.
     */
    public static void test(String loggerName) throws Exception {
        System.out.println("Starting test for " + loggerName);
        test("m.readConfiguration()", (m) -> m.readConfiguration());
        test("m.readConfiguration(new ByteArrayInputStream(new byte[0]))",
                (m) -> m.readConfiguration(new ByteArrayInputStream(new byte[0])));
        System.out.println("Test passed for " + loggerName);
    }

    public static void test(String testName,
            ThrowingConsumer<LogManager, IOException> readConfiguration) throws Exception {


        System.out.println("\nBEGIN " + testName);
        LogManager m = LogManager.getLogManager();

        final TestConfigurationListener l1 = new TestConfigurationListener("l#1");
        final TestConfigurationListener l2 = new TestConfigurationListener("l#2");
        final TestConfigurationListener l3 = new ThrowingConfigurationListener("l#3", false);
        final TestConfigurationListener l4 = new ThrowingConfigurationListener("l#4", true);
        final TestConfigurationListener l5 = new ThrowingConfigurationListener("l#5", false);

        final Set<String> expectedExceptions =
                Collections.unmodifiableSet(
                        new HashSet<>(Arrays.asList(
                                l3.toString(), l4.toString(), l5.toString())));

        m.addConfigurationListener(l1);
        m.addConfigurationListener(l2);
        expect(l1, 0);
        expect(l2, 0);

        readConfiguration.accept(m);
        expect(l1, 1);
        expect(l2, 1);
        m.addConfigurationListener(l1);
        expect(l1, 1);
        expect(l2, 1);
        readConfiguration.accept(m);
        expect(l1, 2);
        expect(l2, 2);
        m.removeConfigurationListener(l1);
        expect(l1, 2);
        expect(l2, 2);
        readConfiguration.accept(m);
        expect(l1, 2);
        expect(l2, 3);
        m.removeConfigurationListener(l1);
        expect(l1, 2);
        expect(l2, 3);
        readConfiguration.accept(m);
        expect(l1, 2);
        expect(l2, 4);
        m.removeConfigurationListener(l2);
        expect(l1, 2);
        expect(l2, 4);
        readConfiguration.accept(m);
        expect(l1, 2);
        expect(l2, 4);

        // l1 and l2 should no longer be present: this should not fail...
        m.removeConfigurationListener(l1);
        m.removeConfigurationListener(l1);
        m.removeConfigurationListener(l2);
        m.removeConfigurationListener(l2);
        expect(l1, 2);
        expect(l2, 4);

        readConfiguration.accept(m);
        expect(l1, 2);
        expect(l2, 4);

        // add back l1 and l2
        m.addConfigurationListener(l1);
        m.addConfigurationListener(l2);
        expect(l1, 2);
        expect(l2, 4);

        readConfiguration.accept(m);
        expect(l1, 3);
        expect(l2, 5);

        m.removeConfigurationListener(l1);
        m.removeConfigurationListener(l2);
        expect(l1, 3);
        expect(l2, 5);

        readConfiguration.accept(m);
        expect(l1, 3);
        expect(l2, 5);

        // Check the behavior when listeners throw exceptions
        // l3, l4, and l5 will throw an error/exception.
        // The first that is raised will be propagated, after all listeners
        // have been invoked. The other exceptions will be added to the
        // suppressed list.
        //
        // We will check that all listeners have been invoked and that we
        // have the set of 3 exceptions expected from l3, l4, l5.
        //
        m.addConfigurationListener(l4);
        m.addConfigurationListener(l1);
        m.addConfigurationListener(l2);
        m.addConfigurationListener(l3);
        m.addConfigurationListener(l5);

        try {
            readConfiguration.accept(m);
            throw new RuntimeException("Excpected exception/error not raised");
        } catch(ConfigurationListenerException | ConfigurationListenerError t) {
            final Set<String> received = new HashSet<>();
            received.add(t.toString());
            for (Throwable s : t.getSuppressed()) {
                received.add(s.toString());
            }
            System.out.println("Received exceptions: " + received);
            if (!expectedExceptions.equals(received)) {
                throw new RuntimeException(
                        "List of received exceptions differs from expected:"
                                + "\n\texpected: " + expectedExceptions
                                + "\n\treceived: " + received);
            }
        }
        expect(l1, 4);
        expect(l2, 6);

        m.removeConfigurationListener(l1);
        m.removeConfigurationListener(l2);
        m.removeConfigurationListener(l3);
        m.removeConfigurationListener(l4);
        m.removeConfigurationListener(l5);
        readConfiguration.accept(m);
        expect(l1, 4);
        expect(l2, 6);


        try {
            m.addConfigurationListener(null);
            throw new RuntimeException(
                    "addConfigurationListener(null): Expected NPE not thrown.");
        } catch (NullPointerException npe) {
            System.out.println("Got expected NPE: "+npe);
        }

        try {
            m.removeConfigurationListener(null);
            throw new RuntimeException(
                    "removeConfigurationListener(null): Expected NPE not thrown.");
        } catch (NullPointerException npe) {
            System.out.println("Got expected NPE: "+npe);
        }

        System.out.println("END " + testName+"\n");

    }
}
