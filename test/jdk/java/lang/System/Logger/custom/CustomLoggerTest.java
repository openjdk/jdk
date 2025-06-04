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
import java.lang.System.LoggerFinder;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * @test
 * @bug     8140364
 * @summary Tests loggers returned by System.getLogger with a naive implementation
 *          of LoggerFinder, and in particular the default body of
 *          System.Logger methods.
 * @build CustomLoggerTest AccessSystemLogger
 * @run driver AccessSystemLogger
 * @run main/othervm -Xbootclasspath/a:boot CustomLoggerTest
 * @author danielfuchs
 */
public class CustomLoggerTest {

    final static AtomicLong sequencer = new AtomicLong();
    final static boolean VERBOSE = false;

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


    public static class BaseLoggerFinder extends LoggerFinder {
        final ConcurrentHashMap<String, LoggerImpl> system = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, LoggerImpl> user = new ConcurrentHashMap<>();
        public Queue<LogEvent> eventQueue = new ArrayBlockingQueue<>(128);

        // changing this to true requires changing the logic in the
        // test in order to load this class with a protection domain
        // that has the CONTROL_PERMISSION (e.g. by using a custom
        // system class loader.
        final boolean doChecks = false;

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
            Level level;
            ResourceBundle bundle;
            Throwable thrown;
            Object[] args;
            Supplier<String> supplier;
            String msg;

            Object[] toArray() {
                return new Object[] {
                    sequenceNumber,
                    isLoggable,
                    loggerName,
                    level,
                    bundle,
                    thrown,
                    args,
                    supplier,
                    msg,
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


            public static LogEvent of(boolean isLoggable, String name,
                    Level level, ResourceBundle bundle,
                    String key, Throwable thrown) {
                LogEvent evt = new LogEvent();
                evt.isLoggable = isLoggable;
                evt.loggerName = name;
                evt.level = level;
                evt.args = null;
                evt.bundle = bundle;
                evt.thrown = thrown;
                evt.supplier = null;
                evt.msg = key;
                return evt;
            }

            public static LogEvent of(boolean isLoggable, String name,
                    Level level, ResourceBundle bundle,
                    String key, Object... params) {
                LogEvent evt = new LogEvent();
                evt.isLoggable = isLoggable;
                evt.loggerName = name;
                evt.level = level;
                evt.args = params;
                evt.bundle = bundle;
                evt.thrown = null;
                evt.supplier = null;
                evt.msg = key;
                return evt;
            }

            public static LogEvent of(long sequenceNumber,
                    boolean isLoggable, String name,
                    Level level, ResourceBundle bundle,
                    String key, Supplier<String> supplier,
                    Throwable thrown, Object... params) {
                LogEvent evt = new LogEvent(sequenceNumber);
                evt.loggerName = name;
                evt.level = level;
                evt.args = params;
                evt.bundle = bundle;
                evt.thrown = thrown;
                evt.supplier = supplier;
                evt.msg = key;
                evt.isLoggable = isLoggable;
                return evt;
            }

        }

        public class LoggerImpl implements Logger {
            private final String name;
            private Level level = Level.INFO;

            public LoggerImpl(String name) {
                this.name = name;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isLoggable(Level level) {
                return this.level != Level.OFF && this.level.getSeverity() <= level.getSeverity();
            }

            @Override
            public void log(Level level, ResourceBundle bundle, String key, Throwable thrown) {
                log(LogEvent.of(isLoggable(level), this.name, level, bundle, key, thrown));
            }

            @Override
            public void log(Level level, ResourceBundle bundle, String format, Object... params) {
                log(LogEvent.of(isLoggable(level), name, level, bundle, format, params));
            }

            void log(LogEvent event) {
                eventQueue.add(event);
            }
        }

        @Override
        public Logger getLogger(String name, Module caller) {

            final ClassLoader callerLoader = caller.getClassLoader();

            if (callerLoader == null) {
                return system.computeIfAbsent(name, (n) -> new LoggerImpl(n));
            } else {
                return user.computeIfAbsent(name, (n) -> new LoggerImpl(n));
            }
        }
    }

    static final AccessSystemLogger accessSystemLogger = new AccessSystemLogger();

    public static void main(String[] args) {

        // 1. Obtain destination loggers directly from the LoggerFinder
        //   - LoggerFinder.getLogger("foo", type)
        BaseLoggerFinder provider =
                BaseLoggerFinder.class.cast(LoggerFinder.getLoggerFinder());
        BaseLoggerFinder.LoggerImpl appSink =
                BaseLoggerFinder.LoggerImpl.class.cast(provider.getLogger("foo", CustomLoggerTest.class.getModule()));
        BaseLoggerFinder.LoggerImpl sysSink =
                BaseLoggerFinder.LoggerImpl.class.cast(provider.getLogger("foo", Thread.class.getModule()));
        test(provider, true, appSink, sysSink);
        System.out.println("\nPASSED: Tested " + sequencer.get() + " cases.");
    }

    public static void test(BaseLoggerFinder provider, boolean hasRequiredPermissions,
            BaseLoggerFinder.LoggerImpl appSink, BaseLoggerFinder.LoggerImpl sysSink) {

        ResourceBundle loggerBundle = ResourceBundle.getBundle(MyLoggerBundle.class.getName());
        final Map<Logger, String> loggerDescMap = new HashMap<>();


        // 1. Test loggers returned by:
        //   - System.getLogger("foo")
        //   - and AccessSystemLogger.getLogger("foo")
        Logger appLogger1 = System.getLogger("foo");
        loggerDescMap.put(appLogger1, "System.getLogger(\"foo\");");

        Logger sysLogger1 = null;
        sysLogger1 = accessSystemLogger.getLogger("foo");
        loggerDescMap.put(sysLogger1, "AccessSystemLogger.getLogger(\"foo\")");

        if (appLogger1 == sysLogger1) {
            throw new RuntimeException("identical loggers");
        }

        if (provider.system.contains(appLogger1)) {
            throw new RuntimeException("app logger in system map");
        }
        if (provider.user.contains(sysLogger1)) {
            throw new RuntimeException("sys logger in appplication map");
        }
        if (provider.system.contains(sysLogger1)) {
            // sysLogger should be a LazyLoggerWrapper
            throw new RuntimeException("sys logger is in system map (should be wrapped)");
        }


        // 2. Test loggers returned by:
        //   - System.getLogger(\"foo\", loggerBundle)
        //   - and AccessSystemLogger.getLogger(\"foo\", loggerBundle)
        Logger appLogger2 =
                System.getLogger("foo", loggerBundle);
        loggerDescMap.put(appLogger2, "System.getLogger(\"foo\", loggerBundle)");

        Logger sysLogger2 = null;
        sysLogger2 = accessSystemLogger.getLogger("foo", loggerBundle);
        loggerDescMap.put(sysLogger2, "AccessSystemLogger.getLogger(\"foo\", loggerBundle)");

        if (appLogger2 == sysLogger2) {
            throw new RuntimeException("identical loggers");
        }
        if (appLogger2 == appSink) {
            throw new RuntimeException("identical loggers");
        }
        if (sysLogger2 == sysSink) {
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

        testLogger(provider, loggerDescMap, "foo", null, appLogger1, appSink);
        testLogger(provider, loggerDescMap, "foo", null, sysLogger1, sysSink);
        testLogger(provider, loggerDescMap, "foo", loggerBundle, appLogger2, appSink);
        testLogger(provider, loggerDescMap, "foo", loggerBundle, sysLogger2, sysSink);
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
    private static void testLogger(BaseLoggerFinder provider,
            Map<Logger, String> loggerDescMap,
            String name,
            ResourceBundle loggerBundle,
            Logger logger,
            BaseLoggerFinder.LoggerImpl sink) {

        System.out.println("Testing " + loggerDescMap.get(logger));

        Foo foo = new Foo();
        String fooMsg = foo.toString();
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, foo): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                BaseLoggerFinder.LogEvent expected =
                        BaseLoggerFinder.LogEvent.of(
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
                    BaseLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
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
                BaseLoggerFinder.LogEvent expected =
                        BaseLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, loggerBundle,
                            msg, null, (Throwable)null, (Object[])null);
                logger.log(messageLevel, msg);
                BaseLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
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
                BaseLoggerFinder.LogEvent expected =
                        BaseLoggerFinder.LogEvent.of(
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
                    BaseLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
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
                BaseLoggerFinder.LogEvent expected =
                        BaseLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, loggerBundle,
                            format, null, (Throwable)null, new Object[] {arg1, arg2});
                logger.log(messageLevel, format, arg1, arg2);
                BaseLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
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
                BaseLoggerFinder.LogEvent expected =
                        BaseLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, loggerBundle,
                            msg, null, thrown, (Object[]) null);
                logger.log(messageLevel, msg, thrown);
                BaseLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
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
                BaseLoggerFinder.LogEvent expected =
                        BaseLoggerFinder.LogEvent.of(
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
                    BaseLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
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
                BaseLoggerFinder.LogEvent expected =
                        BaseLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, bundle,
                            format, null, (Throwable)null, new Object[] {foo, msg});
                logger.log(messageLevel, bundle, format, foo, msg);
                BaseLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
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
                BaseLoggerFinder.LogEvent expected =
                        BaseLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            messageLevel.compareTo(loggerLevel) >= 0 && loggerLevel != Level.OFF,
                            name, messageLevel, bundle,
                            msg, null, thrown, (Object[]) null);
                logger.log(messageLevel, bundle, msg, thrown);
                BaseLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
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
