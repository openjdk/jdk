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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.lang.System.LoggerFinder;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * @test
 * @bug 8140364
 * @summary Tests the default implementation of System.Logger, when
 *          JUL is the default backend.
 * @modules java.logging
 * @build AccessSystemLogger DefaultLoggerFinderTest
 * @run driver AccessSystemLogger
 * @run main/othervm -Xbootclasspath/a:boot DefaultLoggerFinderTest
 */
public class DefaultLoggerFinderTest {

    final static AtomicLong sequencer = new AtomicLong();
    final static boolean VERBOSE = false;

    static final AccessSystemLogger accessSystemLogger = new AccessSystemLogger();

    public static final Queue<LogEvent> eventQueue = new ArrayBlockingQueue<>(128);

    public static final class LogEvent {

        public LogEvent() {
            this(sequencer.getAndIncrement());
        }

        LogEvent(long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
        }

        long sequenceNumber;
        boolean isLoggable;
        String loggerName;
        java.util.logging.Level level;
        ResourceBundle bundle;
        Throwable thrown;
        Object[] args;
        String msg;
        String className;
        String methodName;

        Object[] toArray() {
            return new Object[] {
                sequenceNumber,
                isLoggable,
                loggerName,
                level,
                bundle,
                thrown,
                args,
                msg,
                className,
                methodName,
            };
        }

        @Override
        public String toString() {
            return Arrays.deepToString(toArray());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof LogEvent
                    && Objects.deepEquals(this.toArray(), ((LogEvent)obj).toArray());
        }

        @Override
        public int hashCode() {
            return Objects.hash(toArray());
        }
        public static LogEvent of(long sequenceNumber,
                boolean isLoggable, String name,
                java.util.logging.Level level, ResourceBundle bundle,
                String key, Throwable thrown, Object... params) {
            return LogEvent.of(sequenceNumber, isLoggable, name,
                    DefaultLoggerFinderTest.class.getName(),
                    "testLogger", level, bundle, key,
                    thrown, params);
        }
        public static LogEvent of(long sequenceNumber,
                boolean isLoggable, String name,
                String className, String methodName,
                java.util.logging.Level level, ResourceBundle bundle,
                String key, Throwable thrown, Object... params) {
            LogEvent evt = new LogEvent(sequenceNumber);
            evt.loggerName = name;
            evt.level = level;
            evt.args = params;
            evt.bundle = bundle;
            evt.thrown = thrown;
            evt.msg = key;
            evt.isLoggable = isLoggable;
            evt.className = className;
            evt.methodName = methodName;
            return evt;
        }
    }

    static java.util.logging.Level mapToJul(Level level) {
        switch (level) {
            case ALL: return java.util.logging.Level.ALL;
            case TRACE: return java.util.logging.Level.FINER;
            case DEBUG: return java.util.logging.Level.FINE;
            case INFO: return java.util.logging.Level.INFO;
            case WARNING: return java.util.logging.Level.WARNING;
            case ERROR: return java.util.logging.Level.SEVERE;
            case OFF: return java.util.logging.Level.OFF;
        }
        throw new InternalError("No such level: " + level);
    }

    static final java.util.logging.Level[] julLevels = {
        java.util.logging.Level.ALL,
        new java.util.logging.Level("FINER_THAN_FINEST", java.util.logging.Level.FINEST.intValue() - 10) {},
        java.util.logging.Level.FINEST,
        new java.util.logging.Level("FINER_THAN_FINER", java.util.logging.Level.FINER.intValue() - 10) {},
        java.util.logging.Level.FINER,
        new java.util.logging.Level("FINER_THAN_FINE", java.util.logging.Level.FINE.intValue() - 10) {},
        java.util.logging.Level.FINE,
        new java.util.logging.Level("FINER_THAN_CONFIG", java.util.logging.Level.FINE.intValue() + 10) {},
        java.util.logging.Level.CONFIG,
        new java.util.logging.Level("FINER_THAN_INFO", java.util.logging.Level.INFO.intValue() - 10) {},
        java.util.logging.Level.INFO,
        new java.util.logging.Level("FINER_THAN_WARNING", java.util.logging.Level.INFO.intValue() + 10) {},
        java.util.logging.Level.WARNING,
        new java.util.logging.Level("FINER_THAN_SEVERE", java.util.logging.Level.SEVERE.intValue() - 10) {},
        java.util.logging.Level.SEVERE,
        new java.util.logging.Level("FATAL", java.util.logging.Level.SEVERE.intValue() + 10) {},
        java.util.logging.Level.OFF,
    };

    static final Level[] mappedLevels = {
        Level.ALL,     // ALL
        Level.DEBUG,   // FINER_THAN_FINEST
        Level.DEBUG,   // FINEST
        Level.DEBUG,   // FINER_THAN_FINER
        Level.TRACE,   // FINER
        Level.TRACE,   // FINER_THAN_FINE
        Level.DEBUG,   // FINE
        Level.DEBUG,   // FINER_THAN_CONFIG
        Level.DEBUG,   // CONFIG
        Level.DEBUG,   // FINER_THAN_INFO
        Level.INFO,    // INFO
        Level.INFO,    // FINER_THAN_WARNING
        Level.WARNING, // WARNING
        Level.WARNING, // FINER_THAN_SEVERE
        Level.ERROR,   // SEVERE
        Level.ERROR,   // FATAL
        Level.OFF,     // OFF
    };

    final static Map<java.util.logging.Level, Level> julToSpiMap;
    static {
        Map<java.util.logging.Level, Level> map = new HashMap<>();
        if (mappedLevels.length != julLevels.length) {
            throw new ExceptionInInitializerError("Array lengths differ"
                + "\n\tjulLevels=" + Arrays.deepToString(julLevels)
                + "\n\tmappedLevels=" + Arrays.deepToString(mappedLevels));
        }
        for (int i=0; i<julLevels.length; i++) {
            map.put(julLevels[i], mappedLevels[i]);
        }
        julToSpiMap = Collections.unmodifiableMap(map);
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

    public static class MyHandler extends Handler {

        @Override
        public java.util.logging.Level getLevel() {
            return java.util.logging.Level.ALL;
        }

        @Override
        public void publish(LogRecord record) {
            eventQueue.add(LogEvent.of(sequencer.getAndIncrement(),
                    true, record.getLoggerName(),
                    record.getSourceClassName(),
                    record.getSourceMethodName(),
                    record.getLevel(),
                    record.getResourceBundle(), record.getMessage(),
                    record.getThrown(), record.getParameters()));
        }
        @Override
        public void flush() {
        }
        @Override
        public void close() throws SecurityException {
        }

    }

    public static class MyLoggerBundle extends MyBundle {

    }


    public static void main(String[] args) {

        final java.util.logging.Logger appSink = java.util.logging.Logger.getLogger("foo");
        final java.util.logging.Logger sysSink = accessSystemLogger.demandSystemLogger("foo");
        final java.util.logging.Logger sink = java.util.logging.Logger.getLogger("foo");
        sink.addHandler(new MyHandler());
        sink.setUseParentHandlers(VERBOSE);

        System.out.println("\n*** Running test\n");
        LoggerFinder provider = LoggerFinder.getLoggerFinder();
        test(provider, appSink, sysSink);

        System.out.println("\nPASSED: Tested " + sequencer.get() + " cases.");
    }

    public static void test(LoggerFinder provider,
            java.util.logging.Logger appSink,
            java.util.logging.Logger sysSink) {

        ResourceBundle loggerBundle = ResourceBundle.getBundle(MyLoggerBundle.class.getName());
        final Map<Logger, String> loggerDescMap = new HashMap<>();


        Logger appLogger1 = provider.getLogger("foo", DefaultLoggerFinderTest.class.getModule());
        loggerDescMap.put(appLogger1, "provider.getLogger(\"foo\", DefaultLoggerFinderTest.class.getModule())");

        Logger sysLogger1 = provider.getLogger("foo", Thread.class.getModule());
        loggerDescMap.put(sysLogger1, "provider.getLogger(\"foo\", Thread.class.getModule())");

        if (appLogger1 == sysLogger1) {
            throw new RuntimeException("identical loggers");
        }

        Logger appLogger2 = provider.getLocalizedLogger("foo", loggerBundle, DefaultLoggerFinderTest.class.getModule());
        loggerDescMap.put(appLogger2, "provider.getLocalizedLogger(\"foo\", loggerBundle, DefaultLoggerFinderTest.class.getModule())");

        Logger sysLogger2 = provider.getLocalizedLogger("foo", loggerBundle, Thread.class.getModule());
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


        testLogger(provider, loggerDescMap, "foo", null, appLogger1, appSink);
        testLogger(provider, loggerDescMap, "foo", null, sysLogger1, sysSink);
        testLogger(provider, loggerDescMap, "foo", loggerBundle, appLogger2, appSink);
        testLogger(provider, loggerDescMap, "foo", loggerBundle, sysLogger2, sysSink);


        Logger appLogger3 = System.getLogger("foo");
        loggerDescMap.put(appLogger3, "System.getLogger(\"foo\")");

        testLogger(provider, loggerDescMap, "foo", null, appLogger3, appSink);

        Logger appLogger4 =
                System.getLogger("foo", loggerBundle);
        loggerDescMap.put(appLogger4, "System.getLogger(\"foo\", loggerBundle)");

        if (appLogger4 == appLogger1) {
            throw new RuntimeException("identical loggers");
        }

        testLogger(provider, loggerDescMap, "foo", loggerBundle, appLogger4, appSink);

        Logger sysLogger3 = accessSystemLogger.getLogger("foo");
        loggerDescMap.put(sysLogger3, "AccessSystemLogger.getLogger(\"foo\")");

        testLogger(provider, loggerDescMap, "foo", null, sysLogger3, sysSink);

        Logger sysLogger4 =
                accessSystemLogger.getLogger("foo", loggerBundle);
        loggerDescMap.put(appLogger4, "AccessSystemLogger.getLogger(\"foo\", loggerBundle)");

        if (sysLogger4 == sysLogger1) {
            throw new RuntimeException("identical loggers");
        }

        testLogger(provider, loggerDescMap, "foo", loggerBundle, sysLogger4, sysSink);

    }

    public static class Foo {

    }

    static void verbose(String msg) {
       if (VERBOSE) {
           System.out.println(msg);
       }
    }

    static void setLevel(java.util.logging.Logger sink, java.util.logging.Level loggerLevel) {
        sink.setLevel(loggerLevel);
    }


    // Calls the 8 methods defined on Logger and verify the
    // parameters received by the underlying Logger Impl
    // logger.
    private static void testLogger(LoggerFinder provider,
            Map<Logger, String> loggerDescMap,
            String name,
            ResourceBundle loggerBundle,
            Logger logger,
            java.util.logging.Logger sink) {

        System.out.println("Testing " + loggerDescMap.get(logger) + " [" + logger + "]");
        final java.util.logging.Level OFF = java.util.logging.Level.OFF;

        Foo foo = new Foo();
        String fooMsg = foo.toString();
        for (java.util.logging.Level loggerLevel : julLevels) {
            setLevel(sink, loggerLevel);
            for (Level messageLevel : Level.values()) {
                java.util.logging.Level julLevel = mapToJul(messageLevel);
                String desc = "logger.log(messageLevel, foo): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            julLevel.intValue() >= loggerLevel.intValue(),
                            name, julLevel, (ResourceBundle)null,
                            fooMsg, (Throwable)null, (Object[])null);
                logger.log(messageLevel, foo);
                if (loggerLevel == OFF || julLevel.intValue() < loggerLevel.intValue()) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual =  eventQueue.poll();
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
        for (java.util.logging.Level loggerLevel : julLevels) {
            setLevel(sink, loggerLevel);
            for (Level messageLevel : Level.values()) {
                java.util.logging.Level julLevel = mapToJul(messageLevel);
                String desc = "logger.log(messageLevel, \"blah\"): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            julLevel.intValue() >= loggerLevel.intValue(),
                            name, julLevel, loggerBundle,
                            msg, (Throwable)null, (Object[])null);
                logger.log(messageLevel, msg);
                if (loggerLevel == OFF || julLevel.intValue() < loggerLevel.intValue()) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual =  eventQueue.poll();
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

        Supplier<String> fooSupplier = new Supplier<String>() {
            @Override
            public String get() {
                return this.toString();
            }
        };

        for (java.util.logging.Level loggerLevel : julLevels) {
            setLevel(sink, loggerLevel);
            for (Level messageLevel : Level.values()) {
                java.util.logging.Level julLevel = mapToJul(messageLevel);
                String desc = "logger.log(messageLevel, fooSupplier): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            julLevel.intValue() >= loggerLevel.intValue(),
                            name, julLevel, (ResourceBundle)null,
                            fooSupplier.get(),
                            (Throwable)null, (Object[])null);
                logger.log(messageLevel, fooSupplier);
                if (loggerLevel == OFF || julLevel.intValue() < loggerLevel.intValue()) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual =  eventQueue.poll();
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
        for (java.util.logging.Level loggerLevel : julLevels) {
            setLevel(sink, loggerLevel);
            for (Level messageLevel : Level.values()) {
                java.util.logging.Level julLevel = mapToJul(messageLevel);
                String desc = "logger.log(messageLevel, format, params...): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            julLevel.intValue() >= loggerLevel.intValue(),
                            name, julLevel, loggerBundle,
                            format, (Throwable)null, new Object[] {arg1, arg2});
                logger.log(messageLevel, format, arg1, arg2);
                if (loggerLevel == OFF || julLevel.intValue() < loggerLevel.intValue()) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual =  eventQueue.poll();
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

        Throwable thrown = new Exception("OK: log me!");
        for (java.util.logging.Level loggerLevel : julLevels) {
            setLevel(sink, loggerLevel);
            for (Level messageLevel : Level.values()) {
                java.util.logging.Level julLevel = mapToJul(messageLevel);
                String desc = "logger.log(messageLevel, \"blah\", thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            julLevel.intValue() >= loggerLevel.intValue(),
                            name, julLevel, loggerBundle,
                            msg, thrown, (Object[]) null);
                logger.log(messageLevel, msg, thrown);
                if (loggerLevel == OFF || julLevel.intValue() < loggerLevel.intValue()) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual =  eventQueue.poll();
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


        for (java.util.logging.Level loggerLevel : julLevels) {
            setLevel(sink, loggerLevel);
            for (Level messageLevel : Level.values()) {
                java.util.logging.Level julLevel = mapToJul(messageLevel);
                String desc = "logger.log(messageLevel, thrown, fooSupplier): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            julLevel.intValue() >= loggerLevel.intValue(),
                            name, julLevel, (ResourceBundle)null,
                            fooSupplier.get(),
                            (Throwable)thrown, (Object[])null);
                logger.log(messageLevel, fooSupplier, thrown);
                if (loggerLevel == OFF || julLevel.intValue() < loggerLevel.intValue()) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual =  eventQueue.poll();
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
        for (java.util.logging.Level loggerLevel : julLevels) {
            setLevel(sink, loggerLevel);
            for (Level messageLevel : Level.values()) {
                java.util.logging.Level julLevel = mapToJul(messageLevel);
                String desc = "logger.log(messageLevel, bundle, format, params...): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            julLevel.intValue() >= loggerLevel.intValue(),
                            name, julLevel, bundle,
                            format, (Throwable)null, new Object[] {foo, msg});
                logger.log(messageLevel, bundle, format, foo, msg);
                if (loggerLevel == OFF || julLevel.intValue() < loggerLevel.intValue()) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual =  eventQueue.poll();
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

        for (java.util.logging.Level loggerLevel : julLevels) {
            setLevel(sink, loggerLevel);
            for (Level messageLevel : Level.values()) {
                java.util.logging.Level julLevel = mapToJul(messageLevel);
                String desc = "logger.log(messageLevel, bundle, \"blah\", thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            julLevel.intValue() >= loggerLevel.intValue(),
                            name, julLevel, bundle,
                            msg, thrown, (Object[]) null);
                logger.log(messageLevel, bundle, msg, thrown);
                if (loggerLevel == OFF || julLevel.intValue() < loggerLevel.intValue()) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual =  eventQueue.poll();
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
}
