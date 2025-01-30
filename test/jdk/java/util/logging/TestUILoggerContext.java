/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import jdk.internal.access.JavaAWTAccess;
import jdk.internal.access.SharedSecrets;

/*
 * @test
 * @bug 8017174 8010727 8019945
 * @summary  NPE when using Logger.getAnonymousLogger or
 *           LogManager.getLogManager().getLogger
 *
 * @modules java.base/jdk.internal.access
 *          java.logging
 * @run main/othervm TestUILoggerContext LoadingUIContext
 * @run main/othervm TestUILoggerContext LoadingMain
 * @run main/othervm TestUILoggerContext One
 * @run main/othervm TestUILoggerContext Two
 * @run main/othervm TestUILoggerContext Three
 * @run main/othervm TestUILoggerContext Four
 * @run main/othervm TestUILoggerContext Five
 * @run main/othervm TestUILoggerContext Six
 * @run main/othervm TestUILoggerContext Seven
 * @run main/othervm TestUILoggerContext
 */

// NOTE: We run in other VM in order to cause LogManager class to be loaded anew.
public class TestUILoggerContext {

    // The bridge class initializes the logging system.
    // It stubs the UI context in order to simulate context changes.
    //
    public static class Bridge {

        private static class JavaAWTAccessStub implements JavaAWTAccess {
            boolean active = true;

            private static class TestExc {
                private final Map<Object, Object> map = new HashMap<>();
                void put(Object key, Object v) { map.put(key, v); }
                Object get(Object key) { return map.get(key); }
                void remove(Object o) { map.remove(o); }
                public static TestExc exc(Object o) {
                    return TestExc.class.cast(o);
                }
            }

            TestExc exc;

            @Override
            public Object getAppletContext() { return active ? exc : null; }
        }

        static final JavaAWTAccessStub javaAwtAccess = new JavaAWTAccessStub();
        public static void init() {
            SharedSecrets.setJavaAWTAccess(javaAwtAccess);
        }

        public static void changeContext() {
            System.out.println("... Switching to a new UI context ...");
            javaAwtAccess.active = true;
            javaAwtAccess.exc = new JavaAWTAccessStub.TestExc();
        }

        public static void desactivate() {
            System.out.println("... Running with no UI context ...");
            javaAwtAccess.exc = null;
            javaAwtAccess.active = false;
        }

        public static class CustomAnonymousLogger extends Logger {
            public CustomAnonymousLogger() {
                this("");
            }
            public CustomAnonymousLogger(String name) {
                super(null, null);
                System.out.println( " LogManager: " +LogManager.getLogManager());
                System.out.println( " getLogger: " +LogManager.getLogManager().getLogger(name));
                setParent(LogManager.getLogManager().getLogger(name));
            }
        }

        public static class CustomLogger extends Logger {
            CustomLogger(String name) {
                super(name, null);
            }
        }
    }

    public static enum TestCase {
        LoadingUIContext, LoadingMain, One, Two, Three, Four, Five, Six, Seven;
        public void test() {
            switch(this) {
                // When run - each of these two tests must be
                // run before any other tests and before each other.
                case LoadingUIContext: testLoadingUIContext(); break;
                case LoadingMain:   testLoadingMain(); break;
                case One:   testOne(); break;
                case Two:   testTwo(); break;
                case Three: testThree(); break;
                case Four:  testFour(); break;
                case Five:  testFive(); break;
                case Six:   testSix(); break;
                case Seven: testSeven(); break;
            }
        }
        public String describe() {
            switch(this) {
                case LoadingUIContext:
                    return "Test that when the LogManager class is"
                        + " loaded with UI context first,"
                        + "\n all LoggerContexts are correctly initialized";
                case LoadingMain:
                    return "Test that when the LogManager class is"
                        + " loaded in  the main thread first,"
                        + "\n all LoggerContexts are correctly initialized";
                case One:
                    return "Test that Logger.getAnonymousLogger()"
                        + " and new CustomAnonymousLogger() don't throw NPE";
                case Two:
                    return "Test that Logger.getLogger(\"\")"
                            + " does not return null nor throws NPE";
                case Three:
                    return "Test that LogManager.getLogManager().getLogger(\"\")"
                            + " does not return null nor throws NPE";
                case Four:
                    return "Test that Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)"
                            + " does not return null,\n and that"
                            + " new CustomAnonymousLogger(Logger.GLOBAL_LOGGER_NAME)"
                            + " does not throw NPE";
                case Five:
                    return "Test that LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME)"
                            + "\n does not return null nor throws NPE";
                case Six:
                    return "Test that manager.getLogger(Logger.GLOBAL_LOGGER_NAME)"
                            + " returns null\n when manager is not the default"
                            + " LogManager instance.\n"
                            + "Test adding a new logger named \"global\" in that"
                            + " non default instance.";
                case Seven: return "Test that manager.getLogger(\"\")"
                            + " returns null\n when manager is not the default"
                            + " LogManager instance.\n"
                            + "Test adding a new logger named \"\" in that"
                            + " non default instance.";
                default: return "Undefined";
            }
        }
    };

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Bridge.init();
        EnumSet<TestCase> tests = EnumSet.noneOf(TestCase.class);
        for (String arg : args) {
            tests.add(TestCase.valueOf(arg));
        }
        if (args.length == 0) {
            tests = EnumSet.complementOf(EnumSet.of(TestCase.LoadingMain));
        }
        final EnumSet<TestCase> loadingTests =
            EnumSet.of(TestCase.LoadingUIContext, TestCase.LoadingMain);
        int testrun = 0;
        for (TestCase test : tests) {
            if (loadingTests.contains(test)) {
                if (testrun > 0) {
                    throw new UnsupportedOperationException("Test case "
                          + test + " must be executed first!");
                }
            }
            System.out.println("Testing "+ test+": ");
            System.out.println(test.describe());
            try {
                test.test();
            } catch (Exception x) {
               throw new Error(String.valueOf(test)
                   + "failed: "+x+"\n "+"FAILED: "+test.describe()+"\n", x);
            } finally {
                testrun++;
            }
            Bridge.changeContext();
            System.out.println("PASSED: "+ test);
        }
    }

    public static void testLoadingUIContext() {
        Bridge.changeContext();

        Logger bar = new Bridge.CustomLogger("com.foo.Bar");
        LogManager.getLogManager().addLogger(bar);
        assertNotNull(bar.getParent());
        testParent(bar);
        testParent(LogManager.getLogManager().getLogger("global"));
        testParent(LogManager.getLogManager().getLogger(bar.getName()));

        Bridge.desactivate();

        Logger foo = new Bridge.CustomLogger("com.foo.Foo");
        boolean b = LogManager.getLogManager().addLogger(foo);
        assertEquals(Boolean.TRUE, Boolean.valueOf(b));
        assertNotNull(foo.getParent());
        testParent(foo);
        testParent(LogManager.getLogManager().getLogger("global"));
        testParent(LogManager.getLogManager().getLogger(foo.getName()));
    }

    public static void testLoadingMain() {
        Bridge.desactivate();

        Logger bar = new Bridge.CustomLogger("com.foo.Bar");
        LogManager.getLogManager().addLogger(bar);
        assertNotNull(bar.getParent());
        testParent(bar);
        testParent(LogManager.getLogManager().getLogger("global"));
        testParent(LogManager.getLogManager().getLogger(bar.getName()));

        Bridge.changeContext();

        Logger foo = new Bridge.CustomLogger("com.foo.Foo");
        boolean b = LogManager.getLogManager().addLogger(foo);
        assertEquals(Boolean.TRUE, Boolean.valueOf(b));
        assertNotNull(foo.getParent());
        testParent(foo);
        testParent(LogManager.getLogManager().getLogger("global"));
        testParent(LogManager.getLogManager().getLogger(foo.getName()));

    }

    public static void testOne() {
        for (int i=0; i<3 ; i++) {
            Logger logger1 = Logger.getAnonymousLogger();
            Logger logger1b = Logger.getAnonymousLogger();
            Bridge.changeContext();
            Logger logger2 = Logger.getAnonymousLogger();
            Logger logger2b = Logger.getAnonymousLogger();
            Bridge.changeContext();
            Logger logger3 = new Bridge.CustomAnonymousLogger();
            Logger logger3b = new Bridge.CustomAnonymousLogger();
            Bridge.changeContext();
            Logger logger4 = new Bridge.CustomAnonymousLogger();
            Logger logger4b = new Bridge.CustomAnonymousLogger();
        }
    }


    public static void testTwo() {
        for (int i=0; i<3 ; i++) {
            Logger logger1 = Logger.getLogger("");
            Logger logger1b = Logger.getLogger("");
            assertNotNull(logger1);
            assertNotNull(logger1b);
            assertEquals(logger1, logger1b);
            Bridge.changeContext();
            Logger logger2 = Logger.getLogger("");
            Logger logger2b = Logger.getLogger("");
            assertNotNull(logger2);
            assertNotNull(logger2b);
            assertEquals(logger2, logger2b);
            assertEquals(logger1, logger2);
        }
    }

    public static void testThree() {
        for (int i=0; i<3 ; i++) {
            Logger logger1 = LogManager.getLogManager().getLogger("");
            Logger logger1b = LogManager.getLogManager().getLogger("");
            assertNotNull(logger1);
            assertNotNull(logger1b);
            assertEquals(logger1, logger1b);
            Bridge.changeContext();
            Logger logger2 = LogManager.getLogManager().getLogger("");
            Logger logger2b = LogManager.getLogManager().getLogger("");
            assertNotNull(logger2);
            assertNotNull(logger2b);
            assertEquals(logger2, logger2b);
            assertEquals(logger1, logger2);
        }
    }

    public static void testFour() {
        for (int i=0; i<3 ; i++) {
            Logger logger1 = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger1b = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
            assertNotNull(logger1);
            assertNotNull(logger1b);
            assertEquals(logger1, logger1b);
            Bridge.changeContext();

            Logger logger2 = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger2b = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
            assertNotNull(logger2);
            assertNotNull(logger2b);
            assertEquals(logger2, logger2b);

            assertEquals(logger1, logger2);

            Bridge.changeContext();
            Logger logger3 = new Bridge.CustomAnonymousLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger3b = new Bridge.CustomAnonymousLogger(Logger.GLOBAL_LOGGER_NAME);
            Bridge.changeContext();
            Logger logger4 = new Bridge.CustomAnonymousLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger4b = new Bridge.CustomAnonymousLogger(Logger.GLOBAL_LOGGER_NAME);
        }
    }

    public static void testFive() {
        for (int i=0; i<3 ; i++) {
            Logger logger1 = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger1b = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME);
            assertNotNull(logger1);
            assertNotNull(logger1b);
            assertEquals(logger1, logger1b);

            Bridge.changeContext();

            Logger logger2 = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger2b = LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME);
            assertNotNull(logger2);
            assertNotNull(logger2b);
            assertEquals(logger2, logger2b);

            assertEquals(logger1, logger2);
        }
    }

    /**
     * This test is designed to test the behavior of additional LogManager instances.
     * It must be noted that if the security manager is off, then calling
     * Bridge.changeContext() has actually no effect.
     * off.
     **/
    public static void testSix() {
        for (int i=0; i<3 ; i++) {
            Bridge.desactivate();
            LogManager manager = new LogManager() {};
            Logger logger1 = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger1b = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
            assertNull(logger1);
            assertNull(logger1b);
            Logger global = new Bridge.CustomLogger(Logger.GLOBAL_LOGGER_NAME);
            manager.addLogger(global);
            Logger logger2 = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger2b = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
            assertNotNull(logger2);
            assertNotNull(logger2b);
            assertEquals(logger2, global);
            assertEquals(logger2b, global);
            assertNull(manager.getLogger(""));
            assertNull(manager.getLogger(""));

            for (int j = 0; j<3; j++) {
                Bridge.changeContext();

                // this is not a supported configuration:
                // We are in an UI context with several log managers.
                // We however need to check our assumptions...

                // UI context => root logger and global logger should also be null.

                Logger expected = global;
                Logger logger3 = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
                Logger logger3b = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
                assertEquals(expected, logger3);
                assertEquals(expected, logger3b);
                Logger global2 = new Bridge.CustomLogger(Logger.GLOBAL_LOGGER_NAME);
                manager.addLogger(global2);
                Logger logger4 = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
                Logger logger4b = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
                assertNotNull(logger4);
                assertNotNull(logger4b);
                expected = global;
                assertEquals(logger4,  expected);
                assertEquals(logger4b, expected);

                Logger logger5 = manager.getLogger("");
                Logger logger5b = manager.getLogger("");
                Logger expectedRoot = null;
                assertEquals(logger5, expectedRoot);
                assertEquals(logger5b, expectedRoot);
            }

        }
    }

    /**
     * This test is designed to test the behavior of additional LogManager instances.
     * It must be noted that if the security manager is off, then calling
     * Bridge.changeContext() has actually no effect.
     **/
    public static void testSeven() {
        for (int i=0; i<3 ; i++) {
            Bridge.desactivate();
            LogManager manager = new LogManager() {};
            Logger logger1 = manager.getLogger("");
            Logger logger1b = manager.getLogger("");
            assertNull(logger1);
            assertNull(logger1b);
            Logger global = new Bridge.CustomLogger(Logger.GLOBAL_LOGGER_NAME);
            manager.addLogger(global);
            Logger logger2 = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
            Logger logger2b = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
            assertNotNull(logger2);
            assertNotNull(logger2b);
            assertEquals(logger2, global);
            assertEquals(logger2b, global);
            Logger logger3 = manager.getLogger("");
            Logger logger3b = manager.getLogger("");
            assertNull(logger3);
            assertNull(logger3b);
            Logger root = new Bridge.CustomLogger("");
            manager.addLogger(root);
            Logger logger4 = manager.getLogger("");
            Logger logger4b = manager.getLogger("");
            assertNotNull(logger4);
            assertNotNull(logger4b);
            assertEquals(logger4, root);
            assertEquals(logger4b, root);

            for (int j = 0 ; j < 3 ; j++) {
                Bridge.changeContext();

                // this is not a supported configuration:
                // We are in an UI context with several log managers.
                // We however need to check our assumptions...

                // UI context => root logger and global logger should also be null.

                Logger logger5 = manager.getLogger("");
                Logger logger5b = manager.getLogger("");
                Logger expectedRoot = root;
                assertEquals(logger5, expectedRoot);
                assertEquals(logger5b, expectedRoot);

                assertEquals(global, manager.getLogger(Logger.GLOBAL_LOGGER_NAME));

                Logger global2 = new Bridge.CustomLogger(Logger.GLOBAL_LOGGER_NAME);
                manager.addLogger(global2);
                Logger logger6 = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
                Logger logger6b = manager.getLogger(Logger.GLOBAL_LOGGER_NAME);
                Logger expectedGlobal = global;

                assertNotNull(logger6);
                assertNotNull(logger6b);
                assertEquals(logger6, expectedGlobal);
                assertEquals(logger6b, expectedGlobal);
                assertEquals(root, manager.getLogger(""));

                Logger root2 = new Bridge.CustomLogger("");
                manager.addLogger(root2);
                expectedRoot = root;
                Logger logger7 = manager.getLogger("");
                Logger logger7b = manager.getLogger("");
                assertNotNull(logger7);
                assertNotNull(logger7b);
                assertEquals(logger7, expectedRoot);
                assertEquals(logger7b, expectedRoot);
            }
        }
    }

    public static void testParent(Logger logger) {
        Logger l = logger;
        while (l.getParent() != null) {
            l = l.getParent();
        }
        assertEquals("", l.getName());
    }

    public static class TestError extends RuntimeException {
        public TestError(String msg) {
            super(msg);
        }
    }

    public static void assertNotNull(Object obj) {
        if (obj == null) throw new NullPointerException();
    }

    public static void assertNull(Object obj) {
        if (obj != null) throw new TestError("Null expected, got "+obj);
    }

    public static void assertEquals(Object o1, Object o2) {
        if (o1 != o2) {
            throw new TestError(o1 + " != " + o2);
        }
    }

    public static void assertNotEquals(Object o1, Object o2) {
        if (o1 == o2) {
            throw new TestError(o1 + " == " + o2);
        }
    }
}
