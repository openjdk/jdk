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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * @test
 * @bug 8033661 8189291
 * @summary tests LogManager.updateConfiguration(InputStream, Function) method
 * @run main/othervm SimpleUpdateConfigWithInputStreamTest
 * @author danielfuchs
 */
public class SimpleUpdateConfigWithInputStreamTest {

    // We will test updateConfiguration
    public static void execute(Runnable run) {
        try {
           Configure.doPrivileged(run);
        } finally {
           Configure.doPrivileged(() -> {
               try {
                   setSystemProperty("java.util.logging.config.file", null);
                   LogManager.getLogManager().readConfiguration();
                   System.gc();
               } catch (Exception x) {
                   throw new RuntimeException(x);
               }
           });
        }
    }

    public static class MyHandler extends Handler {
        static final AtomicLong seq = new AtomicLong();
        long count = seq.incrementAndGet();

        @Override
        public void publish(LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        @Override
        public String toString() {
            return super.toString() + "("+count+")";
        }

    }

    static String storePropertyToFile(String name, Properties props)
        throws Exception {
        return Configure.callPrivileged(() -> {
            String scratch = System.getProperty("user.dir", ".");
            Path p = Paths.get(scratch, name);
            try (FileOutputStream fos = new FileOutputStream(p.toFile())) {
                props.store(fos, name);
            }
            return p.toString();
        });
    }

    static void setSystemProperty(String name, String value)
        throws Exception {
        Configure.doPrivileged(() -> {
            if (value == null)
                System.clearProperty(name);
            else
                System.setProperty(name, value);
        });
    }

    static String trim(String value) {
        return value == null ? null : value.trim();
    }


    /**
     * Tests one of the configuration defined above.
     * <p>
     * This is the main test method (the rest is infrastructure).
     */
    static void testUpdateConfiguration() {
        try {
            // manager initialized with default configuration.
            LogManager manager = LogManager.getLogManager();

            // Test default configuration. It should not have
            // any value for "com.foo.level" and "com.foo.handlers"
            assertEquals(null, manager.getProperty("com.foo.level"),
                "com.foo.level in default configuration");
            assertEquals(null, manager.getProperty("com.foo.handlers"),
                "com.foo.handlers in default configuration");

            // Create a logging configuration file that contains
            // com.foo.level=FINEST
            // and set "java.util.logging.config.file" to this file.
            Properties props = new Properties();
            props.setProperty("com.foo.level", "FINEST");

            // Update configuration with props
            // then test that the new configuration has
            // com.foo.level=FINEST
            // and nothing for com.foo.handlers
            Configure.updateConfigurationWith(props, null);
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level in " + props);
            assertEquals(null, manager.getProperty("com.foo.handlers"),
                "com.foo.handlers in " + props);

            // call updateConfiguration with an empty stream.
            // check that the new configuration no longer has
            // any value for com.foo.level, and still no value
            // for com.foo.handlers
            Configure.updateConfigurationWith(new Properties(), null);
            assertEquals(null, manager.getProperty("com.foo.level"),
                    "com.foo.level in default configuration");
            assertEquals(null, manager.getProperty("com.foo.handlers"),
                "com.foo.handlers in default configuration");

            // creates the com.foo logger, check it has
            // the default config: no level, and no handlers
            final Logger logger = Logger.getLogger("com.foo");
            assertEquals(null, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertDeepEquals(new Handler[0], logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");

            // call updateConfiguration with 'props'
            // check that the configuration has
            // com.foo.level=FINEST
            // and nothing for com.foo.handlers
            // check that the logger has now a FINEST level and still
            // no handlers
            Configure.updateConfigurationWith(props, null);
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level in " + props);
            assertEquals(Level.FINEST, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertDeepEquals(new Handler[0], logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals(null, manager.getProperty("com.foo.handlers"),
                "com.foo.handlers in " + props);

            // Calls updateConfiguration with a lambda whose effect should
            // be to set the FINER level on the "com.foo" logger.
            // Check that the new configuration has
            // com.foo.level=FINER
            // and nothing for com.foo.handlers
            // check that the logger has now a FINER level and still
            // no handlers
            Configure.updateConfigurationWith(props,
                    (k) -> ("com.foo.level".equals(k) ? (o, n) -> "FINER" : (o, n) -> n));
            assertEquals("FINER", manager.getProperty("com.foo.level"),
                "com.foo.level set to FINER by updateConfiguration");
            assertEquals(Level.FINER, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertDeepEquals(new Handler[0], logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals(null, manager.getProperty("com.foo.handlers"),
                "com.foo.handlers in " + props);

            // Calls updateConfiguration with a lambda whose effect is a noop.
            // This should not change the configuration, so
            // check that the new configuration still has
            // com.foo.level=FINER
            // and nothing for com.foo.handlers
            // check that the logger still has FINER level and still
            // no handlers
            Configure.updateConfigurationWith(props,
                    (k) -> ((o, n) -> o));
            assertEquals("FINER", manager.getProperty("com.foo.level"),
                "com.foo.level preserved by updateConfiguration");
            assertEquals(Level.FINER, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertDeepEquals(new Handler[0], logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals(null, manager.getProperty("com.foo.handlers"),
                "com.foo.handlers in " + props);

            // Calls updateConfiguration with a lambda whose effect is to
            // take all values from the new configuration.
            // This should update the configuration to what is in props, so
            // check that the new configuration has
            // com.foo.level=FINEST
            // and nothing for com.foo.handlers
            // check that the logger now has FINEST level and still
            // no handlers
            Configure.updateConfigurationWith(props,
                    (k) -> ((o, n) -> n));
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level updated by updateConfiguration");
            assertEquals(Level.FINEST, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertDeepEquals(new Handler[0], logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals(null, manager.getProperty("com.foo.handlers"),
                "com.foo.handlers in " + props);

            // now set a handler on the com.foo logger.
            MyHandler h = new MyHandler();
            logger.addHandler(h);
            assertDeepEquals(new Handler[] {h}, logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");

            // Calls updateConfiguration with a lambda whose effect should
            // be to set the FINER level on the "com.foo" logger, and
            // take the value from props for everything else.
            // Check that the new configuration has
            // com.foo.level=FINER
            // and nothing for com.foo.handlers
            // check that the logger has now a FINER level, but that its
            // handlers are still present and have not been reset
            // since neither the old nor new configuration defined them.
            Configure.updateConfigurationWith(props,
                    (k) -> ("com.foo.level".equals(k) ? (o, n) -> "FINER" : (o, n) -> n));
            assertEquals("FINER", manager.getProperty("com.foo.level"),
                "com.foo.level set to FINER by updateConfiguration");
            assertEquals(Level.FINER, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertDeepEquals(new Handler[] {h}, logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals(null, manager.getProperty("com.foo.handlers"),
                "com.foo.handlers in " + props);

            // now add some configuration for com.foo.handlers
            props.setProperty("com.foo.handlers", MyHandler.class.getName());

            // we didn't call updateConfiguration, so just changing the
            // content of props should have had no effect.
            assertEquals("FINER", manager.getProperty("com.foo.level"),
                "com.foo.level set to FINER by updateConfiguration");
            assertEquals(Level.FINER, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertEquals(null,
                    manager.getProperty("com.foo.handlers"),
                    "manager.getProperty(\"com.foo.handlers\")");
            assertDeepEquals(new Handler[] {h}, logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");

            // Calls updateConfiguration with a lambda whose effect is a noop.
            // This should not change the current configuration, so
            // check that the new configuration still has
            // com.foo.level=FINER
            // and nothing for com.foo.handlers
            // check that the logger still has FINER level and still
            // has its handlers and that they haven't been reset.
            Configure.updateConfigurationWith(props, (k) -> ((o, n) -> o));
            assertEquals("FINER", manager.getProperty("com.foo.level"),
                "com.foo.level set to FINER by updateConfiguration");
            assertEquals(Level.FINER, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertEquals(null,
                    manager.getProperty("com.foo.handlers"),
                    "manager.getProperty(\"com.foo.handlers\")");
            assertDeepEquals(new Handler[] {h}, logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");

            // Calls updateConfiguration with a lambda whose effect is to
            // take all values from the new configuration.
            // This should update the configuration to what is in props, so
            // check that the new configuration has
            // com.foo.level=FINEST
            // com.foo.handlers=SimpleUpdateConfigWithInputStreamTest$MyHandler
            // check that the logger now has FINEST level
            // and a new handler instance, since the old config
            // had no handlers for com.foo and the new config has one.
            Configure.updateConfigurationWith(props, (k) -> ((o, n) -> n));
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level updated by updateConfiguration");
            assertEquals(Level.FINEST, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertEquals(MyHandler.class.getName(),
                    manager.getProperty("com.foo.handlers"),
                    "manager.getProperty(\"com.foo.handlers\")");
            Handler[] loggerHandlers = logger.getHandlers().clone();
            assertEquals(1, loggerHandlers.length,
                    "Logger.getLogger(\"com.foo\").getHandlers().length");
            assertEquals(MyHandler.class, loggerHandlers[0].getClass(),
                    "Logger.getLogger(\"com.foo\").getHandlers()[0].getClass()");
            assertEquals(h.count + 1, ((MyHandler)logger.getHandlers()[0]).count,
                    "Logger.getLogger(\"com.foo\").getHandlers()[0].count");

            // Calls updateConfiguration with a lambda whose effect is a noop.
            // This should not change the current configuration, so
            // check that the new configuration still has
            // com.foo.level=FINEST
            // com.foo.handlers=SimpleUpdateConfigWithInputStreamTest$MyHandler
            // check that the logger still has FINEST level and still
            // has its handlers and that they haven't been reset.
            Configure.updateConfigurationWith(props, (k) -> ((o, n) -> o));
            assertDeepEquals(loggerHandlers, logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level updated by updateConfiguration");
            assertEquals(Level.FINEST, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertEquals(MyHandler.class.getName(),
                    manager.getProperty("com.foo.handlers"),
                    "manager.getProperty(\"com.foo.handlers\")");

            // Calls updateConfiguration with a lambda whose effect is to
            // take all values from the new configuration.
            // Because the content of the props hasn't changed, then
            // it should also be a noop.
            // check that the new configuration still has
            // com.foo.level=FINEST
            // com.foo.handlers=SimpleUpdateConfigWithInputStreamTest$MyHandler
            // check that the logger still has FINEST level and still
            // has its handlers and that they haven't been reset.
            Configure.updateConfigurationWith(props, (k) -> ((o, n) -> n));
            assertDeepEquals(loggerHandlers, logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level updated by updateConfiguration");
            assertEquals(Level.FINEST, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertEquals(MyHandler.class.getName(),
                    manager.getProperty("com.foo.handlers"),
                    "manager.getProperty(\"com.foo.handlers\")");

            // Calls updateConfiguration with a null lambda, whose effect is to
            // take all values from the new configuration.
            // Because the content of the props hasn't changed, then
            // it should also be a noop.
            // check that the new configuration still has
            // com.foo.level=FINEST
            // com.foo.handlers=SimpleUpdateConfigWithInputStreamTest$MyHandler
            // check that the logger still has FINEST level and still
            // has its handlers and that they haven't been reset.
            Configure.updateConfigurationWith(props, (k) -> ((o, n) -> n));
            assertDeepEquals(loggerHandlers, logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level updated by updateConfiguration");
            assertEquals(Level.FINEST, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertEquals(MyHandler.class.getName(),
                    manager.getProperty("com.foo.handlers"),
                    "manager.getProperty(\"com.foo.handlers\")");

            // now remove com.foo.handlers=SimpleUpdateConfigWithInputStreamTest$MyHandler
            // from the configuration file.
            props.remove("com.foo.handlers");

            // Calls updateConfiguration with a lambda whose effect is a noop.
            // This should not change the current configuration, so
            // check that the new configuration still has
            // com.foo.level=FINEST
            // com.foo.handlers=SimpleUpdateConfigWithInputStreamTest$MyHandler
            // check that the logger still has FINEST level and still
            // has its handlers and that they haven't been reset.
            Configure.updateConfigurationWith(props, (k) -> ((o, n) -> o));
            assertDeepEquals(loggerHandlers, logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level updated by updateConfiguration");
            assertEquals(Level.FINEST, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertEquals(MyHandler.class.getName(),
                    manager.getProperty("com.foo.handlers"),
                    "manager.getProperty(\"com.foo.handlers\")");

            // Calls updateConfiguration with a lambda whose effect is to
            // take all values from the new configuration.
            // This should update the configuration to what is in props, so
            // check that the new configuration has
            // com.foo.level=FINEST
            // and nothing for com.foo.handlers
            // check that the logger still has FINEST level
            // and no handlers, since the old config
            // had an handler for com.foo and the new config doesn't.
            Configure.updateConfigurationWith(props, (k) -> ((o, n) -> n));
            assertDeepEquals(new Handler[0], logger.getHandlers(),
                    "Logger.getLogger(\"com.foo\").getHandlers()");
            assertEquals("FINEST", manager.getProperty("com.foo.level"),
                "com.foo.level updated by updateConfiguration");
            assertEquals(Level.FINEST, logger.getLevel(),
                "Logger.getLogger(\"com.foo\").getLevel()");
            assertEquals(null,
                    manager.getProperty("com.foo.handlers"),
                    "manager.getProperty(\"com.foo.handlers\")");


        } catch (RuntimeException | Error r) {
            throw r;
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    public static void main(String[] args) throws Exception {
        execute(SimpleUpdateConfigWithInputStreamTest::testUpdateConfiguration);
    }

    static class Configure {

        static void updateConfigurationWith(Properties propertyFile,
                Function<String,BiFunction<String,String,String>> remapper) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                propertyFile.store(bytes, propertyFile.getProperty("test.name"));
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes.toByteArray());
                LogManager.getLogManager().updateConfiguration(bais, remapper);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        static void doPrivileged(Runnable run) {
            run.run();
        }
        static <T> T callPrivileged(Callable<T> call) throws Exception {
            return call.call();
        }
    }

    static final class TestAssertException extends RuntimeException {
        TestAssertException(String msg) {
            super(msg);
        }
    }

    private static void assertEquals(long expected, long received, String msg) {
        if (expected != received) {
            throw new TestAssertException("Unexpected result for " + msg
                    + ".\n\texpected: " + expected
                    +  "\n\tactual:   " + received);
        } else {
            System.out.println("Got expected " + msg + ": " + received);
        }
    }

    private static void assertEquals(String expected, String received, String msg) {
        if (!Objects.equals(expected, received)) {
            throw new TestAssertException("Unexpected result for " + msg
                    + ".\n\texpected: " + expected
                    +  "\n\tactual:   " + received);
        } else {
            System.out.println("Got expected " + msg + ": " + received);
        }
    }

    private static void assertEquals(Object expected, Object received, String msg) {
        if (!Objects.equals(expected, received)) {
            throw new TestAssertException("Unexpected result for " + msg
                    + ".\n\texpected: " + expected
                    +  "\n\tactual:   " + received);
        } else {
            System.out.println("Got expected " + msg + ": " + received);
        }
    }

    public static String deepToString(Object o) {
        if (o == null) {
            return "null";
        } else if (o.getClass().isArray()) {
            String s;
            if (o instanceof Object[])
                s = Arrays.deepToString((Object[]) o);
            else if (o instanceof byte[])
                s = Arrays.toString((byte[]) o);
            else if (o instanceof short[])
                s = Arrays.toString((short[]) o);
            else if (o instanceof int[])
                s = Arrays.toString((int[]) o);
            else if (o instanceof long[])
                s = Arrays.toString((long[]) o);
            else if (o instanceof char[])
                s = Arrays.toString((char[]) o);
            else if (o instanceof float[])
                s = Arrays.toString((float[]) o);
            else if (o instanceof double[])
                s = Arrays.toString((double[]) o);
            else if (o instanceof boolean[])
                s = Arrays.toString((boolean[]) o);
            else
                s = o.toString();
            return s;
        } else {
            return o.toString();
        }
    }

    private static void assertDeepEquals(Object expected, Object received, String msg) {
        if (!Objects.deepEquals(expected, received)) {
            throw new TestAssertException("Unexpected result for " + msg
                    + ".\n\texpected: " + deepToString(expected)
                    +  "\n\tactual:   " + deepToString(received));
        } else {
            System.out.println("Got expected " + msg + ": " + deepToString(received));
        }
    }
}
