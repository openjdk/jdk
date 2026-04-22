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
import sun.util.logging.PlatformLogger;

/**
 * @test
 * @bug 8140364
 * @summary JDK implementation specific unit test for JDK internal API.
 *   Tests a naive implementation of System.Logger, and in particular
 *   the default mapping provided by PlatformLogger.
 * @modules java.base/sun.util.logging
 * @build CustomSystemClassLoader BaseLoggerFinder BasePlatformLoggerTest
 * @run main/othervm -Djava.system.class.loader=CustomSystemClassLoader BasePlatformLoggerTest
 */
public class BasePlatformLoggerTest {

    public static final RuntimePermission LOGGERFINDER_PERMISSION =
                new RuntimePermission("loggerFinder");

    final static AtomicLong sequencer = new AtomicLong();
    final static boolean VERBOSE = false;
    static final Class<?> providerClass;
    static {
        try {
            providerClass = ClassLoader.getSystemClassLoader().loadClass("BaseLoggerFinder");
        } catch (ClassNotFoundException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static final PlatformLogger.Level[] julLevels = {
        PlatformLogger.Level.ALL,
        PlatformLogger.Level.FINEST,
        PlatformLogger.Level.FINER,
        PlatformLogger.Level.FINE,
        PlatformLogger.Level.CONFIG,
        PlatformLogger.Level.INFO,
        PlatformLogger.Level.WARNING,
        PlatformLogger.Level.SEVERE,
        PlatformLogger.Level.OFF,
    };

    static final Level[] mappedLevels = {
        Level.ALL,     // ALL
        Level.TRACE,   // FINEST
        Level.TRACE,   // FINER
        Level.DEBUG,   // FINE
        Level.DEBUG,   // CONFIG
        Level.INFO,    // INFO
        Level.WARNING, // WARNING
        Level.ERROR,   // SEVERE
        Level.OFF,     // OFF
    };

    final static Map<PlatformLogger.Level, Level> julToSpiMap;
    static {
        Map<PlatformLogger.Level, Level> map = new HashMap<>();
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
    public static class MyLoggerBundle extends MyBundle {

    }


    public static interface TestLoggerFinder  {
        final ConcurrentHashMap<String, LoggerImpl> system = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, LoggerImpl> user = new ConcurrentHashMap<>();
        public Queue<LogEvent> eventQueue = new ArrayBlockingQueue<>(128);

        public static final class LogEvent implements Cloneable {

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

            public LogEvent cloneWith(long sequenceNumber)
                    throws CloneNotSupportedException {
                LogEvent cloned = (LogEvent)super.clone();
                cloned.sequenceNumber = sequenceNumber;
                return cloned;
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
                    Level level, Throwable thrown, Supplier<String> supplier) {
                LogEvent evt = new LogEvent();
                evt.isLoggable = isLoggable;
                evt.loggerName = name;
                evt.level = level;
                evt.args = null;
                evt.bundle = null;
                evt.thrown = thrown;
                evt.supplier = supplier;
                evt.msg = null;
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

            @Override
            public void log(Level level, Supplier<String> msgSupplier) {
                log(LogEvent.of(isLoggable(level), name, level, null, msgSupplier));
            }

            @Override
            public void log(Level level,  Supplier<String> msgSupplier, Throwable thrown) {
                log(LogEvent.of(isLoggable(level), name, level, thrown, msgSupplier));
            }
        }

        public Logger getLogger(String name, Module caller);
    }

    static PlatformLogger getPlatformLogger(String name) {
        return PlatformLogger.getLogger(name);
    }

    public static void main(String[] args) {
        System.out.println("\n*** Running test\n");
        TestLoggerFinder provider = TestLoggerFinder.class.cast(LoggerFinder.getLoggerFinder());
        test(provider);
        System.out.println("Tetscase count: " + sequencer.get());
        System.out.println("\nPASSED: Tested " + sequencer.get() + " cases.");
    }

    public static void test(TestLoggerFinder provider) {

        final Map<PlatformLogger, String> loggerDescMap = new HashMap<>();

        TestLoggerFinder.LoggerImpl appSink = TestLoggerFinder.LoggerImpl.class.cast(
                        provider.getLogger("foo", BasePlatformLoggerTest.class.getModule()));

        TestLoggerFinder.LoggerImpl sysSink = TestLoggerFinder.LoggerImpl.class.cast(
                        provider.getLogger("foo", Thread.class.getModule()));
        if (appSink == sysSink) {
            throw new RuntimeException("identical loggers");
        }

        if (provider.system.contains(appSink)) {
            throw new RuntimeException("app logger in system map");
        }
        if (!provider.user.contains(appSink)) {
            throw new RuntimeException("app logger not in appplication map");
        }
        if (provider.user.contains(sysSink)) {
            throw new RuntimeException("sys logger in appplication map");
        }
        if (!provider.system.contains(sysSink)) {
            throw new RuntimeException("sys logger not in system map");
        }

        PlatformLogger platform = getPlatformLogger("foo");
        loggerDescMap.put(platform, "PlatformLogger.getLogger(\"foo\")");

        testLogger(provider, loggerDescMap, "foo", null, platform, sysSink);
    }

    public static class Foo {

    }

    static void verbose(String msg) {
       if (VERBOSE) {
           System.out.println(msg);
       }
    }

    static void checkLogEvent(TestLoggerFinder provider, String desc,
            TestLoggerFinder.LogEvent expected) {
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

    static void checkLogEvent(TestLoggerFinder provider, String desc,
            TestLoggerFinder.LogEvent expected, boolean expectNotNull) {
        TestLoggerFinder.LogEvent actual =  provider.eventQueue.poll();
        if (actual == null && !expectNotNull) return;
        if (actual != null && !expectNotNull) {
            throw new RuntimeException("Unexpected log event found for " + desc
                + "\n\tgot: " + actual);
        }
        if (!expected.equals(actual)) {
            throw new RuntimeException("mismatch for " + desc
                    + "\n\texpected=" + expected
                    + "\n\t  actual=" + actual);
        } else {
            verbose("Got expected results for "
                    + desc + "\n\t" + expected);
        }
    }

    // Calls the methods defined on LogProducer and verify the
    // parameters received by the underlying TestLoggerFinder.LoggerImpl
    // logger.
    private static void testLogger(TestLoggerFinder provider,
            Map<PlatformLogger, String> loggerDescMap,
            String name,
            ResourceBundle loggerBundle,
            PlatformLogger logger,
            TestLoggerFinder.LoggerImpl sink) {

        System.out.println("Testing " + loggerDescMap.get(logger));

        Foo foo = new Foo();
        String fooMsg = foo.toString();
        System.out.println("\tlogger.<level>(fooMsg)");
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (PlatformLogger.Level messageLevel :julLevels) {
                Level expectedMessageLevel = julToSpiMap.get(messageLevel);
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            loggerLevel != Level.OFF && expectedMessageLevel.compareTo(loggerLevel) >= 0,
                            name, expectedMessageLevel, loggerBundle,
                            fooMsg, null, (Throwable)null, (Object[])null);
                String desc2 = "logger." + messageLevel.toString().toLowerCase()
                        + "(fooMsg): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                if (messageLevel == PlatformLogger.Level.FINEST) {
                    logger.finest(fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.FINER) {
                    logger.finer(fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.FINE) {
                    logger.fine(fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.CONFIG) {
                    logger.config(fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.INFO) {
                    logger.info(fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.WARNING) {
                    logger.warning(fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.SEVERE) {
                    logger.severe(fooMsg);
                    checkLogEvent(provider, desc2, expected);
                }
            }
        }

        Throwable thrown = new Exception("OK: log me!");
        System.out.println("\tlogger.<level>(msg, thrown)");
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (PlatformLogger.Level messageLevel :julLevels) {
                Level expectedMessageLevel = julToSpiMap.get(messageLevel);
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            loggerLevel != Level.OFF && expectedMessageLevel.compareTo(loggerLevel) >= 0,
                            name, expectedMessageLevel, (ResourceBundle) null,
                            fooMsg, null, (Throwable)thrown, (Object[])null);
                String desc2 = "logger." + messageLevel.toString().toLowerCase()
                        + "(msg, thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                if (messageLevel == PlatformLogger.Level.FINEST) {
                    logger.finest(fooMsg, thrown);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.FINER) {
                    logger.finer(fooMsg, thrown);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.FINE) {
                    logger.fine(fooMsg, thrown);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.CONFIG) {
                    logger.config(fooMsg, thrown);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.INFO) {
                    logger.info(fooMsg, thrown);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.WARNING) {
                    logger.warning(fooMsg, thrown);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.SEVERE) {
                    logger.severe(fooMsg, thrown);
                    checkLogEvent(provider, desc2, expected);
                }
            }
        }

        String format = "two params [{1} {2}]";
        Object arg1 = foo;
        Object arg2 = fooMsg;
        System.out.println("\tlogger.<level>(format, arg1, arg2)");
        for (Level loggerLevel : Level.values()) {
            sink.level = loggerLevel;
            for (PlatformLogger.Level messageLevel :julLevels) {
                Level expectedMessageLevel = julToSpiMap.get(messageLevel);
                TestLoggerFinder.LogEvent expected =
                        TestLoggerFinder.LogEvent.of(
                            sequencer.get(),
                            loggerLevel != Level.OFF && expectedMessageLevel.compareTo(loggerLevel) >= 0,
                            name, expectedMessageLevel, (ResourceBundle) null,
                            format, null, (Throwable)null, foo, fooMsg);
                String desc2 = "logger." + messageLevel.toString().toLowerCase()
                        + "(format, foo, fooMsg): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                if (messageLevel == PlatformLogger.Level.FINEST) {
                    logger.finest(format, foo, fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.FINER) {
                    logger.finer(format, foo, fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.FINE) {
                    logger.fine(format, foo, fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.CONFIG) {
                    logger.config(format, foo, fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.INFO) {
                    logger.info(format, foo, fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.WARNING) {
                    logger.warning(format, foo, fooMsg);
                    checkLogEvent(provider, desc2, expected);
                } else if (messageLevel == PlatformLogger.Level.SEVERE) {
                    logger.severe(format, foo, fooMsg);
                    checkLogEvent(provider, desc2, expected);
                }
            }
        }
    }
}
