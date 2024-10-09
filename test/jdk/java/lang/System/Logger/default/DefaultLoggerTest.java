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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.lang.System.LoggerFinder;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

/**
 * @test
 * @bug     8140364 8145686
 * @summary Tests default loggers returned by System.getLogger, and in
 *          particular the implementation of the System.Logger method
 *          performed by the default binding.
 * @modules java.logging
 * @build DefaultLoggerTest AccessSystemLogger
 * @run driver AccessSystemLogger
 * @run main/othervm -Xbootclasspath/a:boot DefaultLoggerTest DEFAULTS
 * @run main/othervm -Xbootclasspath/a:boot DefaultLoggerTest WITHCUSTOMWRAPPERS
 * @run main/othervm -Xbootclasspath/a:boot DefaultLoggerTest WITHREFLECTION
 */
public class DefaultLoggerTest {

    final static AtomicLong sequencer = new AtomicLong();
    final static boolean VERBOSE = false;

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
                    DefaultLoggerTest.class.getName(),
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

    static void setLevel(java.util.logging.Logger sink, java.util.logging.Level loggerLevel) {
        sink.setLevel(loggerLevel);
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

    static final AccessSystemLogger accessSystemLogger = new AccessSystemLogger();

    static enum TestCases {DEFAULTS, WITHCUSTOMWRAPPERS, WITHREFLECTION};

    /**
     * The CustomLoggerWrapper makes it possible to verify that classes
     * which implements System.Logger will be skipped when looking for
     * the calling method.
     */
    static class CustomLoggerWrapper implements Logger {

        Logger impl;
        public CustomLoggerWrapper(Logger logger) {
            this.impl = Objects.requireNonNull(logger);
        }


        @Override
        public String getName() {
            return impl.getName();
        }

        @Override
        public boolean isLoggable(Level level) {
            return impl.isLoggable(level);
        }

        @Override
        public void log(Level level, ResourceBundle rb, String string, Throwable thrwbl) {
            impl.log(level, rb, string, thrwbl);
        }

        @Override
        public void log(Level level, ResourceBundle rb, String string, Object... os) {
            impl.log(level, rb, string, os);
        }

        @Override
        public void log(Level level, Object o) {
            impl.log(level, o);
        }

        @Override
        public void log(Level level, String string) {
            impl.log(level, string);
        }

        @Override
        public void log(Level level, Supplier<String> splr) {
            impl.log(level, splr);
        }

        @Override
        public void log(Level level, String string, Object... os) {
           impl.log(level, string, os);
        }

        @Override
        public void log(Level level, String string, Throwable thrwbl) {
            impl.log(level, string, thrwbl);
        }

        @Override
        public void log(Level level, Supplier<String> splr, Throwable thrwbl) {
            Logger.super.log(level, splr, thrwbl);
        }

        @Override
        public String toString() {
            return super.toString() + "(impl=" + impl + ")";
        }

    }

    /**
     * The ReflectionLoggerWrapper additionally makes it possible to verify
     * that code which use reflection to call System.Logger will be skipped
     * when looking for the calling method.
     */
    static class ReflectionLoggerWrapper implements Logger {

        Logger impl;
        public ReflectionLoggerWrapper(Logger logger) {
            this.impl = Objects.requireNonNull(logger);
        }

        private Object invoke(Method m, Object... params) {
            try {
                return m.invoke(impl, params);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public String getName() {
            return impl.getName();
        }

        @Override
        public boolean isLoggable(Level level) {
            return impl.isLoggable(level);
        }

        @Override
        public void log(Level level, ResourceBundle rb, String string, Throwable thrwbl) {
            try {
                invoke(System.Logger.class.getMethod(
                        "log", Level.class, ResourceBundle.class, String.class, Throwable.class),
                        level, rb, string, thrwbl);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void log(Level level, ResourceBundle rb, String string, Object... os) {
            try {
                invoke(System.Logger.class.getMethod(
                        "log", Level.class, ResourceBundle.class, String.class, Object[].class),
                        level, rb, string, os);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void log(Level level, String string) {
            try {
                invoke(System.Logger.class.getMethod(
                        "log", Level.class, String.class),
                        level, string);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void log(Level level, String string, Object... os) {
            try {
                invoke(System.Logger.class.getMethod(
                        "log", Level.class, String.class, Object[].class),
                        level, string, os);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void log(Level level, String string, Throwable thrwbl) {
            try {
                invoke(System.Logger.class.getMethod(
                        "log", Level.class, String.class, Throwable.class),
                        level, string, thrwbl);
            } catch (NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            }
        }


        @Override
        public String toString() {
            return super.toString() + "(impl=" + impl + ")";
        }

    }

    public static void main(String[] args) {
        if (args.length == 0)
            args = new String[] {
                "DEFAULTS",
                "WITHCUSTOMWRAPPERS",
                "WITHREFLECTION"
            };

        // 1. Obtain destination loggers directly from the LoggerFinder
        //   - LoggerFinder.getLogger("foo", type)


        Stream.of(args).map(TestCases::valueOf).forEach((testCase) -> {
            switch (testCase) {
                case DEFAULTS:
                    System.out.println("\n*** Using defaults\n");
                    test();
                    System.out.println("Tetscase count: " + sequencer.get());
                    break;
                case WITHCUSTOMWRAPPERS:
                    System.out.println("\n*** Using custom Wrappers\n");
                    test(CustomLoggerWrapper::new);
                    System.out.println("Tetscase count: " + sequencer.get());
                    break;
                case WITHREFLECTION:
                    System.out.println("\n*** Using reflection while logging\n");
                    test(ReflectionLoggerWrapper::new);
                    System.out.println("Tetscase count: " + sequencer.get());
                    break;
                default:
                    throw new RuntimeException("Unknown test case: " + testCase);
            }
        });
        System.out.println("\nPASSED: Tested " + sequencer.get() + " cases.");
    }

    public static void test() {
        test(Function.identity());
    }

    public static void test(Function<Logger, Logger> wrapper) {

        ResourceBundle loggerBundle = ResourceBundle.getBundle(MyLoggerBundle.class.getName());
        final Map<Logger, String> loggerDescMap = new HashMap<>();


        // 1. Test loggers returned by:
        //   - System.getLogger("foo")
        //   - and AccessSystemLogger.getLogger("foo")
        Logger sysLogger1 = null;
        sysLogger1 = wrapper.apply(accessSystemLogger.getLogger("foo"));
        loggerDescMap.put(sysLogger1, "AccessSystemLogger.getLogger(\"foo\")");

        Logger appLogger1 = wrapper.apply(System.getLogger("foo"));
        loggerDescMap.put(appLogger1, "System.getLogger(\"foo\");");

        if (appLogger1 == sysLogger1) {
            throw new RuntimeException("identical loggers");
        }

        // 2. Test loggers returned by:
        //   - System.getLogger(\"foo\", loggerBundle)
        //   - and AccessSystemLogger.getLogger(\"foo\", loggerBundle)
        Logger appLogger2 = wrapper.apply(
                System.getLogger("foo", loggerBundle));
        loggerDescMap.put(appLogger2, "System.getLogger(\"foo\", loggerBundle)");

        Logger sysLogger2 = null;
        sysLogger2 = wrapper.apply(accessSystemLogger.getLogger("foo", loggerBundle));
        loggerDescMap.put(sysLogger2, "AccessSystemLogger.getLogger(\"foo\", loggerBundle)");

        if (appLogger2 == sysLogger2) {
            throw new RuntimeException("identical loggers");
        }

        final java.util.logging.Logger sink;
        final java.util.logging.Logger appSink;
        final java.util.logging.Logger sysSink;
        final java.util.logging.Handler appHandler;
        final java.util.logging.Handler sysHandler;
        final  LoggerFinder provider;

        appSink = java.util.logging.Logger.getLogger("foo");
        sysSink = accessSystemLogger.demandSystemLogger("foo");
        sink = java.util.logging.Logger.getLogger("foo");
        sink.addHandler(appHandler = sysHandler = new MyHandler());
        sink.setUseParentHandlers(false);
        provider = LoggerFinder.getLoggerFinder();

        try {
            testLogger(provider, loggerDescMap, "foo", null, sysLogger1, sysSink);
            testLogger(provider, loggerDescMap, "foo", null, appLogger1, appSink);
            testLogger(provider, loggerDescMap, "foo", loggerBundle, sysLogger2, sysSink);
            testLogger(provider, loggerDescMap, "foo", loggerBundle, appLogger2, appSink);
        } finally {
            appSink.removeHandler(appHandler);
            sysSink.removeHandler(sysHandler);
            sysSink.setLevel(null);
            appSink.setLevel(null);
        }
    }

    public static class Foo {

    }

    static void verbose(String msg) {
       if (VERBOSE) {
           System.out.println(msg);
       }
    }

    // Calls the 8 methods defined on Logger and verify the
    // parameters received by the underlying BaseLoggerFinder.LoggerImpl
    // logger.
    private static void testLogger(LoggerFinder provider,
            Map<Logger, String> loggerDescMap,
            String name,
            ResourceBundle loggerBundle,
            Logger logger,
            java.util.logging.Logger sink) {

        System.out.println("Testing " + loggerDescMap.get(logger));

        Foo foo = new Foo();
        String fooMsg = foo.toString();
        for (Level loggerLevel : Level.values()) {
            setLevel(sink, mapToJul(loggerLevel));
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, foo): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;

                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0,
                            name, mapToJul(messageLevel), (ResourceBundle)null,
                            fooMsg, (Throwable)null, (Object[])null);
                logger.log(messageLevel, foo);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (eventQueue.poll() != null) {
                        throw new RuntimeException("unexpected event in queue for " + desc);
                    }
                } else {
                    LogEvent actual = eventQueue.poll();
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
            setLevel(sink, mapToJul(loggerLevel));
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, \"blah\"): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, mapToJul(messageLevel), loggerBundle,
                            msg, (Throwable)null, (Object[])null);
                logger.log(messageLevel, msg);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
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

        for (Level loggerLevel : Level.values()) {
            setLevel(sink, mapToJul(loggerLevel));
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, fooSupplier): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0,
                            name, mapToJul(messageLevel), (ResourceBundle)null,
                            fooSupplier.get(),
                            (Throwable)null, (Object[])null);
                logger.log(messageLevel, fooSupplier);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
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
        for (Level loggerLevel : Level.values()) {
            setLevel(sink, mapToJul(loggerLevel));
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, format, params...): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, mapToJul(messageLevel), loggerBundle,
                            format, (Throwable)null, new Object[] {arg1, arg2});
                logger.log(messageLevel, format, arg1, arg2);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
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
        for (Level loggerLevel : Level.values()) {
            setLevel(sink, mapToJul(loggerLevel));
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, \"blah\", thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, mapToJul(messageLevel), loggerBundle,
                            msg, thrown, (Object[]) null);
                logger.log(messageLevel, msg, thrown);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
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


        for (Level loggerLevel : Level.values()) {
            setLevel(sink, mapToJul(loggerLevel));
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, thrown, fooSupplier): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0,
                            name, mapToJul(messageLevel), (ResourceBundle)null,
                            fooSupplier.get(),
                            (Throwable)thrown, (Object[])null);
                logger.log(messageLevel, fooSupplier, thrown);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
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
        for (Level loggerLevel : Level.values()) {
            setLevel(sink, mapToJul(loggerLevel));
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, bundle, format, params...): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, mapToJul(messageLevel), bundle,
                            format, (Throwable)null, new Object[] {foo, msg});
                logger.log(messageLevel, bundle, format, foo, msg);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
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

        for (Level loggerLevel : Level.values()) {
            setLevel(sink, mapToJul(loggerLevel));
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, bundle, \"blah\", thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                LogEvent expected =
                        LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, mapToJul(messageLevel), bundle,
                            msg, thrown, (Object[]) null);
                logger.log(messageLevel, bundle, msg, thrown);
                if (loggerLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
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
