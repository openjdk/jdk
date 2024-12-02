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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.lang.System.LoggerFinder;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @test
 * @bug 8140364 8189291 8283049
 * @summary JDK implementation specific unit test for LoggerFinderLoader.
 *          Tests the behavior of LoggerFinderLoader with respect to the
 *          value of the internal diagnosability switches. Also test the
 *          DefaultLoggerFinder and SimpleConsoleLogger implementation.
 * @modules java.base/sun.util.logging
 *          java.base/jdk.internal.logger
 * @build AccessSystemLogger LoggerFinderLoaderTest CustomSystemClassLoader BaseLoggerFinder BaseLoggerFinder2
 * @run  driver AccessSystemLogger
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader LoggerFinderLoaderTest
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader -Dtest.fails=true LoggerFinderLoaderTest
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader -Dtest.fails=true -Djdk.logger.finder.error=ERROR LoggerFinderLoaderTest
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader -Dtest.fails=true -Djdk.logger.finder.error=DEBUG LoggerFinderLoaderTest
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader -Dtest.fails=true -Djdk.logger.finder.error=QUIET LoggerFinderLoaderTest
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader -Djdk.logger.finder.singleton=true LoggerFinderLoaderTest
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader -Djdk.logger.finder.singleton=true -Djdk.logger.finder.error=ERROR LoggerFinderLoaderTest
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader -Djdk.logger.finder.singleton=true -Djdk.logger.finder.error=DEBUG LoggerFinderLoaderTest
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader -Djdk.logger.finder.singleton=true -Djdk.logger.finder.error=QUIET LoggerFinderLoaderTest
 */
public class LoggerFinderLoaderTest {

    final static boolean VERBOSE = false;
    final static AccessSystemLogger accessSystemLogger = new AccessSystemLogger();
    static final Class<?>[] providerClass;
    static {
        try {
            providerClass = new Class<?>[] {
                ClassLoader.getSystemClassLoader().loadClass("BaseLoggerFinder"),
                ClassLoader.getSystemClassLoader().loadClass("BaseLoggerFinder2")
            };
        } catch (ClassNotFoundException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    /**
     * What our test provider needs to implement.
     */
    public static interface TestLoggerFinder {
        public final static AtomicBoolean fails = new AtomicBoolean();
        public final static AtomicReference<String> conf = new AtomicReference<>("");
        public final static AtomicLong sequencer = new AtomicLong();
        public final ConcurrentHashMap<String, LoggerImpl> system = new ConcurrentHashMap<>();
        public final ConcurrentHashMap<String, LoggerImpl> user = new ConcurrentHashMap<>();

        public class LoggerImpl implements System.Logger {
            final String name;
            final Logger logger;

            public LoggerImpl(String name, Logger logger) {
                this.name = name;
                this.logger = logger;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isLoggable(Logger.Level level) {
                return logger.isLoggable(level);
            }

            @Override
            public void log(Logger.Level level, ResourceBundle bundle, String key, Throwable thrown) {
                logger.log(level, bundle, key, thrown);
            }

            @Override
            public void log(Logger.Level level, ResourceBundle bundle, String format, Object... params) {
                logger.log(level, bundle, format, params);
            }

        }

        public Logger getLogger(String name, Module caller);
        public Logger getLocalizedLogger(String name, ResourceBundle bundle, Module caller);
    }

    public static class MyBundle extends ResourceBundle {

        final ConcurrentHashMap<String,String> map = new ConcurrentHashMap<>();

        @Override
        protected Object handleGetObject(String key) {
            if (key.contains(" (translated)")) {
                throw new RuntimeException("Unexpected key: " + key);
            }
            return map.computeIfAbsent(key, k -> k.toUpperCase(Locale.ROOT) + " (translated)");
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.enumeration(map.keySet());
        }

    }
    public static class MyLoggerBundle extends MyBundle {

    }

    private static String withoutWarning(String in) {
        return in.lines().filter(s -> !s.startsWith("WARNING:")).collect(Collectors.joining());
    }

    static LoggerFinder getLoggerFinder(Class<?> expectedClass,
            String errorPolicy, boolean singleton) {
        LoggerFinder provider = null;
        try {
            TestLoggerFinder.sequencer.incrementAndGet();
            provider = LoggerFinder.getLoggerFinder();
            if (TestLoggerFinder.fails.get() || singleton) {
                if ("ERROR".equals(errorPolicy.toUpperCase(Locale.ROOT))) {
                    throw new RuntimeException("Expected exception not thrown");
                } else if ("WARNING".equals(errorPolicy.toUpperCase(Locale.ROOT))) {
                    String warning = ErrorStream.errorStream.peek();
                    if (!warning.contains("WARNING: Failed to instantiate LoggerFinder provider; Using default.")) {
                        throw new RuntimeException("Expected message not found. Error stream contained: " + warning);
                    }
                } else if ("DEBUG".equals(errorPolicy.toUpperCase(Locale.ROOT))) {
                    String warning = ErrorStream.errorStream.peek();
                    if (!warning.contains("WARNING: Failed to instantiate LoggerFinder provider; Using default.")) {
                        throw new RuntimeException("Expected message not found. Error stream contained: " + warning);
                    }
                    if (!warning.contains("WARNING: Exception raised trying to instantiate LoggerFinder")) {
                        throw new RuntimeException("Expected message not found. Error stream contained: " + warning);
                    }
                    if (TestLoggerFinder.fails.get()) {
                        if (!warning.contains("java.util.ServiceConfigurationError: java.lang.System$LoggerFinder: Provider BaseLoggerFinder could not be instantiated")) {
                            throw new RuntimeException("Expected message not found. Error stream contained: " + warning);
                        }
                    } else if (singleton) {
                        if (!warning.contains("java.util.ServiceConfigurationError: More than one LoggerFinder implementation")) {
                            throw new RuntimeException("Expected message not found. Error stream contained: " + warning);
                        }
                    }
                } else if ("QUIET".equals(errorPolicy.toUpperCase(Locale.ROOT))) {
                    String warning = ErrorStream.errorStream.peek();
                    warning = withoutWarning(warning);
                    if (!warning.isEmpty()) {
                        throw new RuntimeException("Unexpected error message found: "
                                + ErrorStream.errorStream.peek());
                    }
                }
            }
        } catch(Throwable t) {
            if (TestLoggerFinder.fails.get() || singleton) {
                // must check System.err
                if ("ERROR".equals(errorPolicy.toUpperCase(Locale.ROOT))) {
                    provider = LoggerFinder.getLoggerFinder();
                } else {
                    Throwable orig = t.getCause();
                    while (orig != null && orig.getCause() != null) orig = orig.getCause();
                    if (orig != null) orig.printStackTrace(ErrorStream.err);
                    throw new RuntimeException("Unexpected exception: " + t, t);
                }
            } else {
                throw new RuntimeException("Unexpected exception: " + t, t);
            }
        }
        expectedClass.cast(provider);
        ErrorStream.errorStream.store();
        System.out.println("*** Actual LoggerFinder class is: " + provider.getClass().getName());
        return provider;
    }


    static class ErrorStream extends PrintStream {

        static AtomicBoolean forward = new AtomicBoolean();
        ByteArrayOutputStream out;
        String saved = "";
        public ErrorStream(ByteArrayOutputStream out) {
            super(out);
            this.out = out;
        }

        @Override
        public void write(int b) {
            super.write(b);
            if (forward.get()) err.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            super.write(b);
            if (forward.get()) err.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);
            if (forward.get()) err.write(buf, off, len);
        }

        public String peek() {
            flush();
            return out.toString();
        }

        public String drain() {
            flush();
            String res = out.toString();
            out.reset();
            return res;
        }

        public void store() {
            flush();
            saved = out.toString();
            out.reset();
        }

        public void restore() {
            out.reset();
            try {
                out.write(saved.getBytes());
            } catch(IOException io) {
                throw new UncheckedIOException(io);
            }
        }

        static final PrintStream err = System.err;
        static final ErrorStream errorStream = new ErrorStream(new ByteArrayOutputStream());
    }

    private static StringBuilder appendProperty(StringBuilder b, String name) {
        String value = System.getProperty(name);
        if (value == null) return b;
        return b.append(name).append("=").append(value).append('\n');
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[] {
                "NOSECURITY",
                "NOPERMISSIONS",
                "WITHPERMISSIONS"
            };
        }
        Locale.setDefault(Locale.ENGLISH);
        System.setErr(ErrorStream.errorStream);
        System.setProperty("jdk.logger.packages", TestLoggerFinder.LoggerImpl.class.getName());
        //System.setProperty("jdk.logger.finder.error", "ERROR");
        //System.setProperty("jdk.logger.finder.singleton", "true");
        //System.setProperty("test.fails", "true");
        TestLoggerFinder.fails.set(Boolean.getBoolean("test.fails"));
        StringBuilder c = new StringBuilder();
        appendProperty(c, "jdk.logger.packages");
        appendProperty(c, "jdk.logger.finder.error");
        appendProperty(c, "jdk.logger.finder.singleton");
        appendProperty(c, "test.fails");
        TestLoggerFinder.conf.set(c.toString());
        try {
            test(args);
        } finally {
            try {
                System.setErr(ErrorStream.err);
            } catch (Error | RuntimeException x) {
                x.printStackTrace(ErrorStream.err);
            }
        }
    }


    public static void test(String[] args) {

        final String errorPolicy =  System.getProperty("jdk.logger.finder.error", "WARNING");
        final Boolean ensureSingleton = Boolean.getBoolean("jdk.logger.finder.singleton");

        final Class<?> expectedClass =
                TestLoggerFinder.fails.get() || ensureSingleton
                ? jdk.internal.logger.DefaultLoggerFinder.class
                : TestLoggerFinder.class;

        System.out.println("Declared provider class: " + providerClass[0]
                + "[" + providerClass[0].getClassLoader() + "]");

        if (!TestLoggerFinder.fails.get()) {
            ServiceLoader<LoggerFinder> serviceLoader =
                ServiceLoader.load(LoggerFinder.class, ClassLoader.getSystemClassLoader());
            Iterator<LoggerFinder> iterator = serviceLoader.iterator();
            Object firstProvider = iterator.next();
            if (!firstProvider.getClass().getName().equals("BaseLoggerFinder")) {
                throw new RuntimeException("Unexpected provider: " + firstProvider.getClass().getName());
            }
            if (!iterator.hasNext()) {
                throw new RuntimeException("Expected two providers");
            }
        }

        LoggerFinder provider;
        ErrorStream.errorStream.restore();
        System.out.println("\n*** Test starting\n");
        System.out.println(TestLoggerFinder.conf.get());
        provider = getLoggerFinder(expectedClass, errorPolicy, ensureSingleton);
        test(provider);
        System.out.println("Tetscase count: " + TestLoggerFinder.sequencer.get());
        System.out.println("\nPASSED: Tested " + TestLoggerFinder.sequencer.get() + " cases.");
    }

    public static void test(LoggerFinder provider) {

        ResourceBundle loggerBundle = ResourceBundle.getBundle(MyLoggerBundle.class.getName());
        final Map<Logger, String> loggerDescMap = new HashMap<>();

        System.Logger sysLogger = accessSystemLogger.getLogger("foo");
        loggerDescMap.put(sysLogger, "accessSystemLogger.getLogger(\"foo\")");
        System.Logger localizedSysLogger = accessSystemLogger.getLogger("fox", loggerBundle);
        loggerDescMap.put(localizedSysLogger, "accessSystemLogger.getLogger(\"fox\", loggerBundle)");
        System.Logger appLogger = System.getLogger("bar");
        loggerDescMap.put(appLogger,"System.getLogger(\"bar\")");
        System.Logger localizedAppLogger = System.getLogger("baz", loggerBundle);
        loggerDescMap.put(localizedAppLogger,"System.getLogger(\"baz\", loggerBundle)");

        testLogger(provider, loggerDescMap, "foo", null, sysLogger);
        testLogger(provider, loggerDescMap, "foo", loggerBundle, localizedSysLogger);
        testLogger(provider, loggerDescMap, "foo", null, appLogger);
        testLogger(provider, loggerDescMap, "foo", loggerBundle, localizedAppLogger);
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
    private static void testLogger(LoggerFinder provider,
            Map<Logger, String> loggerDescMap,
            String name,
            ResourceBundle loggerBundle,
            Logger logger) {

        System.out.println("Testing " + loggerDescMap.get(logger) + " [" + logger +"]");
        AtomicLong sequencer = TestLoggerFinder.sequencer;

        Foo foo = new Foo();
        String fooMsg = foo.toString();
        for (Level loggerLevel : EnumSet.of(Level.INFO)) {
            for (Level messageLevel : Level.values()) {
                ErrorStream.errorStream.drain();
                String desc = "logger.log(messageLevel, foo): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                sequencer.incrementAndGet();
                logger.log(messageLevel, foo);
                if (loggerLevel == Level.OFF || messageLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (!ErrorStream.errorStream.peek().isEmpty()) {
                        throw new RuntimeException("unexpected event in queue for "
                                + desc +": " + "\n\t" + ErrorStream.errorStream.drain());
                    }
                } else {
                    String logged = ErrorStream.errorStream.drain();
                    if (!logged.contains("LoggerFinderLoaderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + fooMsg)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] LoggerFinderLoaderTest testLogger\n"
                                + messageLevel.getName() + " " + fooMsg
                                + "\n>>>>"
                                + "\n\t  actual:"
                                + "\n<<<<\n" + logged + ">>>>\n");
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n<<<<\n" + logged + ">>>>\n");
                    }
                }
            }
        }

        String msg = "blah";
        for (Level loggerLevel : EnumSet.of(Level.INFO)) {
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, \"blah\"): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                sequencer.incrementAndGet();
                logger.log(messageLevel, msg);
                if (loggerLevel == Level.OFF || messageLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (!ErrorStream.errorStream.peek().isEmpty()) {
                        throw new RuntimeException("unexpected event in queue for "
                                + desc +": " + "\n\t" + ErrorStream.errorStream.drain());
                    }
                } else {
                    String logged = ErrorStream.errorStream.drain();
                    String msgText = loggerBundle == null ? msg : loggerBundle.getString(msg);
                    if (!logged.contains("LoggerFinderLoaderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + msgText)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] LoggerFinderLoaderTest testLogger\n"
                                + messageLevel.getName() + " " + msgText
                                + "\n>>>>"
                                + "\n\t  actual:"
                                + "\n<<<<\n" + logged + ">>>>\n");
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n<<<<\n" + logged + ">>>>\n");
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

        for (Level loggerLevel : EnumSet.of(Level.INFO)) {
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, fooSupplier): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                sequencer.incrementAndGet();
                logger.log(messageLevel, fooSupplier);
                if (loggerLevel == Level.OFF || messageLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (!ErrorStream.errorStream.peek().isEmpty()) {
                        throw new RuntimeException("unexpected event in queue for "
                                + desc +": " + "\n\t" + ErrorStream.errorStream.drain());
                    }
                } else {
                    String logged = ErrorStream.errorStream.drain();
                    if (!logged.contains("LoggerFinderLoaderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + fooSupplier.get())) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] LoggerFinderLoaderTest testLogger\n"
                                + messageLevel.getName() + " " + fooSupplier.get()
                                + "\n>>>>"
                                + "\n\t  actual:"
                                + "\n<<<<\n" + logged + ">>>>\n");
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n<<<<\n" + logged + ">>>>\n");
                    }
                }
            }
        }


        String format = "two params [{1} {2}]";
        Object arg1 = foo;
        Object arg2 = msg;
        for (Level loggerLevel : EnumSet.of(Level.INFO)) {
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, format, params...): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                sequencer.incrementAndGet();
                logger.log(messageLevel, format, foo, msg);
                if (loggerLevel == Level.OFF || messageLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (!ErrorStream.errorStream.peek().isEmpty()) {
                        throw new RuntimeException("unexpected event in queue for "
                                + desc +": " + "\n\t" + ErrorStream.errorStream.drain());
                    }
                } else {
                    String logged = ErrorStream.errorStream.drain();
                    String msgFormat = loggerBundle == null ? format : loggerBundle.getString(format);
                    String text = java.text.MessageFormat.format(msgFormat, foo, msg);
                    if (!logged.contains("LoggerFinderLoaderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] LoggerFinderLoaderTest testLogger\n"
                                + messageLevel.getName() + " " + text
                                + "\n>>>>"
                                + "\n\t  actual:"
                                + "\n<<<<\n" + logged + ">>>>\n");
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n<<<<\n" + logged + ">>>>\n");
                    }
                }
            }
        }

        Throwable thrown = new Exception("OK: log me!");
        for (Level loggerLevel : EnumSet.of(Level.INFO)) {
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, \"blah\", thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                sequencer.incrementAndGet();
                logger.log(messageLevel, msg, thrown);
                if (loggerLevel == Level.OFF || messageLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (!ErrorStream.errorStream.peek().isEmpty()) {
                        throw new RuntimeException("unexpected event in queue for "
                                + desc +": " + "\n\t" + ErrorStream.errorStream.drain());
                    }
                } else {
                    String logged = ErrorStream.errorStream.drain();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    thrown.printStackTrace(new PrintStream(baos));
                    String text = baos.toString();
                    String msgText = loggerBundle == null ? msg : loggerBundle.getString(msg);
                    if (!logged.contains("LoggerFinderLoaderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + msgText)
                        || !logged.contains(text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] LoggerFinderLoaderTest testLogger\n"
                                + messageLevel.getName() + " " + msgText +"\n"
                                + text
                                + ">>>>"
                                + "\n\t  actual:"
                                + "\n<<<<\n" + logged + ">>>>\n");
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n<<<<\n" + logged + ">>>>\n");
                    }
                }
            }
        }


        for (Level loggerLevel : EnumSet.of(Level.INFO)) {
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, thrown, fooSupplier): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                sequencer.incrementAndGet();
                logger.log(messageLevel, fooSupplier, thrown);
                if (loggerLevel == Level.OFF || messageLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (!ErrorStream.errorStream.peek().isEmpty()) {
                        throw new RuntimeException("unexpected event in queue for "
                                + desc +": " + "\n\t" + ErrorStream.errorStream.drain());
                    }
                } else {
                    String logged = ErrorStream.errorStream.drain();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    thrown.printStackTrace(new PrintStream(baos));
                    String text = baos.toString();
                    if (!logged.contains("LoggerFinderLoaderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + fooSupplier.get())
                        || !logged.contains(text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] LoggerFinderLoaderTest testLogger\n"
                                + messageLevel.getName() + " " + fooSupplier.get() +"\n"
                                + text
                                + ">>>>"
                                + "\n\t  actual:"
                                + "\n<<<<\n" + logged + ">>>>\n");
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n<<<<\n" + logged + ">>>>\n");
                    }
                }
            }
        }

        ResourceBundle bundle = ResourceBundle.getBundle(MyBundle.class.getName());
        for (Level loggerLevel : EnumSet.of(Level.INFO)) {
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, bundle, format, params...): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                sequencer.incrementAndGet();
                logger.log(messageLevel, bundle, format, foo, msg);
                if (loggerLevel == Level.OFF || messageLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (!ErrorStream.errorStream.peek().isEmpty()) {
                        throw new RuntimeException("unexpected event in queue for "
                                + desc +": " + "\n\t" + ErrorStream.errorStream.drain());
                    }
                } else {
                    String logged = ErrorStream.errorStream.drain();
                    String text = java.text.MessageFormat.format(bundle.getString(format), foo, msg);
                    if (!logged.contains("LoggerFinderLoaderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] LoggerFinderLoaderTest testLogger\n"
                                + messageLevel.getName() + " " + text
                                + "\n>>>>"
                                + "\n\t  actual:"
                                + "\n<<<<\n" + logged + ">>>>\n");
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n<<<<\n" + logged + ">>>>\n");
                    }
                }
            }
        }

        for (Level loggerLevel : EnumSet.of(Level.INFO)) {
            for (Level messageLevel : Level.values()) {
                String desc = "logger.log(messageLevel, bundle, \"blah\", thrown): loggerLevel="
                        + loggerLevel+", messageLevel="+messageLevel;
                sequencer.incrementAndGet();
                logger.log(messageLevel, bundle, msg, thrown);
                if (loggerLevel == Level.OFF || messageLevel == Level.OFF || messageLevel.compareTo(loggerLevel) < 0) {
                    if (!ErrorStream.errorStream.peek().isEmpty()) {
                        throw new RuntimeException("unexpected event in queue for "
                                + desc +": " + "\n\t" + ErrorStream.errorStream.drain());
                    }
                } else {
                    String logged = ErrorStream.errorStream.drain();
                    String textMsg = bundle.getString(msg);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    thrown.printStackTrace(new PrintStream(baos));
                    String text = baos.toString();
                    if (!logged.contains("LoggerFinderLoaderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + textMsg)
                        || !logged.contains(text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] LoggerFinderLoaderTest testLogger\n"
                                + messageLevel.getName() + " " + textMsg +"\n"
                                + text
                                + ">>>>"
                                + "\n\t  actual:"
                                + "\n<<<<\n" + logged + ">>>>\n");
                    } else {
                        verbose("Got expected results for "
                                + desc + "\n<<<<\n" + logged + ">>>>\n");
                    }
                }
            }
        }
    }
}
