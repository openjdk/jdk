/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.lang.System.LoggerFinder;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * @test
 * @bug 8140364
 * @summary Tests a naive implementation of LoggerFinder, and in particular
 *   the default body of System.Logger methods.
 * @build AccessSystemLogger BaseLoggerFinderTest CustomSystemClassLoader BaseLoggerFinder TestLoggerFinder
 * @run driver AccessSystemLogger
 * @run main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader BaseLoggerFinderTest
 */
public class BaseLoggerFinderTest {

    final static boolean VERBOSE = false;
    final static AccessSystemLogger accessSystemLogger = new AccessSystemLogger();
    static final Class<?> providerClass;
    static {
        try {
            providerClass = ClassLoader.getSystemClassLoader().loadClass("BaseLoggerFinder");
        } catch (ClassNotFoundException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static class MyBundle extends ResourceBundle {

        final ConcurrentHashMap<String,String> map = new ConcurrentHashMap<>();

        @Override
        protected Object handleGetObject(String key) {
            if (key.contains(" (translated)")) {
                throw new RuntimeException("Unexpected key: " + key);
            }
            return map.computeIfAbsent(key, k -> k + " (translated)");
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.enumeration(map.keySet());
        }

    }
    public static class MyLoggerBundle extends MyBundle {

    }


    public static void main(String[] args) {

        System.out.println("Using provider class: " + providerClass + "[" + providerClass.getClassLoader() + "]");

        TestLoggerFinder provider;
        System.out.println("\n*** Without Security Manager\n");
        provider = TestLoggerFinder.class.cast(LoggerFinder.getLoggerFinder());
        test(provider);
        System.out.println("Tetscase count: " + TestLoggerFinder.sequencer.get());
        System.out.println("\nPASSED: Tested " + TestLoggerFinder.sequencer.get() + " cases.");
    }

    public static void test(TestLoggerFinder provider) {

        ResourceBundle loggerBundle = ResourceBundle.getBundle(MyLoggerBundle.class.getName());
        final Map<Logger, String> loggerDescMap = new HashMap<>();


        // 1. Test loggers returned by LoggerFinder, both for system callers
        //    and not system callers.
        TestLoggerFinder.LoggerImpl appLogger1 = null;
        appLogger1 =
                TestLoggerFinder.LoggerImpl.class.cast(provider.getLogger("foo", BaseLoggerFinderTest.class.getModule()));
        loggerDescMap.put(appLogger1, "provider.getLogger(\"foo\", BaseLoggerFinderTest.class.getModule())");

        TestLoggerFinder.LoggerImpl sysLogger1 =
                TestLoggerFinder.LoggerImpl.class.cast(provider.getLogger("foo", Thread.class.getModule()));
        loggerDescMap.put(sysLogger1, "provider.getLogger(\"foo\", Thread.class.getModule())");

        if (appLogger1 == sysLogger1) {
            throw new RuntimeException("identical loggers");
        }

        if (provider.system.contains(appLogger1)) {
            throw new RuntimeException("app logger in system map");
        }
        if (!provider.user.contains(appLogger1)) {
            throw new RuntimeException("app logger not in appplication map");
        }
        if (provider.user.contains(sysLogger1)) {
            throw new RuntimeException("sys logger in appplication map");
        }
        if (!provider.system.contains(sysLogger1)) {
            throw new RuntimeException("sys logger not in system map");
        }

        testLogger(provider, loggerDescMap, "foo", null, appLogger1, appLogger1);
        testLogger(provider, loggerDescMap, "foo", null, sysLogger1, sysLogger1);

        // 2. Test localized loggers returned LoggerFinder, both for system
        //   callers and non system callers
        Logger appLogger2 =
                provider.getLocalizedLogger("foo", loggerBundle, BaseLoggerFinderTest.class.getModule());
        loggerDescMap.put(appLogger2, "provider.getLocalizedLogger(\"foo\", loggerBundle, BaseLoggerFinderTest.class.getModule())");

        Logger sysLogger2 =
                provider.getLocalizedLogger("foo", loggerBundle, Thread.class.getModule());
        loggerDescMap.put(sysLogger2, "provider.getLocalizedLogger(\"foo\", loggerBundle, Thread.class.getModule())");
        if (appLogger2 == sysLogger2) {
            throw new RuntimeException("identical loggers");
        }
        if (appLogger2 == appLogger1) {
            throw new RuntimeException("identical loggers");
        }
        if (sysLogger2 == sysLogger1) {
            throw new RuntimeException("identical loggers");
        }

        if (provider.system.contains(appLogger2)) {
            throw new RuntimeException("localized app logger in system map");
        }
        if (provider.user.contains(appLogger2)) {
            throw new RuntimeException("localized app logger  in appplication map");
        }
        if (provider.user.contains(sysLogger2)) {
            throw new RuntimeException("localized sys logger in appplication map");
        }
        if (provider.system.contains(sysLogger2)) {
            throw new RuntimeException("localized sys logger not in system map");
        }

        testLogger(provider, loggerDescMap, "foo", loggerBundle, appLogger2, appLogger1);
        testLogger(provider, loggerDescMap, "foo", loggerBundle, sysLogger2, sysLogger1);

        // 3 Test loggers returned by:
        //   3.1: System.getLogger("foo")
        Logger appLogger3 = System.getLogger("foo");
        loggerDescMap.put(appLogger3, "System.getLogger(\"foo\")");
        testLogger(provider, loggerDescMap, "foo", null, appLogger3, appLogger1);

        //   3.2: System.getLogger("foo")
        //        Emulate what System.getLogger() does when the caller is a
        //        platform classes
        Logger sysLogger3 = accessSystemLogger.getLogger("foo");
        loggerDescMap.put(sysLogger3, "AccessSystemLogger.getLogger(\"foo\")");

        if (appLogger3 == sysLogger3) {
            throw new RuntimeException("identical loggers");
        }

        testLogger(provider, loggerDescMap, "foo", null, sysLogger3, sysLogger1);

        // 4. Test loggers returned by:
        //    4.1 System.getLogger("foo", loggerBundle)
        Logger appLogger4 =
                System.getLogger("foo", loggerBundle);
        loggerDescMap.put(appLogger4, "System.getLogger(\"foo\", loggerBundle)");
        if (appLogger4 == appLogger1) {
            throw new RuntimeException("identical loggers");
        }

        testLogger(provider, loggerDescMap, "foo", loggerBundle, appLogger4, appLogger1);

        //   4.2: System.getLogger("foo", loggerBundle)
        //        Emulate what System.getLogger() does when the caller is a
        //        platform classes
        Logger sysLogger4 = accessSystemLogger.getLogger("foo", loggerBundle);
        loggerDescMap.put(sysLogger4, "AccessSystemLogger.getLogger(\"foo\", loggerBundle)");
        if (appLogger4 == sysLogger4) {
            throw new RuntimeException("identical loggers");
        }

        testLogger(provider, loggerDescMap, "foo", loggerBundle, sysLogger4, sysLogger1);

    }

    public static class Foo {

    }

    static void verbose(String msg) {
       if (VERBOSE) {
           System.out.println(msg);
       }
    }

    // Calls the 8 methods defined on Logger and verify the
    // parameters received by the underlying TestProvider.LoggerImpl
    // logger.
    private static void testLogger(TestLoggerFinder provider,
            Map<Logger, String> loggerDescMap,
            String name,
            ResourceBundle loggerBundle,
            Logger logger,
            TestLoggerFinder.LoggerImpl sink) {

        System.out.println("Testing " + loggerDescMap.get(logger) + " [" + logger +"]");
        AtomicLong sequencer = TestLoggerFinder.sequencer;

        Foo foo = new Foo();
        String fooMsg = foo.toString();
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, foo): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0,
                            name, messageLevel, (ResourceBundle)null,
                            fooMsg, null, (Throwable)null, (Object[])null);
                logger.log(messageLevel, foo);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (provider.eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
                    if (!expected.equals(actual)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected=" + expected
                                + "\n\t  actual=" + actual);
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n\t" + expected);
                    }
                }
            }
        }

        String msg = "blah";
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, \"blah\"): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, loggerBundle,
                            msg, null, (Throwable)null, (Object[])null);
                logger.log(messageLevel, msg);
                TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
                if (!expected.equals(actual)) {
                    throw new RuntimeException("mismatch for " + desc
                            + "\n\texpected=" + expected
                            + "\n\t  actual=" + actual);
                } else {
                    verbose("Got expected results for "
                            + desc + "\n\t" + expected);
                }
            }
        }

        Supplier<String> fooSupplier = new Supplier<String>() {
            @Override
            public String get() {
                return this.toString();
            }
        };

        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, fooSupplier): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0,
                            name, messageLevel, (ResourceBundle)null,
                            fooSupplier.get(), null,
                            (Throwable)null, (Object[])null);
                logger.log(messageLevel, fooSupplier);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (provider.eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
                    if (!expected.equals(actual)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected=" + expected
                                + "\n\t  actual=" + actual);
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n\t" + expected);
                    }
                }
            }
        }

        String format = "two params [{1} {2}]";
        Object arg1 = foo;
        Object arg2 = msg;
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, format, params...): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, loggerBundle,
                            format, null, (Throwable)null, new Object[] {foo, msg});
                logger.log(messageLevel, format, foo, msg);
                TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
                if (!expected.equals(actual)) {
                    throw new RuntimeException("mismatch for " + desc
                            + "\n\texpected=" + expected
                            + "\n\t  actual=" + actual);
                } else {
                    verbose("Got expected results for "
                            + desc + "\n\t" + expected);
                }
            }
        }

        Throwable thrown = new Exception("OK: log me!");
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, \"blah\", thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, loggerBundle,
                            msg, null, thrown, (Object[]) null);
                logger.log(messageLevel, msg, thrown);
                TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
                if (!expected.equals(actual)) {
                    throw new RuntimeException("mismatch for " + desc
                            + "\n\texpected=" + expected
                            + "\n\t  actual=" + actual);
                } else {
                    verbose("Got expected results for "
                            + desc + "\n\t" + expected);
                }
            }
        }


        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, thrown, fooSupplier): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0,
                            name, messageLevel, (ResourceBundle)null,
                            fooSupplier.get(), null,
                            (Throwable)thrown, (Object[])null);
                logger.log(messageLevel, fooSupplier, thrown);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (provider.eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
                    if (!expected.equals(actual)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected=" + expected
                                + "\n\t  actual=" + actual);
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n\t" + expected);
                    }
                }
            }
        }

        ResourceBundle bundle = ResourceBundle.getBundle(MyBundle.class.getName());
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, bundle, format, params...): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, bundle,
                            format, null, (Throwable)null, new Object[] {foo, msg});
                logger.log(messageLevel, bundle, format, foo, msg);
                TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
                if (!expected.equals(actual)) {
                    throw new RuntimeException("mismatch for " + desc
                            + "\n\texpected=" + expected
                            + "\n\t  actual=" + actual);
                } else {
                    verbose("Got expected results for "
                            + desc + "\n\t" + expected);
                }
            }
        }

        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, bundle, \"blah\", thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, bundle,
                            msg, null, thrown, (Object[]) null);
                logger.log(messageLevel, bundle, msg, thrown);
                TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
                if (!expected.equals(actual)) {
                    throw new RuntimeException("mismatch for " + desc
                            + "\n\texpected=" + expected
                            + "\n\t  actual=" + actual);
                } else {
                    verbose("Got expected results for "
                            + desc + "\n\t" + expected);
                }
            }
        }
    }

}
