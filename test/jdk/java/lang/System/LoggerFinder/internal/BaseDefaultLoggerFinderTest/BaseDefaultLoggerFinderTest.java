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
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.lang.System.LoggerFinder;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import jdk.internal.logger.DefaultLoggerFinder;
import jdk.internal.logger.SimpleConsoleLogger;
import sun.util.logging.PlatformLogger;

/**
 * @test
 * @bug 8140364 8145686 8189291
 * @summary JDK implementation specific unit test for the base DefaultLoggerFinder.
 *          Tests the behavior of DefaultLoggerFinder and SimpleConsoleLogger
 *          implementation.
 * @modules java.base/sun.util.logging
 *          java.base/jdk.internal.logger
 * @build AccessSystemLogger BaseDefaultLoggerFinderTest CustomSystemClassLoader BaseLoggerFinder
 * @run  driver AccessSystemLogger
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader BaseDefaultLoggerFinderTest DEFAULTS
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader BaseDefaultLoggerFinderTest WITHCUSTOMWRAPPERS
 * @run  main/othervm -Xbootclasspath/a:boot -Djava.system.class.loader=CustomSystemClassLoader BaseDefaultLoggerFinderTest WITHREFLECTION
 * @author danielfuchs
 */
public class BaseDefaultLoggerFinderTest {

    final static boolean VERBOSE = false;

    final static AccessSystemLogger accessSystemLogger = new AccessSystemLogger();
    static final Class<?>[] providerClass;
    static {
        try {
            providerClass = new Class<?>[] {
                ClassLoader.getSystemClassLoader().loadClass("BaseLoggerFinder"),
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


        public Logger getLogger(String name, Module caller);
        public Logger getLocalizedLogger(String name, ResourceBundle bundle, Module caller);
        void setLevel(Logger logger, Level level, Module caller);
        void setLevel(Logger logger, PlatformLogger.Level level, Module caller);
        PlatformLogger.Bridge asPlatformLoggerBridge(Logger logger);
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

    static enum TestCases {DEFAULTS, WITHCUSTOMWRAPPERS, WITHREFLECTION};

    static TestLoggerFinder getLoggerFinder(Class<?> expectedClass) {
        TestLoggerFinder.sequencer.incrementAndGet();
        LoggerFinder provider = LoggerFinder.getLoggerFinder();
        ErrorStream.errorStream.store();
        System.out.println("*** Actual LoggerFinder class is: " + provider.getClass().getName());
        expectedClass.cast(provider);
        return TestLoggerFinder.class.cast(provider);
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
        if (args.length == 0) {
            args = new String[] {
                "DEFAULTS",
                "WITHCUSTOMWRAPPERS",
                "WITHREFLECTION"
            };
        }
        Locale.setDefault(Locale.ENGLISH);
        System.setErr(ErrorStream.errorStream);
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

        final Class<?> expectedClass = jdk.internal.logger.DefaultLoggerFinder.class;

        System.out.println("Declared provider class: " + providerClass[0]
                + "[" + providerClass[0].getClassLoader() + "]");

        Stream.of(args).map(TestCases::valueOf).forEach((testCase) -> {
            TestLoggerFinder provider;
            ErrorStream.errorStream.restore();
            switch (testCase) {
                case DEFAULTS:
                    System.out.println("\n*** Defaults\n");
                    System.out.println(TestLoggerFinder.conf.get());
                    provider = getLoggerFinder(expectedClass);
                    if (!provider.getClass().getName().equals("BaseLoggerFinder")) {
                        throw new RuntimeException("Unexpected provider: " + provider.getClass().getName());
                    }
                    test(provider);
                    System.out.println("Tetscase count: " + TestLoggerFinder.sequencer.get());
                    break;
                case WITHCUSTOMWRAPPERS:
                    System.out.println("\n*** With custom Wrapper\n");
                    System.out.println(TestLoggerFinder.conf.get());
                    provider = getLoggerFinder(expectedClass);
                    if (!provider.getClass().getName().equals("BaseLoggerFinder")) {
                        throw new RuntimeException("Unexpected provider: " + provider.getClass().getName());
                    }
                    test(provider, CustomLoggerWrapper::new);
                    break;
                case WITHREFLECTION:
                    System.out.println("\n*** Using reflection while logging\n");
                    System.out.println(TestLoggerFinder.conf.get());
                    provider = getLoggerFinder(expectedClass);
                    if (!provider.getClass().getName().equals("BaseLoggerFinder")) {
                        throw new RuntimeException("Unexpected provider: " + provider.getClass().getName());
                    }
                    test(provider, ReflectionLoggerWrapper::new);
                    break;
                default:
                    throw new RuntimeException("Unknown test case: " + testCase);
            }
        });
        System.out.println("\nPASSED: Tested " + TestLoggerFinder.sequencer.get() + " cases.");
    }

    public static void test(TestLoggerFinder provider) {
        test(provider, Function.identity());
    }

    public static void test(TestLoggerFinder provider, Function<Logger, Logger> wrapper) {

        ResourceBundle loggerBundle = ResourceBundle.getBundle(MyLoggerBundle.class.getName());
        final Map<Logger, String> loggerDescMap = new HashMap<>();

        System.Logger sysLogger = wrapper.apply(accessSystemLogger.getLogger("foo"));
        loggerDescMap.put(sysLogger, "accessSystemLogger.getLogger(\"foo\")");
        System.Logger localizedSysLogger = wrapper.apply(accessSystemLogger.getLogger("fox", loggerBundle));
        loggerDescMap.put(localizedSysLogger, "accessSystemLogger.getLogger(\"fox\", loggerBundle)");
        System.Logger appLogger = wrapper.apply(System.getLogger("bar"));
        loggerDescMap.put(appLogger,"System.getLogger(\"bar\")");
        System.Logger localizedAppLogger = wrapper.apply(System.getLogger("baz", loggerBundle));
        loggerDescMap.put(localizedAppLogger,"System.getLogger(\"baz\", loggerBundle)");

        testLogger(provider, loggerDescMap, "foo", null, sysLogger, accessSystemLogger.getClass());
        testLogger(provider, loggerDescMap, "foo", loggerBundle, localizedSysLogger, accessSystemLogger.getClass());
        testLogger(provider, loggerDescMap, "foo", null, appLogger, BaseDefaultLoggerFinderTest.class);
        testLogger(provider, loggerDescMap, "foo", loggerBundle, localizedAppLogger, BaseDefaultLoggerFinderTest.class);
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
            Class<?> callerClass) {

        System.out.println("Testing " + loggerDescMap.get(logger) + " [" + logger +"]");
        AtomicLong sequencer = TestLoggerFinder.sequencer;

        Module caller = callerClass.getModule();
        Foo foo = new Foo();
        String fooMsg = foo.toString();
        for (Level loggerLevel : Level.values()) {
            provider.setLevel(logger, loggerLevel, caller);
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
                    if (!logged.contains("BaseDefaultLoggerFinderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + fooMsg)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] BaseDefaultLoggerFinderTest testLogger\n"
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
        for (Level loggerLevel : Level.values()) {
            provider.setLevel(logger, loggerLevel, caller);
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
                    if (!logged.contains("BaseDefaultLoggerFinderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + msgText)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] BaseDefaultLoggerFinderTest testLogger\n"
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

        for (Level loggerLevel : Level.values()) {
            provider.setLevel(logger, loggerLevel, caller);
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
                    if (!logged.contains("BaseDefaultLoggerFinderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + fooSupplier.get())) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] BaseDefaultLoggerFinderTest testLogger\n"
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
        for (Level loggerLevel : Level.values()) {
            provider.setLevel(logger, loggerLevel, caller);
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
                    if (!logged.contains("BaseDefaultLoggerFinderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] BaseDefaultLoggerFinderTest testLogger\n"
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
        for (Level loggerLevel : Level.values()) {
            provider.setLevel(logger, loggerLevel, caller);
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
                    if (!logged.contains("BaseDefaultLoggerFinderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + msgText)
                        || !logged.contains(text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] BaseDefaultLoggerFinderTest testLogger\n"
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


        for (Level loggerLevel : Level.values()) {
            provider.setLevel(logger, loggerLevel, caller);
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
                    if (!logged.contains("BaseDefaultLoggerFinderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + fooSupplier.get())
                        || !logged.contains(text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] BaseDefaultLoggerFinderTest testLogger\n"
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
        for (Level loggerLevel : Level.values()) {
            provider.setLevel(logger, loggerLevel, caller);
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
                    if (!logged.contains("BaseDefaultLoggerFinderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] BaseDefaultLoggerFinderTest testLogger\n"
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

        for (Level loggerLevel : Level.values()) {
            provider.setLevel(logger, loggerLevel, caller);
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
                    if (!logged.contains("BaseDefaultLoggerFinderTest testLogger")
                        || !logged.contains(messageLevel.getName() + ": " + textMsg)
                        || !logged.contains(text)) {
                        throw new RuntimeException("mismatch for " + desc
                                + "\n\texpected:" + "\n<<<<\n"
                                + "[date] BaseDefaultLoggerFinderTest testLogger\n"
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
